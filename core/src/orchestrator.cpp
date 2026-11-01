#include "transpiler/orchestrator.hpp"

#include <algorithm>
#include <format>
#include <fstream>
#include <map>
#include <set>

#include "transpiler/log.hpp"
#include "transpiler/review.hpp"
#include "transpiler/worker_client.hpp"

namespace transpiler {
namespace fs = std::filesystem;

std::string_view to_string(RunResult::Status status) {
  switch (status) {
    case RunResult::Status::ok: return "ok";
    case RunResult::Status::incomplete: return "incomplete";
    case RunResult::Status::failed: return "failed";
  }
  return "failed";
}

std::expected<Job, std::string> parse_job(const json& j) {
  if (!j.is_object()) return std::unexpected("job must be a JSON object");
  if (j.value("job_version", 0) != 1) return std::unexpected("unsupported job_version (expected 1)");
  Job job;
  try {
    if (j.contains("translator") && !j["translator"].is_null()) job.translator = j["translator"].get<std::string>();
    job.source_language = j.at("source_language").get<std::string>();
    job.target_language = j.at("target_language").get<std::string>();
    job.inputs = j.at("inputs").get<std::vector<std::string>>();
    if (j.contains("source_root") && !j["source_root"].is_null()) job.source_root = j["source_root"].get<std::string>();
    job.output = fs::absolute(j.at("output").get<std::string>()).lexically_normal();
    job.translators_dir = j.at("translators_dir").get<std::string>();
    const json& d = j.value("discovery", json::object());
    job.discovery.program_extensions = d.value("program_extensions", std::vector<std::string>{});
    job.discovery.copybook_extensions = d.value("copybook_extensions", std::vector<std::string>{});
    job.discovery.copybook_dirs = d.value("copybook_dirs", std::vector<std::string>{});
    job.discovery.exclude = d.value("exclude", std::vector<std::string>{});
    job.translator_params = j.value("translator_params", json::object());
    job.mappings = j.at("mappings");
    job.provider = j.value("provider", json::object());
    job.request_timeout = std::chrono::seconds(j.value("request_timeout_seconds", 300));
    job.max_decision_rounds = j.value("max_decision_rounds", 8);
    job.write_report = j.value("write_report", true);
  } catch (const json::exception& e) {
    return std::unexpected(std::format("invalid job: {}", e.what()));
  }
  if (job.discovery.program_extensions.empty()) return std::unexpected("discovery.program_extensions is empty");
  if (!job.translator_params.is_object()) return std::unexpected("translator_params must be an object");
  return job;
}

namespace {

class Run {
 public:
  Run(const Job& job, DecisionProvider& provider) : job_(job), provider_(provider) {
    report_ = {{"report_version", 1},
               {"status", "failed"},
               {"output", job.output.string()},
               {"programs", json::array()},
               {"decisions", json::array()},
               {"resolutions",
                {{"provider", provider.name()},
                 {"applied", json::array()},
                 {"rejected", json::array()},
                 {"held", json::array()}}},
               {"settings", json::array()},
               {"files_written", json::array()},
               {"errors", json::array()}};
  }

  RunResult execute(const WorkerSpec& spec) {
    auto discovery = discover(job_.inputs, job_.source_root, job_.discovery);
    if (!discovery) return fail("discovery", discovery.error());
    record_discovery(*discovery);

    auto worker = WorkerClient::start(spec);
    if (!worker) return fail_worker(worker.error());
    report_["translator"] = worker->ready_info();

    auto status = drive(*worker, *discovery);
    if (auto down = worker->shutdown(std::chrono::seconds(10)); !down)
      log(LogLevel::warn, "worker shutdown: {}", down.error().message);
    return finish(status);
  }

  RunResult fail(std::string_view stage, std::string_view message) {
    report_["errors"].push_back({{"stage", stage}, {"message", message}});
    log(LogLevel::error, "{}: {}", stage, message);
    return finish(RunResult::Status::failed);
  }

  RunResult fail_worker(const WorkerError& e, std::string_view stage = "worker") {
    report_["errors"].push_back(
        {{"stage", stage}, {"kind", to_string(e.kind)}, {"message", e.message}, {"stderr_tail", e.stderr_tail}});
    log(LogLevel::error, "{} {}: {}", stage, to_string(e.kind), e.message);
    return finish(RunResult::Status::failed);
  }

 private:

  void record_discovery(const Discovery& d) {
    json programs = json::array(), copybooks = json::array();
    for (const auto& f : d.files) (f.role == DiscoveredFile::Role::program ? programs : copybooks).push_back(f.relative);
    report_["source_root"] = d.source_root.string();
    report_["discovery"] = {{"programs", programs}, {"copybooks", copybooks}, {"ignored", d.ignored}};
    log(LogLevel::info, "discovered {} program(s), {} copybook(s) under {}", programs.size(), copybooks.size(),
        d.source_root.string());
  }

  // A protocol-level error response from the worker ends the run.
  std::optional<RunResult::Status> check(const std::expected<protocol::Response, WorkerError>& r, std::string_view stage) {
    if (!r) {
      fail_worker(r.error());
      return RunResult::Status::failed;
    }
    if (r->status == protocol::Status::error) {
      report_["errors"].push_back({{"stage", stage},
                                   {"code", r->error->code},
                                   {"message", r->error->message},
                                   {"details", r->error->details}});
      log(LogLevel::error, "{}: {} ({})", stage, r->error->message, r->error->code);
      return RunResult::Status::failed;
    }
    return std::nullopt;
  }

  RunResult::Status drive(WorkerClient& worker, const Discovery& d) {
    auto on_event = [](const protocol::Event& e) {
      if (e.name == "progress")
        log(LogLevel::info, "{} {}/{}: {}", e.data.value("phase", ""), e.data.value("index", 0), e.data.value("total", 0),
            e.data.value("file", ""));
    };
    json params = job_.translator_params;
    params["source_root"] = d.source_root.string();
    params["output_root"] = job_.output.string();
    params["target_language"] = job_.target_language;
    params["mappings"] = job_.mappings;
    params["files"] = json::array();
    for (const auto& f : d.files)
      params["files"].push_back(
          {{"path", f.path.string()}, {"role", f.role == DiscoveredFile::Role::program ? "program" : "copybook"}});

    // A pass translates every program under the settings chosen so far. Setting decisions are
    // asked first; a chosen setting starts a new pass (a fresh session), so no statement decision
    // is asked under a setting that is about to change.
    json settings = json::object();
    std::string session;
    for (int pass = 1;; ++pass) {
      session = std::format("session-{}", pass);
      report_["session"] = session;
      programs_.clear();
      order_.clear();
      decisions_.clear();
      resolved_.clear();
      if (!settings.empty()) {
        params["settings"] = settings;
        params["setting_provenance"] = setting_provenance_;
      }

      auto init = worker.call("initialize", session, params, job_.request_timeout, on_event);
      if (auto s = check(init, "initialize")) return *s;
      // The project settings the worker translated under (opaque here; reported for reproducibility).
      if (init->result.contains("dialect")) report_["dialect"] = init->result["dialect"];

      // One request per program keeps the interface ready for scheduling translation in parallel.
      for (const auto& f : d.files) {
        if (f.role != DiscoveredFile::Role::program) continue;
        auto r = worker.call("translate", session, {{"files", {f.relative}}}, job_.request_timeout, on_event);
        if (auto s = check(r, "translate")) return *s;
        for (const auto& p : r->result.value("files", json::array())) programs_[p.value("file", "")] = p;
        for (const auto& raw : r->result.value("decisions", json::array())) {
          if (auto s = add_decision(raw)) return *s;
        }
      }

      json chosen = json::object();
      size_t held_before = report_["resolutions"]["held"].size();
      if (auto s = choose_settings(worker, session, chosen)) return *s;
      if (chosen.empty()) break;
      if (pass >= kMaxSettingPasses) {
        log(LogLevel::warn, "stopped choosing settings after {} passes", pass);
        break;
      }
      // Answers held in a pass that is translated again are asked again in the next one.
      auto& held = report_["resolutions"]["held"];
      held.erase(held.begin() + static_cast<std::ptrdiff_t>(held_before), held.end());
      for (auto& [field, value] : chosen.items()) settings[field] = value;
      log(LogLevel::info, "translating again with setting(s) {}", chosen.dump());
    }

    if (auto s = resolve_rounds(worker, session)) return *s;

    auto fin = worker.call("finalize", session, json::object(), job_.request_timeout, on_event);
    if (auto s = check(fin, "finalize")) return *s;
    report_["files_written"] = fin->result.value("files_written", json::array());
    for (const auto& p : fin->result.value("programs", json::array())) programs_[p.value("file", "")] = p;
    if (fin->result.contains("blocking")) report_["blocking"] = fin->result["blocking"];
    // Opaque here: parts of the source the worker replaced with library calls (not equivalence-checked).
    if (fin->result.contains("libraries_used")) report_["libraries_used"] = fin->result["libraries_used"];
    if (fin->result.contains("equivalence")) report_["equivalence"] = fin->result["equivalence"];
    // finalize reports the authoritative final state of every pending decision.
    for (const auto& p : fin->result.value("pending", json::array()))
      if (auto it = decisions_.find(p.value("id", "")); it != decisions_.end()) it->second.raw = p;
    return fin->status == protocol::Status::ok ? RunResult::Status::ok : RunResult::Status::incomplete;
  }

  std::optional<RunResult::Status> add_decision(const json& raw) {
    auto d = parse_decision_request(raw);
    if (!d) {
      report_["errors"].push_back({{"stage", "decision"}, {"message", d.error()}});
      return RunResult::Status::failed;
    }
    order_.push_back(d->id);
    decisions_.insert_or_assign(d->id, std::move(*d));
    return std::nullopt;
  }

  static bool is_setting(const DecisionRequest& d) { return d.raw.contains("setting"); }

  // Asks the provider about this pass's setting decisions. A passed, valid answer adds
  // `field: value` to `chosen` and is reported under `settings`; it is never sent to resolve.
  // A setting is project-wide: answers that disagree about one field (programs whose code
  // suggests different values) are all held as conflicting_settings.
  std::optional<RunResult::Status> choose_settings(WorkerClient& worker, const std::string& session, json& chosen) {
    std::vector<DecisionRequest> batch;
    std::vector<DecisionResponse> answers;
    for (const auto& id : order_) {
      const auto& d = decisions_.at(id);
      if (!is_setting(d)) continue;
      if (auto cached = cache_.lookup(d))
        answers.push_back(*cached);
      else
        batch.push_back(d);
    }
    if (batch.empty() && answers.empty()) return std::nullopt;
    if (!batch.empty()) {
      log(LogLevel::info, "asking provider '{}' about {} setting(s)", provider_.name(), batch.size());
      for (auto& a : provider_.decide(batch)) answers.push_back(std::move(a));
      if (auto f = provider_.failure()) return fail_provider(*f);
    }
    struct Pick {
      DecisionResponse answer;
      const DecisionRequest* decision;
      json value;
    };
    std::map<std::string, std::vector<Pick>> picks;  // by field
    for (auto& a : answers) {
      auto it = decisions_.find(a.decision_id);
      if (it == decisions_.end() || !is_setting(it->second)) {
        reject(a, "", "unknown setting decision");
        continue;
      }
      const DecisionRequest& d = it->second;
      std::string held_reason;
      if (a.gate == kGateNeedContext) {
        if (auto s = expand(worker, session, d, a, held_reason)) return s;
      }
      if (!a.candidate_id.empty()) {
        if (auto ok = validate(d, a); !ok) {
          reject(a, d.importance, ok.error());
          continue;
        }
      }
      if (!a.passed()) {
        hold(a, d.importance, held_reason);
        continue;
      }
      const json& setting = d.raw["setting"];
      const json& values = setting.value("values", json::object());
      if (!values.contains(a.candidate_id)) {
        reject(a, d.importance, "setting decision has no value for " + a.candidate_id);
        continue;
      }
      picks[setting.value("field", "")].push_back({a, &d, values[a.candidate_id]});
    }
    for (const auto& [field, group] : picks) {
      bool agree = std::ranges::all_of(group, [&](const Pick& p) { return p.value == group.front().value; });
      for (const auto& p : group) {
        if (!agree) {
          hold(p.answer, p.decision->importance, "conflicting_settings");
          continue;
        }
        cache_.store(*p.decision, p.answer);
        report_["settings"].push_back({{"field", field},
                                       {"value", p.value},
                                       {"decision", p.decision->raw},
                                       {"resolution", p.answer.to_report(p.decision->importance)}});
      }
      if (agree) {
        chosen[field] = group.front().value;
        // Who chose it, for the worker to note in the generated code (the lowest confidence of the picks).
        const DecisionResponse& a = group.front().answer;
        json provenance = {{"provider", a.provider}};
        for (const auto& pick : group)
          if (pick.answer.confidence && (!provenance.contains("confidence") || *pick.answer.confidence < provenance["confidence"].get<double>()))
            provenance["confidence"] = *pick.answer.confidence;
        setting_provenance_[field] = provenance;
      }
    }
    return std::nullopt;
  }

  // Asks the provider, opens context it asks for, and sends only gated, validated answers to the
  // worker. Gated-out answers are held (never cached); invalid ones are rejected. Setting
  // decisions were asked by choose_settings and stay pending here.
  std::optional<RunResult::Status> resolve_rounds(WorkerClient& worker, const std::string& session) {
    std::set<std::string> asked;
    for (int round = 0; round < job_.max_decision_rounds; ++round) {
      std::vector<DecisionRequest> batch;
      std::vector<DecisionResponse> answers;
      for (const auto& id : order_) {
        const auto& d = decisions_.at(id);
        if (resolved_.contains(id) || asked.contains(id) || is_setting(d)) continue;
        asked.insert(id);
        if (auto cached = cache_.lookup(d))
          answers.push_back(*cached);
        else
          batch.push_back(d);
      }
      if (batch.empty() && answers.empty()) return std::nullopt;
      if (!batch.empty()) {
        log(LogLevel::info, "asking provider '{}' about {} decision(s)", provider_.name(), batch.size());
        for (auto& a : provider_.decide(batch)) answers.push_back(std::move(a));
        if (auto f = provider_.failure()) return fail_provider(*f);
      }
      json resolutions = json::array();
      std::vector<DecisionResponse> sent;
      for (auto& a : answers) {
        auto it = decisions_.find(a.decision_id);
        if (it == decisions_.end()) {
          reject(a, "", "unknown decision");
          continue;
        }
        const DecisionRequest& d = it->second;
        std::string held_reason;
        if (a.gate == kGateNeedContext) {
          auto s = expand(worker, session, d, a, held_reason);
          if (s) return s;
        }
        if (!a.candidate_id.empty()) {
          if (auto ok = validate(d, a); !ok) {
            reject(a, d.importance, ok.error());
            continue;
          }
        }
        if (!a.passed()) {
          hold(a, d.importance, held_reason);
          continue;
        }
        resolutions.push_back(a.to_resolution());
        sent.push_back(a);
      }
      if (resolutions.empty()) continue;
      auto r = worker.call("resolve", session, {{"resolutions", resolutions}}, job_.request_timeout);
      if (auto s = check(r, "resolve")) return s;
      for (const auto& id : r->result.value("applied", json::array())) {
        std::string decision_id = id.get<std::string>();
        resolved_.insert(decision_id);
        for (const auto& a : sent)
          if (a.decision_id == decision_id) {
            const DecisionRequest& d = decisions_.at(decision_id);
            cache_.store(d, a);
            report_["resolutions"]["applied"].push_back(a.to_report(d.importance));
          }
      }
      for (const auto& rej : r->result.value("rejected", json::array())) {
        json entry = rej;
        entry["stage"] = "worker";
        report_["resolutions"]["rejected"].push_back(entry);
      }
      for (const auto& raw : r->result.value("new_decisions", json::array()))
        if (auto s = add_decision(raw)) return s;
    }
    log(LogLevel::warn, "stopped after {} decision rounds", job_.max_decision_rounds);
    return std::nullopt;
  }

  // Answers a need_context answer: expand_context, then ask again, up to the provider's cap.
  // `answer` becomes the last answer; `held_reason` says why it is still not a selection.
  std::optional<RunResult::Status> expand(WorkerClient& worker, const std::string& session, const DecisionRequest& d,
                                          DecisionResponse& answer, std::string& held_reason) {
    json refs = json::array();
    for (int n = 1; answer.gate == kGateNeedContext; ++n) {
      if (n > provider_.max_expansions()) {
        held_reason = "context_cap";
        return std::nullopt;
      }
      json params = {{"decision_id", d.id}, {"refs", answer.need_context}};
      if (auto chars = provider_.expansion_chars()) params["max_chars"] = *chars;
      auto r = worker.call("expand_context", session, params, job_.request_timeout);
      if (!r) {
        fail_worker(r.error());
        return RunResult::Status::failed;
      }
      if (r->status == protocol::Status::error) {
        held_reason = std::format("expand_context: {}", r->error->message);
        return std::nullopt;
      }
      for (const auto& node : r->result.value("refs", json::array())) refs.push_back(node);
      json extra = {{"decision_id", d.id},
                    {"expansions", n},
                    {"expansions_left", provider_.max_expansions() - n},
                    {"refs", refs}};
      log(LogLevel::info, "asking provider '{}' again about {} with {} context node(s)", provider_.name(), d.id,
          refs.size());
      auto again = provider_.decide_again(d, extra);
      if (auto f = provider_.failure()) return fail_provider(*f);
      if (!again) {
        held_reason = "no_answer";
        return std::nullopt;
      }
      answer = std::move(*again);
      answer.decision_id = d.id;
    }
    return std::nullopt;
  }

  std::optional<RunResult::Status> fail_provider(const WorkerError& e) {
    fail_worker(e, "provider");
    return RunResult::Status::failed;
  }

  void reject(const DecisionResponse& a, const std::string& importance, const std::string& message) {
    json entry = a.to_report(importance);
    entry["stage"] = "orchestrator";
    entry["message"] = message;
    report_["resolutions"]["rejected"].push_back(entry);
  }

  void hold(const DecisionResponse& a, const std::string& importance, const std::string& reason) {
    json entry = a.to_report(importance);
    if (!reason.empty()) entry["held_reason"] = reason;
    else if (!a.held_reason.empty()) entry["held_reason"] = a.held_reason;
    report_["resolutions"]["held"].push_back(entry);
  }

  RunResult finish(RunResult::Status status) {
    for (const auto& id : order_) {
      json d = decisions_.at(id).raw;
      if (resolved_.contains(id)) d["state"] = "resolved";
      report_["decisions"].push_back(d);
    }
    for (auto& [file, p] : programs_) report_["programs"].push_back(p);
    report_["status"] = to_string(status);
    if (job_.write_report) {
      std::error_code ec;
      fs::create_directories(job_.output, ec);
      std::ofstream out(job_.output / "transpile-report.json");
      if (out) out << report_.dump(2) << '\n';
      else log(LogLevel::warn, "could not write {}", (job_.output / "transpile-report.json").string());
      std::ofstream review(job_.output / "review.md");
      if (review) review << review_table(report_);
      else log(LogLevel::warn, "could not write {}", (job_.output / "review.md").string());
    }
    return {status, report_};
  }

  // Each chosen setting can start one more translation pass.
  static constexpr int kMaxSettingPasses = 8;

  const Job& job_;
  DecisionProvider& provider_;
  json report_;
  // Field -> {provider, confidence} of each chosen setting, sent with initialize.settings.
  json setting_provenance_ = json::object();
  std::map<std::string, json> programs_;
  std::vector<std::string> order_;
  std::map<std::string, DecisionRequest> decisions_;
  std::set<std::string> resolved_;
  DecisionCache cache_;
};

}  // namespace

RunResult run_job(const Job& job, const WorkerSpec& worker, DecisionProvider& provider) {
  return Run(job, provider).execute(worker);
}

RunResult run_job(const Job& job) {
  auto provider = make_provider(job.provider);
  DeterministicProvider fallback;
  if (!provider) {
    if (provider.error().process) return Run(job, fallback).fail_worker(*provider.error().process, "provider");
    return Run(job, fallback).fail("provider", provider.error().message);
  }
  auto registry = TranslatorRegistry::scan(job.translators_dir);
  if (!registry) return Run(job, **provider).fail("translator", registry.error());
  auto manifest = registry->find(job.translator, job.source_language, job.target_language);
  if (!manifest) return Run(job, **provider).fail("translator", manifest.error());
  log(LogLevel::info, "translator {} ({} -> {})", manifest->name, manifest->source_language, manifest->target_language);
  return Run(job, **provider).execute(manifest->worker_spec());
}

}  // namespace transpiler

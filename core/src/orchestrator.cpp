#include "transpiler/orchestrator.hpp"

#include <format>
#include <fstream>
#include <map>
#include <set>

#include "transpiler/log.hpp"
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
               {"resolutions", {{"provider", provider.name()}, {"applied", json::array()}, {"rejected", json::array()}}},
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

 private:
  RunResult fail_worker(const WorkerError& e) {
    report_["errors"].push_back(
        {{"stage", "worker"}, {"kind", to_string(e.kind)}, {"message", e.message}, {"stderr_tail", e.stderr_tail}});
    log(LogLevel::error, "worker {}: {}", to_string(e.kind), e.message);
    return finish(RunResult::Status::failed);
  }

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
    const std::string session = "session-1";
    report_["session"] = session;

    json params = job_.translator_params;
    params["source_root"] = d.source_root.string();
    params["output_root"] = job_.output.string();
    params["target_language"] = job_.target_language;
    params["mappings"] = job_.mappings;
    params["files"] = json::array();
    for (const auto& f : d.files)
      params["files"].push_back(
          {{"path", f.path.string()}, {"role", f.role == DiscoveredFile::Role::program ? "program" : "copybook"}});

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

    if (auto s = resolve_rounds(worker, session)) return *s;

    auto fin = worker.call("finalize", session, json::object(), job_.request_timeout, on_event);
    if (auto s = check(fin, "finalize")) return *s;
    report_["files_written"] = fin->result.value("files_written", json::array());
    for (const auto& p : fin->result.value("programs", json::array())) programs_[p.value("file", "")] = p;
    if (fin->result.contains("blocking")) report_["blocking"] = fin->result["blocking"];
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

  std::optional<RunResult::Status> resolve_rounds(WorkerClient& worker, const std::string& session) {
    std::set<std::string> asked;
    for (int round = 0; round < job_.max_decision_rounds; ++round) {
      std::vector<DecisionRequest> batch;
      std::vector<DecisionResponse> answers;
      for (const auto& id : order_) {
        const auto& d = decisions_.at(id);
        if (resolved_.contains(id) || asked.contains(id)) continue;
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
      }
      json resolutions = json::array();
      for (const auto& a : answers) {
        auto it = decisions_.find(a.decision_id);
        auto ok = it == decisions_.end() ? std::expected<void, std::string>(std::unexpected("unknown decision"))
                                         : validate(it->second, a);
        if (!ok) {
          report_["resolutions"]["rejected"].push_back(
              {{"decision_id", a.decision_id}, {"stage", "orchestrator"}, {"message", ok.error()}});
          continue;
        }
        resolutions.push_back(a.to_resolution());
      }
      if (resolutions.empty()) continue;
      auto r = worker.call("resolve", session, {{"resolutions", resolutions}}, job_.request_timeout);
      if (auto s = check(r, "resolve")) return s;
      for (const auto& id : r->result.value("applied", json::array())) {
        std::string decision_id = id.get<std::string>();
        resolved_.insert(decision_id);
        for (const auto& a : answers)
          if (a.decision_id == decision_id) {
            cache_.store(decisions_.at(decision_id), a);
            report_["resolutions"]["applied"].push_back(a.to_resolution());
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
    }
    return {status, report_};
  }

  const Job& job_;
  DecisionProvider& provider_;
  json report_;
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
  if (!provider) return Run(job, fallback).fail("provider", provider.error());
  auto registry = TranslatorRegistry::scan(job.translators_dir);
  if (!registry) return Run(job, **provider).fail("translator", registry.error());
  auto manifest = registry->find(job.translator, job.source_language, job.target_language);
  if (!manifest) return Run(job, **provider).fail("translator", manifest.error());
  log(LogLevel::info, "translator {} ({} -> {})", manifest->name, manifest->source_language, manifest->target_language);
  return Run(job, **provider).execute(manifest->worker_spec());
}

}  // namespace transpiler

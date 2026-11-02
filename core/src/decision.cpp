#include "transpiler/decision.hpp"

#include <algorithm>
#include <array>
#include <format>

#include "transpiler/log.hpp"

namespace transpiler {

namespace {
constexpr std::array<std::string_view, 4> kGates = {"passed", "below_floor", "abstained", "need_context"};
}  // namespace

std::expected<DecisionRequest, std::string> parse_decision_request(const json& j) {
  if (!j.is_object()) return std::unexpected("decision request is not an object");
  DecisionRequest r;
  try {
    r.id = j.at("id").get<std::string>();
    r.fingerprint = j.at("fingerprint").get<std::string>();
    r.reason = j.at("reason").get<std::string>();
    r.operation = j.at("operation").get<std::string>();
    r.importance = j.value("importance", std::string{});
    for (const auto& c : j.at("candidates"))
      r.candidates.push_back({c.at("id").get<std::string>(), c.value("description", std::string{})});
  } catch (const json::exception& e) {
    return std::unexpected(std::format("malformed decision request: {}", e.what()));
  }
  if (r.id.empty() || r.fingerprint.empty()) return std::unexpected("decision request without id or fingerprint");
  r.raw = j;
  return r;
}

json DecisionResponse::to_resolution() const {
  json j = {{"decision_id", decision_id},
            {"selection", {{"kind", "candidate"}, {"candidate_id", candidate_id}}},
            {"provider", provider}};
  if (!fingerprint.empty()) j["fingerprint"] = fingerprint;
  if (!rationale.empty()) j["rationale"] = rationale;
  // The worker writes who decided (and how sure it was) into the generated code.
  if (confidence) j["confidence"] = *confidence;
  return j;
}

json DecisionResponse::to_report(const std::string& importance) const {
  json j = to_resolution();
  if (candidate_id.empty()) j.erase("selection");
  if (!importance.empty()) j["importance"] = importance;
  if (!gate.empty()) j["gate"] = gate;
  if (confidence) j["confidence"] = *confidence;
  if (!probabilities.is_null()) j["probabilities"] = probabilities;
  if (!review_priority.is_null()) j["review_priority"] = review_priority;
  if (!need_context.empty()) j["need_context"] = need_context;
  return j;
}

std::expected<DecisionResponse, std::string> parse_decision_response(const json& j) {
  if (!j.is_object()) return std::unexpected("decision response is not an object");
  DecisionResponse r;
  r.decision_id = j.value("decision_id", std::string{});
  r.fingerprint = j.value("fingerprint", std::string{});
  r.provider = j.value("provider", std::string{});
  r.rationale = j.value("rationale", std::string{});
  if (j.contains("held_reason") && j["held_reason"].is_string()) r.held_reason = j["held_reason"].get<std::string>();
  if (j.contains("gate")) {
    if (!j["gate"].is_string() || std::ranges::find(kGates, j["gate"].get<std::string>()) == kGates.end())
      return std::unexpected(std::format("gate must be one of passed, below_floor, abstained, need_context, not {}",
                                         j["gate"].dump()));
    r.gate = j["gate"].get<std::string>();
  }
  if (j.contains("confidence")) {
    if (!j["confidence"].is_number() || j["confidence"].get<double>() < 0 || j["confidence"].get<double>() > 1)
      return std::unexpected("confidence must be a number in [0, 1]");
    r.confidence = j["confidence"].get<double>();
  }
  if (j.contains("probabilities")) {
    if (!j["probabilities"].is_object()) return std::unexpected("probabilities must be an object");
    r.probabilities = j["probabilities"];
  }
  if (j.contains("review_priority")) {
    if (!j["review_priority"].is_object()) return std::unexpected("review_priority must be an object");
    r.review_priority = j["review_priority"];
  }
  if (r.decision_id.empty() && r.fingerprint.empty())
    return std::unexpected("decision response needs a decision_id or a fingerprint");
  if (r.gate == kGateNeedContext) {
    const json& refs = j.value("need_context", json{});
    if (!refs.is_array() || refs.empty() || !std::ranges::all_of(refs, [](const json& x) { return x.is_string(); }))
      return std::unexpected("a need_context answer must list the refs to expand");
    if (j.contains("selection")) return std::unexpected("a need_context answer cannot select a candidate");
    r.need_context = refs.get<std::vector<std::string>>();
    return r;
  }
  // A gated-out answer may name no candidate (an abstention); a passed one must select one.
  if (!j.contains("selection") && !r.passed()) return r;
  const json& sel = j.value("selection", json{});
  if (!sel.is_object() || sel.value("kind", std::string{}) != "candidate" || !sel.contains("candidate_id") ||
      !sel["candidate_id"].is_string())
    return std::unexpected("selection must be {\"kind\": \"candidate\", \"candidate_id\": ...}");
  r.candidate_id = sel["candidate_id"].get<std::string>();
  return r;
}

std::expected<void, std::string> validate(const DecisionRequest& request, const DecisionResponse& response) {
  if (!response.decision_id.empty() && response.decision_id != request.id)
    return std::unexpected(std::format("answer for '{}' given to '{}'", response.decision_id, request.id));
  if (!response.fingerprint.empty() && response.fingerprint != request.fingerprint)
    return std::unexpected("fingerprint does not match the decision");
  if (request.candidates.empty()) return std::unexpected("decision has no candidates to select");
  if (std::ranges::none_of(request.candidates, [&](const auto& c) { return c.id == response.candidate_id; }))
    return std::unexpected(std::format("candidate '{}' is not offered", response.candidate_id));
  return {};
}

StaticProvider::StaticProvider(std::vector<DecisionResponse> answers) : answers_(std::move(answers)) {}

std::vector<DecisionResponse> StaticProvider::decide(std::span<const DecisionRequest> batch) {
  std::vector<DecisionResponse> out;
  for (const auto& request : batch) {
    auto it = std::ranges::find_if(answers_, [&](const DecisionResponse& a) {
      return (!a.decision_id.empty() && a.decision_id == request.id) ||
             (a.decision_id.empty() && a.fingerprint == request.fingerprint);
    });
    if (it == answers_.end()) continue;
    DecisionResponse answer = *it;
    answer.decision_id = request.id;
    if (answer.provider.empty()) answer.provider = name();
    out.push_back(std::move(answer));
  }
  return out;
}

PythonProvider::PythonProvider(WorkerClient client, Options options)
    : client_(std::move(client)), options_(std::move(options)) {
  name_ = client_.ready_info().value("provider", std::string{"python"});
}

std::expected<std::unique_ptr<PythonProvider>, WorkerError> PythonProvider::start(Options options) {
  options.spec.protocol = std::string(protocol::kProviderVersion);
  auto client = WorkerClient::start(options.spec);
  if (!client) return std::unexpected(client.error());
  log(LogLevel::info, "decision provider ready: {}", client->ready_info().dump());
  return std::unique_ptr<PythonProvider>(new PythonProvider(std::move(*client), std::move(options)));
}

PythonProvider::~PythonProvider() {
  if (!client_.alive()) return;
  if (auto down = client_.shutdown(std::chrono::seconds(10)); !down)
    log(LogLevel::warn, "provider shutdown: {}", down.error().message);
}

std::vector<DecisionResponse> PythonProvider::call(const json& params) {
  if (failure_) return {};
  auto r = client_.call("decide", std::nullopt, params, options_.request_timeout);
  if (!r) {
    failure_ = r.error();
    return {};
  }
  if (r->status == protocol::Status::error) {
    failure_ = client_.error(WorkerError::Kind::error_response,
                             std::format("provider error {}: {}", r->error->code, r->error->message));
    return {};
  }
  const json& list = r->result.value("answers", json{});
  if (!list.is_array()) {
    failure_ = client_.error(WorkerError::Kind::malformed, "decide result without an 'answers' array");
    return {};
  }
  std::vector<DecisionResponse> out;
  for (const auto& a : list) {
    auto parsed = parse_decision_response(a);
    if (!parsed) {
      failure_ = client_.error(WorkerError::Kind::malformed, std::format("malformed answer: {}", parsed.error()));
      return {};
    }
    if (parsed->provider.empty()) parsed->provider = name_;
    out.push_back(std::move(*parsed));
  }
  return out;
}

std::vector<DecisionResponse> PythonProvider::decide(std::span<const DecisionRequest> batch) {
  json decisions = json::array();
  for (const auto& d : batch) decisions.push_back(d.raw);
  return call({{"decisions", decisions}});
}

std::optional<DecisionResponse> PythonProvider::decide_again(const DecisionRequest& request, const json& extra_context) {
  for (auto& a : call({{"decisions", {request.raw}}, {"extra_context", {extra_context}}}))
    if (a.decision_id == request.id) return std::move(a);
  return std::nullopt;
}

std::optional<DecisionResponse> DecisionCache::lookup(const DecisionRequest& request) const {
  auto it = entries_.find(request.fingerprint);
  if (it == entries_.end()) return std::nullopt;
  DecisionResponse r = it->second;
  r.decision_id = request.id;
  r.provider = "cache";
  return r;
}

void DecisionCache::store(const DecisionRequest& request, const DecisionResponse& response) {
  entries_.insert_or_assign(request.fingerprint, response);
}

std::expected<std::unique_ptr<DecisionProvider>, ProviderError> make_provider(const json& config) {
  std::string kind = config.is_object() ? config.value("kind", std::string{"deterministic"}) : "deterministic";
  if (kind == "deterministic") return std::make_unique<DeterministicProvider>();
  if (kind == "static") {
    std::vector<DecisionResponse> answers;
    for (const auto& d : config.value("decisions", json::array())) {
      auto r = parse_decision_response(d);
      if (!r) return std::unexpected(ProviderError{std::format("static provider: {}", r.error()), {}});
      answers.push_back(std::move(*r));
    }
    return std::make_unique<StaticProvider>(std::move(answers));
  }
  if (kind == "python") {
    PythonProvider::Options options;
    try {
      options.spec.command = config.at("command").get<std::vector<std::string>>();
      options.spec.startup_timeout = std::chrono::seconds(config.value("startup_timeout_seconds", 60));
      options.request_timeout = std::chrono::seconds(config.value("request_timeout_seconds", 300));
      const json& context = config.value("context", json::object());
      options.max_expansions = context.value("max_expansions", 0);
      if (context.contains("max_chars")) options.expansion_chars = context["max_chars"].get<int>();
    } catch (const json::exception& e) {
      return std::unexpected(ProviderError{std::format("python provider: invalid configuration: {}", e.what()), {}});
    }
    if (options.spec.command.empty()) return std::unexpected(ProviderError{"python provider: 'command' is empty", {}});
    if (options.max_expansions < 0) return std::unexpected(ProviderError{"python provider: max_expansions < 0", {}});
    options.config = config;
    auto provider = PythonProvider::start(std::move(options));
    if (!provider)
      return std::unexpected(ProviderError{std::format("python provider {}: {}", to_string(provider.error().kind),
                                                       provider.error().message),
                                           provider.error()});
    return std::unique_ptr<DecisionProvider>(std::move(*provider));
  }
  return std::unexpected(
      ProviderError{std::format("unknown decision provider '{}' (supported: deterministic, static, python)", kind), {}});
}

}  // namespace transpiler

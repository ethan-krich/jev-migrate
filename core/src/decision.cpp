#include "transpiler/decision.hpp"

#include <algorithm>
#include <format>

namespace transpiler {

std::expected<DecisionRequest, std::string> parse_decision_request(const json& j) {
  if (!j.is_object()) return std::unexpected("decision request is not an object");
  DecisionRequest r;
  try {
    r.id = j.at("id").get<std::string>();
    r.fingerprint = j.at("fingerprint").get<std::string>();
    r.reason = j.at("reason").get<std::string>();
    r.operation = j.at("operation").get<std::string>();
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
  return j;
}

std::expected<DecisionResponse, std::string> parse_decision_response(const json& j) {
  if (!j.is_object()) return std::unexpected("decision response is not an object");
  DecisionResponse r;
  r.decision_id = j.value("decision_id", std::string{});
  r.fingerprint = j.value("fingerprint", std::string{});
  r.provider = j.value("provider", std::string{});
  r.rationale = j.value("rationale", std::string{});
  const json& sel = j.value("selection", json{});
  if (!sel.is_object() || sel.value("kind", std::string{}) != "candidate" || !sel.contains("candidate_id") ||
      !sel["candidate_id"].is_string())
    return std::unexpected("selection must be {\"kind\": \"candidate\", \"candidate_id\": ...}");
  r.candidate_id = sel["candidate_id"].get<std::string>();
  if (r.decision_id.empty() && r.fingerprint.empty())
    return std::unexpected("decision response needs a decision_id or a fingerprint");
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

std::expected<std::unique_ptr<DecisionProvider>, std::string> make_provider(const json& config) {
  std::string kind = config.is_object() ? config.value("kind", std::string{"deterministic"}) : "deterministic";
  if (kind == "deterministic") return std::make_unique<DeterministicProvider>();
  if (kind == "static") {
    std::vector<DecisionResponse> answers;
    for (const auto& d : config.value("decisions", json::array())) {
      auto r = parse_decision_response(d);
      if (!r) return std::unexpected(std::format("static provider: {}", r.error()));
      answers.push_back(std::move(*r));
    }
    return std::make_unique<StaticProvider>(std::move(answers));
  }
  return std::unexpected(std::format("unknown decision provider '{}' (phase 1 supports: deterministic, static)", kind));
}

}  // namespace transpiler

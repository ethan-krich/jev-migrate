#pragma once

#include <expected>
#include <map>
#include <memory>
#include <optional>
#include <span>
#include <string>
#include <vector>

#include "transpiler/protocol.hpp"

// Decision routing (protocol/decision.schema.json). Workers raise structured decision requests
// for operations they cannot translate deterministically; providers select among the worker's
// validated candidates. Phase 1 ships only deterministic providers; a Jev-backed provider will
// implement the same interface out of process.
namespace transpiler {

using protocol::json;

struct DecisionCandidate {
  std::string id;
  std::string description;
};

struct DecisionRequest {
  std::string id;
  std::string fingerprint;
  std::string reason;
  std::string operation;
  std::vector<DecisionCandidate> candidates;
  json raw;  // the full request as sent by the worker, forwarded to providers unchanged
};

std::expected<DecisionRequest, std::string> parse_decision_request(const json& j);

struct DecisionResponse {
  std::string decision_id;
  std::string fingerprint;  // empty when the provider did not echo it
  std::string candidate_id;
  std::string provider;
  std::string rationale;

  json to_resolution() const;  // the `resolve` request element
};

std::expected<DecisionResponse, std::string> parse_decision_response(const json& j);

// Checks a provider's answer against the request before anything is sent to the worker.
std::expected<void, std::string> validate(const DecisionRequest& request, const DecisionResponse& response);

class DecisionProvider {
 public:
  virtual ~DecisionProvider() = default;
  virtual std::string name() const = 0;
  // Receives a batch of independent decisions; returns answers for any subset of them.
  virtual std::vector<DecisionResponse> decide(std::span<const DecisionRequest> batch) = 0;
};

// Reports every decision as unresolved (returns no answers).
class DeterministicProvider final : public DecisionProvider {
 public:
  std::string name() const override { return "deterministic"; }
  std::vector<DecisionResponse> decide(std::span<const DecisionRequest>) override { return {}; }
};

// Answers from a fixed list keyed by decision id or fingerprint: synthetic decisions for tests
// and for replaying previously approved resolutions. Never contacts anything external.
class StaticProvider final : public DecisionProvider {
 public:
  explicit StaticProvider(std::vector<DecisionResponse> answers);
  std::string name() const override { return "static"; }
  std::vector<DecisionResponse> decide(std::span<const DecisionRequest> batch) override;

 private:
  std::vector<DecisionResponse> answers_;
};

// Remembers accepted answers by fingerprint so identical decisions are not asked twice.
class DecisionCache {
 public:
  std::optional<DecisionResponse> lookup(const DecisionRequest& request) const;
  void store(const DecisionRequest& request, const DecisionResponse& response);
  size_t size() const { return entries_.size(); }

 private:
  std::map<std::string, DecisionResponse> entries_;
};

std::expected<std::unique_ptr<DecisionProvider>, std::string> make_provider(const json& config);

}  // namespace transpiler

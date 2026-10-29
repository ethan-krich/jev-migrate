#pragma once

#include <chrono>
#include <expected>
#include <map>
#include <memory>
#include <optional>
#include <span>
#include <string>
#include <vector>

#include "transpiler/protocol.hpp"
#include "transpiler/worker_client.hpp"

// Decision routing (protocol/decision.schema.json). Workers raise structured decision requests
// for operations they cannot translate deterministically; providers select among the worker's
// validated candidates. The deterministic providers run in process; a `python` provider (Jev)
// runs out of process over protocol/provider.schema.json.
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
  std::string importance;  // empty when the worker sends none
  std::vector<DecisionCandidate> candidates;
  json raw;  // the full request as sent by the worker, forwarded to providers unchanged
};

std::expected<DecisionRequest, std::string> parse_decision_request(const json& j);

// Gate values of an answer (opaque to the core except `passed` and `need_context`).
inline constexpr std::string_view kGatePassed = "passed";
inline constexpr std::string_view kGateNeedContext = "need_context";

struct DecisionResponse {
  std::string decision_id;
  std::string fingerprint;  // empty when the provider did not echo it
  std::string candidate_id;  // empty for a need_context answer
  std::string provider;
  std::string rationale;
  // Set by gated providers. An answer without a gate (static, replayed) counts as passed.
  std::string gate;
  std::optional<double> confidence;
  json probabilities;    // null when absent
  json review_priority;  // null when absent; advisory, never interpreted here
  std::vector<std::string> need_context;  // refs to expand (gate need_context)

  bool passed() const { return gate.empty() || gate == kGatePassed; }
  json to_resolution() const;  // the `resolve` request element
  // A report entry: the resolution plus the gate fields and the decision's importance.
  json to_report(const std::string& importance) const;
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
  // Asks again about one decision after a need_context answer, with the nodes expand_context
  // returned (`extra_context`: {decision_id, expansions, expansions_left, refs}).
  virtual std::optional<DecisionResponse> decide_again(const DecisionRequest&, const json& /*extra_context*/) {
    return std::nullopt;
  }
  // How many expand_context round-trips one decision may take, and their text budget.
  virtual int max_expansions() const { return 0; }
  virtual std::optional<int> expansion_chars() const { return std::nullopt; }
  // A transport failure of an out-of-process provider; the run fails when it is set.
  virtual std::optional<WorkerError> failure() const { return std::nullopt; }
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

// A persistent provider process speaking transpiler-provider/1 (job `provider.kind: "python"`),
// e.g. `python -m transpiler.providers.serve`. It only answers; the orchestrator still gates,
// validates and resolves every answer.
class PythonProvider final : public DecisionProvider {
 public:
  struct Options {
    WorkerSpec spec;
    std::chrono::milliseconds request_timeout{300'000};
    int max_expansions = 0;
    std::optional<int> expansion_chars;
    json config = json::object();  // the job's provider entry
  };

  static std::expected<std::unique_ptr<PythonProvider>, WorkerError> start(Options options);
  ~PythonProvider() override;

  std::string name() const override { return name_; }
  std::vector<DecisionResponse> decide(std::span<const DecisionRequest> batch) override;
  std::optional<DecisionResponse> decide_again(const DecisionRequest& request, const json& extra_context) override;
  int max_expansions() const override { return options_.max_expansions; }
  std::optional<int> expansion_chars() const override { return options_.expansion_chars; }
  std::optional<WorkerError> failure() const override { return failure_; }

 private:
  PythonProvider(WorkerClient client, Options options);
  std::vector<DecisionResponse> call(const json& params);

  WorkerClient client_;
  Options options_;
  std::string name_;
  std::optional<WorkerError> failure_;
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

struct ProviderError {
  std::string message;
  std::optional<WorkerError> process;  // set when a `python` provider failed to start
};

// The provider a job's `provider` entry describes. A `python` provider is started here.
std::expected<std::unique_ptr<DecisionProvider>, ProviderError> make_provider(const json& config);

}  // namespace transpiler

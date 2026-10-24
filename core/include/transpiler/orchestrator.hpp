#pragma once

#include <chrono>
#include <expected>
#include <filesystem>
#include <optional>
#include <string>
#include <vector>

#include "transpiler/decision.hpp"
#include "transpiler/discovery.hpp"
#include "transpiler/protocol.hpp"
#include "transpiler/translator_registry.hpp"

namespace transpiler {

// A translation job, normally written by the Python API (see docs/architecture.md).
struct Job {
  std::optional<std::string> translator;  // explicit translator name, else chosen by languages
  std::string source_language;
  std::string target_language;
  std::vector<std::string> inputs;
  std::optional<std::filesystem::path> source_root;
  std::filesystem::path output;
  std::filesystem::path translators_dir;
  DiscoveryRules discovery;
  json translator_params = json::object();  // opaque: merged into `initialize` params
  json mappings;                            // opaque: validated by the worker
  json provider = json::object();
  std::chrono::milliseconds request_timeout{300'000};
  int max_decision_rounds = 8;
  bool write_report = true;
};

std::expected<Job, std::string> parse_job(const json& j);

struct RunResult {
  enum class Status { ok, incomplete, failed } status;
  json report;
};

std::string_view to_string(RunResult::Status status);

// Runs one job end to end: discover, start the worker, initialize, translate every program,
// route decisions through the provider, resolve, finalize, shut down.
RunResult run_job(const Job& job);

// The same, with an explicit worker and provider (used by tests with a fake worker).
RunResult run_job(const Job& job, const WorkerSpec& worker, DecisionProvider& provider);

}  // namespace transpiler

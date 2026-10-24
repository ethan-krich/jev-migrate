// transpiler-core: language-independent orchestration CLI.
//
//   transpiler-core run --job job.json [--verbose]   run a job; the report JSON goes to stdout
//   transpiler-core translators --dir translators/    list registered translators
//
// Exit status: 0 ok, 2 incomplete (unresolved decisions), 1 failed, 64 usage error.
#include <cstdio>
#include <algorithm>
#include <optional>
#include <fstream>
#include <iostream>
#include <print>
#include <string>
#include <vector>

#include "transpiler/log.hpp"
#include "transpiler/orchestrator.hpp"
#include "transpiler/translator_registry.hpp"

namespace {

int usage() {
  std::println(stderr, "usage: transpiler-core run --job <job.json> [--verbose]\n"
                       "       transpiler-core translators --dir <translators-dir>");
  return 64;
}

std::optional<std::string> option(const std::vector<std::string>& args, std::string_view name) {
  for (size_t i = 0; i + 1 < args.size(); ++i)
    if (args[i] == name) return args[i + 1];
  return std::nullopt;
}

}  // namespace

int main(int argc, char** argv) {
  std::vector<std::string> args(argv + 1, argv + argc);
  if (args.empty()) return usage();
  if (std::ranges::find(args, "--verbose") != args.end()) transpiler::set_log_level(transpiler::LogLevel::debug);

  if (args[0] == "translators") {
    auto dir = option(args, "--dir");
    if (!dir) return usage();
    auto registry = transpiler::TranslatorRegistry::scan(*dir);
    if (!registry) {
      std::println(stderr, "{}", registry.error());
      return 1;
    }
    for (const auto& m : registry->all())
      std::println("{}\t{} -> {}\t{}", m.name, m.source_language, m.target_language, m.directory.string());
    return 0;
  }

  if (args[0] == "run") {
    auto path = option(args, "--job");
    if (!path) return usage();
    std::ifstream in(*path);
    if (!in) {
      std::println(stderr, "cannot read job file {}", *path);
      return 64;
    }
    auto parsed = transpiler::json::parse(in, nullptr, false);
    auto job = transpiler::parse_job(parsed);
    if (!job) {
      std::println(stderr, "{}", job.error());
      return 64;
    }
    auto result = transpiler::run_job(*job);
    std::cout << result.report.dump(2) << std::endl;
    switch (result.status) {
      case transpiler::RunResult::Status::ok: return 0;
      case transpiler::RunResult::Status::incomplete: return 2;
      case transpiler::RunResult::Status::failed: return 1;
    }
  }
  return usage();
}

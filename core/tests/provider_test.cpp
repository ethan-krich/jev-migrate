#include <gtest/gtest.h>

#include <fstream>
#include <sstream>
#include <unistd.h>

#include "transpiler/orchestrator.hpp"

using namespace transpiler;
namespace fs = std::filesystem;
using namespace std::chrono_literals;

namespace {
PythonProvider::Options fake(const char* mode, int max_expansions = 2, std::chrono::milliseconds timeout = 5s) {
  PythonProvider::Options o;
  o.spec = WorkerSpec{{FAKE_PROVIDER_PATH, mode}, 5s};
  o.request_timeout = timeout;
  o.max_expansions = max_expansions;
  o.expansion_chars = 500;
  return o;
}
}  // namespace

class ProviderTest : public testing::Test {
 protected:
  void SetUp() override {
    root_ = fs::temp_directory_path() / ("prov-" + std::to_string(::getpid()) + "-" +
                                         testing::UnitTest::GetInstance()->current_test_info()->name());
    fs::remove_all(root_);
    fs::create_directories(root_ / "src");
    std::ofstream(root_ / "src/A.cbl") << "x";
    auto job = parse_job({{"job_version", 1},
                          {"source_language", "cobol"},
                          {"target_language", "java"},
                          {"inputs", {(root_ / "src").string()}},
                          {"output", (root_ / "out").string()},
                          {"translators_dir", root_.string()},
                          {"discovery", {{"program_extensions", {".cbl"}}}},
                          {"mappings", {{"version", 1}, {"rules", json::array()}}},
                          {"request_timeout_seconds", 5}});
    ASSERT_TRUE(job.has_value()) << job.error();
    job_ = *job;
  }
  void TearDown() override { fs::remove_all(root_); }

  RunResult run(const char* mode, int max_expansions = 2, std::chrono::milliseconds timeout = 5s) {
    auto provider = PythonProvider::start(fake(mode, max_expansions, timeout));
    EXPECT_TRUE(provider.has_value()) << provider.error().message;
    return run_job(job_, WorkerSpec{{FAKE_WORKER_PATH, "normal"}, 5s}, **provider);
  }

  std::string review() const {
    std::ifstream in(root_ / "out/review.md");
    std::stringstream s;
    s << in.rdbuf();
    return s.str();
  }

  fs::path root_;
  Job job_;
};

TEST_F(ProviderTest, PassedAnswerIsAppliedWithItsGateFields) {
  auto r = run("passed");
  EXPECT_EQ(r.status, RunResult::Status::ok) << r.report.dump(2);
  EXPECT_EQ(r.report["resolutions"]["provider"], "fake");
  ASSERT_EQ(r.report["resolutions"]["applied"].size(), 1u);
  const auto& applied = r.report["resolutions"]["applied"][0];
  EXPECT_EQ(applied["gate"], "passed");
  EXPECT_DOUBLE_EQ(applied["confidence"].get<double>(), 0.97);
  EXPECT_EQ(applied["importance"], "high");
  EXPECT_EQ(applied["review_priority"]["label"], "business");
  EXPECT_EQ(applied["provider"], "fake");
  EXPECT_EQ(r.report["files_written"].size(), 1u);
  EXPECT_NE(review().find("| decision-1 | resolved | high | 3 business | applied | passed | 0.97 | builtin:display |"),
            std::string::npos)
      << review();
}

TEST_F(ProviderTest, AnswerBelowItsFloorIsHeldAndNothingIsWritten) {
  auto r = run("below_floor");
  EXPECT_EQ(r.status, RunResult::Status::incomplete);
  EXPECT_TRUE(r.report["resolutions"]["applied"].empty());
  ASSERT_EQ(r.report["resolutions"]["held"].size(), 1u);
  EXPECT_EQ(r.report["resolutions"]["held"][0]["gate"], "below_floor");
  EXPECT_EQ(r.report["resolutions"]["held"][0]["importance"], "high");
  EXPECT_TRUE(r.report["files_written"].empty());
  EXPECT_NE(review().find("| decision-1 | pending | high | 3 business | held | below_floor | 0.60 |"), std::string::npos)
      << review();
}

TEST_F(ProviderTest, AbstentionIsHeld) {
  auto r = run("abstained");
  EXPECT_EQ(r.status, RunResult::Status::incomplete);
  ASSERT_EQ(r.report["resolutions"]["held"].size(), 1u);
  EXPECT_EQ(r.report["resolutions"]["held"][0]["gate"], "abstained");
  EXPECT_FALSE(r.report["resolutions"]["held"][0].contains("selection"));
}

TEST_F(ProviderTest, NeedContextIsExpandedAndAskedAgain) {
  auto r = run("need_context");
  EXPECT_EQ(r.status, RunResult::Status::ok) << r.report.dump(2);
  ASSERT_EQ(r.report["resolutions"]["applied"].size(), 1u);
  EXPECT_EQ(r.report["resolutions"]["applied"][0]["gate"], "passed");
}

TEST_F(ProviderTest, ExpansionCapHoldsTheDecision) {
  auto r = run("context_forever", 2);
  EXPECT_EQ(r.status, RunResult::Status::incomplete);
  ASSERT_EQ(r.report["resolutions"]["held"].size(), 1u);
  const auto& held = r.report["resolutions"]["held"][0];
  EXPECT_EQ(held["gate"], "need_context");
  EXPECT_EQ(held["held_reason"], "context_cap");
  EXPECT_EQ(held["need_context"], json({"ref-a"}));
  EXPECT_NE(review().find("held (context_cap)"), std::string::npos) << review();
}

TEST_F(ProviderTest, WithoutExpansionsNeedContextIsHeldAtOnce) {
  auto r = run("need_context", 0);
  EXPECT_EQ(r.status, RunResult::Status::incomplete);
  EXPECT_EQ(r.report["resolutions"]["held"][0]["held_reason"], "context_cap");
}

TEST_F(ProviderTest, FabricatedCandidateIsRejectedBeforeResolve) {
  auto r = run("fabricated");
  EXPECT_EQ(r.status, RunResult::Status::incomplete);
  ASSERT_EQ(r.report["resolutions"]["rejected"].size(), 1u);
  const auto& rejected = r.report["resolutions"]["rejected"][0];
  EXPECT_EQ(rejected["stage"], "orchestrator");
  EXPECT_EQ(rejected["message"], "candidate 'builtin:invented' is not offered");
  EXPECT_TRUE(r.report["resolutions"]["applied"].empty());
}

TEST_F(ProviderTest, ProviderCrashFailsTheRunWithItsStderr) {
  auto r = run("crash");
  EXPECT_EQ(r.status, RunResult::Status::failed);
  const auto& e = r.report["errors"][0];
  EXPECT_EQ(e["stage"], "provider");
  EXPECT_EQ(e["kind"], "crashed");
  EXPECT_NE(e["stderr_tail"].get<std::string>().find("fake provider starting in mode crash"), std::string::npos);
}

TEST_F(ProviderTest, ProviderTimeoutFailsTheRun) {
  auto r = run("slow", 2, 500ms);
  EXPECT_EQ(r.status, RunResult::Status::failed);
  EXPECT_EQ(r.report["errors"][0]["kind"], "timeout");
}

TEST_F(ProviderTest, MalformedOutputFailsTheRun) {
  auto r = run("malformed");
  EXPECT_EQ(r.status, RunResult::Status::failed);
  EXPECT_EQ(r.report["errors"][0]["kind"], "malformed");
  auto bad = run("bad_answer");
  EXPECT_EQ(bad.status, RunResult::Status::failed);
  EXPECT_EQ(bad.report["errors"][0]["kind"], "malformed");
  EXPECT_NE(bad.report["errors"][0]["stderr_tail"].get<std::string>().find("fake provider starting"), std::string::npos);
}

TEST(PythonProvider, StartupFailures) {
  auto silent = fake("no_ready");
  silent.spec.startup_timeout = 300ms;
  auto p = PythonProvider::start(silent);
  ASSERT_FALSE(p.has_value());
  EXPECT_EQ(p.error().kind, WorkerError::Kind::startup_timeout);
  auto missing = make_provider({{"kind", "python"}, {"command", {"/nonexistent/provider"}}});
  ASSERT_FALSE(missing.has_value());
  ASSERT_TRUE(missing.error().process.has_value());
  EXPECT_EQ(missing.error().process->kind, WorkerError::Kind::spawn_failed);
  EXPECT_FALSE(make_provider({{"kind", "python"}}).has_value());
  EXPECT_FALSE(make_provider({{"kind", "python"}, {"command", json::array()}}).has_value());
}

TEST(PythonProvider, StartedFromJobConfig) {
  auto p = make_provider({{"kind", "python"},
                          {"command", {FAKE_PROVIDER_PATH, "passed"}},
                          {"context", {{"max_expansions", 3}, {"max_chars", 900}}}});
  ASSERT_TRUE(p.has_value()) << p.error().message;
  EXPECT_EQ((*p)->name(), "fake");
  EXPECT_EQ((*p)->max_expansions(), 3);
  EXPECT_EQ((*p)->expansion_chars(), 900);
}

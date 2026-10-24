#include <gtest/gtest.h>

#include <fstream>
#include <unistd.h>

#include "transpiler/orchestrator.hpp"

using namespace transpiler;
namespace fs = std::filesystem;
using namespace std::chrono_literals;

class OrchestratorTest : public testing::Test {
 protected:
  void SetUp() override {
    root_ = fs::temp_directory_path() / ("orch-" + std::to_string(::getpid()) + "-" +
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

  WorkerSpec fake(const char* mode) const { return WorkerSpec{{FAKE_WORKER_PATH, mode}, 5s}; }

  fs::path root_;
  Job job_;
};

TEST_F(OrchestratorTest, DeterministicProviderLeavesRunIncomplete) {
  DeterministicProvider p;
  auto r = run_job(job_, fake("normal"), p);
  EXPECT_EQ(r.status, RunResult::Status::incomplete);
  ASSERT_EQ(r.report["decisions"].size(), 1u);
  EXPECT_EQ(r.report["decisions"][0]["id"], "decision-1");
  EXPECT_TRUE(r.report["files_written"].empty());
  EXPECT_TRUE(fs::exists(root_ / "out/transpile-report.json"));
}

TEST_F(OrchestratorTest, StaticDecisionIsValidatedAndApplied) {
  StaticProvider p({DecisionResponse{"decision-1", "sha256:abc", "builtin:display", "", "synthetic"}});
  auto r = run_job(job_, fake("normal"), p);
  EXPECT_EQ(r.status, RunResult::Status::ok) << r.report.dump(2);
  EXPECT_EQ(r.report["resolutions"]["applied"].size(), 1u);
  EXPECT_EQ(r.report["decisions"][0]["state"], "resolved");
  EXPECT_EQ(r.report["files_written"].size(), 1u);
}

TEST_F(OrchestratorTest, InvalidProviderAnswerIsRejectedBeforeReachingWorker) {
  StaticProvider p({DecisionResponse{"decision-1", "", "builtin:invented", "", ""}});
  auto r = run_job(job_, fake("normal"), p);
  EXPECT_EQ(r.status, RunResult::Status::incomplete);
  ASSERT_EQ(r.report["resolutions"]["rejected"].size(), 1u);
  EXPECT_EQ(r.report["resolutions"]["rejected"][0]["stage"], "orchestrator");
}

TEST_F(OrchestratorTest, WorkerCrashFailsTheRun) {
  DeterministicProvider p;
  auto r = run_job(job_, fake("crash"), p);
  EXPECT_EQ(r.status, RunResult::Status::failed);
  EXPECT_EQ(r.report["errors"][0]["kind"], "crashed");
}

TEST_F(OrchestratorTest, WorkerProtocolErrorFailsTheRun) {
  job_.mappings = json();  // the fake rejects initialize without mappings
  DeterministicProvider p;
  auto r = run_job(job_, fake("normal"), p);
  EXPECT_EQ(r.status, RunResult::Status::failed);
  EXPECT_EQ(r.report["errors"][0]["code"], "invalid_mapping");
}

TEST_F(OrchestratorTest, MissingTranslatorFails) {
  auto r = run_job(job_);
  EXPECT_EQ(r.status, RunResult::Status::failed);
  EXPECT_EQ(r.report["errors"][0]["stage"], "translator");
}

TEST(Job, RejectsInvalidJobs) {
  EXPECT_FALSE(parse_job(json::array()).has_value());
  EXPECT_FALSE(parse_job({{"job_version", 2}}).has_value());
  EXPECT_FALSE(parse_job({{"job_version", 1}, {"source_language", "cobol"}}).has_value());
}

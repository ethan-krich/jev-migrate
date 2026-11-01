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

TEST_F(OrchestratorTest, ChosenSettingTranslatesAgainBeforeOtherDecisions) {
  StaticProvider p({DecisionResponse{"", "sha256:setting", "setting:ebcdic", "", "evidence"},
                    DecisionResponse{"", "sha256:abc", "builtin:display", "", "synthetic"}});
  auto r = run_job(job_, fake("setting"), p);
  EXPECT_EQ(r.status, RunResult::Status::ok) << r.report.dump(2);
  EXPECT_EQ(r.report["session"], "session-2");
  EXPECT_EQ(r.report["dialect"]["collating_sequence"], "ebcdic");
  ASSERT_EQ(r.report["settings"].size(), 1u);
  EXPECT_EQ(r.report["settings"][0]["field"], "collating_sequence");
  EXPECT_EQ(r.report["settings"][0]["value"], "ebcdic");
  EXPECT_EQ(r.report["settings"][0]["resolution"]["rationale"], "evidence");
  // Only the statement decision of the second pass was resolved; the setting never reached resolve.
  ASSERT_EQ(r.report["resolutions"]["applied"].size(), 1u);
  EXPECT_EQ(r.report["resolutions"]["applied"][0]["selection"]["candidate_id"], "builtin:display");
  EXPECT_TRUE(r.report["resolutions"]["rejected"].empty()) << r.report["resolutions"].dump(2);
  std::ifstream review(root_ / "out/review.md");
  std::string text((std::istreambuf_iterator<char>(review)), std::istreambuf_iterator<char>());
  EXPECT_NE(text.find("| collating_sequence | ebcdic | - | A.cbl:5 | evidence |"), std::string::npos) << text;
}

TEST_F(OrchestratorTest, HeldSettingLeavesTheRunIncomplete) {
  DecisionResponse held{"", "sha256:setting", "setting:ebcdic", "", ""};
  held.gate = "below_floor";
  held.confidence = 0.4;
  StaticProvider p({held});
  auto r = run_job(job_, fake("setting"), p);
  EXPECT_EQ(r.status, RunResult::Status::incomplete);
  EXPECT_EQ(r.report["session"], "session-1");
  EXPECT_TRUE(r.report["settings"].empty());
  ASSERT_EQ(r.report["resolutions"]["held"].size(), 1u);
  EXPECT_EQ(r.report["decisions"][0]["operation"], "dialect:collating_sequence");
}

TEST_F(OrchestratorTest, SettingAnswersThatDisagreeAreHeld) {
  std::ofstream(root_ / "src/B.cbl") << "x";
  StaticProvider p({DecisionResponse{"", "sha256:setting", "setting:ebcdic", "", ""},
                    DecisionResponse{"", "sha256:setting-B.cbl", "setting:ascii", "", ""}});
  auto r = run_job(job_, fake("setting"), p);
  EXPECT_EQ(r.status, RunResult::Status::incomplete);
  EXPECT_EQ(r.report["session"], "session-1");
  EXPECT_TRUE(r.report["settings"].empty());
  ASSERT_EQ(r.report["resolutions"]["held"].size(), 2u);
  EXPECT_EQ(r.report["resolutions"]["held"][0]["held_reason"], "conflicting_settings");
  EXPECT_EQ(r.report["resolutions"]["held"][1]["held_reason"], "conflicting_settings");
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

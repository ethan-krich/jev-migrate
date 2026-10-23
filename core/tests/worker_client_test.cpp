#include <gtest/gtest.h>

#include "transpiler/worker_client.hpp"

using namespace transpiler;
using namespace std::chrono_literals;

namespace {
WorkerSpec fake(const char* mode, std::chrono::milliseconds startup = 5s) {
  return WorkerSpec{{FAKE_WORKER_PATH, mode}, startup};
}
const protocol::json kInit = {{"mappings", {{"version", 1}, {"rules", protocol::json::array()}}}};
}  // namespace

TEST(WorkerClient, LifecycleAndCorrelation) {
  auto w = WorkerClient::start(fake("normal"));
  ASSERT_TRUE(w.has_value()) << w.error().message;
  EXPECT_EQ(w->ready_info()["translator"], "fake");
  auto init = w->call("initialize", "s", kInit, 5s);
  ASSERT_TRUE(init.has_value());
  EXPECT_EQ(init->status, protocol::Status::ok);

  int events = 0;
  auto tr = w->call("translate", "s", {{"files", {"A.cbl"}}}, 5s, [&](const protocol::Event& e) {
    EXPECT_EQ(e.name, "progress");
    ++events;
  });
  ASSERT_TRUE(tr.has_value());
  EXPECT_EQ(tr->status, protocol::Status::incomplete);
  EXPECT_EQ(events, 1);

  auto down = w->shutdown(5s);
  ASSERT_TRUE(down.has_value());
  EXPECT_EQ(*down, 0);
  EXPECT_FALSE(w->alive());
}

TEST(WorkerClient, ProtocolErrorIsAResponseNotATransportFailure) {
  auto w = WorkerClient::start(fake("normal"));
  ASSERT_TRUE(w.has_value());
  auto r = w->call("initialize", "s", protocol::json::object(), 5s);
  ASSERT_TRUE(r.has_value());
  EXPECT_EQ(r->status, protocol::Status::error);
  EXPECT_EQ(r->error->code, "invalid_mapping");
}

TEST(WorkerClient, SpawnFailure) {
  auto w = WorkerClient::start(WorkerSpec{{"/nonexistent/worker"}, 1s});
  ASSERT_FALSE(w.has_value());
  EXPECT_EQ(w.error().kind, WorkerError::Kind::spawn_failed);
}

TEST(WorkerClient, StartupTimeout) {
  auto w = WorkerClient::start(fake("no_ready", 300ms));
  ASSERT_FALSE(w.has_value());
  EXPECT_EQ(w.error().kind, WorkerError::Kind::startup_timeout);
}

TEST(WorkerClient, ProtocolMismatch) {
  auto w = WorkerClient::start(fake("bad_protocol"));
  ASSERT_FALSE(w.has_value());
  EXPECT_EQ(w.error().kind, WorkerError::Kind::protocol_mismatch);
}

struct Failure {
  const char* mode;
  WorkerError::Kind kind;
};

class WorkerFailures : public testing::TestWithParam<Failure> {};

TEST_P(WorkerFailures, AreReportedWithKind) {
  auto w = WorkerClient::start(fake(GetParam().mode));
  ASSERT_TRUE(w.has_value());
  ASSERT_TRUE(w->call("initialize", "s", kInit, 5s).has_value());
  auto r = w->call("translate", "s", {{"files", {"A.cbl"}}}, 1s);
  ASSERT_FALSE(r.has_value());
  EXPECT_EQ(r.error().kind, GetParam().kind) << r.error().message;
  EXPECT_NE(r.error().stderr_tail.find("fake worker starting"), std::string::npos);
}

INSTANTIATE_TEST_SUITE_P(WorkerClient, WorkerFailures, testing::Values(
    Failure{"crash", WorkerError::Kind::crashed},
    Failure{"malformed", WorkerError::Kind::malformed},
    Failure{"wrong_id", WorkerError::Kind::id_mismatch},
    Failure{"slow", WorkerError::Kind::timeout}),
    [](const testing::TestParamInfo<Failure>& info) { return std::string(info.param.mode); });

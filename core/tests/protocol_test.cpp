#include <gtest/gtest.h>

#include "transpiler/protocol.hpp"

using namespace transpiler::protocol;

TEST(Protocol, EncodesRequestOnOneLine) {
  Request r{"c-1", "translate", "s", {{"text", "line1\nline2"}}};
  std::string line = encode(r);
  EXPECT_EQ(line.find('\n'), std::string::npos);
  auto j = json::parse(line);
  EXPECT_EQ(j["protocol"], kVersion);
  EXPECT_EQ(j["id"], "c-1");
  EXPECT_EQ(j["method"], "translate");
  EXPECT_EQ(j["session"], "s");
  EXPECT_EQ(j["params"]["text"], "line1\nline2");
}

TEST(Protocol, DecodesOkResponse) {
  auto m = decode(R"({"protocol":"transpiler-worker/1","type":"response","id":"c-1","status":"incomplete","result":{"x":1}})");
  ASSERT_TRUE(m.has_value()) << m.error();
  auto& r = std::get<Response>(*m);
  EXPECT_EQ(r.id, "c-1");
  EXPECT_EQ(r.status, Status::incomplete);
  EXPECT_EQ(r.result["x"], 1);
}

TEST(Protocol, DecodesErrorResponseWithNullId) {
  auto m = decode(R"({"protocol":"transpiler-worker/1","type":"response","id":null,"status":"error","error":{"code":"malformed_json","message":"bad"}})");
  ASSERT_TRUE(m.has_value()) << m.error();
  auto& r = std::get<Response>(*m);
  EXPECT_FALSE(r.id.has_value());
  ASSERT_TRUE(r.error.has_value());
  EXPECT_EQ(r.error->code, "malformed_json");
}

TEST(Protocol, DecodesEvent) {
  auto m = decode(R"({"protocol":"transpiler-worker/1","type":"event","event":"progress","in_reply_to":"c-2","data":{"index":1}})");
  ASSERT_TRUE(m.has_value());
  auto& e = std::get<Event>(*m);
  EXPECT_EQ(e.name, "progress");
  EXPECT_EQ(e.in_reply_to, "c-2");
}

struct Malformed : testing::TestWithParam<const char*> {};

TEST_P(Malformed, IsRejected) { EXPECT_FALSE(decode(GetParam()).has_value()); }

INSTANTIATE_TEST_SUITE_P(Protocol, Malformed, testing::Values(
    "not json",
    "[1,2]",
    R"({"protocol":"transpiler-worker/2","type":"response","id":"a","status":"ok","result":{}})",
    R"({"protocol":"transpiler-worker/1","type":"response","id":"a","status":"maybe","result":{}})",
    R"({"protocol":"transpiler-worker/1","type":"response","id":"a","status":"ok"})",
    R"({"protocol":"transpiler-worker/1","type":"response","id":null,"status":"ok","result":{}})",
    R"({"protocol":"transpiler-worker/1","type":"response","id":"a","status":"error","error":{"code":1}})",
    R"({"protocol":"transpiler-worker/1","type":"response","status":"ok","result":{}})",
    R"({"protocol":"transpiler-worker/1","type":"event"})",
    R"({"protocol":"transpiler-worker/1","type":"gossip"})"),
    [](const testing::TestParamInfo<const char*>& info) { return "case" + std::to_string(info.index); });

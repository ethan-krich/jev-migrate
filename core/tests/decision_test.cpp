#include <gtest/gtest.h>

#include "transpiler/decision.hpp"

using namespace transpiler;

namespace {
DecisionRequest request() {
  auto r = parse_decision_request({{"id", "decision-1"},
                                   {"fingerprint", "sha256:f"},
                                   {"reason", "missing_mapping"},
                                   {"operation", "DISPLAY"},
                                   {"candidates", {{{"id", "builtin:display"}, {"description", "print"}}}}});
  EXPECT_TRUE(r.has_value());
  return *r;
}

DecisionResponse answer(std::string candidate, std::string fingerprint = "") {
  return DecisionResponse{"decision-1", std::move(fingerprint), std::move(candidate), "test", ""};
}
}  // namespace

TEST(Decision, ParsesRequest) {
  auto r = request();
  EXPECT_EQ(r.candidates.size(), 1u);
  EXPECT_EQ(r.raw["operation"], "DISPLAY");
}

TEST(Decision, RejectsMalformedRequest) {
  EXPECT_FALSE(parse_decision_request({{"id", "x"}}).has_value());
  EXPECT_FALSE(parse_decision_request(json::array()).has_value());
}

TEST(Decision, ValidatesCandidate) {
  EXPECT_TRUE(validate(request(), answer("builtin:display")).has_value());
  EXPECT_FALSE(validate(request(), answer("builtin:invented")).has_value());
}

TEST(Decision, ValidatesFingerprint) {
  EXPECT_TRUE(validate(request(), answer("builtin:display", "sha256:f")).has_value());
  EXPECT_FALSE(validate(request(), answer("builtin:display", "sha256:other")).has_value());
}

TEST(Decision, RejectsSelectionWithoutCandidates) {
  auto r = request();
  r.candidates.clear();
  EXPECT_FALSE(validate(r, answer("builtin:display")).has_value());
}

TEST(Decision, ParsesResponse) {
  auto ok = parse_decision_response({{"decision_id", "decision-1"}, {"selection", {{"kind", "candidate"}, {"candidate_id", "c"}}}});
  ASSERT_TRUE(ok.has_value());
  EXPECT_EQ(ok->candidate_id, "c");
  EXPECT_FALSE(parse_decision_response({{"decision_id", "d"}, {"selection", {{"kind", "code"}, {"java", "x"}}}}).has_value());
  EXPECT_FALSE(parse_decision_response({{"selection", {{"kind", "candidate"}, {"candidate_id", "c"}}}}).has_value());
}

TEST(Decision, DeterministicProviderAnswersNothing) {
  DeterministicProvider p;
  std::vector<DecisionRequest> batch{request()};
  EXPECT_TRUE(p.decide(batch).empty());
}

TEST(Decision, StaticProviderMatchesByIdOrFingerprint) {
  StaticProvider by_fp({DecisionResponse{"", "sha256:f", "builtin:display", "", ""}});
  std::vector<DecisionRequest> batch{request()};
  auto out = by_fp.decide(batch);
  ASSERT_EQ(out.size(), 1u);
  EXPECT_EQ(out[0].decision_id, "decision-1");
  EXPECT_EQ(out[0].provider, "static");
  StaticProvider none({DecisionResponse{"decision-9", "", "x", "", ""}});
  EXPECT_TRUE(none.decide(batch).empty());
}

TEST(Decision, CacheKeysOnFingerprint) {
  DecisionCache cache;
  auto r = request();
  EXPECT_FALSE(cache.lookup(r).has_value());
  cache.store(r, answer("builtin:display"));
  auto other = r;
  other.id = "decision-7";
  auto hit = cache.lookup(other);
  ASSERT_TRUE(hit.has_value());
  EXPECT_EQ(hit->decision_id, "decision-7");
  EXPECT_EQ(hit->provider, "cache");
}

TEST(Decision, MakeProvider) {
  EXPECT_EQ((*make_provider({{"kind", "deterministic"}}))->name(), "deterministic");
  EXPECT_EQ((*make_provider(json::object()))->name(), "deterministic");
  EXPECT_FALSE(make_provider({{"kind", "jev"}}).has_value());
  EXPECT_FALSE(make_provider({{"kind", "static"}, {"decisions", {{{"decision_id", "d"}}}}}).has_value());
}

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

TEST(Decision, ParsesGatedAnswers) {
  auto passed = parse_decision_response({{"decision_id", "decision-1"},
                                         {"selection", {{"kind", "candidate"}, {"candidate_id", "c"}}},
                                         {"gate", "passed"},
                                         {"confidence", 0.97},
                                         {"probabilities", {{"c", 0.97}, {"__abstain__", 0.03}}},
                                         {"review_priority", {{"level", 2}, {"label", "visible"}}}});
  ASSERT_TRUE(passed.has_value()) << passed.error();
  EXPECT_TRUE(passed->passed());
  EXPECT_EQ(passed->confidence, 0.97);
  EXPECT_EQ(passed->review_priority["label"], "visible");
  auto need = parse_decision_response({{"decision_id", "decision-1"}, {"gate", "need_context"}, {"need_context", {"data:A@3:8"}}});
  ASSERT_TRUE(need.has_value()) << need.error();
  EXPECT_FALSE(need->passed());
  EXPECT_EQ(need->need_context, std::vector<std::string>{"data:A@3:8"});
  EXPECT_TRUE(parse_decision_response({{"decision_id", "decision-1"}, {"gate", "abstained"}}).has_value());
  // A passed answer must select; need_context must name refs and select nothing; gates and confidences are checked.
  EXPECT_FALSE(parse_decision_response({{"decision_id", "decision-1"}, {"gate", "passed"}}).has_value());
  EXPECT_FALSE(parse_decision_response({{"decision_id", "decision-1"}, {"gate", "need_context"}}).has_value());
  EXPECT_FALSE(parse_decision_response({{"decision_id", "decision-1"}, {"gate", "need_context"}, {"need_context", {"r"}},
                                        {"selection", {{"kind", "candidate"}, {"candidate_id", "c"}}}})
                   .has_value());
  EXPECT_FALSE(parse_decision_response({{"decision_id", "decision-1"}, {"gate", "maybe"},
                                        {"selection", {{"kind", "candidate"}, {"candidate_id", "c"}}}})
                   .has_value());
  EXPECT_FALSE(parse_decision_response({{"decision_id", "decision-1"}, {"confidence", 1.5},
                                        {"selection", {{"kind", "candidate"}, {"candidate_id", "c"}}}})
                   .has_value());
}

TEST(Decision, ReportEntryCarriesGateFieldsAndImportance) {
  DecisionResponse a = answer("builtin:display");
  a.gate = "below_floor";
  a.confidence = 0.5;
  json entry = a.to_report("critical");
  EXPECT_EQ(entry["importance"], "critical");
  EXPECT_EQ(entry["gate"], "below_floor");
  EXPECT_EQ(entry["confidence"], 0.5);
  EXPECT_FALSE(a.to_resolution().contains("gate")) << "the worker only receives the selection";
}

TEST(Decision, CacheKeepsConfidenceAndReviewPriority) {
  DecisionCache cache;
  DecisionResponse a = answer("builtin:display");
  a.gate = "passed";
  a.confidence = 0.99;
  a.review_priority = {{"level", 4}, {"label", "money/compliance"}};
  cache.store(request(), a);
  auto hit = cache.lookup(request());
  ASSERT_TRUE(hit.has_value());
  EXPECT_EQ(hit->confidence, 0.99);
  EXPECT_EQ(hit->review_priority["level"], 4);
  EXPECT_EQ(hit->provider, "cache");
}

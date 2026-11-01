#include <gtest/gtest.h>

#include "transpiler/review.hpp"

using namespace transpiler;
using protocol::json;

namespace {
json decision(const char* id, const char* state, const char* importance) {
  return {{"id", id}, {"state", state}, {"importance", importance}, {"operation", "MOVE"}, {"reason", "missing_mapping"},
          {"source", {{"file", "A.cbl"}, {"line", 7}}}};
}
json answer(const char* id, int level, const char* label) {
  return {{"decision_id", id}, {"gate", "passed"}, {"confidence", 0.991},
          {"selection", {{"kind", "candidate"}, {"candidate_id", "c|x"}}},
          {"review_priority", {{"level", level}, {"label", label}}}};
}
size_t row(const std::string& text, const char* id) { return text.find(std::string("| ") + id + " |"); }
}  // namespace

TEST(Review, SortsPendingFirstThenReviewPriorityThenImportance) {
  json held = answer("decision-4", 1, "local");
  held["gate"] = "below_floor";
  json report = {{"status", "incomplete"},
                 {"decisions", {decision("decision-1", "resolved", "normal"), decision("decision-2", "resolved", "low"),
                                decision("decision-3", "pending", "low"), decision("decision-4", "pending", "critical"),
                                decision("decision-5", "resolved", "critical")}},
                 {"resolutions", {{"provider", "jev"},
                                  {"applied", {answer("decision-1", 0, "cosmetic"), answer("decision-2", 4, "money/compliance"),
                                               answer("decision-5", 0, "cosmetic")}},
                                  {"held", {held}},
                                  {"rejected", json::array()}}}};
  std::string md = review_table(report);
  EXPECT_LT(row(md, "decision-4"), row(md, "decision-3"));  // pending: level 1 before none
  EXPECT_LT(row(md, "decision-3"), row(md, "decision-2"));  // pending before resolved
  EXPECT_LT(row(md, "decision-2"), row(md, "decision-5"));  // level 4 before level 0
  EXPECT_LT(row(md, "decision-5"), row(md, "decision-1"));  // same level: critical before normal
  EXPECT_NE(md.find("Status `incomplete`: 5 decision(s), 2 pending; provider `jev`: 3 applied, 1 held, 0 rejected."),
            std::string::npos)
      << md;
  EXPECT_NE(md.find("| decision-4 | pending | critical | 1 local | held | below_floor | 0.99 | c\\|x |"), std::string::npos) << md;
}

TEST(Review, FlagsTierMismatches) {
  json report = {{"status", "ok"},
                 {"decisions", {decision("decision-1", "resolved", "low"), decision("decision-2", "resolved", "critical"),
                                decision("decision-3", "resolved", "normal")}},
                 {"resolutions", {{"applied", {answer("decision-1", 3, "business"), answer("decision-2", 0, "cosmetic"),
                                               answer("decision-3", 4, "money/compliance")}}}}};
  std::string md = review_table(report);
  auto line = [&](const char* id) { return md.substr(row(md, id), md.find('\n', row(md, id)) - row(md, id)); };
  EXPECT_TRUE(line("decision-1").ends_with("| tier mismatch |"));
  EXPECT_TRUE(line("decision-2").ends_with("| tier mismatch |"));
  EXPECT_TRUE(line("decision-3").ends_with("| - |"));
}

TEST(Review, EmptyReport) {
  EXPECT_NE(review_table({{"status", "ok"}, {"decisions", json::array()}}).find("No decisions."), std::string::npos);
}

TEST(Review, LibraryMappingsComeFirstWithTheWarning) {
  json uses = {{{"program", "BILLING"}, {"file", "BILLING.cbl"}, {"line", 12}, {"selector", "program"}, {"part", "DATEADD"},
                {"outcome", "library"}, {"chain", {"Dates.addDays(int,int)"}}, {"decided_by", "Decided by jev (confidence 0.82)"}},
               {{"program", "BILLING"}, {"file", "BILLING.cbl"}, {"line", 15}, {"selector", "program"}, {"part", "WEEKDAY"},
                {"outcome", "translated"}, {"chain", json::array()}, {"reason", "cobol:translate chosen at the start step"}}};
  json report = {{"status", "ok"},
                 {"decisions", {decision("decision-1", "resolved", "high")}},
                 {"libraries_used", {{{"id", "acme.dates"}, {"java", "com.acme:acme-dates:2.3.1"}, {"declared_in_target", false},
                                      {"target_note", "not a dependency in pom.xml"}, {"uses", uses}}}},
                 {"settings", {{{"field", "encoding"}, {"value", "ascii"}}}}};
  std::string md = review_table(report);
  size_t section = md.find("## Library mappings");
  ASSERT_NE(section, std::string::npos) << md;
  EXPECT_LT(section, md.find("## Settings chosen"));
  EXPECT_LT(section, row(md, "decision-1"));
  EXPECT_NE(md.find("run your tests against both the original program and the translation."), std::string::npos);
  EXPECT_NE(md.find("| acme.dates | com.acme:acme-dates:2.3.1 | program DATEADD | BILLING.cbl:12 | library | Dates.addDays(int,int) | "
                    "Decided by jev (confidence 0.82) |"),
            std::string::npos)
      << md;
  EXPECT_NE(md.find("| program WEEKDAY | BILLING.cbl:15 | translated | cobol:translate chosen at the start step | - |"),
            std::string::npos)
      << md;
  EXPECT_NE(md.find("- `acme.dates` (com.acme:acme-dates:2.3.1): not declared in the target project: not a dependency in pom.xml"),
            std::string::npos)
      << md;
}

TEST(Review, NoLibrarySectionWithoutLibraries) {
  EXPECT_EQ(review_table({{"status", "ok"}, {"decisions", json::array()}}).find("Library mappings"), std::string::npos);
}

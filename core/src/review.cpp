#include "transpiler/review.hpp"

#include <algorithm>
#include <array>
#include <format>
#include <map>
#include <vector>

namespace transpiler {
namespace {

using protocol::json;

constexpr std::array<std::string_view, 4> kTiers = {"low", "normal", "high", "critical"};

int tier_rank(const std::string& importance) {
  auto it = std::ranges::find(kTiers, importance);
  return it == kTiers.end() ? -1 : static_cast<int>(it - kTiers.begin());
}

// Text for a table cell: pipes escaped, line breaks flattened.
std::string cell(std::string text) {
  std::string out;
  for (char c : text) {
    if (c == '|') out += "\\|";
    else if (c == '\n' || c == '\r') out += ' ';
    else out += c;
  }
  return out.empty() ? "-" : out;
}

std::string str(const json& j, const char* key) {
  return j.is_object() && j.contains(key) && j[key].is_string() ? j[key].get<std::string>() : std::string{};
}

struct Row {
  json decision;
  json outcome;         // the applied, held or rejected entry for the decision (null if none)
  std::string outcome_kind;
  size_t index;
  bool pending;
  int level;            // review_priority level, -1 when absent
  int tier;
};

}  // namespace

std::string review_table(const json& report) {
  std::map<std::string, std::pair<json, std::string>> outcomes;
  const json& resolutions = report.value("resolutions", json::object());
  // An applied answer outranks a held or rejected one for the same decision.
  for (const char* kind : {"rejected", "held", "applied"})
    for (const auto& entry : resolutions.value(kind, json::array()))
      outcomes[str(entry, "decision_id")] = {entry, kind};

  std::vector<Row> rows;
  const json& decisions = report.value("decisions", json::array());
  for (size_t i = 0; i < decisions.size(); ++i) {
    const json& d = decisions[i];
    Row row{d, json{}, "", i, str(d, "state") != "resolved", -1, tier_rank(str(d, "importance"))};
    if (auto it = outcomes.find(str(d, "id")); it != outcomes.end()) {
      row.outcome = it->second.first;
      row.outcome_kind = it->second.second;
      const json& rp = row.outcome.value("review_priority", json{});
      if (rp.is_object() && rp.contains("level") && rp["level"].is_number_integer()) row.level = rp["level"].get<int>();
    }
    rows.push_back(std::move(row));
  }
  std::ranges::stable_sort(rows, [](const Row& a, const Row& b) {
    if (a.pending != b.pending) return a.pending;
    if (a.level != b.level) return a.level > b.level;
    return a.tier > b.tier;
  });

  size_t pending = std::ranges::count_if(rows, [](const Row& r) { return r.pending; });
  std::string out = "# Review queue\n\n";
  out += std::format("Status `{}`: {} decision(s), {} pending; provider `{}`: {} applied, {} held, {} rejected.\n\n",
                     str(report, "status"), rows.size(), pending, str(resolutions, "provider"),
                     resolutions.value("applied", json::array()).size(), resolutions.value("held", json::array()).size(),
                     resolutions.value("rejected", json::array()).size());
  if (rows.empty()) return out + "No decisions.\n";
  out += "| Decision | State | Importance | Review priority | Outcome | Gate | Confidence | Selection | Source | Operation | "
         "Reason | Flags |\n";
  out += "|---|---|---|---|---|---|---|---|---|---|---|---|\n";
  for (const auto& r : rows) {
    const json& d = r.decision;
    const json& o = r.outcome;
    const json& rp = o.is_object() ? o.value("review_priority", json{}) : json{};
    std::string priority = rp.is_object() ? std::format("{} {}", r.level, str(rp, "label")) : "";
    std::string confidence =
        o.is_object() && o.contains("confidence") && o["confidence"].is_number() ? std::format("{:.2f}", o["confidence"].get<double>()) : "";
    std::string selection = o.is_object() ? str(o.value("selection", json::object()), "candidate_id") : "";
    if (selection.empty()) selection = str(d, "selected_candidate");
    std::string outcome = r.outcome_kind;
    if (!str(o, "held_reason").empty()) outcome += " (" + str(o, "held_reason") + ")";
    if (r.outcome_kind == "rejected" && !str(o, "message").empty()) outcome += ": " + str(o, "message");
    const json& src = d.value("source", json::object());
    std::string source = std::format("{}:{}", str(src, "file"), src.value("line", 0));
    std::string importance = str(d, "importance");
    std::string flags;
    if ((r.level >= 3 && importance == "low") || (r.level == 0 && importance == "critical")) flags = "tier mismatch";
    out += std::format("| {} | {} | {} | {} | {} | {} | {} | {} | {} | {} | {} | {} |\n", cell(str(d, "id")),
                       cell(str(d, "state")), cell(importance), cell(priority), cell(outcome), cell(str(o, "gate")),
                       cell(confidence), cell(selection), cell(source), cell(str(d, "operation")), cell(str(d, "reason")),
                       cell(flags));
  }
  return out;
}

}  // namespace transpiler

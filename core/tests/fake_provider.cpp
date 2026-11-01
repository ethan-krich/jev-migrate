// A scriptable decision provider (transpiler-provider/1) for orchestrator tests.
// Usage: fake_provider <mode>
//   passed          selects the first candidate with confidence 0.97 (gate passed)
//   below_floor     the same pick with confidence 0.60 (gate below_floor)
//   abstained       abstains without selecting
//   need_context    asks to expand "ref-a" first; answers passed once extra_context holds its text
//   context_forever always asks for more context
//   fabricated      selects a candidate the worker never offered (gate passed)
//   bad_answer      a passed answer without a selection
//   no_ready        never announces readiness
//   crash           exits with status 4 when asked to decide
//   malformed       answers decide with a non-JSON line
//   slow            sleeps 3 s before answering decide
#include <chrono>
#include <iostream>
#include <nlohmann/json.hpp>
#include <string>
#include <thread>

using json = nlohmann::json;

namespace {
const char* kProtocol = "transpiler-provider/1";

void send(const json& j) { std::cout << j.dump() << std::endl; }

json pick(const json& d, double confidence, const char* gate) {
  return {{"decision_id", d["id"]},
          {"fingerprint", d["fingerprint"]},
          {"selection", {{"kind", "candidate"}, {"candidate_id", d["candidates"][0]["id"]}}},
          {"gate", gate},
          {"confidence", confidence},
          {"probabilities", {{d["candidates"][0]["id"].get<std::string>(), confidence}, {"__abstain__", 1 - confidence}}},
          {"review_priority", {{"level", 3}, {"label", "business"}, {"confidence", 0.8}}}};
}

bool has_text(const json& params, const std::string& ref) {
  for (const auto& extra : params.value("extra_context", json::array()))
    for (const auto& node : extra.value("refs", json::array()))
      if (node.value("ref", "") == ref && node.contains("text")) return true;
  return false;
}
}  // namespace

int main(int argc, char** argv) {
  std::string mode = argc > 1 ? argv[1] : "passed";
  std::cerr << "fake provider starting in mode " << mode << std::endl;
  if (mode == "no_ready") {
    std::this_thread::sleep_for(std::chrono::seconds(30));
    return 0;
  }
  send({{"protocol", kProtocol}, {"type", "event"}, {"event", "ready"}, {"data", {{"provider", "fake"}}}});
  std::string line;
  while (std::getline(std::cin, line)) {
    json req = json::parse(line, nullptr, false);
    std::string method = req.value("method", "");
    if (method == "shutdown") {
      send({{"protocol", kProtocol}, {"type", "response"}, {"id", req["id"]}, {"status", "ok"}, {"result", json::object()}});
      return 0;
    }
    if (mode == "crash") return 4;
    if (mode == "malformed") {
      std::cout << "{definitely not json" << std::endl;
      continue;
    }
    if (mode == "slow") std::this_thread::sleep_for(std::chrono::seconds(3));
    json answers = json::array();
    for (const auto& d : req["params"]["decisions"]) {
      if (d["candidates"].empty()) continue;
      json need = {{"decision_id", d["id"]}, {"gate", "need_context"}, {"need_context", {"ref-a"}}};
      if (mode == "passed") answers.push_back(pick(d, 0.97, "passed"));
      else if (mode == "below_floor") answers.push_back(pick(d, 0.60, "below_floor"));
      else if (mode == "abstained") answers.push_back({{"decision_id", d["id"]}, {"gate", "abstained"}, {"confidence", 0.1}});
      else if (mode == "need_context") answers.push_back(has_text(req["params"], "ref-a") ? pick(d, 0.97, "passed") : need);
      else if (mode == "context_forever") answers.push_back(need);
      else if (mode == "fabricated") {
        json a = pick(d, 0.99, "passed");
        a["selection"]["candidate_id"] = "builtin:invented";
        answers.push_back(a);
      } else if (mode == "bad_answer") {
        answers.push_back({{"decision_id", d["id"]}, {"gate", "passed"}, {"confidence", 0.99}});
      }
    }
    send({{"protocol", kProtocol}, {"type", "response"}, {"id", req["id"]}, {"status", "ok"}, {"result", {{"answers", answers}}}});
  }
  return 0;
}

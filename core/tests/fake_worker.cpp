// A scriptable worker for orchestrator tests. Usage: fake_worker <mode>
//   normal      well-behaved: one decision on translate, applied by resolve
//   no_ready    never announces readiness
//   bad_protocol announces readiness with another protocol version
//   crash       exits with status 3 when asked to translate
//   malformed   answers translate with a non-JSON line
//   wrong_id    answers translate with a different correlation id
//   slow        sleeps 3 s before answering translate
#include <chrono>
#include <cstdio>
#include <iostream>
#include <nlohmann/json.hpp>
#include <string>
#include <thread>

using json = nlohmann::json;

namespace {
const char* kProtocol = "transpiler-worker/1";

void send(const json& j) { std::cout << j.dump() << std::endl; }

json response(const json& req, const char* status, json result) {
  return {{"protocol", kProtocol}, {"type", "response"}, {"id", req["id"]}, {"status", status}, {"result", std::move(result)}};
}

json decision() {
  return {{"id", "decision-1"},
          {"fingerprint", "sha256:abc"},
          {"reason", "missing_mapping"},
          {"operation", "DISPLAY"},
          {"message", "no rule"},
          {"source", {{"file", "A.cbl"}, {"line", 3}, {"column", 8}, {"text", "DISPLAY X"}}},
          {"context", {{"source_operation", "DISPLAY"}, {"target_language", "java"}}},
          {"candidates", {{{"id", "builtin:display"}, {"description", "print"}}}}};
}
}  // namespace

int main(int argc, char** argv) {
  std::string mode = argc > 1 ? argv[1] : "normal";
  std::cerr << "fake worker starting in mode " << mode << std::endl;
  if (mode == "no_ready") {
    std::this_thread::sleep_for(std::chrono::seconds(30));
    return 0;
  }
  send({{"protocol", mode == "bad_protocol" ? "other/9" : kProtocol}, {"type", "event"}, {"event", "ready"},
        {"data", {{"translator", "fake"}}}});
  bool resolved = false;
  std::string line;
  while (std::getline(std::cin, line)) {
    json req = json::parse(line, nullptr, false);
    if (req.is_discarded()) {
      send({{"protocol", kProtocol}, {"type", "response"}, {"id", nullptr}, {"status", "error"},
            {"error", {{"code", "malformed_json"}, {"message", "bad"}}}});
      continue;
    }
    std::string method = req.value("method", "");
    if (method == "shutdown") {
      send(response(req, "ok", json::object()));
      return 0;
    }
    if (method == "initialize") {
      if (!req["params"].value("mappings", json()).is_object()) {
        send({{"protocol", kProtocol}, {"type", "response"}, {"id", req["id"]}, {"status", "error"},
              {"error", {{"code", "invalid_mapping"}, {"message", "missing"}}}});
        continue;
      }
      send(response(req, "ok", {{"session", req["session"]}, {"programs", json::array()}}));
    } else if (method == "translate") {
      if (mode == "crash") return 3;
      if (mode == "malformed") {
        std::cout << "this is not json" << std::endl;
        continue;
      }
      if (mode == "slow") std::this_thread::sleep_for(std::chrono::seconds(3));
      send({{"protocol", kProtocol}, {"type", "event"}, {"event", "progress"}, {"in_reply_to", req["id"]},
            {"data", {{"phase", "translate"}, {"index", 1}, {"total", 1}}}});
      json r = response(req, "incomplete",
                        {{"files", {{{"file", req["params"]["files"][0]}, {"status", "incomplete"}}}},
                         {"decisions", {decision()}}});
      if (mode == "wrong_id") r["id"] = "someone-else";
      send(r);
    } else if (method == "resolve") {
      json applied = json::array(), rejected = json::array();
      for (const auto& res : req["params"]["resolutions"]) {
        if (res["decision_id"] == "decision-1" && res["selection"]["candidate_id"] == "builtin:display") {
          applied.push_back("decision-1");
          resolved = true;
        } else {
          rejected.push_back({{"decision_id", res["decision_id"]}, {"code", "unknown_candidate"}, {"message", "no"}});
        }
      }
      send(response(req, resolved ? "ok" : "incomplete",
                    {{"applied", applied}, {"rejected", rejected}, {"new_decisions", json::array()},
                     {"pending", resolved ? json::array() : json::array({"decision-1"})}}));
    } else if (method == "finalize") {
      if (resolved)
        send(response(req, "ok", {{"files_written", {{{"path", "src/main/java/A.java"}}}}, {"pending", json::array()}}));
      else
        send(response(req, "incomplete", {{"files_written", json::array()}, {"pending", {decision()}}}));
    } else {
      send({{"protocol", kProtocol}, {"type", "response"}, {"id", req["id"]}, {"status", "error"},
            {"error", {{"code", "unknown_method"}, {"message", method}}}});
    }
  }
  return 0;
}

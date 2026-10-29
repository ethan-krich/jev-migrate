#include "transpiler/protocol.hpp"

#include <format>

namespace transpiler::protocol {

std::string_view to_string(Status status) {
  switch (status) {
    case Status::ok: return "ok";
    case Status::incomplete: return "incomplete";
    case Status::error: return "error";
  }
  return "error";
}

std::optional<Status> parse_status(std::string_view text) {
  if (text == "ok") return Status::ok;
  if (text == "incomplete") return Status::incomplete;
  if (text == "error") return Status::error;
  return std::nullopt;
}

std::string encode(const Request& request, std::string_view version) {
  json j = {{"protocol", version}, {"id", request.id}, {"method", request.method}, {"params", request.params}};
  if (request.session) j["session"] = *request.session;
  // dump() never emits raw newlines: control characters in strings are escaped.
  return j.dump(-1, ' ', false, json::error_handler_t::replace);
}

namespace {

std::optional<std::string> optional_string(const json& j, const char* key) {
  auto it = j.find(key);
  if (it == j.end() || it->is_null() || !it->is_string()) return std::nullopt;
  return it->get<std::string>();
}

}  // namespace

std::expected<Message, std::string> decode(std::string_view line, std::string_view version) {
  json j = json::parse(line, nullptr, false);
  if (j.is_discarded()) return std::unexpected("worker output is not valid JSON");
  if (!j.is_object()) return std::unexpected("worker message is not a JSON object");
  const auto protocol = optional_string(j, "protocol");
  if (protocol != version)
    return std::unexpected(std::format("unexpected protocol '{}'", protocol.value_or("<missing or not a string>")));

  const std::string type = optional_string(j, "type").value_or("");
  if (type == "event") {
    if (!j.contains("event") || !j["event"].is_string()) return std::unexpected("event without a name");
    Event e{.name = j["event"].get<std::string>(),
            .session = optional_string(j, "session"),
            .in_reply_to = optional_string(j, "in_reply_to"),
            .data = j.contains("data") && j["data"].is_object() ? j["data"] : json::object()};
    return e;
  }
  if (type != "response") return std::unexpected(std::format("unknown message type '{}'", type));

  Response r;
  if (!j.contains("id")) return std::unexpected("response without 'id'");
  if (!j["id"].is_null()) {
    if (!j["id"].is_string()) return std::unexpected("response 'id' must be a string or null");
    r.id = j["id"].get<std::string>();
  }
  r.session = optional_string(j, "session");
  auto status = parse_status(optional_string(j, "status").value_or(""));
  if (!status) return std::unexpected("response has an invalid 'status'");
  r.status = *status;
  if (r.status == Status::error) {
    const json err = j.contains("error") ? j["error"] : json{};
    if (!err.is_object() || !err.contains("code") || !err["code"].is_string() || !err.contains("message") ||
        !err["message"].is_string())
      return std::unexpected("error response without {code, message}");
    r.error = ErrorInfo{err["code"].get<std::string>(), err["message"].get<std::string>(), err.contains("details") ? err["details"] : json{}};
  } else {
    if (!j.contains("result") || !j["result"].is_object()) return std::unexpected("response without a 'result' object");
    r.result = j["result"];
    if (!r.id) return std::unexpected("non-error response with a null 'id'");
  }
  return r;
}

}  // namespace transpiler::protocol

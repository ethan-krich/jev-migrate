#pragma once

#include <expected>
#include <nlohmann/json.hpp>
#include <optional>
#include <string>
#include <string_view>
#include <variant>

// Newline-delimited JSON worker protocol (protocol/worker.schema.json). The orchestrator treats
// `params` and `result` payloads as opaque JSON except for the generic fields it routes on.
namespace transpiler::protocol {

using json = nlohmann::json;

inline constexpr std::string_view kVersion = "transpiler-worker/1";
// Out-of-process decision providers speak the same envelope under their own version.
inline constexpr std::string_view kProviderVersion = "transpiler-provider/1";

enum class Status { ok, incomplete, error };

struct Request {
  std::string id;
  std::string method;
  std::optional<std::string> session;
  json params = json::object();
};

struct ErrorInfo {
  std::string code;
  std::string message;
  json details;
};

struct Response {
  std::optional<std::string> id;  // null only for errors about unparseable requests
  std::optional<std::string> session;
  Status status = Status::ok;
  json result = json::object();
  std::optional<ErrorInfo> error;
};

struct Event {
  std::string name;
  std::optional<std::string> session;
  std::optional<std::string> in_reply_to;
  json data = json::object();
};

using Message = std::variant<Response, Event>;

// Serializes a request as a single line (no trailing newline).
std::string encode(const Request& request, std::string_view version = kVersion);

// Parses one line from a worker (or a provider, with its version). Errors describe exactly what
// was malformed.
std::expected<Message, std::string> decode(std::string_view line, std::string_view version = kVersion);

std::string_view to_string(Status status);
std::optional<Status> parse_status(std::string_view text);

}  // namespace transpiler::protocol

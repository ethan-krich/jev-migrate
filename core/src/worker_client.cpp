#include "transpiler/worker_client.hpp"

#include <format>

#include "transpiler/log.hpp"

namespace transpiler {

std::string_view to_string(WorkerError::Kind kind) {
  using enum WorkerError::Kind;
  switch (kind) {
    case spawn_failed: return "spawn_failed";
    case startup_timeout: return "startup_timeout";
    case timeout: return "timeout";
    case crashed: return "crashed";
    case malformed: return "malformed";
    case id_mismatch: return "id_mismatch";
    case protocol_mismatch: return "protocol_mismatch";
    case io: return "io";
  }
  return "unknown";
}

std::expected<WorkerClient, WorkerError> WorkerClient::start(const WorkerSpec& spec) {
  auto tail = std::make_shared<StderrTail>();
  auto process = Subprocess::spawn(spec.command, [tail](std::string_view line) {
    log(LogLevel::debug, "[worker] {}", line);
    std::scoped_lock lock(tail->mutex);
    tail->lines.emplace_back(line);
    if (tail->lines.size() > 40) tail->lines.pop_front();
  });
  if (!process) return std::unexpected(WorkerError{WorkerError::Kind::spawn_failed, process.error(), {}});

  WorkerClient client(std::move(*process), tail);
  auto first = client.next_message(spec.startup_timeout);
  if (!first) {
    auto e = first.error();
    if (e.kind == WorkerError::Kind::timeout) e.kind = WorkerError::Kind::startup_timeout;
    return std::unexpected(e);
  }
  auto* ready = std::get_if<protocol::Event>(&*first);
  if (ready == nullptr || ready->name != "ready")
    return std::unexpected(client.make_error(WorkerError::Kind::malformed, "worker did not announce 'ready' first"));
  client.ready_ = ready->data;
  log(LogLevel::debug, "worker ready: {}", ready->data.dump());
  return client;
}

WorkerError WorkerClient::make_error(WorkerError::Kind kind, std::string message) {
  std::string joined;
  {
    std::scoped_lock lock(tail_->mutex);
    for (const auto& l : tail_->lines) joined += l + "\n";
  }
  return WorkerError{kind, std::move(message), std::move(joined)};
}

std::expected<protocol::Message, WorkerError> WorkerClient::next_message(std::chrono::milliseconds timeout) {
  auto line = process_.read_line(timeout);
  if (!line) {
    switch (line.error()) {
      case Subprocess::ReadError::timeout:
        return std::unexpected(make_error(WorkerError::Kind::timeout, std::format("no message within {} ms", timeout.count())));
      case Subprocess::ReadError::eof: {
        int status = process_.wait_or_kill(std::chrono::milliseconds(2000));
        return std::unexpected(make_error(WorkerError::Kind::crashed, std::format("worker exited (status {})", status)));
      }
      case Subprocess::ReadError::io:
        return std::unexpected(make_error(WorkerError::Kind::io, "error reading worker stdout"));
    }
  }
  auto message = protocol::decode(*line);
  if (!message) {
    auto kind = message.error().starts_with("unexpected protocol") ? WorkerError::Kind::protocol_mismatch
                                                                    : WorkerError::Kind::malformed;
    std::string excerpt = line->substr(0, 200);
    return std::unexpected(make_error(kind, std::format("{}: {}", message.error(), excerpt)));
  }
  return std::move(*message);
}

std::expected<protocol::Response, WorkerError> WorkerClient::call(const std::string& method,
                                                                  const std::optional<std::string>& session,
                                                                  const protocol::json& params,
                                                                  std::chrono::milliseconds timeout,
                                                                  const EventHandler& on_event) {
  protocol::Request request{std::format("c-{}", next_id_++), method, session, params};
  if (auto w = process_.write_line(protocol::encode(request)); !w)
    return std::unexpected(make_error(WorkerError::Kind::crashed, w.error()));

  auto deadline = std::chrono::steady_clock::now() + timeout;
  for (;;) {
    auto remaining = std::chrono::duration_cast<std::chrono::milliseconds>(deadline - std::chrono::steady_clock::now());
    if (remaining.count() <= 0)
      return std::unexpected(make_error(WorkerError::Kind::timeout, std::format("'{}' timed out", method)));
    auto message = next_message(remaining);
    if (!message) return std::unexpected(message.error());
    if (auto* event = std::get_if<protocol::Event>(&*message)) {
      if (on_event) on_event(*event);
      continue;
    }
    auto& response = std::get<protocol::Response>(*message);
    if (response.id != request.id)
      return std::unexpected(make_error(WorkerError::Kind::id_mismatch,
                                        std::format("expected response to '{}', got '{}'", request.id,
                                                    response.id.value_or("null"))));
    return std::move(response);
  }
}

std::expected<int, WorkerError> WorkerClient::shutdown(std::chrono::milliseconds timeout) {
  auto response = call("shutdown", std::nullopt, protocol::json::object(), timeout);
  process_.close_stdin();
  int status = process_.wait_or_kill(timeout);
  if (!response) return std::unexpected(response.error());
  return status;
}

bool WorkerClient::alive() { return !process_.poll_exit().has_value(); }

}  // namespace transpiler

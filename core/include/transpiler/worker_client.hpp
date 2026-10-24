#pragma once

#include <chrono>
#include <deque>
#include <expected>
#include <filesystem>
#include <functional>
#include <memory>
#include <mutex>
#include <string>
#include <vector>

#include "transpiler/process.hpp"
#include "transpiler/protocol.hpp"

namespace transpiler {

struct WorkerError {
  enum class Kind {
    spawn_failed,       // the command could not be started
    startup_timeout,    // no `ready` event in time
    timeout,            // no response to a request in time
    crashed,            // the process exited or closed stdout unexpectedly
    malformed,          // stdout carried something that is not a valid protocol message
    id_mismatch,        // a response did not correlate with the outstanding request
    protocol_mismatch,  // the worker speaks another protocol version
    io,
  };
  Kind kind;
  std::string message;
  std::string stderr_tail;  // the worker's last stderr lines, for diagnostics
};

std::string_view to_string(WorkerError::Kind kind);

struct WorkerSpec {
  std::vector<std::string> command;  // absolute paths; the worker inherits the caller's cwd
  std::chrono::milliseconds startup_timeout{60'000};
};

// A persistent translator process. Requests are strictly sequential: one outstanding request,
// correlated by id; events received meanwhile are passed to the caller.
class WorkerClient {
 public:
  using EventHandler = std::function<void(const protocol::Event&)>;

  static std::expected<WorkerClient, WorkerError> start(const WorkerSpec& spec);

  // Sends a request and waits for its response. A protocol-level `error` status is a successful
  // call (returned in the Response); transport failures are WorkerErrors.
  std::expected<protocol::Response, WorkerError> call(const std::string& method, const std::optional<std::string>& session,
                                                      const protocol::json& params, std::chrono::milliseconds timeout,
                                                      const EventHandler& on_event = {});

  // Sends `shutdown` and waits for exit; kills the process if it does not comply.
  std::expected<int, WorkerError> shutdown(std::chrono::milliseconds timeout);

  const protocol::json& ready_info() const { return ready_; }
  bool alive();

 private:
  struct StderrTail {
    std::mutex mutex;
    std::deque<std::string> lines;
  };

  WorkerClient(Subprocess process, std::shared_ptr<StderrTail> tail)
      : process_(std::move(process)), tail_(std::move(tail)) {}
  WorkerError make_error(WorkerError::Kind kind, std::string message);
  std::expected<protocol::Message, WorkerError> next_message(std::chrono::milliseconds timeout);

  Subprocess process_;
  std::shared_ptr<StderrTail> tail_;
  protocol::json ready_;
  unsigned long next_id_ = 1;
};

}  // namespace transpiler

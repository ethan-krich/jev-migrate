#pragma once

#include <chrono>
#include <expected>
#include <functional>
#include <optional>
#include <string>
#include <thread>
#include <vector>

namespace transpiler {

// A child process with piped stdin/stdout. stderr lines are delivered to a callback on a
// background thread so worker diagnostics never mix with protocol traffic.
class Subprocess {
 public:
  using LineSink = std::function<void(std::string_view)>;

  enum class ReadError { timeout, eof, io };

  // The child inherits the current working directory and environment.
  static std::expected<Subprocess, std::string> spawn(const std::vector<std::string>& argv, LineSink on_stderr);

  Subprocess(Subprocess&&) noexcept;
  Subprocess& operator=(Subprocess&&) noexcept;
  Subprocess(const Subprocess&) = delete;
  Subprocess& operator=(const Subprocess&) = delete;
  ~Subprocess();

  // Writes a line (a newline is appended). Fails if the child closed its stdin.
  std::expected<void, std::string> write_line(std::string_view line);

  // Reads one stdout line, waiting at most `timeout`.
  std::expected<std::string, ReadError> read_line(std::chrono::milliseconds timeout);

  void close_stdin();

  // Non-blocking: the exit status if the child has exited.
  std::optional<int> poll_exit();

  // Waits up to `timeout` for exit, then sends SIGKILL. Returns the exit status
  // (128 + signal for signalled children).
  int wait_or_kill(std::chrono::milliseconds timeout);

  int pid() const { return pid_; }

 private:
  Subprocess() = default;
  void release();

  int pid_ = -1;
  int stdin_fd_ = -1;
  int stdout_fd_ = -1;
  std::string buffer_;
  std::optional<int> exit_status_;
  std::jthread stderr_thread_;
};

}  // namespace transpiler

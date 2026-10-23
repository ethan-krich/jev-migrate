#include "transpiler/process.hpp"

#include <fcntl.h>
#include <poll.h>
#include <signal.h>
#include <spawn.h>
#include <sys/wait.h>
#include <unistd.h>

#include <cerrno>
#include <cstring>
#include <format>
#include <utility>

extern char** environ;

namespace transpiler {
namespace {

int decode_status(int raw) {
  if (WIFEXITED(raw)) return WEXITSTATUS(raw);
  if (WIFSIGNALED(raw)) return 128 + WTERMSIG(raw);
  return -1;
}

void close_fd(int& fd) {
  if (fd >= 0) {
    ::close(fd);
    fd = -1;
  }
}

}  // namespace

std::expected<Subprocess, std::string> Subprocess::spawn(const std::vector<std::string>& argv, LineSink on_stderr) {
  if (argv.empty()) return std::unexpected("empty command line");
  int in[2], out[2], err[2];
  if (::pipe(in) != 0 || ::pipe(out) != 0 || ::pipe(err) != 0)
    return std::unexpected(std::format("pipe failed: {}", std::strerror(errno)));
  for (int fd : {in[1], out[0], err[0]}) ::fcntl(fd, F_SETFD, FD_CLOEXEC);

  posix_spawn_file_actions_t actions;
  posix_spawn_file_actions_init(&actions);
  posix_spawn_file_actions_adddup2(&actions, in[0], STDIN_FILENO);
  posix_spawn_file_actions_adddup2(&actions, out[1], STDOUT_FILENO);
  posix_spawn_file_actions_adddup2(&actions, err[1], STDERR_FILENO);
  for (int fd : {in[0], out[1], err[1]}) posix_spawn_file_actions_addclose(&actions, fd);

  std::vector<char*> args;
  for (const auto& a : argv) args.push_back(const_cast<char*>(a.c_str()));
  args.push_back(nullptr);

  pid_t pid = -1;
  int rc = ::posix_spawnp(&pid, args[0], &actions, nullptr, args.data(), environ);
  posix_spawn_file_actions_destroy(&actions);
  ::close(in[0]);
  ::close(out[1]);
  ::close(err[1]);
  if (rc != 0) {
    ::close(in[1]);
    ::close(out[0]);
    ::close(err[0]);
    return std::unexpected(std::format("cannot start '{}': {}", argv[0], std::strerror(rc)));
  }

  Subprocess p;
  p.pid_ = pid;
  p.stdin_fd_ = in[1];
  p.stdout_fd_ = out[0];
  ::signal(SIGPIPE, SIG_IGN);
  int err_fd = err[0];
  p.stderr_thread_ = std::jthread([err_fd, sink = std::move(on_stderr)] {
    std::string pending;
    char chunk[4096];
    ssize_t n;
    while ((n = ::read(err_fd, chunk, sizeof chunk)) > 0) {
      pending.append(chunk, static_cast<size_t>(n));
      for (size_t nl; (nl = pending.find('\n')) != std::string::npos; pending.erase(0, nl + 1))
        if (sink) sink(std::string_view(pending).substr(0, nl));
    }
    if (!pending.empty() && sink) sink(pending);
    ::close(err_fd);
  });
  return p;
}

Subprocess::Subprocess(Subprocess&& o) noexcept { *this = std::move(o); }

Subprocess& Subprocess::operator=(Subprocess&& o) noexcept {
  if (this != &o) {
    release();
    pid_ = std::exchange(o.pid_, -1);
    stdin_fd_ = std::exchange(o.stdin_fd_, -1);
    stdout_fd_ = std::exchange(o.stdout_fd_, -1);
    buffer_ = std::move(o.buffer_);
    exit_status_ = o.exit_status_;
    stderr_thread_ = std::move(o.stderr_thread_);
  }
  return *this;
}

Subprocess::~Subprocess() { release(); }

void Subprocess::release() {
  close_fd(stdin_fd_);
  if (pid_ > 0 && !exit_status_) wait_or_kill(std::chrono::milliseconds(2000));
  close_fd(stdout_fd_);
  if (stderr_thread_.joinable()) stderr_thread_.join();
  pid_ = -1;
}

std::expected<void, std::string> Subprocess::write_line(std::string_view line) {
  if (stdin_fd_ < 0) return std::unexpected("worker stdin is closed");
  std::string data(line);
  data.push_back('\n');
  size_t off = 0;
  while (off < data.size()) {
    ssize_t n = ::write(stdin_fd_, data.data() + off, data.size() - off);
    if (n < 0) {
      if (errno == EINTR) continue;
      return std::unexpected(std::format("write to worker failed: {}", std::strerror(errno)));
    }
    off += static_cast<size_t>(n);
  }
  return {};
}

std::expected<std::string, Subprocess::ReadError> Subprocess::read_line(std::chrono::milliseconds timeout) {
  auto deadline = std::chrono::steady_clock::now() + timeout;
  size_t nl;
  while ((nl = buffer_.find('\n')) == std::string::npos) {
    if (stdout_fd_ < 0) return std::unexpected(ReadError::eof);
    auto remaining = std::chrono::duration_cast<std::chrono::milliseconds>(deadline - std::chrono::steady_clock::now());
    if (remaining.count() <= 0) return std::unexpected(ReadError::timeout);
    pollfd pfd{stdout_fd_, POLLIN, 0};
    int rc = ::poll(&pfd, 1, static_cast<int>(remaining.count()));
    if (rc < 0) {
      if (errno == EINTR) continue;
      return std::unexpected(ReadError::io);
    }
    if (rc == 0) return std::unexpected(ReadError::timeout);
    char chunk[65536];
    ssize_t n = ::read(stdout_fd_, chunk, sizeof chunk);
    if (n < 0) {
      if (errno == EINTR) continue;
      return std::unexpected(ReadError::io);
    }
    if (n == 0)
      close_fd(stdout_fd_);
    else
      buffer_.append(chunk, static_cast<size_t>(n));
  }
  std::string line = buffer_.substr(0, nl);
  buffer_.erase(0, nl + 1);
  return line;
}

void Subprocess::close_stdin() { close_fd(stdin_fd_); }

std::optional<int> Subprocess::poll_exit() {
  if (exit_status_) return exit_status_;
  if (pid_ <= 0) return std::nullopt;
  int raw = 0;
  if (::waitpid(pid_, &raw, WNOHANG) == pid_) exit_status_ = decode_status(raw);
  return exit_status_;
}

int Subprocess::wait_or_kill(std::chrono::milliseconds timeout) {
  auto deadline = std::chrono::steady_clock::now() + timeout;
  while (!poll_exit()) {
    if (std::chrono::steady_clock::now() >= deadline) {
      ::kill(pid_, SIGKILL);
      int raw = 0;
      ::waitpid(pid_, &raw, 0);
      exit_status_ = decode_status(raw);
      break;
    }
    std::this_thread::sleep_for(std::chrono::milliseconds(10));
  }
  return *exit_status_;
}

}  // namespace transpiler

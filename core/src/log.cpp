#include "transpiler/log.hpp"

#include <atomic>
#include <cstdio>
#include <mutex>

namespace transpiler {
namespace {
std::atomic<LogLevel> g_level{LogLevel::info};
std::mutex g_mutex;

constexpr std::string_view label(LogLevel level) {
  switch (level) {
    case LogLevel::debug: return "debug";
    case LogLevel::info: return "info";
    case LogLevel::warn: return "warn";
    case LogLevel::error: return "error";
  }
  return "?";
}
}  // namespace

void set_log_level(LogLevel level) { g_level = level; }
LogLevel log_level() { return g_level; }

void log_line(LogLevel level, std::string_view message) {
  std::scoped_lock lock(g_mutex);
  std::fprintf(stderr, "[transpiler-core] %.*s: %.*s\n", static_cast<int>(label(level).size()), label(level).data(),
               static_cast<int>(message.size()), message.data());
}

}  // namespace transpiler

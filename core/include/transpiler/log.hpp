#pragma once

#include <format>
#include <string_view>

namespace transpiler {

enum class LogLevel { debug, info, warn, error };

// Diagnostics go to stderr only; stdout is reserved for machine-readable output.
void set_log_level(LogLevel level);
LogLevel log_level();
void log_line(LogLevel level, std::string_view message);

template <class... Args>
void log(LogLevel level, std::format_string<Args...> fmt, Args&&... args) {
  if (level >= log_level()) log_line(level, std::format(fmt, std::forward<Args>(args)...));
}

}  // namespace transpiler

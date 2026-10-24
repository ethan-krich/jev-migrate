#pragma once

#include <expected>
#include <filesystem>
#include <optional>
#include <string>
#include <vector>

namespace transpiler {

// Language-neutral rules supplied by the language package (e.g. cobol_to_java.Layout).
struct DiscoveryRules {
  std::vector<std::string> program_extensions;   // ".cbl", ".cob"
  std::vector<std::string> copybook_extensions;  // ".cpy"
  std::vector<std::string> copybook_dirs;        // directory names whose files are copybooks
  std::vector<std::string> exclude;              // glob patterns relative to the source root
};

struct DiscoveredFile {
  std::filesystem::path path;      // absolute, normalised
  std::string relative;            // relative to the source root, '/' separated
  enum class Role { program, copybook } role;
};

struct Discovery {
  std::filesystem::path source_root;
  std::vector<DiscoveredFile> files;  // programs and copybooks, sorted by relative path
  std::vector<std::string> ignored;   // files under the inputs that matched no rule
};

// Expands inputs (files, directories, glob patterns with *, ?, [..] and **), classifies files, and
// adds every copybook under the source root so programs given individually still find theirs.
// The source root defaults to the deepest common directory of the inputs.
std::expected<Discovery, std::string> discover(const std::vector<std::string>& inputs,
                                               const std::optional<std::filesystem::path>& source_root,
                                               const DiscoveryRules& rules);

// Glob matching of one '/'-separated relative path; exposed for tests.
bool glob_match(std::string_view pattern, std::string_view path);

}  // namespace transpiler

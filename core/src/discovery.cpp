#include "transpiler/discovery.hpp"

#include <fnmatch.h>

#include <algorithm>
#include <cctype>
#include <format>
#include <map>
#include <optional>
#include <ranges>
#include <set>
#include <span>

namespace transpiler {
namespace fs = std::filesystem;

namespace {

std::string lower(std::string s) {
  std::ranges::transform(s, s.begin(), [](unsigned char c) { return static_cast<char>(std::tolower(c)); });
  return s;
}

bool has_glob(std::string_view s) { return s.find_first_of("*?[") != std::string_view::npos; }

std::vector<std::string> split(std::string_view path) {
  std::vector<std::string> parts;
  for (auto part : path | std::views::split('/'))
    if (!std::ranges::empty(part)) parts.emplace_back(part.begin(), part.end());
  return parts;
}

bool match_parts(std::span<const std::string> pat, std::span<const std::string> path) {
  if (pat.empty()) return path.empty();
  if (pat.front() == "**") {
    for (size_t skip = 0; skip <= path.size(); ++skip)
      if (match_parts(pat.subspan(1), path.subspan(skip))) return true;
    return false;
  }
  if (path.empty()) return false;
  if (::fnmatch(pat.front().c_str(), path.front().c_str(), FNM_PERIOD) != 0) return false;
  return match_parts(pat.subspan(1), path.subspan(1));
}

// The non-glob directory prefix of a pattern, where expansion starts.
fs::path glob_base(const std::string& pattern) {
  fs::path base;
  for (const auto& part : fs::path(pattern)) {
    if (has_glob(part.string())) break;
    base /= part;
  }
  return base;
}

void walk(const fs::path& dir, std::vector<fs::path>& out) {
  std::error_code ec;
  for (auto it = fs::recursive_directory_iterator(dir, fs::directory_options::skip_permission_denied, ec);
       it != fs::recursive_directory_iterator(); it.increment(ec)) {
    if (ec) break;
    if (it->is_regular_file(ec) && !it->path().filename().string().starts_with(".")) out.push_back(it->path());
  }
}

fs::path common_dir(const std::vector<fs::path>& paths) {
  fs::path common = paths.front().parent_path();
  for (const auto& p : paths) {
    fs::path dir = p.parent_path();
    while (!common.empty() && std::mismatch(common.begin(), common.end(), dir.begin(), dir.end()).first != common.end())
      common = common.parent_path();
  }
  return common;
}

}  // namespace

bool glob_match(std::string_view pattern, std::string_view path) {
  auto pat = split(pattern);
  auto parts = split(path);
  return match_parts(pat, parts);
}

std::expected<Discovery, std::string> discover(const std::vector<std::string>& inputs,
                                               const std::optional<fs::path>& source_root,
                                               const DiscoveryRules& rules) {
  if (inputs.empty()) return std::unexpected("no inputs given");
  std::vector<fs::path> candidates;
  std::set<fs::path> explicit_files;
  std::vector<fs::path> anchors;  // used to infer the source root
  for (const auto& input : inputs) {
    std::error_code ec;
    if (has_glob(input)) {
      fs::path base = fs::absolute(glob_base(input)).lexically_normal();
      std::string pattern = fs::absolute(fs::path(input)).lexically_normal().generic_string();
      std::vector<fs::path> all;
      if (fs::is_directory(base, ec)) walk(base, all);
      size_t before = candidates.size();
      for (auto& p : all)
        if (glob_match(pattern, p.generic_string())) candidates.push_back(p);
      if (candidates.size() == before) return std::unexpected(std::format("pattern '{}' matched no files", input));
      anchors.push_back(base / "x");
    } else {
      fs::path p = fs::absolute(input).lexically_normal();
      if (fs::is_directory(p, ec)) {
        walk(p, candidates);
        anchors.push_back(p / "x");
      } else if (fs::is_regular_file(p, ec)) {
        candidates.push_back(p);
        explicit_files.insert(p);
        anchors.push_back(p);
      } else {
        return std::unexpected(std::format("input '{}' does not exist", input));
      }
    }
  }

  Discovery d;
  d.source_root = source_root ? fs::absolute(*source_root).lexically_normal() : common_dir(anchors);
  auto relative = [&](const fs::path& p) -> std::optional<std::string> {
    auto rel = p.lexically_relative(d.source_root);
    if (rel.empty() || *rel.begin() == "..") return std::nullopt;
    return rel.generic_string();
  };
  auto ext_in = [](const fs::path& p, const std::vector<std::string>& exts) {
    std::string e = lower(p.extension().string());
    return std::ranges::any_of(exts, [&](const std::string& x) { return lower(x) == e; });
  };
  auto in_copybook_dir = [&](const std::string& rel) {
    auto parts = split(rel);
    parts.pop_back();
    return std::ranges::any_of(parts, [&](const std::string& part) {
      return std::ranges::any_of(rules.copybook_dirs, [&](const std::string& c) { return lower(c) == lower(part); });
    });
  };
  auto excluded = [&](const std::string& rel) {
    return std::ranges::any_of(rules.exclude, [&](const std::string& pat) { return glob_match(pat, rel); });
  };

  std::map<std::string, DiscoveredFile> by_rel;
  auto classify = [&](const fs::path& p, bool must_be_program) -> std::expected<void, std::string> {
    auto rel = relative(p);
    if (!rel) return std::unexpected(std::format("{} is outside the source root {}", p.string(), d.source_root.string()));
    if (excluded(*rel)) return {};
    std::optional<DiscoveredFile::Role> role;
    if (ext_in(p, rules.copybook_extensions) || in_copybook_dir(*rel))
      role = DiscoveredFile::Role::copybook;
    else if (ext_in(p, rules.program_extensions))
      role = DiscoveredFile::Role::program;
    if (!role) {
      if (must_be_program)
        return std::unexpected(std::format("{} has no recognised program extension", *rel));
      d.ignored.push_back(*rel);
      return {};
    }
    by_rel.try_emplace(*rel, DiscoveredFile{p, *rel, *role});
    return {};
  };

  for (const auto& p : candidates)
    if (auto r = classify(p, explicit_files.contains(p)); !r) return std::unexpected(r.error());

  // Programs may COPY members that were not named in the inputs.
  std::vector<fs::path> all;
  walk(d.source_root, all);
  for (const auto& p : all) {
    auto rel = relative(p);
    if (rel && !by_rel.contains(*rel) && !excluded(*rel) && (ext_in(p, rules.copybook_extensions) || in_copybook_dir(*rel)))
      by_rel.try_emplace(*rel, DiscoveredFile{p, *rel, DiscoveredFile::Role::copybook});
  }

  for (auto& [rel, f] : by_rel) d.files.push_back(std::move(f));
  std::ranges::sort(d.ignored);
  d.ignored.erase(std::ranges::unique(d.ignored).begin(), d.ignored.end());
  if (std::ranges::none_of(d.files, [](const auto& f) { return f.role == DiscoveredFile::Role::program; }))
    return std::unexpected("no program files found in the inputs");
  return d;
}

}  // namespace transpiler

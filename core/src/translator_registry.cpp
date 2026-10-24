#include "transpiler/translator_registry.hpp"

#include <algorithm>
#include <format>
#include <fstream>

#include "transpiler/protocol.hpp"

namespace transpiler {

using protocol::json;

WorkerSpec TranslatorManifest::worker_spec() const {
  WorkerSpec spec;
  for (auto arg : command) {
    for (size_t pos; (pos = arg.find("{translator_dir}")) != std::string::npos;)
      arg.replace(pos, std::string_view("{translator_dir}").size(), directory.string());
    spec.command.push_back(std::move(arg));
  }
  spec.startup_timeout = startup_timeout;
  return spec;
}

std::expected<TranslatorManifest, std::string> load_manifest(const std::filesystem::path& file) {
  std::ifstream in(file);
  if (!in) return std::unexpected(std::format("cannot read {}", file.string()));
  json j = json::parse(in, nullptr, false);
  if (j.is_discarded() || !j.is_object()) return std::unexpected(std::format("{} is not a JSON object", file.string()));
  TranslatorManifest m;
  try {
    m.name = j.at("name").get<std::string>();
    m.source_language = j.at("source_language").get<std::string>();
    m.target_language = j.at("target_language").get<std::string>();
    m.protocol = j.at("protocol").get<std::string>();
    m.command = j.at("command").get<std::vector<std::string>>();
    m.startup_timeout = std::chrono::seconds(j.value("startup_timeout_seconds", 60));
  } catch (const json::exception& e) {
    return std::unexpected(std::format("{}: {}", file.string(), e.what()));
  }
  if (m.protocol != protocol::kVersion)
    return std::unexpected(std::format("{}: protocol '{}' is not supported (expected '{}')", file.string(), m.protocol,
                                       protocol::kVersion));
  if (m.command.empty()) return std::unexpected(std::format("{}: empty command", file.string()));
  m.directory = std::filesystem::absolute(file).parent_path();
  return m;
}

std::expected<TranslatorRegistry, std::string> TranslatorRegistry::scan(const std::filesystem::path& root) {
  TranslatorRegistry registry;
  std::error_code ec;
  if (!std::filesystem::is_directory(root, ec)) return std::unexpected(std::format("translators directory {} not found", root.string()));
  for (const auto& entry : std::filesystem::directory_iterator(root)) {
    auto manifest = entry.path() / "translator.json";
    if (!entry.is_directory() || !std::filesystem::exists(manifest)) continue;
    auto m = load_manifest(manifest);
    if (!m) return std::unexpected(m.error());
    registry.manifests_.push_back(std::move(*m));
  }
  std::ranges::sort(registry.manifests_, {}, &TranslatorManifest::name);
  return registry;
}

std::expected<TranslatorManifest, std::string> TranslatorRegistry::find(const std::optional<std::string>& name,
                                                                        const std::string& source_language,
                                                                        const std::string& target_language) const {
  std::vector<const TranslatorManifest*> matches;
  for (const auto& m : manifests_) {
    if (name ? m.name == *name : (m.source_language == source_language && m.target_language == target_language))
      matches.push_back(&m);
  }
  if (matches.empty())
    return std::unexpected(name ? std::format("no translator named '{}'", *name)
                                : std::format("no translator for {} -> {}", source_language, target_language));
  if (matches.size() > 1)
    return std::unexpected(std::format("several translators for {} -> {}; name one explicitly", source_language, target_language));
  return *matches.front();
}

}  // namespace transpiler

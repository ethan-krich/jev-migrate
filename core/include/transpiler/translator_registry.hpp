#pragma once

#include <expected>
#include <filesystem>
#include <optional>
#include <string>
#include <vector>

#include "transpiler/worker_client.hpp"

namespace transpiler {

// A language pair, described by translators/<name>/translator.json. Any program that speaks the
// worker protocol can be registered; the orchestrator knows nothing else about it.
struct TranslatorManifest {
  std::string name;
  std::string source_language;
  std::string target_language;
  std::string protocol;
  std::vector<std::string> command;  // "{translator_dir}" is substituted
  std::filesystem::path directory;
  std::chrono::milliseconds startup_timeout{60'000};

  WorkerSpec worker_spec() const;
};

std::expected<TranslatorManifest, std::string> load_manifest(const std::filesystem::path& file);

class TranslatorRegistry {
 public:
  // Loads every translators/*/translator.json below `root`; unreadable manifests are errors.
  static std::expected<TranslatorRegistry, std::string> scan(const std::filesystem::path& root);

  std::expected<TranslatorManifest, std::string> find(const std::optional<std::string>& name,
                                                      const std::string& source_language,
                                                      const std::string& target_language) const;

  const std::vector<TranslatorManifest>& all() const { return manifests_; }

 private:
  std::vector<TranslatorManifest> manifests_;
};

}  // namespace transpiler

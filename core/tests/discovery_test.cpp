#include <gtest/gtest.h>

#include <fstream>
#include <unistd.h>

#include "transpiler/discovery.hpp"

using namespace transpiler;
namespace fs = std::filesystem;

class DiscoveryTest : public testing::Test {
 protected:
  void SetUp() override {
    root_ = fs::temp_directory_path() / ("discovery-" + std::to_string(::getpid()) + "-" +
                                         testing::UnitTest::GetInstance()->current_test_info()->name());
    fs::remove_all(root_);
    for (const char* f : {"billing/PAY.cbl", "billing/copy/ACCT.cpy", "billing/notes.txt", "reports/month-end/RPT.COB",
                          "shared/copybooks/COMMON", "top.cbl"}) {
      fs::create_directories((root_ / f).parent_path());
      std::ofstream(root_ / f) << "x";
    }
  }
  void TearDown() override { fs::remove_all(root_); }

  DiscoveryRules rules() const { return {{".cbl", ".cob"}, {".cpy"}, {"copy", "copybooks"}, {}}; }

  static std::vector<std::string> rels(const Discovery& d, DiscoveredFile::Role role) {
    std::vector<std::string> out;
    for (const auto& f : d.files)
      if (f.role == role) out.push_back(f.relative);
    return out;
  }

  fs::path root_;
};

TEST_F(DiscoveryTest, DirectoryClassifiesProgramsAndCopybooks) {
  auto d = discover({root_.string()}, std::nullopt, rules());
  ASSERT_TRUE(d.has_value()) << d.error();
  EXPECT_EQ(d->source_root, root_);
  EXPECT_EQ(rels(*d, DiscoveredFile::Role::program),
            (std::vector<std::string>{"billing/PAY.cbl", "reports/month-end/RPT.COB", "top.cbl"}));
  EXPECT_EQ(rels(*d, DiscoveredFile::Role::copybook),
            (std::vector<std::string>{"billing/copy/ACCT.cpy", "shared/copybooks/COMMON"}));
  EXPECT_EQ(d->ignored, (std::vector<std::string>{"billing/notes.txt"}));
}

TEST_F(DiscoveryTest, GlobsIncludingRecursive) {
  auto d = discover({(root_ / "**/*.cbl").string()}, root_, rules());
  ASSERT_TRUE(d.has_value()) << d.error();
  EXPECT_EQ(rels(*d, DiscoveredFile::Role::program), (std::vector<std::string>{"billing/PAY.cbl", "top.cbl"}));
  // Copybooks under the source root are always discovered.
  EXPECT_EQ(rels(*d, DiscoveredFile::Role::copybook).size(), 2u);
}

TEST_F(DiscoveryTest, ExplicitFilesInferTheirCommonRoot) {
  auto d = discover({(root_ / "billing/PAY.cbl").string()}, std::nullopt, rules());
  ASSERT_TRUE(d.has_value()) << d.error();
  EXPECT_EQ(d->source_root, root_ / "billing");
  EXPECT_EQ(rels(*d, DiscoveredFile::Role::copybook), (std::vector<std::string>{"copy/ACCT.cpy"}));
}

TEST_F(DiscoveryTest, Excludes) {
  auto r = rules();
  r.exclude = {"reports/**"};
  auto d = discover({root_.string()}, std::nullopt, r);
  ASSERT_TRUE(d.has_value());
  EXPECT_EQ(rels(*d, DiscoveredFile::Role::program), (std::vector<std::string>{"billing/PAY.cbl", "top.cbl"}));
}

TEST_F(DiscoveryTest, Errors) {
  EXPECT_FALSE(discover({(root_ / "missing.cbl").string()}, std::nullopt, rules()).has_value());
  EXPECT_FALSE(discover({(root_ / "*.nothing").string()}, std::nullopt, rules()).has_value());
  EXPECT_FALSE(discover({(root_ / "billing/notes.txt").string()}, std::nullopt, rules()).has_value());
  EXPECT_FALSE(discover({(root_ / "top.cbl").string()}, root_ / "billing", rules()).has_value());
}

TEST(Glob, Matching) {
  EXPECT_TRUE(glob_match("a/*.cbl", "a/x.cbl"));
  EXPECT_FALSE(glob_match("a/*.cbl", "a/b/x.cbl"));
  EXPECT_TRUE(glob_match("a/**/x.cbl", "a/x.cbl"));
  EXPECT_TRUE(glob_match("a/**/x.cbl", "a/b/c/x.cbl"));
  EXPECT_TRUE(glob_match("**", "anything/at/all"));
  EXPECT_TRUE(glob_match("PAY?.cbl", "PAY1.cbl"));
  EXPECT_TRUE(glob_match("[AB]*.cbl", "B1.cbl"));
}

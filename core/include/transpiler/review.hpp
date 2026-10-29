#pragma once

#include <string>

#include "transpiler/protocol.hpp"

namespace transpiler {

// <output>/review.md: one table row per decision of a run report, built mechanically from the
// report's fields (no interpretation). Rows are sorted pending first, then by review_priority
// level (highest first, none last), then by importance (critical first). A row is flagged
// "tier mismatch" when the provider's review_priority is business or higher on a low-importance
// decision, or cosmetic on a critical one.
std::string review_table(const protocol::json& report);

}  // namespace transpiler

rootProject.name = "cobol-to-java"

// ProLeap COBOL parser, compiled from the pinned git submodule (third_party/).
include(":proleap")
// Dependency-free COBOL semantics runtime; its sources are copied into generated output.
include(":runtime")

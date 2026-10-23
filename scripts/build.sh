#!/usr/bin/env bash
# Builds the C++ orchestrator, the Java worker and the Python workspace.
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

[[ -f "$ROOT/third_party/proleap-cobol-parser/pom.xml" ]] || git -C "$ROOT" submodule update --init --recursive

info "C++ core -> $BUILD_DIR/core"
cmake -S "$ROOT/core" -B "$BUILD_DIR/core" -DCMAKE_BUILD_TYPE="${CMAKE_BUILD_TYPE:-Release}" >/dev/null
cmake --build "$BUILD_DIR/core" --parallel "$(jobs_count)"

info "Java worker (Gradle)"
(cd "$JAVA_DIR" && ./gradlew --quiet installDist)

info "Python workspace (uv)"
(cd "$ROOT" && uv sync --frozen --quiet)

info "build complete: $TRANSPILER_CORE"

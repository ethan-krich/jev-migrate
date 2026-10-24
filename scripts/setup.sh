#!/usr/bin/env bash
# Checks prerequisites and prepares dependencies (submodule, Python env, Gradle, CMake deps).
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

missing=0
require() {  # require <command> <why> <hint>
  if have "$1"; then info "found $1: $($1 --version 2>&1 | head -1)"; else warn "missing $1 ($2). $3"; missing=1; fi
}
require git "submodules" "Install git."
require java "JDK 21+ runs the Java worker and compiles generated code" "Install a JDK 21+ (e.g. brew install openjdk@21, apt install openjdk-21-jdk-headless)."
require javac "compiles generated Java in tests" "Install a full JDK, not only a JRE."
require cmake "builds the C++ core (3.28+)" "brew install cmake / apt install cmake"
require c++ "C++23 compiler (clang 17+ or GCC 14+)" "Install Xcode command line tools or g++-14."
require uv "Python 3.11+ and dependency management" "See https://docs.astral.sh/uv/getting-started/installation/"
if have java; then
  v=$(java -version 2>&1 | sed -nE 's/.*version "([0-9]+).*/\1/p' | head -1)
  [[ "${v:-0}" -ge 21 ]] || { warn "Java $v found; 21+ is required"; missing=1; }
fi
if have cobc; then
  info "found cobc: $(cobc --version | head -1)"
  cobc --version | head -1 | grep -q "3.2" || warn "equivalence fixtures were recorded with GnuCOBOL 3.2; other versions may format output differently (use ./scripts/docker.sh test)"
else
  warn "cobc (GnuCOBOL) not found: equivalence tests will compare against recorded GnuCOBOL 3.2 output. For live comparison use ./scripts/docker.sh test, or brew install gnucobol / apt install gnucobol3."
fi
[[ $missing -eq 0 ]] || die "install the missing prerequisites above, or use ./scripts/docker.sh"

info "initialising submodules (ProLeap COBOL parser)"
git -C "$ROOT" submodule update --init --recursive

info "syncing Python workspace"
(cd "$ROOT" && uv sync --frozen)

info "fetching Gradle and Java dependencies"
(cd "$JAVA_DIR" && ./gradlew --quiet dependencies >/dev/null)

info "configuring C++ build in $BUILD_DIR/core"
cmake -S "$ROOT/core" -B "$BUILD_DIR/core" -DCMAKE_BUILD_TYPE=Release >/dev/null

info "setup complete; next: ./scripts/build.sh"

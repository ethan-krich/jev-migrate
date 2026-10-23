#!/usr/bin/env bash
# Runs all test suites: GoogleTest (CTest), JUnit 5 (Gradle), pytest (unit, integration,
# end-to-end and COBOL/Java equivalence). Usage: test.sh [cpp|java|python ...] [-- pytest args]
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

suites=()
while [[ $# -gt 0 && "$1" != "--" ]]; do suites+=("$1"); shift; done
[[ "${1:-}" == "--" ]] && shift
[[ ${#suites[@]} -gt 0 ]] || suites=(cpp java python)

"$ROOT/scripts/build.sh"
status=0
for suite in "${suites[@]}"; do
  case "$suite" in
    cpp) info "C++ unit tests (CTest + GoogleTest)"
         ctest --test-dir "$BUILD_DIR/core" --output-on-failure --parallel "$(jobs_count)" || status=1 ;;
    java) info "Java unit tests (JUnit 5)"
          (cd "$JAVA_DIR" && ./gradlew --quiet test) || status=1 ;;
    python) info "Python, integration and equivalence tests (pytest)"
            (cd "$ROOT" && uv run --frozen pytest "$@") || status=1 ;;
    *) die "unknown suite '$suite' (cpp, java, python)" ;;
  esac
done
[[ $status -eq 0 ]] && info "all requested suites passed" || die "some suites failed"

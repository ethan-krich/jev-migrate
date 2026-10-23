#!/usr/bin/env bash
# Translates COBOL to Java with the Python CLI. Without arguments, runs the bundled example.
#   ./scripts/run.sh SOURCE... --output DIR [--base-package PKG] [--mappings FILE.py]
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

[[ -x "$TRANSPILER_CORE" ]] || "$ROOT/scripts/build.sh"
cd "$ROOT"
if [[ $# -eq 0 ]]; then
  exec uv run --frozen python examples/cobol-to-java/run_example.py
fi
exec uv run --frozen python -m transpiler "$@"

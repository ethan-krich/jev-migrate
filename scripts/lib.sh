# Shared helpers for the top-level scripts. Source it; do not execute.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD_DIR="${TRANSPILER_BUILD_DIR:-$ROOT/build}"
JAVA_DIR="$ROOT/translators/cobol-to-java/java"
export TRANSPILER_CORE="${TRANSPILER_CORE:-$BUILD_DIR/core/transpiler-core}"

info() { printf '\033[1;34m==>\033[0m %s\n' "$*" >&2; }
warn() { printf '\033[1;33mwarning:\033[0m %s\n' "$*" >&2; }
die() { printf '\033[1;31merror:\033[0m %s\n' "$*" >&2; exit 1; }
have() { command -v "$1" >/dev/null 2>&1; }

jobs_count() { getconf _NPROCESSORS_ONLN 2>/dev/null || echo 4; }

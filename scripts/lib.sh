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

# Exports the KEY=value lines of $ROOT/.env (read as data, never executed; quotes and a leading
# `export` are allowed). Variables already in the environment take precedence.
load_env() {
  [[ -f "$ROOT/.env" ]] || return 0
  local line name value
  while IFS= read -r line || [[ -n "$line" ]]; do
    line="${line#"${line%%[![:space:]]*}"}"
    line="${line#export }"
    [[ "$line" =~ ^([A-Za-z_][A-Za-z0-9_]*)=(.*)$ ]] || continue
    name="${BASH_REMATCH[1]}" value="${BASH_REMATCH[2]%$'\r'}"
    if [[ "$value" =~ ^\"(.*)\"$ || "$value" =~ ^\'(.*)\'$ ]]; then value="${BASH_REMATCH[1]}"; fi
    [[ -n "${!name+set}" ]] || export "$name=$value"
  done < "$ROOT/.env"
}

# Chooses the live Jev endpoint (after load_env) and sets endpoint, key_env, model and base_url.
# JEV_ENDPOINT=openrouter|typesafe chooses when both keys are set (default: openrouter if its key
# is set); JEV_MODEL overrides the model, JEV_API_KEY_ENV the key variable.
jev_endpoint() {
  endpoint="${JEV_ENDPOINT:-$([[ -n "${OPENROUTER_API_KEY:-}" ]] && echo openrouter || echo typesafe)}"
  case "$endpoint" in
    openrouter) key_env="${JEV_API_KEY_ENV:-OPENROUTER_API_KEY}" model="${JEV_MODEL:-jev-latest}"
                base_url="https://openrouter.ai/api" ;;
    typesafe)   key_env="${JEV_API_KEY_ENV:-TYPESAFE_API_KEY}" model="${JEV_MODEL:-jev-preview}"
                base_url="${TYPESAFE_BASE_URL:-}" ;;
    *) die "JEV_ENDPOINT must be openrouter or typesafe, not '$endpoint'" ;;
  esac
  [[ -n "${!key_env:-}" ]] || die "set OPENROUTER_API_KEY or TYPESAFE_API_KEY, in the environment or in .env (this calls the live Jev API; $key_env is empty)"
}

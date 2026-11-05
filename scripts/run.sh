#!/usr/bin/env bash
# Translates COBOL to Java with the Python CLI, resolving open decisions with the live Jev API.
#   ./scripts/run.sh SOURCE... [--output DIR] [--base-package PKG] [--mappings FILE.py] [CLI options]
# --output defaults to generated/ (git-ignored). The API key comes from OPENROUTER_API_KEY or
# TYPESAFE_API_KEY, in the environment or in .env (see .env.example). JEV_ENDPOINT, JEV_MODEL,
# JEV_API_KEY_ENV and JEV_TIMEOUT_SECONDS work as in jev-smoke.sh.
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

if [[ $# -eq 0 ]]; then
  echo "usage: ./scripts/run.sh SOURCE... [--output DIR] [--base-package PKG] [--mappings FILE.py]" >&2
  exit 2
fi
load_env
jev_endpoint
has_output=0
for arg in "$@"; do
  case "$arg" in --output|--output=*|-o|-o?*) has_output=1 ;; esac
done
[[ $has_output -eq 1 ]] || set -- "$@" --output "$ROOT/generated"
jev=(--jev "$model" --jev-api-key-env "$key_env" --jev-timeout "${JEV_TIMEOUT_SECONDS:-120}")
[[ -n "$base_url" ]] && jev+=(--jev-base-url "$base_url")

# Incremental: picks up any change to the core or the worker since the last run.
"$ROOT/scripts/build.sh" >&2
info "translating with Jev via $endpoint (model $model, key from \$$key_env)"
# --project selects the workspace without changing cwd, so relative CLI paths resolve for the caller.
# --all-packages --extra jev installs the optional typesafe_sdk dependency from uv.lock.
exec uv run --project "$ROOT" --frozen --all-packages --extra jev python -m transpiler "$@" "${jev[@]}"

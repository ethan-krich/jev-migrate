#!/usr/bin/env bash
# Opt-in live run of examples/cobol-to-java with Jev as the decision provider. The DISPLAY rule
# is removed (as in the README) so the example has decisions for Jev to answer. It calls the
# API, so it is never part of test.sh or docker.sh test.
#   OPENROUTER_API_KEY=... ./scripts/jev-smoke.sh     # through OpenRouter (model jev-latest)
#   TYPESAFE_API_KEY=... ./scripts/jev-smoke.sh       # TypeSafe's own API (model jev-preview)
# JEV_ENDPOINT=openrouter|typesafe chooses when both keys are set (default: openrouter if its key is
# set). JEV_MODEL overrides the model, JEV_API_KEY_ENV the key variable, JEV_TIMEOUT_SECONDS
# (default 120) the timeout of each API call. Any of these may also be set in $ROOT/.env (see
# .env.example); variables already in the environment take precedence.
# Exit status: 0 when the run finished (ok or incomplete), 1 when it failed.
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

# KEY=value lines of .env, read as data (never executed); quotes and a leading `export` are allowed.
if [[ -f "$ROOT/.env" ]]; then
  while IFS= read -r line || [[ -n "$line" ]]; do
    line="${line#"${line%%[![:space:]]*}"}"
    line="${line#export }"
    [[ "$line" =~ ^([A-Za-z_][A-Za-z0-9_]*)=(.*)$ ]] || continue
    name="${BASH_REMATCH[1]}" value="${BASH_REMATCH[2]%$'\r'}"
    if [[ "$value" =~ ^\"(.*)\"$ || "$value" =~ ^\'(.*)\'$ ]]; then value="${BASH_REMATCH[1]}"; fi
    [[ -n "${!name+set}" ]] || export "$name=$value"
  done < "$ROOT/.env"
fi

endpoint="${JEV_ENDPOINT:-$([[ -n "${OPENROUTER_API_KEY:-}" ]] && echo openrouter || echo typesafe)}"
case "$endpoint" in
  openrouter) key_env="${JEV_API_KEY_ENV:-OPENROUTER_API_KEY}" model="${JEV_MODEL:-jev-latest}"
              base_url="https://openrouter.ai/api" ;;
  typesafe)   key_env="${JEV_API_KEY_ENV:-TYPESAFE_API_KEY}" model="${JEV_MODEL:-jev-preview}"
              base_url="${TYPESAFE_BASE_URL:-}" ;;
  *) die "JEV_ENDPOINT must be openrouter or typesafe, not '$endpoint'" ;;
esac
[[ -n "${!key_env:-}" ]] || die "set OPENROUTER_API_KEY or TYPESAFE_API_KEY (this script calls the live API; $key_env is empty)"
[[ -x "$TRANSPILER_CORE" ]] || "$ROOT/scripts/build.sh"

info "examples/cobol-to-java with JevProvider via $endpoint (model $model, key from \$$key_env)"
# --all-packages --extra jev installs the optional typesafe_sdk dependency from uv.lock.
ROOT="$ROOT" JEV_API_KEY_ENV="$key_env" JEV_MODEL="$model" JEV_BASE_URL="$base_url" \
  uv run --project "$ROOT" --frozen --all-packages --extra jev python - <<'PY'
import os
import sys
from pathlib import Path

from cobol_to_java import Layout, default_mappings
from transpiler import ContextBudget, Gate, JevProvider, transpile

root = Path(os.environ["ROOT"])
example = root / "examples/cobol-to-java"
sys.path.insert(0, str(example))
from mappings import configure  # noqa: E402

mappings = configure(default_mappings())
mappings.remove("DISPLAY")
jev = JevProvider(os.environ["JEV_MODEL"], Gate(), ContextBudget(), api_key_env=os.environ["JEV_API_KEY_ENV"],
                  base_url=os.environ["JEV_BASE_URL"] or None,
                  timeout_seconds=float(os.environ.get("JEV_TIMEOUT_SECONDS", "120")))
result = transpile(example / "input", output=root / "build/examples/cobol-to-java-jev",
                   mappings=mappings, layout=Layout(base_package="com.example.legacy"),
                   provider=jev)
print(result.summary())
r = result.resolutions
print(f"\njev: {len(r.applied)} applied, {len(r.held)} held, {len(r.rejected)} rejected")
for x in r:
    rp = x.review_priority.label if x.review_priority else "-"
    print(f"  {x.outcome:8} {x.decision_id} [{x.importance}] gate={x.gate} confidence={x.confidence} "
          f"review={rp} -> {x.candidate_id}")
print(f"\nreview queue: {result.output / 'review.md'}")
sys.exit(1 if result.status == "failed" else 0)
PY

#!/usr/bin/env bash
# Opt-in live run of examples/cobol-to-java with Jev as the decision provider. The DISPLAY rule
# is removed (as in the README) so the example has decisions for Jev to answer. It calls the
# TypeSafe API, so it is never part of test.sh or docker.sh test.
#   TYPESAFE_API_KEY=... ./scripts/jev-smoke.sh
# JEV_MODEL (default jev-preview) picks the model; JEV_API_KEY_ENV names another key variable.
# Exit status: 0 when the run finished (ok or incomplete), 1 when it failed.
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

key_env="${JEV_API_KEY_ENV:-TYPESAFE_API_KEY}"
[[ -n "${!key_env:-}" ]] || die "set $key_env to a TypeSafe API key (this script calls the live API)"
[[ -x "$TRANSPILER_CORE" ]] || "$ROOT/scripts/build.sh"

info "examples/cobol-to-java with JevProvider (model ${JEV_MODEL:-jev-preview})"
# --all-packages --extra jev installs the optional typesafe_sdk dependency from uv.lock.
ROOT="$ROOT" JEV_API_KEY_ENV="$key_env" \
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
jev = JevProvider(os.environ.get("JEV_MODEL", "jev-preview"), Gate(), ContextBudget(),
                  api_key_env=os.environ["JEV_API_KEY_ENV"])
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

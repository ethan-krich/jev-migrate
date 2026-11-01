"""Golden tests of the decision context graph (``context.refs``) the worker builds from the ASG.

Regenerate the goldens after an intended change with ``UPDATE_GOLDEN=1 pytest tests/integration/test_context.py``.
"""

from __future__ import annotations

import json
import os
from pathlib import Path

import pytest
from cobol_to_java import default_mappings
from test_worker_protocol import Worker, init_params
from support import FIXTURES

pytestmark = pytest.mark.integration
GOLDEN = Path(__file__).parent / "golden/context"

# name -> (fixture, program, operation removed from the mappings, decision line, context budget or None)
CASES = {
    "basics_display_default_budget": ("basics", "BASICS.cbl", "DISPLAY", 23, None),
    "basics_display_truncated": ("basics", "BASICS.cbl", "DISPLAY", 23, {"max_depth": 1, "max_chars": 120}),
    "multi_program_copybook_move": ("multi_program", "billing/PAY-MAIN.cbl", "MOVE", None, {"max_depth": 1, "max_chars": 600}),
}


def context_of(tmp_path: Path, fixture: str, program: str, removed: str, line: int | None, budget: dict | None) -> dict:
    src = FIXTURES / fixture / "src"
    mappings = default_mappings()
    mappings.remove(removed)
    params = init_params(tmp_path, [src / program], src, mappings.to_dict())
    if budget is not None:
        params["context"] = budget
    w = Worker()
    try:
        assert w.call("initialize", params)["status"] == "ok"
        decisions = w.call("translate")["result"]["decisions"]
    finally:
        w.close()
    d = next(d for d in decisions if d["operation"] == removed and (line is None or d["source"]["line"] == line))
    return {"source": d["source"], "context": {k: d["context"][k] for k in ("refs", "budget")}}


@pytest.mark.parametrize("name", sorted(CASES))
def test_context_matches_golden(tmp_path, built, name):
    got = context_of(tmp_path, *CASES[name])
    path = GOLDEN / f"{name}.json"
    if os.environ.get("UPDATE_GOLDEN"):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(got, indent=2) + "\n")
    assert got == json.loads(path.read_text())


def test_context_is_deterministic_and_within_budget(tmp_path, built):
    first = context_of(tmp_path / "a", *CASES["basics_display_truncated"])
    second = context_of(tmp_path / "b", *CASES["basics_display_truncated"])
    assert first == second
    budget = first["context"]["budget"]
    refs = first["context"]["refs"]
    assert budget["used_chars"] == sum(len(r["text"]) for r in refs if "text" in r) <= budget["max_chars"]
    assert budget["truncated"] == sum(1 for r in refs if r.get("truncated"))
    assert all(r["depth"] <= budget["max_depth"] + 1 for r in refs)
    assert len({r["ref"] for r in refs}) == len(refs)

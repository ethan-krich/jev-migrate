"""JevProvider end to end: transpiler-core starts it as a `python` provider process, with recorded
Jev answers (RecordedJevClient) and the real worker. No network."""

from __future__ import annotations

import json
from pathlib import Path

import pytest
from cobol_to_java import Layout, default_mappings
from support import FIXTURES, golden, cases, run_java
from transpiler import ContextBudget, Gate, JevProvider, transpile
from transpiler.providers.jev import RecordedJevClient

pytestmark = pytest.mark.integration
BASICS = next(c for c in cases() if c.name == "basics")
REVIEW = {"score": 3.0, "confidence": 0.7, "probabilities": {"0": 0.0, "1": 0.1, "2": 0.1, "3": 0.7, "4": 0.1}}


def pick(choice: str, confidence: float) -> dict:
    return {"choice": choice, "confidence": confidence, "probabilities": {choice: confidence, "__abstain__": 1 - confidence}}


def jev(tmp_path: Path, recordings: dict, context: ContextBudget | None = None, name: str = "rec.json",
        gate: Gate | None = Gate()) -> JevProvider:
    path = tmp_path / name
    path.write_text(json.dumps(recordings))
    return JevProvider("jev-test", gate, context or ContextBudget(), client=RecordedJevClient(path))


def basics(tmp_path: Path, provider: JevProvider, out: str = "out"):
    mappings = default_mappings()
    mappings.remove("DISPLAY")
    return transpile(BASICS.src, output=tmp_path / out, mappings=mappings, provider=provider,
                     layout=Layout(**BASICS.spec["layout"]))


def pending_display(tmp_path: Path, context: ContextBudget | None = None):
    """The DISPLAY decisions of basics without recordings (so the worker's context budget applies)."""
    result = basics(tmp_path, jev(tmp_path, {}, context, "none.json"), "probe")
    assert result.status == "incomplete" and not result.resolutions.applied
    return result.pending_decisions


def test_recorded_answers_above_their_floors_translate_the_program(tmp_path, built):
    decisions = pending_display(tmp_path)
    assert {d.importance for d in decisions} == {"low"}  # DEFAULT_IMPORTANCE: (*, DISPLAY) -> low
    recordings = {d.fingerprint: {"pick": pick("builtin:display", 0.97), "review_priority": REVIEW} for d in decisions}
    result = basics(tmp_path, jev(tmp_path, recordings))
    assert result.status == "ok", result.summary()
    assert len(result.resolutions.applied) == len(decisions)
    first = result.resolutions.applied[0]
    assert first.applied and first.gate == "passed" and first.confidence == 0.97 and first.importance == "low"
    assert first.review_priority.label == "business" and first.provider == "jev"
    main = next(p for p in result.programs if p.class_name).package + ".Basics"
    assert run_java(result.output, main) == golden(BASICS)
    assert "| applied | passed | 0.97 | builtin:display |" in (result.output / "review.md").read_text()


def test_answer_below_its_floor_holds_the_decision_and_writes_nothing(tmp_path, built):
    decisions = pending_display(tmp_path)
    recordings = {d.fingerprint: {"pick": pick("builtin:display", 0.97)} for d in decisions}
    low = decisions[2]
    recordings[low.fingerprint] = {"pick": pick("builtin:display", 0.65), "review_priority": REVIEW}  # floor 0.70
    result = basics(tmp_path, jev(tmp_path, recordings))
    assert result.status == "incomplete"
    assert result.files_written == [] and not (result.output / "src").exists()
    [held] = result.resolutions.held
    assert (held.decision_id, held.gate, held.confidence, held.applied) == (low.id, "below_floor", 0.65, False)
    assert [d.id for d in result.pending_decisions] == [low.id]
    assert result.review_queue()[0].decision.id == low.id
    review = (result.output / "review.md").read_text()
    assert f"| {low.id} | pending | low | 3 business | held | below_floor | 0.65 | builtin:display |" in review


def test_without_a_gate_an_abstention_is_still_attempted_and_reported(tmp_path, built):
    decisions = pending_display(tmp_path)
    recordings = {d.fingerprint: {"pick": pick("builtin:display", 0.97)} for d in decisions}
    doubted = decisions[2]
    recordings[doubted.fingerprint] = {"pick": {"choice": "__abstain__", "confidence": 0.7,
                                                "probabilities": {"builtin:display": 0.2, "__abstain__": 0.8}},
                                       "review_priority": REVIEW}
    result = basics(tmp_path, jev(tmp_path, recordings, gate=None))
    assert result.status == "ok", result.summary()
    attempt = result.resolutions.for_decision(doubted.id)
    assert (attempt.gate, attempt.candidate_id, round(attempt.confidence, 6)) == ("passed", "builtin:display", 0.2)
    assert attempt.rationale.startswith("jev ranked __abstain__ first (0.80)")
    main = next(p for p in result.programs if p.class_name).package + ".Basics"
    assert run_java(result.output, main) == golden(BASICS)
    assert f"| {doubted.id} | resolved | low | 3 business | applied | passed | 0.20 | builtin:display |" in (
        result.output / "review.md").read_text()


def test_fabricated_candidate_is_rejected_before_resolve(tmp_path, built):
    [first, *_] = pending_display(tmp_path)
    result = basics(tmp_path, jev(tmp_path, {first.fingerprint: {"pick": pick("builtin:exec_java", 0.999)}}))
    assert result.status == "incomplete" and result.files_written == []
    [rejected] = result.resolutions.rejected
    assert rejected.stage == "orchestrator" and rejected.decision_id == first.id
    assert rejected.message == "candidate 'builtin:exec_java' is not offered"


def test_need_context_is_expanded_by_the_worker_and_asked_again(tmp_path, built):
    budget = ContextBudget(max_chars=40, max_depth=1, max_expansions=1)
    decision = pending_display(tmp_path, budget)[3]
    truncated = [r for r in decision.refs if r.get("truncated")]
    assert truncated, decision.refs
    want = truncated[0]["ref"]
    recordings = {decision.fingerprint: [
        {"pick": pick("__need_context__", 0.7), "refs": {"choice": want, "confidence": 0.9, "probabilities": {want: 0.9}}},
        {"pick": pick("builtin:display", 0.96), "review_priority": REVIEW},
    ]}
    provider = jev(tmp_path, recordings, budget)
    result = basics(tmp_path, provider)
    [applied] = result.resolutions.applied
    assert applied.decision_id == decision.id and applied.gate == "passed" and applied.confidence == 0.96


def test_need_context_beyond_the_cap_is_held(tmp_path, built):
    budget = ContextBudget(max_chars=40, max_depth=1, max_expansions=0)
    decision = pending_display(tmp_path, budget)[3]
    want = next(r for r in decision.refs if r.get("truncated"))["ref"]
    # Without expansions left the provider cannot offer __need_context__; a recorded request for it abstains.
    recordings = {decision.fingerprint: {"pick": pick("__need_context__", 0.9),
                                         "refs": {"choice": want, "confidence": 0.9, "probabilities": {want: 0.9}}}}
    result = basics(tmp_path, jev(tmp_path, recordings, budget))
    [held] = result.resolutions.held
    assert held.decision_id == decision.id and held.gate == "abstained"


def test_external_call_slots_are_separate_jev_decisions(tmp_path, built):
    src = FIXTURES / "unresolved_call/src"
    probe = transpile(src, output=tmp_path / "probe", provider=jev(tmp_path, {}, name="none.json"))
    [parent] = probe.pending_decisions
    assert parent.importance == "high" and [c.id for c in parent.candidates] == ["builtin:call_external"]
    recordings = {parent.fingerprint: {"pick": pick("builtin:call_external", 0.99)}}
    opened = transpile(src, output=tmp_path / "opened", provider=jev(tmp_path, recordings, name="one.json"))
    assert opened.status == "incomplete"
    [slot] = opened.pending_decisions
    assert slot.context["facts"]["slot"] == "target" and [c.id for c in slot.candidates] == ["target:not_found"]
    recordings[slot.fingerprint] = {"pick": pick("target:not_found", 0.98), "review_priority": REVIEW}
    done = transpile(src, output=tmp_path / "done", provider=jev(tmp_path, recordings, name="two.json"))
    assert done.status == "ok", done.summary()
    assert [r.candidate_id for r in done.resolutions.applied] == ["builtin:call_external", "target:not_found"]
    java = (done.output / "src/main/java/generated/Caller.java").read_text()
    assert 'ProgramSupport.callNamed(this, "EXTPROG", name -> null, false);' in java


EBCDIC = next(c for c in cases() if c.name == "ebcdic_collating")


def ebcdic_unset(tmp_path: Path, provider: JevProvider, out: str):
    """ebcdic_collating with its collating sequence left for the provider to choose."""
    from cobol_to_java import Dialect
    dialect = {k: v for k, v in EBCDIC.spec["dialect"].items() if k != "collating_sequence"}
    return transpile(EBCDIC.src, output=tmp_path / out, mappings=default_mappings(), provider=provider,
                     dialect=Dialect(**dialect), layout=Layout(**EBCDIC.spec["layout"]))


def test_a_chosen_setting_translates_the_program_again_with_it(tmp_path, built):
    probe = ebcdic_unset(tmp_path, jev(tmp_path, {}, name="none.json", gate=None), "probe")
    [setting] = probe.pending_decisions
    assert setting.operation == "dialect:collating_sequence" and setting.setting["field"] == "collating_sequence"
    assert [c.id for c in setting.candidates] == ["setting:native", "setting:ascii", "setting:ebcdic"]
    recordings = {setting.fingerprint: {"pick": {"choice": "setting:ebcdic", "confidence": 0.9,
                                                 "probabilities": {"setting:ebcdic": 0.92, "setting:native": 0.04,
                                                                   "setting:ascii": 0.02, "__abstain__": 0.02}}}}
    result = ebcdic_unset(tmp_path, jev(tmp_path, recordings, gate=None), "out")
    assert result.status == "ok", result.summary()
    [chosen] = result.settings
    assert (chosen.field, chosen.value, chosen.confidence) == ("collating_sequence", "ebcdic", 0.92)
    assert result.report["dialect"]["collating_sequence"] == "ebcdic"
    assert run_java(result.output, EBCDIC.equivalence["main_class"], case=EBCDIC) == golden(EBCDIC)
    review = (result.output / "review.md").read_text()
    assert "## Settings chosen" in review and "| collating_sequence | ebcdic | 0.92 |" in review
    assert "setting collating_sequence = 'ebcdic'" in result.summary()

"""Public Python API end to end: user-modified mappings travel through C++ to the Java worker."""

from __future__ import annotations

import json
from pathlib import Path

import pytest
from cobol_to_java import Layout, default_mappings
from support import FIXTURES, golden, cases, run_java
from transpiler import DecisionResponse, MappingRegistry, Reason, StaticProvider, transpile

pytestmark = pytest.mark.integration
BASICS = FIXTURES / "basics/src"
LAYOUT = Layout(base_package="fixtures.basics")


def test_default_translation_writes_project(tmp_path, built):
    result = transpile(BASICS, output=tmp_path, layout=LAYOUT)
    assert result.ok and result.exit_code == 0
    assert "src/main/java/fixtures/basics/Basics.java" in result.files_written
    assert "src/main/java/fixtures/basics/runtime/NumericField.java" in result.files_written
    report = json.loads((tmp_path / "transpile-report.json").read_text())
    assert report["translator"]["translator"] == "cobol-to-java"
    assert report["discovery"]["programs"] == ["BASICS.cbl"]


def test_removed_mapping_becomes_decisions_with_candidates(tmp_path, built):
    mappings = default_mappings()
    mappings.remove("DISPLAY")
    result = transpile(BASICS, output=tmp_path, mappings=mappings, layout=LAYOUT)
    assert result.status == "incomplete" and result.exit_code == 2
    assert result.files_written == []
    assert {d.reason for d in result.pending_decisions} == {Reason.MISSING_MAPPING}
    assert all([c.id for c in d.candidates] == ["builtin:display"] for d in result.pending_decisions)


def test_static_provider_applies_synthetic_decisions(tmp_path, built):
    mappings = default_mappings()
    mappings.remove("DISPLAY")
    first = transpile(BASICS, output=tmp_path / "probe", mappings=mappings, layout=LAYOUT)
    answers = [DecisionResponse("builtin:display", fingerprint=d.fingerprint) for d in first.pending_decisions]
    result = transpile(BASICS, output=tmp_path / "out", mappings=mappings, layout=LAYOUT, provider=StaticProvider(answers))
    assert result.ok, result.summary()
    assert all(d.resolved for d in result.decisions)
    assert len(result.report["resolutions"]["applied"]) == len(answers)
    case = next(c for c in cases() if c.name == "basics")
    assert run_java(tmp_path / "out", "fixtures.basics.Basics") == golden(case)


def test_invalid_synthetic_decision_is_rejected(tmp_path, built):
    mappings = default_mappings()
    mappings.remove("DISPLAY")
    bogus = StaticProvider([DecisionResponse("builtin:add", decision_id="decision-1")])
    result = transpile(BASICS, output=tmp_path, mappings=mappings, layout=LAYOUT, provider=bogus)
    assert result.status == "incomplete"
    [rejected] = result.report["resolutions"]["rejected"]
    assert rejected["decision_id"] == "decision-1" and "not offered" in rejected["message"]


def test_user_rule_can_force_operations_unresolved(tmp_path, built):
    mappings = default_mappings()
    mappings.add("MOVE", strategy="unresolved", when={"target_category": "numeric"}, priority=200, id="review.moves")
    result = transpile(BASICS, output=tmp_path, mappings=mappings, layout=LAYOUT)
    moves = [d for d in result.pending_decisions if d.operation == "MOVE"]
    assert moves and all(d.context["rule_id"] == "review.moves" for d in moves)
    assert all([c.id for c in d.candidates] == ["builtin:move_to_numeric"] for d in moves)
    assert all(d.context["facts"]["target_category"] == "numeric" for d in moves)


def test_equal_priority_conflict_is_ambiguous(tmp_path, built):
    mappings = default_mappings()
    mappings.add("MOVE", handler="move_to_numeric", when={"target_category": "alphanumeric"}, priority=100, id="clash")
    result = transpile(BASICS, output=tmp_path, mappings=mappings, layout=LAYOUT)
    ambiguous = [d for d in result.pending_decisions if d.reason is Reason.AMBIGUOUS_MAPPING]
    assert ambiguous
    # Only the handler that can actually translate the statement is offered.
    assert all([c.id for c in d.candidates] == ["builtin:move_to_alphanumeric"] for d in ambiguous)


def test_mismatched_handler_is_an_invalid_mapping_decision(tmp_path, built):
    mappings = default_mappings()
    mappings.replace("cobol.move.to_alphanumeric", handler="move_to_numeric")
    result = transpile(BASICS, output=tmp_path, mappings=mappings, layout=LAYOUT)
    invalid = [d for d in result.pending_decisions if d.reason is Reason.INVALID_MAPPING]
    assert invalid and all([c.id for c in d.candidates] == ["builtin:move_to_alphanumeric"] for d in invalid)


def test_callback_mapping_is_carried_as_reference(tmp_path, built):
    mappings = default_mappings()
    mappings.remove("CALL")
    mappings.add("CALL", strategy="callback", script="./callbacks.py", function="resolve_call", id="calls.cb")
    result = transpile(FIXTURES / "unresolved_call/src", output=tmp_path, mappings=mappings)
    [d] = result.pending_decisions
    assert d.reason is Reason.CALLBACK_REQUIRED
    assert d.callback == {"script": "./callbacks.py", "function": "resolve_call"}


def test_worker_validates_mappings_too(tmp_path, built):
    unchecked = MappingRegistry("cobol-to-java")  # no catalog: skips client-side validation
    unchecked.add("MOVE", handler="move_string")
    result = transpile(BASICS, output=tmp_path, mappings=unchecked, layout=LAYOUT)
    assert result.status == "failed" and result.exit_code == 1
    assert result.errors[0]["code"] == "invalid_mapping"
    assert "move_string" in result.errors[0]["details"]["problems"][0]


def test_inputs_accept_files_lists_and_globs(tmp_path, built):
    src = FIXTURES / "multi_program/src"
    layout = Layout(base_package="fixtures.multi")
    extra = [FIXTURES / "multi_program/src/shared/copybooks"]
    by_glob = transpile(str(src / "**/*.c[bo][lb]"), output=tmp_path / "g", source_root=src, layout=layout,
                        copybook_paths=extra)
    assert by_glob.ok, by_glob.summary()
    assert by_glob.report["discovery"]["programs"] == [
        "billing/PAY-MAIN.cbl", "billing/PAY-UTIL.cbl", "reports/month-end/RPT01.cob"]
    one = transpile([src / "billing/PAY-UTIL.cbl"], output=tmp_path / "f", source_root=src, layout=layout)
    assert one.ok and [p.file for p in one.programs] == ["billing/PAY-UTIL.cbl"]
    # A CALL to a program that is not part of this translation is reported, not guessed.
    main_only = transpile(src / "billing/PAY-MAIN.cbl", output=tmp_path / "m", source_root=src, layout=layout)
    assert {d.reason for d in main_only.pending_decisions} == {Reason.MISSING_TARGET_API}


def test_decision_locations_point_at_original_lines_despite_copybooks(tmp_path, built):
    src = FIXTURES / "multi_program/src"
    mappings = default_mappings()
    mappings.remove("DISPLAY")
    result = transpile(src, output=tmp_path, mappings=mappings, layout=Layout(base_package="fixtures.multi"),
                       copybook_paths=[src / "shared/copybooks"])
    assert result.pending_decisions
    for d in result.pending_decisions:
        line = (src / d.source.file).read_text().splitlines()[d.source.line - 1]
        assert line.strip().startswith(d.source.text.splitlines()[0].strip()), (d.source, line)
    assert not any(diag["code"] == "approximate_line_numbers" for p in result.programs for diag in p.diagnostics)

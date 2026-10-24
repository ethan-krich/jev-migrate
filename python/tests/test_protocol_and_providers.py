import pytest

from transpiler import DecisionRequest, DecisionResponse, DeterministicProvider, Reason, StaticProvider

RAW = {
    "id": "decision-1",
    "fingerprint": "sha256:aa",
    "reason": "missing_mapping",
    "operation": "DISPLAY",
    "message": "no rule",
    "state": "pending",
    "source": {"file": "A.cbl", "line": 4, "column": 12, "text": "DISPLAY X"},
    "context": {"program_id": "A"},
    "candidates": [{"id": "builtin:display", "handler": "display", "description": "print", "preview": "Display.line(x)"}],
}


def test_decision_request_from_json():
    d = DecisionRequest.from_json(RAW)
    assert d.reason is Reason.MISSING_MAPPING
    assert d.source.line == 4
    assert d.candidates[0].handler == "display"
    assert not d.resolved


def test_decision_response_json():
    assert DecisionResponse("builtin:display", decision_id="decision-1").to_json() == {
        "decision_id": "decision-1", "selection": {"kind": "candidate", "candidate_id": "builtin:display"}}
    with pytest.raises(ValueError):
        DecisionResponse("x")


def test_providers_configs_and_decide():
    request = DecisionRequest.from_json(RAW)
    assert DeterministicProvider().to_config() == {"kind": "deterministic"}
    assert DeterministicProvider().decide([request]) == []
    static = StaticProvider([DecisionResponse("builtin:display", fingerprint="sha256:aa")])
    assert static.to_config()["kind"] == "static"
    [answer] = static.decide([request])
    assert answer.decision_id == "decision-1"

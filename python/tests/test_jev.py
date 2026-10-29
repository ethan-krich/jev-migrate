"""JevProvider, its gate and the provider process loop, with recorded Jev answers only (offline)."""

from __future__ import annotations

import json
import subprocess
import sys

import pytest
from transpiler import ContextBudget, DecisionRequest, DecisionResponse, Gate, JevProvider, TranspileResult
from transpiler.protocol import ExtraContext, ReviewPriority
from transpiler.providers.jev import RecordedJevClient, _Choice, merge_refs
from transpiler.providers.serve import handle


def request(fingerprint="sha256:aa", importance="normal", candidates=("builtin:display",), refs=(), id_="decision-1"):
    return DecisionRequest.from_json({
        "id": id_, "fingerprint": fingerprint, "reason": "missing_mapping", "operation": "DISPLAY",
        "message": "no rule", "importance": importance, "state": "pending",
        "source": {"file": "A.cbl", "line": 3, "column": 8, "text": "DISPLAY X"},
        "context": {"facts": {}, "refs": list(refs)},
        "candidates": [{"id": c, "description": c, "preview": "Display.line(x);"} for c in candidates],
    })


def pick(choice, confidence, runner_up=None):
    probabilities = {choice: confidence}
    probabilities["__abstain__"] = round(1 - confidence, 6) if runner_up is None else runner_up
    return {"choice": choice, "confidence": confidence, "probabilities": probabilities}


REVIEW = {"score": 3.1, "confidence": 0.8, "probabilities": {"0": 0.0, "1": 0.05, "2": 0.1, "3": 0.8, "4": 0.05}}
TRUNCATED = [{"ref": "data:A@3:8", "kind": "data", "depth": 0, "text": "01 A PIC X."},
             {"ref": "stmt:9:12", "kind": "writes", "depth": 1, "truncated": True, "summary": "MOVE B TO A"}]


@pytest.mark.parametrize("importance, answer, gate", [
    ("normal", _Choice("c", 0.90, {"c": 0.90, "d": 0.10}), "passed"),
    ("normal", _Choice("c", 0.80, {"c": 0.80, "d": 0.20}), "below_floor"),
    ("low", _Choice("c", 0.55, {"c": 0.55, "d": 0.45}), "below_floor"),          # 0.55 < 0.70
    ("low", _Choice("c", 0.72, {"c": 0.72, "d": 0.28}), "passed"),
    ("low", _Choice("c", 0.72, {"c": 0.72, "d": 0.60}), "below_floor"),          # margin 0.12 < 0.15
    ("normal", _Choice("c", 0.90, {"c": 0.90, "d": 0.80}), "below_floor"),       # margin 0.10
    ("normal", _Choice("c", 0.90, {"c": 0.90, "d": 0.75}), "passed"),            # margin exactly 0.15
    ("critical", _Choice("c", 0.99, {"c": 0.99, "d": 0.01}), "passed"),
    ("critical", _Choice("c", 0.985, {"c": 0.985, "d": 0.015}), "below_floor"),
    ("normal", _Choice("__abstain__", 0.99, {"__abstain__": 0.99}), "abstained"),
    ("normal", _Choice("__need_context__", 0.6, {"__need_context__": 0.6}), "need_context"),
])
def test_gate_checks_floor_and_margin(importance, answer, gate):
    assert Gate().check(importance, answer) == gate


def test_gate_none_floor_never_passes_and_validates_tiers():
    gate = Gate({"low": 0.5, "normal": 0.6, "high": 0.7, "critical": None}, min_margin=0.0)
    assert gate.check("critical", _Choice("c", 1.0, {"c": 1.0})) == "below_floor"
    assert Gate({"low": 0.5}).check("high", _Choice("c", 1.0, {"c": 1.0})) == "below_floor", "a missing tier never passes"
    assert gate.check("low", _Choice("c", 0.5, {"c": 0.5, "d": 0.5})) == "passed"
    with pytest.raises(ValueError, match="unknown importance tiers"):
        Gate({"urgent": 0.5})
    with pytest.raises(ValueError):
        Gate({"low": 1.5})


def provider(recordings, **kw):
    client = RecordedJevClient(recordings)
    return JevProvider("jev-test", client=client, **kw), client


def test_decide_picks_with_gate_confidence_and_review_priority():
    p, client = provider({"sha256:aa": {"pick": pick("builtin:display", 0.97), "review_priority": REVIEW}})
    [answer] = p.decide([request()])
    assert answer == DecisionResponse("builtin:display", "decision-1", "sha256:aa", gate="passed", confidence=0.97,
                                      probabilities={"builtin:display": 0.97, "__abstain__": 0.03},
                                      review_priority=ReviewPriority(3, "business", 0.8))
    [call] = client.calls
    assert call["model"] == "jev-test"
    assert set(call["questions"]) == {"pick", "review_priority"}, "no refs question without truncated context"
    criteria = call["questions"]["pick"]["criteria"]
    assert set(criteria) == {"builtin:display", "__abstain__"}
    assert call["questions"]["review_priority"]["criteria"][4].startswith("money/compliance:")
    assert call["state"]["decision"]["fingerprint"] == "sha256:aa" and call["state"]["expansions"] == 0


def test_decide_skips_decisions_without_candidates_or_recordings():
    p, client = provider({"sha256:aa": {"pick": pick("builtin:display", 0.97)}})
    assert p.decide([request(candidates=()), request(fingerprint="sha256:zz")]) == []
    assert client.calls == []


def test_below_floor_and_abstention_are_reported_not_applied():
    p, _ = provider({"sha256:aa": {"pick": pick("builtin:display", 0.90)},
                     "sha256:bb": {"pick": pick("__abstain__", 0.9)}})
    low, abstain = p.decide([request(importance="critical"), request(fingerprint="sha256:bb", id_="decision-2")])
    assert low.gate == "below_floor" and low.candidate_id == "builtin:display" and not low.passed
    assert abstain.gate == "abstained" and abstain.candidate_id is None


def test_fabricated_choice_is_passed_on_for_the_orchestrator_to_reject():
    p, _ = provider({"sha256:aa": {"pick": pick("builtin:invented", 0.99)}})
    [answer] = p.decide([request()])
    assert answer.candidate_id == "builtin:invented" and answer.gate == "passed"


def test_need_context_round_trip():
    recordings = {"sha256:aa": [
        {"pick": pick("__need_context__", 0.6), "refs": {"choice": "stmt:9:12", "confidence": 0.9,
                                                          "probabilities": {"stmt:9:12": 0.9}}},
        {"pick": pick("builtin:display", 0.95), "review_priority": REVIEW},
    ]}
    p, client = provider(recordings, context=ContextBudget(max_expansions=1))
    [ask] = p.decide([request(refs=TRUNCATED)])
    assert ask.gate == "need_context" and ask.need_context == ("stmt:9:12",) and ask.candidate_id is None
    assert "__need_context__" in client.calls[0]["questions"]["pick"]["criteria"]
    assert client.calls[0]["questions"]["refs"]["criteria"] == {"stmt:9:12": "MOVE B TO A"}
    opened = {"ref": "stmt:9:12", "kind": "writes", "depth": 1, "text": "MOVE B TO A."}
    extra = ExtraContext("decision-1", expansions=1, expansions_left=0, refs=(opened,))
    [answer] = p.decide([request(refs=TRUNCATED)], {"decision-1": extra})
    assert answer.gate == "passed" and answer.candidate_id == "builtin:display"
    second = client.calls[1]
    assert second["state"]["expansions"] == 1
    assert {"ref": "stmt:9:12", "kind": "writes", "depth": 1, "text": "MOVE B TO A."} in second["state"]["context"]
    assert "__need_context__" not in second["questions"]["pick"]["criteria"], "no expansions left"
    assert "refs" not in second["questions"]


def test_need_context_without_a_valid_ref_is_an_abstention():
    p, _ = provider({"sha256:aa": {"pick": pick("__need_context__", 0.6),
                                   "refs": {"choice": "data:ELSEWHERE@1:1", "confidence": 0.9, "probabilities": {}}}})
    [answer] = p.decide([request(refs=TRUNCATED)])
    assert answer.gate == "abstained"


def test_merge_refs_replaces_truncated_nodes_only():
    merged = merge_refs(TRUNCATED, [{"ref": "stmt:9:12", "text": "x"}, {"ref": "data:A@3:8", "text": "other"},
                                    {"ref": "para:P@1:8", "text": "P."}])
    assert [r["ref"] for r in merged] == ["data:A@3:8", "stmt:9:12", "para:P@1:8"]
    assert merged[0]["text"] == "01 A PIC X." and merged[1] == {"ref": "stmt:9:12", "text": "x"}


def test_to_config_runs_this_interpreter_and_never_carries_the_key(tmp_path, monkeypatch):
    monkeypatch.setenv("MY_JEV_KEY", "secret-value")
    path = tmp_path / "rec.json"
    path.write_text("{}")
    p = JevProvider("jev-test", Gate(min_margin=0.2), ContextBudget(1000, 1, 3), api_key_env="MY_JEV_KEY",
                    client=RecordedJevClient(path))
    config = p.to_config()
    assert config["kind"] == "python" and config["command"][:3] == [sys.executable, "-m", "transpiler.providers.serve"]
    assert config["command"][3] == "transpiler.providers.jev:JevProvider"
    assert config["context"] == {"max_chars": 1000, "max_depth": 1, "max_expansions": 3}
    assert "secret-value" not in json.dumps(config)
    again = JevProvider.from_config(json.loads(config["command"][4]))
    assert again.gate == p.gate and again.context == p.context and again.api_key_env == "MY_JEV_KEY"
    assert p.context_budget() == {"max_depth": 1, "max_chars": 1000}
    with pytest.raises(ValueError):
        JevProvider("m", client=RecordedJevClient({})).to_config()


def test_real_client_needs_the_key_variable(monkeypatch):
    monkeypatch.delenv("UNSET_JEV_KEY", raising=False)
    with pytest.raises(RuntimeError, match="UNSET_JEV_KEY|transpiler\\[jev\\]"):
        JevProvider("m", api_key_env="UNSET_JEV_KEY").client


def test_serve_handles_requests_and_errors():
    p, _ = provider({"sha256:aa": {"pick": pick("builtin:display", 0.97)}})
    decide = {"protocol": "transpiler-provider/1", "id": "c-1", "method": "decide",
              "params": {"decisions": [json.loads(json.dumps(raw_request()))]}}
    response, keep = handle(p, json.dumps(decide))
    assert keep and response["status"] == "ok"
    assert response["result"]["answers"][0]["selection"]["candidate_id"] == "builtin:display"
    assert handle(p, "{nope")[0]["error"]["code"] == "malformed_json"
    assert handle(p, json.dumps({**decide, "protocol": "transpiler-worker/1"}))[0]["error"]["code"] == "unsupported_protocol"
    assert handle(p, json.dumps({**decide, "method": "explode"}))[0]["error"]["code"] == "unknown_method"
    assert handle(p, json.dumps({**decide, "params": {}}))[0]["error"]["code"] == "invalid_params"
    broken, _ = provider({"sha256:aa": {"nothing": {}}})
    assert handle(broken, json.dumps(decide))[0]["error"]["code"] == "internal_error"
    assert handle(p, json.dumps({"protocol": "transpiler-provider/1", "id": "c-9", "method": "shutdown"})) == (
        {"protocol": "transpiler-provider/1", "type": "response", "id": "c-9", "status": "ok", "result": {}}, False)


def raw_request():
    return {"id": "decision-1", "fingerprint": "sha256:aa", "reason": "missing_mapping", "operation": "DISPLAY",
            "message": "no rule", "required": True, "state": "pending", "importance": "low",
            "source": {"file": "A.cbl", "line": 3, "column": 8, "text": "DISPLAY X"},
            "context": {"program_id": "A", "source_operation": "DISPLAY", "target_language": "java", "facts": {}},
            "candidates": [{"id": "builtin:display", "description": "print"}]}


def test_provider_process_speaks_the_protocol(tmp_path):
    rec = tmp_path / "rec.json"
    rec.write_text(json.dumps({"sha256:aa": {"pick": pick("builtin:display", 0.97)}}))
    config = JevProvider("jev-test", client=RecordedJevClient(rec)).to_config()
    lines = [{"protocol": "transpiler-provider/1", "id": "c-1", "method": "decide", "params": {"decisions": [raw_request()]}},
             {"protocol": "transpiler-provider/1", "id": "c-2", "method": "shutdown"}]
    proc = subprocess.run(config["command"], input="\n".join(json.dumps(x) for x in lines) + "\n",
                          capture_output=True, text=True, timeout=60)
    assert proc.returncode == 0, proc.stderr
    ready, decided, down = [json.loads(line) for line in proc.stdout.splitlines()]
    assert ready["event"] == "ready" and ready["data"]["provider"] == "jev"
    assert decided["result"]["answers"][0]["gate"] == "passed"
    assert down["id"] == "c-2" and down["status"] == "ok"


def test_decision_response_validation():
    with pytest.raises(ValueError):
        DecisionResponse(None, "decision-1")
    with pytest.raises(ValueError):
        DecisionResponse(None, "decision-1", gate="need_context")
    with pytest.raises(ValueError):
        DecisionResponse("c", "decision-1", gate="need_context", need_context=("r",))
    with pytest.raises(ValueError):
        DecisionResponse("c", "decision-1", gate="sure")
    with pytest.raises(ValueError):
        ReviewPriority(3, "cosmetic")
    held = DecisionResponse(None, "decision-1", gate="abstained", confidence=0.4)
    assert DecisionResponse.from_json(held.to_json()) == held


def test_result_resolutions_and_review_queue(tmp_path):
    def decision(id_, state, importance):
        return {**raw_request(), "id": id_, "state": state, "importance": importance}
    report = {
        "status": "incomplete",
        "decisions": [decision("decision-1", "resolved", "low"), decision("decision-2", "pending", "critical"),
                      decision("decision-3", "pending", "normal"), decision("decision-4", "resolved", "critical")],
        "resolutions": {
            "provider": "jev",
            "applied": [{"decision_id": "decision-1", "selection": {"kind": "candidate", "candidate_id": "builtin:display"},
                         "gate": "passed", "confidence": 0.9, "importance": "low",
                         "review_priority": {"level": 3, "label": "business"}},
                        {"decision_id": "decision-4", "selection": {"kind": "candidate", "candidate_id": "builtin:display"},
                         "gate": "passed", "confidence": 0.999, "importance": "critical",
                         "review_priority": {"level": 0, "label": "cosmetic"}}],
            "held": [{"decision_id": "decision-2", "gate": "below_floor", "confidence": 0.9, "importance": "critical",
                      "selection": {"kind": "candidate", "candidate_id": "builtin:display"}}],
            "rejected": [{"decision_id": "decision-3", "stage": "orchestrator", "message": "candidate 'x' is not offered",
                          "selection": {"kind": "candidate", "candidate_id": "x"}}],
        },
    }
    result = TranspileResult("incomplete", tmp_path, report, 2,
                             decisions=[DecisionRequest.from_json(d) for d in report["decisions"]])
    r = result.resolutions
    assert r.provider == "jev"
    assert [x.decision_id for x in r.applied] == ["decision-1", "decision-4"] and all(x.applied for x in r.applied)
    assert r.held[0].gate == "below_floor" and not r.held[0].applied
    assert r.rejected[0].stage == "orchestrator" and not r.rejected[0].applied
    queue = result.review_queue()
    assert [i.decision.id for i in queue] == ["decision-2", "decision-3", "decision-1", "decision-4"]
    assert [i.tier_mismatch for i in queue] == [False, False, True, True]

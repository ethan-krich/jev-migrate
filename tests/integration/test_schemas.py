"""Real messages and serialized registries conform to the published JSON schemas."""

from __future__ import annotations

import json
from pathlib import Path

import pytest
from cobol_to_java import default_mappings
from jsonschema import Draft202012Validator
from referencing import Registry, Resource
from support import FIXTURES, REPO_ROOT, WORKER
from transpiler import transpile

SCHEMAS = {p.name: json.loads(p.read_text()) for p in (REPO_ROOT / "protocol").glob("*.schema.json")}
REGISTRY = Registry().with_resources(
    (s["$id"], Resource.from_contents(s)) for s in SCHEMAS.values()
)


def validator(name: str) -> Draft202012Validator:
    return Draft202012Validator(SCHEMAS[name], registry=REGISTRY)


def definition(name: str, ref: str) -> Draft202012Validator:
    """A validator for one ``$defs`` entry, resolving references against its schema."""
    return Draft202012Validator({"$ref": f"{SCHEMAS[name]['$id']}#/$defs/{ref}"}, registry=REGISTRY)


def test_schemas_are_valid():
    for schema in SCHEMAS.values():
        Draft202012Validator.check_schema(schema)


def test_default_mappings_match_schema():
    validator("mapping.schema.json").validate(default_mappings().to_dict())


@pytest.mark.integration
def test_worker_traffic_and_decisions_match_schemas(tmp_path: Path, built):
    import subprocess

    src = FIXTURES / "unsupported/src"
    mappings = default_mappings()
    mappings.remove("DISPLAY")
    requests = [
        {"protocol": "transpiler-worker/1", "id": "1", "method": "initialize", "session": "s", "params": {
            "source_root": str(src), "output_root": str(tmp_path), "target_language": "java",
            "files": [{"path": str(src / "UNSUP.cbl"), "role": "program"}], "layout": {"base_package": "s"},
            "mappings": mappings.to_dict()}},
        {"protocol": "transpiler-worker/1", "id": "2", "method": "translate", "session": "s", "params": {}},
        {"protocol": "transpiler-worker/1", "id": "3", "method": "resolve", "session": "s", "params": {"resolutions": [
            {"decision_id": "decision-1", "selection": {"kind": "candidate", "candidate_id": "x"}}]}},
        {"protocol": "transpiler-worker/1", "id": "4", "method": "finalize", "session": "s"},
        {"protocol": "transpiler-worker/1", "id": "5", "method": "bogus", "session": "s"},
        {"protocol": "transpiler-worker/1", "id": "6", "method": "shutdown"},
    ]
    worker_v = validator("worker.schema.json")
    for r in requests[:4] + requests[5:]:
        worker_v.validate(r)
    out = subprocess.run([str(WORKER)], input="\n".join(json.dumps(r) for r in requests) + "\n",
                         capture_output=True, text=True, timeout=120).stdout
    messages = [json.loads(line) for line in out.splitlines()]
    assert len([m for m in messages if m["type"] == "response"]) == len(requests)
    decision_v = validator("decision.schema.json")
    for m in messages:
        worker_v.validate(m)
        for d in m.get("result", {}).get("decisions", []):
            decision_v.validate(d)
    rejection = next(m for m in messages if m.get("id") == "3")["result"]["rejected"][0]
    Draft202012Validator(SCHEMAS["decision.schema.json"]["$defs"]["rejection"]).validate(rejection)


@pytest.mark.integration
def test_report_decisions_match_schema(tmp_path: Path, built):
    result = transpile(FIXTURES / "unresolved_call/src", output=tmp_path)
    for d in result.report["decisions"]:
        validator("decision.schema.json").validate(d)


SAMPLE_DECISION = {
    "id": "decision-1", "fingerprint": "sha256:" + "0" * 64, "reason": "missing_target_api", "operation": "CALL",
    "message": "program 'EXTPROG' is not part of the translation project", "required": True, "state": "pending",
    "importance": "high",
    "source": {"file": "CALLER.cbl", "line": 5, "column": 12, "text": "CALL 'EXTPROG'"},
    "context": {
        "program_id": "CALLER", "source_operation": "CALL", "target_language": "java", "facts": {},
        "refs": [
            {"ref": "data:WS-A", "kind": "data", "depth": 0, "text": "01 WS-A PIC X(4).", "file": "CALLER.cbl", "line": 4},
            {"ref": "stmt:CALLER.cbl:9:12", "kind": "writes", "depth": 1, "parent": "data:WS-A", "truncated": True,
             "summary": "MOVE 'ABCD' TO WS-A"},
        ],
        "budget": {"max_depth": 2, "max_chars": 4000, "used_chars": 17, "truncated": 1, "omitted": 0},
    },
    "candidates": [{"id": "builtin:call_external", "handler": "call_external", "description": "bind",
                    "slots": [{"name": "target", "description": "what CALL reaches"}]}],
}


def test_provider_messages_match_schema():
    v = validator("provider.schema.json")
    v.validate({"protocol": "transpiler-provider/1", "type": "event", "event": "ready", "data": {"provider": "jev"}})
    v.validate({"protocol": "transpiler-provider/1", "id": "p-1", "method": "decide", "params": {
        "decisions": [SAMPLE_DECISION],
        "extra_context": [{"decision_id": "decision-1", "expansions": 1, "expansions_left": 1,
                           "refs": SAMPLE_DECISION["context"]["refs"]}]}})
    v.validate({"protocol": "transpiler-provider/1", "id": "p-2", "method": "shutdown"})
    answers = [
        {"decision_id": "decision-1", "gate": "passed", "confidence": 0.97,
         "selection": {"kind": "candidate", "candidate_id": "builtin:call_external"},
         "probabilities": {"builtin:call_external": 0.97, "__abstain__": 0.03},
         "review_priority": {"level": 3, "label": "business", "confidence": 0.8}},
        {"decision_id": "decision-1", "gate": "need_context", "need_context": ["stmt:CALLER.cbl:9:12"]},
        {"decision_id": "decision-1", "gate": "below_floor", "confidence": 0.6,
         "selection": {"kind": "candidate", "candidate_id": "builtin:call_external"}},
    ]
    v.validate({"protocol": "transpiler-provider/1", "type": "response", "id": "p-1", "status": "ok",
                "result": {"answers": answers}})
    answer = definition("provider.schema.json", "answer")
    for a in answers:
        answer.validate(a)
    for bad in [{"decision_id": "decision-1", "gate": "need_context"},
                {"decision_id": "decision-1", "gate": "passed",
                 "selection": {"kind": "candidate", "candidate_id": "x"}},
                {"decision_id": "decision-1", "gate": "maybe"}]:
        assert not answer.is_valid(bad), bad


def test_decision_extensions_match_schema():
    validator("decision.schema.json").validate(SAMPLE_DECISION)
    resolution = definition("decision.schema.json", "resolution")
    resolution.validate({"decision_id": "decision-1", "selection": {"kind": "candidate", "candidate_id": "c"},
                         "confidence": 0.9, "gate": "passed", "importance": "normal",
                         "review_priority": {"level": 0, "label": "cosmetic"}})
    assert not resolution.is_valid({"decision_id": "d", "selection": {"kind": "candidate", "candidate_id": "c"},
                                    "review_priority": {"level": 9, "label": "urgent"}})


def test_expand_context_request_matches_schema():
    v = validator("worker.schema.json")
    v.validate({"protocol": "transpiler-worker/1", "id": "7", "method": "expand_context", "session": "s",
                "params": {"decision_id": "decision-1", "refs": ["data:WS-A"], "max_chars": 2000}})
    assert not v.is_valid({"protocol": "transpiler-worker/1", "id": "7", "method": "expand_context", "session": "s",
                           "params": {"decision_id": "decision-1", "refs": []}})

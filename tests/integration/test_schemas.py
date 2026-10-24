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

"""The Java worker driven directly over its NDJSON protocol (no orchestrator)."""

from __future__ import annotations

import json
import subprocess
from pathlib import Path

import pytest
from cobol_to_java import BUILTIN_HANDLERS, FACTS, Layout, default_mappings
from support import FIXTURES, WORKER

PROTOCOL = "transpiler-worker/1"
pytestmark = pytest.mark.integration


class Worker:
    def __init__(self) -> None:
        self.proc = subprocess.Popen([str(WORKER)], stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                     stderr=subprocess.PIPE, text=True, bufsize=1)
        self.ready = self.read()
        self.events: list[dict] = []
        self._n = 0

    def read(self) -> dict:
        line = self.proc.stdout.readline()
        assert line, f"worker closed stdout; stderr: {self.proc.stderr.read()}"
        return json.loads(line)

    def send_raw(self, line: str) -> dict:
        self.proc.stdin.write(line + "\n")
        self.proc.stdin.flush()
        while True:
            msg = self.read()
            if msg["type"] == "response":
                return msg
            self.events.append(msg)

    def call(self, method: str, params: dict | None = None, session: str | None = "s") -> dict:
        self._n += 1
        req = {"protocol": PROTOCOL, "id": f"r{self._n}", "method": method, "params": params or {}}
        if session:
            req["session"] = session
        resp = self.send_raw(json.dumps(req))
        assert resp["id"] == req["id"]
        return resp

    def close(self) -> int:
        if self.proc.poll() is None:
            self.call("shutdown", session=None)
        return self.proc.wait(timeout=10)


@pytest.fixture
def worker(built):
    w = Worker()
    yield w
    w.close()


def init_params(tmp_path: Path, files: list[Path], root: Path, mappings: dict | None = None) -> dict:
    return {
        "source_root": str(root),
        "output_root": str(tmp_path / "out"),
        "target_language": "java",
        "files": [{"path": str(f), "role": "program"} for f in files],
        "layout": Layout(base_package="proto").to_dict(),
        "mappings": mappings if mappings is not None else default_mappings().to_dict(),
    }


def test_ready_announces_catalog_matching_python_defaults(worker):
    assert worker.ready["event"] == "ready"
    data = worker.ready["data"]
    assert {h["id"]: h["operation"] for h in data["handlers"]} == dict(BUILTIN_HANDLERS)
    assert {op: frozenset(keys) for op, keys in data["facts"].items()} == dict(FACTS)
    assert data["methods"] == ["initialize", "translate", "resolve", "expand_context", "finalize", "shutdown"]


@pytest.mark.parametrize("line, code", [
    ("{not json", "malformed_json"),
    ("[1, 2]", "invalid_request"),
    (json.dumps({"protocol": "transpiler-worker/0", "id": "a", "method": "translate"}), "unsupported_protocol"),
    (json.dumps({"protocol": PROTOCOL, "method": "translate"}), "invalid_request"),
    (json.dumps({"protocol": PROTOCOL, "id": "a", "method": "explode", "session": "s"}), "unknown_method"),
    (json.dumps({"protocol": PROTOCOL, "id": "a", "method": "translate"}), "invalid_request"),
    (json.dumps({"protocol": PROTOCOL, "id": "a", "method": "translate", "session": "nope"}), "unknown_session"),
    (json.dumps({"protocol": PROTOCOL, "id": "a", "method": "translate", "session": "s", "params": []}), "invalid_request"),
])
def test_malformed_requests_get_error_responses(worker, line, code):
    resp = worker.send_raw(line)
    assert resp["status"] == "error"
    assert resp["error"]["code"] == code
    # The worker keeps serving after a bad request.
    assert worker.call("translate", session="nope")["error"]["code"] == "unknown_session"


def test_invalid_mappings_are_rejected_with_all_problems(worker, tmp_path):
    src = FIXTURES / "basics/src"
    bad = {"version": 1, "rules": [
        {"id": "a", "source": "MOVE", "strategy": "builtin", "handler": "move_string"},
        {"id": "a", "source": "DISPLAY", "strategy": "builtin", "handler": "add"},
        {"id": "c", "source": "MOVE", "strategy": "builtin", "handler": "move_to_numeric", "when": {"colour": 1}},
    ]}
    resp = worker.call("initialize", init_params(tmp_path, [src / "BASICS.cbl"], src, bad))
    assert resp["error"]["code"] == "invalid_mapping"
    problems = "\n".join(resp["error"]["details"]["problems"])
    assert "unknown builtin handler 'move_string'" in problems
    assert "duplicate id" in problems
    assert "implements ADD, not DISPLAY" in problems
    assert "unknown condition 'colour'" in problems


def test_session_lifecycle_with_synthetic_resolution(worker, tmp_path):
    src = FIXTURES / "basics/src"
    mappings = default_mappings()
    mappings.remove("DISPLAY")
    init = worker.call("initialize", init_params(tmp_path, [src / "BASICS.cbl"], src, mappings.to_dict()))
    assert init["status"] == "ok"
    assert init["result"]["programs"][0]["class"] == "Basics"
    assert any(e["event"] == "progress" for e in worker.events)
    assert worker.call("initialize", init_params(tmp_path, [src / "BASICS.cbl"], src))["error"]["code"] == "session_exists"

    tr = worker.call("translate", {"files": ["BASICS.cbl"]})
    assert tr["status"] == "incomplete"
    decisions = tr["result"]["decisions"]
    assert decisions and all(d["reason"] == "missing_mapping" and d["operation"] == "DISPLAY" for d in decisions)
    assert all([c["id"] for c in d["candidates"]] == ["builtin:display"] for d in decisions)
    assert "Display.line" in decisions[0]["candidates"][0]["preview"]

    blocked = worker.call("finalize")
    assert blocked["status"] == "incomplete" and blocked["result"]["files_written"] == []
    assert not (tmp_path / "out").exists()

    first, rest = decisions[0], decisions[1:]
    rejected = worker.call("resolve", {"resolutions": [
        {"decision_id": "decision-999", "selection": {"kind": "candidate", "candidate_id": "builtin:display"}},
        {"decision_id": first["id"], "selection": {"kind": "candidate", "candidate_id": "builtin:invented"}},
        {"decision_id": first["id"], "fingerprint": "sha256:stale", "selection": {"kind": "candidate", "candidate_id": "builtin:display"}},
        {"decision_id": rest[0]["id"], "selection": {"kind": "java", "code": "System.exit(0);"}},
    ]})["result"]
    assert [r["code"] for r in rejected["rejected"]] == [
        "unknown_decision", "unknown_candidate", "duplicate_resolution", "unsupported_selection"]
    assert rejected["applied"] == []

    applied = worker.call("resolve", {"resolutions": [
        {"decision_id": d["id"], "fingerprint": d["fingerprint"],
         "selection": {"kind": "candidate", "candidate_id": "builtin:display"}} for d in decisions]})
    assert applied["status"] == "ok"
    assert applied["result"]["applied"] == [d["id"] for d in decisions]
    again = worker.call("resolve", {"resolutions": [
        {"decision_id": first["id"], "selection": {"kind": "candidate", "candidate_id": "builtin:display"}}]})
    assert again["result"]["rejected"][0]["code"] == "already_resolved"

    done = worker.call("finalize")
    assert done["status"] == "ok"
    java = (tmp_path / "out/src/main/java/proto/Basics.java").read_text()
    assert "UNRESOLVED" not in java and java.count("Display.line") == len(decisions) - 1
    assert worker.call("translate")["error"]["code"] == "invalid_state"
    assert worker.call("expand_context", {"decision_id": first["id"], "refs": ["x"]})["error"]["code"] == "invalid_state"


def test_decisions_without_candidates_cannot_be_selected(worker, tmp_path):
    src = FIXTURES / "unsupported/src"
    worker.call("initialize", init_params(tmp_path, [src / "UNSUP.cbl"], src))
    decision = next(d for d in worker.call("translate")["result"]["decisions"] if d["operation"] == "DATA")
    assert decision["candidates"] == []
    resp = worker.call("resolve", {"resolutions": [
        {"decision_id": decision["id"], "selection": {"kind": "candidate", "candidate_id": "builtin:display"}}]})
    assert resp["result"]["rejected"][0]["code"] == "no_candidates"
    assert resp["status"] == "incomplete"


def test_setting_decisions_offer_values_and_are_applied_by_initialize_settings(worker, tmp_path):
    src = FIXTURES / "dialect_unset/src"
    worker.call("initialize", init_params(tmp_path, [src / "NEEDS.cbl"], src))
    decision = worker.call("translate")["result"]["decisions"][0]
    assert decision["operation"] == "dialect:encoding"
    assert decision["setting"] == {"field": "encoding", "values": {"setting:ascii": "ascii", "setting:ebcdic": "ebcdic"}}
    assert decision["context"]["facts"]["usages"][0].startswith("line 6: ")
    resp = worker.call("resolve", {"resolutions": [
        {"decision_id": decision["id"], "selection": {"kind": "candidate", "candidate_id": "setting:ascii"}}]})
    assert resp["result"]["rejected"][0]["code"] == "setting"
    params = init_params(tmp_path, [src / "NEEDS.cbl"], src)
    params["settings"] = {"encoding": "ascii"}
    init = worker.call("initialize", params, session="session-2")
    assert init["result"]["dialect"]["encoding"] == "ascii"
    again = worker.call("translate", session="session-2")["result"]["decisions"]
    assert [d["operation"] for d in again] == ["dialect:collating_sequence", "dialect:move_overlap"]


def test_external_call_is_bound_through_slot_decisions(worker, tmp_path):
    src = FIXTURES / "unresolved_call/src"
    worker.call("initialize", init_params(tmp_path, [src / "CALLER.cbl"], src))
    [decision] = worker.call("translate")["result"]["decisions"]
    assert decision["reason"] == "missing_target_api" and "EXTPROG" in decision["message"]
    [external] = decision["candidates"]
    assert external["id"] == "builtin:call_external" and "preview" not in external
    assert [s["name"] for s in external["slots"]] == ["target"]
    wrong = worker.call("resolve", {"resolutions": [
        {"decision_id": decision["id"], "selection": {"kind": "candidate", "candidate_id": "builtin:call_program"}}]})
    assert wrong["result"]["rejected"][0]["code"] == "unknown_candidate"

    opened = worker.call("resolve", {"resolutions": [
        {"decision_id": decision["id"], "fingerprint": decision["fingerprint"],
         "selection": {"kind": "candidate", "candidate_id": "builtin:call_external"}}]})
    assert opened["status"] == "incomplete" and opened["result"]["applied"] == [decision["id"]]
    [target] = opened["result"]["new_decisions"]
    assert target["context"]["facts"]["slot"] == "target" and target["importance"] == "critical"  # no table given
    assert [c["id"] for c in target["candidates"]] == ["target:not_found"]
    assert 'callNamed(this, "EXTPROG", name -> null, false)' in target["candidates"][0]["preview"]
    assert worker.call("finalize")["status"] == "incomplete"

    done = worker.call("resolve", {"resolutions": [
        {"decision_id": target["id"], "selection": {"kind": "candidate", "candidate_id": "target:not_found"}}]})
    assert done["status"] == "ok" and done["result"]["new_decisions"] == []
    assert worker.call("finalize")["status"] == "ok"
    java = (tmp_path / "out/src/main/java/proto/Caller.java").read_text()
    assert "UNRESOLVED" not in java and 'ProgramSupport.callNamed(this, "EXTPROG", name -> null, false);' in java


def test_importance_table_sets_tiers_and_fingerprints(worker, tmp_path):
    src = FIXTURES / "unresolved_call/src"
    params = init_params(tmp_path, [src / "CALLER.cbl"], src)
    params["importance"] = [{"reason": "*", "operation": "*", "importance": "normal"},
                            {"reason": "missing_target_api", "operation": "CALL", "importance": "critical"}]
    worker.call("initialize", params)
    [critical] = worker.call("translate")["result"]["decisions"]
    assert critical["importance"] == "critical"
    params["importance"][1]["importance"] = "low"
    worker.call("initialize", params, session="t")
    [low] = worker.call("translate", session="t")["result"]["decisions"]
    assert low["importance"] == "low"
    assert low["fingerprint"] != critical["fingerprint"], "importance is part of the fingerprint"
    worker.call("initialize", init_params(tmp_path, [src / "CALLER.cbl"], src), session="u")
    [unmatched] = worker.call("translate", session="u")["result"]["decisions"]
    assert unmatched["importance"] == "critical", "without a table every decision is critical"


@pytest.mark.parametrize("table", [
    {"reason": "*"},
    [{"reason": "*", "operation": "*", "importance": "urgent"}],
    [{"reason": "nonsense", "operation": "*", "importance": "low"}],
    [{"reason": "*", "operation": "*", "importance": "low"}, {"reason": "*", "operation": "*", "importance": "high"}],
])
def test_invalid_importance_tables_are_rejected(worker, tmp_path, table):
    src = FIXTURES / "basics/src"
    params = init_params(tmp_path, [src / "BASICS.cbl"], src)
    params["importance"] = table
    resp = worker.call("initialize", params)
    assert resp["error"]["code"] == "invalid_params" and "importance" in resp["error"]["message"]


def test_expand_context_returns_truncated_text_and_rejects_unknown_refs(worker, tmp_path):
    src = FIXTURES / "basics/src"
    mappings = default_mappings()
    mappings.remove("DISPLAY")
    params = init_params(tmp_path, [src / "BASICS.cbl"], src, mappings.to_dict())
    params["context"] = {"max_depth": 1, "max_chars": 40}
    worker.call("initialize", params)
    decision = worker.call("translate")["result"]["decisions"][3]
    refs = decision["context"]["refs"]
    budget = decision["context"]["budget"]
    assert budget["max_depth"] == 1 and budget["used_chars"] <= 40 and budget["truncated"] > 0
    truncated = [r for r in refs if r.get("truncated")]
    assert truncated and all("text" not in r and r["summary"] for r in truncated)
    want = truncated[0]["ref"]
    resp = worker.call("expand_context", {"decision_id": decision["id"], "refs": [want, "data:NOPE@1:1"]})
    assert resp["status"] == "ok"
    first = resp["result"]["refs"][0]
    assert first["ref"] == want and "text" in first and "truncated" not in first
    deeper = resp["result"]["refs"][1:]
    assert all(r["truncated"] and r["parent"] == want and r["depth"] == first["depth"] + 1 for r in deeper)
    assert resp["result"]["rejected"] == [
        {"ref": "data:NOPE@1:1", "code": "unknown_ref", "message": f"decision {decision['id']} has no context node 'data:NOPE@1:1'"}]
    # Refs listed by an expansion can be expanded in turn.
    if deeper:
        again = worker.call("expand_context", {"decision_id": decision["id"], "refs": [deeper[0]["ref"]]})
        assert again["result"]["refs"][0]["text"]
    assert worker.call("expand_context", {"decision_id": "decision-999", "refs": [want]})["error"]["code"] == "invalid_params"
    assert worker.call("expand_context", {"decision_id": decision["id"], "refs": []})["error"]["code"] == "invalid_params"
    params["context"] = {"max_depth": -1}
    assert worker.call("initialize", params, session="t")["error"]["code"] == "invalid_params"


def test_translate_rejects_unknown_files_and_retranslation(worker, tmp_path):
    src = FIXTURES / "basics/src"
    worker.call("initialize", init_params(tmp_path, [src / "BASICS.cbl"], src))
    assert worker.call("translate", {"files": ["OTHER.cbl"]})["error"]["code"] == "invalid_params"
    assert worker.call("translate", {"files": ["BASICS.cbl"]})["status"] == "ok"
    assert worker.call("translate", {"files": ["BASICS.cbl"]})["error"]["code"] == "invalid_state"


def test_initialize_validates_paths_and_layout(worker, tmp_path):
    src = FIXTURES / "basics/src"
    params = init_params(tmp_path, [src / "BASICS.cbl"], src)
    params["layout"]["base_package"] = "com.example.class"
    assert worker.call("initialize", params)["error"]["code"] == "invalid_params"
    params = init_params(tmp_path, [FIXTURES / "control_flow/src/FLOW.cbl"], src)
    assert "outside source_root" in worker.call("initialize", params)["error"]["message"]


def test_shutdown_exits_cleanly(built):
    w = Worker()
    assert w.close() == 0
    assert w.proc.stdout.read() == ""


LIBRARY_CALLS = FIXTURES / "library_calls"
LIBRARY_DIALECT = {"encoding": "ascii", "collating_sequence": "native", "out_of_range": "fail"}


def library_params(tmp_path: Path, libraries: list[dict], caches: dict | None = None) -> dict:
    src = LIBRARY_CALLS / "src"
    params = init_params(tmp_path, sorted(src.rglob("*.cbl")), src)
    params["options"] = LIBRARY_DIALECT
    params["libraries"] = {"version": 1, "libraries": libraries,
                           "caches": caches or {"maven": str(tmp_path / "m2"), "gradle": str(tmp_path / "gradle")}}
    return params


def acme_dates(**extra) -> dict:
    return {"id": "acme.dates", "source": [{"kind": "path", "pattern": "common/dates/"}],
            "target": {"kind": "path", "path": str(LIBRARY_CALLS / "lib")}, **extra}


def test_initialize_indexes_libraries(worker, tmp_path):
    r = worker.call("initialize", library_params(tmp_path, [acme_dates()]))
    assert r["status"] == "ok", r
    [lib] = r["result"]["libraries"]
    assert lib["id"] == "acme.dates" and lib["classes"] == 3 and lib["hash"].startswith("sha256:")


def test_uninstalled_or_sourceless_dependencies_are_unavailable(worker, tmp_path):
    jar = tmp_path / "m2/com/acme/acme-dates/1.0/acme-dates-1.0.jar"
    jar.parent.mkdir(parents=True)
    jar.write_bytes(b"")
    r = worker.call("initialize", library_params(tmp_path, [
        {"id": "a", "source": [{"kind": "program", "names": ["DATEADD"]}], "target": {"kind": "maven", "coordinates": "com.acme:acme-dates:1.0"}},
        {"id": "b", "source": [{"kind": "program", "names": ["WEEKDAY"]}], "target": {"kind": "maven", "coordinates": "com.acme:nope:1.0"}}]))
    assert r["status"] == "error" and r["error"]["code"] == "library_unavailable"
    a, b = r["error"]["details"]["problems"]
    assert "without its sources jar" in a and "mvn dependency:sources" in a
    assert "not installed in the local caches" in b


def test_the_cobol_side_is_checked_against_the_parse(worker, tmp_path):
    r = worker.call("initialize", library_params(tmp_path, [acme_dates(
        source=[{"kind": "path", "pattern": "nowhere/"}, {"kind": "paragraph", "names": ["NOPE"]},
                {"kind": "copybook", "names": ["NOREC"]}, {"kind": "operation", "names": ["FUNCTION integer-of-date!"]}],
        hints={"DATEADD": ["Dates.addDays(int,int)"], "BILLING": ["Dates.nothing()"]})]))
    assert r["status"] == "error" and r["error"]["code"] == "invalid_library"
    problems = r["error"]["details"]["problems"]
    assert any("Dates.nothing()" in p for p in problems)  # checked against the Java index
    r = worker.call("initialize", library_params(tmp_path, [acme_dates(
        source=[{"kind": "path", "pattern": "nowhere/"}, {"kind": "paragraph", "names": ["NOPE"]},
                {"kind": "copybook", "names": ["NOREC"]}, {"kind": "operation", "names": ["FUNCTION integer-of-date!"]}],
        hints={"BILLING": ["Dates.addDays(int,int)"]})]), session="s2")
    problems = r["error"]["details"]["problems"]
    assert len(problems) == 5, problems
    assert "matches no program" in problems[0] and "paragraph NOPE" in problems[1] and "copybook NOREC" in problems[2]
    assert "not a COBOL verb" in problems[3] and "does not claim" in problems[4]

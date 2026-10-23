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
    assert data["methods"] == ["initialize", "translate", "resolve", "finalize", "shutdown"]


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


def test_decisions_without_candidates_cannot_be_selected(worker, tmp_path):
    src = FIXTURES / "unresolved_call/src"
    worker.call("initialize", init_params(tmp_path, [src / "CALLER.cbl"], src))
    [decision] = worker.call("translate")["result"]["decisions"]
    assert decision["reason"] == "missing_target_api"
    assert "EXTPROG" in decision["message"] and decision["candidates"] == []
    resp = worker.call("resolve", {"resolutions": [
        {"decision_id": decision["id"], "selection": {"kind": "candidate", "candidate_id": "builtin:call_program"}}]})
    assert resp["result"]["rejected"][0]["code"] == "no_candidates"
    assert resp["status"] == "incomplete"


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

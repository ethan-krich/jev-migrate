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
    assert "not an operation a library can claim" in problems[3] and "does not claim" in problems[4]


def resolve(worker, decision: dict, candidate: str, session: str = "s") -> dict:
    r = worker.call("resolve", {"resolutions": [{"decision_id": decision["id"], "fingerprint": decision["fingerprint"],
                                                 "provider": "test", "confidence": 0.9,
                                                 "selection": {"kind": "candidate", "candidate_id": candidate}}]}, session=session)
    assert r["result"]["applied"] == [decision["id"]], r
    return r


def translate_billing(worker, tmp_path, session="s", libraries=None) -> list[dict]:
    params = library_params(tmp_path, libraries if libraries is not None else [acme_dates()])
    if libraries == []:
        params.pop("libraries")
    assert worker.call("initialize", params, session=session)["status"] == "ok"
    decisions = worker.call("translate", {"files": ["BILLING.cbl"]}, session=session)["result"]["decisions"]
    rest = worker.call("translate", {"files": ["common/dates/DATEADD.cbl", "common/dates/DATEFMT.cbl", "common/dates/WEEKDAY.cbl"]},
                       session=session)
    assert rest["status"] == "ok"
    return decisions


def test_a_claimed_program_call_is_a_library_call_decision(worker, tmp_path):
    dateadd, weekday, datefmt = translate_billing(worker, tmp_path)
    assert [d["reason"] for d in (dateadd, weekday, datefmt)] == ["library_call"] * 3
    assert dateadd["context"]["rule_id"] == "cobol.call.library" and dateadd["context"]["facts"]["program_in_library"] is True
    assert dateadd["library"]["step"] == "start" and dateadd["library"]["part"]["file"] == "common/dates/DATEADD.cbl"
    assert "COMPUTE WS-DAY-NUMBER" in dateadd["library"]["part"]["text"]
    facts = dateadd["context"]["facts"]
    assert facts["library"] == "acme.dates" and facts["library_hash"].startswith("sha256:") and facts["chain"] == []
    ids = [c["id"] for c in dateadd["candidates"]]
    assert ids == ["library:acme.dates:CalendarDate.new(int,int,int)", "library:acme.dates:CalendarDate.ofNumber(int)",
                   "library:acme.dates:Dates.addDays(int,int)", "library:acme.dates:Dates.daysBetween(int,int)", "cobol:translate"]
    add = dateadd["candidates"][2]
    assert add["handler"] == "library" and "not equivalence-checked" in add["description"]
    assert add["docs"][0]["signature"] == "int Dates.addDays(int yyyymmdd, int days)"
    assert "ProgramSupport.call(this" in dateadd["candidates"][-1]["preview"]
    # A boolean result cannot be stored, so isLeapYear is never offered.
    assert not any("isLeapYear" in c["id"] for d in (dateadd, weekday, datefmt) for c in d["candidates"])

    # Two bindings of addDays fit WS-DUE-DATE: the order is a follow-up decision, whose fingerprint has the chain.
    [args] = resolve(worker, dateadd, "library:acme.dates:Dates.addDays(int,int)")["result"]["new_decisions"]
    assert args["library"]["step"] == "args" and args["context"]["facts"]["chain"] == ["Dates.addDays(int,int)"]
    arg_ids = [c["id"] for c in args["candidates"]]
    assert arg_ids[0] == "args:0,1" and "args:1,0" in arg_ids and arg_ids[-1] == "cobol:translate"
    assert resolve(worker, args, "args:0,1")["result"]["new_decisions"] == []

    # DateFormatter() then .format(int) is forced; only its lifetime is asked.
    [lifetime] = resolve(worker, datefmt, "library:acme.dates:DateFormatter.new()")["result"]["new_decisions"]
    assert lifetime["library"]["step"] == "lifetime"
    assert [c["id"] for c in lifetime["candidates"]] == ["lifetime:per_call", "lifetime:per_program", "cobol:translate"]
    resolve(worker, lifetime, "lifetime:per_program")
    resolve(worker, weekday, "cobol:translate")

    done = worker.call("finalize")
    assert done["status"] == "ok", done
    assert done["result"]["equivalence"] == "partial"
    [used] = done["result"]["libraries_used"]
    uses = {u["part"]: u for u in used["uses"]}
    assert uses["DATEADD"]["outcome"] == "library" and uses["DATEADD"]["chain"] == ["Dates.addDays(int,int)"]
    assert uses["DATEFMT"]["chain"] == ["DateFormatter.new()", "DateFormatter.format(int)"]
    assert uses["WEEKDAY"]["outcome"] == "translated" and "start step" in uses["WEEKDAY"]["reason"]
    java = (tmp_path / "out/src/main/java/proto/Billing.java").read_text()
    assert "// Library acme.dates, not equivalence-checked: Decided by test (confidence 0.90)" in java
    # Library types are imported once (written fully qualified only on a name clash).
    assert "import com.acme.dates.Dates;" in java and "import com.acme.dates.DateFormatter;" in java
    assert "Dates.addDays(wsInvoiceDate.intValue(), wsTermsDays.intValue())" in java and "com.acme.dates.Dates.addDays" not in java
    assert "Uses libraries (not equivalence-checked against the COBOL): acme.dates (lib)." in java
    assert "private DateFormatter acmeDatesDateFormatter;" in java
    assert "wsDueText.set(acmeDatesDateFormatter().format(wsDueDate.intValue()));" in java
    assert "Weekday.instance()" in java and "LIBRARY" not in java


def test_a_chain_continues_through_library_types(worker, tmp_path):
    _, _, datefmt = translate_billing(worker, tmp_path)
    # CalendarDate.ofNumber(..) gives a CalendarDate; only .iso() reaches a String, so it is taken without asking.
    r = resolve(worker, datefmt, "library:acme.dates:CalendarDate.ofNumber(int)")
    assert r["result"]["new_decisions"] == []
    worker.call("resolve", {"resolutions": []})
    used = worker.call("finalize")["result"]["libraries_used"][0]["uses"]
    assert [u["chain"] for u in used if u["part"] == "DATEFMT"] == [["CalendarDate.ofNumber(int)", "CalendarDate.iso()"]]


def test_cobol_translate_everywhere_writes_what_a_run_without_libraries_writes(worker, tmp_path):
    for d in translate_billing(worker, tmp_path / "lib"):
        resolve(worker, d, "cobol:translate")
    done = worker.call("finalize")
    assert done["status"] == "ok" and done["result"]["equivalence"] == "exact"
    assert all(u["outcome"] == "translated" for u in done["result"]["libraries_used"][0]["uses"])
    assert translate_billing(worker, tmp_path / "plain", session="s2", libraries=[]) == []
    assert worker.call("finalize", session="s2")["status"] == "ok"
    for java in (tmp_path / "plain/out").rglob("*.java"):
        assert (tmp_path / "lib/out" / java.relative_to(tmp_path / "plain/out")).read_text() == java.read_text(), java


def test_a_library_without_a_binding_member_leaves_the_translation(worker, tmp_path):
    only_bool = tmp_path / "Leap.java"
    only_bool.write_text("package acme; public final class Leap { public static boolean leap(int y) { return false; } }")
    decisions = translate_billing(worker, tmp_path, libraries=[{
        "id": "leap", "source": [{"kind": "program", "names": ["DATEADD"]}], "target": {"kind": "path", "path": str(only_bool)}}])
    assert decisions == []
    done = worker.call("finalize")
    assert done["status"] == "ok" and done["result"]["equivalence"] == "exact"
    [use] = done["result"]["libraries_used"][0]["uses"]
    assert use["outcome"] == "translated" and use["reason"].startswith("library not offered: no public member")


OPS_PROGRAM = """\
       IDENTIFICATION DIVISION.
       PROGRAM-ID. OPS.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01 WS-NAME  PIC X(10) VALUE "ada".
       01 WS-UP    PIC X(10).
       01 WS-DATE  PIC 9(8) VALUE 20261115.
       01 WS-DAYS  PIC 9(7).
       01 WS-TODAY PIC 9(8).
       PROCEDURE DIVISION.
           MOVE FUNCTION UPPER-CASE(WS-NAME) TO WS-UP.
           COMPUTE WS-DAYS = FUNCTION INTEGER-OF-DATE(WS-DATE) + 1.
           DISPLAY "HELLO " WS-UP.
           ACCEPT WS-TODAY FROM DATE YYYYMMDD.
           CALL "CBL_TOUPPER" USING WS-NAME BY VALUE 10.
           STOP RUN.
"""
OPS_LIBRARY = """\
package acme.ops;

/** Text, dates and logging. */
public final class Ops {
    private Ops() {}
    /** The text in upper case. */
    public static String upper(String s) { return s.toUpperCase(); }
    /** The day number of a YYYYMMDD date, 1 for 1601-01-01. */
    public static int dayNumber(int yyyymmdd) { return 0; }
    /** Writes a line to the log. */
    public static void log(String a, String b) {}
    /** Today as YYYYMMDD. */
    public static int today() { return 0; }
}
"""


def test_operations_claim_functions_verbs_and_system_routines(worker, tmp_path):
    src = tmp_path / "src"
    src.mkdir()
    (src / "OPS.cbl").write_text(OPS_PROGRAM)
    lib = tmp_path / "Ops.java"
    lib.write_text(OPS_LIBRARY)
    params = init_params(tmp_path, [src / "OPS.cbl"], src)
    params["options"] = LIBRARY_DIALECT
    params["libraries"] = {"version": 1, "libraries": [{
        "id": "acme.ops", "target": {"kind": "path", "path": str(lib)},
        "source": [{"kind": "operation", "names": ["FUNCTION UPPER-CASE", "FUNCTION INTEGER-OF-DATE", "DISPLAY", "ACCEPT",
                                                   "CALL 'CBL_TOUPPER'"]}]}]}
    assert worker.call("initialize", params)["status"] == "ok"
    decisions = worker.call("translate")["result"]["decisions"]
    by_part = {d["library"]["part"]["name"]: d for d in decisions}
    assert list(by_part) == ["FUNCTION UPPER-CASE", "FUNCTION INTEGER-OF-DATE", "DISPLAY", "ACCEPT"]
    upper = by_part["FUNCTION UPPER-CASE"]
    assert [c["id"] for c in upper["candidates"]] == ["library:acme.ops:Ops.upper(String)", "cobol:translate"]
    days = by_part["FUNCTION INTEGER-OF-DATE"]
    assert days["candidates"][0]["id"] == "library:acme.ops:Ops.dayNumber(int)"
    assert by_part["DISPLAY"]["candidates"][0]["id"] == "library:acme.ops:Ops.log(String,String)"
    assert by_part["ACCEPT"]["candidates"][0]["id"] == "library:acme.ops:Ops.today()"
    todo = list(decisions)
    while todo:
        d = todo.pop(0)
        todo += resolve(worker, d, d["candidates"][0]["id"])["result"]["new_decisions"]
    done = worker.call("finalize")
    assert done["status"] == "ok", done
    uses = {u["part"]: u for u in done["result"]["libraries_used"][0]["uses"]}
    # CBL_TOUPPER passes a BY VALUE length the library has no parameter for: not offered.
    assert uses["CALL 'CBL_TOUPPER'"]["outcome"] == "translated" and "library not offered" in uses["CALL 'CBL_TOUPPER'"]["reason"]
    java = (tmp_path / "out/src/main/java/proto/Ops.java").read_text()
    # The program's own class is Ops, so the library's Ops stays fully qualified and is not imported.
    assert "import acme.ops.Ops;" not in java
    assert "wsUp.set(acme.ops.Ops.upper(wsName.get()));" in java
    # The statement around a library function stays exact: the + 1 and the store into WS-DAYS are as without the library.
    assert "wsDays.store(Arithmetic.plus(BigDecimal.valueOf(acme.ops.Ops.dayNumber(wsDate.intValue())), BigDecimal.ONE)" in java
    assert 'acme.ops.Ops.log("HELLO ", wsUp.get());' in java
    assert "wsToday.move(BigDecimal.valueOf(acme.ops.Ops.today()));" in java
    assert "SystemRoutines.toUpper" in java


PARAGRAPHS = """\
       IDENTIFICATION DIVISION.
       PROGRAM-ID. PARAS.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01 WS-DATE  PIC 9(8) VALUE 20261115.
       01 WS-DAYS  PIC S9(4) VALUE 30.
       01 WS-DUE   PIC 9(8).
       01 WS-WORK  PIC 9(7).
       01 WS-I     PIC 9.
       PROCEDURE DIVISION.
       MAIN.
           PERFORM CALC-DUE.
           PERFORM CALC-DUE 2 TIMES.
           PERFORM COPIED.
           PERFORM JUMPY.
           PERFORM FIRST-P THRU LAST-P.
           PERFORM FIRST-P.
           PERFORM SCRATCH.
           DISPLAY WS-DUE.
           STOP RUN.
      * The due date: the invoice date plus the terms in days.
       CALC-DUE.
           IF WS-DAYS > 0
               COMPUTE WS-DUE = WS-DATE + WS-DAYS
           END-IF.
       SCRATCH.
           COMPUTE WS-WORK = FUNCTION INTEGER-OF-DATE(WS-DATE).
           ADD WS-DAYS TO WS-WORK.
           COMPUTE WS-DUE = FUNCTION DATE-OF-INTEGER(WS-WORK).
       COPIED.
           COPY DUECALC.
       JUMPY.
           IF WS-DAYS > 0 GO TO JUMPY-EXIT END-IF.
       JUMPY-EXIT.
           EXIT.
       FIRST-P.
           MOVE 1 TO WS-I.
       LAST-P.
           EXIT.
"""
DUECALC = """\
           COMPUTE WS-DUE = WS-DATE + WS-DAYS.
"""


def test_paragraphs_and_procedure_copybooks_are_claimed_at_their_performs(worker, tmp_path):
    src = tmp_path / "src"
    src.mkdir()
    (src / "PARAS.cbl").write_text(PARAGRAPHS)
    (src / "DUECALC.cpy").write_text(DUECALC)
    params = library_params(tmp_path, [acme_dates(source=[
        {"kind": "paragraph", "names": ["CALC-DUE", "JUMPY", "FIRST-P", "LAST-P", "SCRATCH"], "program": "PARAS"},
        {"kind": "copybook", "names": ["DUECALC"]}])])
    params["source_root"] = str(src)
    params["files"] = [{"path": str(src / "PARAS.cbl"), "role": "program"}, {"path": str(src / "DUECALC.cpy"), "role": "copybook"}]
    assert worker.call("initialize", params)["status"] == "ok"
    decisions = worker.call("translate")["result"]["decisions"]
    parts = [(d["library"]["part"]["kind"], d["library"]["part"]["name"], d["source"]["line"]) for d in decisions]
    assert parts == [("paragraph", "CALC-DUE", 12), ("copybook", "DUECALC", 14)]
    calc = decisions[0]
    # CALC-DUE reads WS-DAYS (the IF) and WS-DATE before writing anything, and writes WS-DUE only.
    assert "COMPUTE WS-DUE" in calc["library"]["part"]["text"]
    ids = [c["id"] for c in calc["candidates"]]
    assert "library:acme.dates:Dates.addDays(int,int)" in ids and ids[-1] == "cobol:translate"
    [args] = resolve(worker, calc, "library:acme.dates:Dates.addDays(int,int)")["result"]["new_decisions"]
    assert {c["id"] for c in args["candidates"]} == {"args:0,1", "args:1,0", "cobol:translate"}
    resolve(worker, args, "args:1,0")  # inputs in read order: WS-DAYS, WS-DATE
    resolve(worker, decisions[1], "cobol:translate")
    done = worker.call("finalize")
    assert done["status"] == "ok", done
    uses = {(u["part"], u["line"]): u for u in done["result"]["libraries_used"][0]["uses"]}
    assert uses[("CALC-DUE", 12)]["outcome"] == "library"
    assert "runs the paragraph times" in uses[("CALC-DUE", 13)]["reason"]
    assert "GO TO" in uses[("JUMPY", 15)]["reason"]
    assert "PERFORM FIRST-P THRU LAST-P range" in uses[("FIRST-P", 17)]["reason"]
    assert "several unrelated items [WS-WORK, WS-DUE]" in uses[("SCRATCH", 18)]["reason"]
    java = (tmp_path / "out/src/main/java/proto/Paras.java").read_text()
    assert "wsDue.move(BigDecimal.valueOf(Dates.addDays(wsDate.intValue(), wsDays.intValue())));" in java
    # The paragraph body is still translated (it can be fallen into, or PERFORMed in a loop).
    assert "calcDue" in java.replace("CalcDue", "calcDue")


RECORDS = """\
       IDENTIFICATION DIVISION.
       PROGRAM-ID. RECS.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       COPY DATEREC.
       01 WS-DAYS PIC S9(4) VALUE 30.
       PROCEDURE DIVISION.
           MOVE "20261115" TO DATE-REC.
           CALL "SHIFT" USING DATE-REC WS-DAYS.
           DISPLAY DR-YEAR "-" DR-MONTH "-" DR-DAY.
           CALL "SHIFT" USING DATE-REC WS-DAYS.
           DISPLAY DR-YEAR "-" DR-MONTH "-" DR-DAY.
           STOP RUN.
"""
DATEREC = """\
       01 DATE-REC.
          05 DR-YEAR  PIC 9(4).
          05 DR-MONTH PIC 99.
          05 DR-DAY   PIC 99.
"""


def test_data_copybook_records_convert_to_library_classes_at_calls(worker, tmp_path):
    src = tmp_path / "src"
    src.mkdir()
    (src / "RECS.cbl").write_text(RECORDS)
    (src / "DATEREC.cpy").write_text(DATEREC)
    params = library_params(tmp_path, [acme_dates(source=[{"kind": "program", "names": ["SHIFT"]},
                                                          {"kind": "copybook", "names": ["DATEREC"]}])])
    params["source_root"] = str(src)
    params["files"] = [{"path": str(src / "RECS.cbl"), "role": "program"}, {"path": str(src / "DATEREC.cpy"), "role": "copybook"}]
    assert worker.call("initialize", params)["status"] == "ok"
    first, second = worker.call("translate")["result"]["decisions"]
    ids = [c["id"] for c in first["candidates"]]
    assert "library:acme.dates:CalendarDate.of(DATE-REC)" in ids, ids
    convert = next(c for c in first["candidates"] if c["id"] == "library:acme.dates:CalendarDate.of(DATE-REC)")
    assert "takes the record DATE-REC as a CalendarDate" in convert["description"]
    r = resolve(worker, first, "library:acme.dates:CalendarDate.of(DATE-REC)")
    [next_step] = r["result"]["new_decisions"]
    # The record's object goes on to .plusDays(WS-DAYS); stopping right away (storing it back unchanged) is also offered.
    assert next_step["library"]["step"] == "next"
    assert "library:acme.dates:CalendarDate.plusDays(int)" in [c["id"] for c in next_step["candidates"]]
    [then] = resolve(worker, next_step, "library:acme.dates:CalendarDate.plusDays(int)")["result"]["new_decisions"]
    # It could go on (.year() into WS-DAYS, the other in-out item); end stores the CalendarDate back into DATE-REC.
    assert then["library"]["step"] == "next" and then["candidates"][0]["id"] == "end"
    [fields] = resolve(worker, then, "end")["result"]["new_decisions"]
    # Three int fields and three int components: the pairing is asked once, with every exact pairing offered.
    assert fields["library"]["step"] == "fields"
    fids = [c["id"] for c in fields["candidates"]]
    assert fids[0] == "fields:DR-YEAR,DR-MONTH,DR-DAY" and len(fids) == 7 and fids[-1] == "cobol:translate"
    assert "new com.acme.dates.CalendarDate(drYear.intValue(), drMonth.intValue(), drDay.intValue())" in fields["candidates"][0]["preview"]
    assert resolve(worker, fields, "fields:DR-YEAR,DR-MONTH,DR-DAY")["result"]["new_decisions"] == []
    # The second CALL reuses the pairing: no fields step.
    r = resolve(worker, second, "library:acme.dates:CalendarDate.of(DATE-REC)")
    [again] = r["result"]["new_decisions"]
    [again_then] = resolve(worker, again, "library:acme.dates:CalendarDate.plusDays(int)")["result"]["new_decisions"]
    assert resolve(worker, again_then, "end")["result"]["new_decisions"] == []
    done = worker.call("finalize")
    assert done["status"] == "ok", done
    java = (tmp_path / "out/src/main/java/proto/Recs.java").read_text()
    import re
    found = re.findall(r"CalendarDate (libraryValue\d+) = new CalendarDate\(drYear.intValue\(\), drMonth.intValue\(\), "
                       r"drDay.intValue\(\)\).plusDays\(wsDays.intValue\(\)\);", java)
    assert len(found) == 2
    var = found[0]
    assert f"drYear.move(BigDecimal.valueOf({var}.year()));" in java and f"drDay.move(BigDecimal.valueOf({var}.day()));" in java
    out = tmp_path / "out"
    subprocess.run(["javac", "-d", str(out / "classes"), "-sourcepath", str(LIBRARY_CALLS / "lib"),
                    *map(str, (out / "src/main/java").rglob("*.java"))], check=True, capture_output=True)
    run = subprocess.run(["java", "-cp", str(out / "classes"), "proto.Recs"], capture_output=True, text=True, check=True)
    assert run.stdout.splitlines() == ["2026-12-15", "2027-01-14"]

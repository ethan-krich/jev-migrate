"""Live benchmark: the Jev pipeline, the same pipeline with a chat LLM deciding, and a plain LLM.

    uv run --frozen --all-packages --extra jev python benchmarks/jev-vs-llm/bench.py \
        [--runs 3] [--llm-model anthropic/claude-haiku-5.5] [--jev-model jev-latest] [--output benchmarks/jev-vs-llm/results.md]

It calls paid APIs through OpenRouter (OPENROUTER_API_KEY, from the environment or .env), so it is
never part of a test suite. The project is project/ (see project.py there): three COBOL
programs with copybooks, a tax routine mapped to a Java library, and statements that can use the
runtime library or the plain JDK.

Each approach runs --runs times:

* **Jev**: `transpile` with `JevProvider` via OpenRouter and the library mapping. The translator
  writes all the Java; Jev only picks among candidates. Cost is the OpenRouter key's usage before
  and after the run, each read once the key's spend has stopped changing (Jev's SDK reports
  tokens, not prices), attributed to files by their share of Jev's decisions; time is the run's
  wall time, attributed the same way.
* **decider**: the same `transpile`, with `llm_decider.LlmDecisionProvider` (the --llm-model) in
  Jev's place: Jev's state and questions, answered by a chat completion. The cost is what
  OpenRouter reports for each call, by the decision's file.
* **LLM**: one chat completion per COBOL file (its copybooks inlined, the library's sources and the
  programs' interfaces given), with the cost OpenRouter reports for that call.

Scoring, per file and per run:

* **construct**: Jev: each decision's pick against `RUBRIC` (runtime for money, the JDK for
  counters and messages, `Withholding.tax` for the TAXCALC call). LLM: static checks of its Java
  for the same intent (the library call, no floating point on money, the cent truncation and the
  rounding, the hyphen replacement).
* **compiles**: javac on the file (LLM: with stubs of the other programs' interfaces, so one
  file's errors do not fail another).
* **exact**: the whole program compiled and run; stdout equals GnuCOBOL 3.2's recorded output
  byte for byte (benchmarks/jev-vs-llm/project/expected/stdout.txt), exit status 0.

Raw results (JSON, the Java of every run) go to build/benchmarks/jev-vs-llm/; the summary to --output.
"""

from __future__ import annotations

import argparse
import contextlib
import io
import json
import os
import re
import shutil
import statistics
import subprocess
import sys
import time
import urllib.error
import urllib.request
import zipfile
from dataclasses import asdict, dataclass, field
from datetime import date
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[2]
DEMO = Path(__file__).resolve().parent / "project"
WORK = ROOT / "build/benchmarks/jev-vs-llm"
OPENROUTER = "https://openrouter.ai/api/v1"
sys.path.insert(0, str(DEMO))
import project as P  # noqa: E402

from cobol_to_java import Cobol, Dialect, Java, Layout, Library, libraries  # noqa: E402
from transpiler import JevProvider, transpile  # noqa: E402
from transpiler.providers.jev import OPENROUTER_BASE_URL  # noqa: E402

# The provider process imports llm_decider too: it inherits PYTHONPATH from this process.
os.environ["PYTHONPATH"] = os.pathsep.join(filter(None, [str(Path(__file__).parent), os.environ.get("PYTHONPATH")]))
from llm_decider import LlmDecisionProvider  # noqa: E402

KEY_ENV = "OPENROUTER_API_KEY"


# ------------------------------------------------------------------ results


@dataclass
class FileResult:
    file: str
    cost_usd: float | None = None
    seconds: float = 0.0
    construct_passed: int = 0
    construct_total: int = 0
    compiles: bool = False
    checks: list[dict[str, Any]] = field(default_factory=list)  # {name, ok, detail}
    tokens_in: int | None = None
    tokens_out: int | None = None
    error: str | None = None


@dataclass
class RunResult:
    approach: str
    run: int
    seconds: float
    cost_usd: float | None
    compiles: bool
    exact: bool
    stdout: str
    files: list[FileResult]
    note: str = ""


# ------------------------------------------------------------------ helpers


def load_env() -> None:
    """Reads KEY=VALUE lines of .env into os.environ, keeping variables already set."""
    env = ROOT / ".env"
    if not env.exists():
        return
    for line in env.read_text().splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        k, v = line.split("=", 1)
        if v.strip() and not os.environ.get(k.strip()):
            os.environ[k.strip()] = v.strip().strip('"').strip("'")


def openrouter(path: str, body: dict[str, Any] | None = None, timeout: float = 120, tries: int = 3) -> dict[str, Any]:
    """One OpenRouter call; transient network errors (a dropped connection, a timeout, a 5xx) are retried."""
    for attempt in range(1, tries + 1):
        try:
            return _openrouter(path, body, timeout)
        except (urllib.error.URLError, TimeoutError, ConnectionError) as e:
            status = getattr(e, "code", None)
            if attempt == tries or (status is not None and status < 500 and status != 429):
                raise
            print(f"[openrouter] {path}: {type(e).__name__}: {e}; retrying ({attempt}/{tries - 1})", file=sys.stderr)
            time.sleep(5 * attempt)
    raise AssertionError("unreachable")


def _openrouter(path: str, body: dict[str, Any] | None, timeout: float) -> dict[str, Any]:
    req = urllib.request.Request(OPENROUTER + path, data=json.dumps(body).encode() if body is not None else None,
                                 headers={"Authorization": f"Bearer {os.environ[KEY_ENV]}",
                                          "Content-Type": "application/json",
                                          "X-Title": "jev-migrate benchmark"})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return json.loads(r.read())


def key_usage() -> float:
    return float(openrouter("/key")["data"]["usage"])


def quiet_usage(wait: float = 120.0, still: float = 20.0) -> float:
    """The key's usage once it has not changed for `still` seconds (OpenRouter books spend with a
    delay, so earlier calls, such as the LLM runs, must land before a Jev run is measured)."""
    deadline = time.monotonic() + wait
    last, since = key_usage(), time.monotonic()
    while time.monotonic() < deadline:
        time.sleep(5)
        now = key_usage()
        if now != last:
            last, since = now, time.monotonic()
        elif time.monotonic() - since >= still:
            break
    return last


def settled_usage(before: float, wait: float = 120.0) -> float | None:
    """The key's usage once it has moved past `before` and then stayed still; None if it never moves."""
    deadline = time.monotonic() + wait
    while time.monotonic() < deadline and key_usage() <= before:
        time.sleep(5)
    now = quiet_usage(wait=max(deadline - time.monotonic(), 30))
    return now if now > before else None


def install_library(m2: Path) -> Path:
    """Compiles the library and puts its jar and sources jar where Maven installs them; returns the jar."""
    spec = P.LIBRARY
    group, artifact, version = spec["coordinates"].split(":")
    sources = DEMO / spec["sources"]
    target = m2 / group.replace(".", "/") / artifact / version
    classes = WORK / f"{artifact}-classes"
    shutil.rmtree(classes, ignore_errors=True)
    files = sorted(sources.rglob("*.java"))
    subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(classes), *map(str, files)], check=True)
    target.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(target / f"{artifact}-{version}.jar", "w") as jar:
        for f in sorted(classes.rglob("*.class")):
            jar.write(f, f.relative_to(classes).as_posix())
    with zipfile.ZipFile(target / f"{artifact}-{version}-sources.jar", "w") as src:
        for f in files:
            src.write(f, f.relative_to(sources).as_posix())
    return target / f"{artifact}-{version}.jar"


def javac(files: list[Path], classes: Path, classpath: list[Path]) -> subprocess.CompletedProcess[str]:
    shutil.rmtree(classes, ignore_errors=True)
    classes.mkdir(parents=True)
    cp = ["-cp", os.pathsep.join(map(str, classpath))] if classpath else []
    return subprocess.run(["javac", "-encoding", "UTF-8", "-nowarn", *cp, "-d", str(classes), *map(str, files)],
                          capture_output=True, text=True)


def run_java(classes: Path, classpath: list[Path]) -> subprocess.CompletedProcess[bytes]:
    return subprocess.run(["java", "-cp", os.pathsep.join(map(str, [classes, *classpath])), P.MAIN],
                          capture_output=True, timeout=60)


def files_with_errors(output: str) -> set[str]:
    return {Path(m).name for m in re.findall(r"^(\S+\.java):\d+: error:", output, re.M)}


def expected_stdout() -> bytes:
    return (DEMO / P.EXPECTED_STDOUT).read_bytes()


def java_name(cobol_file: str) -> str:
    return P.PROGRAMS[cobol_file]["class"].rsplit(".", 1)[1] + ".java"


# ------------------------------------------------------------------ Jev


def run_pipeline(approach: str, index: int, model: str, jar: Path, m2: Path) -> tuple[RunResult, list[dict[str, Any]]]:
    """The translator with a decision provider: Jev (approach "jev") or a chat LLM in Jev's place ("decider")."""
    out = WORK / f"{approach}/run{index}"
    shutil.rmtree(out, ignore_errors=True)
    out.mkdir(parents=True)
    libs = libraries(caches={"maven": m2, "gradle": WORK / "no-gradle-cache"})
    libs.add(Library(id=P.LIBRARY["id"], cobol=Cobol.path(P.LIBRARY["cobol_path"]),
                     java=Java.maven(P.LIBRARY["coordinates"])))
    calls_log = out / "llm-calls.jsonl"
    if approach == "jev":
        provider = JevProvider(model, base_url=OPENROUTER_BASE_URL, api_key_env=KEY_ENV, timeout_seconds=120)
        before = quiet_usage()
    else:
        provider = LlmDecisionProvider(model, log=str(calls_log), api_key_env=KEY_ENV, timeout_seconds=120)
    log = io.StringIO()
    start = time.perf_counter()
    with contextlib.redirect_stderr(log):
        result = transpile(DEMO / "payroll", output=out, provider=provider, dialect=Dialect(**P.DIALECT),
                           layout=Layout(base_package=P.BASE_PACKAGE), copybook_paths=[DEMO / "payroll/copy"],
                           libraries=libs, target_project=DEMO / P.LIBRARY["target_project"])
    seconds = time.perf_counter() - start
    (out / "worker.log").write_text(log.getvalue())
    file_costs: dict[str, float] | None = None
    if approach == "jev":
        after = settled_usage(before)
        cost = after - before if after is not None else None
    else:  # the exact cost OpenRouter reported for each call, by the decision's file
        entries = [json.loads(line) for line in calls_log.read_text().splitlines()] if calls_log.exists() else []
        file_costs = {f: sum(e["cost_usd"] for e in entries if e["file"] == f) for f in P.PROGRAMS}
        cost = sum(e["cost_usd"] for e in entries)

    # Each decision the provider answered, scored against the rubric.
    decisions = []
    for d in result.decisions:
        r = result.resolutions.for_decision(d.id)
        if r is None or r.provider != provider.name:
            continue
        step = d.library.get("step") if d.library else None
        key = (d.source.file, d.source.line, step)
        want, why = P.RUBRIC.get(key, (None, "not in the rubric"))
        decisions.append({"file": d.source.file, "line": d.source.line, "step": step,
                          "statement": " ".join(d.source.text.split())[:60], "pick": r.candidate_id,
                          "outcome": r.outcome, "confidence": r.confidence,
                          "expected": sorted(want) if want else None, "why": why,
                          "ok": None if want is None else r.candidate_id in want})

    # Compile the generated project and run it.
    compiled = exact = False
    stdout = ""
    errors: set[str] = set()
    if result.ok:
        sources = sorted((out / "src/main/java").rglob("*.java"))
        c = javac(sources, out / "classes", [jar])
        compiled = c.returncode == 0
        errors = files_with_errors(c.stdout + c.stderr)
        if compiled:
            ran = run_java(out / "classes", [jar])
            stdout = ran.stdout.decode(errors="replace")
            exact = ran.returncode == 0 and ran.stdout == expected_stdout()

    calls = {f: sum(1 for d in decisions if d["file"] == f) for f in P.PROGRAMS}
    total_calls = sum(calls.values()) or 1
    files = []
    for f in P.PROGRAMS:
        mine = [d for d in decisions if d["file"] == f and d["ok"] is not None]
        java_file = out / "src/main/java" / (P.PROGRAMS[f]["class"].replace(".", "/") + ".java")
        checks = [{"name": f"line {d['line']}{' ' + d['step'] if d['step'] else ''}: {d['statement'][:40]}",
                   "ok": d["ok"], "detail": f"{d['pick']} (want {' or '.join(d['expected'])}; {d['why']})"}
                  for d in mine]
        if f == "PAYROLL.cbl":
            text = java_file.read_text() if java_file.exists() else ""
            used = "Withholding.tax(" in text
            checks.append({"name": "calls the acme-tax library", "ok": used,
                           "detail": "Withholding.tax(...) in Payroll.java" if used else "no library call"})
        share = calls[f] / total_calls
        file_cost = file_costs[f] if file_costs is not None else (cost * share if cost is not None else None)
        files.append(FileResult(
            file=f, cost_usd=file_cost, seconds=seconds * share,
            construct_passed=sum(1 for c in checks if c["ok"]), construct_total=len(checks),
            compiles=compiled or (result.ok and java_name(f) not in errors), checks=checks))
    note = "" if result.ok else f"status {result.status}: nothing written"
    return RunResult(approach, index, seconds, cost, compiled, exact, stdout, files, note), decisions


# ------------------------------------------------------------------ LLM

SYSTEM = (
    "You translate COBOL programs to Java 21. The Java must behave exactly like the COBOL program compiled with "
    "GnuCOBOL 3.2: it must print byte for byte the same output, with the same decimal truncation, rounding, "
    "editing and padding. Reply with exactly one complete Java source file in a single ```java code block and "
    "nothing else."
)


def copybook_text(name: str) -> str:
    return (DEMO / "payroll/copy" / f"{name}.cpy").read_text()


def llm_prompt(cobol_file: str) -> str:
    spec = P.PROGRAMS[cobol_file]
    package, cls = spec["class"].rsplit(".", 1)
    library = "\n\n".join(f"// {f.name}\n{f.read_text()}"
                          for f in sorted((DEMO / P.LIBRARY["sources"]).rglob("*.java")))
    parts = [
        f"Translate the COBOL program `{cobol_file}` to the Java class `{cls}` in package `{package}`.",
        "It is one program of a project translated one file at a time; the other programs are translated "
        "separately. The programs call each other through these Java interfaces (use them exactly):\n" + P.INTERFACES,
        "The team has replaced the COBOL programs under `common/tax/` (TAXCALC) with the Java library "
        f"`{P.LIBRARY['coordinates']}`, which is on the classpath. Where a program CALLs one of those programs, "
        "call the library instead when it does what the COBOL program does. The library's sources:\n"
        f"```java\n{library}```",
    ]
    for name in spec["copybooks"]:
        parts.append(f"Copybook `{name}` (copy/{name}.cpy, inlined where the program says COPY {name}):\n"
                     f"```cobol\n{copybook_text(name)}```")
    parts.append(f"The program `{cobol_file}`:\n```cobol\n{(DEMO / 'payroll' / cobol_file).read_text()}```")
    return "\n\n".join(parts)


def extract_java(text: str) -> str:
    blocks = re.findall(r"```(?:java)?\s*\n(.*?)```", text, re.S)
    return max(blocks, key=len) if blocks else text


FLOAT = re.compile(r"\b(double|float|Double|Float)\b|\.doubleValue\(|\.floatValue\(")


def llm_checks(cobol_file: str, java: str) -> list[dict[str, Any]]:
    """Static checks that the Java uses the construct the COBOL needs (the LLM's counterpart of the rubric)."""
    def check(name: str, ok: bool, detail: str) -> dict[str, Any]:
        return {"name": name, "ok": bool(ok), "detail": detail}

    no_float = not FLOAT.search(java)
    float_detail = "BigDecimal / integers only" if no_float else f"uses {FLOAT.search(java).group(0)}"
    if cobol_file == "PAYROLL.cbl":
        return [
            check("calls the acme-tax library", "Withholding.tax(" in java,
                  "Withholding.tax(...)" if "Withholding.tax(" in java else "no Withholding.tax call"),
            check("does not call the translated TAXCALC instead", "Taxcalc.call(" not in java,
                  "Taxcalc.call(...) used" if "Taxcalc.call(" in java else "library only"),
            check("money without floating point", no_float, float_detail),
            check("gross truncated to the cent", re.search(r"RoundingMode\.(DOWN|FLOOR)|ROUND_DOWN|ROUND_FLOOR", java),
                  "setScale(2, DOWN/FLOOR) present" if re.search(r"RoundingMode\.(DOWN|FLOOR)", java) else "no truncation"),
        ]
    if cobol_file == "PAYSLIP.cbl":
        return [
            check("hyphens in the name replaced", re.search(r"replace(All)?\(\s*['\"]-['\"]\s*,\s*['\"] ['\"]", java),
                  "replace('-', ' ')" if re.search(r"replace(All)?\(\s*['\"]-", java) else "no replacement found"),
            check("amounts without floating point", no_float, float_detail),
        ]
    # TAXCALC may delegate to the library itself (it implements this program): that does both below.
    library = "Withholding.tax(" in java
    return [
        check("tax without floating point", no_float, float_detail),
        check("tax rounded half up", library or re.search(r"HALF_UP", java),
              "delegated to Withholding.tax" if library else ("HALF_UP" if "HALF_UP" in java else "no HALF_UP")),
        check("allowance compared as a decimal", library or "compareTo(" in java,
              "delegated to Withholding.tax" if library else ("compareTo" if "compareTo(" in java else "no compareTo")),
    ]


STUBS = {
    "com.example.payroll.Payslip": "package com.example.payroll;\npublic class Payslip {\n"
        "  public static void call(String a, String b, java.math.BigDecimal c, java.math.BigDecimal d, java.math.BigDecimal e) {}\n}\n",
    "com.example.payroll.common.tax.Taxcalc": "package com.example.payroll.common.tax;\npublic class Taxcalc {\n"
        "  public static java.math.BigDecimal call(java.math.BigDecimal g) { return java.math.BigDecimal.ZERO; }\n}\n",
}


def run_llm(index: int, model: str, jar: Path) -> RunResult:
    out = WORK / f"llm/run{index}"
    shutil.rmtree(out, ignore_errors=True)
    src = out / "src"
    files: list[FileResult] = []
    paths: dict[str, Path] = {}
    total = time.perf_counter()
    for cobol_file, spec in P.PROGRAMS.items():
        fr = FileResult(file=cobol_file)
        path = src / (spec["class"].replace(".", "/") + ".java")
        path.parent.mkdir(parents=True, exist_ok=True)
        start = time.perf_counter()
        try:
            reply = openrouter("/chat/completions", {
                "model": model, "max_tokens": 8000, "usage": {"include": True},
                "messages": [{"role": "system", "content": SYSTEM}, {"role": "user", "content": llm_prompt(cobol_file)}]})
        except Exception as e:  # recorded, not fatal: the file counts as failed
            fr.seconds, fr.error = time.perf_counter() - start, f"{type(e).__name__}: {e}"
            files.append(fr)
            continue
        fr.seconds = time.perf_counter() - start
        usage = reply.get("usage") or {}
        fr.cost_usd = usage.get("cost")
        fr.tokens_in, fr.tokens_out = usage.get("prompt_tokens"), usage.get("completion_tokens")
        java = extract_java(reply["choices"][0]["message"]["content"] or "")
        path.write_text(java)
        (out / f"{path.stem}.reply.json").write_text(json.dumps(reply, indent=1))
        paths[spec["class"]] = path
        fr.checks = llm_checks(cobol_file, java)
        fr.construct_passed = sum(1 for c in fr.checks if c["ok"])
        fr.construct_total = len(fr.checks)
        # Compile this file alone against stubs of the other programs' interfaces.
        stubs = out / "stubs" / path.stem
        stub_files = []
        for cls, text in STUBS.items():
            if cls != spec["class"]:
                p = stubs / (cls.replace(".", "/") + ".java")
                p.parent.mkdir(parents=True, exist_ok=True)
                p.write_text(text)
                stub_files.append(p)
        c = javac([path, *stub_files], out / "classes-one", [jar])
        fr.compiles = c.returncode == 0
        if not fr.compiles:
            fr.error = (c.stdout + c.stderr).strip()[:2000]
        files.append(fr)
    seconds = time.perf_counter() - total

    compiled = exact = False
    stdout = note = ""
    if len(paths) == len(P.PROGRAMS):
        c = javac(list(paths.values()), out / "classes", [jar])
        compiled = c.returncode == 0
        if compiled:
            try:
                ran = run_java(out / "classes", [jar])
                stdout = ran.stdout.decode(errors="replace")
                exact = ran.returncode == 0 and ran.stdout == expected_stdout()
                if ran.returncode != 0:
                    note = f"exit {ran.returncode}: {ran.stderr.decode(errors='replace').strip()[:200]}"
            except subprocess.TimeoutExpired:
                note = "timed out"
        else:
            note = "the files do not compile together"
    else:
        note = "a request failed"
    costs = [f.cost_usd for f in files]
    cost = sum(costs) if all(c is not None for c in costs) else None
    return RunResult("llm", index, seconds, cost, compiled, exact, stdout, files, note)


# ------------------------------------------------------------------ report


def money(v: float | None) -> str:
    return "n/a" if v is None else f"${v:.5f}"


def mean(values: list[float | None]) -> float | None:
    vals = [v for v in values if v is not None]
    return statistics.mean(vals) if vals else None


def yes(b: bool) -> str:
    return "✅" if b else "❌"


APPROACHES = ("jev", "decider", "llm")


def report(runs: list[RunResult], decisions: dict[str, list[list[dict[str, Any]]]], args: argparse.Namespace,
           commit: str) -> str:
    label = {"jev": f"Jev (`{args.jev_model}`) decides, translator writes",
             "decider": f"`{args.llm_model}` decides in Jev's place, translator writes",
             "llm": f"`{args.llm_model}` writes the Java, one call per file"}
    out = [f"# Benchmark: Jev pipeline vs. LLM decider vs. LLM-only translation", "",
           f"Run {date.today().isoformat()} at `{commit}` with `benchmarks/jev-vs-llm/bench.py` "
           f"({args.runs} runs per approach, through OpenRouter).", "",
           "**Project**: `benchmarks/jev-vs-llm/project/payroll`: three COBOL programs (PAYROLL, PAYSLIP, TAXCALC) and three "
           "copybooks (EMPTAB, PAYTOT, SLIPREC). `common/tax/` (TAXCALC) is mapped to the Java library "
           "`com.acme:acme-tax:1.0`, and every ADD, SUBTRACT, COMPUTE, DISPLAY, IF, STRING and INSPECT can use the "
           "runtime library (exact) or the plain JDK (approximate). See `benchmarks/jev-vs-llm/project/project.py`.", "",
           "**Approaches**", "",
           f"- **jev**: {label['jev']}. The deterministic translator writes all the Java; Jev only picks among the "
           "candidates the worker generated (runtime or JDK, which library call).",
           f"- **decider**: {label['decider']}. The same pipeline and the same decisions, with `benchmarks/jev-vs-llm/"
           "llm_decider.py` in Jev's place: `JevProvider`'s state, questions and task texts, answered by a chat "
           "completion as JSON with a probability per option. It still only picks a candidate.",
           f"- **llm**: {label['llm']}: each COBOL file with its copybooks, the library's sources and the "
           "programs' interfaces, and the instruction to match GnuCOBOL 3.2 byte for byte. One shot, no feedback.", "",
           "**How it is measured**", "",
           "- *Cost*: jev: the OpenRouter key's usage delta over the run (Jev's SDK reports no prices), split by the "
           "file's share of decisions; decider and llm: the cost OpenRouter reports for each call, by file.",
           "- *Time*: wall time. jev and decider include the whole translation (JVM worker start, ProLeap, every "
           "provider call), split by the file's share of decisions; llm is the API call per file.",
           "- *Construct*: jev and decider: each decision's pick against the rubric in `project.py` (runtime for "
           "money, JDK for counters and messages, `Withholding.tax` for the TAXCALC call), plus whether Payroll.java "
           "calls the library. llm: static checks of its Java for the same intent (library call, no floating point "
           "on money, cent truncation, HALF_UP rounding, hyphen replacement).",
           "- *Compiles*: javac on the file (LLM: against stubs of the other programs' interfaces).",
           "- *Exact*: the whole program compiled and run, stdout byte-for-byte equal to GnuCOBOL 3.2's "
           "(`benchmarks/jev-vs-llm/project/expected/stdout.txt`), exit status 0.", ""]

    out += ["## Summary (mean over runs)", "",
            "| Approach | Cost / file | Time / file | Cost / run | Time / run | Construct | Compiles (files) | Exact runs |",
            "|---|---|---|---|---|---|---|---|"]
    for a in APPROACHES:
        rs = [r for r in runs if r.approach == a]
        if not rs:
            continue
        fs = [f for r in rs for f in r.files]
        passed, total = sum(f.construct_passed for f in fs), sum(f.construct_total for f in fs)
        out.append(f"| {label[a]} | {money(mean([f.cost_usd for f in fs]))} | {mean([f.seconds for f in fs]):.1f}s | "
                   f"{money(mean([r.cost_usd for r in rs]))} | {mean([r.seconds for r in rs]):.1f}s | "
                   f"{passed}/{total} ({passed / max(total, 1):.0%}) | {sum(f.compiles for f in fs)}/{len(fs)} | "
                   f"{sum(r.exact for r in rs)}/{len(rs)} |")
    out.append("")

    out += ["## Per file (mean over runs)", "",
            "| Approach | File | Cost | Time | Construct | Compiles |", "|---|---|---|---|---|---|"]
    for a in APPROACHES:
        rs = [r for r in runs if r.approach == a]
        for f in P.PROGRAMS:
            fs = [x for r in rs for x in r.files if x.file == f]
            if not fs:
                continue
            passed, total = sum(x.construct_passed for x in fs), sum(x.construct_total for x in fs)
            out.append(f"| {a} | `{f}` | {money(mean([x.cost_usd for x in fs]))} | {mean([x.seconds for x in fs]):.1f}s | "
                       f"{passed}/{total} | {sum(x.compiles for x in fs)}/{len(fs)} |")
    out.append("")

    out += ["## Per run", "", "| Approach | Run | Cost | Time | Construct | Compiles | Exact | Note |",
            "|---|---|---|---|---|---|---|---|"]
    for r in sorted(runs, key=lambda r: (APPROACHES.index(r.approach), r.run)):
        passed, total = sum(f.construct_passed for f in r.files), sum(f.construct_total for f in r.files)
        out.append(f"| {r.approach} | {r.run} | {money(r.cost_usd)} | {r.seconds:.1f}s | {passed}/{total} | "
                   f"{yes(r.compiles)} | {yes(r.exact)} | {r.note} |")
    out.append("")

    for a, runs_decisions in decisions.items():
        out += decision_table(f"Decisions: {a}", runs_decisions)
    return finish(out, runs)


def decision_table(title: str, decisions: list[list[dict[str, Any]]]) -> list[str]:
    out: list[str] = []
    if decisions:
        out += [f"## {title}", "", "Pick and confidence per run against the rubric (✅ acceptable, ❌ not, · unscored).", "",
                "| File | Line | Statement | Expected | " + " | ".join(f"Run {i + 1}" for i in range(len(decisions))) + " |",
                "|---|---|---|---|" + "---|" * len(decisions)]
        keys: list[tuple[str, int, str | None]] = []
        for ds in decisions:
            for d in ds:
                k = (d["file"], d["line"], d["step"])
                if k not in keys:
                    keys.append(k)
        for k in sorted(keys, key=lambda k: (list(P.PROGRAMS).index(k[0]) if k[0] in P.PROGRAMS else 9, k[1], k[2] or "")):
            row = None
            cells = []
            for ds in decisions:
                d = next((x for x in ds if (x["file"], x["line"], x["step"]) == k), None)
                row = row or d
                if d is None:
                    cells.append("-")
                else:
                    mark = "·" if d["ok"] is None else yes(d["ok"])
                    conf = f" {d['confidence']:.2f}" if d["confidence"] is not None else ""
                    cells.append(f"{mark} `{short(d['pick'])}`{conf}")
            stmt = row["statement"] + (f" ({row['step']})" if row["step"] else "")
            want = " / ".join(f"`{short(e)}`" for e in row["expected"]) if row["expected"] else "-"
            out.append(f"| `{k[0]}` | {k[1]} | `{stmt[:48]}` | {want} | " + " | ".join(cells) + " |")
        out.append("")
    return out


def finish(out: list[str], runs: list[RunResult]) -> str:

    llm_runs = [r for r in runs if r.approach == "llm"]
    if llm_runs:
        out += ["## LLM checks", "", "| File | Check | " + " | ".join(f"Run {r.run}" for r in llm_runs) + " |",
                "|---|---|" + "---|" * len(llm_runs)]
    for f in P.PROGRAMS if llm_runs else ():
        names: list[str] = []
        for r in llm_runs:
            for x in r.files:
                if x.file == f:
                    names += [c["name"] for c in x.checks if c["name"] not in names]
        for n in names + ["compiles"]:
            cells = []
            for r in llm_runs:
                x = next(x for x in r.files if x.file == f)
                if n == "compiles":
                    cells.append(yes(x.compiles))
                else:
                    c = next((c for c in x.checks if c["name"] == n), None)
                    cells.append("-" if c is None else f"{yes(c['ok'])} {c['detail']}")
            out.append(f"| `{f}` | {n} | " + " | ".join(cells) + " |")
    out.append("")

    wrong = [r for r in runs if not r.exact and r.stdout]
    if wrong:
        out += ["## Output differences", "", "Expected (GnuCOBOL 3.2):", "", "```", expected_stdout().decode().rstrip("\n"), "```", ""]
        for r in wrong:
            out += [f"{r.approach} run {r.run}:", "", "```", r.stdout.rstrip("\n"), "```", ""]
    out.append(f"Raw results and every run's Java: `build/benchmarks/jev-vs-llm/` (git-ignored).")
    return "\n".join(out) + "\n"


def short(candidate: str | None) -> str:
    if candidate is None:
        return "held"
    return candidate.replace("library:acme.tax:", "").replace("builtin:", "")


# ------------------------------------------------------------------ main


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("--runs", type=int, default=3)
    parser.add_argument("--jev-model", default=os.environ.get("JEV_MODEL", "jev-latest"))
    parser.add_argument("--llm-model", default="anthropic/claude-haiku-5.5")
    parser.add_argument("--only", choices=APPROACHES, action="append", help="run only this approach (repeatable)")
    parser.add_argument("--output", type=Path, default=Path(__file__).resolve().parent / "results.md")
    args = parser.parse_args()
    load_env()
    if not os.environ.get(KEY_ENV):
        sys.exit(f"set {KEY_ENV} in the environment or in .env (this calls paid APIs through OpenRouter)")
    WORK.mkdir(parents=True, exist_ok=True)
    m2 = WORK / "m2"
    jar = install_library(m2)
    commit = subprocess.run(["git", "rev-parse", "--short", "HEAD"], cwd=ROOT, capture_output=True, text=True).stdout.strip()

    runs: list[RunResult] = []
    decisions: dict[str, list[list[dict[str, Any]]]] = {}
    chosen = [a for a in APPROACHES if not args.only or a in args.only]
    for i in range(1, args.runs + 1):
        for a in chosen:
            print(f"[{a}] run {i}/{args.runs} ...", file=sys.stderr, flush=True)
            if a == "llm":
                r = run_llm(i, args.llm_model, jar)
            else:
                r, ds = run_pipeline(a, i, args.jev_model if a == "jev" else args.llm_model, jar, m2)
                decisions.setdefault(a, []).append(ds)
            runs.append(r)
            print(f"[{a}] run {i}: {r.seconds:.1f}s {money(r.cost_usd)} exact={r.exact} {r.note}", file=sys.stderr)
    runs.sort(key=lambda r: (APPROACHES.index(r.approach), r.run))

    (WORK / "results.json").write_text(json.dumps({"runs": [asdict(r) for r in runs], "decisions": decisions},
                                                  indent=1, default=str))
    args.output.write_text(report(runs, decisions, args, commit))
    print(f"wrote {args.output}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())

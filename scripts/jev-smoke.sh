#!/usr/bin/env bash
# Opt-in live run of examples/cobol-to-java-jev with Jev as the decision provider. Two projects
# do not state their collating sequence (the default rules translate everything else): Jev infers
# the setting from the code; the run prints its pick and confidence, the setting each program was
# written for, and the translated program's output. The billing project maps its date routines to
# a Java library (libraries/acme-dates, installed into a scratch Maven repository under build/):
# Jev builds each library call; the run prints its choices, then runs the library translation and
# the plain COBOL translation side by side, since library calls are not equivalence-checked.
# It calls the API, so it is never part of test.sh or docker.sh test.
#   OPENROUTER_API_KEY=... ./scripts/jev-smoke.sh     # through OpenRouter (model jev-latest)
#   TYPESAFE_API_KEY=... ./scripts/jev-smoke.sh       # TypeSafe's own API (model jev-preview)
# JEV_ENDPOINT=openrouter|typesafe chooses when both keys are set (default: openrouter if its key is
# set). JEV_MODEL overrides the model, JEV_API_KEY_ENV the key variable, JEV_TIMEOUT_SECONDS
# (default 120) the timeout of each API call. JEV_PROJECTS=billing,... runs only those projects.
# JEV_VERBOSE=1 shows the worker log. Any of these may also be set in $ROOT/.env (see
# .env.example); variables already in the environment take precedence.
# Exit status: 0 when both runs finished (whatever Jev chose), 1 when one failed.
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

# KEY=value lines of .env, read as data (never executed); quotes and a leading `export` are allowed.
if [[ -f "$ROOT/.env" ]]; then
  while IFS= read -r line || [[ -n "$line" ]]; do
    line="${line#"${line%%[![:space:]]*}"}"
    line="${line#export }"
    [[ "$line" =~ ^([A-Za-z_][A-Za-z0-9_]*)=(.*)$ ]] || continue
    name="${BASH_REMATCH[1]}" value="${BASH_REMATCH[2]%$'\r'}"
    if [[ "$value" =~ ^\"(.*)\"$ || "$value" =~ ^\'(.*)\'$ ]]; then value="${BASH_REMATCH[1]}"; fi
    [[ -n "${!name+set}" ]] || export "$name=$value"
  done < "$ROOT/.env"
fi

endpoint="${JEV_ENDPOINT:-$([[ -n "${OPENROUTER_API_KEY:-}" ]] && echo openrouter || echo typesafe)}"
case "$endpoint" in
  openrouter) key_env="${JEV_API_KEY_ENV:-OPENROUTER_API_KEY}" model="${JEV_MODEL:-jev-latest}"
              base_url="https://openrouter.ai/api" ;;
  typesafe)   key_env="${JEV_API_KEY_ENV:-TYPESAFE_API_KEY}" model="${JEV_MODEL:-jev-preview}"
              base_url="${TYPESAFE_BASE_URL:-}" ;;
  *) die "JEV_ENDPOINT must be openrouter or typesafe, not '$endpoint'" ;;
esac
[[ -n "${!key_env:-}" ]] || die "set OPENROUTER_API_KEY or TYPESAFE_API_KEY (this script calls the live API; $key_env is empty)"
# Incremental: picks up any change to the core or the worker since the last run.
"$ROOT/scripts/build.sh" >&2

info "examples/cobol-to-java-jev with JevProvider via $endpoint (model $model, key from \$$key_env)"
# --all-packages --extra jev installs the optional typesafe_sdk dependency from uv.lock.
ROOT="$ROOT" JEV_API_KEY_ENV="$key_env" JEV_MODEL="$model" JEV_BASE_URL="$base_url" \
  uv run --project "$ROOT" --frozen --all-packages --extra jev python - <<'PY'
import contextlib
import io
import os
import subprocess
import sys
from pathlib import Path

from rich.console import Console
from rich.table import Table
from rich.text import Text

import zipfile

from cobol_to_java import Cobol, Dialect, Java, Layout, Library, libraries
from transpiler import DeterministicProvider, JevProvider, transpile
from transpiler.console import print_log, print_summary

root = Path(os.environ["ROOT"])
demo = root / "examples/cobol-to-java-jev"
sys.path.insert(0, str(demo))
from projects import PROJECTS  # noqa: E402

console = Console()
stderr = Console(stderr=True)
failed = False


def install(sources: Path, coordinates: str, m2: Path) -> Path:
    """Compiles a library and puts its jar and sources jar where Maven installs them; returns the jar."""
    group, artifact, version = coordinates.split(":")
    target = m2 / group.replace(".", "/") / artifact / version
    classes = m2.parent / f"{artifact}-classes"
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


def compile_and_run(out: Path, main: str, classpath: list[Path]) -> str:
    classes = out / "classes"
    cp = ["-cp", os.pathsep.join(map(str, classpath))] if classpath else []
    subprocess.run(["javac", "-encoding", "UTF-8", *cp, "-d", str(classes),
                    *map(str, (out / "src/main/java").rglob("*.java"))], check=True)
    run = subprocess.run(["java", "-cp", os.pathsep.join(map(str, [classes, *classpath])), main], capture_output=True, text=True)
    return run.stdout.strip().replace("\n", " | ")


only = [p for p in os.environ.get("JEV_PROJECTS", "").split(",") if p]
for name, project in PROJECTS.items():
    if only and name not in only:
        continue
    jev = JevProvider(os.environ["JEV_MODEL"], api_key_env=os.environ["JEV_API_KEY_ENV"],
                      base_url=os.environ["JEV_BASE_URL"] or None,
                      timeout_seconds=float(os.environ.get("JEV_TIMEOUT_SECONDS", "120")))
    out = root / "build/examples/cobol-to-java-jev" / name
    console.rule(Text(f"{name}: {project['about']}", style="bold cyan"))
    console.print("Translating and requesting a Jev decision…", style="cyan")
    verbose = os.environ.get("JEV_VERBOSE", "").lower() in ("1", "true", "yes")
    log = io.StringIO()
    libs, target, classpath = None, None, []
    if "library" in project:
        spec = project["library"]
        m2 = root / "build/examples/m2"
        classpath = [install(demo / spec["sources"], spec["coordinates"], m2)]
        libs = libraries(caches={"maven": m2, "gradle": root / "build/examples/no-gradle-cache"})
        libs.add(Library(id=spec["id"], cobol=Cobol.path(spec["cobol_path"]), java=Java.maven(spec["coordinates"])))
        target = demo / spec["target_project"]
    try:
        with contextlib.redirect_stderr(log):
            result = transpile(demo / name, output=out, provider=jev, dialect=Dialect(**project["dialect"]),
                               layout=Layout(base_package=f"com.example.{name}"), verbose=verbose,
                               libraries=libs, target_project=target)
    finally:
        print_log(log.getvalue(), stderr)
    print_summary(result, console)
    library_decisions = [d for d in result.decisions if d.library]
    if library_decisions:
        table = Table(title="Library decisions (Jev)", header_style="bold cyan")
        for column in ("Line", "Part", "Step", "Choice", "Confidence"):
            table.add_column(column)
        for d in library_decisions:
            r = result.resolutions.for_decision(d.id)
            choice = (r.candidate_id or f"held ({r.held_reason or r.gate})") if r else "-"
            confidence = f"{r.confidence:.2f}" if r and r.confidence is not None else "-"
            table.add_row(str(d.source.line), d.library["part"]["name"], d.library["step"], choice, confidence)
        console.print(table)
        for lib in result.libraries_used:
            for u in lib.uses:
                calls = " . ".join(u.chain) if u.chain else (u.reason or "")
                console.print(Text(f"  {u.part} at {u.file}:{u.line}: {u.outcome}  {calls}",
                                   style="yellow" if u.outcome == "library" else "green"))
    for index, s in enumerate(result.settings):
        p = s.resolution.confidence
        want = project["written_for"].get(s.field)
        verdict = "matches" if s.value == want else f"does NOT match (written for {want!r})"
        confidence = f"{p:.2f}" if p is not None else "unavailable"
        console.print(Text(f"Jev chose {s.field} = {s.value!r} (confidence {confidence}); {verdict}",
                           style="green" if s.value == want else "yellow"))
        probs = result.report["settings"][index]["resolution"].get("probabilities", {})
        if probs:
            table = Table(title="Decision probabilities", header_style="bold cyan")
            table.add_column("Candidate")
            table.add_column("Probability", justify="right")
            for candidate, probability in sorted(probs.items(), key=lambda kv: -kv[1]):
                table.add_row(str(candidate), f"{probability:.2f}")
            console.print(table)
    failed |= result.status == "failed"
    if result.ok:
        output = compile_and_run(out, project["main"], classpath)
        console.print(Text("Output:   ", style="bold") + Text(output))
        if libs is not None:
            # Library calls are not equivalence-checked: run the plain COBOL translation as well.
            plain = out.parent / f"{name}-without-libraries"
            again = transpile(demo / name, output=plain, provider=DeterministicProvider(),
                              dialect=Dialect(**project["dialect"]), layout=Layout(base_package=f"com.example.{name}"))
            if again.ok:
                reference = compile_and_run(plain, project["main"], [])
                console.print(Text("COBOL:    ", style="bold") + Text(reference))
                same = reference == output
                console.print(Text("The library translation prints " + ("the same as" if same else "something else than")
                                   + " the COBOL translation", style="green" if same else "yellow"))
        console.print(Text("Expected: ", style="bold") + Text(" | ".join(project["expected_output"])))
    console.print(Text("Review queue: ", style="bold") + Text(str(out / "review.md"), style="cyan"))
sys.exit(1 if failed else 0)
PY

"""Shared helpers for integration and equivalence tests (stdlib only)."""

from __future__ import annotations

import json
import os
import shutil
import subprocess
import tempfile
from dataclasses import dataclass
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[1]
FIXTURES = REPO_ROOT / "translators/cobol-to-java/fixtures"
BUILD_DIR = Path(os.environ.get("TRANSPILER_BUILD_DIR", REPO_ROOT / "build"))
CORE = Path(os.environ.get("TRANSPILER_CORE", BUILD_DIR / "core/transpiler-core"))
WORKER = REPO_ROOT / "translators/cobol-to-java/java/build/install/cobol-to-java-worker/bin/cobol-to-java-worker"
REFERENCE_COBC = "3.2"


@dataclass(frozen=True)
class Case:
    name: str
    path: Path
    spec: dict

    @property
    def src(self) -> Path:
        return self.path / "src"

    @property
    def equivalence(self) -> dict | None:
        return self.spec.get("equivalence")


def cases() -> list[Case]:
    return [Case(p.parent.name, p.parent, json.loads(p.read_text())) for p in sorted(FIXTURES.glob("*/case.json"))]


@dataclass(frozen=True)
class Execution:
    stdout: bytes
    exit_code: int


def cobc_version() -> str | None:
    if not shutil.which("cobc"):
        return None
    first = subprocess.run(["cobc", "--version"], capture_output=True, text=True).stdout.splitlines()[0]
    return first.split()[-1]  # "cobc (GnuCOBOL) 3.2.0" -> "3.2.0"


def run_cobol(case: Case) -> Execution:
    """Compiles the case's programs with GnuCOBOL (main program first) and runs the result."""
    eq = case.equivalence
    with tempfile.TemporaryDirectory(prefix="cobc-") as tmp:
        exe = Path(tmp) / "program"
        args = ["cobc", "-x", "-o", str(exe), *eq.get("cobc_flags", [])]
        for d in eq.get("cobc_copybook_dirs", []):
            args += ["-I", str(case.src / d)]
        args += [str(case.src / p) for p in eq["programs"]]
        compiled = subprocess.run(args, capture_output=True, text=True)
        if compiled.returncode != 0:
            raise AssertionError(f"cobc failed for {case.name}:\n{compiled.stderr}")
        copy_data(case, Path(tmp))
        run = subprocess.run([str(exe)], capture_output=True, timeout=60, cwd=tmp, input=stdin(case),
                             env={**os.environ, **eq.get("env", {})})
        return Execution(run.stdout, run.returncode)


def stdin(case: Case) -> bytes:
    """Bytes fed to the program's standard input (``<fixture>/stdin.txt``, empty by default)."""
    path = case.path / "stdin.txt"
    return path.read_bytes() if path.is_file() else b""


def mappings(case: Case):
    """The default mappings with the case's ``replace_rules`` ({rule id: changed fields}) applied."""
    from cobol_to_java import default_mappings

    registry = default_mappings()
    for rule_id, changes in case.spec.get("replace_rules", {}).items():
        registry.replace(rule_id, **changes)
    return registry


def copy_data(case: Case, run_dir: Path) -> None:
    """Puts the fixture's input files (``<fixture>/data/``) in the directory the program runs in."""
    data = case.path / "data"
    if data.is_dir():
        for f in data.iterdir():
            if f.is_file():
                shutil.copy(f, run_dir / f.name)


def golden(case: Case) -> Execution:
    expected = case.path / "expected"
    return Execution((expected / "stdout.txt").read_bytes(), int((expected / "exit_code").read_text().strip()))


def run_java(output: Path, main_class: str, input: bytes = b"", env: dict[str, str] | None = None,
             case: Case | None = None) -> Execution:
    sources = sorted(str(p) for p in (output / "src/main/java").rglob("*.java"))
    classes = output / "classes"
    compiled = subprocess.run(["javac", "-encoding", "UTF-8", "-Xlint:all", "-Werror", "-d", str(classes), *sources],
                              capture_output=True, text=True)
    if compiled.returncode != 0:
        raise AssertionError(f"generated Java does not compile:\n{compiled.stderr}")
    with tempfile.TemporaryDirectory(prefix="java-") as tmp:
        if case is not None:
            copy_data(case, Path(tmp))
        run = subprocess.run(["java", "-cp", str(classes.resolve()), main_class], capture_output=True, timeout=60, input=input,
                             cwd=tmp, env=None if env is None else {**os.environ, **env})
    return Execution(run.stdout, run.returncode)

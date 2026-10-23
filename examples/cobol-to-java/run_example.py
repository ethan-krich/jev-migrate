"""Translates examples/cobol-to-java/input to Java, compiles it and runs it.

    ./scripts/run.sh            (or: uv run python examples/cobol-to-java/run_example.py)
"""

from __future__ import annotations

import subprocess
import sys
from pathlib import Path

from cobol_to_java import Layout, default_mappings
from transpiler import transpile

HERE = Path(__file__).resolve().parent
OUTPUT = HERE.parents[1] / "build/examples/cobol-to-java"

sys.path.insert(0, str(HERE))
from mappings import configure  # noqa: E402

mappings = configure(default_mappings())
result = transpile(HERE / "input", output=OUTPUT, mappings=mappings, layout=Layout(base_package="com.example.legacy"))
print(result.summary(), flush=True)
if not result.ok:
    sys.exit(2)

classes = OUTPUT / "classes"
sources = [str(p) for p in (OUTPUT / "src/main/java").rglob("*.java")]
subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(classes), *sources], check=True)
print("\n--- running com.example.legacy.payroll.Payroll ---", flush=True)
sys.exit(subprocess.run(["java", "-cp", str(classes), "com.example.legacy.payroll.Payroll"]).returncode)

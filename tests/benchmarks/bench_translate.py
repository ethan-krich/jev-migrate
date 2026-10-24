"""Rough translation timing (not part of the test suite).

    uv run python tests/benchmarks/bench_translate.py [--copies N]

Copies the multi-program fixture N times into one source tree so a single persistent worker
translates N*3 programs, then reports wall time per run and per program.
"""

from __future__ import annotations

import argparse
import shutil
import sys
import tempfile
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from support import FIXTURES  # noqa: E402

from cobol_to_java import Layout  # noqa: E402
from transpiler import transpile  # noqa: E402


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--copies", type=int, default=10)
    args = parser.parse_args()
    src = FIXTURES / "multi_program/src"
    with tempfile.TemporaryDirectory() as tmp:
        root = Path(tmp) / "src"
        for i in range(args.copies):
            dest = root / f"copy{i}"
            shutil.copytree(src, dest)
            # PROGRAM-IDs must stay unique across the project.
            for f in dest.rglob("*.c[bo][lb]"):
                f.write_text(f.read_text().replace("PAY-MAIN", f"PAY-MAIN{i}").replace("PAY-UTIL", f"PAY-UTIL{i}")
                             .replace("RPT01", f"RPT{i:02d}"))
        start = time.perf_counter()
        result = transpile(root, output=Path(tmp) / "out", layout=Layout(base_package="bench"),
                           copybook_paths=[d / "shared/copybooks" for d in root.iterdir()])
        elapsed = time.perf_counter() - start
    n = len(result.programs)
    print(f"{result.status}: {n} programs in {elapsed:.2f}s ({elapsed / max(n, 1) * 1000:.0f} ms/program incl. JVM start)")


if __name__ == "__main__":
    main()

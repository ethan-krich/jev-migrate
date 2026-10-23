"""Records GnuCOBOL's behaviour for each equivalence fixture into <fixture>/expected/.

Run inside the pinned toolchain: ./scripts/docker.sh uv run python tests/equivalence/record_fixtures.py
The recorded output lets equivalence tests run on machines without GnuCOBOL; when cobc is
available the tests compare against it live as well.
"""

from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from support import REFERENCE_COBC, cases, cobc_version, run_cobol  # noqa: E402


def main() -> int:
    version = cobc_version()
    if version is None:
        print("cobc not found; run inside ./scripts/docker.sh", file=sys.stderr)
        return 1
    if not version.startswith(REFERENCE_COBC):
        print(f"refusing to record with GnuCOBOL {version}; fixtures use {REFERENCE_COBC}", file=sys.stderr)
        return 1
    for case in cases():
        if not case.equivalence:
            continue
        result = run_cobol(case)
        out = case.path / "expected"
        out.mkdir(exist_ok=True)
        (out / "stdout.txt").write_bytes(result.stdout)
        (out / "exit_code").write_text(f"{result.exit_code}\n")
        (out / "cobc_version").write_text(f"{version}\n")
        print(f"{case.name}: exit {result.exit_code}, {len(result.stdout)} bytes")
    return 0


if __name__ == "__main__":
    sys.exit(main())

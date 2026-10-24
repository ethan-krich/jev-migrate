"""Behavioural equivalence: the translated Java must reproduce GnuCOBOL's observable behaviour.

stdout is compared byte for byte (no normalisation) together with the exit status. With cobc on
PATH (e.g. ./scripts/docker.sh test) the COBOL program is compiled and run live; otherwise the
output recorded from GnuCOBOL 3.2 in <fixture>/expected/ is used.
"""

from __future__ import annotations

import difflib
from pathlib import Path

import pytest
from cobol_to_java import Dialect, Layout
from support import REFERENCE_COBC, Case, Execution, cases, cobc_version, golden, run_cobol, run_java, stdin
from transpiler import transpile

CASES = [c for c in cases() if c.equivalence]


def describe(expected: Execution, actual: Execution) -> str:
    diff = difflib.unified_diff(
        expected.stdout.decode("latin-1").splitlines(keepends=True),
        actual.stdout.decode("latin-1").splitlines(keepends=True),
        "cobol", "java",
    )
    return f"exit: cobol={expected.exit_code} java={actual.exit_code}\n" + "".join(diff)


@pytest.mark.equivalence
@pytest.mark.parametrize("case", CASES, ids=[c.name for c in CASES])
def test_java_matches_cobol(case: Case, tmp_path: Path, built) -> None:
    result = transpile(
        case.src,
        output=tmp_path,
        layout=Layout(**case.spec.get("layout", {})),
        dialect=Dialect(**case.spec.get("dialect", {})),
        copybook_paths=[case.path / p for p in case.spec.get("copybook_paths", [])],
    )
    assert result.ok, result.summary()
    java = run_java(tmp_path, case.equivalence["main_class"], stdin(case), case.equivalence.get("env"))

    version = cobc_version()
    if version is not None:
        cobol = run_cobol(case)
        assert java == cobol, describe(cobol, java)
        if version.startswith(REFERENCE_COBC):
            assert golden(case) == cobol, f"recorded output for {case.name} is stale; run record_fixtures.py"
    else:
        expected = golden(case)
        assert java == expected, describe(expected, java)

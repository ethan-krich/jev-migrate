"""Every fixture translated through the public API: Python -> C++ orchestrator -> Java worker."""

from __future__ import annotations

from pathlib import Path

import pytest
from cobol_to_java import Dialect, Layout
from support import Case, cases
from transpiler import transpile

CASES = cases()


def translate(case: Case, output: Path):
    return transpile(
        case.src,
        output=output,
        layout=Layout(**case.spec.get("layout", {})),
        dialect=Dialect(**case.spec.get("dialect", {})),
        copybook_paths=[case.path / p for p in case.spec.get("copybook_paths", [])],
    )


@pytest.mark.integration
@pytest.mark.parametrize("case", CASES, ids=[c.name for c in CASES])
def test_fixture_translation(case: Case, tmp_path: Path, built) -> None:
    result = translate(case, tmp_path)
    expect = case.spec["expect"]
    assert result.status == expect["status"], result.summary()
    assert (tmp_path / "transpile-report.json").is_file()

    if result.status == "ok":
        assert not result.pending_decisions
        assert all(p.status == "translated" for p in result.programs)
    else:
        # Incomplete translations never write (partial) Java.
        assert result.files_written == []
        assert not (tmp_path / "src").exists()

    for source, path in expect.get("outputs", {}).items():
        program = next(p for p in result.programs if p.file == source)
        assert program.output_path == path
        assert (tmp_path / path).is_file()

    if "decisions" in expect:
        actual = [(d.reason.value, d.operation, d.source.line) for d in result.pending_decisions]
        wanted = [(d["reason"], d["operation"], d["line"]) for d in expect["decisions"]]
        assert actual == wanted, result.summary()
        for want, got in zip(expect["decisions"], result.pending_decisions):
            if "candidates" in want:
                assert [c.id for c in got.candidates] == want["candidates"]

    for source, codes in expect.get("diagnostics", {}).items():
        program = next(p for p in result.programs if p.file == source)
        assert program.status == "failed"
        assert sorted(d["code"] for d in program.diagnostics) == sorted(codes)


@pytest.mark.integration
def test_translation_is_deterministic(tmp_path: Path, built) -> None:
    case = next(c for c in CASES if c.name == "multi_program")
    first, second = translate(case, tmp_path / "a"), translate(case, tmp_path / "b")
    assert first.ok and second.ok
    for rel in first.files_written:
        assert (tmp_path / "a" / rel).read_bytes() == (tmp_path / "b" / rel).read_bytes()

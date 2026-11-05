"""Library mappings end to end: transpile(libraries=...) with chosen library calls.

Library output is not equivalence-checked (that is the point of the warning); these tests check
that it compiles against the library, runs, and is flagged everywhere. Choosing cobol:translate
everywhere is checked against a run without libraries in test_worker_protocol.py.
"""

from __future__ import annotations

import subprocess
import zipfile
from pathlib import Path

import pytest
from cobol_to_java import Cobol, Dialect, Java, Layout, Library, libraries
from support import FIXTURES
from transpiler import DecisionResponse, StaticProvider, transpile

pytestmark = pytest.mark.integration

CASE = FIXTURES / "library_calls"
DIALECT = Dialect(encoding="ascii", collating_sequence="native", out_of_range="fail")


def install(m2: Path) -> None:
    """Puts the fixture library in a fake Maven repository, as `mvn install` with sources would."""
    d = m2 / "com/acme/acme-dates/2.3.1"
    d.mkdir(parents=True)
    with zipfile.ZipFile(d / "acme-dates-2.3.1-sources.jar", "w") as z:
        for f in sorted((CASE / "lib").rglob("*.java")):
            z.write(f, f.relative_to(CASE / "lib").as_posix())
    (d / "acme-dates-2.3.1.jar").write_bytes(b"")


PICKS = {
    "decision-1": "library:acme.dates:Dates.addDays(int,int)",  # CALL "DATEADD"
    "decision-2": "library:acme.dates:Dates.dayOfWeek(int)",    # CALL "WEEKDAY"
    "decision-3": "library:acme.dates:DateFormatter.new()",     # CALL "DATEFMT"
    "decision-4": "args:0,1",                                    # addDays(yyyymmdd = WS-INVOICE-DATE, days = WS-TERMS-DAYS)
    "decision-5": "args:0",                                      # dayOfWeek(yyyymmdd = WS-DUE-DATE)
    "decision-6": "lifetime:per_program",                        # one DateFormatter
}


def run(tmp_path: Path, picks: dict[str, str], target: str | None) -> tuple:
    m2 = tmp_path / "m2"
    install(m2)
    libs = libraries(caches={"maven": m2, "gradle": tmp_path / "gradle"})
    libs.add(Library(id="acme.dates", cobol=Cobol.path("common/dates/"), java=Java.maven("com.acme:acme-dates:2.3.1")))
    project = None
    if target is not None:
        project = tmp_path / "app"
        project.mkdir()
        (project / "pom.xml").write_text(target)
    out = tmp_path / "out"
    provider = StaticProvider([DecisionResponse(c, decision_id=d) for d, c in picks.items()])
    result = transpile(CASE / "src", output=out, libraries=libs, target_project=project, provider=provider,
                       layout=Layout(base_package="fixtures.libs"), dialect=DIALECT)
    return result, out


def compile_and_run(out: Path, extra_sources: Path | None) -> str:
    sources = [str(p) for p in (out / "src/main/java").rglob("*.java")]
    args = ["javac", "-encoding", "UTF-8", "-d", str(out / "classes")]
    if extra_sources is not None:
        args += ["-sourcepath", str(extra_sources)]
    subprocess.run(args + sources, check=True, capture_output=True)
    return subprocess.run(["java", "-cp", str(out / "classes"), "fixtures.libs.Billing"], check=True, capture_output=True,
                          text=True).stdout


POM = """<project><modelVersion>4.0.0</modelVersion><dependencies>
<dependency><groupId>com.acme</groupId><artifactId>acme-dates</artifactId><version>2.3.1</version></dependency>
</dependencies></project>"""


def test_library_calls_compile_run_and_are_flagged(tmp_path):
    result, out = run(tmp_path, PICKS, POM)
    assert result.ok, result.summary()
    assert result.equivalence == "partial"
    [lib] = result.libraries_used
    assert lib.java == "com.acme:acme-dates:2.3.1" and lib.declared_in_target is True
    assert [u.outcome for u in lib.uses] == ["library"] * 3
    assert lib.uses[2].chain == ("DateFormatter.new()", "DateFormatter.format(int)")
    assert "not equivalence-checked" in result.summary() and "review.md" in result.summary()

    java = (out / "src/main/java/fixtures/libs/Billing.java").read_text()
    assert java.count("// Library acme.dates, not equivalence-checked: Decided by static") == 3
    assert "acme.dates (com.acme:acme-dates:2.3.1)" in java and "import com.acme.dates.Dates;" in java
    review = (out / "review.md").read_text()
    assert review.index("## Library mappings") < review.index("| Decision |")
    assert "run your tests against both the original program and the translation" in review
    assert "does not declare" not in review

    # The claimed programs are still translated and written: other callers and the tests may use them.
    assert (out / "src/main/java/fixtures/libs/common/dates/Dateadd.java").is_file()
    stdout = compile_and_run(out, CASE / "lib")
    assert stdout.splitlines() == ["DUE 20261215", "WEEKDAY 2", "DUE ON 15/12/2026"]


def test_library_missing_from_the_target_build_is_a_warning_not_a_failure(tmp_path):
    result, out = run(tmp_path, PICKS, "<project><modelVersion>4.0.0</modelVersion></project>")
    assert result.ok
    [lib] = result.libraries_used
    assert lib.declared_in_target is False and lib.target_note == "not a dependency in pom.xml"
    assert "warning: library acme.dates (com.acme:acme-dates:2.3.1): not a dependency in pom.xml" in result.summary()
    assert "Libraries the output uses that the target build does not declare" in (out / "review.md").read_text()


def test_pending_library_decisions_write_nothing(tmp_path):
    result, out = run(tmp_path, {}, None)
    assert result.status == "incomplete" and result.files_written == []
    assert [d.reason for d in result.pending_decisions] == ["library_call"] * 3
    assert all(d.library["step"] == "start" for d in result.pending_decisions)
    assert [u.outcome for u in result.libraries_used[0].uses] == ["pending"] * 3
    assert result.equivalence == "exact"
    assert "## Library mappings" in (out / "review.md").read_text()


IF_CALL = """       IDENTIFICATION DIVISION.
       PROGRAM-ID. BILLING.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01 WS-INVOICE-DATE PIC 9(8) VALUE 20261115.
       01 WS-TERMS-DAYS   PIC S9(4) VALUE 30.
       01 WS-DUE-DATE     PIC 9(8).
       PROCEDURE DIVISION.
           IF WS-TERMS-DAYS > 10
               CALL "DATEADD" USING WS-INVOICE-DATE WS-TERMS-DAYS
                                    WS-DUE-DATE
           END-IF.
           STOP RUN.
"""


DATEADD = """       IDENTIFICATION DIVISION.
       PROGRAM-ID. DATEADD.
       DATA DIVISION.
       LINKAGE SECTION.
       01 LK-DATE   PIC 9(8).
       01 LK-DAYS   PIC S9(4).
       01 LK-RESULT PIC 9(8).
       PROCEDURE DIVISION USING LK-DATE LK-DAYS LK-RESULT.
           MOVE LK-DATE TO LK-RESULT.
           GOBACK.
"""


@pytest.mark.parametrize("pick, equivalence", [("builtin:if", "exact"), ("builtin:if_jdk", "partial")])
def test_only_the_selected_alternatives_library_use_is_recorded(tmp_path, pick, equivalence):
    """An IF with a claimed CALL inside has a runtime and a JDK alternative, each previewed with its own
    use of the library; the discarded one must leave no (pending) use behind."""
    src = tmp_path / "src"
    (src / "common/dates").mkdir(parents=True)
    (src / "common/dates/DATEADD.cbl").write_text(DATEADD)
    (src / "BILLING.cbl").write_text(IF_CALL)
    m2 = tmp_path / "m2"
    install(m2)
    libs = libraries(caches={"maven": m2, "gradle": tmp_path / "gradle"})
    libs.add(Library(id="acme.dates", cobol=Cobol.path("common/dates/"), java=Java.maven("com.acme:acme-dates:2.3.1")))
    provider = StaticProvider([DecisionResponse(pick, decision_id="decision-1"),
                               DecisionResponse("cobol:translate", decision_id="decision-2")])
    result = transpile(src, output=tmp_path / "out", libraries=libs, provider=provider, options={"jdk_alternatives": True},
                       layout=Layout(base_package="fixtures.libs"), dialect=DIALECT)
    assert result.ok, result.summary()
    [lib] = result.libraries_used
    assert [(u.outcome, u.reason is None) for u in lib.uses] == [("translated", False)]
    assert result.equivalence == equivalence

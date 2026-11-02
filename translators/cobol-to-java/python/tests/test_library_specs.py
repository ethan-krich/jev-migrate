import json
from pathlib import Path

import pytest

from cobol_to_java import Cobol, Java, Library, libraries
from transpiler import LibraryError, transpile
from transpiler.api import TranspilerError

SCHEMA = Path(__file__).resolve().parents[4] / "protocol/library.schema.json"


def test_minimal_library_serialises_with_caches(tmp_path):
    libs = libraries(caches={"maven": tmp_path / "m2", "gradle": tmp_path / "gradle"})
    libs.add(Library(id="acme.dates", cobol=Cobol.path("common/dates/"), java=Java.maven("com.acme:acme-dates:2.3.1")))
    d = libs.to_dict()
    assert d == {"version": 1, "libraries": [{"id": "acme.dates", "source": [{"kind": "path", "pattern": "common/dates/"}],
                                              "target": {"kind": "maven", "coordinates": "com.acme:acme-dates:2.3.1"},
                                              "max_chain": 4}],
                 "caches": {"maven": str((tmp_path / "m2").resolve()), "gradle": str((tmp_path / "gradle").resolve())}}
    jsonschema = pytest.importorskip("jsonschema")
    jsonschema.Draft202012Validator(json.loads(SCHEMA.read_text())).validate(d)


def test_every_option_serialises(tmp_path):
    src = tmp_path / "DateUtils.java"
    src.write_text("public class DateUtils {}")
    lib = Library(
        id="acme.dates",
        cobol=[Cobol.program("DATEFMT", "DATEDIFF"), Cobol.operation("function  integer-of-date"),
               Cobol.copybook("daterec"), Cobol.paragraph("calc-due-date", program="billing")],
        java=Java.path(src), expose=["com.acme.dates.*"], hints={"DATEFMT": ["Dates.format(String,int)"]}, max_chain=2)
    libs = libraries()
    libs.add(lib)
    [d] = libs.to_dict()["libraries"]
    assert d["source"] == [{"kind": "program", "names": ["DATEFMT", "DATEDIFF"]},
                           {"kind": "operation", "names": ["FUNCTION INTEGER-OF-DATE"]},
                           {"kind": "copybook", "names": ["DATEREC"]},
                           {"kind": "paragraph", "names": ["CALC-DUE-DATE"], "program": "BILLING"}]
    assert d["target"] == {"kind": "path", "path": str(src.resolve())}
    assert d["expose"] == ["com.acme.dates.*"] and d["hints"] == {"DATEFMT": ["Dates.format(String,int)"]}
    assert d["max_chain"] == 2
    jsonschema = pytest.importorskip("jsonschema")
    jsonschema.Draft202012Validator(json.loads(SCHEMA.read_text())).validate(libs.to_dict())


def test_shape_problems_are_reported_together(tmp_path):
    with pytest.raises(LibraryError) as e:
        libraries().add(Library(id="bad", cobol=[Cobol.path("/abs"), Cobol.program()],
                                java=Java.maven("com.acme:acme-dates"), expose=["com.acme..x"],
                                hints={"X": "notalist"}, max_chain=0))
    problems = e.value.problems
    assert len(problems) == 6
    assert any("relative to the source root" in p for p in problems)
    assert any("'group:artifact:version'" in p for p in problems)
    with pytest.raises(LibraryError, match="does not exist"):
        libraries().add(Library(id="x", cobol=Cobol.program("A"), java=Java.path(tmp_path / "missing")))
    with pytest.raises(LibraryError, match="is not a file"):
        libraries().add(Library(id="x", cobol=Cobol.program("A"), java=Java.jar(tmp_path)))


def test_ids_and_claims_are_unique(tmp_path):
    libs = libraries()
    libs.add(Library(id="a", cobol=Cobol.program("DATEFMT"), java=Java.maven("g:a:1")))
    with pytest.raises(LibraryError, match="duplicate id"):
        libs.add(Library(id="a", cobol=Cobol.program("OTHER"), java=Java.maven("g:a:1")))
    with pytest.raises(LibraryError, match="program DATEFMT is already claimed by library 'a'"):
        libs.add(Library(id="b", cobol=Cobol.program("DATEFMT"), java=Java.maven("g:b:1")))
    assert len(libs) == 1 and "a" in libs
    libs.remove("a")
    libs.add(Library(id="b", cobol=Cobol.program("DATEFMT"), java=Java.maven("g:b:1")))


def test_transpile_checks_libraries_before_running(tmp_path):
    libs = libraries()
    src = tmp_path / "Lib.java"
    src.write_text("public class Lib {}")
    libs.add(Library(id="a", cobol=Cobol.program("X"), java=Java.path(src)))
    src.unlink()
    with pytest.raises(LibraryError, match="does not exist"):
        transpile(tmp_path, output=tmp_path / "out", libraries=libs, core="/nonexistent")
    with pytest.raises(TranspilerError, match="only used with libraries"):
        transpile(tmp_path, output=tmp_path / "out", target_project=tmp_path, core="/nonexistent")


def test_operation_names_are_normalised_but_call_literals_keep_their_case():
    assert Cobol.operation("function  upper-case", "display").names == ("FUNCTION UPPER-CASE", "DISPLAY")
    assert Cobol.operation("CALL 'CBL_toupper'", 'call "X"').names == ("CALL 'CBL_toupper'", "CALL 'X'")

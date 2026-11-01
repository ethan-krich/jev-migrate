import pytest

import cobol_to_java
from cobol_to_java import BUILTIN_HANDLERS, Layout, default_mappings, empty_mappings, language_pair
from cobol_to_java.mappings import CANDIDATE_HANDLERS
from transpiler import MappingError, find_language_pair


def test_defaults_cover_the_supported_subset():
    m = default_mappings()
    assert {r.handler for r in m if r.handler} == set(BUILTIN_HANDLERS) - CANDIDATE_HANDLERS
    assert CANDIDATE_HANDLERS <= set(BUILTIN_HANDLERS)
    assert m.sources() == sorted({"MOVE", "ADD", "SUBTRACT", "MULTIPLY", "DIVIDE", "COMPUTE", "DISPLAY", "IF",
                                  "PERFORM", "STOP", "GOBACK", "EXIT", "CONTINUE", "CALL", "CANCEL", "ENTRY", "SET", "EVALUATE",
                                  "ACCEPT", "EXHIBIT", "INITIALIZE", "SEARCH", "STRING", "UNSTRING",
                                  "INSPECT", "GO_TO", "ALTER", "OPEN", "CLOSE", "READ", "WRITE",
                                  "REWRITE", "DELETE", "START", "SORT", "MERGE", "RELEASE", "RETURN"})
    m.validate()


def test_each_call_returns_an_independent_registry():
    a, b = default_mappings(), default_mappings()
    a.remove("DISPLAY")
    a.replace("cobol.add", priority=1)
    assert b.for_source("DISPLAY") and b.get("cobol.add").priority == 100
    assert default_mappings().get("cobol.add").priority == 100


def test_defaults_reject_unknown_handlers():
    with pytest.raises(MappingError, match="unknown builtin handler 'move_string'"):
        default_mappings().add("MOVE", handler="move_string", when={"source_category": "alphanumeric"})


def test_users_can_mark_operations_unresolved():
    m = default_mappings()
    m.add("MOVE", strategy="unresolved", when={"target_category": "numeric"}, priority=200, id="review.numeric-moves")
    assert [r.id for r in m.for_source("MOVE")][-1] == "review.numeric-moves"
    assert len(empty_mappings()) == 0


def test_layout_serialisation():
    layout = Layout(base_package="com.acme", package_overrides={"reports/month-end": "com.acme.reports"})
    assert layout.to_dict()["base_package"] == "com.acme"
    assert layout.to_dict()["runtime_package"] is None
    assert layout.discovery()["copybook_dirs"] == ["copy", "copybooks"]


def test_language_pair_registration():
    pair = find_language_pair("java")
    assert pair == language_pair()
    assert (pair.translator_dir / "translator.json").is_file()
    assert pair.translator_dir == cobol_to_java.translator_dir()


def test_dialect_defaults_leave_site_settings_unset():
    from cobol_to_java import Dialect, default_dialect

    options = default_dialect().to_options()
    assert options == {
        "encoding": None, "collating_sequence": None, "out_of_range": None, "binary_truncate": True,
        "sync_alignment": None, "debugging_lines": False, "source_format": "fixed", "file_assignments": {},
        "when_compiled": None, "dynamic_calls": None, "move_overlap": None,
    }
    assert language_pair().default_dialect() == Dialect()


def test_dialect_is_validated_and_frozen():
    from cobol_to_java import Dialect

    d = Dialect(encoding="ascii", out_of_range="fail", file_assignments={"IN-FILE": "/data/in.txt"},
                when_compiled="2024-01-02T03:04:05")
    assert d.to_options()["file_assignments"] == {"IN-FILE": "/data/in.txt"}
    assert hash(d) == hash(Dialect(encoding="ascii", out_of_range="fail", when_compiled="2024-01-02T03:04:05"))
    with pytest.raises(TypeError):
        d.file_assignments["X"] = "y"  # type: ignore[index]
    with pytest.raises(ValueError, match="encoding must be one of"):
        Dialect(encoding="utf-8")
    with pytest.raises(ValueError, match="out_of_range"):
        Dialect(out_of_range="wrap")
    with pytest.raises(ValueError, match="when_compiled"):
        Dialect(when_compiled="yesterday")
    with pytest.raises(ValueError, match="source_format"):
        Dialect(source_format="punched")

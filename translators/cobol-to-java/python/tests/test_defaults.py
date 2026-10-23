import pytest

import cobol_to_java
from cobol_to_java import BUILTIN_HANDLERS, Layout, default_mappings, empty_mappings, language_pair
from transpiler import MappingError, find_language_pair


def test_defaults_cover_the_supported_subset():
    m = default_mappings()
    assert {r.handler for r in m} == set(BUILTIN_HANDLERS)
    assert m.sources() == sorted({"MOVE", "ADD", "SUBTRACT", "MULTIPLY", "DIVIDE", "COMPUTE", "DISPLAY", "IF",
                                  "PERFORM", "STOP", "GOBACK", "EXIT", "CONTINUE", "CALL"})
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

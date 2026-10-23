import json

import pytest

from transpiler import HandlerCatalog, Mapping, MappingError, MappingRegistry, Strategy

CATALOG = HandlerCatalog(
    handlers={"move_a": "MOVE", "move_b": "MOVE", "show": "DISPLAY"},
    facts={"MOVE": frozenset({"target_category"}), "DISPLAY": frozenset()},
    operations=frozenset({"MOVE", "DISPLAY", "CALL"}),
)


@pytest.fixture
def registry() -> MappingRegistry:
    r = MappingRegistry("x-to-y", CATALOG)
    r.add("MOVE", handler="move_a", when={"target_category": "alphanumeric"}, priority=100, id="move.a")
    r.add("DISPLAY", handler="show", priority=100, id="display")
    return r


def test_add_derives_stable_ids(registry):
    first = registry.add("call", strategy="unresolved")
    second = registry.add("CALL", strategy="unresolved")
    assert (first.id, second.id) == ("user.call.unresolved", "user.call.unresolved.2")
    assert first.source == "CALL"


def test_remove_source_removes_all_rules_for_it(registry):
    registry.add("MOVE", handler="move_b", priority=50, id="move.b")
    removed = registry.remove("move")
    assert [r.id for r in removed] == ["move.a", "move.b"]
    assert registry.for_source("MOVE") == []
    with pytest.raises(KeyError):
        registry.remove("MOVE")


def test_remove_rule_by_id_leaves_others(registry):
    registry.add("MOVE", handler="move_b", priority=50, id="move.b")
    registry.remove_rule("move.a")
    assert [r.id for r in registry.for_source("MOVE")] == ["move.b"]


def test_replace_overrides_in_place(registry):
    registry.add("CALL", strategy="unresolved", id="call")
    updated = registry.replace("move.a", priority=10, when={"target_category": ["alphanumeric", "numeric"]})
    assert updated.priority == 10
    assert updated.when["target_category"] == ("alphanumeric", "numeric")
    assert [r.id for r in registry] == ["move.a", "display", "call"]


def test_duplicate_id_rejected(registry):
    with pytest.raises(MappingError, match="already exists"):
        registry.add("DISPLAY", handler="show", id="display")


@pytest.mark.parametrize(
    "kwargs, message",
    [
        ({"source": "MOVE", "handler": "nope"}, "unknown builtin handler"),
        ({"source": "DISPLAY", "handler": "move_a"}, "implements MOVE"),
        ({"source": "MOVE"}, "requires handler"),
        ({"source": "MOVE", "strategy": "unresolved", "handler": "move_a"}, "only valid with the builtin"),
        ({"source": "MOVE", "handler": "move_a", "when": {"colour": "red"}}, "unknown condition 'colour'"),
        ({"source": "MOVE", "handler": "move_a", "when": {"target_category": {"nested": 1}}}, "scalar"),
        ({"source": "FROB", "strategy": "unresolved"}, "unknown source operation"),
        ({"source": "CALL", "strategy": "callback"}, "requires script"),
        ({"source": "CALL", "strategy": "unresolved", "script": "a.py", "function": "f"}, "only valid with the callback"),
    ],
)
def test_invalid_rules_are_rejected(registry, kwargs, message):
    with pytest.raises(MappingError, match=message):
        registry.add(**kwargs)
    assert len(registry) == 2


def test_invalid_replace_keeps_original(registry):
    with pytest.raises(MappingError):
        registry.replace("move.a", handler="show")
    assert registry.get("move.a").handler == "move_a"


def test_serialisation_round_trip(registry):
    registry.add("CALL", strategy="callback", script="./cb.py", function="resolve_call", id="call.cb")
    data = json.loads(registry.to_json())
    assert data["version"] == 1
    assert data["rules"][2] == {
        "id": "call.cb", "source": "CALL", "strategy": "callback", "priority": 0,
        "callback": {"script": "./cb.py", "function": "resolve_call"},
    }
    again = MappingRegistry.from_dict("x-to-y", data, CATALOG)
    assert list(again) == list(registry)


def test_only_plain_data_is_serialised(registry):
    text = registry.to_json()
    assert "function at" not in text and "object at" not in text


def test_copy_is_independent(registry):
    clone = registry.copy()
    clone.remove("DISPLAY")
    assert "display" in registry and "display" not in clone


def test_mappings_are_immutable(registry):
    rule = registry.get("move.a")
    with pytest.raises(AttributeError):
        rule.priority = 5  # type: ignore[misc]
    with pytest.raises(TypeError):
        rule.when["target_category"] = "numeric"  # type: ignore[index]
    assert isinstance(rule, Mapping) and rule.strategy is Strategy.BUILTIN


def test_registry_without_catalog_defers_validation_to_worker():
    r = MappingRegistry("x-to-y")
    r.add("ANYTHING", handler="whatever")
    assert r.to_dict()["rules"][0]["handler"] == "whatever"

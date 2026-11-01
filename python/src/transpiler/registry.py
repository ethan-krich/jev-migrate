"""The mutable mapping registry.

A registry is an ordered list of :class:`Mapping` rules. Each rule says how one source
operation (a COBOL verb such as ``MOVE``) is handled:

* ``builtin``    - apply a deterministic transformation implemented by the translator worker,
                   named by a stable handler id;
* ``unresolved`` - report the operation as a structured decision instead of translating it;
* ``callback``   - reserved: defer to a function in a user Python script (phase 2);
* ``library``    - the operation uses a part a library claims (``transpile(libraries=...)``): a
                   ``library_call`` decision offering calls into the library and the exact translation.

Rules carry optional ``when`` conditions over facts the worker computes for each operation
instance, and a ``priority``. The worker picks, among matching rules, the highest priority,
then the most specific (most conditions), then the earliest registered; if the best rules tie
on priority and specificity but differ in effect, the operation becomes an
``ambiguous_mapping`` decision instead of an arbitrary choice.

Registries serialise to plain JSON (``protocol/mapping.schema.json``); no Python callables or
AST objects ever cross the process boundary.
"""

from __future__ import annotations

import json
from collections.abc import Iterable, Iterator, Mapping as MappingABC
from dataclasses import dataclass, field, replace as dc_replace
from enum import StrEnum
from types import MappingProxyType
from typing import Any

from .callbacks import CallbackRef

FORMAT_VERSION = 1

Scalar = str | int | float | bool | None
Condition = Scalar | tuple[Scalar, ...]


class Strategy(StrEnum):
    BUILTIN = "builtin"
    UNRESOLVED = "unresolved"
    CALLBACK = "callback"
    LIBRARY = "library"


class MappingError(ValueError):
    """A mapping registry failed validation. ``problems`` lists every issue found."""

    def __init__(self, problems: list[str]):
        self.problems = problems
        super().__init__("; ".join(problems))


@dataclass(frozen=True)
class Mapping:
    """One mapping rule. Immutable: use :meth:`MappingRegistry.replace` to change a rule."""

    id: str
    source: str
    strategy: Strategy
    handler: str | None = None
    when: MappingABC[str, Condition] = field(default_factory=lambda: MappingProxyType({}))
    priority: int = 0
    callback: CallbackRef | None = None
    description: str | None = None

    def to_dict(self) -> dict[str, Any]:
        d: dict[str, Any] = {"id": self.id, "source": self.source, "strategy": self.strategy.value}
        if self.handler is not None:
            d["handler"] = self.handler
        if self.when:
            d["when"] = {k: list(v) if isinstance(v, tuple) else v for k, v in self.when.items()}
        d["priority"] = self.priority
        if self.callback is not None:
            d["callback"] = self.callback.to_dict()
        if self.description is not None:
            d["description"] = self.description
        return d

    @classmethod
    def from_dict(cls, d: MappingABC[str, Any]) -> Mapping:
        cb = d.get("callback")
        return cls(
            id=d["id"],
            source=str(d["source"]).upper(),
            strategy=Strategy(d["strategy"]),
            handler=d.get("handler"),
            when=_freeze_when(d.get("when") or {}),
            priority=int(d.get("priority", 0)),
            callback=CallbackRef(script=cb["script"], function=cb["function"]) if cb else None,
            description=d.get("description"),
        )


def _freeze_when(when: MappingABC[str, Any]) -> MappingABC[str, Condition]:
    frozen: dict[str, Condition] = {}
    for key, value in when.items():
        frozen[key] = tuple(value) if isinstance(value, (list, tuple, set, frozenset)) else value
    return MappingProxyType(frozen)


@dataclass(frozen=True)
class HandlerCatalog:
    """What a language pair's worker implements, for validation before a job is started.

    ``handlers`` maps builtin handler id to the operation it implements; ``facts`` maps an
    operation to the condition keys usable in ``when``. The worker validates again and is the
    authority; this catalog only makes mistakes visible earlier.
    """

    handlers: MappingABC[str, str]
    facts: MappingABC[str, frozenset[str]]
    operations: frozenset[str]


class MappingRegistry:
    """An ordered, mutable collection of mapping rules for one language pair."""

    def __init__(self, language_pair: str, catalog: HandlerCatalog | None = None, rules: Iterable[Mapping] = ()):
        self.language_pair = language_pair
        self.catalog = catalog
        self._rules: list[Mapping] = []
        for rule in rules:
            self._insert(rule)

    # -- inspection ---------------------------------------------------------------------

    def __iter__(self) -> Iterator[Mapping]:
        return iter(list(self._rules))

    def __len__(self) -> int:
        return len(self._rules)

    def __contains__(self, rule_id: object) -> bool:
        return any(r.id == rule_id for r in self._rules)

    def __repr__(self) -> str:
        return f"MappingRegistry({self.language_pair!r}, {len(self)} rules)"

    def get(self, rule_id: str) -> Mapping:
        for rule in self._rules:
            if rule.id == rule_id:
                return rule
        raise KeyError(f"no mapping rule with id {rule_id!r}")

    def for_source(self, source: str) -> list[Mapping]:
        """Rules for one operation, in registry order."""
        source = source.upper()
        return [r for r in self._rules if r.source == source]

    def sources(self) -> list[str]:
        return sorted({r.source for r in self._rules})

    # -- mutation -----------------------------------------------------------------------

    def add(
        self,
        source: str,
        strategy: Strategy | str = Strategy.BUILTIN,
        *,
        handler: str | None = None,
        when: MappingABC[str, Any] | None = None,
        priority: int = 0,
        id: str | None = None,
        script: str | None = None,
        function: str | None = None,
        description: str | None = None,
    ) -> Mapping:
        """Appends a rule and returns it. Rejects invalid rules immediately.

        Without an explicit ``id`` a stable one is derived from the rule's content, e.g.
        ``user.move.move_to_alphanumeric``.
        """
        source = source.upper()
        strategy = Strategy(strategy)
        callback = None
        if script is not None or function is not None:
            if script is None or function is None:
                raise MappingError([f"{source}: callback mappings need both script= and function="])
            callback = CallbackRef(script=str(script), function=function)
        rule = Mapping(
            id=id or self._derive_id(source, handler or strategy.value),
            source=source,
            strategy=strategy,
            handler=handler,
            when=_freeze_when(when or {}),
            priority=priority,
            callback=callback,
            description=description,
        )
        self._insert(rule)
        return rule

    def remove(self, source: str) -> list[Mapping]:
        """Removes every rule for an operation (e.g. ``remove("DISPLAY")``) and returns them."""
        source = source.upper()
        removed = [r for r in self._rules if r.source == source]
        if not removed:
            raise KeyError(f"no mapping rules for {source}")
        self._rules = [r for r in self._rules if r.source != source]
        return removed

    def remove_rule(self, rule_id: str) -> Mapping:
        """Removes exactly one rule by id, leaving other rules for the operation intact."""
        rule = self.get(rule_id)
        self._rules.remove(rule)
        return rule

    def replace(self, rule_id: str, **changes: Any) -> Mapping:
        """Overrides fields of an existing rule in place (its position is kept)."""
        index = next((i for i, r in enumerate(self._rules) if r.id == rule_id), None)
        if index is None:
            raise KeyError(f"no mapping rule with id {rule_id!r}")
        if "when" in changes:
            changes["when"] = _freeze_when(changes["when"] or {})
        if "strategy" in changes:
            changes["strategy"] = Strategy(changes["strategy"])
        if "source" in changes:
            changes["source"] = str(changes["source"]).upper()
        if "script" in changes or "function" in changes:
            old = self._rules[index].callback
            changes["callback"] = CallbackRef(
                script=str(changes.pop("script", old.script if old else None)),
                function=changes.pop("function", old.function if old else None),
            )
        updated = dc_replace(self._rules[index], **changes)
        problems = self._problems(updated)
        if problems:
            raise MappingError(problems)
        self._rules[index] = updated
        return updated

    def copy(self) -> MappingRegistry:
        # Rules are immutable, so sharing them between registries is safe.
        return MappingRegistry(self.language_pair, self.catalog, self._rules)

    # -- validation and serialisation ---------------------------------------------------

    def validate(self) -> None:
        problems: list[str] = []
        seen: set[str] = set()
        for rule in self._rules:
            if rule.id in seen:
                problems.append(f"duplicate rule id {rule.id!r}")
            seen.add(rule.id)
            problems.extend(self._problems(rule))
        if problems:
            raise MappingError(problems)

    def to_dict(self) -> dict[str, Any]:
        self.validate()
        return {"version": FORMAT_VERSION, "rules": [r.to_dict() for r in self._rules]}

    def to_json(self, **kwargs: Any) -> str:
        return json.dumps(self.to_dict(), **kwargs)

    @classmethod
    def from_dict(cls, language_pair: str, data: MappingABC[str, Any], catalog: HandlerCatalog | None = None) -> MappingRegistry:
        if data.get("version") != FORMAT_VERSION:
            raise MappingError([f"unsupported mapping format version {data.get('version')!r}"])
        return cls(language_pair, catalog, (Mapping.from_dict(r) for r in data.get("rules", [])))

    # -- internals ----------------------------------------------------------------------

    def _insert(self, rule: Mapping) -> None:
        problems = self._problems(rule)
        if rule.id in self:
            problems.append(f"rule id {rule.id!r} already exists; use replace() or remove_rule() first")
        if problems:
            raise MappingError(problems)
        self._rules.append(rule)

    def _derive_id(self, source: str, suffix: str) -> str:
        base = f"user.{source.lower()}.{suffix}"
        candidate, n = base, 2
        while candidate in self:
            candidate, n = f"{base}.{n}", n + 1
        return candidate

    def _problems(self, rule: Mapping) -> list[str]:
        where = f"rule {rule.id!r}"
        problems: list[str] = []
        if not rule.id:
            problems.append("rule id must not be empty")
        if not isinstance(rule.priority, int) or isinstance(rule.priority, bool):
            problems.append(f"{where}: priority must be an integer")
        if rule.strategy is Strategy.BUILTIN:
            if not rule.handler:
                problems.append(f"{where}: builtin strategy requires handler=")
        elif rule.handler is not None:
            problems.append(f"{where}: handler= is only valid with the builtin strategy")
        if rule.strategy is Strategy.CALLBACK and rule.callback is None:
            problems.append(f"{where}: callback strategy requires script= and function=")
        if rule.strategy is not Strategy.CALLBACK and rule.callback is not None:
            problems.append(f"{where}: script=/function= are only valid with the callback strategy")
        for key, value in rule.when.items():
            values = value if isinstance(value, tuple) else (value,)
            if not values or not all(v is None or isinstance(v, (str, int, float, bool)) for v in values):
                problems.append(f"{where}: condition {key!r} must be a scalar or a list of scalars")
        if self.catalog is not None:
            if rule.source not in self.catalog.operations:
                problems.append(f"{where}: unknown source operation {rule.source!r}")
            if rule.handler is not None:
                op = self.catalog.handlers.get(rule.handler)
                if op is None:
                    known = ", ".join(sorted(self.catalog.handlers))
                    problems.append(f"{where}: unknown builtin handler {rule.handler!r} (known: {known})")
                elif op != rule.source:
                    problems.append(f"{where}: handler {rule.handler!r} implements {op}, not {rule.source}")
            known_facts = self.catalog.facts.get(rule.source, frozenset())
            for key in rule.when:
                if key not in known_facts:
                    problems.append(
                        f"{where}: unknown condition {key!r} for {rule.source} (known: {sorted(known_facts)})"
                    )
        return problems

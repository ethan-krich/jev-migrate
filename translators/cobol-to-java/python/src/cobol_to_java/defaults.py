"""Factories for the COBOL-to-Java defaults. Every call returns a new, independent object."""

from __future__ import annotations

from pathlib import Path

from transpiler import HandlerCatalog, MappingRegistry, Strategy

from .layout import Layout
from .mappings import BUILTIN_HANDLERS, DEFAULT_IMPORTANCE, DEFAULT_RULES, FACTS, OPERATIONS, PRIORITY

LANGUAGE_PAIR = "cobol-to-java"

CATALOG = HandlerCatalog(handlers=BUILTIN_HANDLERS, facts=FACTS, operations=OPERATIONS)


def default_mappings() -> MappingRegistry:
    """A fresh, mutable registry with the default deterministic mappings."""
    registry = MappingRegistry(LANGUAGE_PAIR, CATALOG)
    for rule_id, source, handler, when, description in DEFAULT_RULES:
        registry.add(source, Strategy.BUILTIN, handler=handler, when=when, priority=PRIORITY, id=rule_id,
                     description=description)
    return registry


def empty_mappings() -> MappingRegistry:
    """A registry with no rules (every operation becomes a missing_mapping decision)."""
    return MappingRegistry(LANGUAGE_PAIR, CATALOG)


def default_importance() -> dict[tuple[str, str], str]:
    """A fresh, mutable copy of DEFAULT_IMPORTANCE: ``{(reason, operation): tier}``."""
    return dict(DEFAULT_IMPORTANCE)


def default_layout() -> Layout:
    return Layout()


def translator_dir() -> Path:
    """translators/cobol-to-java/ (holds translator.json and the built Java worker)."""
    return Path(__file__).resolve().parents[3]

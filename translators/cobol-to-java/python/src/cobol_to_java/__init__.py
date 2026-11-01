"""COBOL-to-Java language pair for the transpiler.

    from cobol_to_java import default_mappings, Dialect, Layout
    from cobol_to_java import Cobol, Java, Library, libraries
"""

from transpiler import LanguagePair

from .defaults import (
    CATALOG,
    LANGUAGE_PAIR,
    default_importance,
    default_layout,
    default_mappings,
    empty_mappings,
    translator_dir,
)
from .dialect import Dialect, default_dialect
from .layout import Layout
from .libraries import Cobol, CobolSelector, Java, JavaSource, Library, libraries
from .mappings import BUILTIN_HANDLERS, DEFAULT_IMPORTANCE, FACTS


def language_pair() -> LanguagePair:
    """Entry point for ``transpiler.language_pairs``."""
    return LanguagePair(
        name=LANGUAGE_PAIR,
        source_language="cobol",
        target_language="java",
        translator_dir=translator_dir(),
        default_mappings=default_mappings,
        default_layout=default_layout,
        catalog=CATALOG,
        default_dialect=default_dialect,
        default_importance=default_importance,
    )


__all__ = [
    "BUILTIN_HANDLERS",
    "CATALOG",
    "Cobol",
    "CobolSelector",
    "DEFAULT_IMPORTANCE",
    "Dialect",
    "FACTS",
    "Java",
    "JavaSource",
    "LANGUAGE_PAIR",
    "Layout",
    "Library",
    "default_dialect",
    "default_importance",
    "default_layout",
    "default_mappings",
    "empty_mappings",
    "language_pair",
    "libraries",
    "translator_dir",
]

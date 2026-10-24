"""COBOL-to-Java language pair for the transpiler.

    from cobol_to_java import default_mappings, Dialect, Layout
"""

from transpiler import LanguagePair

from .defaults import CATALOG, LANGUAGE_PAIR, default_layout, default_mappings, empty_mappings, translator_dir
from .dialect import Dialect, default_dialect
from .layout import Layout
from .mappings import BUILTIN_HANDLERS, FACTS


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
    )


__all__ = [
    "BUILTIN_HANDLERS",
    "CATALOG",
    "Dialect",
    "FACTS",
    "LANGUAGE_PAIR",
    "Layout",
    "default_dialect",
    "default_layout",
    "default_mappings",
    "empty_mappings",
    "language_pair",
    "translator_dir",
]

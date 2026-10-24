"""Hybrid deterministic/probabilistic code translation - public Python API.

    from transpiler import transpile
    from cobol_to_java import default_mappings

    mappings = default_mappings()
    mappings.remove("DISPLAY")
    result = transpile("./legacy", target="java", output="./generated", mappings=mappings)
    print(result.summary())
"""

from .api import (
    LanguagePair,
    ProgramResult,
    TranspileResult,
    TranspilerError,
    find_language_pair,
    language_pairs,
    transpile,
)
from .callbacks import CallbackRef, CallbackRequest, CallbackResponse
from .protocol import Candidate, DecisionRequest, DecisionResponse, Reason, SourceLocation
from .providers import DecisionProvider, DeterministicProvider, StaticProvider
from .registry import HandlerCatalog, Mapping, MappingError, MappingRegistry, Strategy

__all__ = [
    "CallbackRef",
    "CallbackRequest",
    "CallbackResponse",
    "Candidate",
    "DecisionProvider",
    "DecisionRequest",
    "DecisionResponse",
    "DeterministicProvider",
    "HandlerCatalog",
    "LanguagePair",
    "Mapping",
    "MappingError",
    "MappingRegistry",
    "ProgramResult",
    "Reason",
    "SourceLocation",
    "StaticProvider",
    "Strategy",
    "TranspileResult",
    "TranspilerError",
    "find_language_pair",
    "language_pairs",
    "transpile",
]

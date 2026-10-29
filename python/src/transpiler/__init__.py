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
    Resolution,
    Resolutions,
    ReviewItem,
    TranspileResult,
    TranspilerError,
    find_language_pair,
    language_pairs,
    transpile,
)
from .callbacks import CallbackRef, CallbackRequest, CallbackResponse
from .protocol import Candidate, DecisionRequest, DecisionResponse, Reason, ReviewPriority, SourceLocation
from .providers import ContextBudget, DecisionProvider, DeterministicProvider, Gate, JevProvider, StaticProvider
from .registry import HandlerCatalog, Mapping, MappingError, MappingRegistry, Strategy

__all__ = [
    "CallbackRef",
    "CallbackRequest",
    "CallbackResponse",
    "Candidate",
    "ContextBudget",
    "DecisionProvider",
    "DecisionRequest",
    "DecisionResponse",
    "DeterministicProvider",
    "Gate",
    "HandlerCatalog",
    "JevProvider",
    "LanguagePair",
    "Mapping",
    "MappingError",
    "MappingRegistry",
    "ProgramResult",
    "Reason",
    "Resolution",
    "Resolutions",
    "ReviewItem",
    "ReviewPriority",
    "SourceLocation",
    "StaticProvider",
    "Strategy",
    "TranspileResult",
    "TranspilerError",
    "find_language_pair",
    "language_pairs",
    "transpile",
]

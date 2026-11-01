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
    SettingChoice,
    TranspileResult,
    TranspilerError,
    find_language_pair,
    language_pairs,
    transpile,
)
from .callbacks import CallbackRef, CallbackRequest, CallbackResponse
from .libraries import LibraryError, LibraryRegistry
from .protocol import Candidate, DecisionRequest, DecisionResponse, Reason, ReviewPriority, SourceLocation
from .providers import (
    OPENROUTER_BASE_URL,
    ContextBudget,
    DecisionProvider,
    DeterministicProvider,
    Gate,
    JevProvider,
    StaticProvider,
)
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
    "LibraryError",
    "LibraryRegistry",
    "Mapping",
    "MappingError",
    "MappingRegistry",
    "OPENROUTER_BASE_URL",
    "ProgramResult",
    "Reason",
    "Resolution",
    "Resolutions",
    "ReviewItem",
    "ReviewPriority",
    "SettingChoice",
    "SourceLocation",
    "StaticProvider",
    "Strategy",
    "TranspileResult",
    "TranspilerError",
    "find_language_pair",
    "language_pairs",
    "transpile",
]

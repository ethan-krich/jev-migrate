"""Decision providers choose among the validated candidates of unresolved operations.

Phase 1 providers are executed by the C++ orchestrator from their declarative configuration
(:meth:`DecisionProvider.to_config`). A phase 2 provider (Jev) will run as a persistent Python
subprocess that the orchestrator sends batches of decision requests; it will implement
:meth:`decide` with the same request/response types, and the orchestrator will keep validating
every answer before the worker applies it.
"""

from __future__ import annotations

from abc import ABC, abstractmethod
from collections.abc import Sequence
from typing import Any

from ..protocol import DecisionRequest, DecisionResponse


class DecisionProvider(ABC):
    name: str

    @abstractmethod
    def to_config(self) -> dict[str, Any]:
        """The provider configuration placed in the orchestrator job."""

    def decide(self, batch: Sequence[DecisionRequest]) -> list[DecisionResponse]:
        """Answers a batch of independent decisions (subset allowed). Out-of-process providers only."""
        raise NotImplementedError(f"{type(self).__name__} is executed by the orchestrator")

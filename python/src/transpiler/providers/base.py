"""Decision providers choose among the validated candidates of unresolved operations.

Deterministic providers are executed by the C++ orchestrator from their declarative
configuration (:meth:`DecisionProvider.to_config`). An out-of-process provider (``kind:
"python"``, e.g. :class:`~transpiler.providers.jev.JevProvider`) runs as a persistent Python
subprocess (:mod:`transpiler.providers.serve`) that the orchestrator sends batches of decision
requests; it implements :meth:`decide`, and the orchestrator still gates and validates every
answer before the worker applies it.
"""

from __future__ import annotations

from abc import ABC, abstractmethod
from collections.abc import Mapping, Sequence
from typing import Any

from ..protocol import DecisionRequest, DecisionResponse, ExtraContext


class DecisionProvider(ABC):
    name: str

    @abstractmethod
    def to_config(self) -> dict[str, Any]:
        """The provider configuration placed in the orchestrator job."""

    def decide(
        self, batch: Sequence[DecisionRequest], extra_context: Mapping[str, ExtraContext] | None = None
    ) -> list[DecisionResponse]:
        """Answers a batch of independent decisions (subset allowed). Out-of-process providers only.

        ``extra_context`` holds, by decision id, the context nodes the worker returned after this
        provider answered ``need_context`` for that decision.
        """
        raise NotImplementedError(f"{type(self).__name__} is executed by the orchestrator")

    def context_budget(self) -> dict[str, int] | None:
        """The worker's ``initialize.context`` budget ``{max_depth, max_chars}``; None for its default."""
        return None

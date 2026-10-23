from __future__ import annotations

from collections.abc import Iterable, Sequence
from typing import Any

from ..protocol import DecisionRequest, DecisionResponse
from .base import DecisionProvider


class DeterministicProvider(DecisionProvider):
    """Makes no decisions: unresolved operations are reported, and the translation is incomplete."""

    name = "deterministic"

    def to_config(self) -> dict[str, Any]:
        return {"kind": "deterministic"}

    def decide(self, batch: Sequence[DecisionRequest]) -> list[DecisionResponse]:
        return []


class StaticProvider(DecisionProvider):
    """Applies fixed, pre-approved selections (synthetic decisions, replayed reviews).

    Answers are matched by decision id or by fingerprint; the orchestrator validates each one
    against the worker's candidates and rejects anything not offered.
    """

    name = "static"

    def __init__(self, decisions: Iterable[DecisionResponse]):
        self.decisions = list(decisions)

    def to_config(self) -> dict[str, Any]:
        return {"kind": "static", "decisions": [d.to_json() for d in self.decisions]}

    def decide(self, batch: Sequence[DecisionRequest]) -> list[DecisionResponse]:
        out = []
        for request in batch:
            for d in self.decisions:
                if d.decision_id == request.id or (not d.decision_id and d.fingerprint == request.fingerprint):
                    out.append(DecisionResponse(d.candidate_id, request.id, request.fingerprint, d.rationale))
                    break
        return out

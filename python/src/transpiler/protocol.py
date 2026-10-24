"""Python views of the shared JSON protocol (see the schemas in ``protocol/``).

These mirror what the C++ orchestrator and translator workers exchange, so results can be
inspected and synthetic decisions constructed without touching raw dictionaries.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from enum import StrEnum
from typing import Any

WORKER_PROTOCOL = "transpiler-worker/1"
JOB_VERSION = 1
REPORT_VERSION = 1


class Reason(StrEnum):
    """Why an operation was not translated deterministically."""

    MISSING_MAPPING = "missing_mapping"
    UNSUPPORTED_CONSTRUCT = "unsupported_construct"
    AMBIGUOUS_MAPPING = "ambiguous_mapping"
    MISSING_TARGET_API = "missing_target_api"
    INVALID_MAPPING = "invalid_mapping"
    CALLBACK_REQUIRED = "callback_required"


@dataclass(frozen=True)
class SourceLocation:
    file: str
    line: int
    column: int
    text: str


@dataclass(frozen=True)
class Candidate:
    id: str
    description: str
    handler: str | None = None
    preview: str | None = None


@dataclass(frozen=True)
class DecisionRequest:
    id: str
    fingerprint: str
    reason: Reason
    operation: str
    message: str
    source: SourceLocation
    state: str
    candidates: tuple[Candidate, ...]
    context: dict[str, Any] = field(default_factory=dict)
    callback: dict[str, str] | None = None

    @property
    def resolved(self) -> bool:
        return self.state == "resolved"

    @classmethod
    def from_json(cls, d: dict[str, Any]) -> DecisionRequest:
        s = d.get("source", {})
        return cls(
            id=d["id"],
            fingerprint=d["fingerprint"],
            reason=Reason(d["reason"]),
            operation=d["operation"],
            message=d.get("message", ""),
            source=SourceLocation(s.get("file", ""), s.get("line", 0), s.get("column", 0), s.get("text", "")),
            state=d.get("state", "pending"),
            candidates=tuple(
                Candidate(c["id"], c.get("description", ""), c.get("handler"), c.get("preview"))
                for c in d.get("candidates", [])
            ),
            context=d.get("context", {}),
            callback=d.get("callback"),
        )


@dataclass(frozen=True)
class DecisionResponse:
    """A selection among a decision's candidates. Identify the decision by id or fingerprint."""

    candidate_id: str
    decision_id: str | None = None
    fingerprint: str | None = None
    rationale: str = ""

    def __post_init__(self) -> None:
        if not self.decision_id and not self.fingerprint:
            raise ValueError("DecisionResponse needs decision_id or fingerprint")

    def to_json(self) -> dict[str, Any]:
        d: dict[str, Any] = {"selection": {"kind": "candidate", "candidate_id": self.candidate_id}}
        if self.decision_id:
            d["decision_id"] = self.decision_id
        if self.fingerprint:
            d["fingerprint"] = self.fingerprint
        if self.rationale:
            d["rationale"] = self.rationale
        return d

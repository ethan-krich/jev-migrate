"""Python views of the shared JSON protocol (see the schemas in ``protocol/``).

These mirror what the C++ orchestrator and translator workers exchange, so results can be
inspected and synthetic decisions constructed without touching raw dictionaries.
"""

from __future__ import annotations

from collections.abc import Mapping
from dataclasses import dataclass, field
from enum import StrEnum
from typing import Any

WORKER_PROTOCOL = "transpiler-worker/1"
PROVIDER_PROTOCOL = "transpiler-provider/1"
JOB_VERSION = 1
REPORT_VERSION = 1

# Importance tiers of a decision, lowest first (the worker assigns one from an importance table).
IMPORTANCE_TIERS = ("low", "normal", "high", "critical")

# Gate outcomes of a provider's answer; only "passed" answers are applied.
GATES = ("passed", "below_floor", "abstained", "need_context")

# Advisory review priority levels (index = level), each with what it means for a reviewer.
REVIEW_PRIORITY = (
    ("cosmetic", "only the look of the code or output layout changes"),
    ("local", "one statement or paragraph behaves differently; nothing outside it notices"),
    ("visible", "users or downstream programs see different output or behaviour"),
    ("business", "a business rule, decision or record that people rely on changes"),
    ("money/compliance", "amounts, balances, audit trails or regulatory results change"),
)


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
class Slot:
    """A follow-up choice of a candidate: applying the candidate raises one decision per slot."""

    name: str
    description: str


@dataclass(frozen=True)
class Candidate:
    id: str
    description: str
    handler: str | None = None
    preview: str | None = None
    slots: tuple[Slot, ...] = ()
    # The Java methods the preview calls: {signature, summary} (first Javadoc paragraph). Advisory.
    docs: tuple[dict[str, str], ...] = ()


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
    importance: str = "critical"  # what the worker assigns when no table matches
    # A setting decision ({field, values: {candidate_id: value}}): choosing a candidate means
    # translating again with that setting, which the orchestrator does.
    setting: dict[str, Any] | None = None

    @property
    def refs(self) -> list[dict[str, Any]]:
        """The context graph nodes (``context.refs``) the worker attached."""
        return list(self.context.get("refs", []))

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
                Candidate(c["id"], c.get("description", ""), c.get("handler"), c.get("preview"),
                          tuple(Slot(s["name"], s.get("description", "")) for s in c.get("slots", [])),
                          tuple(c.get("docs", [])))
                for c in d.get("candidates", [])
            ),
            context=d.get("context", {}),
            callback=d.get("callback"),
            importance=d.get("importance", "critical"),
            setting=d.get("setting"),
        )


@dataclass(frozen=True)
class ReviewPriority:
    """Advisory: how carefully a person should review an answer. Never affects the gate."""

    level: int
    label: str
    confidence: float | None = None

    def __post_init__(self) -> None:
        if not 0 <= self.level < len(REVIEW_PRIORITY) or REVIEW_PRIORITY[self.level][0] != self.label:
            raise ValueError(f"review priority level {self.level} is not {self.label!r}")

    def to_json(self) -> dict[str, Any]:
        d: dict[str, Any] = {"level": self.level, "label": self.label}
        if self.confidence is not None:
            d["confidence"] = self.confidence
        return d

    @classmethod
    def from_json(cls, d: dict[str, Any]) -> ReviewPriority:
        return cls(d["level"], d["label"], d.get("confidence"))


@dataclass(frozen=True)
class DecisionResponse:
    """A selection among a decision's candidates. Identify the decision by id or fingerprint.

    A gated provider also reports ``gate`` (only ``passed`` answers are applied), its
    ``confidence`` and ``probabilities``, and an advisory ``review_priority``. A
    ``need_context`` answer selects nothing and names the truncated context refs to open.
    """

    candidate_id: str | None
    decision_id: str | None = None
    fingerprint: str | None = None
    rationale: str = ""
    gate: str | None = None
    confidence: float | None = None
    probabilities: Mapping[str, float] | None = None
    need_context: tuple[str, ...] = ()
    review_priority: ReviewPriority | None = None

    def __post_init__(self) -> None:
        if not self.decision_id and not self.fingerprint:
            raise ValueError("DecisionResponse needs decision_id or fingerprint")
        if self.gate is not None and self.gate not in GATES:
            raise ValueError(f"gate must be one of {GATES}, not {self.gate!r}")
        if self.gate == "need_context":
            if not self.need_context or self.candidate_id:
                raise ValueError("a need_context answer names refs to expand and selects no candidate")
        elif self.need_context:
            raise ValueError("need_context refs are only allowed with gate 'need_context'")
        elif not self.candidate_id and self.gate in (None, "passed"):
            raise ValueError("a passed answer must select a candidate")
        if self.confidence is not None and not 0 <= self.confidence <= 1:
            raise ValueError(f"confidence must be in [0, 1], not {self.confidence}")

    @property
    def passed(self) -> bool:
        return self.gate in (None, "passed")

    def to_json(self) -> dict[str, Any]:
        d: dict[str, Any] = {}
        if self.candidate_id:
            d["selection"] = {"kind": "candidate", "candidate_id": self.candidate_id}
        if self.decision_id:
            d["decision_id"] = self.decision_id
        if self.fingerprint:
            d["fingerprint"] = self.fingerprint
        if self.rationale:
            d["rationale"] = self.rationale
        if self.gate is not None:
            d["gate"] = self.gate
        if self.confidence is not None:
            d["confidence"] = self.confidence
        if self.probabilities is not None:
            d["probabilities"] = dict(self.probabilities)
        if self.need_context:
            d["need_context"] = list(self.need_context)
        if self.review_priority is not None:
            d["review_priority"] = self.review_priority.to_json()
        return d

    @classmethod
    def from_json(cls, d: dict[str, Any]) -> DecisionResponse:
        rp = d.get("review_priority")
        return cls(
            candidate_id=d.get("selection", {}).get("candidate_id"),
            decision_id=d.get("decision_id"),
            fingerprint=d.get("fingerprint"),
            rationale=d.get("rationale", ""),
            gate=d.get("gate"),
            confidence=d.get("confidence"),
            probabilities=d.get("probabilities"),
            need_context=tuple(d.get("need_context", ())),
            review_priority=ReviewPriority.from_json(rp) if rp else None,
        )


@dataclass(frozen=True)
class ExtraContext:
    """Context nodes the worker returned for a decision asked again (``decide.extra_context``)."""

    decision_id: str
    expansions: int
    expansions_left: int
    refs: tuple[dict[str, Any], ...]

    @classmethod
    def from_json(cls, d: dict[str, Any]) -> ExtraContext:
        return cls(d["decision_id"], d["expansions"], d.get("expansions_left", 0), tuple(d.get("refs", ())))

def importance_table(table: Mapping[tuple[str, str], str]) -> list[dict[str, str]]:
    """``{(reason, operation): tier}`` as ``initialize.importance``; ``*`` matches anything."""
    out = []
    for key, tier in table.items():
        if not (isinstance(key, tuple) and len(key) == 2 and all(isinstance(k, str) and k for k in key)):
            raise ValueError(f"importance key must be (reason, operation), not {key!r}")
        if key[0] != "*" and key[0] not in {r.value for r in Reason}:
            raise ValueError(f"unknown decision reason {key[0]!r} in importance table")
        if tier not in IMPORTANCE_TIERS:
            raise ValueError(f"importance of {key} must be one of {IMPORTANCE_TIERS}, not {tier!r}")
        out.append({"reason": key[0], "operation": key[1], "importance": tier})
    return out


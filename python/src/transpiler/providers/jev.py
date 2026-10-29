"""Jev as a decision provider (Phase 2).

Jev never writes code: for each decision it picks one of the candidates the worker has already
generated and validated, or abstains. One ``system_one`` call per decision asks three questions
about the same state (the decision, its candidates and its context graph):

- ``pick``: a ``Choice`` among the candidate ids plus ``__abstain__`` ("none preserves the
  semantics exactly") and, when context is truncated and expansions remain, ``__need_context__``;
- ``refs`` (only with ``__need_context__``): a ``Choice`` of which truncated context node to open;
- ``review_priority``: an advisory ``Score`` on the :data:`~transpiler.protocol.REVIEW_PRIORITY`
  scale. It never affects the gate and is not part of the fingerprint.

The :class:`Gate` decides from the decision's deterministic importance tier whether the pick may
be applied. A pick that fails it stays pending (``below_floor`` / ``abstained``): the run is
incomplete and nothing is written. The C++ orchestrator re-validates every passed pick.

The network client (``typesafe_sdk``, the ``transpiler[jev]`` extra) is imported only when a
real client is needed; tests use :class:`RecordedJevClient`. The API key is read from the
environment variable named by ``api_key_env`` and never logged or written to the job.

``base_url`` picks the System One endpoint: TypeSafe's own API by default (or ``TYPESAFE_BASE_URL``),
or :data:`OPENROUTER_BASE_URL` with an OpenRouter key, where bare model ids such as ``jev-latest``
are mapped onto OpenRouter's ``typesafe/`` models.
"""

from __future__ import annotations

import json
import math
import os
import sys
from collections.abc import Mapping, Sequence
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Protocol

from ..protocol import IMPORTANCE_TIERS, REVIEW_PRIORITY, DecisionRequest, DecisionResponse, ExtraContext, ReviewPriority
from .base import DecisionProvider

ABSTAIN = "__abstain__"
NEED_CONTEXT = "__need_context__"
OPENROUTER_BASE_URL = "https://openrouter.ai/api"
DEFAULT_FLOORS: Mapping[str, float | None] = {"low": 0.70, "normal": 0.85, "high": 0.95, "critical": 0.99}


class ChoiceLike(Protocol):
    choice: str
    confidence: float
    probabilities: Mapping[str, float]


@dataclass(frozen=True)
class Gate:
    """A minimum confidence per importance tier, plus a margin over the runner-up.

    An answer passes only if ``confidence >= floors[importance]`` and the top probability exceeds
    the runner-up's by at least ``min_margin``. A floor of ``None`` (or a tier left out) means
    the tier is never applied automatically.
    """

    floors: Mapping[str, float | None] = field(default_factory=lambda: dict(DEFAULT_FLOORS))
    min_margin: float = 0.15

    def __post_init__(self) -> None:
        unknown = set(self.floors) - set(IMPORTANCE_TIERS)
        if unknown:
            raise ValueError(f"unknown importance tiers {sorted(unknown)} (have {IMPORTANCE_TIERS})")
        for tier, floor in self.floors.items():
            if floor is not None and not 0 <= floor <= 1:
                raise ValueError(f"floor of {tier!r} must be in [0, 1] or None, not {floor}")
        if not 0 <= self.min_margin <= 1:
            raise ValueError(f"min_margin must be in [0, 1], not {self.min_margin}")

    def check(self, importance: str, answer: ChoiceLike) -> str:
        """``passed``, ``below_floor``, ``abstained`` or ``need_context`` for a ``pick`` answer."""
        if answer.choice == ABSTAIN:
            return "abstained"
        if answer.choice == NEED_CONTEXT:
            return "need_context"
        floor = self.floors.get(importance)
        if floor is None or answer.confidence < floor:
            return "below_floor"
        ranked = sorted(answer.probabilities.values(), reverse=True)
        top = ranked[0] if ranked else answer.confidence
        runner_up = ranked[1] if len(ranked) > 1 else 0.0
        # Probabilities arrive as floats; a margin equal to min_margin up to rounding passes.
        if top - runner_up < self.min_margin and not math.isclose(top - runner_up, self.min_margin, abs_tol=1e-9):
            return "below_floor"
        return "passed"

    def to_json(self) -> dict[str, Any]:
        return {"floors": dict(self.floors), "min_margin": self.min_margin}


@dataclass(frozen=True)
class ContextBudget:
    """The context graph the worker attaches (``max_depth``, ``max_chars``) and how many
    ``expand_context`` round trips one decision may take."""

    max_chars: int = 4000
    max_depth: int = 2
    max_expansions: int = 2

    def __post_init__(self) -> None:
        if min(self.max_chars, self.max_depth, self.max_expansions) < 0:
            raise ValueError("context budget values must be non-negative")

    def to_json(self) -> dict[str, int]:
        return {"max_chars": self.max_chars, "max_depth": self.max_depth, "max_expansions": self.max_expansions}


class SystemOneClient(Protocol):
    def system_one(self, state: Any, questions: Mapping[str, Any], *, model: str | None = None) -> Any: ...


@dataclass(frozen=True)
class _Choice:
    choice: str
    confidence: float
    probabilities: Mapping[str, float]


@dataclass(frozen=True)
class _Score:
    score: float
    confidence: float
    probabilities: Mapping[int, float]


@dataclass(frozen=True)
class _Response:
    choices: Mapping[str, _Choice]
    scores: Mapping[str, _Score]


class RecordedJevClient:
    """Replays ``system_one`` answers from JSON recordings keyed by decision fingerprint.

    The recording file maps a fingerprint to one response, or to a list of responses indexed by
    the number of context expansions already made for the decision. A response is
    ``{"pick": {choice, confidence, probabilities}, "refs"?: {...}, "review_priority"?: {score,
    confidence, probabilities: {"0": p, ...}}}``. A decision without a recording gets no answer.
    Every call is kept in :attr:`calls` for tests.
    """

    def __init__(self, recordings: Mapping[str, Any] | str | os.PathLike[str]):
        if isinstance(recordings, Mapping):
            self.path: Path | None = None
            self.recordings = dict(recordings)
        else:
            self.path = Path(recordings).resolve()
            self.recordings = json.loads(self.path.read_text())
        self.calls: list[dict[str, Any]] = []

    def has(self, fingerprint: str) -> bool:
        return fingerprint in self.recordings

    def system_one(self, state: Any, questions: Mapping[str, Any], *, model: str | None = None) -> _Response:
        self.calls.append({"state": state, "questions": dict(questions), "model": model})
        recorded = self.recordings[state["decision"]["fingerprint"]]
        if isinstance(recorded, list):
            recorded = recorded[min(state["expansions"], len(recorded) - 1)]
        choices = {name: _Choice(a["choice"], a["confidence"], a.get("probabilities", {}))
                   for name, a in recorded.items() if name in questions and "choice" in a}
        scores = {name: _Score(a["score"], a["confidence"], {int(k): v for k, v in a.get("probabilities", {}).items()})
                  for name, a in recorded.items() if name in questions and "score" in a}
        return _Response(choices, scores)


class JevProvider(DecisionProvider):
    """Asks Jev to choose among worker-validated candidates, gated by importance.

        JevProvider(model="jev-preview",
                    gate=Gate({"low": 0.70, "normal": 0.85, "high": 0.95, "critical": 0.99}, min_margin=0.15),
                    context=ContextBudget(max_chars=4000, max_depth=2, max_expansions=2))

    Through OpenRouter: ``JevProvider("jev-latest", base_url=OPENROUTER_BASE_URL,
    api_key_env="OPENROUTER_API_KEY")``. ``timeout_seconds`` bounds each HTTP call to the API.
    """

    name = "jev"

    def __init__(
        self,
        model: str,
        gate: Gate | None = None,
        context: ContextBudget | None = None,
        api_key_env: str = "TYPESAFE_API_KEY",
        client: SystemOneClient | None = None,
        request_timeout_seconds: int = 900,
        base_url: str | None = None,
        timeout_seconds: float = 120.0,
    ):
        self.model = model
        self.gate = gate or Gate()
        self.context = context or ContextBudget()
        self.api_key_env = api_key_env
        self.request_timeout_seconds = request_timeout_seconds
        self.base_url = base_url
        self.timeout_seconds = timeout_seconds
        self._client = client

    # ---------------------------------------------------------------- config

    def config(self) -> dict[str, Any]:
        """What :meth:`from_config` needs in the provider process (never the API key itself)."""
        d: dict[str, Any] = {"model": self.model, "gate": self.gate.to_json(), "context": self.context.to_json(),
                             "api_key_env": self.api_key_env, "timeout_seconds": self.timeout_seconds}
        if self.base_url:
            d["base_url"] = self.base_url
        if isinstance(self._client, RecordedJevClient):
            if self._client.path is None:
                raise ValueError("a RecordedJevClient used out of process must be loaded from a file")
            d["recordings"] = str(self._client.path)
        elif self._client is not None:
            raise ValueError("only a RecordedJevClient can be passed to the provider process; omit client for Jev")
        return d

    @classmethod
    def from_config(cls, config: Mapping[str, Any]) -> JevProvider:
        recordings = config.get("recordings")
        return cls(
            model=config["model"],
            gate=Gate(**config.get("gate", {})),
            context=ContextBudget(**config.get("context", {})),
            api_key_env=config.get("api_key_env", "TYPESAFE_API_KEY"),
            client=RecordedJevClient(recordings) if recordings else None,
            base_url=config.get("base_url"),
            timeout_seconds=config.get("timeout_seconds", 120.0),
        )

    def to_config(self) -> dict[str, Any]:
        """The ``kind: "python"`` job entry: this interpreter runs :mod:`transpiler.providers.serve`."""
        return {
            "kind": "python",
            "command": [sys.executable, "-m", "transpiler.providers.serve", f"{__name__}:{type(self).__name__}",
                        json.dumps(self.config())],
            "context": self.context.to_json(),
            "request_timeout_seconds": self.request_timeout_seconds,
        }

    def context_budget(self) -> dict[str, int]:
        return {"max_depth": self.context.max_depth, "max_chars": self.context.max_chars}

    @property
    def client(self) -> SystemOneClient:
        if self._client is None:
            try:
                from typesafe_sdk import TypeSafeClient
            except ImportError as e:
                raise RuntimeError("JevProvider needs the typesafe_sdk package: install transpiler[jev]") from e
            key = os.environ.get(self.api_key_env, "").strip()
            if not key:
                raise RuntimeError(f"JevProvider: environment variable {self.api_key_env} is not set")
            self._client = TypeSafeClient(api_key=key, model=self.model, base_url=self.base_url,
                                          timeout=self.timeout_seconds)
        return self._client

    # ---------------------------------------------------------------- decide

    def decide(
        self, batch: Sequence[DecisionRequest], extra_context: Mapping[str, ExtraContext] | None = None
    ) -> list[DecisionResponse]:
        out = []
        for request in batch:
            if not request.candidates:
                continue  # nothing to choose: the decision stays pending for a person
            extra = (extra_context or {}).get(request.id)
            if isinstance(self._client, RecordedJevClient) and not self._client.has(request.fingerprint):
                print(f"[jev] no recording for {request.id} ({request.fingerprint})", file=sys.stderr)
                continue
            out.append(self._decide_one(request, extra))
        return out

    def _decide_one(self, request: DecisionRequest, extra: ExtraContext | None) -> DecisionResponse:
        refs = merge_refs(request.refs, extra.refs if extra else ())
        expansions = extra.expansions if extra else 0
        left = extra.expansions_left if extra else self.context.max_expansions
        truncated = [r for r in refs if r.get("truncated")]
        can_expand = bool(truncated) and left > 0
        questions = self.questions(request, truncated if can_expand else [])
        state = {
            "task": "Choose how this COBOL operation is translated to Java. Pick a candidate only if its generated "
                    "Java preserves the COBOL semantics exactly in this program; otherwise abstain.",
            "decision": {"id": request.id, "fingerprint": request.fingerprint, "reason": str(request.reason),
                         "operation": request.operation, "message": request.message,
                         "source": {"file": request.source.file, "line": request.source.line, "text": request.source.text},
                         "facts": request.context.get("facts", {}), "program_id": request.context.get("program_id")},
            "candidates": [{"id": c.id, "description": c.description, "preview": c.preview} for c in request.candidates],
            "context": refs,
            "expansions": expansions,
        }
        response = self.client.system_one(state, questions, model=self.model)
        pick = response.choices["pick"]
        review = self.review_priority(response.scores.get("review_priority"))
        gate = self.gate.check(request.importance, pick)
        common = {"decision_id": request.id, "fingerprint": request.fingerprint, "confidence": pick.confidence,
                  "probabilities": dict(pick.probabilities), "review_priority": review}
        if gate == "need_context":
            wanted = response.choices.get("refs")
            if not can_expand or wanted is None or wanted.choice not in {r["ref"] for r in truncated}:
                return DecisionResponse(None, gate="abstained", rationale="asked for context that cannot be opened",
                                        **common)
            return DecisionResponse(None, gate="need_context", need_context=(wanted.choice,), **common)
        candidate = None if gate == "abstained" else pick.choice
        return DecisionResponse(candidate, gate=gate, **common)

    @staticmethod
    def questions(request: DecisionRequest, truncated: Sequence[Mapping[str, Any]]) -> dict[str, Any]:
        """The ``system_one`` questions (plain question dictionaries; no SDK import needed)."""
        criteria: dict[str, Any] = {}
        for c in request.candidates:
            option: dict[str, Any] = {"description": c.description}
            if c.preview is not None:
                option["java"] = c.preview
            if c.slots:
                option["then_asks"] = [f"{s.name}: {s.description}" for s in c.slots]
            criteria[c.id] = option
        criteria[ABSTAIN] = "None of the candidates preserves the COBOL semantics exactly."
        if truncated:
            criteria[NEED_CONTEXT] = "The context shown is not enough to decide; open one of the truncated nodes."
        questions: dict[str, Any] = {"pick": {
            "type": "choice",
            "instructions": f"{request.message} Which candidate translates `{request.source.text}` exactly?",
            "criteria": criteria,
        }}
        if truncated:
            questions["refs"] = {
                "type": "choice",
                "instructions": "Which truncated context node would settle the choice?",
                "criteria": {r["ref"]: r.get("summary") for r in truncated},
            }
        questions["review_priority"] = {
            "type": "score",
            "instructions": "If this translation were wrong, how much would it matter?",
            "criteria": [f"{label}: {meaning}" for label, meaning in REVIEW_PRIORITY],
        }
        return questions

    @staticmethod
    def review_priority(score: Any) -> ReviewPriority | None:
        """The most probable level of the ``review_priority`` score (the lower one on a tie)."""
        if score is None or not score.probabilities:
            return None
        level = max(sorted(score.probabilities), key=lambda k: score.probabilities[k])
        return ReviewPriority(level, REVIEW_PRIORITY[level][0], score.confidence)


def merge_refs(refs: Sequence[Mapping[str, Any]], extra: Sequence[Mapping[str, Any]]) -> list[dict[str, Any]]:
    """The context with expanded nodes replacing their truncated versions; new nodes appended."""
    out = {r["ref"]: dict(r) for r in refs}
    for r in extra:
        if r["ref"] not in out or out[r["ref"]].get("truncated"):
            out[r["ref"]] = dict(r)
    return list(out.values())

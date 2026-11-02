"""Jev as a decision provider (Phase 2).

Jev never writes code: for each decision it picks one of the candidates the worker has already
generated and validated, or abstains. One ``system_one`` call per decision asks three questions
about the same state (the decision, its candidates and its context graph):

- ``pick``: a ``Choice`` among the candidate ids plus ``__abstain__`` ("none preserves the
  semantics exactly") and, when context is truncated and expansions remain, ``__need_context__``;
- ``refs`` (only with ``__need_context__``): a ``Choice`` of which truncated context node to open;
- ``review_priority``: an advisory ``Score`` on the :data:`~transpiler.protocol.REVIEW_PRIORITY`
  scale. It never affects the gate and is not part of the fingerprint.

The state tells Jev what the candidates are (tested handlers whose preview was generated for this
statement) and asks it to check fit: whether a candidate's description covers the statement as
written and its Java carries out every part of it. Abstaining is for a concrete misfit or a
candidate that is correct only under a condition the program does not guarantee; a vague
"preserves the semantics exactly" makes Jev abstain on candidates it cannot fault.

By default Jev always makes an attempt: the candidate it ranks highest is applied even when it
ranks ``__abstain__`` above every candidate, and the answer's ``confidence`` is the probability
Jev gives that candidate (the full distribution, ``__abstain__`` included, stays in the report and
a rationale says when Jev would have abstained). Only a ``__need_context__`` answer that can be
served delays the pick. With a :class:`Gate`, the decision's deterministic importance tier
instead decides whether the pick may be applied: a pick that fails it stays pending
(``below_floor`` / ``abstained``), the run is incomplete and nothing is written. Either way the
C++ orchestrator re-validates every passed pick.

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

TASK = (
    "You are choosing which builtin handler translates one COBOL statement to Java (GnuCOBOL 3.2 semantics). "
    "Every candidate is a tested handler of the translator: for the statements its description covers, its Java "
    "reproduces GnuCOBOL exactly, and the translator already generated and compiled the preview for this very "
    "statement. Your job is to check fit: does the candidate's description cover this statement's form, operands "
    "and clauses as written? Pick the candidate whose description fits. Abstain only for a concrete reason: the "
    "description excludes something this statement has, the preview does something the statement does not ask "
    "for, or the description says the candidate is correct only under a condition (\"correct only if\", "
    "\"candidate only\", \"may\") that the program does not guarantee. Behaviour a description names, such as "
    "truncation, padding, alignment, rounding, sign handling or GnuCOBOL's intermediate precision, is how COBOL "
    "itself behaves and the handler reproduces it; it is not a loss of semantics. Use the data definitions in the "
    "context (PICTURE, USAGE, VALUE) to check the operands' categories against what the description covers."
)
LIBRARY_TASK = (
    "The user mapped a part of this COBOL program (a called program, an operation, a paragraph or a copybook; "
    "decision.library.part has its COBOL source) to a Java library already installed in their target project, and "
    "this statement uses that part. The call into the library is built one step at a time (decision.library.step: "
    "start = the first call, args = which COBOL item each parameter takes, next = a further call on the value so far "
    "or end, result = which COBOL item receives the value, lifetime = one object per use or per program); "
    "decision.library.chain lists the calls already chosen. The library candidates are NOT tested against the "
    "original COBOL: judge from their Java docs (signature and Javadoc), their parameter names, their preview and "
    "the COBOL part's source and comments whether they do what the COBOL does with these items. The user chose this "
    "library for this code, so prefer a library candidate that does what the COBOL part does. Choose cobol:translate "
    "(the exact translation of the COBOL) only if no library candidate does anything close to what the COBOL does; "
    "for args and result, pick the binding whose parameter names and docs match what each COBOL item holds."
)
LIBRARY_ABSTAIN_CRITERION = "Neither a library candidate nor cobol:translate fits this step."
# Jev's input limit in characters, measured with scripts/jev-smoke.sh (see docs/architecture.md); the
# options of a decision may use what is left after the rest of the state, with 25% left free.
JEV_INPUT_CHARS = 32000
STATE_OVERHEAD_CHARS = 4000
WHY_ASKED: Mapping[str, str] = {
    "missing_mapping": "No mapping rule is configured for this statement's facts, so no handler was chosen automatically.",
    "unsupported_construct": "The translator could not prove that the standard translation is exact here.",
    "missing_target_api": "The statement depends on something outside the translation project.",
    "callback_required": "The mapping rule asks for a person's decision.",
    "library_call": "The statement uses a part of the program that the user mapped to a Java library.",
}
ABSTAIN_CRITERION = ("No candidate fits: each one either excludes something this statement has, or is correct only "
                     "under a condition the program does not guarantee.")
SETTING_TASK = (
    "You are inferring a project setting that COBOL source does not state, so that the Java translation behaves "
    "like the original programs did. The programs were written for, and ran with, one of the candidate values; "
    "each candidate says what its value means. Decide from the evidence: decision.evidence lists every statement of "
    "the program that depends on the setting, and the context shows the data they use. Look for logic that only "
    "makes sense under one value, such as a range check, an ordering or a comparison whose outcome the program "
    "relies on (for example `>= \"A\" AND <= \"9\"` covers letters and digits only in EBCDIC order, where digits "
    "sort after letters). Comments (program.comments, a node's comments), the IDENTIFICATION DIVISION paragraphs "
    "(program.identification) and names may also say where the code came from. Abstain only if the evidence "
    "does not favour any value."
)
SETTING_ABSTAIN_CRITERION = "The evidence does not favour any value: the statements behave the same under each."


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
    # Characters of a decision's options (descriptions, previews, docs) sent to Jev; None: what Jev's
    # input limit leaves after the state, the question and max_chars, with 25% left free. A decision
    # whose options do not fit is held as too_many_options instead of being cut.
    max_option_chars: int | None = None

    def __post_init__(self) -> None:
        if min(self.max_chars, self.max_depth, self.max_expansions) < 0:
            raise ValueError("context budget values must be non-negative")
        if self.max_option_chars is not None and self.max_option_chars <= 0:
            raise ValueError("max_option_chars must be positive")

    @property
    def option_chars(self) -> int:
        """The option budget in effect."""
        if self.max_option_chars is not None:
            return self.max_option_chars
        return max(1, int((JEV_INPUT_CHARS - STATE_OVERHEAD_CHARS - self.max_chars) * 0.75))

    def to_json(self) -> dict[str, int]:
        d = {"max_chars": self.max_chars, "max_depth": self.max_depth, "max_expansions": self.max_expansions}
        if self.max_option_chars is not None:
            d["max_option_chars"] = self.max_option_chars
        return d


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
    """Asks Jev to choose among worker-validated candidates.

        JevProvider(model="jev-preview", context=ContextBudget(max_chars=4000, max_depth=2, max_expansions=2))

    always applies Jev's best candidate and reports its probability as the confidence. A gate
    holds picks below a floor per importance tier instead:

        JevProvider(model="jev-preview",
                    gate=Gate({"low": 0.70, "normal": 0.85, "high": 0.95, "critical": 0.99}, min_margin=0.15))

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
        self.gate = gate
        self.context = context or ContextBudget()
        self.api_key_env = api_key_env
        self.request_timeout_seconds = request_timeout_seconds
        self.base_url = base_url
        self.timeout_seconds = timeout_seconds
        self._client = client

    # ---------------------------------------------------------------- config

    def config(self) -> dict[str, Any]:
        """What :meth:`from_config` needs in the provider process (never the API key itself)."""
        d: dict[str, Any] = {"model": self.model, "gate": self.gate.to_json() if self.gate else None,
                             "context": self.context.to_json(),
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
            gate=Gate(**config["gate"]) if config.get("gate") is not None else None,
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
        held = self.too_many_options(request, questions)
        if held is not None:
            return held
        state = self.state(request, refs, expansions)
        response = self.client.system_one(state, questions, model=self.model)
        pick = response.choices["pick"]
        review = self.review_priority(response.scores.get("review_priority"))
        common = {"decision_id": request.id, "fingerprint": request.fingerprint, "confidence": pick.confidence,
                  "probabilities": dict(pick.probabilities), "review_priority": review}
        if pick.choice == NEED_CONTEXT:
            wanted = response.choices.get("refs")
            if can_expand and wanted is not None and wanted.choice in {r["ref"] for r in truncated}:
                return DecisionResponse(None, gate="need_context", need_context=(wanted.choice,), **common)
            if self.gate is not None:
                return DecisionResponse(None, gate="abstained", rationale="asked for context that cannot be opened",
                                        **common)
        if self.gate is None:
            return self.attempt(request, pick, common)
        gate = self.gate.check(request.importance, pick)
        candidate = None if gate == "abstained" else pick.choice
        return DecisionResponse(candidate, gate=gate, **common)

    def too_many_options(self, request: DecisionRequest, questions: Mapping[str, Any]) -> DecisionResponse | None:
        """A held answer when the options do not fit :attr:`ContextBudget.option_chars`. Options are
        never dropped to fit (a cut could hide the right one): the user narrows the library instead."""
        criteria = questions["pick"]["criteria"]
        size = sum(len(json.dumps(v)) for k, v in criteria.items() if k not in (ABSTAIN, NEED_CONTEXT))
        if size <= self.context.option_chars:
            return None
        rationale = (f"too_many_options: {len(request.candidates)} options use {size} characters, over the "
                     f"{self.context.option_chars} Jev can read with room to spare")
        if request.library:
            owners: dict[str, int] = {}
            for c in request.candidates:
                if c.handler == "library" and c.id.startswith("library:"):
                    owner = c.id.split(":", 2)[2].split("(")[0].rsplit(".", 1)[0]
                    owners[owner] = owners.get(owner, 0) + len(json.dumps(criteria.get(c.id, "")))
            top = sorted(owners.items(), key=lambda kv: -kv[1])[:5]
            part = request.library.get("part", {}).get("name", "")
            rationale += (f"; narrow library {request.library.get('id')!r} for {part} with expose= or hints=; "
                          f"the classes with the most options: {', '.join(o for o, _ in top)}")
        return DecisionResponse(None, decision_id=request.id, fingerprint=request.fingerprint, gate="abstained",
                                rationale=rationale, held_reason="too_many_options")

    @staticmethod
    def attempt(request: DecisionRequest, pick: ChoiceLike, common: Mapping[str, Any]) -> DecisionResponse:
        """The ungated answer: Jev's choice, or the candidate it ranks highest when it chose ``__abstain__``
        or context that cannot be opened. ``confidence`` is the probability of the selected candidate."""
        def p(c: str) -> float:
            return pick.probabilities.get(c, pick.confidence if c == pick.choice else 0.0)
        rationale = ""
        selected = pick.choice
        if selected in (ABSTAIN, NEED_CONTEXT):
            selected = max((c.id for c in request.candidates), key=p)  # the first offered on a tie
            rationale = f"jev ranked {pick.choice} first ({p(pick.choice):.2f}); applied its best candidate"
        # A choice the worker did not offer is passed on for the orchestrator to reject.
        return DecisionResponse(selected, gate="passed", rationale=rationale, **{**common, "confidence": p(selected)})

    @staticmethod
    def state(request: DecisionRequest, refs: Sequence[Mapping[str, Any]], expansions: int) -> dict[str, Any]:
        """The ``system_one`` state: the task, the statement, its candidates and its context graph."""
        if request.setting:
            facts = request.context.get("facts", {})
            values = request.setting.get("values", {})
            return {
                "task": SETTING_TASK,
                "decision": {"id": request.id, "fingerprint": request.fingerprint, "setting": request.setting.get("field"),
                             "evidence": facts.get("usages", [request.source.text]), "question": request.message,
                             "program_id": request.context.get("program_id"), "file": request.source.file},
                "candidates": [{"id": c.id, "value": values.get(c.id), "means": c.description} for c in request.candidates],
                "context": list(refs),
                "program": request.context.get("program", {}),
                "expansions": expansions,
            }
        reason = str(request.reason)
        decision = {"id": request.id, "fingerprint": request.fingerprint, "cobol": request.source.text,
                    "operation": request.operation, "facts": request.context.get("facts", {}),
                    "why_asked": f"{WHY_ASKED.get(reason, reason)} {request.message}",
                    "program_id": request.context.get("program_id"),
                    "file": request.source.file, "line": request.source.line}
        if request.library:
            decision["library"] = request.library
        return {
            "task": LIBRARY_TASK if request.library else TASK,
            "decision": decision,
            "candidates": [{"id": c.id, "description": c.description, "java": c.preview, **({"docs": list(c.docs)} if c.docs else {})}
                           for c in request.candidates],
            "context": list(refs),
            "program": request.context.get("program", {}),
            "expansions": expansions,
        }

    @staticmethod
    def questions(request: DecisionRequest, truncated: Sequence[Mapping[str, Any]]) -> dict[str, Any]:
        """The ``system_one`` questions (plain question dictionaries; no SDK import needed)."""
        if request.setting:
            return JevProvider._setting_questions(request, truncated)
        criteria: dict[str, Any] = {}
        for c in request.candidates:
            option: dict[str, Any] = {"covers": c.description}
            if c.preview is not None:
                option["java"] = c.preview
            if c.docs:
                option["docs"] = list(c.docs)
            if c.slots:
                option["then_asks"] = [f"{s.name}: {s.description}" for s in c.slots]
            criteria[c.id] = option
        criteria[ABSTAIN] = LIBRARY_ABSTAIN_CRITERION if request.library else ABSTAIN_CRITERION
        if truncated:
            criteria[NEED_CONTEXT] = "Deciding needs a context node that is shown truncated."
        instructions = (f"Which candidate does, at this step, what the COBOL part does for `{request.source.text}`?"
                        if request.library else f"Which handler's description covers `{request.source.text}` as written?")
        questions: dict[str, Any] = {"pick": {"type": "choice", "instructions": instructions, "criteria": criteria}}
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
    def _setting_questions(request: DecisionRequest, truncated: Sequence[Mapping[str, Any]]) -> dict[str, Any]:
        values = request.setting.get("values", {}) if request.setting else {}
        criteria: dict[str, Any] = {c.id: {"value": values.get(c.id), "means": c.description} for c in request.candidates}
        criteria[ABSTAIN] = SETTING_ABSTAIN_CRITERION
        if truncated:
            criteria[NEED_CONTEXT] = "Deciding needs a context node that is shown truncated."
        field = request.setting.get("field") if request.setting else request.operation
        questions: dict[str, Any] = {"pick": {
            "type": "choice",
            "instructions": f"Which value of `{field}` were these programs written for and run with?",
            "criteria": criteria,
        }}
        if truncated:
            questions["refs"] = {"type": "choice", "instructions": "Which truncated context node would settle the choice?",
                                 "criteria": {r["ref"]: r.get("summary") for r in truncated}}
        questions["review_priority"] = {
            "type": "score",
            "instructions": "If this setting were wrong, how much would it matter?",
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

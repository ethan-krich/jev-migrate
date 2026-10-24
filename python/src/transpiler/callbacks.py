"""User callback references and a minimal dispatcher.

A ``callback`` mapping names a function in a user script::

    mappings.add("CALL", strategy="callback", script="./callbacks.py", function="resolve_call")

Only the *reference* travels through the protocol. The intended flow (phase 2) is: the worker
raises a ``callback_required`` decision carrying the reference, the C++ orchestrator forwards
it to a persistent Python subprocess, which calls :func:`dispatch` and returns the answer for
the worker to validate and apply. The worker never executes Python.

Phase 1 implements the data types and :func:`dispatch` (tested on its own); the orchestrator
does not route callbacks yet, so callback mappings surface as unresolved decisions.
"""

from __future__ import annotations

import importlib.util
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Callable


@dataclass(frozen=True)
class CallbackRef:
    """A script path and function name. Relative scripts resolve against the job's base dir."""

    script: str
    function: str

    def to_dict(self) -> dict[str, str]:
        return {"script": self.script, "function": self.function}

    def resolve(self, base_dir: Path) -> CallbackRef:
        path = Path(self.script)
        return CallbackRef(str(path if path.is_absolute() else (base_dir / path).resolve()), self.function)


@dataclass(frozen=True)
class CallbackRequest:
    """What a callback receives: the decision request raised by the worker, as plain JSON."""

    decision: dict[str, Any]

    @property
    def callback(self) -> CallbackRef:
        cb = self.decision["callback"]
        return CallbackRef(cb["script"], cb["function"])


@dataclass(frozen=True)
class CallbackResponse:
    """A callback's answer. Phase 1 defines only candidate selection, like every provider."""

    decision_id: str
    candidate_id: str
    rationale: str = ""

    def to_dict(self) -> dict[str, Any]:
        return {
            "decision_id": self.decision_id,
            "selection": {"kind": "candidate", "candidate_id": self.candidate_id},
            "provider": "callback",
            "rationale": self.rationale,
        }


class CallbackError(RuntimeError):
    pass


def load_callback(ref: CallbackRef) -> Callable[[CallbackRequest], Any]:
    path = Path(ref.script)
    if not path.is_file():
        raise CallbackError(f"callback script {path} not found")
    spec = importlib.util.spec_from_file_location(f"transpiler_callback_{abs(hash(path))}", path)
    if spec is None or spec.loader is None:
        raise CallbackError(f"cannot load {path}")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    fn = getattr(module, ref.function, None)
    if not callable(fn):
        raise CallbackError(f"{path} has no callable {ref.function!r}")
    return fn


def dispatch(request: CallbackRequest) -> CallbackResponse:
    """Invokes the referenced function and validates its answer against the decision.

    The function may return a ``CallbackResponse``, a candidate id string, or ``None`` (leave
    the decision unresolved, reported as ``CallbackError``).
    """
    fn = load_callback(request.callback)
    answer = fn(request)
    decision_id = request.decision["id"]
    if isinstance(answer, str):
        answer = CallbackResponse(decision_id, answer)
    if answer is None:
        raise CallbackError(f"callback {request.callback.function} left {decision_id} unresolved")
    if not isinstance(answer, CallbackResponse):
        raise CallbackError(f"callback returned {type(answer).__name__}, expected CallbackResponse or str")
    offered = {c["id"] for c in request.decision.get("candidates", [])}
    if answer.decision_id != decision_id or answer.candidate_id not in offered:
        raise CallbackError(f"callback selected {answer.candidate_id!r}, which {decision_id} does not offer")
    return answer

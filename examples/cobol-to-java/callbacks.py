"""Example callback script referenced by mappings.py.

Phase 1 carries the reference ({script, function}) through C++ to Java and back as a
`callback_required` decision; the orchestrator does not invoke callbacks yet. The function
signature below is what transpiler.callbacks.dispatch calls.
"""

from transpiler import CallbackRequest


def resolve_call(request: CallbackRequest) -> str | None:
    # Choose among the worker's validated candidates, or return None to leave it unresolved.
    candidates = [c["id"] for c in request.decision.get("candidates", [])]
    return candidates[0] if candidates else None

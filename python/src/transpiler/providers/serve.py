"""The out-of-process provider loop (``transpiler-provider/1``, see docs/worker-protocol.md).

    python -m transpiler.providers.serve <module:Class> <config-json>

loads ``Class.from_config(config)`` and answers the orchestrator's ``decide`` requests on
stdout, one JSON object per line. Everything else (logging, prints of the provider) goes to
stderr, which the orchestrator keeps as a diagnostic tail.
"""

from __future__ import annotations

import importlib
import json
import sys
import traceback
from collections.abc import Callable
from typing import IO, Any

from ..protocol import PROVIDER_PROTOCOL, DecisionRequest, ExtraContext
from .base import DecisionProvider

VERSION = "1"


def load(factory: str, config: dict[str, Any]) -> DecisionProvider:
    """``"module:Class"`` -> ``Class.from_config(config)``."""
    module_name, sep, attr = factory.partition(":")
    if not sep or not module_name or not attr:
        raise ValueError(f"provider factory must be 'module:Class', not {factory!r}")
    cls = getattr(importlib.import_module(module_name), attr)
    return cls.from_config(config)


def _response(id_: str | None, result: dict[str, Any]) -> dict[str, Any]:
    return {"protocol": PROVIDER_PROTOCOL, "type": "response", "id": id_, "status": "ok", "result": result}


def _error(id_: str | None, code: str, message: str) -> dict[str, Any]:
    return {"protocol": PROVIDER_PROTOCOL, "type": "response", "id": id_, "status": "error",
            "error": {"code": code, "message": message}}


def handle(provider: DecisionProvider, line: str) -> tuple[dict[str, Any], bool]:
    """One request line -> (response, keep serving)."""
    try:
        msg = json.loads(line)
    except json.JSONDecodeError as e:
        return _error(None, "malformed_json", f"request is not valid JSON: {e}"), True
    if not isinstance(msg, dict):
        return _error(None, "invalid_request", "request must be a JSON object"), True
    id_ = msg.get("id") if isinstance(msg.get("id"), str) else None
    if msg.get("protocol") != PROVIDER_PROTOCOL:
        return _error(id_, "unsupported_protocol", f"expected protocol {PROVIDER_PROTOCOL!r}"), True
    if not id_:
        return _error(None, "invalid_request", "'id' must be a non-empty string"), True
    method = msg.get("method")
    if method == "shutdown":
        return _response(id_, {}), False
    if method != "decide":
        return _error(id_, "unknown_method", f"unknown method {method!r}"), True
    params = msg.get("params", {})
    try:
        batch = [DecisionRequest.from_json(d) for d in params["decisions"]]
        extra = {e.decision_id: e for e in (ExtraContext.from_json(x) for x in params.get("extra_context", []))}
    except (KeyError, TypeError, ValueError) as e:
        return _error(id_, "invalid_params", f"malformed decide params: {e!r}"), True
    try:
        answers = provider.decide(batch, extra)
    except Exception as e:  # reported to the orchestrator, which fails the run
        traceback.print_exc(file=sys.stderr)
        return _error(id_, "internal_error", f"{type(e).__name__}: {e}"), True
    return _response(id_, {"answers": [a.to_json() for a in answers]}), True


def serve(provider: DecisionProvider, stdin: IO[str], stdout: IO[str]) -> int:
    write: Callable[[dict[str, Any]], None] = lambda m: (stdout.write(json.dumps(m) + "\n"), stdout.flush())  # noqa: E731
    write({"protocol": PROVIDER_PROTOCOL, "type": "event", "event": "ready",
           "data": {"provider": provider.name, "version": VERSION}})
    for line in stdin:
        if not line.strip():
            continue
        response, keep = handle(provider, line)
        write(response)
        if not keep:
            return 0
    print("[provider] stdin closed without shutdown; exiting", file=sys.stderr)
    return 0


def main(argv: list[str] | None = None) -> int:
    args = sys.argv[1:] if argv is None else argv
    if len(args) != 2:
        print("usage: python -m transpiler.providers.serve <module:Class> <config-json>", file=sys.stderr)
        return 64
    protocol_out = sys.stdout
    sys.stdout = sys.stderr  # nothing but protocol messages on the real stdout
    provider = load(args[0], json.loads(args[1]))
    return serve(provider, sys.stdin, protocol_out)


if __name__ == "__main__":
    sys.exit(main())

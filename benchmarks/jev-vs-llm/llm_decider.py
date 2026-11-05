"""A chat LLM in Jev's place, for benchmarks/jev-vs-llm/bench.py only (not a supported provider).

`LlmDecisionProvider` is `JevProvider` with another client: the decision state, the questions, the
task texts, the context expansion and the ungated "always attempt" rule are Jev's own, so the
LLM sees exactly what Jev sees and still only picks among the worker's candidates. Instead of
`system_one`, the state and the questions go to an OpenRouter chat model, which answers each
question as JSON with a probability per option (the pick's probability is the confidence).

Each call is appended to the JSONL file `log` (file, line, cost, tokens, seconds) so the
benchmark can report the exact cost per file. The provider runs in the orchestrator's provider
process, so this module must be importable there (the benchmark puts its directory on PYTHONPATH).
"""

from __future__ import annotations

import json
import os
import re
import sys
import time
import urllib.error
import urllib.request
from collections.abc import Mapping
from typing import Any

from transpiler.providers.jev import ContextBudget, JevProvider, _Choice, _Response, _Score

OPENROUTER_CHAT = "https://openrouter.ai/api/v1/chat/completions"

SYSTEM = (
    "You answer questions about one decision of a COBOL-to-Java translator. You get a JSON state (the task, the "
    "COBOL statement, the candidates the translator generated, and the program context) and JSON questions. Follow "
    "the task text in the state. Reply with one JSON object and nothing else: for each question of type \"choice\", "
    "\"<question name>\": {\"choice\": \"<one of its criteria keys, exactly>\", \"probabilities\": {\"<criteria key>\": "
    "<probability>, ...}} with a probability for every criteria key, summing to 1; for each question of type "
    "\"score\", \"<question name>\": {\"score\": <index of the criteria entry, from 0>}."
)


class LlmSystemOneClient:
    def __init__(self, model: str, api_key: str, log: str | None, timeout: float):
        self.model, self.api_key, self.log, self.timeout = model, api_key, log, timeout

    def _chat(self, messages: list[dict[str, str]]) -> dict[str, Any]:
        body = {"model": self.model, "max_tokens": 2000, "usage": {"include": True}, "messages": messages,
                "response_format": {"type": "json_object"}}
        req = urllib.request.Request(OPENROUTER_CHAT, data=json.dumps(body).encode(),
                                     headers={"Authorization": f"Bearer {self.api_key}", "Content-Type": "application/json",
                                              "X-Title": "jev-migrate benchmark"})
        for attempt in range(1, 4):  # transient network errors (a dropped connection, a timeout, a 5xx) are retried
            try:
                with urllib.request.urlopen(req, timeout=self.timeout) as r:
                    return json.loads(r.read())
            except (urllib.error.URLError, TimeoutError, ConnectionError) as e:
                status = getattr(e, "code", None)
                if attempt == 3 or (status is not None and status < 500 and status != 429):
                    raise
                print(f"[llm_decider] {type(e).__name__}: {e}; retrying", file=sys.stderr)
                time.sleep(5 * attempt)
        raise AssertionError("unreachable")

    def system_one(self, state: Any, questions: Mapping[str, Any], *, model: str | None = None) -> _Response:
        messages = [{"role": "system", "content": SYSTEM},
                    {"role": "user", "content": "State:\n" + json.dumps(state, indent=1)
                     + "\n\nQuestions:\n" + json.dumps(questions, indent=1)}]
        start = time.perf_counter()
        cost = 0.0
        tokens_in = tokens_out = 0
        error = ""
        for _attempt in range(3):  # a reply that is not valid JSON for the questions is asked again
            reply = self._chat(messages)
            usage = reply.get("usage") or {}
            cost += float(usage.get("cost") or 0.0)
            tokens_in += int(usage.get("prompt_tokens") or 0)
            tokens_out += int(usage.get("completion_tokens") or 0)
            text = reply["choices"][0]["message"]["content"] or ""
            try:
                response = parse(text, questions)
                break
            except ValueError as e:
                error = str(e)
                messages += [{"role": "assistant", "content": text},
                             {"role": "user", "content": f"That reply is not valid: {e}. Reply with the JSON object only."}]
        else:
            raise RuntimeError(f"{self.model} gave no valid answer: {error}")
        if self.log:
            d = state.get("decision", {})
            with open(self.log, "a") as f:
                f.write(json.dumps({"file": d.get("file"), "line": d.get("line"), "cost_usd": cost,
                                    "tokens_in": tokens_in, "tokens_out": tokens_out,
                                    "seconds": time.perf_counter() - start, "pick": response.choices["pick"].choice}) + "\n")
        return response


def parse(text: str, questions: Mapping[str, Any]) -> _Response:
    """The reply as Jev's response type; ValueError when it does not answer the questions."""
    m = re.search(r"\{.*\}", text, re.S)
    if not m:
        raise ValueError("no JSON object")
    try:
        answer = json.loads(m.group(0))
    except json.JSONDecodeError as e:
        raise ValueError(f"invalid JSON ({e})") from e
    choices: dict[str, _Choice] = {}
    scores: dict[str, _Score] = {}
    for name, q in questions.items():
        a = answer.get(name)
        if q["type"] == "choice":
            keys = list(q["criteria"])
            if not isinstance(a, dict) or a.get("choice") not in keys:
                raise ValueError(f"{name}.choice must be one of {keys}")
            raw = a.get("probabilities") if isinstance(a.get("probabilities"), dict) else {}
            probs = {k: max(float(raw.get(k, 0.0) or 0.0), 0.0) for k in keys}
            total = sum(probs.values())
            if total <= 0:
                probs = {k: 1.0 if k == a["choice"] else 0.0 for k in keys}
                total = 1.0
            probs = {k: v / total for k, v in probs.items()}
            choices[name] = _Choice(a["choice"], probs[a["choice"]], probs)
        elif q["type"] == "score" and isinstance(a, dict) and isinstance(a.get("score"), int):
            level = min(max(a["score"], 0), len(q["criteria"]) - 1)
            scores[name] = _Score(float(level), 1.0, {level: 1.0})  # advisory only, as for Jev
    return _Response(choices, scores)


class LlmDecisionProvider(JevProvider):
    """`JevProvider` whose answers come from an OpenRouter chat model."""

    name = "llm"

    def __init__(self, model: str, log: str | None = None, context: ContextBudget | None = None,
                 api_key_env: str = "OPENROUTER_API_KEY", timeout_seconds: float = 120.0):
        super().__init__(model=model, context=context, api_key_env=api_key_env, timeout_seconds=timeout_seconds)
        self.log = log

    def config(self) -> dict[str, Any]:
        return {"model": self.model, "log": self.log, "context": self.context.to_json(),
                "api_key_env": self.api_key_env, "timeout_seconds": self.timeout_seconds}

    @classmethod
    def from_config(cls, config: Mapping[str, Any]) -> LlmDecisionProvider:
        return cls(model=config["model"], log=config.get("log"), context=ContextBudget(**config.get("context", {})),
                   api_key_env=config.get("api_key_env", "OPENROUTER_API_KEY"),
                   timeout_seconds=config.get("timeout_seconds", 120.0))

    def to_config(self) -> dict[str, Any]:
        d = super().to_config()
        d["command"] = [sys.executable, "-m", "transpiler.providers.serve", f"{__name__}:{type(self).__name__}",
                        json.dumps(self.config())]
        return d

    @property
    def client(self) -> LlmSystemOneClient:
        if self._client is None:
            key = os.environ.get(self.api_key_env, "").strip()
            if not key:
                raise RuntimeError(f"LlmDecisionProvider: environment variable {self.api_key_env} is not set")
            self._client = LlmSystemOneClient(self.model, key, self.log, self.timeout_seconds)
        return self._client

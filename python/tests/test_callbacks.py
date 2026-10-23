import pytest

from transpiler import CallbackRef, CallbackRequest, CallbackResponse
from transpiler.callbacks import CallbackError, dispatch

DECISION = {
    "id": "decision-3",
    "candidates": [{"id": "builtin:display"}, {"id": "builtin:other"}],
}


def request(tmp_path, body: str, function: str = "choose") -> CallbackRequest:
    script = tmp_path / "callbacks.py"
    script.write_text(body)
    return CallbackRequest({**DECISION, "callback": {"script": str(script), "function": function}})


def test_dispatch_returns_validated_selection(tmp_path):
    req = request(tmp_path, "def choose(request):\n    return request.decision['candidates'][1]['id']\n")
    assert dispatch(req) == CallbackResponse("decision-3", "builtin:other")


def test_dispatch_accepts_response_objects(tmp_path):
    req = request(tmp_path, "from transpiler import CallbackResponse\n"
                            "def choose(request):\n    return CallbackResponse('decision-3', 'builtin:display', 'ok')\n")
    assert dispatch(req).rationale == "ok"


@pytest.mark.parametrize("body, message", [
    ("def choose(request):\n    return 'builtin:invented'\n", "does not offer"),
    ("def choose(request):\n    return None\n", "unresolved"),
    ("def choose(request):\n    return 42\n", "expected CallbackResponse"),
    ("x = 1\n", "no callable"),
])
def test_dispatch_rejects_bad_answers(tmp_path, body, message):
    with pytest.raises(CallbackError, match=message):
        dispatch(request(tmp_path, body))


def test_missing_script(tmp_path):
    with pytest.raises(CallbackError, match="not found"):
        dispatch(CallbackRequest({**DECISION, "callback": {"script": str(tmp_path / "nope.py"), "function": "f"}}))


def test_ref_resolution(tmp_path):
    assert CallbackRef("cb.py", "f").resolve(tmp_path).script == str((tmp_path / "cb.py").resolve())
    assert CallbackRef("/abs/cb.py", "f").resolve(tmp_path).script == "/abs/cb.py"

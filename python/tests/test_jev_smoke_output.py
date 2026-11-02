"""Exercise the smoke script's presentation without making a live API call."""

from pathlib import Path
from types import SimpleNamespace

import pytest

import transpiler


def test_smoke_output_without_live_jev(monkeypatch, capsys, tmp_path):
    root = Path(__file__).resolve().parents[2]
    script = (root / "scripts/jev-smoke.sh").read_text()
    code = script.split("python - <<'PY'\n", 1)[1].rsplit("\nPY", 1)[0]
    project = {"about": "sample project", "dialect": {}, "written_for": {"order": "ascii"},
               "main": "Example", "expected_output": ["hello"]}
    monkeypatch.setitem(__import__("sys").modules, "projects", SimpleNamespace(PROJECTS={"demo": project}))
    monkeypatch.setenv("ROOT", str(tmp_path))
    monkeypatch.setenv("JEV_MODEL", "test")
    monkeypatch.setenv("JEV_API_KEY_ENV", "TEST_KEY")
    monkeypatch.setenv("JEV_BASE_URL", "")
    monkeypatch.setenv("JEV_VERBOSE", "1")
    monkeypatch.setattr(transpiler, "JevProvider", lambda *a, **kw: object())
    setting = SimpleNamespace(field="order", value="ascii", resolution=SimpleNamespace(confidence=0.8))
    result = SimpleNamespace(status="ok", ok=True, settings=[setting], decisions=[], libraries_used=[],
                             report={"settings": [{"resolution": {"probabilities": {"ascii": 0.8}}}]},
                             summary=lambda: "status: ok (1 program(s), 1 file(s) written)")

    def translate(*args, **kwargs):
        import sys
        assert kwargs["verbose"] is True
        print("worker log", file=sys.stderr)
        return result

    monkeypatch.setattr(transpiler, "transpile", translate)
    monkeypatch.setattr("subprocess.run", lambda *a, **kw: SimpleNamespace(stdout="hello\n"))
    with pytest.raises(SystemExit) as exit_info:
        exec(compile(code, str(root / "scripts/jev-smoke.sh"), "exec"), {})
    assert exit_info.value.code == 0
    output = capsys.readouterr()
    assert "demo: sample project" in output.out
    assert "Decision probabilities" in output.out
    assert "Jev chose order = 'ascii'" in output.out
    assert "Output:" in output.out and "hello" in output.out
    assert "worker log" in output.err

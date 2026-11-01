import json
from types import SimpleNamespace

from rich.console import Console

from transpiler import TranspileResult, TranspilerError
from transpiler import __main__ as cli
from transpiler.console import print_summary


def test_summary_preserves_text_and_uses_terminal_colors(tmp_path):
    result = TranspileResult(status="incomplete", output=tmp_path, report={}, exit_code=2)
    console = Console(force_terminal=True, color_system="standard", width=120, record=True)
    print_summary(result, console)
    assert console.export_text().strip() == result.summary()
    console = Console(force_terminal=True, color_system="standard", width=120, record=True)
    print_summary(result, console)
    assert "\x1b[" in console.export_text(styles=True)


def test_json_output_stays_parseable_and_logs_go_to_stderr(monkeypatch, capsys, tmp_path):
    pair = SimpleNamespace(default_mappings=lambda: object(), default_layout=lambda: object())
    monkeypatch.setattr(cli, "find_language_pair", lambda target: pair)

    def translate(*args, **kwargs):
        import sys
        print("worker [message]", file=sys.stderr)
        return TranspileResult(status="ok", output=tmp_path, report={"status": "ok"}, exit_code=0)

    monkeypatch.setattr(cli, "transpile", translate)
    assert cli.main(["input.cbl", "-o", str(tmp_path), "--json", "-v"]) == 0
    out = capsys.readouterr()
    assert json.loads(out.out) == {"status": "ok"}
    assert "worker [message]" in out.err
    assert "\x1b[" not in out.err


def test_error_is_literal_and_stays_on_stderr(monkeypatch, capsys, tmp_path):
    def fail(target):
        raise TranspilerError("bad [path]")

    monkeypatch.setattr(cli, "find_language_pair", fail)
    assert cli.main(["input.cbl", "-o", str(tmp_path)]) == 1
    out = capsys.readouterr()
    assert out.out == ""
    assert "error: bad [path]" in out.err

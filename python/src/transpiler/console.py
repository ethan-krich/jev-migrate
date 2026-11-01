"""Human-readable command-line presentation, separate from the Python API and JSON protocol."""

from __future__ import annotations

from rich.console import Console
from rich.text import Text

from .api import TranspileResult


def print_summary(result: TranspileResult, console: Console) -> None:
    """Render the complete API summary with status and diagnostic colors."""
    status_styles = {"ok": "bold green", "incomplete": "bold yellow", "failed": "bold red"}
    lines = result.summary().splitlines()
    for index, line in enumerate(lines):
        stripped = line.strip().lower()
        style = None
        if index == 0:
            style = status_styles.get(result.status, "bold")
        elif stripped.startswith("error") or ": error:" in stripped:
            style = "red"
        elif stripped.startswith("warning") or ": warning:" in stripped:
            style = "yellow"
        elif stripped.startswith("candidate "):
            style = "dim"
        elif any(f": {status} ->" in line for status in ("failed", "incomplete")):
            style = "yellow" if ": incomplete ->" in line else "red"
        console.print(Text(line, style=style), soft_wrap=True)


def print_log(log: str, console: Console) -> None:
    if log:
        console.print("Verbose log", style="bold cyan")
        console.print(Text(log.rstrip("\n")), soft_wrap=True)

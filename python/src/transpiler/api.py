"""Launching translations and examining results.

``transpile()`` writes a job description, runs the C++ orchestrator (``transpiler-core``),
which starts the language pair's persistent worker, and returns a :class:`TranspileResult`
built from the orchestrator's JSON report.
"""

from __future__ import annotations

import json
import os
import shutil
import subprocess
import sys
import tempfile
from collections.abc import Callable, Mapping, Sequence
from dataclasses import dataclass, field
from importlib.metadata import entry_points
from pathlib import Path
from typing import Any, Protocol

from .protocol import IMPORTANCE_TIERS, JOB_VERSION, DecisionRequest, ReviewPriority, importance_table
from .providers import DecisionProvider, DeterministicProvider
from .registry import HandlerCatalog, MappingRegistry

REPO_ROOT = Path(__file__).resolve().parents[3]


class TranspilerError(RuntimeError):
    """The orchestrator could not be run, or rejected the job before translating."""


class Layout(Protocol):
    """A language pair's folder convention (e.g. ``cobol_to_java.Layout``)."""

    def discovery(self) -> dict[str, Any]: ...

    def to_dict(self) -> dict[str, Any]: ...


class Dialect(Protocol):
    """A language pair's project settings (e.g. ``cobol_to_java.Dialect``)."""

    def to_options(self) -> dict[str, Any]: ...


@dataclass(frozen=True)
class LanguagePair:
    """What a language package contributes; registered via the ``transpiler.language_pairs`` entry point."""

    name: str
    source_language: str
    target_language: str
    translator_dir: Path
    default_mappings: Callable[[], MappingRegistry]
    default_layout: Callable[[], Layout]
    catalog: HandlerCatalog
    default_dialect: Callable[[], Dialect] | None = None
    default_importance: Callable[[], Mapping[tuple[str, str], str]] | None = None


def language_pairs() -> dict[str, LanguagePair]:
    pairs = {}
    for ep in entry_points(group="transpiler.language_pairs"):
        pair = ep.load()()
        pairs[pair.name] = pair
    return pairs


def find_language_pair(target: str, source_language: str | None = None, name: str | None = None) -> LanguagePair:
    pairs = language_pairs()
    if name:
        if name not in pairs:
            raise TranspilerError(f"no language pair {name!r} installed (have: {sorted(pairs)})")
        return pairs[name]
    matches = [
        p for p in pairs.values()
        if p.target_language == target and (source_language is None or p.source_language == source_language)
    ]
    if len(matches) != 1:
        raise TranspilerError(
            f"{len(matches)} language pairs produce {target!r}" + (f" from {source_language!r}" if source_language else "")
            + f"; installed: {sorted(pairs)}"
        )
    return matches[0]


@dataclass(frozen=True)
class ProgramResult:
    file: str
    status: str  # translated | incomplete | failed | parsed
    program_id: str | None
    package: str | None
    class_name: str | None
    output_path: str | None
    statements: int
    translated_statements: int
    decisions: tuple[str, ...]
    diagnostics: tuple[dict[str, Any], ...]

    @classmethod
    def from_json(cls, d: dict[str, Any]) -> ProgramResult:
        return cls(
            file=d["file"],
            status=d["status"],
            program_id=d.get("program_id"),
            package=d.get("package"),
            class_name=d.get("class"),
            output_path=d.get("output_path"),
            statements=d.get("statements", 0),
            translated_statements=d.get("translated_statements", 0),
            decisions=tuple(d.get("decisions", [])),
            diagnostics=tuple(d.get("diagnostics", [])),
        )


@dataclass(frozen=True)
class Resolution:
    """What happened to one provider answer: applied by the worker, held by the gate (or the
    context cap), or rejected (not an offered candidate, stale fingerprint, ...)."""

    decision_id: str
    outcome: str  # applied | held | rejected
    candidate_id: str | None
    provider: str | None
    importance: str | None
    gate: str | None
    confidence: float | None
    review_priority: ReviewPriority | None
    rationale: str = ""
    held_reason: str | None = None
    stage: str | None = None  # rejected only: orchestrator | worker
    message: str | None = None

    @property
    def applied(self) -> bool:
        return self.outcome == "applied"

    @classmethod
    def from_json(cls, d: dict[str, Any], outcome: str) -> Resolution:
        rp = d.get("review_priority")
        return cls(
            decision_id=d.get("decision_id", ""),
            outcome=outcome,
            candidate_id=d.get("selection", {}).get("candidate_id"),
            provider=d.get("provider"),
            importance=d.get("importance"),
            gate=d.get("gate"),
            confidence=d.get("confidence"),
            review_priority=ReviewPriority.from_json(rp) if rp else None,
            rationale=d.get("rationale", ""),
            held_reason=d.get("held_reason"),
            stage=d.get("stage"),
            message=d.get("message"),
        )


@dataclass(frozen=True)
class Resolutions:
    provider: str | None
    applied: tuple[Resolution, ...]
    held: tuple[Resolution, ...]
    rejected: tuple[Resolution, ...]

    def __iter__(self):
        return iter(self.applied + self.held + self.rejected)

    def for_decision(self, decision_id: str) -> Resolution | None:
        """The applied answer for a decision, else its held, else its rejected one."""
        for group in (self.applied, self.held, self.rejected):
            for r in reversed(group):
                if r.decision_id == decision_id:
                    return r
        return None


@dataclass(frozen=True)
class ReviewItem:
    """A row of ``review.md``: a decision, what happened to its answer, and the tier-mismatch flag."""

    decision: DecisionRequest
    resolution: Resolution | None

    @property
    def pending(self) -> bool:
        return not self.decision.resolved

    @property
    def tier_mismatch(self) -> bool:
        """review_priority business or higher on a low decision, or cosmetic on a critical one."""
        rp = self.resolution.review_priority if self.resolution else None
        if rp is None:
            return False
        return (rp.level >= 3 and self.decision.importance == "low") or (
            rp.level == 0 and self.decision.importance == "critical")


@dataclass
class TranspileResult:
    status: str  # ok | incomplete | failed
    output: Path
    report: dict[str, Any]
    exit_code: int
    log: str = ""
    programs: list[ProgramResult] = field(default_factory=list)
    decisions: list[DecisionRequest] = field(default_factory=list)

    @property
    def ok(self) -> bool:
        return self.status == "ok"

    @property
    def pending_decisions(self) -> list[DecisionRequest]:
        return [d for d in self.decisions if not d.resolved]

    @property
    def files_written(self) -> list[str]:
        return [f["path"] for f in self.report.get("files_written", [])]

    @property
    def errors(self) -> list[dict[str, Any]]:
        return self.report.get("errors", [])

    @property
    def resolutions(self) -> Resolutions:
        r = self.report.get("resolutions", {})
        return Resolutions(
            provider=r.get("provider"),
            **{kind: tuple(Resolution.from_json(e, kind) for e in r.get(kind, [])) for kind in ("applied", "held", "rejected")},
        )

    def review_queue(self) -> list[ReviewItem]:
        """Every decision with its answer, in ``review.md`` order: pending first, then review
        priority level (highest first, none last), then importance (critical first)."""
        resolutions = self.resolutions
        items = [ReviewItem(d, resolutions.for_decision(d.id)) for d in self.decisions]

        def key(item: ReviewItem) -> tuple[bool, int, int]:
            rp = item.resolution.review_priority if item.resolution else None
            tier = IMPORTANCE_TIERS.index(item.decision.importance) if item.decision.importance in IMPORTANCE_TIERS else -1
            return (not item.pending, -(rp.level if rp else -1), -tier)

        return sorted(items, key=key)

    def raise_for_status(self) -> TranspileResult:
        if not self.ok:
            raise TranspilerError(self.summary())
        return self

    def summary(self) -> str:
        lines = [f"status: {self.status} ({len(self.programs)} program(s), {len(self.files_written)} file(s) written)"]
        for p in self.programs:
            lines.append(f"  {p.file}: {p.status} -> {p.output_path or '-'}")
            lines.extend(f"    {d['severity']}: {d['message']}" for d in p.diagnostics)
        for d in self.pending_decisions:
            lines.append(f"  {d.id} [{d.reason}] {d.source.file}:{d.source.line} {d.operation}: {d.message}")
            lines.extend(f"      candidate {c.id}" for c in d.candidates)
        for e in self.errors:
            lines.append(f"  error ({e.get('stage')}): {e.get('message')}")
        return "\n".join(lines)


def find_core(explicit: str | os.PathLike[str] | None = None) -> Path:
    """Locates the transpiler-core binary: argument, $TRANSPILER_CORE, the repo build dir, PATH."""
    candidates = [explicit, os.environ.get("TRANSPILER_CORE"), REPO_ROOT / "build/core/transpiler-core",
                  shutil.which("transpiler-core")]
    for c in candidates:
        if c and Path(c).is_file() and os.access(c, os.X_OK):
            return Path(c)
    raise TranspilerError("transpiler-core not found; run ./scripts/build.sh or set TRANSPILER_CORE")


def transpile(
    source: str | os.PathLike[str] | Sequence[str | os.PathLike[str]],
    *,
    output: str | os.PathLike[str],
    target: str = "java",
    mappings: MappingRegistry | None = None,
    layout: Layout | None = None,
    dialect: Dialect | None = None,
    importance: Mapping[tuple[str, str], str] | None = None,
    source_root: str | os.PathLike[str] | None = None,
    provider: DecisionProvider | None = None,
    options: dict[str, Any] | None = None,
    copybook_paths: Sequence[str | os.PathLike[str]] = (),
    source_language: str | None = None,
    core: str | os.PathLike[str] | None = None,
    timeout: float | None = None,
    verbose: bool = False,
) -> TranspileResult:
    """Translates source files and writes the target project under ``output``.

    ``source`` is a directory, a file, a glob pattern (``*``, ``?``, ``[..]``, ``**``) or a list
    of those. Output files are written only when every operation was translated; otherwise the
    result is ``incomplete`` and lists the pending decisions. A ``transpile-report.json`` is
    written to ``output`` in every case.

    ``dialect`` holds the project settings the source does not determine (``cobol_to_java.Dialect``);
    without it the language pair's default dialect is used. A key given both by an explicit
    ``dialect`` and in ``options`` is an error; ``options`` may override the default dialect.

    ``importance`` maps ``(reason, operation)`` (``*`` matches anything) to a decision's tier,
    ``low``, ``normal``, ``high`` or ``critical``; without it the language pair's default table
    is used (``cobol_to_java.DEFAULT_IMPORTANCE``).
    """
    if mappings is not None:
        pair = find_language_pair(target, source_language, mappings.language_pair)
    else:
        pair = find_language_pair(target, source_language)
        mappings = pair.default_mappings()
    layout = layout or pair.default_layout()
    provider = provider or DeterministicProvider()

    worker_options = _merge_options(dialect, pair, options or {})
    inputs = [source] if isinstance(source, (str, os.PathLike)) else list(source)
    output_path = Path(output).resolve()
    job = {
        "job_version": JOB_VERSION,
        "translator": pair.name,
        "source_language": pair.source_language,
        "target_language": pair.target_language,
        "inputs": [_absolute_input(i) for i in inputs],
        "source_root": str(Path(source_root).resolve()) if source_root else None,
        "output": str(output_path),
        "translators_dir": str(pair.translator_dir.parent),
        "discovery": layout.discovery(),
        "translator_params": {
            "layout": layout.to_dict(),
            "options": worker_options,
            "copybook_paths": [str(Path(p).resolve()) for p in copybook_paths],
        },
        "mappings": mappings.to_dict(),
        "provider": provider.to_config(),
    }
    if (budget := provider.context_budget()) is not None:
        job["translator_params"]["context"] = budget
    if importance is None and pair.default_importance is not None:
        importance = pair.default_importance()
    if importance is not None:
        try:
            job["translator_params"]["importance"] = importance_table(importance)
        except ValueError as e:
            raise TranspilerError(str(e)) from None
    if timeout is not None:
        job["request_timeout_seconds"] = max(1, int(timeout))

    binary = find_core(core)
    with tempfile.TemporaryDirectory(prefix="transpiler-job-") as tmp:
        job_file = Path(tmp) / "job.json"
        job_file.write_text(json.dumps(job, indent=2))
        args = [str(binary), "run", "--job", str(job_file)] + (["--verbose"] if verbose else [])
        proc = subprocess.run(args, capture_output=True, text=True, timeout=None if timeout is None else timeout * 4)
    if verbose and proc.stderr:
        sys.stderr.write(proc.stderr)
    if proc.returncode not in (0, 1, 2) or not proc.stdout.strip():
        raise TranspilerError(f"transpiler-core exited with {proc.returncode}: {proc.stderr.strip()}")
    report = json.loads(proc.stdout)
    return TranspileResult(
        status=report["status"],
        output=output_path,
        report=report,
        exit_code=proc.returncode,
        log=proc.stderr,
        programs=[ProgramResult.from_json(p) for p in report.get("programs", [])],
        decisions=[DecisionRequest.from_json(d) for d in report.get("decisions", [])],
    )


def _merge_options(dialect: Dialect | None, pair: LanguagePair, options: dict[str, Any]) -> dict[str, Any]:
    if dialect is not None:
        settings = dialect.to_options()
        clash = sorted(set(settings) & set(options))
        if clash:
            raise TranspilerError(f"options {clash} are also set by dialect; set them in one place")
        return {**settings, **options}
    if pair.default_dialect is None:
        return dict(options)
    return {**pair.default_dialect().to_options(), **options}


def _absolute_input(value: str | os.PathLike[str]) -> str:
    text = os.fspath(value)
    return text if os.path.isabs(text) else os.path.join(os.getcwd(), text)

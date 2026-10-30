"""Project settings that COBOL source alone does not determine.

The same program behaves differently under different compilers and run-time configurations
(the character set behind HIGH-VALUES, what an out-of-range subscript does, how binary items
truncate). The translator never guesses these: a program that needs a setting left at ``None``
gets one ``missing_mapping`` decision named ``dialect:<field>`` and is not written.

    from cobol_to_java import Dialect
    transpile("./legacy", output="./out", dialect=Dialect(encoding="ascii", out_of_range="fail"))
"""

from __future__ import annotations

import re
from collections.abc import Mapping
from dataclasses import dataclass, field
from types import MappingProxyType
from typing import Any

ENCODINGS = ("ascii", "ebcdic")
COLLATING_SEQUENCES = ("native", "ascii", "ebcdic")
OUT_OF_RANGE = ("fail", "decision")
SOURCE_FORMATS = ("fixed", "variable", "tandem", "free")
DYNAMIC_CALLS = ("project",)
_WHEN_COMPILED = re.compile(r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}$")


@dataclass(frozen=True)
class Dialect:
    """Compiler and run-time settings for one translation project.

    * ``encoding``: the character set the original programs ran with (``ascii`` or ``ebcdic``);
      decides the bytes of HIGH-VALUES / LOW-VALUES and of class tests.
    * ``collating_sequence``: the order of alphanumeric comparisons (``native`` follows
      ``encoding``).
    * ``out_of_range``: what a subscript or reference modification outside its table does:
      ``fail`` stops the run unit as GnuCOBOL does with run-time checks, ``decision`` refuses to
      translate any access that cannot be proven in range.
    * ``binary_truncate``: COMP/BINARY values are truncated to their PICTURE (GnuCOBOL
      ``-fbinary-truncate``, the default).
    * ``sync_alignment``: whether SYNCHRONIZED inserts slack bytes before binary items.
    * ``debugging_lines``: compile lines with ``D`` in the indicator area.
    * ``source_format``: ``fixed`` (columns 8-72), ``variable``, ``tandem`` or ``free`` (GnuCOBOL's ``-free``).
    * ``file_assignments``: ``ASSIGN TO`` name -> path used at run time.
    * ``when_compiled``: fixed value of the WHEN-COMPILED register, ``YYYY-MM-DDTHH:MM:SS``.
    * ``dynamic_calls``: what ``CALL identifier`` can reach. ``project``: the programs of this
      translation project and nothing else (any other name is a program that is not found).
    """

    encoding: str | None = None
    collating_sequence: str | None = None
    out_of_range: str | None = None
    binary_truncate: bool = True
    sync_alignment: bool | None = None
    debugging_lines: bool = False
    source_format: str = "fixed"
    file_assignments: Mapping[str, str] = field(default_factory=lambda: MappingProxyType({}), hash=False)
    when_compiled: str | None = None
    dynamic_calls: str | None = None

    def __post_init__(self) -> None:
        problems = []
        _choice(problems, "encoding", self.encoding, ENCODINGS, optional=True)
        _choice(problems, "collating_sequence", self.collating_sequence, COLLATING_SEQUENCES, optional=True)
        _choice(problems, "out_of_range", self.out_of_range, OUT_OF_RANGE, optional=True)
        _choice(problems, "source_format", self.source_format, SOURCE_FORMATS, optional=False)
        _choice(problems, "dynamic_calls", self.dynamic_calls, DYNAMIC_CALLS, optional=True)
        for name in ("binary_truncate", "debugging_lines"):
            if not isinstance(getattr(self, name), bool):
                problems.append(f"{name} must be a bool")
        if self.sync_alignment is not None and not isinstance(self.sync_alignment, bool):
            problems.append("sync_alignment must be a bool or None")
        if not isinstance(self.file_assignments, Mapping) or not all(
                isinstance(k, str) and k and isinstance(v, str) and v for k, v in self.file_assignments.items()):
            problems.append("file_assignments must map non-empty names to non-empty paths")
        if self.when_compiled is not None and (not isinstance(self.when_compiled, str)
                                               or not _WHEN_COMPILED.match(self.when_compiled)):
            problems.append("when_compiled must be 'YYYY-MM-DDTHH:MM:SS'")
        if problems:
            raise ValueError("invalid Dialect: " + "; ".join(problems))
        # Freeze a caller's dict so the dataclass stays hashable and immutable.
        object.__setattr__(self, "file_assignments", MappingProxyType(dict(self.file_assignments)))

    def to_options(self) -> dict[str, Any]:
        """The worker ``initialize`` options for these settings (unset ones are ``None``)."""
        return {
            "encoding": self.encoding,
            "collating_sequence": self.collating_sequence,
            "out_of_range": self.out_of_range,
            "binary_truncate": self.binary_truncate,
            "sync_alignment": self.sync_alignment,
            "debugging_lines": self.debugging_lines,
            "source_format": self.source_format,
            "file_assignments": dict(self.file_assignments),
            "when_compiled": self.when_compiled,
            "dynamic_calls": self.dynamic_calls,
        }


def _choice(problems: list[str], name: str, value: object, allowed: tuple[str, ...], *, optional: bool) -> None:
    if value is None and optional:
        return
    if value not in allowed:
        problems.append(f"{name} must be one of {allowed}" + (" or None" if optional else "") + f", not {value!r}")


def default_dialect() -> Dialect:
    """Fixed-format source with every compiler- or site-specific setting left unset."""
    return Dialect()

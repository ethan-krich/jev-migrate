"""COBOL parts mapped to Java libraries already installed in the target project.

    from cobol_to_java import Cobol, Java, Library, libraries

    libs = libraries()
    libs.add(Library(id="acme.dates", cobol=Cobol.path("common/dates/"),
                     java=Java.maven("com.acme:acme-dates:2.3.1")))
    transpile("src/", output="out/", libraries=libs, provider=JevProvider(...))

The declarations are data only: the worker reads every public constructor and method of the Java
library itself and offers calls into it at each use of a claimed COBOL part, next to the exact
translation (``cobol:translate``). A library use is **not** equivalence-checked against the
original COBOL; every one is a decision and is flagged in the Java, the report, ``review.md`` and
on the console.
"""

from __future__ import annotations

import re
from collections.abc import Hashable, Iterable, Mapping, Sequence
from dataclasses import dataclass, field
from pathlib import Path
from types import MappingProxyType
from typing import Any

from transpiler.libraries import LibraryRegistry

from .defaults import LANGUAGE_PAIR

# group:artifact:version (no classifier or packaging; the sources jar is found next to the jar).
_MAVEN = re.compile(r"^[A-Za-z0-9_.\-]+:[A-Za-z0-9_.\-]+:[A-Za-z0-9_.\-+]+$")
# A Java name pattern for `expose`: a.b.C, a.b.*, a.b.C.Inner.
_JAVA_PATTERN = re.compile(r"^[A-Za-z_$][\w$]*(\.[A-Za-z_$][\w$]*)*(\.\*)?$")
_SELECTOR_KINDS = ("program", "path", "operation", "copybook", "paragraph")


@dataclass(frozen=True)
class CobolSelector:
    """One COBOL part a library claims; made with the :class:`Cobol` factories."""

    kind: str
    names: tuple[str, ...] = ()
    path: str | None = None
    program: str | None = None

    def claims(self) -> list[Hashable]:
        match self.kind:
            case "path":
                return [("path", self.path)]
            case "paragraph":
                return [("paragraph", n, self.program) for n in self.names]
            case _:
                return [(self.kind, n) for n in self.names]

    def problems(self) -> list[str]:
        if self.kind not in _SELECTOR_KINDS:
            return [f"unknown COBOL selector kind {self.kind!r}"]
        if self.kind == "path":
            if not self.path or Path(self.path).is_absolute():
                return [f"Cobol.path({self.path!r}) must be a non-empty path relative to the source root"]
            return []
        if not self.names or any(not n.strip() for n in self.names):
            return [f"Cobol.{self.kind}() needs at least one non-empty name"]
        return []

    def to_dict(self) -> dict[str, Any]:
        if self.kind == "path":
            return {"kind": "path", "pattern": self.path}
        d: dict[str, Any] = {"kind": self.kind, "names": list(self.names)}
        if self.program is not None:
            d["program"] = self.program
        return d


class Cobol:
    """Factories for the COBOL side of a :class:`Library`."""

    @staticmethod
    def program(*names: str) -> CobolSelector:
        """Programs called by PROGRAM-ID (``CALL 'DATEFMT'``), whether or not they are in the project."""
        return CobolSelector("program", tuple(n.strip() for n in names))

    @staticmethod
    def path(pattern: str) -> CobolSelector:
        """Every program in a directory (``common/dates/``), a single file, or a glob, relative to the source root."""
        return CobolSelector("path", path=str(pattern))

    @staticmethod
    def operation(*names: str) -> CobolSelector:
        """A verb, intrinsic function or system routine (``FUNCTION INTEGER-OF-DATE``, ``CALL 'CBL_TOUPPER'``)."""
        return CobolSelector("operation", tuple(" ".join(n.upper().split()) for n in names))

    @staticmethod
    def copybook(*names: str) -> CobolSelector:
        """A copybook: its procedure code at each COPY, or a data record that library calls take and return."""
        return CobolSelector("copybook", tuple(n.strip().upper() for n in names))

    @staticmethod
    def paragraph(*names: str, program: str | None = None) -> CobolSelector:
        """A paragraph or section at each PERFORM of it; ``program`` limits it to one PROGRAM-ID."""
        return CobolSelector("paragraph", tuple(n.strip().upper() for n in names),
                             program=program.strip().upper() if program else None)


@dataclass(frozen=True)
class JavaSource:
    """Where the Java library's source is read from; made with the :class:`Java` factories."""

    kind: str  # maven | path | jar
    coordinates: str | None = None
    path: Path | None = None

    def problems(self) -> list[str]:
        match self.kind:
            case "maven":
                if not self.coordinates or not _MAVEN.match(self.coordinates):
                    return [f"Java.maven({self.coordinates!r}) must be 'group:artifact:version'"]
                return []
            case "path":
                if self.path is None or not self.path.exists():
                    return [f"Java.path({str(self.path)!r}) does not exist"]
                if self.path.is_file() and self.path.suffix != ".java":
                    return [f"Java.path({str(self.path)!r}) must be a source directory or a .java file"]
                return []
            case "jar":
                if self.path is None or not self.path.is_file():
                    return [f"Java.jar({str(self.path)!r}) is not a file"]
                return []
        return [f"unknown Java source kind {self.kind!r}"]

    def to_dict(self) -> dict[str, Any]:
        if self.kind == "maven":
            return {"kind": "maven", "coordinates": self.coordinates}
        return {"kind": self.kind, "path": str(self.path)}

    def __str__(self) -> str:
        return self.coordinates if self.kind == "maven" else str(self.path)


class Java:
    """Factories for the Java side of a :class:`Library`. Nothing is downloaded: a Maven / Gradle
    dependency must already be installed (with its sources jar) in the local caches."""

    @staticmethod
    def maven(coordinates: str) -> JavaSource:
        """An installed dependency, ``group:artifact:version``, found in ``~/.m2`` or Gradle's cache."""
        return JavaSource("maven", coordinates=coordinates.strip())

    @staticmethod
    def path(path: str | Path) -> JavaSource:
        """A source directory (``vendor/acme-dates/src/main/java``) or a single ``.java`` file."""
        return JavaSource("path", path=Path(path).expanduser().resolve())

    @staticmethod
    def jar(path: str | Path) -> JavaSource:
        """A sources jar anywhere on disk."""
        return JavaSource("jar", path=Path(path).expanduser().resolve())


@dataclass(frozen=True)
class Library:
    """A COBOL part implemented by a Java library.

    ``expose`` limits which classes are offered (``com.acme.dates.*``, ``com.acme.dates.Dates``);
    ``hints`` steers one COBOL part (a PROGRAM-ID, operation, copybook or paragraph name) to
    given methods (``"com.acme.dates.Dates.format(String,int)"``); ``max_chain`` caps how many
    calls one use may chain (``LocalDate.of(..).plusDays(..)`` is two).
    """

    id: str
    cobol: CobolSelector | Sequence[CobolSelector]
    java: JavaSource
    expose: Sequence[str] = ()
    hints: Mapping[str, Sequence[str]] = field(default_factory=lambda: MappingProxyType({}))
    max_chain: int = 4

    @property
    def selectors(self) -> tuple[CobolSelector, ...]:
        return (self.cobol,) if isinstance(self.cobol, CobolSelector) else tuple(self.cobol)

    def claims(self) -> Iterable[Hashable]:
        for s in self.selectors:
            yield from s.claims()

    def problems(self) -> list[str]:
        problems: list[str] = []
        if not self.selectors:
            problems.append("cobol= needs at least one selector")
        for s in self.selectors:
            if not isinstance(s, CobolSelector):
                problems.append(f"cobol= takes Cobol.program/path/operation/copybook/paragraph, not {s!r}")
            else:
                problems.extend(s.problems())
        if not isinstance(self.java, JavaSource):
            problems.append(f"java= takes Java.maven/path/jar, not {self.java!r}")
        else:
            problems.extend(self.java.problems())
        problems.extend(f"expose pattern {p!r} must be a class name or a package followed by .*"
                        for p in self.expose if not _JAVA_PATTERN.match(p))
        for part, methods in self.hints.items():
            if isinstance(methods, str) or not methods or not all(isinstance(m, str) and m for m in methods):
                problems.append(f"hints[{part!r}] must be a non-empty list of method ids")
        if not isinstance(self.max_chain, int) or not 1 <= self.max_chain <= 8:
            problems.append(f"max_chain must be an integer from 1 to 8, not {self.max_chain!r}")
        return problems

    def to_dict(self) -> dict[str, Any]:
        d: dict[str, Any] = {"id": self.id, "source": [s.to_dict() for s in self.selectors],
                             "target": self.java.to_dict(), "max_chain": self.max_chain}
        if self.expose:
            d["expose"] = list(self.expose)
        if self.hints:
            d["hints"] = {k: list(v) for k, v in self.hints.items()}
        return d


def libraries(caches: Mapping[str, str | Path] | None = None) -> LibraryRegistry:
    """A new, empty library registry for COBOL-to-Java. ``caches`` overrides where installed
    packages are looked up: ``{"maven": "~/.m2/repository", "gradle": "~/.gradle/caches/modules-2/files-2.1"}``."""
    return LibraryRegistry(LANGUAGE_PAIR, dict(caches or {}))

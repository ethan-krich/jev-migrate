"""Library declarations: parts of the source program that correspond to a target-language library.

A *library* says that a part of the source (programs, operations, copybooks, paragraphs, as the
language pair defines them) is implemented by a library already installed in the target project.
When the source uses that part, the worker offers calls into the library as candidates of a
``library_call`` decision, next to the exact translation. Library uses are never chosen
silently and are always flagged: their behaviour is **not** checked for equivalence with the
original.

This module is the language-agnostic container: a :class:`LibraryRegistry` holds language-pair
specs (``cobol_to_java.Library``), checks what can be checked without parsing (unique ids, each
part claimed by one library, paths that exist), and serialises everything to plain JSON
(``protocol/library.schema.json``) for the worker's ``initialize``. Python never interprets the
source or target language: the worker checks what needs the parse and rejects the whole set with
``invalid_library``.
"""

from __future__ import annotations

import os
from collections.abc import Hashable, Iterable, Iterator
from pathlib import Path
from typing import Any, Protocol

FORMAT_VERSION = 1


class LibraryError(ValueError):
    """A library set failed validation. ``problems`` lists every issue found."""

    def __init__(self, problems: list[str]):
        self.problems = problems
        super().__init__("; ".join(problems))


class LibrarySpec(Protocol):
    """What a language pair's library declaration provides to the registry."""

    id: str

    def claims(self) -> Iterable[Hashable]:
        """Keys of the source parts the library claims; no two libraries may share one."""
        ...

    def problems(self) -> list[str]:
        """Shape problems found without parsing (paths that do not exist, malformed names)."""
        ...

    def to_dict(self) -> dict[str, Any]: ...


def default_caches() -> dict[str, str]:
    """Where installed dependencies are looked up (never downloaded): the Maven local repository
    and Gradle's module cache, honouring ``MAVEN_REPOSITORY`` and ``GRADLE_USER_HOME``."""
    home = Path.home()
    maven = os.environ.get("MAVEN_REPOSITORY") or str(home / ".m2/repository")
    gradle_home = os.environ.get("GRADLE_USER_HOME") or str(home / ".gradle")
    return {"maven": str(Path(maven).expanduser().resolve()),
            "gradle": str((Path(gradle_home).expanduser() / "caches/modules-2/files-2.1").resolve())}


class LibraryRegistry:
    """An ordered, mutable set of library declarations for one language pair.

    ``caches`` overrides where installed packages are looked up (``{"maven": dir, "gradle":
    dir}``); by default :func:`default_caches`.
    """

    def __init__(self, language_pair: str, caches: dict[str, str | os.PathLike[str]] | None = None):
        self.language_pair = language_pair
        self._libraries: list[LibrarySpec] = []
        self.caches = {k: str(Path(v).expanduser().resolve()) for k, v in (caches or {}).items()}

    def __iter__(self) -> Iterator[LibrarySpec]:
        return iter(list(self._libraries))

    def __len__(self) -> int:
        return len(self._libraries)

    def __contains__(self, library_id: object) -> bool:
        return any(lib.id == library_id for lib in self._libraries)

    def __repr__(self) -> str:
        return f"LibraryRegistry({self.language_pair!r}, {[lib.id for lib in self._libraries]})"

    def get(self, library_id: str) -> LibrarySpec:
        for lib in self._libraries:
            if lib.id == library_id:
                return lib
        raise KeyError(library_id)

    def add(self, library: LibrarySpec) -> LibrarySpec:
        """Adds a library; raises :class:`LibraryError` if the set would be invalid."""
        problems = self._problems([*self._libraries, library])
        if problems:
            raise LibraryError(problems)
        self._libraries.append(library)
        return library

    def remove(self, library_id: str) -> LibrarySpec:
        lib = self.get(library_id)
        self._libraries.remove(lib)
        return lib

    def validate(self) -> None:
        problems = self._problems(self._libraries)
        if problems:
            raise LibraryError(problems)

    @staticmethod
    def _problems(libraries: list[LibrarySpec]) -> list[str]:
        problems: list[str] = []
        ids: set[str] = set()
        claimed: dict[Hashable, str] = {}
        for lib in libraries:
            if not lib.id or not isinstance(lib.id, str):
                problems.append("a library needs a non-empty string id")
            elif lib.id in ids:
                problems.append(f"library {lib.id!r}: duplicate id")
            ids.add(lib.id)
            problems.extend(f"library {lib.id!r}: {p}" for p in lib.problems())
            for key in lib.claims():
                owner = claimed.setdefault(key, lib.id)
                if owner != lib.id:
                    problems.append(f"library {lib.id!r}: {_describe(key)} is already claimed by library {owner!r}")
        return problems

    def to_dict(self) -> dict[str, Any]:
        """The ``initialize.libraries`` object (``protocol/library.schema.json``)."""
        self.validate()
        return {"version": FORMAT_VERSION, "libraries": [lib.to_dict() for lib in self._libraries],
                "caches": {**default_caches(), **self.caches}}


def _describe(key: Hashable) -> str:
    if isinstance(key, tuple):
        return " ".join(str(k) for k in key if k is not None)
    return str(key)

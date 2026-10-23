"""The COBOL-to-Java folder convention.

Given a source root ``legacy/`` and ``base_package="com.example.legacy"``::

    legacy/billing/PAY-CALC.cbl  (PROGRAM-ID. PAY-CALC)
      -> <output>/src/main/java/com/example/legacy/billing/PayCalc.java
    legacy/reports/month-end/RPT01.cob
      -> <output>/src/main/java/com/example/legacy/reports/month_end/Rpt01.java
    legacy/billing/copy/ACCT-REC.cpy -> copybook, never a class

Rules (applied by the worker, which owns Java naming):

* every directory below the source root becomes a package segment: lower-cased, characters that
  are not letters or digits become ``_``, Java keywords get a ``_`` suffix, a leading digit gets a
  ``_`` prefix; ``package_overrides`` maps a relative directory to an explicit package;
* the class name is PROGRAM-ID in PascalCase (``PAY-CALC`` -> ``PayCalc``);
* two programs that map to the same class (case-insensitively) are a hard error; nothing is
  renamed silently;
* copybooks are files with a ``copybook_extensions`` extension or inside a directory named in
  ``copybook_dirs``; a program finds them in its own directory, then in ``copybook_dirs`` of each
  directory up to the source root, then in extra ``copybook_paths``;
* the runtime support classes are written to ``runtime_package`` (default
  ``<base_package>.runtime``) so the output compiles with plain ``javac``.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any


@dataclass
class Layout:
    base_package: str = "generated"
    java_source_dir: str = "src/main/java"
    program_extensions: tuple[str, ...] = (".cbl", ".cob")
    copybook_extensions: tuple[str, ...] = (".cpy", ".copy")
    copybook_dirs: tuple[str, ...] = ("copy", "copybooks")
    runtime_package: str | None = None
    package_overrides: dict[str, str] = field(default_factory=dict)
    exclude: tuple[str, ...] = ()

    def discovery(self) -> dict[str, Any]:
        """Language-neutral file classification rules for the orchestrator."""
        return {
            "program_extensions": list(self.program_extensions),
            "copybook_extensions": list(self.copybook_extensions),
            "copybook_dirs": list(self.copybook_dirs),
            "exclude": list(self.exclude),
        }

    def to_dict(self) -> dict[str, Any]:
        """The ``layout`` parameters of the worker's ``initialize`` request."""
        return {
            "base_package": self.base_package,
            "java_source_dir": self.java_source_dir,
            "copybook_dirs": list(self.copybook_dirs),
            "copybook_extensions": list(self.copybook_extensions),
            "runtime_package": self.runtime_package,
            "package_overrides": dict(self.package_overrides),
        }

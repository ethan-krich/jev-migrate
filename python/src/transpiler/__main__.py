"""Command line: python -m transpiler SOURCE... --output DIR [options]

--mappings FILE.py loads a script defining ``configure(mappings)``, which receives the language
pair's default registry and may modify it in place or return a replacement.
"""

from __future__ import annotations

import argparse
import contextlib
import dataclasses
import importlib.util
import io
import json
import sys
from pathlib import Path

from rich.console import Console
from rich.text import Text

from .api import TranspilerError, find_language_pair, transpile
from .console import print_log, print_summary


def load_configure(path: Path):
    spec = importlib.util.spec_from_file_location("user_mappings", path)
    if spec is None or spec.loader is None:
        raise TranspilerError(f"cannot load {path}")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    if not callable(getattr(module, "configure", None)):
        raise TranspilerError(f"{path} must define configure(mappings)")
    return module.configure


def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser(prog="python -m transpiler", description="Translate source code deterministically.")
    p.add_argument("source", nargs="+", help="files, directories or glob patterns (quote patterns)")
    p.add_argument("--output", "-o", required=True)
    p.add_argument("--target", default="java")
    p.add_argument("--source-root", help="root for package/folder mapping (default: common directory of inputs)")
    p.add_argument("--base-package", help="Java base package (layout.base_package)")
    p.add_argument("--mappings", type=Path, help="Python file with configure(mappings)")
    p.add_argument("--copybook-path", action="append", default=[], help="extra copybook directory (repeatable)")
    p.add_argument("--json", action="store_true", help="print the full JSON report")
    p.add_argument("--verbose", "-v", action="store_true")
    args = p.parse_args(argv)

    stderr = Console(stderr=True)
    log = io.StringIO()
    try:
        pair = find_language_pair(args.target)
        mappings = pair.default_mappings()
        if args.mappings:
            configured = load_configure(args.mappings)(mappings)
            if configured is not None:
                mappings = configured
        layout = pair.default_layout()
        if args.base_package:
            layout = dataclasses.replace(layout, base_package=args.base_package)
        with contextlib.redirect_stderr(log):
            result = transpile(args.source, output=args.output, target=args.target, mappings=mappings, layout=layout,
                               source_root=args.source_root, copybook_paths=args.copybook_path, verbose=args.verbose)
    except TranspilerError as e:
        stderr.print(Text(f"error: {e}", style="bold red"), soft_wrap=True)
        return 1
    finally:
        print_log(log.getvalue(), stderr)
    if args.json:
        print(json.dumps(result.report, indent=2))
    else:
        print_summary(result, Console())
    return {"ok": 0, "incomplete": 2}.get(result.status, 1)


if __name__ == "__main__":
    sys.exit(main())

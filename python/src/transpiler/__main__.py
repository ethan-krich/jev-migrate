"""Command line: python -m transpiler SOURCE... --output DIR [options]

--mappings FILE.py loads a script defining ``configure(mappings)``, which receives the language
pair's default registry and may modify it in place or return a replacement.

--jev MODEL asks Jev (the live API) to resolve the decisions the default rules leave open; the key
is read from the variable --jev-api-key-env names. Without --jev the run is deterministic: a
decision without a rule stays pending and nothing is written.
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
    p = argparse.ArgumentParser(prog="python -m transpiler", description="Translate source code.")
    p.add_argument("source", nargs="+", help="files, directories or glob patterns (quote patterns)")
    p.add_argument("--output", "-o", required=True)
    p.add_argument("--target", default="java")
    p.add_argument("--source-root", help="root for package/folder mapping (default: common directory of inputs)")
    p.add_argument("--base-package", help="Java base package (layout.base_package)")
    p.add_argument("--mappings", type=Path, help="Python file with configure(mappings)")
    p.add_argument("--copybook-path", action="append", default=[], help="extra copybook directory (repeatable)")
    p.add_argument("--json", action="store_true", help="print the full JSON report")
    p.add_argument("--verbose", "-v", action="store_true")
    jev = p.add_argument_group("Jev (live API)")
    jev.add_argument("--jev", metavar="MODEL", help="resolve open decisions with Jev, e.g. jev-latest or jev-preview")
    jev.add_argument("--jev-api-key-env", default="TYPESAFE_API_KEY", metavar="VAR",
                     help="environment variable holding the API key (default: TYPESAFE_API_KEY)")
    jev.add_argument("--jev-base-url", metavar="URL", help="API base URL (default: TypeSafe's own API)")
    jev.add_argument("--jev-timeout", type=float, default=120.0, metavar="SECONDS",
                     help="timeout of each API call (default: 120)")
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
        provider = None
        if args.jev:
            from .providers import JevProvider

            provider = JevProvider(args.jev, api_key_env=args.jev_api_key_env, base_url=args.jev_base_url,
                                   timeout_seconds=args.jev_timeout)
        layout = pair.default_layout()
        if args.base_package:
            layout = dataclasses.replace(layout, base_package=args.base_package)
        with contextlib.redirect_stderr(log):
            result = transpile(args.source, output=args.output, target=args.target, mappings=mappings, layout=layout,
                               source_root=args.source_root, copybook_paths=args.copybook_path, verbose=args.verbose,
                               provider=provider)
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

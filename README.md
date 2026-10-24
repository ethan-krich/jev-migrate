# jev-migrate

A modular transpiler for researching hybrid deterministic + probabilistic code translation.
**Phase 1** is a working, tested, *deterministic* COBOL-to-Java translator. Operations that cannot
be translated reliably come back as structured **decisions**, which Phase 2 will route to
[Jev](docs/architecture.md#phase-2-jev). Phase 1 makes no AI or network calls at runtime.

```
Python API (transpiler, cobol_to_java)  ──job──▶  C++ orchestrator (transpiler-core)
                                                      │  NDJSON over stdin/stdout
                                                      ▼
                                        Java worker (ProLeap ➜ rules ➜ JavaParser)
```

- **Python** owns the public API, the mutable mapping registry and the folder convention.
- **C++** discovers files, drives persistent translator workers, routes and validates decisions.
  It knows nothing about COBOL or Java.
- **Java** parses COBOL, applies mapping rules, builds Java ASTs, and writes the output project.

## Prerequisites

| Tool | Version | Used for |
|---|---|---|
| JDK | 21+ | Java worker, compiling generated code |
| CMake + C++ compiler | CMake 3.28+, clang 17+ / GCC 14+ (C++23) | orchestrator |
| [uv](https://docs.astral.sh/uv/) | 0.11+ | Python 3.11+ workspace |
| git | any | ProLeap parser submodule |
| GnuCOBOL | 3.2 (optional) | live behavioural-equivalence tests |
| Docker | optional | pinned toolchain (`docker/Dockerfile`) |

Gradle needs no install: the wrapper is pinned to 9.3.1 with a checksum. Without GnuCOBOL, the
equivalence tests compare against output recorded from GnuCOBOL 3.2.

## Setup, build, test

```bash
git clone --recurse-submodules <repo> && cd jev-migrate
./scripts/setup.sh     # checks tools, inits submodule, uv sync, fetches Gradle deps
./scripts/build.sh     # CMake (core), Gradle installDist (worker), uv sync
./scripts/test.sh      # GoogleTest via CTest, JUnit 5, pytest (unit, integration, equivalence)
./scripts/run.sh       # translates, compiles and runs examples/cobol-to-java
```

The scripts work from any directory. `./scripts/test.sh java` (or `cpp`, `python`) runs a single
suite; arguments after `--` go to pytest.

**Pinned toolchain.** `./scripts/docker.sh test` (or `build`, `run`, `shell`) runs the same
scripts in a slim Debian image with every tool version pinned, including GnuCOBOL 3.2. That is the
reference environment for equivalence results.

## A first translation

```python
from transpiler import transpile
from cobol_to_java import Layout, default_mappings

mappings = default_mappings()            # a fresh, independent, mutable registry
mappings.remove("DISPLAY")               # every DISPLAY now becomes a decision
mappings.add("CALL", strategy="unresolved", when={"using": True}, priority=200)

result = transpile(
    "examples/cobol-to-java/input",      # directory, file, glob ("src/**/*.cbl") or list
    target="java",
    output="generated",
    mappings=mappings,
    layout=Layout(base_package="com.example.legacy"),
)
print(result.summary())
```

```
status: incomplete (2 program(s), 0 file(s) written)
  payroll/PAYROLL.cbl: incomplete -> src/main/java/com/example/legacy/payroll/Payroll.java
  ...
  decision-1 [missing_mapping] payroll/PAYROLL.cbl:21 DISPLAY: no mapping rule matches DISPLAY ...
      candidate builtin:display
```

Nothing is written while any decision is pending, and a `generated/transpile-report.json` is
always written. To resolve decisions offline, pass validated selections:

```python
from transpiler import DecisionResponse, StaticProvider
answers = [DecisionResponse("builtin:display", fingerprint=d.fingerprint) for d in result.pending_decisions]
result = transpile("examples/cobol-to-java/input", output="generated", mappings=mappings,
                   layout=Layout(base_package="com.example.legacy"), provider=StaticProvider(answers))
assert result.ok   # generated/src/main/java/com/example/legacy/... compiles with plain javac
```

From the shell: `./scripts/run.sh examples/cobol-to-java/input -o generated --base-package com.acme`
(`--mappings file.py` loads a `configure(mappings)` hook).

## Output layout

Directories under the source root become packages, and PROGRAM-ID becomes the class:

```
legacy/billing/PAY-CALC.cbl         -> generated/src/main/java/<base>/billing/PayCalc.java
legacy/reports/month-end/RPT01.cob  -> generated/src/main/java/<base>/reports/month_end/Rpt01.java
legacy/billing/copy/ACCT.cpy        -> copybook (inlined, never a class)
                                       generated/src/main/java/<base>/runtime/*.java  (support library)
```

The full rules are in [docs/mapping-api.md](docs/mapping-api.md#folder-layout).

## Supported COBOL (Phase 1)

The subset is deliberately small and exact. Anything outside it becomes a decision; nothing is
approximated or dropped. The behaviour below is checked against GnuCOBOL 3.2 by the
equivalence fixtures.

| | Construct | Notes |
|---|---|---|
| ✅ | IDENTIFICATION / DATA / PROCEDURE divisions, WORKING-STORAGE | one program per file, fixed format |
| ✅ | `PIC X(n)`, `PIC [S]9(i)[V9(s)]` USAGE DISPLAY, levels 01–49 and 77, `VALUE` literal / ZERO / SPACE | default init: spaces / zero |
| ✅ | `MOVE` (alphanumeric pad/truncate; numeric decimal alignment, high-/low-order truncation, sign drop; integer numeric → alphanumeric digits; figurative ZERO/SPACE) | per receiver |
| ✅ | `ADD`, `SUBTRACT`, `MULTIPLY`, `DIVIDE` (TO/FROM/BY/INTO/GIVING), `ROUNDED`, `[NOT] ON SIZE ERROR` | exact `BigDecimal`; divide by zero is a size error |
| ✅ | `COMPUTE` with `+ - *`, unary minus, parentheses | |
| ✅ | `DISPLAY` literals and items, `WITH NO ADVANCING` | GnuCOBOL format (`+005`, `012.34`) |
| ✅ | `IF`/`ELSE`, relation (`= < > <= >= NOT`), sign (`POSITIVE`/`NEGATIVE`/`ZERO`), `AND`/`OR`/`NOT`, parentheses | numeric, alphanumeric (space padded), figurative |
| ✅ | Paragraphs, sections, fall-through, `PERFORM` proc / inline, `TIMES`, `UNTIL`, single `VARYING`, `WITH TEST AFTER` | |
| ✅ | `STOP RUN`, `GOBACK`, `EXIT`, `EXIT PROGRAM`, `CONTINUE`, `RETURN-CODE` → exit status | |
| ✅ | `COPY` (copybook search path; decision lines map back to the original file), static `CALL 'literal'` of a program in the project (no USING) | persistent WORKING-STORAGE, RETURN-CODE propagation |
| ◐ | Group items | declared (children become fields); referencing the group itself is unsupported |
| ◐ | `REPLACE` statements | parsed by ProLeap; line numbers then fall back to the preprocessed source (warning `approximate_line_numbers`) |
| ❌ | `OCCURS`, `REDEFINES`, `RENAMES`, level 88, `USAGE` other than DISPLAY, edited / `P` / `A` / `N` pictures, `SIGN`, `JUSTIFIED` | `unsupported_construct` |
| ❌ | `/` and `**` in COMPUTE (implementation-defined intermediate precision), `DIVIDE … REMAINDER`, `CORRESPONDING` | `unsupported_construct` |
| ❌ | `EVALUATE`, `GO TO`, `PERFORM … THRU`, `STRING`, `INSPECT`, `ACCEPT`, files, `CALL … USING`, dynamic `CALL`, `NEXT SENTENCE`, `DISPLAY UPON` | `missing_mapping` or `unsupported_construct` |
| ❌ | Tables, reference modification, intrinsic functions, special registers other than RETURN-CODE, free format / other dialects | |

Known divergence: GnuCOBOL 3.2 prints an empty value for a `COMPUTE` receiver when it is displayed
*inside that statement's own* `ON SIZE ERROR` phrase. The translation prints the (unchanged) value.
The fixtures avoid that pattern.

## Repository map

| Path | What |
|---|---|
| `core/` | C++23 orchestrator, GoogleTest suite, scriptable fake worker |
| `translators/cobol-to-java/java/` | Gradle build: worker, `runtime/` support library, `proleap/` parser build |
| `translators/cobol-to-java/python/` | `cobol_to_java`: default mappings, `Layout`, language-pair registration |
| `translators/cobol-to-java/fixtures/` | COBOL fixtures + recorded GnuCOBOL output |
| `python/` | `transpiler`: public API, registry, providers, callbacks, protocol types |
| `protocol/` | JSON schemas: worker messages, mappings, decisions |
| `tests/` | integration, equivalence, benchmarks (pytest) |
| `third_party/proleap-cobol-parser` | ProLeap 4.0.0 submodule (MIT), pinned commit |

Documentation: [architecture](docs/architecture.md) · [mapping API](docs/mapping-api.md) ·
[worker protocol](docs/worker-protocol.md) · [AGENTS.md](AGENTS.md)

## Third-party components

| Component | Version | License | Use |
|---|---|---|---|
| ProLeap COBOL Parser | 4.0.0 (commit `d1bfe75`) | MIT | COBOL parsing and semantic graph (built from the submodule) |
| ANTLR | 4.7.2 | BSD-3-Clause | ProLeap's parser runtime |
| JavaParser | 3.28.2 | Apache-2.0 / LGPL-3.0 (dual) | Java AST construction and printing |
| Jackson Databind | 2.22.4 | Apache-2.0 | worker JSON |
| nlohmann/json | 3.12.0 | MIT | orchestrator JSON |
| GoogleTest | 1.17.0 | BSD-3-Clause | C++ tests |
| JUnit | 5.14.4 | EPL-2.0 | Java tests |
| pytest, jsonschema | locked in `uv.lock` | MIT | Python tests |
| GnuCOBOL | 3.2 | GPL-3.0 | test oracle only (not linked or shipped) |

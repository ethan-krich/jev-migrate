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

Settings the source does not determine (character set, out-of-range policy, file paths, …) go
in a [`Dialect`](docs/mapping-api.md#dialect-project-settings):
`transpile(..., dialect=Dialect(encoding="ascii", out_of_range="fail"))`. A program that needs
an unset one gets a single `dialect:<field>` decision.

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
| ✅ | IDENTIFICATION / DATA / PROCEDURE divisions, WORKING-STORAGE | one top-level program per file (nested programs allowed), fixed / variable / tandem format |
| ✅ | Debugging lines (`D` indicator) | comments unless `Dialect.debugging_lines` or `WITH DEBUGGING MODE` |
| ✅ | `PIC X(n)` / `A(n)`, `PIC [S]9(i)[V9(s)]`, `P` scaling (`99PPP`, `PPP99`, `VPP9`), levels 01–49, 77 and 78, `VALUE` literal / figurative | byte-addressed storage per record (GnuCOBOL byte images); default init: spaces / zero |
| ✅ | `USAGE DISPLAY`, `PACKED-DECIMAL` / `COMP-3`, `BINARY` / `COMP` / `COMP-4` / `COMP-X` (big-endian; `Dialect.binary_truncate`), `COMP-5`, `INDEX`; group USAGE | |
| ✅ | Group items as operands (MOVE, compare, DISPLAY, ACCEPT), `REDEFINES`, `SIGN LEADING / TRAILING [SEPARATE]`, `JUSTIFIED RIGHT`, `BLANK WHEN ZERO`, `SYNCHRONIZED` (no slack bytes) | byte images of signed zoned / COMP-5 items need `Dialect.encoding` = ascii |
| ✅ | Numeric-edited and alphanumeric-edited pictures, de-editing | GnuCOBOL's editing, byte for byte |
| ✅ | `OCCURS` (nested, `VALUE`, `DEPENDING ON`, `KEY`), subscripts (`n`, `item`, `index`, `± n`), `INDEXED BY`, `SET` index, reference modification | ranges that are not literals need `Dialect.out_of_range` |
| ✅ | `INITIALIZE`, `STRING`, `UNSTRING`, `INSPECT`, serial `SEARCH` | `SEARCH ALL` is a decision with candidates |
| ✅ | LINKAGE SECTION, `PROCEDURE DIVISION USING`, `CALL 'literal' USING BY REFERENCE / BY CONTENT`, `NUMBER-OF-CALL-PARAMETERS`, `LENGTH OF`, `WHEN-COMPILED` (`Dialect.when_compiled`), `IS INITIAL` | argument number and sizes are checked against the callee |
| ✅ | Level 88 (values, `THRU`, `WHEN SET TO FALSE`) on elementary items and groups; `SET cond TO TRUE / FALSE` | a group's values are nonnumeric literals or figuratives |
| ✅ | Level 66 `RENAMES [THRU]`, the `TALLY` register | RENAMES of a single COMP / COMP-3 item is a decision (GnuCOBOL 3.2 reads it as DISPLAY) |
| ✅ | Figuratives ZERO, SPACE, QUOTE, HIGH-/LOW-VALUE (need `Dialect.encoding`), `ALL "lit"` | |
| ✅ | Identification paragraphs, SOURCE-/OBJECT-COMPUTER, SPECIAL-NAMES `DECIMAL-POINT IS COMMA`, `CLASS`, `ALPHABET`, mnemonic names | other clauses are decisions |
| ✅ | `MOVE` (alphanumeric pad/truncate; numeric decimal alignment, high-/low-order truncation, sign drop; integer numeric → alphanumeric digits; figurative ZERO/SPACE) | per receiver |
| ✅ | `ADD`, `SUBTRACT`, `MULTIPLY`, `DIVIDE` (TO/FROM/BY/INTO/GIVING), `ROUNDED`, `[NOT] ON SIZE ERROR` | exact `BigDecimal`; divide by zero is a size error |
| ✅ | `COMPUTE` with `+ - * /`, integer `**`, unary minus, parentheses | GnuCOBOL 3.2 quotient scaling, checked by a dense fixture; non-integer or negative exponents are decisions |
| ✅ | `DISPLAY` literals and items, `WITH NO ADVANCING` | GnuCOBOL format (`+005`, `012.34`) |
| ✅ | `IF`/`ELSE`, `EVALUATE`, relation (`= < > <= >= NOT`), sign, abbreviated (`A = 1 OR 2`) and combined (`A = (1 OR 2)`) conditions, condition-names, class conditions read from the bytes (`NUMERIC`, `ALPHABETIC[-UPPER/-LOWER]`, user CLASS) | ordered alphanumeric comparisons need `Dialect.collating_sequence` (ASCII) |
| ✅ | `NEXT SENTENCE`, `EXIT PARAGRAPH / SECTION / PERFORM [CYCLE]` | exits that leave a PERFORMed range are decisions |
| ✅ | `DIVIDE … REMAINDER`, `MOVE / ADD / SUBTRACT CORRESPONDING` | |
| ✅ | `DISPLAY UPON CONSOLE / SYSERR`, `EXHIBIT [NAMED]`, `ACCEPT` from stdin and `FROM DATE / DAY / DAY-OF-WEEK / TIME` | |
| ✅ | Paragraphs, sections, fall-through, `PERFORM` proc / inline / `THRU`, `TIMES`, `UNTIL`, `VARYING … AFTER`, `WITH TEST AFTER` | |
| ✅ | `GO TO`, `GO TO … DEPENDING ON` | procedure dispatcher with GnuCOBOL's PERFORM return points; overlapping / recursive ranges and GO TO out of a performed range are decisions; `ALTER` is a decision with a candidate |
| ✅ | `STOP RUN`, `GOBACK`, `EXIT`, `EXIT PROGRAM`, `CONTINUE`, `RETURN-CODE` → exit status | |
| ✅ | `COPY` (copybook search path; decision lines map back to the original file), `CALL 'literal'` of a program in the project, `CALL identifier` (needs `Dialect.dynamic_calls`), `ON EXCEPTION` / `ON OVERFLOW`, `CANCEL`, `ENTRY "name" [USING]` (first statement of a paragraph, programs without sections) | persistent WORKING-STORAGE, RETURN-CODE propagation, PROGRAM-IDs matched case-sensitively as GnuCOBOL does |
| ◐ | `REPLACE` statements | parsed by ProLeap; line numbers then fall back to the preprocessed source (warning `approximate_line_numbers`) |
| ✅ | Nested programs (`END PROGRAM`), `IS COMMON` | static nested classes of the file's class; callable only from their container (and, when COMMON, from its other programs), as in GnuCOBOL |
| ✅ | `IS RECURSIVE` (also assumed for a CALL of the program itself, as GnuCOBOL does), `LOCAL-STORAGE SECTION` | LOCAL-STORAGE starts afresh at each invocation; a recursive invocation keeps its own LOCAL-STORAGE and LINKAGE bindings; recursive programs with GO TO / PERFORM THRU are decisions |
| ✅ | `EXTERNAL` data items and files, `SAME RECORD AREA` | shared by name across the run unit's programs (zero bytes until set, as in GnuCOBOL); an EXTERNAL file's FILE STATUS / RELATIVE KEY must be EXTERNAL |
| ❌ | `COMP-1` / `COMP-2`, `POINTER`, `ADDRESS OF`, `N` pictures, `P` in edited pictures, a fixed sign before a floating currency string, `GLOBAL`, `CALL … BY VALUE` | `unsupported_construct` |
| ❌ | `**` with a non-integer or negative exponent, EBCDIC collating sequence | `unsupported_construct` |
| ✅ | SEQUENTIAL and LINE SEQUENTIAL files: `OPEN`, `READ [INTO]`, `WRITE [FROM]`, `REWRITE`, `CLOSE`, `FILE STATUS` | paths from `Dialect.file_assignments`; GnuCOBOL status values; record areas start as zero bytes, as in GnuCOBOL |
| ✅ | RELATIVE and INDEXED files: `ACCESS SEQUENTIAL / RANDOM / DYNAMIC`, `RELATIVE KEY`, `RECORD KEY`, `ALTERNATE RECORD KEY [WITH DUPLICATES]`, `READ [NEXT / PREVIOUS] [KEY IS]`, `START` (`= > >= < <= NOT < NOT >`, leading part of a key), `WRITE`, `REWRITE`, `DELETE`, `[NOT] INVALID KEY` | GnuCOBOL 3.2's statuses and file positioning, checked by dense random fixtures; RELATIVE files use GnuCOBOL's file format, INDEXED files the runtime's own (not Berkeley DB) |
| ✅ | `SORT … WITH DUPLICATES IN ORDER`, `MERGE`, `RELEASE`, `RETURN`, `USING` / `GIVING` / INPUT and OUTPUT PROCEDURE | SORT without DUPLICATES IN ORDER is a decision with a stable-sort candidate |
| ✅ | Exact intrinsic functions (see docs/mapping-api.md) | floating-point functions are decisions |
| ✅ | `DECLARATIVES` with `USE AFTER ERROR / EXCEPTION PROCEDURE ON file / INPUT / OUTPUT / I-O / EXTEND` | run for file exceptions the statement's phrases do not handle, chosen by file, else by the mode of the file's last OPEN, as GnuCOBOL does |
| ✅ | `WRITE … BEFORE / AFTER ADVANCING n LINES / PAGE / mnemonic`, `LINAGE` (`FOOTING`, `TOP`, `BOTTOM`), `LINAGE-COUNTER`, `[NOT] AT END-OF-PAGE` | GnuCOBOL's control bytes and paging, byte for byte |
| ❌ | `SELECT OPTIONAL`, `ASSIGN TO` a data item (file name chosen at run time), split keys, `SUPPRESS WHEN`, `LOCK` phrases, SORT / MERGE `USING` / `GIVING` of RELATIVE / INDEXED files, `REWRITE` of an INDEXED file with alternate keys in `ACCESS SEQUENTIAL`, `USE FOR DEBUGGING` / `GLOBAL` declaratives, declaratives with `GO TO` / `PERFORM THRU`, `RERUN` / `MULTIPLE FILE` | decisions |
| ✅ | `ACCEPT FROM ENVIRONMENT-VALUE / ENVIRONMENT "name" / ARGUMENT-NUMBER / ARGUMENT-VALUE / COMMAND-LINE`, `DISPLAY UPON ENVIRONMENT-NAME / ENVIRONMENT-VALUE / ARGUMENT-NUMBER`, `[NOT] ON EXCEPTION` | as GnuCOBOL: unset variables give spaces and an exception |
| ❌ | `CALL` of a program outside the project or of the program itself, `CALL … RETURNING`, `DISPLAY UPON` printer / other devices, `STOP literal` | `missing_mapping`, `missing_target_api` or `unsupported_construct` |
| ❌ | Other special registers (`DEBUG-ITEM`, `SORT-RETURN`, …), free format / other dialects | files ProLeap cannot parse (XML / JSON GENERATE, ALLOCATE, OO COBOL, …) fail with `unsupported_dialect` |

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
| ProLeap COBOL Parser | 4.0.0 (commit `d1bfe75`) | MIT | COBOL parsing and semantic graph (built from the submodule; `proleap/build.gradle.kts` extends a copy of the grammar with `EXIT PARAGRAPH/SECTION/PERFORM [CYCLE]`, `WHEN SET TO FALSE`, `INITIALIZE ... WITH FILLER / ALL TO VALUE / THEN TO DEFAULT` and `COMP-X`) |
| ANTLR | 4.7.2 | BSD-3-Clause | ProLeap's parser runtime |
| JavaParser | 3.28.2 | Apache-2.0 / LGPL-3.0 (dual) | Java AST construction and printing |
| Jackson Databind | 2.22.4 | Apache-2.0 | worker JSON |
| nlohmann/json | 3.12.0 | MIT | orchestrator JSON |
| GoogleTest | 1.17.0 | BSD-3-Clause | C++ tests |
| JUnit | 5.14.4 | EPL-2.0 | Java tests |
| pytest, jsonschema | locked in `uv.lock` | MIT | Python tests |
| GnuCOBOL | 3.2 | GPL-3.0 | test oracle only (not linked or shipped) |

# jev-migrate

A modular transpiler for researching hybrid deterministic + probabilistic code translation.
**Phase 1** is a working, tested, *deterministic* COBOL-to-Java translator. Operations that cannot
be translated reliably come back as structured **decisions**. **Phase 2** can route them to
[Jev](docs/architecture.md#phase-2-jev), which only chooses among candidates the worker has already
generated and validated, and reports how confident it is in each pick. Phase 2 is opt-in: by default
nothing makes AI or network calls at runtime.

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
./scripts/run.sh legacy -o generated   # translates the COBOL under legacy/ (CLI)
```

The scripts work from any directory. `./scripts/test.sh java` (or `cpp`, `python`) runs a single
suite; arguments after `--` go to pytest.

**Pinned toolchain.** `./scripts/docker.sh test` (or `build`, `run`, `shell`) runs the same
scripts in a slim Debian image with every tool version pinned, including GnuCOBOL 3.2. That is the
reference environment for equivalence results.

The command-line report uses colors on interactive terminals; redirected output stays plain text.
Use `--verbose` for the worker log on stderr, or `--json` for a machine-readable report on stdout.
Set `NO_COLOR` to disable colors.

## A first translation

```python
from transpiler import transpile
from cobol_to_java import Layout, default_mappings

mappings = default_mappings()            # a fresh, independent, mutable registry
mappings.remove("DISPLAY")               # every DISPLAY now becomes a decision
mappings.add("CALL", strategy="unresolved", when={"using": True}, priority=200)

result = transpile(
    "legacy",                            # directory, file, glob ("src/**/*.cbl") or list
    target="java",
    output="generated",
    mappings=mappings,
    layout=Layout(base_package="com.example.legacy"),
)
print(result.summary())
```

```
status: incomplete (2 program(s), 0 file(s) written)
  billing/PAY-CALC.cbl: incomplete -> src/main/java/com/example/legacy/billing/PayCalc.java
  ...
  decision-1 [missing_mapping] billing/PAY-CALC.cbl:21 DISPLAY: no mapping rule matches DISPLAY ...
      candidate builtin:display
```

Nothing is written while any decision is pending, and a `generated/transpile-report.json` is
always written. To resolve decisions offline, pass validated selections:

```python
from transpiler import DecisionResponse, StaticProvider
answers = [DecisionResponse("builtin:display", fingerprint=d.fingerprint) for d in result.pending_decisions]
result = transpile("legacy", output="generated", mappings=mappings,
                   layout=Layout(base_package="com.example.legacy"), provider=StaticProvider(answers))
assert result.ok   # generated/src/main/java/com/example/legacy/... compiles with plain javac
```

### Asking Jev (Phase 2, opt-in)

```bash
uv sync --all-packages --extra jev     # the optional transpiler[jev] extra (typesafe_sdk)
export TYPESAFE_API_KEY=...            # read by the provider process only; never written to the job
```

Jev is also served through [OpenRouter](https://openrouter.ai/docs/guides/community/typesafe-sdk)
with an OpenRouter key: `JevProvider("jev-latest", base_url=OPENROUTER_BASE_URL,
api_key_env="OPENROUTER_API_KEY")` (bare ids map to OpenRouter's `typesafe/` models; pin one with
`jev-1.13`). `timeout_seconds` (default 120) bounds each API call.

```python
from transpiler import ContextBudget, Gate, JevProvider, transpile

jev = JevProvider(
    model="jev-preview",
    context=ContextBudget(max_chars=4000, max_depth=2, max_expansions=2),
    api_key_env="TYPESAFE_API_KEY",
)
result = transpile("legacy", output="generated", mappings=mappings, provider=jev)
for r in sorted(result.resolutions.applied, key=lambda r: r.confidence):   # least confident first
    print(r.decision_id, r.importance, r.confidence, r.candidate_id, r.rationale)
print((result.output / "review.md").read_text())   # the review queue, also result.review_queue()
```

By default Jev always makes an attempt: every decision with candidates gets the candidate Jev
ranks highest, and `confidence` is the probability Jev gives it. When Jev would rather have
abstained (no candidate fits), the pick is still applied and its rationale says so; a low
confidence is the signal to review it. To hold picks instead, pass a gate:
`gate=Gate(floors={"low": 0.70, "normal": 0.85, "high": 0.95, "critical": None}, min_margin=0.15)`
applies a pick only if Jev's confidence reaches the floor of the decision's importance tier
(`None`: never automatically) and beats the runner-up by `min_margin`; anything else stays
pending, so the run is `incomplete` and nothing is written. Tiers come from
[`DEFAULT_IMPORTANCE`](docs/mapping-api.md#importance) (`transpile(importance=...)` overrides
it).

Jev can also choose a setting the source does not state, such as the collating sequence a
program was written for, by reading the code that depends on it; every program is then
translated again with that value (see [setting decisions](docs/worker-protocol.md#setting-decisions)).
`./scripts/jev-smoke.sh` runs the setting projects in
[`examples/cobol-to-java-jev`](examples/cobol-to-java-jev/projects.py) against the live API and
says whether Jev's pick matches the setting each program was written for
(`OPENROUTER_API_KEY=... ./scripts/jev-smoke.sh`, or `TYPESAFE_API_KEY=...`; it also reads them
from a git-ignored `.env`, see `.env.example`). Set `JEV_VERBOSE=1` to include the worker log.
The script builds the project before running and is not part of any test suite.

### Mapping COBOL parts to Java libraries (opt-in, not equivalence-checked)

When a team has already replaced some COBOL routines with a Java library, the translation can
call that library instead of translating those routines:

```python
from cobol_to_java import Cobol, Java, Library, libraries

libs = libraries()
libs.add(Library(id="acme.dates", cobol=Cobol.path("common/dates/"),
                 java=Java.maven("com.acme:acme-dates:2.3.1")))   # installed, with its sources jar
result = transpile("src", output="out", libraries=libs, target_project="../billing-app", provider=jev)
print(result.equivalence, [(u.part, u.outcome, u.chain) for lib in result.libraries_used for u in lib.uses])
```

A library can claim called programs (by PROGRAM-ID or path), intrinsic functions, MOVE / DISPLAY /
ACCEPT, system routines, paragraphs, and copybooks (procedure code, or a data record that converts
to a library class at the call). The worker reads the library's public API from its sources
(nothing is downloaded) and each use becomes a chain of `library_call` decisions: which call
starts it, how the COBOL items bind to its parameters, further calls on the result, which item
receives it. Every step also offers `cobol:translate`, the exact translation. **Library calls are
not checked against the original COBOL**: each one is flagged in the Java, the report and the
first section of `review.md`, and the CLI says to run your tests against both the original
program and the translation. Details: [library mappings](docs/mapping-api.md#library-mappings).
The third smoke project, `billing`, maps its date routines to a small Java library; the smoke run
installs it into a scratch Maven repository, lets Jev build each call, and runs the library
translation next to the plain one (`JEV_PROJECTS=billing ./scripts/jev-smoke.sh`).

Settings the source does not determine (character set, out-of-range policy, file paths, …) go
in a [`Dialect`](docs/mapping-api.md#dialect-project-settings):
`transpile(..., dialect=Dialect(encoding="ascii", out_of_range="fail"))`. A program that needs
an unset one gets a single `dialect:<field>` decision, which a provider can answer for the
fields with a fixed set of values.

From the shell: `./scripts/run.sh legacy -o generated --base-package com.acme`
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
| ✅ | IDENTIFICATION / DATA / PROCEDURE divisions, WORKING-STORAGE | one top-level program per file (nested programs allowed), fixed / variable / tandem / free format (free: no COPY, no directives other than `>>SOURCE` and `>>D`) |
| ✅ | Debugging lines (`D` indicator) | comments unless `Dialect.debugging_lines` or `WITH DEBUGGING MODE` |
| ✅ | `PIC X(n)` / `A(n)`, `PIC [S]9(i)[V9(s)]`, `P` scaling (`99PPP`, `PPP99`, `VPP9`), levels 01–49, 77 and 78, `VALUE` literal / figurative | byte-addressed storage per record (GnuCOBOL byte images); default init: spaces / zero |
| ✅ | `USAGE DISPLAY`, `PACKED-DECIMAL` / `COMP-3`, `BINARY` / `COMP` / `COMP-4` / `COMP-X` (big-endian; `Dialect.binary_truncate`), `COMP-5`, `INDEX`; group USAGE | |
| ✅ | Group items as operands (MOVE, compare, DISPLAY, ACCEPT), `REDEFINES`, `SIGN LEADING / TRAILING [SEPARATE]`, `JUSTIFIED RIGHT`, `BLANK WHEN ZERO`, `SYNCHRONIZED` (no slack bytes) | byte images of signed zoned / COMP-5 items need `Dialect.encoding` = ascii |
| ✅ | Numeric-edited and alphanumeric-edited pictures, de-editing | GnuCOBOL's editing, byte for byte |
| ✅ | `OCCURS` (nested, `VALUE`, `DEPENDING ON`, `KEY`), subscripts (`n`, `item`, `index`, `± n`), `INDEXED BY`, `SET` index, reference modification | ranges that are not literals need `Dialect.out_of_range` |
| ✅ | `INITIALIZE`, `STRING`, `UNSTRING`, `INSPECT`, `SEARCH`, `SEARCH ALL … WHEN key = value` | `SEARCH ALL` is GnuCOBOL's binary search, whatever order the keys are in |
| ✅ | LINKAGE SECTION, `PROCEDURE DIVISION USING`, `CALL 'literal' USING BY REFERENCE / BY CONTENT`, `NUMBER-OF-CALL-PARAMETERS`, `LENGTH OF`, `WHEN-COMPILED` (`Dialect.when_compiled`), `IS INITIAL` | argument number and sizes are checked against the callee |
| ✅ | Level 88 (values, `THRU`, `WHEN SET TO FALSE`) on elementary items and groups; `SET cond TO TRUE / FALSE` | a group's values are nonnumeric literals or figuratives |
| ✅ | Level 66 `RENAMES [THRU]`, the `TALLY` register | RENAMES of a single COMP / COMP-3 item is a decision (GnuCOBOL 3.2 reads it as DISPLAY) |
| ✅ | Figuratives ZERO, SPACE, QUOTE, HIGH-/LOW-VALUE (need `Dialect.encoding`), `ALL "lit"` | |
| ✅ | Identification paragraphs, SOURCE-/OBJECT-COMPUTER, SPECIAL-NAMES `DECIMAL-POINT IS COMMA`, `CLASS`, `ALPHABET`, mnemonic names, `SWITCH-n … ON / OFF STATUS` with `SET switch TO ON / OFF` | switches start from `COB_SWITCH_n`; other clauses are decisions |
| ✅ | `MOVE` (alphanumeric pad/truncate; numeric decimal alignment, high-/low-order truncation, sign drop; integer numeric → alphanumeric digits; alphanumeric → numeric as libcob converts it; `MOVE ALL`; figurative ZERO/SPACE) | per receiver |
| ✅ | `ADD`, `SUBTRACT`, `MULTIPLY`, `DIVIDE` (TO/FROM/BY/INTO/GIVING), `ROUNDED`, `[NOT] ON SIZE ERROR` | exact `BigDecimal`; divide by zero is a size error |
| ✅ | `COMPUTE` with `+ - * /`, integer `**`, unary minus, parentheses | GnuCOBOL 3.2 quotient scaling, checked by a dense fixture; non-integer or negative exponents are decisions |
| ✅ | `DISPLAY` literals and items, `WITH NO ADVANCING` | GnuCOBOL format (`+005`, `012.34`) |
| ✅ | `IF`/`ELSE`, `EVALUATE`, relation (`= < > <= >= NOT`), sign, abbreviated (`A = 1 OR 2`) and combined (`A = (1 OR 2 AND 3)`) conditions, condition-names (also subscripted), class conditions read from the bytes (`NUMERIC`, `ALPHABETIC[-UPPER/-LOWER]`, user CLASS) | ordered alphanumeric comparisons need `Dialect.collating_sequence` (ASCII) |
| ✅ | `NEXT SENTENCE`, `EXIT PARAGRAPH / SECTION / PERFORM [CYCLE]` | EXIT SECTION from a paragraph PERFORMed on its own uses the procedure dispatcher |
| ✅ | `DIVIDE … REMAINDER`, `MOVE / ADD / SUBTRACT CORRESPONDING` | |
| ✅ | `DISPLAY UPON CONSOLE / SYSERR` and cobc's other names for them (`SYSLST`, `STDOUT`, `PRINT`, `TERMINAL`, `STDERR`, …), `EXHIBIT [NAMED]`, `ACCEPT` from stdin and `FROM DATE / DAY / DAY-OF-WEEK / TIME / MICROSECOND-TIME` | |
| ✅ | Paragraphs, sections, fall-through, `PERFORM` proc / inline / `THRU`, `TIMES`, `UNTIL`, `VARYING … AFTER`, `WITH TEST AFTER` | |
| ✅ | `GO TO`, `GO TO … DEPENDING ON` | procedure dispatcher with GnuCOBOL's PERFORM return points (a GO TO out of a performed range leaves the PERFORM waiting, as GnuCOBOL does); overlapping / recursive ranges are decisions; `ALTER` of a paragraph holding only a GO TO |
| ✅ | `STOP RUN [GIVING]`, `GOBACK`, `EXIT`, `EXIT PROGRAM`, `CONTINUE`, `RETURN-CODE` → exit status, `CALL … RETURNING / GIVING` a numeric item | RETURNING gets the callee's RETURN-CODE and leaves the caller's unchanged, as GnuCOBOL |
| ✅ | `COPY` (copybook search path; decision lines map back to the original file), `CALL 'literal'` of a program in the project, `CALL identifier` (needs `Dialect.dynamic_calls`), the system routines `CBL_TOUPPER / TOLOWER / AND / OR / XOR / NOT / EQ / IMP / NIMP`, `ON EXCEPTION` / `ON OVERFLOW`, `CANCEL`, `ENTRY "name" [USING]` (first statement of a paragraph, programs without sections) | persistent WORKING-STORAGE, RETURN-CODE propagation, PROGRAM-IDs matched case-sensitively as GnuCOBOL does |
| ◐ | `REPLACE` statements | parsed by ProLeap; line numbers then fall back to the preprocessed source (warning `approximate_line_numbers`) |
| ✅ | Nested programs (`END PROGRAM`), `IS COMMON`, `GLOBAL` data items | static nested classes of the file's class; callable only from their container (and, when COMMON, from its other programs), as in GnuCOBOL; GLOBAL items of containers are shared, a program's own item of the same name hides them |
| ✅ | `IS RECURSIVE` (also assumed for a CALL of the program itself, as GnuCOBOL does), `LOCAL-STORAGE SECTION` | LOCAL-STORAGE starts afresh at each invocation; a recursive invocation keeps its own LOCAL-STORAGE and LINKAGE bindings; recursive programs with GO TO / PERFORM THRU are decisions |
| ✅ | `EXTERNAL` data items and files, `SAME RECORD AREA` | shared by name across the run unit's programs (zero bytes until set, as in GnuCOBOL); an EXTERNAL file's FILE STATUS / RELATIVE KEY must be EXTERNAL |
| ❌ | `COMP-1` / `COMP-2`, `POINTER`, `ADDRESS OF`, `N` pictures, `P` in edited pictures, a fixed sign before a floating currency string, `GLOBAL` files, `CALL … BY VALUE` | `unsupported_construct` |
| ✅ | `Dialect.collating_sequence = ebcdic` | ordered comparisons and SORT / MERGE keys in GnuCOBOL's EBCDIC sequence (`-fdefault-colseq=EBCDIC`); EBCDIC *data* (`Dialect.encoding = ebcdic`) is not implemented |
| ❌ | `**` with a non-integer or negative exponent | `unsupported_construct` |
| ✅ | SEQUENTIAL and LINE SEQUENTIAL files: `OPEN`, `READ [INTO]`, `WRITE [FROM]`, `REWRITE`, `CLOSE`, `FILE STATUS` | paths from `Dialect.file_assignments`; GnuCOBOL status values; record areas start as zero bytes, as in GnuCOBOL |
| ✅ | RELATIVE and INDEXED files: `ACCESS SEQUENTIAL / RANDOM / DYNAMIC`, `RELATIVE KEY`, `RECORD KEY`, `ALTERNATE RECORD KEY [WITH DUPLICATES]`, `READ [NEXT / PREVIOUS] [KEY IS]`, `START` (`= > >= < <= NOT < NOT >`, leading part of a key), `WRITE`, `REWRITE`, `DELETE`, `[NOT] INVALID KEY` | GnuCOBOL 3.2's statuses and file positioning, checked by dense random fixtures; RELATIVE files use GnuCOBOL's file format, INDEXED files the runtime's own (not Berkeley DB) |
| ✅ | `SORT [WITH DUPLICATES IN ORDER]`, `MERGE`, `RELEASE`, `RETURN`, `USING` / `GIVING` (several files; RELATIVE and INDEXED too) / INPUT and OUTPUT PROCEDURE, `COLLATING SEQUENCE` (NATIVE / ASCII / EBCDIC alphabets) | stable, as GnuCOBOL 3.2: equal keys come out in release order with or without DUPLICATES IN ORDER |
| ✅ | Exact intrinsic functions (see docs/mapping-api.md) | floating-point functions are decisions |
| ✅ | `DECLARATIVES` with `USE AFTER ERROR / EXCEPTION PROCEDURE ON file / INPUT / OUTPUT / I-O / EXTEND` | run for file exceptions the statement's phrases do not handle, chosen by file, else by the mode of the file's last OPEN, as GnuCOBOL does |
| ✅ | `WRITE … BEFORE / AFTER ADVANCING n LINES / PAGE / mnemonic`, `LINAGE` (`FOOTING`, `TOP`, `BOTTOM`), `LINAGE-COUNTER`, `[NOT] AT END-OF-PAGE` | GnuCOBOL's control bytes and paging, byte for byte |
| ❌ | `SELECT OPTIONAL`, `ASSIGN TO` a data item (file name chosen at run time), split keys, `SUPPRESS WHEN`, `LOCK MODE` (READ lock phrases are decisions with a candidate; `CLOSE WITH LOCK / NO REWIND` are translated), SORT / MERGE `USING` / `GIVING` of files in ACCESS RANDOM, `REWRITE` of an INDEXED file with alternate keys in `ACCESS SEQUENTIAL`, `USE FOR DEBUGGING` with `WITH DEBUGGING MODE` (without it the section never runs and is left out), `GLOBAL` declaratives in programs with nested programs, declaratives with `GO TO` / `PERFORM THRU`, `RERUN` / `MULTIPLE FILE` | decisions |
| ✅ | `ACCEPT FROM ENVIRONMENT-VALUE / ENVIRONMENT "name" / ARGUMENT-NUMBER / ARGUMENT-VALUE / COMMAND-LINE`, `DISPLAY UPON ENVIRONMENT-NAME / ENVIRONMENT-VALUE / ARGUMENT-NUMBER`, `[NOT] ON EXCEPTION` | as GnuCOBOL: unset variables give spaces and an exception |
| ◐ | `CALL` of a program outside the project, `DISPLAY UPON PRINTER / SYSPCH`, `STOP literal` | decisions with candidates (GnuCOBOL's behaviour among them) that a provider such as Jev chooses; `CALL … RETURNING` with `ON EXCEPTION` is a decision |
| ❌ | Other special registers (`DEBUG-ITEM`, `SORT-RETURN`, …), other dialects | files ProLeap cannot parse (XML / JSON GENERATE, ALLOCATE, OO COBOL, …) fail with `unsupported_dialect` |

Constructs deliberately left to Phase 2 (floating point, pointers, national pictures,
EBCDIC data) are listed with the reasons in
[docs/architecture.md](docs/architecture.md#constructs-deferred-to-phase-2).

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
| `python/` | `transpiler`: public API, registry, library registry, providers (deterministic, static, Jev) and the provider process, callbacks, protocol types |
| `protocol/` | JSON schemas: worker messages, mappings, decisions, providers, libraries |
| `examples/cobol-to-java-jev/` | the projects of `scripts/jev-smoke.sh` (settings, and `billing` with a Java date library) |
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

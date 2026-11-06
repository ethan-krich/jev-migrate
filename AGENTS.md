# AGENTS.md

## Core rules

- **Jev resolves decisions; it never writes the translation.** The translator is deterministic,
  and Jev (the live API, `JevProvider`) only picks among the candidates a decision offers.
  `./scripts/run.sh` translates with Jev. No invented translation results.
- **Tests stay deterministic and offline.** No test suite calls the API; providers in tests are
  deterministic or recorded (`RecordedJevClient`). Live runs (`run.sh`, `jev-smoke.sh`) are
  never part of `test.sh` or `docker.sh test`.
- **Correctness over coverage.** If semantics cannot be preserved exactly, raise a decision.
  Never approximate, and never drop source functionality. `finalize` must never write output
  while a decision is pending.
- **Separation:**
  - COBOL/Java semantics live only in the Java worker and runtime.
  - Python holds declarative mappings (handler ids) and the folder `Layout`.
  - C++ stays language-agnostic and never sees ProLeap or JavaParser types.
- Build Java with JavaParser nodes, not source strings. Parse COBOL with ProLeap, never regex.
- Handler ids and default rule ids are a public API. When the catalog changes, update
  `cobol_to_java/mappings.py` too; `test_worker_protocol.py` enforces that.
- Expected COBOL behaviour comes from GnuCOBOL 3.2 in `docker/Dockerfile`. Re-record fixtures
  with `./scripts/docker.sh uv run python tests/equivalence/record_fixtures.py`, and never
  normalise output in comparisons.
- No CI config, monorepo frameworks or extra services. Shell scripts coordinate CMake, Gradle
  and uv.

## Library mappings

- `transpile(libraries=...)` is one of two opt-in exceptions to exact equivalence: a claimed COBOL
  part may call an installed Java library. Keep it narrow: every use is a `library_call` decision
  (never chosen silently, `cobol:translate` always offered, nothing written while pending), every
  use is flagged (Java comment, class Javadoc, report `libraries_used` / `equivalence`,
  `review.md`, CLI), candidates come only from the library's declarations through exact
  `JavaTypes` pairs, and nothing is downloaded (local Maven / Gradle caches, sources jar).
- Without `libraries=` the output, facts and fingerprints must stay unchanged.
- The library facts and the `cobol.*.library` rules are public API like handler ids: update
  `cobol_to_java/mappings.py` with them.

## Runtime or JDK

- The other exception: with `options.jdk_alternatives` (on by default when Jev decides, via the
  pair's `judged_options`) a statement a builtin handler and its JDK twin (`<handler>_jdk`) can
  both translate is a `runtime_choice` decision; Jev decides whether the runtime's exactness is
  needed. A twin is the runtime handler's generator in JDK mode (`TranslationContext.jdk()`), built
  with JavaParser nodes like any handler; its description must say where it can differ.
- Without the option the output, facts and fingerprints must stay unchanged; tests that are not
  about the choice pin `jdk_alternatives: False`.
- Twins are public API like handler ids: update `BUILTIN_HANDLERS`, `JDK_TWINS` and
  `CANDIDATE_HANDLERS` in `cobol_to_java/mappings.py` with them.

## Commands

`./scripts/setup.sh`, `build.sh`, `test.sh [cpp|java|python]`, `run.sh` (translates into
`generated/` with the live Jev API; needs an API key), and `cobol.sh` / `java.sh` to compile and
run one COBOL program or its translation.
`./scripts/docker.sh test` runs everything in the pinned toolchain. `./scripts/jev-smoke.sh` is
an opt-in live run against the TypeSafe API; it is never part of a test suite.

## Where things are documented

- `README.md`: setup, first translation, the supported COBOL subset, licenses.
- `docs/architecture.md`: responsibilities, data flow, decisions, the Jev integration.
- `docs/mapping-api.md`: mapping registry, handlers, facts, folder layout.
- `docs/worker-protocol.md` and `protocol/*.schema.json`: the worker protocol.

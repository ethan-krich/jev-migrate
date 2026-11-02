# AGENTS.md

## Core rules

- **Phase 1 is deterministic and offline.** No Jev integration, no AI or network calls at
  runtime, no invented translation results.
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

- `transpile(libraries=...)` is the one opt-in exception to exact equivalence: a claimed COBOL
  part may call an installed Java library. Keep it narrow: every use is a `library_call` decision
  (never chosen silently, `cobol:translate` always offered, nothing written while pending), every
  use is flagged (Java comment, class Javadoc, report `libraries_used` / `equivalence`,
  `review.md`, CLI), candidates come only from the library's declarations through exact
  `JavaTypes` pairs, and nothing is downloaded (local Maven / Gradle caches, sources jar).
- Without `libraries=` the output, facts and fingerprints must stay unchanged.
- The library facts and the `cobol.*.library` rules are public API like handler ids: update
  `cobol_to_java/mappings.py` with them.

## Commands

`./scripts/setup.sh`, `build.sh`, `test.sh [cpp|java|python]`, `run.sh`.
`./scripts/docker.sh test` runs everything in the pinned toolchain. `./scripts/jev-smoke.sh` is
an opt-in live run against the TypeSafe API; it is never part of a test suite.

## Where things are documented

- `README.md`: setup, first translation, the supported COBOL subset, licenses.
- `docs/architecture.md`: responsibilities, data flow, decisions, the Phase 2 Jev plan.
- `docs/mapping-api.md`: mapping registry, handlers, facts, folder layout.
- `docs/worker-protocol.md` and `protocol/*.schema.json`: the worker protocol.

# Architecture

## Responsibilities

| Layer | Owns | Must not |
|---|---|---|
| **Python** `transpiler` (`python/`) | Public API (`transpile`), mutable `MappingRegistry`, result objects, provider and callback types, language-pair discovery (entry point `transpiler.language_pairs`) | Contain COBOL or Java transformation logic |
| **Python** `cobol_to_java` (`translators/cobol-to-java/python/`) | Default mapping table (handler ids only), `Layout` folder convention, handler/fact catalog for early validation | Implement semantics |
| **C++** `transpiler-core` (`core/`) | File discovery (dirs, files, globs incl. `**`), translator selection from `translators/*/translator.json`, worker process lifecycle, timeouts, NDJSON protocol, decision batching/validation/caching, run report | Know about COBOL, Java, ProLeap or JavaParser |
| **Java** worker (`translators/cobol-to-java/java/`) | Parsing (ProLeap), semantic checks, mapping-rule selection, builtin handlers, JavaParser AST construction, session state (partial ASTs, decisions), writing output | Execute Python; write output while decisions are pending |
| **Java** runtime (`…/java/runtime/`) | COBOL data semantics used by generated code: byte-addressed `Storage` per level-01 record with `AlphanumericField` / `NumericField` (zoned, packed, binary) / `EditedField` views (GnuCOBOL byte images), `Bounds` (run-time range checks), `StringOps`, `Functions`, `CobolFile` (sequential), `RelativeFile`, `IndexedFile`, `SortFile`, `PerformStack`, `Display`, `ProgramSupport` (CALL, LINKAGE binding) | Depend on anything; it is copied into every output |

## Data flow

```
transpile(source, mappings, layout, provider)                       [Python]
  │ writes job.json: inputs, discovery rules, layout, mappings (JSON), provider config
  ▼
transpiler-core run --job job.json                                  [C++]
  1. discover()  -> programs + copybooks under source_root
  2. start worker (translator.json command), wait for `ready` event
  3. initialize  { files, layout, mappings, options }   ──▶ parse ALL programs, name classes,
                                                             detect collisions, PROGRAM-ID directory
  4. translate   { files: [one program] }  × N          ──▶ per statement: facts -> rule -> handler
                                                             -> JavaParser nodes, or placeholder + decision
  5. decision rounds: batch pending -> cache -> provider.decide -> validate(candidate offered,
     fingerprint) -> resolve { resolutions }            ──▶ replace placeholders; nested decisions return
  6. finalize                                           ──▶ writes Java + runtime only if nothing pending
  7. shutdown; write <output>/transpile-report.json; print report on stdout
  ▼
TranspileResult(status ok|incomplete|failed, programs, decisions, files_written)   [Python]
```

Exit status of `transpiler-core`: `0` ok, `2` incomplete, `1` failed, `64` usage.

The worker is a **persistent process** per job: one JVM translates every file, and its session
keeps the partially built ASTs between `translate`, `resolve` and `finalize`. Translation is
sequential (one `translate` request per program), so scheduling can be added later without
changing the protocol.

## Multi-file projects

- `initialize` receives every program and copybook. Each program is parsed up front, so the
  session knows every PROGRAM-ID → class mapping before translating anything.
- `CALL 'X'` becomes a static call only if `X` is a program in the session. Otherwise it is a
  `missing_target_api` decision ("unresolved cross-file dependency").
- Copybooks are expanded per program by ProLeap. Each program gets its own copy of the copied
  data, which is COBOL's semantics. A missing copybook fails that file with `missing_copybook`. A file ProLeap cannot parse (a dialect construct its grammar does not cover, such as XML / JSON GENERATE, ALLOCATE, object-oriented COBOL or compiler directives, or invalid COBOL) fails with `unsupported_dialect`, naming the construct and line when they can be recognised.
- Output paths and class names are deterministic. Collisions fail both files; nothing is renamed.

## Decisions

Each statement unit (a MOVE is one unit per receiver) goes through these steps:

1. Facts are computed (e.g. `target_category`).
2. The registry selects a rule by priority, then specificity, then registry order. Equal
   priority and specificity with different effects counts as **ambiguous**.
3. The outcome depends on the rule's strategy:
   - `builtin` runs the handler.
   - `unresolved` creates a `missing_mapping` decision.
   - `callback` creates a `callback_required` decision.
   - A handler that refuses creates an `unsupported_construct`, `invalid_mapping` or
     `missing_target_api` decision.
4. A placeholder is left in the AST.

Candidates are offered only after the worker has generated their code successfully, so a
provider can only pick from **validated** options. Unsupported data declarations (e.g.
`OCCURS`) are decisions without candidates; they block output.

Decision reasons: `missing_mapping`, `unsupported_construct`, `ambiguous_mapping`,
`missing_target_api`, `invalid_mapping`, `callback_required`. The fingerprint is a SHA-256 over
the reason, location, text, facts and candidate ids. The C++ cache keys on it.

Providers in Phase 1:
- `DeterministicProvider` answers nothing, so the result is incomplete with structured decisions.
- `StaticProvider` replays fixed selections by id or fingerprint, for tests and approved
  replays.

## Phase 2: Jev

Nothing below is implemented. The interfaces it plugs into exist and are tested.

1. Java raises decision requests (unchanged).
2. C++ batches the pending requests and passes them to a `DecisionProvider`. A new
   `kind: "python"` provider will start a persistent Python subprocess speaking the same
   NDJSON style, sending `decide` batches.
3. That Python process implements `transpiler.providers.DecisionProvider.decide()` by calling
   Jev's Decisions API with the request and its candidates. Each answer must be
   `{decision_id, selection: {kind: "candidate", candidate_id}}`.
4. C++ validates every answer (`validate()`: candidate offered, fingerprint matches), caches
   accepted ones, and sends `resolve` to Java, which re-validates and applies them.
5. `callback_required` decisions are routed the same way to `transpiler.callbacks.dispatch`,
   which loads the user script. The worker never runs Python.

Selections are constrained to worker-validated candidates by design. A model can never inject
arbitrary target code.

## Adding a language pair

1. Implement a worker that speaks `transpiler-worker/1` (see `worker-protocol.md`).
2. Add `translators/<pair>/translator.json`.
3. Add a Python package that registers a `LanguagePair` (default mappings, layout, catalog)
   under the `transpiler.language_pairs` entry point.

The C++ core needs no changes.

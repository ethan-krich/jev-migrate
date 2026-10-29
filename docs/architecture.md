# Architecture

## Responsibilities

| Layer | Owns | Must not |
|---|---|---|
| **Python** `transpiler` (`python/`) | Public API (`transpile`), mutable `MappingRegistry`, result objects, provider and callback types, language-pair discovery (entry point `transpiler.language_pairs`), the provider process (`transpiler.providers.serve`), `JevProvider` and its `Gate` | Contain COBOL or Java transformation logic |
| **Python** `cobol_to_java` (`translators/cobol-to-java/python/`) | Default mapping table (handler ids only), `DEFAULT_IMPORTANCE`, `Layout` folder convention, handler/fact catalog for early validation | Implement semantics |
| **C++** `transpiler-core` (`core/`) | File discovery (dirs, files, globs incl. `**`), translator selection from `translators/*/translator.json`, worker and provider process lifecycle, timeouts, NDJSON protocol, decision batching/validation/caching, the gate outcome, the `expand_context` loop, run report and `review.md` | Know about COBOL, Java, ProLeap or JavaParser |
| **Java** worker (`translators/cobol-to-java/java/`) | Parsing (ProLeap), semantic checks, mapping-rule selection, builtin handlers, JavaParser AST construction, session state (partial ASTs, decisions), importance tiers, the decision context graph, writing output | Execute Python; write output while decisions are pending |
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
  5. decision rounds: batch pending -> cache -> provider.decide -> [expand_context -> ask again]
     -> gate passed? -> validate(candidate offered, fingerprint)
     -> resolve { resolutions }                         ──▶ replace placeholders; nested and slot
                                                             decisions return
  6. finalize                                           ──▶ writes Java + runtime only if nothing pending
  7. shutdown; write <output>/transpile-report.json and review.md; print report on stdout
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
the reason, location, text, facts, candidate ids, dialect and importance. The C++ cache keys on
it.

Every decision also carries an `importance` tier and a `context` graph (see
[Phase 2](#phase-2-jev)). A candidate may declare `slots`: applying it raises one follow-up
decision per slot, so a nested choice is asked as separate flat choices.

Providers:
- `DeterministicProvider` (the default) answers nothing, so the result is incomplete with
  structured decisions.
- `StaticProvider` replays fixed selections by id or fingerprint, for tests and approved
  replays.
- `JevProvider` asks Jev, out of process; see below.

## Phase 2: Jev

Phase 1 stays the default and stays offline: without `provider=JevProvider(...)` nothing calls
the network. The only network code is `transpiler.providers.jev`, behind the optional
`transpiler[jev]` extra (`typesafe_sdk`).

```
transpiler-core                     provider process                       Jev
  │ start provider.command, wait `ready`  (python -m transpiler.providers.serve)
  │ decide {decisions}  ───────────────▶ JevProvider.decide: per decision ──▶ system_one(pick, refs?, review_priority)
  │ ◀─────────────── {answers: gate, confidence, review_priority, selection | need_context}
  │ need_context? expand_context ──▶ worker; decide {decisions, extra_context} again (≤ max_expansions)
  │ gate passed and validate() ok? ──▶ worker resolve (re-validates, applies)
  │ otherwise: held (below_floor / abstained / context cap) or rejected; never cached
  ▼ transpile-report.json + review.md
```

1. The worker raises decisions as in Phase 1. Each has a deterministic `importance` tier and a
   budgeted `context` graph (see below).
2. C++ starts the job's `kind: "python"` provider command (`JevProvider.to_config()`: this
   interpreter running `transpiler.providers.serve`) and talks `transpiler-provider/1` with it
   through the same `WorkerClient` as the worker: startup and request timeouts, crashes,
   malformed output and id mismatches fail the run with `stage: provider` and the stderr tail.
3. `JevProvider` makes one `system_one` call per decision with candidates (decisions without
   candidates are never sent). It asks `pick`, a `Choice` among the candidate ids plus
   `__abstain__` ("none preserves the semantics exactly") and, when the context is truncated
   and expansions remain, `__need_context__`. It asks `refs`, a `Choice` of which truncated
   node to open, only with `__need_context__`. It asks `review_priority`, a `Score` (see below).
4. `Gate.check(importance, pick)` turns the answer into `passed`, `below_floor`, `abstained`
   or `need_context`.
5. C++ applies only `passed` answers that `validate()` accepts (candidate offered, fingerprint
   matches); the worker's `resolve` checks again. A candidate the worker did not offer is
   rejected before `resolve`. Gated-out answers are reported as `held`. Only applied answers
   are cached (with their confidence and review priority, so a replay reproduces them).
6. A held or unanswered decision stays pending: the run is `incomplete` and `finalize` writes
   nothing.

Jev never writes code. It only chooses among candidates the worker generated and validated.

### Importance and the gate

Importance is decided before any model is asked. The worker looks the tier up from the
`(reason, operation)` table passed in `initialize` (`cobol_to_java.DEFAULT_IMPORTANCE`,
overridable with `transpile(importance=...)`): `low`, `normal`, `high` or `critical`. The tier
is part of the fingerprint.

```python
Gate(floors={"low": 0.70, "normal": 0.85, "high": 0.95, "critical": 0.99}, min_margin=0.15)
```

An answer passes only if its confidence reaches the tier's floor **and** the top probability
beats the runner-up (including `__abstain__` / `__need_context__`) by at least `min_margin`. A
floor of `None`, or a tier left out, means the tier is never applied automatically.
Confidence is never multiplied by importance: the tier only selects the floor.

### Review priority

`review_priority` is Jev's advisory answer to "if this were wrong, how much would it matter?",
asked in the same `system_one` call. The scale runs from 0 to 4: `cosmetic`, `local`, `visible`,
`business` and `money/compliance`. It never affects the gate and is not part of the fingerprint.
It is stored with the answer, so it is in the report, in `review.md` and in a cached replay.
`review.md` lists every decision: pending first, then by review priority, then by importance.
It flags a **tier mismatch** when the review priority is `business` or higher on a `low`
decision, or `cosmetic` on a `critical` one: a hint that the importance table may need an
entry.

### Context and expansion

`context.refs` is built breadth-first over the ProLeap ASG. Depth 0 is the statement's
operands. Each further level follows the parent group, `REDEFINES`, condition-names (`VALUE`)
and copybook origin, plus the statements that write the data, read it or pass it on (`usage`:
WRITE, CALL, DISPLAY). It also follows the decision's paragraph and where that paragraph is
PERFORMed. Refs are stable ids. Within `max_depth` and `max_chars`, nodes carry their text.
Beyond the budget they keep their `ref` and a one-line `summary` (`truncated: true`). Context
is never part of the fingerprint.

When Jev answers `__need_context__` with a ref, C++ calls the worker's
`expand_context {decision_id, refs, max_chars}`. It then asks the provider again with the
returned nodes as `extra_context`, at most `ContextBudget.max_expansions` times per decision.
After the cap the decision is held (`held_reason: context_cap`).

### Nested choices

A candidate with `slots` asks its sub-choices as separate decisions. The first real case is a
`CALL 'X'` of a program outside the project. It offers `builtin:call_external`, whose `target`
slot asks what `X` reaches. The options are `target:not_found`, which is GnuCOBOL's behaviour
when the module is absent, or a project program under another name. With `USING`, an `args`
slot asks how the arguments are passed. Every combination is generated, and so validated,
before any slot is offered.

Not wired yet: `callback_required` decisions still carry their `{script, function}` and have
no candidates, so no provider can answer them. `transpiler.callbacks.dispatch` exists for when
they are routed.

### Constructs deferred to Phase 2

These constructs are deliberately not translated in Phase 1. They are decisions today, and
they stay that way until Jev (or a project callback) can supply what the source alone does not
determine. Each one either has no single correct Java behaviour, or GnuCOBOL 3.2 cannot serve
as the reference to prove an exact one.

| Construct | Decision today | Why it waits for Phase 2 |
|---|---|---|
| `COMP-1` / `COMP-2` (floating point) | `unsupported_construct` | IEEE results and GnuCOBOL's DISPLAY of floats depend on its C library; mainframe data uses hexadecimal floating point, which GnuCOBOL does not reproduce |
| Floating-point intrinsic functions (`SQRT`, `LOG`, `EXP`, trigonometry, `RANDOM`, statistics, `PRESENT-VALUE`, `ANNUITY`) | `unsupported_construct` | Last-digit results differ between glibc / GMP and Java's `Math` |
| `**` with a non-integer or negative exponent | `unsupported_construct` | Same: not provably bit-exact |
| `POINTER`, `ADDRESS OF`, `ALLOCATE` / `FREE`, `SET ADDRESS` | `unsupported_construct` (`ALLOCATE`: `unsupported_dialect`, ProLeap cannot parse it) | Byte-addressed storage can model offsets, not arbitrary addresses or memory shared with C |
| `N` (national) pictures | `unsupported_construct` | GnuCOBOL 3.2's national support is incomplete, so there is no reliable reference |
| Free-format source | `source_format` accepts only `fixed`, `variable`, `tandem` | ProLeap's grammar does not read it; needs a reformatter or grammar work |
| `STOP literal` | `missing_target_api` | Shows the literal and waits for an operator; the target system decides what replaces the console |
| `DISPLAY UPON` printer / other devices | `missing_target_api` | The device has no Java counterpart until the project names one |
| EBCDIC data (`Dialect.encoding = ebcdic`) | `missing_mapping` / `unsupported_construct` | GnuCOBOL runs on ASCII data, so EBCDIC byte images cannot be checked against it (the EBCDIC *collating sequence* is supported) |

Like every decision, these may get worker-validated candidates over time; a model choosing
among them still never writes target code itself.

## Adding a language pair

1. Implement a worker that speaks `transpiler-worker/1` (see `worker-protocol.md`).
2. Add `translators/<pair>/translator.json`.
3. Add a Python package that registers a `LanguagePair` (default mappings, layout, catalog)
   under the `transpiler.language_pairs` entry point.

The C++ core needs no changes.

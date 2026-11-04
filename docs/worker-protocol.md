# Worker protocol `transpiler-worker/1`

The orchestrator and a translator worker exchange newline-delimited JSON:
- one object per line;
- requests go on the worker's stdin, responses and events come on its stdout;
- **stderr is for diagnostics only** (the Java worker even redirects `System.out` to stderr).

Schemas: [`protocol/worker.schema.json`](../protocol/worker.schema.json),
[`mapping.schema.json`](../protocol/mapping.schema.json),
[`decision.schema.json`](../protocol/decision.schema.json),
[`provider.schema.json`](../protocol/provider.schema.json),
[`library.schema.json`](../protocol/library.schema.json). Tests validate real traffic against
them.

## Messages

```json
{"protocol":"transpiler-worker/1","id":"c-2","method":"translate","session":"session-1","params":{"files":["billing/PAY.cbl"]}}
{"protocol":"transpiler-worker/1","type":"event","event":"progress","session":"session-1","in_reply_to":"c-2","data":{"phase":"translate","file":"billing/PAY.cbl","index":1,"total":1}}
{"protocol":"transpiler-worker/1","type":"response","id":"c-2","session":"session-1","status":"incomplete","result":{"files":[...],"decisions":[...]}}
```

- Every request gets **exactly one** response with the same `id`. Requests are sequential;
  events may arrive before the response.
- `status` is `ok`, `incomplete` (valid outcome, work pending) or `error`. Errors carry
  `{code, message, details?}`.
- On startup the worker emits a `ready` event with `translator`, `version`, `methods`,
  `handlers` and `facts`. The orchestrator waits for it, up to `startup_timeout_seconds` from
  `translator.json`.

Error codes: `malformed_json` (response `id` is null), `invalid_request`,
`unsupported_protocol`, `unknown_method`, `unknown_session`, `session_exists`, `invalid_state`,
`invalid_params`, `invalid_mapping`, `invalid_library` (the COBOL or Java side of
`initialize.libraries` names something that does not exist; `details.problems` lists every
problem), `library_unavailable` (a library's source cannot be read: not in the local caches, or
installed without its sources jar; the message says how to fetch it), `internal_error`. After any
error the worker keeps serving.

## Methods

| Method | Params | Result |
|---|---|---|
| `initialize` | `source_root`, `output_root` (absolute), `target_language`, `files: [{path, role: program\|copybook}]`, `mappings`, `layout`, `options` (the dialect keys of [`Dialect`](mapping-api.md#dialect-project-settings), plus `source_encoding`: `ISO-8859-1` and `jdk_alternatives`: a boolean, default `false`, see [runtime choices](#runtime-choices); any other key is `invalid_params`), `settings` (values chosen for [setting decisions](#setting-decisions), written over `options`; only fields a setting decision offers), `copybook_paths`, `importance` (the [importance table](#importance)), `context` (`{max_depth, max_chars}`, the [context](#decision-context) budget), `libraries` (optional, [library mappings](#library-mappings)) | `programs: [{file, status, program_id, package, class, output_path, diagnostics}]`, `dialect` (the settings in effect), `libraries: [{id, java, source, hash, classes, members, skipped}]` (with `libraries`). Parse failures are per-file diagnostics, not errors. |
| `translate` | `files` (relative paths; default all) | `files: [...]`, `decisions: [request…]`. Status `incomplete` if any file has pending decisions or failed. |
| `resolve` | `resolutions: [{decision_id, fingerprint?, selection: {kind: "candidate", candidate_id}}]` | `applied`, `rejected: [{decision_id, code, message}]`, `new_decisions` (raised by a newly applied structural candidate), `pending` |
| `expand_context` | `decision_id`, `refs: [ref]`, `max_chars?` | `decision_id`, `refs` (the nodes with their text, then their neighbours one level deeper as truncated nodes), `rejected: [{ref, code: unknown_ref, message}]`. An unknown `decision_id` is `invalid_params`. |
| `finalize` | — | Writes all files **only** if every program is translated with no pending decision; else `incomplete` with `blocking` and `pending`, nothing written. Result: `files_written`, `programs`; with libraries also `libraries_used` and `equivalence` (both written or not). |
| `shutdown` | — (no session) | `ok`, then the process exits 0 |

Resolution rejection codes: `unknown_decision`, `already_resolved`, `fingerprint_mismatch`,
`unsupported_selection` (only `candidate` exists in v1), `no_candidates`, `unknown_candidate`,
`duplicate_resolution`, `setting` (a [setting decision](#setting-decisions) is never resolved).

A session can't be used after `finalize` (`invalid_state`, `expand_context` included), and
re-translating a file is `invalid_state`.

### Importance

Every decision request carries `importance`: `low`, `normal`, `high` or `critical`. The worker
looks it up in the `initialize.importance` table, a list of `{reason, operation, importance}`
entries where `*` matches anything. The most specific entry wins: `(reason, operation)`, then
`(*, operation)`, then `(reason, *)`, then `(*, *)`. A decision that no entry matches is
`critical`, so a missing table can only make a provider stricter. An unknown tier or a duplicate
entry is `invalid_params`. Importance is part of the fingerprint: the same decision under
another tier is cached separately. The defaults are `cobol_to_java.DEFAULT_IMPORTANCE`.

### Decision context

`context.refs` is a graph of source nodes around the decision, built breadth-first from the
statement's operands (depth 0). Each further level follows a data item's parent group,
`REDEFINES` target, `VALUE`, copybook origin, and the statements that write it (`writes`) or
read it (`reads`), the `usage` of the data (the WRITE, CALL and DISPLAY statements it flows to),
and the decision's `paragraph` with the statements that PERFORM it. The order is deterministic
and `ref` ids are stable for the session.

Nodes up to `max_depth` keep their `text` until `max_chars` is used up. Every node after that,
and the nodes one level below `max_depth`, are listed with `truncated: true` and a one-line
`summary` only. `context.budget` reports `{max_depth, max_chars, used_chars, truncated,
omitted}`. A paragraph or data node with `text` also has `comments`: the source comments
directly above it and on its own lines (ProLeap drops comments, so the worker reads them from the
original file; none for copybook text), counted towards `max_chars`. `context.program` holds the
program's header comments (those before the DATA DIVISION) and its IDENTIFICATION DIVISION
paragraphs (`identification: {author, installation, date_written, security, remarks}`), capped
separately. Context is never part of the fingerprint. `expand_context` returns a truncated node's
text, so a provider can ask for exactly the part it needs.

### Candidate docs

A candidate may carry `docs: [{signature, summary}]`: each Java method its preview calls, with
the first paragraph of its Javadoc (inline tags reduced to text). Overloads are not resolved, so
a call lists every overload of its arity. Docs are advisory and never part of the fingerprint.

### Runtime choices

With `options.jdk_alternatives: true`, a statement that a builtin handler and its JDK twin can both
translate is a `runtime_choice` decision. Each candidate carries `uses`: `runtime` (the
translator's runtime library, exact) or `jdk` (plain JDK calls, approximate). When only the twin
can translate the statement it is the only candidate; when the twin cannot, the statement is
translated as without the option. The option is in the dialect hash only when it is on, so a run
without it has the same fingerprints as before.

### Candidate slots

A candidate may declare `slots: [{name, description}]`. Applying it raises one follow-up
decision per slot (in `resolve.new_decisions`), each with its own validated candidates and the
fact `slot`, so every question stays a flat choice. Nothing is generated until every slot is
resolved.

### Library mappings

`initialize.libraries` is `{version: 1, libraries: [{id, source: [selector], target, expose?,
hints?, max_chain?}], caches?: {maven, gradle}, target_project?}`
([library.schema.json](../protocol/library.schema.json)). The worker indexes each library's Java
source at `initialize` (a Maven `target` is looked up in `caches`, never downloaded) and checks
the selectors against the parsed programs. A use of a claimed part is a `library_call` decision
with a `library` object `{id, java, step, chain, part: {kind, name, file?, text?}}`; its facts
hold `library`, `library_hash`, `step` and `chain`, so the fingerprint changes with the library's
sources and with the steps chosen before. Every candidate set ends with `cobol:translate`.
Resolving a step returns the next step in `new_decisions`; the statement is translated when the
last step is resolved. `finalize` reports `libraries_used: [{id, java, declared_in_target?,
target_note?, uses: [{program, file, line, selector, part, outcome, chain, reason?, decided_by?,
java?}]}]` (`outcome`: `library`, `translated` or `pending`) and `equivalence` (`partial` when
any use calls a library, else `exact`). The orchestrator copies both into the report unread.

### Setting decisions

A decision with `setting: {field, values: {candidate_id: value}}` asks for a project setting the
source does not state (cobol-to-java: `dialect:encoding`, `dialect:collating_sequence`,
`dialect:out_of_range`, `dialect:sync_alignment`, `dialect:move_overlap`). Each candidate is one value with what it means;
`context.facts.usages` lists every statement of the program that depends on it, as evidence.
A setting a provider chose comes back in `initialize.settings`, with `initialize.setting_provenance`
(`{field: {provider, confidence}}`) saying who chose it; the worker notes it in the generated code.
A resolution may carry the provider's `confidence`; the worker writes who decided each statement,
with that confidence, as a line comment of the target language on the code it generates for it.
The orchestrator asks the provider about setting decisions before any other decision of the
pass. A passed, valid answer is not sent to `resolve`: the orchestrator starts a new session
whose `initialize.settings` holds the value and translates every program again, so no other
decision is asked under a setting that is about to change. Answers that disagree about one field
(two programs suggesting different values) are all held as `conflicting_settings`. Chosen
settings are reported as `settings: [{field, value, decision, resolution}]` in
`transpile-report.json` and in a "Settings chosen" section of `review.md`.

## Provider protocol `transpiler-provider/1`

A job's `provider: {"kind": "python", "command": [...], ...}` starts a persistent decision
provider. It speaks the same NDJSON envelope with protocol `transpiler-provider/1`:

- On startup it emits a `ready` event with `{provider, version}`.
- `decide {decisions, extra_context?}` returns `{answers}`. An answer is
  `{decision_id, fingerprint?, gate, selection?, need_context?, confidence?, probabilities?,
  review_priority?, rationale?, held_reason?}` (`held_reason`: why the provider holds a decision
  without choosing, such as `too_many_options`; reported on the held entry). `gate` is `passed`, `below_floor`, `abstained` or
  `need_context`, in which case `need_context` lists the truncated refs to open.
- `extra_context: [{decision_id, expansions, expansions_left, refs}]` carries the nodes
  returned by `expand_context` when a decision is asked again.
- `shutdown` gets `ok`, then the process exits 0.

The orchestrator applies only answers with `gate: passed` that `validate()` accepts (candidate
offered, fingerprint matches), and reports the others as `held` or `rejected`. Startup and
request timeouts, crashes and malformed output are handled as for workers.

## Lifecycle and failure handling (orchestrator side)

- `spawn_failed`, `startup_timeout`, `timeout` (per request), `crashed` (EOF or exit),
  `malformed` (non-protocol stdout), `id_mismatch` and `protocol_mismatch` are all reported in
  the run report with the worker's last 40 stderr lines.
- The process is killed if it does not exit after `shutdown`.
- These paths are covered by GoogleTest against a scriptable fake worker
  (`core/tests/fake_worker.cpp`) and, for providers (reported with `stage: provider`), a fake
  provider (`core/tests/fake_provider.cpp`): crashes, timeouts, malformed answers, gated
  answers, the `need_context` round trip, the expansion cap and fabricated candidates.

## translator.json

```json
{"name": "cobol-to-java", "source_language": "cobol", "target_language": "java",
 "protocol": "transpiler-worker/1",
 "command": ["{translator_dir}/java/build/install/cobol-to-java-worker/bin/cobol-to-java-worker"],
 "startup_timeout_seconds": 60}
```

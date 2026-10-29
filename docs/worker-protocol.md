# Worker protocol `transpiler-worker/1`

The orchestrator and a translator worker exchange newline-delimited JSON:
- one object per line;
- requests go on the worker's stdin, responses and events come on its stdout;
- **stderr is for diagnostics only** (the Java worker even redirects `System.out` to stderr).

Schemas: [`protocol/worker.schema.json`](../protocol/worker.schema.json),
[`mapping.schema.json`](../protocol/mapping.schema.json),
[`decision.schema.json`](../protocol/decision.schema.json),
[`provider.schema.json`](../protocol/provider.schema.json). Tests validate real traffic against
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
`invalid_params`, `invalid_mapping`, `internal_error`. After any error the worker keeps serving.

## Methods

| Method | Params | Result |
|---|---|---|
| `initialize` | `source_root`, `output_root` (absolute), `target_language`, `files: [{path, role: program\|copybook}]`, `mappings`, `layout`, `options` (the dialect keys of [`Dialect`](mapping-api.md#dialect-project-settings), plus `source_encoding`: `ISO-8859-1`; any other key is `invalid_params`), `copybook_paths`, `importance` (the [importance table](#importance)), `context` (`{max_depth, max_chars}`, the [context](#decision-context) budget) | `programs: [{file, status, program_id, package, class, output_path, diagnostics}]`, `dialect` (the settings in effect). Parse failures are per-file diagnostics, not errors. |
| `translate` | `files` (relative paths; default all) | `files: [...]`, `decisions: [request…]`. Status `incomplete` if any file has pending decisions or failed. |
| `resolve` | `resolutions: [{decision_id, fingerprint?, selection: {kind: "candidate", candidate_id}}]` | `applied`, `rejected: [{decision_id, code, message}]`, `new_decisions` (raised by a newly applied structural candidate), `pending` |
| `expand_context` | `decision_id`, `refs: [ref]`, `max_chars?` | `decision_id`, `refs` (the nodes with their text, then their neighbours one level deeper as truncated nodes), `rejected: [{ref, code: unknown_ref, message}]`. An unknown `decision_id` is `invalid_params`. |
| `finalize` | — | Writes all files **only** if every program is translated with no pending decision; else `incomplete` with `blocking` and `pending`, nothing written. Result: `files_written`, `programs`. |
| `shutdown` | — (no session) | `ok`, then the process exits 0 |

Resolution rejection codes: `unknown_decision`, `already_resolved`, `fingerprint_mismatch`,
`unsupported_selection` (only `candidate` exists in v1), `no_candidates`, `unknown_candidate`,
`duplicate_resolution`.

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
omitted}`. Context is never part of the fingerprint. `expand_context` returns a truncated node's
text, so a provider can ask for exactly the part it needs.

### Candidate slots

A candidate may declare `slots: [{name, description}]`. Applying it raises one follow-up
decision per slot (in `resolve.new_decisions`), each with its own validated candidates and the
fact `slot`, so every question stays a flat choice. Nothing is generated until every slot is
resolved.

## Provider protocol `transpiler-provider/1`

A job's `provider: {"kind": "python", "command": [...], ...}` starts a persistent decision
provider. It speaks the same NDJSON envelope with protocol `transpiler-provider/1`:

- On startup it emits a `ready` event with `{provider, version}`.
- `decide {decisions, extra_context?}` returns `{answers}`. An answer is
  `{decision_id, fingerprint?, gate, selection?, need_context?, confidence?, probabilities?,
  review_priority?, rationale?}`. `gate` is `passed`, `below_floor`, `abstained` or
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

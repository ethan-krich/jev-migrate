# Worker protocol `transpiler-worker/1`

The orchestrator and a translator worker exchange newline-delimited JSON:
- one object per line;
- requests go on the worker's stdin, responses and events come on its stdout;
- **stderr is for diagnostics only** (the Java worker even redirects `System.out` to stderr).

Schemas: [`protocol/worker.schema.json`](../protocol/worker.schema.json),
[`mapping.schema.json`](../protocol/mapping.schema.json),
[`decision.schema.json`](../protocol/decision.schema.json). Tests validate real traffic against
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
| `initialize` | `source_root`, `output_root` (absolute), `target_language`, `files: [{path, role: program\|copybook}]`, `mappings`, `layout`, `options` (the dialect keys of [`Dialect`](mapping-api.md#dialect-project-settings), plus `source_encoding`: `ISO-8859-1`; any other key is `invalid_params`), `copybook_paths` | `programs: [{file, status, program_id, package, class, output_path, diagnostics}]`, `dialect` (the settings in effect). Parse failures are per-file diagnostics, not errors. |
| `translate` | `files` (relative paths; default all) | `files: [...]`, `decisions: [request…]`. Status `incomplete` if any file has pending decisions or failed. |
| `resolve` | `resolutions: [{decision_id, fingerprint?, selection: {kind: "candidate", candidate_id}}]` | `applied`, `rejected: [{decision_id, code, message}]`, `new_decisions` (raised by a newly applied structural candidate), `pending` |
| `finalize` | — | Writes all files **only** if every program is translated with no pending decision; else `incomplete` with `blocking` and `pending`, nothing written. Result: `files_written`, `programs`. |
| `shutdown` | — (no session) | `ok`, then the process exits 0 |

Resolution rejection codes: `unknown_decision`, `already_resolved`, `fingerprint_mismatch`,
`unsupported_selection` (only `candidate` exists in v1), `no_candidates`, `unknown_candidate`,
`duplicate_resolution`.

A session can't be used after `finalize` (`invalid_state`), and re-translating a file is
`invalid_state`.

## Lifecycle and failure handling (orchestrator side)

- `spawn_failed`, `startup_timeout`, `timeout` (per request), `crashed` (EOF or exit),
  `malformed` (non-protocol stdout), `id_mismatch` and `protocol_mismatch` are all reported in
  the run report with the worker's last 40 stderr lines.
- The process is killed if it does not exit after `shutdown`.
- These paths are covered by GoogleTest against a scriptable fake worker
  (`core/tests/fake_worker.cpp`).

## translator.json

```json
{"name": "cobol-to-java", "source_language": "cobol", "target_language": "java",
 "protocol": "transpiler-worker/1",
 "command": ["{translator_dir}/java/build/install/cobol-to-java-worker/bin/cobol-to-java-worker"],
 "startup_timeout_seconds": 60}
```

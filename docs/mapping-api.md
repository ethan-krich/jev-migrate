# Mapping API

Mappings decide, per source operation, whether the Java worker translates it deterministically
or reports it as a decision. The defaults live in `cobol_to_java.mappings` and are ordinary
Python data. Users import a **fresh, independent copy** and change it with normal Python.

```python
from cobol_to_java import default_mappings, empty_mappings

m = default_mappings()                        # new registry on every call
[r.id for r in m.for_source("MOVE")]          # ['cobol.move.to_alphanumeric', 'cobol.move.to_numeric']
m.remove("DISPLAY")                           # remove every rule for an operation
m.remove_rule("cobol.move.to_numeric")        # remove exactly one rule by stable id
m.replace("cobol.add", priority=50)           # override fields of a rule in place
m.add("MOVE", handler="move_to_numeric", when={"target_category": "numeric"}, priority=150)
m.add("CALL", strategy="unresolved", when={"using": True}, priority=200, id="review.call-using")
m.add("CALL", strategy="callback", script="./callbacks.py", function="resolve_call")
m.to_dict()                                   # {"version": 1, "rules": [...]}  (protocol/mapping.schema.json)
```

## Rules

| Field | Meaning |
|---|---|
| `id` | Stable, unique. Defaults use `cobol.*`. `add()` without `id` derives `user.<op>.<handler-or-strategy>[.N]`. |
| `source` | Operation (COBOL verb): `MOVE`, `ADD`, `GOBACK`, `GO_TO`, `EXEC_SQL`, … |
| `strategy` | `builtin` (needs `handler`), `unresolved`, or `callback` (needs `script`, `function`) |
| `when` | Conditions over facts; a list matches any of its values |
| `priority` | Integer; higher wins |

**Precedence:** among matching rules, the highest `priority` wins, then the one with the most
`when` conditions, then the earliest registered. If the best rules tie on priority and
specificity but differ in effect, the result is an `ambiguous_mapping` decision; the worker
never picks arbitrarily.

**Validation:** Python validates against the catalog when a rule is added: unknown handlers,
handlers for another operation, unknown fact keys and malformed values all raise
`MappingError`. The worker validates again at `initialize` and rejects the whole registry with
`invalid_mapping`, listing every problem. Nothing is silently ignored.

Only plain JSON crosses process boundaries: no callables, AST objects or Python types.

## Builtin handlers (implemented in Java)

| Handler | Operation | Behaviour |
|---|---|---|
| `move_to_alphanumeric` | MOVE | receiver must be PIC X; pad/truncate right; integer numeric → digits |
| `move_to_numeric` | MOVE | receiver must be PIC 9; decimal alignment, truncation, sign |
| `add`, `subtract`, `multiply`, `divide` | … | exact `BigDecimal`, `ROUNDED`, `SIZE ERROR` |
| `compute` | COMPUTE | `+ - *`, unary minus |
| `display` | DISPLAY | stdout, GnuCOBOL format, `NO ADVANCING` |
| `if` | IF | conditions incl. AND/OR precedence |
| `perform` | PERFORM | procedure/inline, TIMES, UNTIL, VARYING, TEST AFTER |
| `stop_run`, `goback`, `exit`, `continue` | … | run-unit control |
| `call_program` | CALL | static `CALL 'X'` of a program in the project |

The running worker announces this list (with facts) in its `ready` event. A test keeps it equal
to `cobol_to_java.BUILTIN_HANDLERS` / `FACTS`.

## Facts (usable in `when`)

| Operation | Facts |
|---|---|
| MOVE | `source_category` (`alphanumeric`, `numeric`, `alphanumeric_literal`, `numeric_literal`, `figurative`, `group`, `unsupported`, `corresponding`), `target_category`, `source_figurative` (`zero`, `space`, null) |
| ADD / SUBTRACT / MULTIPLY | `form` (`to`/`from`/`by`, `giving`, `corresponding`), `rounded`, `size_error` |
| DIVIDE | `form` (`into`, `into_giving`, `by_giving`), `rounded`, `size_error`, `remainder` |
| COMPUTE | `rounded`, `size_error`, `receivers` |
| DISPLAY | `upon`, `no_advancing`, `operands` |
| IF | `has_else` |
| PERFORM | `form` (`procedure`/`inline`), `loop` (`once`, `times`, `until`, `varying`), `test`, `thru` |
| STOP | `kind` (`run`, `literal`, `giving`) |
| EXIT | `kind` (`plain`, `program`) |
| CALL | `program`, `program_kind` (`literal`/`identifier`), `program_in_project`, `using` |

## Callbacks

`strategy="callback"` stores `{script, function}`. In Phase 1 such operations surface as
`callback_required` decisions carrying the reference. `transpiler.callbacks.dispatch()` already
loads the script, calls `function(CallbackRequest)`, and validates that the answer is one of the
offered candidates. Wiring it through the orchestrator is Phase 2.

## Dialect (project settings)

`cobol_to_java.Dialect` holds what the COBOL source does not determine. Pass it as
`transpile(..., dialect=Dialect(...))`; `to_options()` becomes the worker's `initialize.options`.
A key set both by `dialect=` and `options=` raises `TranspilerError`.

| Field | Default | Meaning |
|---|---|---|
| `encoding` | `None` | `ascii` or `ebcdic`: the bytes of HIGH-VALUES / LOW-VALUES, class tests |
| `collating_sequence` | `None` | `native` (follows `encoding`), `ascii` or `ebcdic` |
| `out_of_range` | `None` | subscripts and reference modification out of range: `fail` (stop the run unit) or `decision` (refuse unprovable accesses) |
| `binary_truncate` | `True` | COMP/BINARY truncates to the PICTURE (GnuCOBOL `-fbinary-truncate`) |
| `sync_alignment` | `None` | whether SYNCHRONIZED inserts slack bytes |
| `debugging_lines` | `False` | compile `D` lines (otherwise comments, as GnuCOBOL; `WITH DEBUGGING MODE` also enables them) |
| `source_format` | `fixed` | `fixed`, `variable`, `tandem` |
| `file_assignments` | `{}` | `ASSIGN TO` name → run-time path |
| `when_compiled` | `None` | value of WHEN-COMPILED, `YYYY-MM-DDTHH:MM:SS` |

A program that needs an unset setting gets exactly one `missing_mapping` decision whose
operation is `dialect:<field>` (no candidates; facts `{"field": ...}`), and its statements that
depend on the setting stay placeholders. Set the field and translate again. The dialect is
part of every decision fingerprint and is recorded as `dialect` in `transpile-report.json`.
Debugging lines inside copybooks cannot be excluded and fail the file with
`unsupported_dialect` unless `debugging_lines=True`.

## Folder layout

`cobol_to_java.Layout` declares the folder convention. The worker applies it, because it owns
Java naming:

| Field | Default | Meaning |
|---|---|---|
| `base_package` | `generated` | root package |
| `java_source_dir` | `src/main/java` | under the output directory |
| `program_extensions` | `.cbl`, `.cob` | case-insensitive |
| `copybook_extensions` | `.cpy`, `.copy` | members without an extension also resolve |
| `copybook_dirs` | `copy`, `copybooks` | files inside are copybooks; searched up the tree |
| `runtime_package` | `<base>.runtime` | where the support classes are written |
| `package_overrides` | `{}` | `{"reports/month-end": "com.acme.reports"}` |
| `exclude` | `()` | globs relative to the source root |

Package rules for each directory segment:
- It is lower-cased.
- Characters other than letters and digits become `_`.
- A Java keyword gets a `_` suffix.
- A leading digit gets a `_` prefix.

The class name is PROGRAM-ID in PascalCase (`PAY-CALC` → `PayCalc`). A program's copybook
search path is:
1. its own directory;
2. `copybook_dirs` in each ancestor directory up to the source root;
3. `copybook_paths=[...]` passed to `transpile`.

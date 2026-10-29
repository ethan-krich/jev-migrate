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
| `move_to_alphanumeric` | MOVE | receiver PIC X / A or a reference modification; pad/truncate right (left with JUSTIFIED); integer numeric → digits; figuratives incl. `ALL "lit"` |
| `move_to_numeric` | MOVE | receiver PIC 9 (any USAGE); decimal alignment, truncation, sign |
| `move_group` | MOVE | receiver a group: the sending bytes unconverted (a numeric item's digits and sign as stored), space-padded / cut; a variable-length group with its DEPENDING ON item inside receives at its maximum length |
| `move_to_edited` | MOVE | receiver numeric-edited (GnuCOBOL editing: `Z * $ + - CR DB . , B 0 /`, fixed and floating insertion, BLANK WHEN ZERO) or alphanumeric-edited (insertion) |
| `move_from_edited` | MOVE | numeric-edited sender, numeric receiver: de-editing |
| `add`, `subtract`, `multiply`, `divide` | … | exact `BigDecimal`, `ROUNDED`, `SIZE ERROR`; `CORRESPONDING` (one unit per pair, no SIZE ERROR); `DIVIDE … REMAINDER` (remainder from the quotient truncated to its scale, as GnuCOBOL) |
| `compute_gnucobol` | COMPUTE | default: `+ - *` exact; `/` as GnuCOBOL 3.2's `cob_decimal_div` (scale `s1 - s2`, 38 extra digits, truncated); `**` right-associative with non-negative integer literal / unsigned integer exponents (others are `unsupported_construct`); division by zero is a size error |
| `compute` | COMPUTE | candidate only: `+ - *`, unary minus (refuses `/` and `**`) |
| `display` | DISPLAY | GnuCOBOL format, `NO ADVANCING`, `UPON CONSOLE / SYSOUT` (stdout) and `SYSERR` (stderr), mnemonic names; other devices are `missing_target_api` |
| `exhibit` | EXHIBIT | `[NAMED]`: `NAME = value` with the name as written (CHANGED is a decision) |
| `if` | IF | conditions incl. AND/OR precedence; `NEXT SENTENCE` branches |
| `evaluate` | EVALUATE | if/else chain; `ALSO`, `THRU`, `ANY`, `NOT`, `TRUE`/`FALSE`, condition subjects, `OTHER` |
| `perform` | PERFORM | procedure/inline, `THRU`, TIMES, UNTIL, VARYING [`AFTER`], TEST AFTER; `EXIT PERFORM [CYCLE]` inside inline bodies; overlapping or recursive ranges are `unsupported_construct` |
| `open_file`, `close_file` | OPEN, CLOSE | SEQUENTIAL, LINE SEQUENTIAL, RELATIVE and INDEXED files (`INPUT`, `OUTPUT`, `I-O` except LINE SEQUENTIAL, `EXTEND`); paths from `Dialect.file_assignments` |
| `read_file` | READ | `READ [NEXT / PREVIOUS] [INTO] [AT END] [NOT AT END]`, and for RELATIVE / INDEXED files in ACCESS RANDOM / DYNAMIC `READ [KEY IS key] [INVALID KEY] [NOT INVALID KEY]`; GnuCOBOL FILE STATUS values (00, 06 long line split, 10, 14, 21–24, 35, 41, 42, 46, 47, …); an unhandled error status ends the run unit (status 1) unless the file has FILE STATUS; the NOT phrase runs only after a successful READ |
| `write_file`, `rewrite_file` | WRITE, REWRITE | `[FROM]`, `[NOT] INVALID KEY` for RELATIVE / INDEXED files; LINE SEQUENTIAL strips trailing spaces and refuses control characters (status 71); `BEFORE / AFTER ADVANCING n LINES / PAGE / channel mnemonic` writes GnuCOBOL's newline / carriage return / form feed bytes (a file last written AFTER ADVANCING gets a newline at CLOSE); with `LINAGE` (files become LINE SEQUENTIAL, as in GnuCOBOL) pages are laid out with `LINAGE-COUNTER`, footing, top and bottom lines, and `[NOT] AT END-OF-PAGE` runs at the footing or page overflow; REWRITE of an INDEXED file with alternate keys in ACCESS SEQUENTIAL is `unsupported_construct` (GnuCOBOL's result depends on its internal state) |
| `delete_file`, `start_file` | DELETE, START | RELATIVE / INDEXED files: `DELETE file [RECORD]`, `START file [KEY IS = > >= < <= NOT < NOT > item]` (an INDEXED key or a leading part of one), `[NOT] INVALID KEY` |
| `sort` | SORT | `WITH DUPLICATES IN ORDER` required (otherwise `unsupported_construct` offering `sort_stable`); `USING` / `INPUT PROCEDURE`, `GIVING` / `OUTPUT PROCEDURE` |
| `sort_stable` | SORT | candidate only: the same stable sort without `DUPLICATES IN ORDER` |
| `merge` | MERGE | `USING` files in key order (ties in file order), `GIVING` / `OUTPUT PROCEDURE` |
| `release`, `return_record` | RELEASE, RETURN | `RELEASE rec [FROM]`, `RETURN file [INTO] AT END` |
| `go_to` | GO_TO | `GO TO p` and `GO TO p1 p2 … DEPENDING ON n` through the procedure dispatcher; a GO TO out of a PERFORMed range is `unsupported_construct` |
| `alter` | ALTER | always an `unsupported_construct` decision offering `alter_goto` |
| `alter_goto` | ALTER | candidate only: assigns the altered paragraph's GO TO target |
| `set_condition` | SET | `SET cond TO TRUE` moves the first value; `TO FALSE` needs `WHEN SET TO FALSE` |
| `accept_stdin` | ACCEPT | one stdin line into a PIC X item (spaces at end of input); `FROM SYSIN / CONSOLE`; other devices `missing_target_api` |
| `accept_environment` | ACCEPT | `FROM ENVIRONMENT-VALUE` (the variable named by `DISPLAY ... UPON ENVIRONMENT-NAME`), `FROM ENVIRONMENT "name"`, `ARGUMENT-NUMBER`, `ARGUMENT-VALUE`, `COMMAND-LINE` into alphanumeric items (ARGUMENT-NUMBER into numeric ones); an unset variable moves spaces and, like a missing argument, raises `ON EXCEPTION`; `DISPLAY ... UPON ENVIRONMENT-VALUE` sets a variable for the rest of the run |
| `accept_datetime` | ACCEPT | `FROM DATE [YYYYMMDD]`, `DAY [YYYYDDD]`, `DAY-OF-WEEK`, `TIME` via runtime `Clock` (`COB_CURRENT_DATE` fixes it, as in GnuCOBOL) |
| `stop_run`, `goback`, `exit`, `continue` | … | run-unit control; `EXIT PARAGRAPH / SECTION / PERFORM [CYCLE]`; `STOP literal` is `missing_target_api` |
| `call_program` | CALL | `CALL 'X'` of a program in the project (PROGRAM-IDs match case-sensitively, as written); `USING [BY REFERENCE | BY CONTENT]` items whose number and sizes match the callee's `PROCEDURE DIVISION USING` (otherwise `unsupported_construct`); `CALL identifier` with `Dialect.dynamic_calls` (the name is resolved at run time among the project's programs; a shorter argument than the callee's LINKAGE item, a module path or a GnuCOBOL system routine name ends the run unit); `ON EXCEPTION` / `ON OVERFLOW` when the program is not found or active (recursive CALL: RETURN-CODE -1), `NOT ON EXCEPTION` after it returns; a CALL of the program itself is `unsupported_construct` |
| `call_external` | CALL | candidate only: offered on the `missing_target_api` decision of a `CALL 'X'` whose program is not in the project. Its slots are separate decisions: `target` (`target:not_found`, GnuCOBOL's behaviour when the module is absent at run time: `ON EXCEPTION`, or the run unit ends with "module not found"; or `target:program:<name>`, a project program whose USING items match, under another name; never the calling program itself) and, with `USING`, `args` (`args:as_written`, or `args:by_content`, correct only if the target never changes its parameters). Nothing is generated until every slot is chosen |
| `entry` | ENTRY | no operation when reached; `CALL 'name'` of an ENTRY name starts at that paragraph with the entry's USING items (other LINKAGE items unbound, as in GnuCOBOL); an ENTRY that is not the first statement of a paragraph, or in a program with sections or GO TO, is `unsupported_construct` |
| `cancel_program` | CANCEL | `CANCEL 'X'` / identifier: the program's next CALL finds its WORKING-STORAGE initial; names that are not programs of the project are ignored, as in GnuCOBOL; CANCEL of an active program ends the run unit |
| `initialize` | INITIALIZE | item by item: `WITH FILLER`, `ALL TO VALUE`, `REPLACING category BY`, `THEN TO DEFAULT`; REDEFINES and FILLER skipped; every occurrence of tables |
| `set_index` | SET | `SET index TO n / index / item`, `SET item TO index`, `UP / DOWN BY` (indexes and USAGE INDEX items) |
| `search` | SEARCH | serial: from the index's value, `AT END` past the occurrences in use, first `WHEN` that holds, `VARYING` |
| `search_all` | SEARCH | `SEARCH ALL`: always an `unsupported_construct` decision (key order and uniqueness cannot be proven), offering the two candidates below |
| `search_all_binary`, `search_all_linear` | SEARCH | candidates only (no default rule): binary search on the first KEY / serial scan; correct only for ordered / unique keys |
| `string` | STRING | `DELIMITED BY SIZE / literal / item`, `WITH POINTER`, `[NOT] ON OVERFLOW`; the target keeps unwritten positions |
| `unstring` | UNSTRING | `DELIMITED BY [ALL] x OR [ALL] y`, `DELIMITER IN`, `COUNT IN`, `WITH POINTER`, `TALLYING IN`, `[NOT] ON OVERFLOW`; alphanumeric receivers |
| `inspect` | INSPECT | `TALLYING` (`CHARACTERS`, `ALL`, `LEADING`), `REPLACING` (`CHARACTERS`, `ALL`, `LEADING`, `FIRST`), `CONVERTING`, `BEFORE / AFTER INITIAL`; comparands in source order |

The running worker announces this list (with facts) in its `ready` event. A test keeps it equal
to `cobol_to_java.BUILTIN_HANDLERS` / `FACTS`.

## Facts (usable in `when`)

| Operation | Facts |
|---|---|
| MOVE | `source_category` (`alphanumeric`, `numeric`, `numeric_edited`, `alphanumeric_edited`, `alphanumeric_literal`, `numeric_literal`, `figurative`, `group`, `index`, `condition`, `unsupported`, `corresponding`; a reference modification is `alphanumeric`), `target_category`, `source_figurative` (`zero`, `space`, `quote`, `high_value`, `low_value`, `all`, null). A MOVE CORRESPONDING pair has the categories of its items. |
| ADD / SUBTRACT / MULTIPLY | `form` (`to`/`from`/`by`, `giving`, `corresponding`), `rounded`, `size_error` |
| DIVIDE | `form` (`into`, `into_giving`, `by_giving`), `rounded`, `size_error`, `remainder` |
| COMPUTE | `rounded`, `size_error`, `receivers` |
| DISPLAY | `upon` (device after resolving mnemonic names), `no_advancing`, `operands` |
| IF | `has_else` |
| PERFORM | `form` (`procedure`/`inline`), `loop` (`once`, `times`, `until`, `varying`), `test`, `thru` |
| STOP | `kind` (`run`, `literal`, `giving`) |
| EXIT | `kind` (`plain`, `program`, `paragraph`, `section`, `perform`, `perform_cycle`, `invalid`) |
| CALL | `program`, `program_kind` (`literal`/`identifier`), `program_in_project`, `using` |
| CANCEL | (none) |
| SET | `form` (`true`, `false`, `to`, `on_off`, `up_by`, `down_by`) |
| EVALUATE | `subjects`, `has_other`, `has_thru` |
| ACCEPT | `from` (`stdin`, `device`, `environment`, `argument_number`, `argument_value`, `command_line`, `date`, `date_yyyymmdd`, `day`, `day_yyyyddd`, `day_of_week`, `time`, `other_date`, `escape_key`, `message_count`) |
| EXHIBIT | `named`, `changed` |
| INITIALIZE | `replacing`, `to_value`, `with_filler`, `then_default` |
| SEARCH | `all`, `varying`, `at_end` |
| STRING | `pointer`, `overflow` |
| UNSTRING | `delimited`, `pointer`, `tallying`, `overflow` |
| INSPECT | `form` (`tallying`, `replacing`, `tallying_replacing`, `converting`) |
| GO_TO | `depending` |
| OPEN / CLOSE | (none) |
| READ | `at_end`, `into`, `organization` (`sequential`, `line_sequential`, `relative`, `indexed`, `sort`, `unsupported`), `direction` (`next`, `previous`, null), `key`, `invalid_key` |
| WRITE | `from`, `advancing`, `organization`, `invalid_key` |
| REWRITE | `from`, `organization`, `invalid_key` |
| DELETE | `organization`, `invalid_key` |
| START | `organization`, `key`, `invalid_key` |
| SORT | `duplicates`, `input_procedure`, `output_procedure` |
| MERGE | `output_procedure` |
| RELEASE | `from` |
| RETURN | `into` |
| ALTER | (none) |

## Importance

Every decision carries a deterministic `importance` tier: `low`, `normal`, `high` or
`critical`. A provider's gate uses it to select the minimum confidence for applying an answer
automatically ([Phase 2](architecture.md#importance-and-the-gate)). The table is ordinary Python
data keyed by `(reason, operation)`, where `*` matches anything:

```python
from cobol_to_java import DEFAULT_IMPORTANCE, default_importance

DEFAULT_IMPORTANCE[("*", "COMPUTE")]          # 'critical'
table = default_importance()                  # a fresh, mutable dict
table[("*", "DISPLAY")] = "normal"
table[("missing_target_api", "CALL")] = "critical"
transpile(..., importance=table)
```

The worker uses the most specific entry: `(reason, operation)`, then `(*, operation)`, then
`(reason, *)`, then `(*, *)`. A decision that no entry matches is `critical`. An unknown reason
or tier raises `TranspilerError`, and the worker rejects the table with `invalid_params`. The
tier is part of the decision fingerprint, so cached answers are never reused across tiers.

| Entry | Tier | Why |
|---|---|---|
| `(*, *)` | normal | default |
| `(unsupported_construct, *)`, `(missing_target_api, *)`, `(callback_required, *)` | high | no builtin translation, or a target the project must name |
| `(*, DATA)` | critical | a declaration shapes every statement that uses the data |
| `(*, ADD / SUBTRACT / MULTIPLY / DIVIDE / COMPUTE)` | critical | wrong digits are wrong amounts |
| `(*, MOVE / INITIALIZE)` | high | data conversion |
| `(*, WRITE / REWRITE / DELETE / SORT / MERGE / SEARCH)` | high | records written, ordered or looked up |
| `(*, CALL / GO_TO / ALTER)` | high | control flow and program linkage |
| `(*, DISPLAY / EXHIBIT)` | low | console output |

## Declaratives

`DECLARATIVES` sections with `USE AFTER ERROR / EXCEPTION PROCEDURE ON file / INPUT / OUTPUT /
I-O / EXTEND` become methods. When a file statement ends with an exception that its own `AT
END` / `INVALID KEY` / `END-OF-PAGE` phrase does not handle, the runtime runs the section for
that file, else the one for the mode of the file's last OPEN, and control continues after the
statement; without one the run unit ends unless the file has FILE STATUS (GnuCOBOL's rules).
`USE FOR DEBUGGING`, `GLOBAL` and declaratives in dispatcher-form programs are decisions.

## Intrinsic functions

Function results are operands, not handlers. Exact functions are translated: `UPPER-CASE`,
`LOWER-CASE`, `REVERSE`, `TRIM`, `LENGTH`, `MOD`, `REM`, `INTEGER`, `INTEGER-PART`, `ABS`,
`MAX`, `MIN`, `SUM`, `ORD-MAX`, `ORD-MIN`, `NUMVAL`, `NUMVAL-C`, `ORD`, `CHAR`, `CURRENT-DATE`
(through `Clock`), `INTEGER-OF-DATE`, `INTEGER-OF-DAY`, `DATE-OF-INTEGER`, `DAY-OF-INTEGER`.
DISPLAY reproduces GnuCOBOL's form for text and integer results; DISPLAY of `ABS`, `MAX`, `MIN`,
`SUM`, `NUMVAL[-C]` and non-integer `REM` is refused (MOVE them first). Floating-point functions
(`SQRT`, `LOG`, trigonometry, `RANDOM`, statistics, `PRESENT-VALUE`, `ANNUITY`) are
`unsupported_construct`.

## Procedure dispatcher

A program that uses `GO TO`, `PERFORM … THRU` or `ALTER` is generated in dispatcher form: each
paragraph (and each section's own statements and end) is numbered in source order and becomes a
method returning the number of the procedure a `GO TO` goes to, or `-1` when it ends normally.
`run(n)` executes procedures in order from `n`; `perform(from, thru)` pushes `thru` on a runtime
`PerformStack` and runs from `from`. When a procedure ends normally and it is the end of the
innermost PERFORM, that PERFORM returns, which is GnuCOBOL's return-point rule (so a `GO TO` out
of a range leaves the PERFORM waiting). Ranges that partially overlap, a PERFORM inside its own
range, and a `GO TO` out of a performed range are refused statically. Programs without these
statements keep the simpler form (one method per paragraph, fall-through calls).

## Callbacks

`strategy="callback"` stores `{script, function}`. Such operations surface as
`callback_required` decisions carrying the reference. `transpiler.callbacks.dispatch()` already
loads the script, calls `function(CallbackRequest)`, and validates that the answer is one of the
offered candidates. Wiring it through the orchestrator is not done yet; the Jev provider does
not answer these decisions, because they have no candidates.

## Dialect (project settings)

`cobol_to_java.Dialect` holds what the COBOL source does not determine. Pass it as
`transpile(..., dialect=Dialect(...))`; `to_options()` becomes the worker's `initialize.options`.
A key set both by `dialect=` and `options=` raises `TranspilerError`.

| Field | Default | Meaning |
|---|---|---|
| `encoding` | `None` | `ascii` or `ebcdic`: the bytes of HIGH-VALUES / LOW-VALUES, class tests, and the byte image of signed zoned and native binary items seen through groups, REDEFINES or reference modification (only `ascii`, GnuCOBOL's, is implemented) |
| `collating_sequence` | `None` | `native` (follows `encoding`), `ascii` or `ebcdic`: the order of alphanumeric comparisons and SORT / MERGE keys (`ebcdic` is GnuCOBOL's `-fdefault-colseq=EBCDIC`; INDEXED keys stay in byte order, as in GnuCOBOL) |
| `out_of_range` | `None` | subscripts, reference modification and OCCURS DEPENDING ON values that are not literals in range: `fail` (stop the run unit with status 1, as GnuCOBOL with `-fec=EC-BOUND-SUBSCRIPT,EC-BOUND-REF-MOD,EC-BOUND-ODO`) or `decision` (refuse them) |
| `binary_truncate` | `True` | COMP/BINARY arithmetic is limited by the PICTURE (GnuCOBOL `binary-truncate`); `False` limits it by the bytes only (MOVE still cuts to the PICTURE) |
| `sync_alignment` | `None` | whether SYNCHRONIZED inserts slack bytes before binary items (`False` only; `True` is a decision) |
| `debugging_lines` | `False` | compile `D` lines (otherwise comments, as GnuCOBOL; `WITH DEBUGGING MODE` also enables them) |
| `source_format` | `fixed` | `fixed`, `variable`, `tandem` |
| `file_assignments` | `{}` | `ASSIGN TO` name (literal text or word) → run-time path; an unmapped name is a `dialect:file_assignments` decision |
| `when_compiled` | `None` | value of WHEN-COMPILED, `YYYY-MM-DDTHH:MM:SS` |
| `dynamic_calls` | `None` | what `CALL identifier` can reach: `project` (the programs of this translation project; any other name is a program that is not found) |

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

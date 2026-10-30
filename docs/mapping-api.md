# Mapping API

Every COBOL statement and declaration ends up with one of four outcomes:

| Outcome | What is generated | Who acts | Section |
|---|---|---|---|
| **Plain Java** | Java control flow (`if`, `while`, `for`, `switch`, `return`, `break`) and `BigDecimal` methods | nobody: deterministic | [Plain Java](#plain-java) |
| **Runtime helper** | a call into the generated `runtime` package, which reproduces a GnuCOBOL rule that Java has no equivalent for | nobody: deterministic | [Runtime helpers](#runtime-helpers) |
| **Jev** | a decision *with candidates*: the worker generated each candidate (and compiled its preview) for this statement; a provider only picks one | `JevProvider`, `StaticProvider` or a person; pending without a provider | [Jev decisions](#jev-decisions) |
| **Unsupported** | a decision *without candidates*: nothing exact can be generated | a person changes the source, the `Dialect` or the mappings | [Unsupported](#unsupported) |

Most supported statements combine the first two. For example, an `IF` becomes a Java `if` with
`&&` / `||` / `!`, but each comparison inside it calls `Compare`, because COBOL pads the shorter
alphanumeric operand with spaces before comparing. `finalize` writes nothing while any decision
is pending.

Mappings choose, per statement, which builtin handler generates the Java (or that the statement
is a decision). They are ordinary Python data in `cobol_to_java.mappings`; see
[Configuring mappings](#configuring-mappings).

## Plain Java

These COBOL constructs become Java language constructs, with no runtime call for the construct
itself (operands and comparisons inside them may still use [runtime helpers](#runtime-helpers)):

| COBOL | Java |
|---|---|
| `IF … ELSE` | `if / else`; `AND`, `OR`, `NOT` as `&&`, `\|\|`, `!` (AND before OR, also inside `A = (B OR C AND D)`); condition-names with subscripts test their subscripted variable; `NEXT SENTENCE` jumps to the end of the sentence |
| `EVALUATE` | an `if / else if` chain |
| `PERFORM p` (no GO TO / THRU / ALTER in the program) | a method call; paragraphs are methods |
| `PERFORM … TIMES / UNTIL / VARYING`, `WITH TEST AFTER` | `for`, `while`, `do / while`, `while (true) { … break; }` |
| `EXIT PARAGRAPH / SECTION / PERFORM [CYCLE]` | `return`, `break`, `continue` |
| `GO TO … DEPENDING ON`, `GO TO`, `ALTER` | `switch` and `return n` to the [procedure dispatcher](#procedure-dispatcher); ALTER assigns an `int` field |
| `SEARCH` | a labelled `while` loop over the index, with `break` |
| `SEARCH ALL` | a binary-search `while` loop (cobc 3.2's probes) |
| `SET index TO / UP BY / DOWN BY` | `int` assignment, `+=`, `-=` |
| `ON SIZE ERROR`, `AT END`, `INVALID KEY`, `ON OVERFLOW`, `ON EXCEPTION` | a `boolean` from the helper, tested with `if / else` |
| `ADD`, `SUBTRACT`, `MULTIPLY` operands | `BigDecimal.add`, `subtract`, `multiply` |
| `CONTINUE` | nothing (an empty statement) |

## Runtime helpers

Classes in `transpiler.cobol.runtime` (copied into `<base>.runtime` with the program). Each one
exists because the plain Java result would differ from GnuCOBOL 3.2's:

| Helper | Used by | Why not plain Java |
|---|---|---|
| `Storage` | every data item | items are views over the bytes of a record, so group MOVEs, REDEFINES and reference modification see GnuCOBOL's bytes; Java objects do not share bytes |
| `NumericField` | numeric items, MOVE, arithmetic | `move` aligns decimals and cuts high- and low-order digits to the PICTURE; `store` adds ROUNDED (half away from zero) and size errors; DISPLAY, COMP-3 and binary encodings. `BigDecimal` has no PICTURE |
| `AlphanumericField` | PIC X items, groups, MOVE | left-justify, space-pad and right-truncate (or JUSTIFIED RIGHT); a Java `String` has no fixed width |
| `EditedField` | MOVE to / from edited items | PICTURE editing (`Z * $ + - CR DB . , B 0 /`, floating insertion, BLANK WHEN ZERO) and de-editing; `DecimalFormat` cannot express these pictures |
| `Arithmetic` | DIVIDE, COMPUTE | `divide` truncates to the receiver's scale (one extra digit for ROUNDED); `remainder` uses the truncated quotient; `over` is `cob_decimal_div` (38 extra digits, truncated); `power` handles `0 ** n`, `±1 ** n` and huge exponents. `BigDecimal.divide` throws on 1/3 and scales differently. `plus`, `minus`, `times` and `negate` are `add`, `subtract`, `multiply` and `negate` that pass on GnuCOBOL's NaN (`null`, from division by zero) so it becomes a size error; `sum` adds several operands |
| `Compare` | IF, EVALUATE, PERFORM UNTIL, SEARCH | `alphanumeric` pads the shorter operand with spaces (`"AB" = "AB  "` is true, `String.equals` says false); `ebcdic` orders by EBCDIC code; `figurative` compares with `SPACES`, `ALL "AB"`, …; class tests (`NUMERIC`, `ALPHABETIC`, …). `numeric` is only `BigDecimal.compareTo` |
| `Display` | DISPLAY, EXHIBIT | GnuCOBOL's output format, byte for byte with a single LF |
| `Accept`, `Environment`, `Clock` | ACCEPT, DISPLAY UPON ENVIRONMENT-* | one stdin line per ACCEPT; environment and arguments as GnuCOBOL gives them; `COB_CURRENT_DATE` fixes the clock |
| `Switches` | SET switch TO ON / OFF, switch ON / OFF STATUS conditions | GnuCOBOL's run-unit switches SWITCH-0 .. SWITCH-36, started from `COB_SWITCH_n` as libcob does |
| `StringOps` | STRING, UNSTRING, INSPECT | COBOL's delimiter, pointer, tally and BEFORE / AFTER INITIAL rules |
| `Functions` | intrinsic functions | GnuCOBOL's results, including zero for invalid arguments instead of an exception |
| `CobolFile`, `RelativeFile`, `IndexedFile`, `FileBase` | OPEN, CLOSE, READ, WRITE, REWRITE, DELETE, START, declaratives | GnuCOBOL's record formats and FILE STATUS values, LINAGE, and the rules for an unhandled error status |
| `SortFile` | SORT, MERGE, RELEASE, RETURN | stable sort by COBOL keys, merge ties in file order |
| `PerformStack` | programs in dispatcher form | GnuCOBOL's PERFORM return points (a GO TO out of a range leaves the PERFORM waiting) |
| `ProgramSupport`, `StopRun`, `Goback`, `CobolProgram` | CALL, CANCEL, STOP RUN, GOBACK, EXIT PROGRAM, EXTERNAL | run-unit rules: STOP RUN from any depth, RETURN-CODE after CALL, recursive CALL and CANCEL of an active program |
| `Bounds` | subscripts, reference modification, OCCURS DEPENDING ON (`out_of_range = "fail"`) | ends the run unit with GnuCOBOL's message instead of an `IndexOutOfBoundsException` |

## Supported statements

Each builtin handler is implemented in the Java worker and selected by a default rule (see
[Configuring mappings](#configuring-mappings)). "Java" summarises the generated code: the
[plain Java](#plain-java) constructs and [runtime helpers](#runtime-helpers) it uses. Anything
a handler does not cover is listed under [Unsupported](#unsupported).

### Data movement

| Handler | Default rule | Java | Behaviour |
|---|---|---|---|
| `move_to_alphanumeric` | MOVE, `target_category` alphanumeric | `AlphanumericField.set` | receiver PIC X / A or a reference modification; pad/truncate right (left with JUSTIFIED); integer numeric → digits; figuratives incl. `ALL "lit"` |
| `move_to_numeric` | MOVE, `target_category` numeric | `NumericField.move`, `moveAlphanumeric` | receiver PIC 9 (any USAGE); decimal alignment, truncation, sign. From an alphanumeric item or reference modification: libcob's `cob_move_alphanum_to_display` (leading spaces and one sign skipped, digits aligned on the first decimal point, spaces and the numeric separator ignored, any other byte or a second point moves zero; packed and binary receivers through a 36-digit intermediate). An alphanumeric literal into a DISPLAY item without decimals or separate sign is copied as written, right-aligned, as cobc does at compile time. `ALL "digits"` moves 38 repeated digits |
| `move_group` | MOVE, `target_category` group | `AlphanumericField.set` on the group's bytes | receiver a group: the sending bytes unconverted (a numeric item's digits and sign as stored), space-padded / cut; a variable-length group with its DEPENDING ON item inside receives at its maximum length |
| `move_to_edited` | MOVE, `target_category` numeric- or alphanumeric-edited | `EditedField.move` | receiver numeric-edited (GnuCOBOL editing: `Z * $ + - CR DB . , B 0 /`, fixed and floating insertion, BLANK WHEN ZERO) or alphanumeric-edited (insertion) |
| `move_from_edited` | MOVE, numeric-edited → numeric (wins over `move_to_numeric`: more conditions) | `NumericField.move(edited.value())` | de-editing |
| `initialize` | INITIALIZE | a `move` / `set` per elementary item | item by item: `WITH FILLER`, `ALL TO VALUE`, `REPLACING category BY`, `THEN TO DEFAULT`; REDEFINES and FILLER skipped; every occurrence of tables, the occurrences in use of an OCCURS DEPENDING ON table |
| `set_condition` | SET, `form` true / false | a `move` / `set` of the condition's value | `SET cond TO TRUE` moves the first value; `TO FALSE` needs `WHEN SET TO FALSE` |
| `set_switch` | SET, `form` on_off | `Switches.set` | `SET name TO ON / OFF` of a SPECIAL-NAMES `SWITCH-0` .. `SWITCH-36` name; `ON / OFF STATUS` condition-names test `Switches.get` in IF, EVALUATE and PERFORM UNTIL; each switch starts from `COB_SWITCH_n` (`1…` or `ON`), as libcob reads it |
| `set_index` | SET, `form` to / up_by / down_by | `int` assignment, `+=`, `-=` | `SET index TO n / index / item`, `SET item TO index`, `UP / DOWN BY` (indexes and USAGE INDEX items) |

### Arithmetic

| Handler | Default rule | Java | Behaviour |
|---|---|---|---|
| `add`, `subtract`, `multiply`, `divide` | ADD, SUBTRACT, MULTIPLY, DIVIDE | `BigDecimal.add / subtract / multiply`, `Arithmetic.divide / remainder / sum`, `NumericField.store`; size error as a `boolean` + `if` | exact `BigDecimal`, `ROUNDED`, `SIZE ERROR`; `CORRESPONDING` (one unit per pair, no SIZE ERROR); `DIVIDE … REMAINDER` (remainder from the quotient truncated to its scale, as GnuCOBOL) |
| `compute_gnucobol` | COMPUTE | `Arithmetic.plus / minus / times / over / power / negate`, `NumericField.store` | `+ - *` exact; `/` as GnuCOBOL 3.2's `cob_decimal_div` (scale `s1 - s2`, 38 extra digits, truncated); `**` right-associative with non-negative integer literal / unsigned integer exponents; division by zero is a size error |
| `compute` | none (candidate only) | `BigDecimal.add / subtract / multiply / negate` | `+ - *`, unary minus; refuses `/` and `**` |

### Control flow

| Handler | Default rule | Java | Behaviour |
|---|---|---|---|
| `if` | IF | `if / else`, comparisons via `Compare` | conditions incl. AND/OR precedence; `NEXT SENTENCE` branches |
| `evaluate` | EVALUATE | `if / else if` chain | `ALSO`, `THRU`, `ANY`, `NOT`, `TRUE`/`FALSE`, condition subjects, `OTHER` |
| `perform` | PERFORM | method calls, `for`, `while`, `do / while`; `perform(from, thru)` in dispatcher form | procedure/inline, `THRU`, TIMES, UNTIL, VARYING [`AFTER`], TEST AFTER; `EXIT PERFORM [CYCLE]` inside inline bodies |
| `go_to` | GO_TO | `return n`; `switch` for DEPENDING ON | `GO TO p` and `GO TO p1 p2 … DEPENDING ON n` through the [procedure dispatcher](#procedure-dispatcher) |
| `alter` | ALTER | `int` field assignment | assigns the altered paragraph's GO TO target in the procedure dispatcher; the state lives with the program (kept across CALLs, reset by CANCEL) |
| `stop_run`, `goback`, `exit`, `continue` | STOP (`kind` run), GOBACK, EXIT, CONTINUE | `ProgramSupport.stopRun / goback / exitProgram`; `return`, `break`, `continue` | run-unit control; `EXIT PARAGRAPH / SECTION / PERFORM [CYCLE]` |
| `search` | SEARCH, `all` false | labelled `while` + `break` | serial: from the index's value, `AT END` past the occurrences in use, first `WHEN` that holds, `VARYING` |
| `search_all` | SEARCH, `all` true | binary-search `while` loop | `SEARCH ALL … WHEN key = value [AT END]` on the first KEY as cobc 3.2's binary search (same probes, so duplicate keys and keys out of order behave as in GnuCOBOL) |

### Programs

| Handler | Default rule | Java | Behaviour |
|---|---|---|---|
| `call_program` | CALL, `program_in_project` true (or `CALL identifier` with `Dialect.dynamic_calls`) | `ProgramSupport.call / callEntry / callNamed` | `CALL 'X'` of a program in the project (PROGRAM-IDs match case-sensitively, as written); `USING [BY REFERENCE \| BY CONTENT]` items whose number and sizes match the callee's `PROCEDURE DIVISION USING`; `CALL identifier` with `Dialect.dynamic_calls` (the name is resolved at run time among the project's programs; a shorter argument than the callee's LINKAGE item, a module path or a GnuCOBOL system routine name ends the run unit); `ON EXCEPTION` / `ON OVERFLOW` when the program is not found or active (recursive CALL: RETURN-CODE -1), `NOT ON EXCEPTION` after it returns |
| `call_external` | none (candidate only, see [Jev decisions](#jev-decisions)) | `ProgramSupport` | a `CALL 'X'` whose program is not in the project |
| `entry` | ENTRY | no operation; `ProgramSupport.callEntry` at the caller | no operation when reached; `CALL 'name'` of an ENTRY name starts at that paragraph with the entry's USING items (other LINKAGE items unbound, as in GnuCOBOL) |
| `cancel_program` | CANCEL | `ProgramSupport.cancel` | `CANCEL 'X'` / identifier: the program's next CALL finds its WORKING-STORAGE initial; names that are not programs of the project are ignored, as in GnuCOBOL; CANCEL of an active program ends the run unit |

### Console and environment

| Handler | Default rule | Java | Behaviour |
|---|---|---|---|
| `display` | DISPLAY | `Display.line / noAdvancing` | GnuCOBOL format, `NO ADVANCING`, `UPON CONSOLE / SYSOUT` (stdout) and `SYSERR` (stderr), mnemonic names |
| `exhibit` | EXHIBIT, `changed` false | `Display` | `[NAMED]`: `NAME = value` with the name as written |
| `accept_stdin` | ACCEPT, `from` stdin / device | `Accept.line` | one stdin line into a PIC X item (spaces at end of input); `FROM SYSIN / CONSOLE` |
| `accept_environment` | ACCEPT, `from` environment / argument / command line | `Environment` | `FROM ENVIRONMENT-VALUE` (the variable named by `DISPLAY ... UPON ENVIRONMENT-NAME`), `FROM ENVIRONMENT "name"`, `ARGUMENT-NUMBER`, `ARGUMENT-VALUE`, `COMMAND-LINE` into alphanumeric items (ARGUMENT-NUMBER into numeric ones); an unset variable moves spaces and, like a missing argument, raises `ON EXCEPTION`; `DISPLAY ... UPON ENVIRONMENT-VALUE` sets a variable for the rest of the run |
| `accept_datetime` | ACCEPT, `from` a date or time | `Clock` | `FROM DATE [YYYYMMDD]`, `DAY [YYYYDDD]`, `DAY-OF-WEEK`, `TIME`, `MICROSECOND-TIME` (`COB_CURRENT_DATE` fixes it, as in GnuCOBOL; without a fraction of a second the microseconds stay those of the system clock) |

### Strings

| Handler | Default rule | Java | Behaviour |
|---|---|---|---|
| `string` | STRING | `StringOps.Concat` | `DELIMITED BY SIZE / literal / item`, `WITH POINTER`, `[NOT] ON OVERFLOW`; the target keeps unwritten positions |
| `unstring` | UNSTRING | `StringOps.Unstring` | `DELIMITED BY [ALL] x OR [ALL] y`, `DELIMITER IN`, `COUNT IN`, `WITH POINTER`, `TALLYING IN`, `[NOT] ON OVERFLOW`; alphanumeric receivers, and numeric ones (each field moved as alphanumeric data, as `move_to_numeric` does) |
| `inspect` | INSPECT | `StringOps.Inspect` | `TALLYING` (`CHARACTERS`, `ALL`, `LEADING`), `REPLACING` (`CHARACTERS`, `ALL`, `LEADING`, `FIRST`), `CONVERTING`, `BEFORE / AFTER INITIAL`; comparands in source order |

### Files and sorting

| Handler | Default rule | Java | Behaviour |
|---|---|---|---|
| `open_file`, `close_file` | OPEN, CLOSE | `CobolFile`, `RelativeFile`, `IndexedFile` | SEQUENTIAL, LINE SEQUENTIAL, RELATIVE and INDEXED files (`INPUT`, `OUTPUT`, `I-O` except LINE SEQUENTIAL, `EXTEND`); paths from `Dialect.file_assignments` |
| `read_file` | READ | `file.read(…)` returning a `boolean` for AT END / INVALID KEY | `READ [NEXT / PREVIOUS] [INTO] [AT END] [NOT AT END]`, and for RELATIVE / INDEXED files in ACCESS RANDOM / DYNAMIC `READ [KEY IS key] [INVALID KEY] [NOT INVALID KEY]`; GnuCOBOL FILE STATUS values (00, 06 long line split, 10, 14, 21–24, 35, 41, 42, 46, 47, …); an unhandled error status ends the run unit (status 1) unless the file has FILE STATUS; the NOT phrase runs only after a successful READ |
| `write_file`, `rewrite_file` | WRITE, REWRITE | `file.write / rewrite` | `[FROM]`, `[NOT] INVALID KEY` for RELATIVE / INDEXED files; LINE SEQUENTIAL strips trailing spaces and refuses control characters (status 71); `BEFORE / AFTER ADVANCING n LINES / PAGE / channel mnemonic` writes GnuCOBOL's newline / carriage return / form feed bytes (a file last written AFTER ADVANCING gets a newline at CLOSE); with `LINAGE` (files become LINE SEQUENTIAL, as in GnuCOBOL) pages are laid out with `LINAGE-COUNTER`, footing, top and bottom lines, and `[NOT] AT END-OF-PAGE` runs at the footing or page overflow |
| `delete_file`, `start_file` | DELETE, START | `RelativeFile`, `IndexedFile` | RELATIVE / INDEXED files: `DELETE file [RECORD]`, `START file [KEY IS = > >= < <= NOT < NOT > item]` (an INDEXED key or a leading part of one), `[NOT] INVALID KEY` |
| `sort` | SORT | `SortFile` | a stable sort with or without `WITH DUPLICATES IN ORDER` (GnuCOBOL 3.2 returns equal keys in release order either way); `USING` / `INPUT PROCEDURE`, `GIVING` (several files: each record written to each in turn) / `OUTPUT PROCEDURE`; USING / GIVING files may be RELATIVE or INDEXED (sequential or dynamic access); `COLLATING SEQUENCE` of an alphabet declared `NATIVE`, `STANDARD-1`, `ASCII` or `EBCDIC` orders alphanumeric keys |
| `merge` | MERGE | `SortFile` | as libcob: the `USING` files' records sorted stably (files in key order merge, ties in file order), `GIVING` / `OUTPUT PROCEDURE`, `COLLATING SEQUENCE` as for SORT |
| `release`, `return_record` | RELEASE, RETURN | `SortFile` | `RELEASE rec [FROM]`, `RETURN file [INTO] AT END` |

The running worker announces this list (with facts) in its `ready` event. A test keeps it equal
to `cobol_to_java.BUILTIN_HANDLERS` / `FACTS`.

### Intrinsic functions

Function results are operands, not handlers. Exact functions are translated through
`Functions`: `UPPER-CASE`, `LOWER-CASE`, `REVERSE`, `TRIM`, `LENGTH`, `MOD`, `REM`, `INTEGER`,
`INTEGER-PART`, `ABS`, `MAX`, `MIN`, `SUM`, `ORD-MAX`, `ORD-MIN`, `NUMVAL`, `NUMVAL-C`, `ORD`,
`CHAR`, `CURRENT-DATE` (through `Clock`), `INTEGER-OF-DATE`, `INTEGER-OF-DAY`,
`DATE-OF-INTEGER`, `DAY-OF-INTEGER`. DISPLAY reproduces GnuCOBOL's form of every result:
`ABS`, `MAX` and `MIN` show the winning argument's own picture and usage (a literal as an item of
its own digits, scale and sign); `SUM`, `NUMVAL`, `NUMVAL-C`, `MOD`, `REM`, `INTEGER` and
`INTEGER-PART` show the field libcob sizes from the value (`Functions.decimalDisplay`: trailing
fractional zeros dropped, nine digits up to 32 bits, twenty up to 64, a minus sign only when
negative, higher digits cut as GnuCOBOL cuts them).

Literals: `"text"`, `X"hex"` (the bytes written in hexadecimal) and `Z"text"` (the text and a NUL
byte); national (`N"…"`) and DBCS literals are decisions.

### Procedure dispatcher

A program that uses `GO TO`, `PERFORM … THRU` or `ALTER` is generated in dispatcher form: each
paragraph (and each section's own statements and end) is numbered in source order and becomes a
method returning the number of the procedure a `GO TO` goes to, or `-1` when it ends normally.
`run(n)` executes procedures in order from `n`; `perform(from, thru)` pushes `thru` on a runtime
`PerformStack` and runs from `from`. When a procedure ends normally and it is the end of the
innermost PERFORM, that PERFORM returns, which is GnuCOBOL's return-point rule (so a `GO TO` out
of a range leaves the PERFORM waiting, and so does an EXIT SECTION from a paragraph that is also
PERFORMed on its own; GnuCOBOL's frame stack holds 255 PERFORMs, beyond which the run unit ends with
"stack overflow"). Programs without these statements keep the simpler form
(one method per paragraph, fall-through calls).

### Declaratives

`DECLARATIVES` sections with `USE AFTER ERROR / EXCEPTION PROCEDURE ON file / INPUT / OUTPUT /
I-O / EXTEND` become methods. When a file statement ends with an exception that its own `AT
END` / `INVALID KEY` / `END-OF-PAGE` phrase does not handle, the runtime (`FileBase`) runs the
section for that file, else the one for the mode of the file's last OPEN, and control continues
after the statement; without one the run unit ends unless the file has FILE STATUS (GnuCOBOL's
rules).

## Jev decisions

A decision with candidates. The worker generates each candidate for the statement and compiles
its preview; `JevProvider` (or `StaticProvider`, or a person) only picks one. Without a provider
these stay pending. With `default_mappings()` there are these kinds:

| Decision | When | Candidates |
|---|---|---|
| STOP literal | `STOP "text"` (`missing_target_api`): it waits for an operator, and the target system decides what replaces the console | `builtin:stop_literal_accept` (GnuCOBOL's behaviour: the literal on standard output, then one line of standard input read and discarded) and `builtin:stop_literal_continue` (shown, and the program goes on; correct only if nothing must wait for an operator) |
| DISPLAY UPON PRINTER / SYSPCH | the device's output goes where the target system says (`missing_target_api`) | `builtin:display_printer` (PRINTER only; GnuCOBOL's behaviour: appended to the file `COB_DISPLAY_PRINT_FILE` names, else standard output; a print command in `COB_DISPLAY_PRINT_PIPE` ends the run unit), `builtin:display_device_stdout`, `builtin:display_device_stderr` |
| CALL of a program outside the project | `CALL 'X'` and `X` is not in the project (`missing_target_api`) | `builtin:call_external`, whose slots are separate decisions, and nothing is generated until every slot is chosen: `target` (`target:not_found`, GnuCOBOL's behaviour when the module is absent at run time: `ON EXCEPTION`, or the run unit ends with "module not found"; or `target:program:<name>`, a project program whose USING items match, under another name; never the calling program itself) and, with `USING`, `args` (`args:as_written`, or `args:by_content`, correct only if the target never changes its parameters) |
| An unset `encoding`, `collating_sequence`, `out_of_range`, `sync_alignment` or `move_overlap` | a statement depends on the setting (`dialect:<field>`, `missing_mapping`) | the field's values (`setting:ascii`, `setting:ebcdic`, …), with every statement that depends on it as evidence; the program is translated again with the chosen value. See [Dialect](#dialect-project-settings) |
| A handler choice | only after the project changes the rules: a removed rule or `strategy="unresolved"` (`missing_mapping`), rules that tie (`ambiguous_mapping`), or a rule whose handler does not apply (`invalid_mapping`) | the verb's handlers that generate for that statement, e.g. the other MOVE handlers, or `compute` (`+ - *` only) for COMPUTE |

Every default rule covers every fact value of its verb, except ACCEPT
`escape_key` / `message_count` / `other_date` (`TODAYS-DATE`, `YEAR`, `TIMER`, …: other dialects, which cobc rejects) and EXHIBIT `changed`. No handler generates those,
so they are [unsupported](#unsupported) rather than handler choices.

A decision's [importance](#importance) tier does not change who answers it. It only sets the
confidence floor when `JevProvider` runs with a `Gate`.

## Unsupported

A decision without candidates. `JevProvider` never receives it, because there is nothing for any
provider to pick; a person changes the source, the `Dialect` or the mappings. Every
`unsupported_construct` and `callback_required` decision is of this kind today
(`HandlerException.unprovable`, which would offer candidates, has no caller yet).

### Statements

The table gives examples, not a full list; the [supported statements](#supported-statements),
the supported subset in the README and
[deferred constructs](architecture.md#constructs-deferred-to-phase-2) cover the rest.

| Operation | Unsupported |
|---|---|
| MOVE | a sending category the receiver does not take: non-integer numeric → alphanumeric and `MOVE ALL` of a numeric literal (invalid COBOL: cobc rejects them), alphanumeric data into edited, BLANK WHEN ZERO or P-scaled numeric items, a group or an alphanumeric literal into a packed or binary item, `ALL` of non-digits into a numeric item; a receiver sharing storage with the sender; CORRESPONDING problems |
| ADD, SUBTRACT, MULTIPLY, DIVIDE | non-numeric operands or receivers, CORRESPONDING with SIZE ERROR, REMAINDER without GIVING (or with several receivers) |
| COMPUTE | `**` with a non-integer or negative exponent, floating-point intrinsic functions |
| DISPLAY | `UPON` a name that is not an output device of GnuCOBOL (`missing_target_api`, no candidates), AT / exception phrases |
| EXHIBIT | `CHANGED` (`missing_mapping`; GnuCOBOL 3.2 does not implement it) |
| IF, EVALUATE | nested parenthesised abbreviations, WHEN objects that do not match the subjects |
| PERFORM | overlapping or recursive ranges, a PERFORM inside its own range, TIMES of a non-integer (invalid COBOL: cobc rejects it) |
| GO TO, ALTER | `GO TO.` with no target, ALTER of a paragraph that holds more than a GO TO |
| STOP, GOBACK, EXIT, CONTINUE | `STOP RUN GIVING` of a non-numeric item |
| CALL | `RETURNING` / `GIVING` into a non-numeric item or together with ON EXCEPTION, `BY VALUE` (cobc 3.2 warns that its handling is unfinished and may change, so there is no stable reference), USING items whose number or sizes differ from the callee's, a CALL of the program itself, both ON OVERFLOW and ON EXCEPTION, a path or system routine with no matching program |
| CANCEL, ENTRY | CANCEL of a library name; ENTRY that is not the first statement of a paragraph, or in a program with sections or GO TO |
| SET | `SET` of a mnemonic name that is not SWITCH-0 .. SWITCH-36, several values, `TO FALSE` without `WHEN SET TO FALSE`, pointers |
| ACCEPT | `FROM ESCAPE KEY`, message count, other date forms (`missing_mapping`); other devices (`missing_target_api`); non-alphanumeric receivers; exception phrases |
| INITIALIZE, STRING, UNSTRING, INSPECT | INITIALIZE of RENAMES groups, `TO VALUE` of OCCURS DEPENDING ON groups (GnuCOBOL 3.2 sets occurrences beyond the count unevenly) and groups with `OCCURS 0 TO`; `STRING … FOR`; UNSTRING into edited items; INSPECT REPLACING with lengths that differ |
| SEARCH | a table without INDEXED BY; SEARCH ALL without KEY, or WHEN other than `key = value` on the first key |
| OPEN, CLOSE, READ, WRITE, REWRITE, DELETE, START | LOCK phrases, `NO REWIND`, I-O of LINE SEQUENTIAL, READ PREVIOUS of a sequential file, REWRITE of an INDEXED file with alternate keys in ACCESS SEQUENTIAL (GnuCOBOL's result depends on its internal state) |
| SORT, MERGE, RELEASE, RETURN | `COLLATING SEQUENCE` of an alphabet of literals or several alphabets, USING / GIVING of a RELATIVE or INDEXED file in ACCESS RANDOM |
| ENABLE, DISABLE, SEND, RECEIVE, PURGE, GENERATE, INITIATE, TERMINATE, EXEC SQL / CICS / SQLIMS | everything: no handler (`missing_mapping`) |

Operands are checked the same way in every statement: floating-point literals and functions
(`SQRT`, `LOG`, trigonometry, `RANDOM`, statistics, `PRESENT-VALUE`, `ANNUITY`), national
literals, unsupported special registers, and subscripts or reference modification that cannot be
proven in range (with `out_of_range` set to `decision`) make the statement
`unsupported_construct`. DISPLAY of `ABS`, `MAX` or `MIN` of an arithmetic expression is refused
(MOVE it first): GnuCOBOL shows it in the picture of a temporary it does not document.

### Settings and declarations

| Decision | Operation | What the person does |
|---|---|---|
| An unset `dynamic_calls` or `when_compiled` | `dialect:<field>` | set the field |
| `ASSIGN TO` name missing from `file_assignments` | `dialect:file_assignments` | add the path (it is never guessed) |
| Declarations the subset does not cover (COMP-1 / COMP-2, POINTER, `N` pictures, ON / OFF STATUS of names other than SWITCH-0 .. SWITCH-36, SCREEN / REPORT sections, …) | DATA, ENVIRONMENT, IDENTIFICATION, PROCEDURE (`unsupported_construct`) | change the source |
| `USE FOR DEBUGGING` in a source with `WITH DEBUGGING MODE`, `GLOBAL` declaratives of a program that contains nested programs (GnuCOBOL needs GLOBAL files for them), declaratives in dispatcher-form programs | PROCEDURE | change the source |
| A rule with `strategy="callback"` | the verb (`callback_required`) | answer it, until callbacks are routed (see [Callbacks](#callbacks)) |

## Configuring mappings

The defaults live in `cobol_to_java.mappings` and are ordinary Python data. Users import a
**fresh, independent copy** and change it with normal Python.

```python
from cobol_to_java import default_mappings, empty_mappings

m = default_mappings()                        # new registry on every call
[r.id for r in m.for_source("MOVE")]          # ['cobol.move.to_alphanumeric', 'cobol.move.to_numeric', ...]
m.remove("DISPLAY")                           # remove every rule for an operation
m.remove_rule("cobol.move.to_numeric")        # remove exactly one rule by stable id
m.replace("cobol.add", priority=50)           # override fields of a rule in place
m.add("MOVE", handler="move_to_numeric", when={"target_category": "numeric"}, priority=150)
m.add("CALL", strategy="unresolved", when={"using": True}, priority=200, id="review.call-using")
m.add("CALL", strategy="callback", script="./callbacks.py", function="resolve_call")
m.to_dict()                                   # {"version": 1, "rules": [...]}  (protocol/mapping.schema.json)
```

### Rules

| Field | Meaning |
|---|---|
| `id` | Stable, unique. Defaults use `cobol.*`. `add()` without `id` derives `user.<op>.<handler-or-strategy>[.N]`. |
| `source` | Operation (COBOL verb): `MOVE`, `ADD`, `GOBACK`, `GO_TO`, `EXEC_SQL`, … |
| `strategy` | `builtin` (needs `handler`), `unresolved`, or `callback` (needs `script`, `function`) |
| `when` | Conditions over [facts](#facts-usable-in-when); a list matches any of its values |
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

### Facts (usable in `when`)

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
| ACCEPT | `from` (`stdin`, `device`, `environment`, `argument_number`, `argument_value`, `command_line`, `date`, `date_yyyymmdd`, `day`, `day_yyyyddd`, `day_of_week`, `time`, `microsecond_time`, `other_date`, `escape_key`, `message_count`) |
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

### Callbacks

`strategy="callback"` stores `{script, function}`. Such operations surface as
`callback_required` decisions carrying the reference. `transpiler.callbacks.dispatch()` already
loads the script, calls `function(CallbackRequest)`, and validates that the answer is one of the
offered candidates. Wiring it through the orchestrator is not done yet; the Jev provider does
not answer these decisions, because they have no candidates.

## Importance

Every decision carries a deterministic `importance` tier: `low`, `normal`, `high` or
`critical`. A provider's optional gate uses it to select the minimum confidence for applying an answer
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
| `source_format` | `fixed` | `fixed`, `variable`, `tandem`, `free` (GnuCOBOL's `-free`: rewritten line for line as `variable` format; `*>` comments, `>>D` debugging lines and `>>SOURCE FORMAT FREE` are carried over; other directives, literals continued on the next line and COPY fail the file with `unsupported_dialect`) |
| `file_assignments` | `{}` | `ASSIGN TO` name (literal text or word) → run-time path; an unmapped name is a `dialect:file_assignments` decision |
| `when_compiled` | `None` | value of WHEN-COMPILED, `YYYY-MM-DDTHH:MM:SS` |
| `dynamic_calls` | `None` | what `CALL identifier` can reach: `project` (the programs of this translation project; any other name is a program that is not found) |
| `move_overlap` | `None` | how a MOVE whose receiver starts inside its alphanumeric sender copies: `temporary` (as through a temporary item: GnuCOBOL's default memmove) or `ibm` (left to right, so the first bytes repeat: IBM's MVC, GnuCOBOL `-fmove-ibm`; only where cobc copies that way, with fixed sizes and a sender at least as long). The COBOL standard leaves this undefined, so the program's original compiler decides |

A program that needs an unset setting gets exactly one `missing_mapping` decision whose
operation is `dialect:<field>` (facts `{"field": ..., "usages": [...]}`, every statement that
depends on the setting), and those statements stay placeholders. Set the field and translate
again, or let a provider choose: `encoding`, `collating_sequence`, `out_of_range`,
`sync_alignment` and `move_overlap` decisions are [setting decisions](worker-protocol.md#setting-decisions) whose
candidates are the field's values (`setting:ascii`, `setting:ebcdic`, ...); the other fields have
no candidates. The dialect is
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

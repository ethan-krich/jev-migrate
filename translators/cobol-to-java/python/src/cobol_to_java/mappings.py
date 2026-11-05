"""The default COBOL-to-Java mapping table.

Each entry names a builtin handler implemented by the Java worker; Python holds only the
declarative choice of handler, never transformation logic. Rule ids are stable: replace or
remove them by id (``registry.replace("cobol.display", ...)``).
"""

from __future__ import annotations

from types import MappingProxyType

# Builtin handler id -> COBOL operation it implements. Must equal the worker's catalog
# (checked by tests against the worker's `ready` announcement).
BUILTIN_HANDLERS = MappingProxyType({
    "move_to_alphanumeric": "MOVE",
    "move_to_numeric": "MOVE",
    "add": "ADD",
    "subtract": "SUBTRACT",
    "multiply": "MULTIPLY",
    "divide": "DIVIDE",
    "compute": "COMPUTE",
    "compute_gnucobol": "COMPUTE",
    "display": "DISPLAY",
    "display_printer": "DISPLAY",
    "display_device_stdout": "DISPLAY",
    "display_device_stderr": "DISPLAY",
    "if": "IF",
    "perform": "PERFORM",
    "stop_run": "STOP",
    "stop_literal_accept": "STOP",
    "stop_literal_continue": "STOP",
    "goback": "GOBACK",
    "exit": "EXIT",
    "continue": "CONTINUE",
    "call_program": "CALL",
    "call_external": "CALL",
    "cancel_program": "CANCEL",
    "entry": "ENTRY",
    "set_condition": "SET",
    "evaluate": "EVALUATE",
    "accept_stdin": "ACCEPT",
    "accept_datetime": "ACCEPT",
    "accept_environment": "ACCEPT",
    "exhibit": "EXHIBIT",
    "move_group": "MOVE",
    "move_to_edited": "MOVE",
    "move_from_edited": "MOVE",
    "initialize": "INITIALIZE",
    "string": "STRING",
    "go_to": "GO_TO",
    "open_file": "OPEN",
    "close_file": "CLOSE",
    "read_file": "READ",
    "read_file_unlocked": "READ",
    "write_file": "WRITE",
    "rewrite_file": "REWRITE",
    "delete_file": "DELETE",
    "start_file": "START",
    "sort": "SORT",
    "merge": "MERGE",
    "release": "RELEASE",
    "return_record": "RETURN",
    "alter": "ALTER",
    "unstring": "UNSTRING",
    "inspect": "INSPECT",
    "set_switch": "SET",
    "set_index": "SET",
    "search": "SEARCH",
    "search_all": "SEARCH",
    # JDK twins: the same statement with plain JDK calls (approximate), see JDK_TWINS.
    "add_jdk": "ADD",
    "subtract_jdk": "SUBTRACT",
    "multiply_jdk": "MULTIPLY",
    "divide_jdk": "DIVIDE",
    "compute_gnucobol_jdk": "COMPUTE",
    "display_jdk": "DISPLAY",
    "if_jdk": "IF",
    "string_jdk": "STRING",
    "unstring_jdk": "UNSTRING",
    "inspect_jdk": "INSPECT",
})

# Builtin handler -> its JDK twin. With options.jdk_alternatives (on by default when Jev decides)
# a statement both can translate is a runtime_choice decision: the runtime library's exact
# GnuCOBOL semantics, or plain JDK calls (double arithmetic, java.lang.Math, String, System.out),
# which are approximate. Jev picks per statement whether the exactness is needed.
JDK_TWINS = MappingProxyType({
    "add": "add_jdk",
    "subtract": "subtract_jdk",
    "multiply": "multiply_jdk",
    "divide": "divide_jdk",
    "compute_gnucobol": "compute_gnucobol_jdk",
    "display": "display_jdk",
    "if": "if_jdk",
    "string": "string_jdk",
    "unstring": "unstring_jdk",
    "inspect": "inspect_jdk",
})

# Handlers that no default rule selects: the worker offers them as candidates on a decision
# (compute when a rule is missing, call_external for a CALL outside the project, the STOP literal
# and DISPLAY UPON PRINTER / SYSPCH ones for a console or device the target system decides, and the
# JDK twins of a runtime_choice).
CANDIDATE_HANDLERS = frozenset({
    "compute", "call_external", "stop_literal_accept", "stop_literal_continue",
    "display_printer", "display_device_stdout", "display_device_stderr", "read_file_unlocked",
    *JDK_TWINS.values(),
})

# Facts the worker computes per operation instance; usable as `when` condition keys.
FACTS = MappingProxyType({
    "MOVE": frozenset({"source_category", "target_category", "source_figurative", "operation_in_library"}),
    "ADD": frozenset({"form", "rounded", "size_error"}),
    "SUBTRACT": frozenset({"form", "rounded", "size_error"}),
    "MULTIPLY": frozenset({"form", "rounded", "size_error"}),
    "DIVIDE": frozenset({"form", "rounded", "size_error", "remainder"}),
    "COMPUTE": frozenset({"rounded", "size_error", "receivers"}),
    "DISPLAY": frozenset({"upon", "no_advancing", "operands", "operation_in_library"}),
    "IF": frozenset({"has_else"}),
    "PERFORM": frozenset({"form", "loop", "test", "thru", "paragraph_in_library"}),
    "STOP": frozenset({"kind"}),
    "EXIT": frozenset({"kind"}),
    "CALL": frozenset({"program", "program_kind", "program_in_project", "program_in_library", "using"}),
    "CANCEL": frozenset(),
    "ENTRY": frozenset(),
    "SET": frozenset({"form"}),
    "EVALUATE": frozenset({"subjects", "has_other", "has_thru"}),
    "ACCEPT": frozenset({"from", "operation_in_library"}),
    "EXHIBIT": frozenset({"named", "changed"}),
    "INITIALIZE": frozenset({"replacing", "to_value", "with_filler", "then_default"}),
    "SEARCH": frozenset({"all", "varying", "at_end"}),
    "STRING": frozenset({"pointer", "overflow"}),
    "UNSTRING": frozenset({"delimited", "pointer", "tallying", "overflow"}),
    "INSPECT": frozenset({"form"}),
    "GO_TO": frozenset({"depending"}),
    "OPEN": frozenset(),
    "CLOSE": frozenset(),
    "READ": frozenset({"at_end", "into", "organization", "direction", "key", "invalid_key"}),
    "WRITE": frozenset({"from", "advancing", "organization", "invalid_key"}),
    "REWRITE": frozenset({"from", "organization", "invalid_key"}),
    "DELETE": frozenset({"organization", "invalid_key"}),
    "START": frozenset({"organization", "key", "invalid_key"}),
    "SORT": frozenset({"duplicates", "input_procedure", "output_procedure"}),
    "MERGE": frozenset({"output_procedure"}),
    "RELEASE": frozenset({"from"}),
    "RETURN": frozenset({"into"}),
    "ALTER": frozenset(),
})

# COBOL statement verbs known to the parser (rules may target any of them, e.g. to mark one
# unresolved explicitly). GOBACK is spelled without the underscore.
OPERATIONS = frozenset({
    "ACCEPT", "ADD", "ALTER", "CALL", "CANCEL", "CLOSE", "COMPUTE", "CONTINUE", "DECLARATIVES", "DELETE",
    "DISABLE", "DISPLAY", "DIVIDE", "ENABLE", "ENTRY", "EVALUATE", "EXEC_CICS", "EXEC_SQL", "EXEC_SQLIMS",
    "EXHIBIT", "EXIT", "GENERATE", "GOBACK", "GO_TO", "IF", "INITIALIZE", "INITIATE", "INSPECT", "MERGE",
    "MOVE", "MULTIPLY", "OPEN", "PERFORM", "PURGE", "READ", "RECEIVE", "RELEASE", "RETURN", "REWRITE",
    "SEARCH", "SEND", "SET", "SORT", "START", "STOP", "STRING", "SUBTRACT", "TERMINATE", "UNSTRING", "USE",
    "WRITE",
})

PRIORITY = 100
LIBRARY_PRIORITY = PRIORITY + 10

# (decision reason, operation) -> importance tier, "*" matching anything. The worker puts the
# tier of the most specific entry on every decision: (reason, operation), then (*, operation),
# then (reason, *), then (*, *). The tier selects a provider's confidence floor and is part of
# the fingerprint. Override it with transpile(importance=...).
DEFAULT_IMPORTANCE = MappingProxyType({
    ("*", "*"): "normal",
    ("unsupported_construct", "*"): "high",
    ("missing_target_api", "*"): "high",
    ("callback_required", "*"): "high",
    # A library call is not equivalence-checked: every step of it is reviewed as a high-stakes choice.
    ("library_call", "*"): "high",
    # Declarations shape every statement that uses the data.
    ("*", "DATA"): "critical",
    # Arithmetic and data conversion: wrong digits are wrong amounts.
    ("*", "ADD"): "critical",
    ("*", "SUBTRACT"): "critical",
    ("*", "MULTIPLY"): "critical",
    ("*", "DIVIDE"): "critical",
    ("*", "COMPUTE"): "critical",
    ("*", "MOVE"): "high",
    ("*", "INITIALIZE"): "high",
    # Records written, ordered or looked up.
    ("*", "WRITE"): "high",
    ("*", "REWRITE"): "high",
    ("*", "DELETE"): "high",
    ("*", "SORT"): "high",
    ("*", "MERGE"): "high",
    ("*", "SEARCH"): "high",
    # Control flow and program linkage.
    ("*", "CALL"): "high",
    ("*", "GO_TO"): "high",
    ("*", "ALTER"): "high",
    # Console output.
    ("*", "DISPLAY"): "low",
    ("*", "EXHIBIT"): "low",
})

# Rules with the library strategy: (id, source, when, description). They match only when
# transpile(libraries=...) claims the part, and turn its use into a library_call decision. They
# take LIBRARY_PRIORITY so they win over the handler rules of the same verb. (A claimed FUNCTION is
# offered wherever it appears, whatever the rule.)
DEFAULT_LIBRARY_RULES: tuple[tuple[str, str, dict, str], ...] = (
    ("cobol.call.library", "CALL", {"program_in_library": True},
     "CALL of a program or system routine a library claims: library calls or the exact translation (library_call)"),
    ("cobol.perform.library", "PERFORM", {"paragraph_in_library": True},
     "PERFORM of a paragraph or section a library claims (by name, or as a procedure copybook) (library_call)"),
    ("cobol.move.library", "MOVE", {"operation_in_library": True}, "MOVE claimed by a library (library_call)"),
    ("cobol.display.library", "DISPLAY", {"operation_in_library": True}, "DISPLAY claimed by a library (library_call)"),
    ("cobol.accept.library", "ACCEPT", {"operation_in_library": True}, "ACCEPT claimed by a library (library_call)"),
)

# (id, source, handler, when, description)
DEFAULT_RULES: tuple[tuple[str, str, str, dict, str], ...] = (
    ("cobol.move.to_alphanumeric", "MOVE", "move_to_alphanumeric", {"target_category": "alphanumeric"},
     "MOVE into PIC X: pad/truncate on the right"),
    ("cobol.move.to_numeric", "MOVE", "move_to_numeric", {"target_category": "numeric"},
     "MOVE into PIC 9: decimal alignment with truncation"),
    ("cobol.add", "ADD", "add", {}, "ADD TO / GIVING"),
    ("cobol.subtract", "SUBTRACT", "subtract", {}, "SUBTRACT FROM / GIVING"),
    ("cobol.multiply", "MULTIPLY", "multiply", {}, "MULTIPLY BY / GIVING"),
    ("cobol.divide", "DIVIDE", "divide", {}, "DIVIDE INTO / BY / GIVING"),
    ("cobol.compute", "COMPUTE", "compute_gnucobol", {}, "COMPUTE with + - * / and integer ** (GnuCOBOL 3.2 scaling)"),
    ("cobol.display", "DISPLAY", "display", {}, "DISPLAY to stdout"),
    ("cobol.if", "IF", "if", {}, "IF / ELSE"),
    ("cobol.perform", "PERFORM", "perform", {}, "PERFORM procedure or inline"),
    ("cobol.stop_run", "STOP", "stop_run", {}, "STOP RUN (STOP literal has no target)"),
    ("cobol.goback", "GOBACK", "goback", {}, "GOBACK"),
    ("cobol.exit", "EXIT", "exit", {}, "EXIT / EXIT PROGRAM"),
    ("cobol.continue", "CONTINUE", "continue", {}, "CONTINUE"),
    ("cobol.call", "CALL", "call_program", {}, "CALL of a program in the project (by identifier with Dialect.dynamic_calls)"),
    ("cobol.cancel", "CANCEL", "cancel_program", {}, "CANCEL"),
    ("cobol.entry", "ENTRY", "entry", {}, "ENTRY reached in the flow (no operation)"),
    ("cobol.set.condition", "SET", "set_condition", {"form": ("true", "false")}, "SET condition-name TO TRUE / FALSE"),
    ("cobol.evaluate", "EVALUATE", "evaluate", {}, "EVALUATE as an if/else chain"),
    ("cobol.accept.stdin", "ACCEPT", "accept_stdin", {"from": ("stdin", "device")},
     "ACCEPT from standard input (other devices have no target)"),
    ("cobol.accept.environment", "ACCEPT", "accept_environment",
     {"from": ("environment", "argument_number", "argument_value", "command_line")},
     "ACCEPT FROM ENVIRONMENT-VALUE / ENVIRONMENT / ARGUMENT-NUMBER / ARGUMENT-VALUE / COMMAND-LINE"),
    ("cobol.accept.datetime", "ACCEPT", "accept_datetime",
     {"from": ("date", "date_yyyymmdd", "day", "day_yyyyddd", "day_of_week", "time", "microsecond_time")},
     "ACCEPT FROM DATE / DAY / TIME / MICROSECOND-TIME"),
    ("cobol.exhibit", "EXHIBIT", "exhibit", {"changed": False}, "EXHIBIT [NAMED]"),
    ("cobol.move.to_group", "MOVE", "move_group", {"target_category": "group"},
     "MOVE into a group: bytes as alphanumeric data"),
    ("cobol.move.to_edited", "MOVE", "move_to_edited", {"target_category": ("numeric_edited", "alphanumeric_edited")},
     "MOVE into an edited item: PICTURE editing / insertion"),
    ("cobol.move.from_edited", "MOVE", "move_from_edited", {"source_category": "numeric_edited", "target_category": "numeric"},
     "MOVE numeric-edited to numeric: de-editing"),
    ("cobol.initialize", "INITIALIZE", "initialize", {}, "INITIALIZE item by item"),
    ("cobol.string", "STRING", "string", {}, "STRING ... INTO"),
    ("cobol.unstring", "UNSTRING", "unstring", {}, "UNSTRING ... INTO alphanumeric items"),
    ("cobol.inspect", "INSPECT", "inspect", {}, "INSPECT TALLYING / REPLACING / CONVERTING"),
    ("cobol.open", "OPEN", "open_file", {}, "OPEN files"),
    ("cobol.close", "CLOSE", "close_file", {}, "CLOSE files"),
    ("cobol.read", "READ", "read_file", {}, "READ (sequential, or by key)"),
    ("cobol.write", "WRITE", "write_file", {}, "WRITE [ADVANCING] [END-OF-PAGE] [INVALID KEY]"),
    ("cobol.rewrite", "REWRITE", "rewrite_file", {}, "REWRITE"),
    ("cobol.delete", "DELETE", "delete_file", {}, "DELETE of a RELATIVE / INDEXED record"),
    ("cobol.start", "START", "start_file", {}, "START of a RELATIVE / INDEXED file"),
    ("cobol.sort", "SORT", "sort", {}, "SORT as a stable sort (GnuCOBOL's tie order)"),
    ("cobol.merge", "MERGE", "merge", {}, "MERGE of files in key order"),
    ("cobol.release", "RELEASE", "release", {}, "RELEASE to a sort file"),
    ("cobol.return", "RETURN", "return_record", {}, "RETURN from a sort file"),
    ("cobol.go_to", "GO_TO", "go_to", {}, "GO TO / GO TO DEPENDING ON (procedure dispatcher)"),
    ("cobol.alter", "ALTER", "alter", {}, "ALTER as an assignment of the GO TO target"),
    ("cobol.set.switch", "SET", "set_switch", {"form": "on_off"}, "SET switch TO ON / OFF (SWITCH-0 .. SWITCH-36)"),
    ("cobol.set.index", "SET", "set_index", {"form": ("to", "up_by", "down_by")}, "SET index TO / UP BY / DOWN BY"),
    ("cobol.search", "SEARCH", "search", {"all": False}, "serial SEARCH"),
    ("cobol.search_all", "SEARCH", "search_all", {"all": True},
     "SEARCH ALL as GnuCOBOL's binary search"),
)

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
    "display": "DISPLAY",
    "if": "IF",
    "perform": "PERFORM",
    "stop_run": "STOP",
    "goback": "GOBACK",
    "exit": "EXIT",
    "continue": "CONTINUE",
    "call_program": "CALL",
    "set_condition": "SET",
    "evaluate": "EVALUATE",
    "accept_stdin": "ACCEPT",
    "accept_datetime": "ACCEPT",
    "exhibit": "EXHIBIT",
    "move_group": "MOVE",
    "move_to_edited": "MOVE",
    "move_from_edited": "MOVE",
    "initialize": "INITIALIZE",
    "string": "STRING",
    "unstring": "UNSTRING",
    "inspect": "INSPECT",
    "set_index": "SET",
    "search": "SEARCH",
    "search_all": "SEARCH",
    "search_all_binary": "SEARCH",
    "search_all_linear": "SEARCH",
})

# Handlers that no default rule selects: the worker offers them as candidates on a decision
# (they are correct only under conditions it cannot prove, e.g. SEARCH ALL key order).
CANDIDATE_HANDLERS = frozenset({"search_all_binary", "search_all_linear"})

# Facts the worker computes per operation instance; usable as `when` condition keys.
FACTS = MappingProxyType({
    "MOVE": frozenset({"source_category", "target_category", "source_figurative"}),
    "ADD": frozenset({"form", "rounded", "size_error"}),
    "SUBTRACT": frozenset({"form", "rounded", "size_error"}),
    "MULTIPLY": frozenset({"form", "rounded", "size_error"}),
    "DIVIDE": frozenset({"form", "rounded", "size_error", "remainder"}),
    "COMPUTE": frozenset({"rounded", "size_error", "receivers"}),
    "DISPLAY": frozenset({"upon", "no_advancing", "operands"}),
    "IF": frozenset({"has_else"}),
    "PERFORM": frozenset({"form", "loop", "test", "thru"}),
    "STOP": frozenset({"kind"}),
    "EXIT": frozenset({"kind"}),
    "CALL": frozenset({"program", "program_kind", "program_in_project", "using"}),
    "SET": frozenset({"form"}),
    "EVALUATE": frozenset({"subjects", "has_other", "has_thru"}),
    "ACCEPT": frozenset({"from"}),
    "EXHIBIT": frozenset({"named", "changed"}),
    "INITIALIZE": frozenset({"replacing", "to_value", "with_filler", "then_default"}),
    "SEARCH": frozenset({"all", "varying", "at_end"}),
    "STRING": frozenset({"pointer", "overflow"}),
    "UNSTRING": frozenset({"delimited", "pointer", "tallying", "overflow"}),
    "INSPECT": frozenset({"form"}),
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
    ("cobol.compute", "COMPUTE", "compute", {}, "COMPUTE with +, -, *"),
    ("cobol.display", "DISPLAY", "display", {}, "DISPLAY to stdout"),
    ("cobol.if", "IF", "if", {}, "IF / ELSE"),
    ("cobol.perform", "PERFORM", "perform", {}, "PERFORM procedure or inline"),
    ("cobol.stop_run", "STOP", "stop_run", {}, "STOP RUN (STOP literal has no target)"),
    ("cobol.goback", "GOBACK", "goback", {}, "GOBACK"),
    ("cobol.exit", "EXIT", "exit", {}, "EXIT / EXIT PROGRAM"),
    ("cobol.continue", "CONTINUE", "continue", {}, "CONTINUE"),
    ("cobol.call", "CALL", "call_program", {}, "static CALL of a program in the project"),
    ("cobol.set.condition", "SET", "set_condition", {"form": ("true", "false")}, "SET condition-name TO TRUE / FALSE"),
    ("cobol.evaluate", "EVALUATE", "evaluate", {}, "EVALUATE as an if/else chain"),
    ("cobol.accept.stdin", "ACCEPT", "accept_stdin", {"from": ("stdin", "device")},
     "ACCEPT from standard input (other devices have no target)"),
    ("cobol.accept.datetime", "ACCEPT", "accept_datetime",
     {"from": ("date", "date_yyyymmdd", "day", "day_yyyyddd", "day_of_week", "time")}, "ACCEPT FROM DATE / DAY / TIME"),
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
    ("cobol.set.index", "SET", "set_index", {"form": ("to", "up_by", "down_by")}, "SET index TO / UP BY / DOWN BY"),
    ("cobol.search", "SEARCH", "search", {"all": False}, "serial SEARCH"),
    ("cobol.search_all", "SEARCH", "search_all", {"all": True},
     "SEARCH ALL (a decision unless key order is proven; binary and serial candidates)"),
)

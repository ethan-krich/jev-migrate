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
})

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
)

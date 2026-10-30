"""The two demo projects of scripts/jev-smoke.sh.

Neither project states its collating sequence (the order of alphanumeric comparisons), which
COBOL source never does; the team sets only its own policy (out_of_range). Each program's checks
only work under the order it was written for, so the code is the evidence:

* mainframe/PARTCHK.cbl accepts a code character when it is not `< "A"` and not `> "9"`, a
  range that holds letters and digits only in EBCDIC (digits sort after letters); in ASCII it is
  empty and every code is rejected.
* workstation/TICKETS.cbl sorts characters with a cascade `< "0"`, `<= "9"`, `< "A"`, `<= "Z"`,
  `>= "a"`, which is ASCII order; in EBCDIC every letter falls into "other".

`written_for` and `expected_output` are what the programs do under the order they were written
for (checked by translating with that setting given explicitly); the smoke run compares Jev's
choice with them.
"""

PROJECTS = {
    "mainframe": {
        "about": "part-code check from a mainframe stock system",
        "dialect": {"out_of_range": "fail"},
        "written_for": {"collating_sequence": "ebcdic"},
        "main": "com.example.mainframe.Partchk",
        "expected_output": ["AB12 accepted", "CD7Z rejected", "x9Q1 rejected", "RS40 accepted", "M#55 rejected"],
    },
    "workstation": {
        "about": "help-desk ticket-key check from a PC application",
        "dialect": {"out_of_range": "fail"},
        "written_for": {"collating_sequence": "ascii"},
        "main": "com.example.workstation.Tickets",
        "expected_output": ["Ab3x valid", "abcd invalid", "AB12 invalid", "zZ9! invalid", "Q7rT valid"],
    },
}

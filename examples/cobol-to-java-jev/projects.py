"""The demo projects of scripts/jev-smoke.sh.

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

* billing/ calls three date routines of its own (common/dates/: DATEADD, WEEKDAY, DATEFMT) that
  the team has replaced with a Java library, libraries/acme-dates (com.acme:acme-dates:1.0), which
  their target project libraries/billing-app already depends on. The smoke run installs that
  library (jar and sources jar) into a scratch Maven repository under build/, maps
  Cobol.path("common/dates/") to it, and lets Jev build each call. Library calls are not
  equivalence-checked, so the run translates the project again without the library and runs both:
  `expected_output` is what the COBOL translation prints.
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
    "billing": {
        "about": "invoice due dates, with the shop's date routines mapped to a Java date library",
        "dialect": {"encoding": "ascii", "collating_sequence": "native", "out_of_range": "fail"},
        "main": "com.example.billing.Billing",
        "library": {"id": "acme.dates", "cobol_path": "common/dates/", "coordinates": "com.acme:acme-dates:1.0",
                    "sources": "libraries/acme-dates/src/main/java", "target_project": "libraries/billing-app"},
        "expected_output": ["20261115 +0030 -> 20261215 15/12/2026 day 2",
                            "20261220 +0045 -> 20270203 03/02/2027 day 3",
                            "20270226 +0010 -> 20270308 08/03/2027 day 1"],
    },
}

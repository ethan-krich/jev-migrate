"""The payroll project of benchmarks/jev-vs-llm/bench.py.

payroll/ is a weekly payroll in three programs and three copybooks:

* PAYROLL.cbl (main) copies the employee table (copy/EMPTAB.cpy), the run totals
  (copy/PAYTOT.cpy) and the payslip record (copy/SLIPREC.cpy), computes each gross pay, calls
  TAXCALC for the tax, adds up the totals and calls PAYSLIP once per employee.
* PAYSLIP.cbl gets the payslip record (the same copybook, in its LINKAGE SECTION), edits the
  amounts, replaces hyphens in the name (INSPECT), builds the line (STRING) and displays it.
* common/tax/TAXCALC.cbl computes the tax withheld. The team has replaced it with a Java library,
  libraries/acme-tax (com.acme:acme-tax:1.0), which their target project libraries/payroll-app
  already depends on. The benchmark maps Cobol.path("common/tax/") to it, so the CALL becomes a
  library_call decision.

Every ADD, SUBTRACT, COMPUTE, DISPLAY, IF, STRING and INSPECT is also a runtime_choice: the
runtime library (exact GnuCOBOL decimal semantics) or plain JDK code (doubles, String). Money
needs the runtime: in doubles, 37.5 * 18.40 is 689.9999999999999, which PIC 9(5)V99 truncates to
689.99. A loop counter or an operator message does not.

expected/stdout.txt is what GnuCOBOL 3.2 prints, recorded with ./scripts/docker.sh cobol -I
payroll/copy payroll/PAYROLL.cbl payroll/PAYSLIP.cbl payroll/common/tax/TAXCALC.cbl (from this
directory's parent paths). The library computes the
same tax as TAXCALC, so a translation that calls it prints the same.

`RUBRIC` is the construct each decision should get, keyed by (file, line, library step or None):
the set of acceptable candidate ids and why. Lines are those of the COBOL source.
"""

MAIN = "com.example.payroll.Payroll"
BASE_PACKAGE = "com.example.payroll"
DIALECT = {"encoding": "ascii", "collating_sequence": "native", "out_of_range": "fail"}
LIBRARY = {"id": "acme.tax", "cobol_path": "common/tax/", "coordinates": "com.acme:acme-tax:1.0",
           "sources": "libraries/acme-tax/src/main/java", "target_project": "libraries/payroll-app"}

# The program files (copybooks are inlined into the program that copies them).
PROGRAMS = {
    "PAYROLL.cbl": {"class": "com.example.payroll.Payroll", "copybooks": ["EMPTAB", "PAYTOT", "SLIPREC"]},
    "PAYSLIP.cbl": {"class": "com.example.payroll.Payslip", "copybooks": ["SLIPREC"]},
    "common/tax/TAXCALC.cbl": {"class": "com.example.payroll.common.tax.Taxcalc", "copybooks": []},
}

EXPECTED_STDOUT = "expected/stdout.txt"  # compared byte for byte, trailing spaces included

RUNTIME = "runtime"
JDK = "jdk"
EITHER = "either"


def _choice(handler: str, want: str) -> set[str]:
    return {RUNTIME: {f"builtin:{handler}"}, JDK: {f"builtin:{handler}_jdk"},
            EITHER: {f"builtin:{handler}", f"builtin:{handler}_jdk"}}[want]


RUBRIC: dict[tuple[str, int, str | None], tuple[set[str], str]] = {
    ("PAYROLL.cbl", 17, None): (_choice("display", JDK), "operator message, a literal only"),
    ("PAYROLL.cbl", 20, None): (_choice("compute_gnucobol", RUNTIME), "gross pay: money, truncated to the cent"),
    ("PAYROLL.cbl", 21, "start"): ({"library:acme.tax:Withholding.tax(BigDecimal)"},
                                   "TAXCALC computes the tax, not the net pay"),
    ("PAYROLL.cbl", 21, "result"): ({"result:item:WS-TAX"}, "the tax goes to WS-TAX"),
    ("PAYROLL.cbl", 21, "args"): ({"args:0"}, "the gross pay is the argument"),
    ("PAYROLL.cbl", 22, None): (_choice("subtract", RUNTIME), "net pay: money"),
    ("PAYROLL.cbl", 23, None): (_choice("add", RUNTIME), "total gross: money"),
    ("PAYROLL.cbl", 24, None): (_choice("add", RUNTIME), "total tax: money"),
    ("PAYROLL.cbl", 25, None): (_choice("add", RUNTIME), "total net: money"),
    ("PAYROLL.cbl", 33, None): (_choice("add", JDK), "progress counter"),
    ("PAYROLL.cbl", 35, None): (_choice("display", JDK), "operator message with the counter"),
    ("PAYROLL.cbl", 36, None): (_choice("display", RUNTIME), "money totals in the report"),
    ("PAYSLIP.cbl", 16, None): (_choice("inspect", JDK), "cosmetic name clean-up, one REPLACING ALL phrase"),
    ("PAYSLIP.cbl", 21, None): (_choice("string", EITHER), "the receiver is cleared first, so both are exact"),
    ("PAYSLIP.cbl", 24, None): (_choice("display", EITHER), "an alphanumeric line: both print the same"),
    ("common/tax/TAXCALC.cbl", 13, None): (_choice("if", RUNTIME), "compares money with the allowance"),
    ("common/tax/TAXCALC.cbl", 14, None): (_choice("compute_gnucobol", RUNTIME), "tax: money, ROUNDED"),
}

# How the programs call each other, given to the LLM baseline so that files translated one at a time fit.
INTERFACES = """\
- PAYROLL (main program) -> class com.example.payroll.Payroll with `public static void main(String[] args)`.
- PAYSLIP -> class com.example.payroll.Payslip with
  `public static void call(String slipId, String slipName, java.math.BigDecimal slipGross,
  java.math.BigDecimal slipTax, java.math.BigDecimal slipNet)`; each argument is the value of the
  SLIP-RECORD field of the same name (SLIP-ID, SLIP-NAME, ...), alphanumeric fields with their trailing spaces.
- TAXCALC -> class com.example.payroll.common.tax.Taxcalc with
  `public static java.math.BigDecimal call(java.math.BigDecimal lkGross)`, returning the value TAXCALC stores in LK-TAX.
"""

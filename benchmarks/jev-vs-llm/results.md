# Benchmark: Jev pipeline vs. LLM decider vs. LLM-only translation

Run 2026-11-05 at `a798235` with `benchmarks/jev-vs-llm/bench.py` (3 runs per approach, through OpenRouter).

**Project**: `benchmarks/jev-vs-llm/project/payroll`: three COBOL programs (PAYROLL, PAYSLIP, TAXCALC) and three copybooks (EMPTAB, PAYTOT, SLIPREC). `common/tax/` (TAXCALC) is mapped to the Java library `com.acme:acme-tax:1.0`, and every ADD, SUBTRACT, COMPUTE, DISPLAY, IF, STRING and INSPECT can use the runtime library (exact) or the plain JDK (approximate). See `benchmarks/jev-vs-llm/project/project.py`.

**Approaches**

- **jev**: Jev (`jev-latest`) decides, translator writes. The deterministic translator writes all the Java; Jev only picks among the candidates the worker generated (runtime or JDK, which library call).
- **decider**: `anthropic/claude-haiku-5.5` decides in Jev's place, translator writes. The same pipeline and the same decisions, with `benchmarks/jev-vs-llm/llm_decider.py` in Jev's place: `JevProvider`'s state, questions and task texts, answered by a chat completion as JSON with a probability per option. It still only picks a candidate.
- **llm**: `anthropic/claude-haiku-5.5` writes the Java, one call per file: each COBOL file with its copybooks, the library's sources and the programs' interfaces, and the instruction to match GnuCOBOL 3.2 byte for byte. One shot, no feedback.

**How it is measured**

- *Cost*: jev: the OpenRouter key's usage delta over the run (Jev's SDK reports no prices), split by the file's share of decisions; decider and llm: the cost OpenRouter reports for each call, by file.
- *Time*: wall time. jev and decider include the whole translation (JVM worker start, ProLeap, every provider call), split by the file's share of decisions; llm is the API call per file.
- *Construct*: jev and decider: each decision's pick against the rubric in `project.py` (runtime for money, JDK for counters and messages, `Withholding.tax` for the TAXCALC call), plus whether Payroll.java calls the library. llm: static checks of its Java for the same intent (library call, no floating point on money, cent truncation, HALF_UP rounding, hyphen replacement).
- *Compiles*: javac on the file (LLM: against stubs of the other programs' interfaces).
- *Exact*: the whole program compiled and run, stdout byte-for-byte equal to GnuCOBOL 3.2's (`benchmarks/jev-vs-llm/project/expected/stdout.txt`), exit status 0.

## Summary (mean over runs)

| Approach | Cost / file | Time / file | Cost / run | Time / run | Construct | Compiles (files) | Exact runs |
|---|---|---|---|---|---|---|---|
| Jev (`jev-latest`) decides, translator writes | $0.00084 | 1.9s | $0.00253 | 5.8s | 51/51 (100%) | 9/9 | 3/3 |
| `anthropic/claude-haiku-5.5` decides in Jev's place, translator writes | $0.00429 | 19.8s | $0.01286 | 59.3s | 50/51 (98%) | 9/9 | 3/3 |
| `anthropic/claude-haiku-5.5` writes the Java, one call per file | $0.00169 | 13.1s | $0.00506 | 40.5s | 27/27 (100%) | 9/9 | 0/3 |

## Per file (mean over runs)

| Approach | File | Cost | Time | Construct | Compiles |
|---|---|---|---|---|---|
| jev | `PAYROLL.cbl` | $0.00174 | 4.0s | 36/36 | 3/3 |
| jev | `PAYSLIP.cbl` | $0.00047 | 1.1s | 9/9 | 3/3 |
| jev | `common/tax/TAXCALC.cbl` | $0.00032 | 0.7s | 6/6 | 3/3 |
| decider | `PAYROLL.cbl` | $0.00918 | 40.8s | 36/36 | 3/3 |
| decider | `PAYSLIP.cbl` | $0.00259 | 11.1s | 9/9 | 3/3 |
| decider | `common/tax/TAXCALC.cbl` | $0.00109 | 7.4s | 5/6 | 3/3 |
| llm | `PAYROLL.cbl` | $0.00217 | 15.9s | 12/12 | 3/3 |
| llm | `PAYSLIP.cbl` | $0.00200 | 16.2s | 6/6 | 3/3 |
| llm | `common/tax/TAXCALC.cbl` | $0.00089 | 7.1s | 9/9 | 3/3 |

## Per run

| Approach | Run | Cost | Time | Construct | Compiles | Exact | Note |
|---|---|---|---|---|---|---|---|
| jev | 1 | $0.00253 | 5.6s | 17/17 | ✅ | ✅ |  |
| jev | 2 | $0.00253 | 5.9s | 17/17 | ✅ | ✅ |  |
| jev | 3 | $0.00253 | 6.0s | 17/17 | ✅ | ✅ |  |
| decider | 1 | $0.01237 | 52.9s | 16/17 | ✅ | ✅ |  |
| decider | 2 | $0.01347 | 67.1s | 17/17 | ✅ | ✅ |  |
| decider | 3 | $0.01275 | 57.9s | 17/17 | ✅ | ✅ |  |
| llm | 1 | $0.00531 | 41.7s | 9/9 | ✅ | ❌ |  |
| llm | 2 | $0.00451 | 36.0s | 9/9 | ✅ | ❌ |  |
| llm | 3 | $0.00537 | 43.8s | 9/9 | ✅ | ❌ |  |

## Decisions: jev

Pick and confidence per run against the rubric (✅ acceptable, ❌ not, · unscored).

| File | Line | Statement | Expected | Run 1 | Run 2 | Run 3 |
|---|---|---|---|---|---|---|
| `PAYROLL.cbl` | 17 | `DISPLAY "Payroll run started"` | `display_jdk` | ✅ `display_jdk` 0.84 | ✅ `display_jdk` 0.84 | ✅ `display_jdk` 0.86 |
| `PAYROLL.cbl` | 20 | `COMPUTE WS-GROSS = EMP-HOURS(WS-I) * EMP-RATE(WS` | `compute_gnucobol` | ✅ `compute_gnucobol` 0.94 | ✅ `compute_gnucobol` 0.94 | ✅ `compute_gnucobol` 0.94 |
| `PAYROLL.cbl` | 21 | `CALL "TAXCALC" USING WS-GROSS WS-TAX (args)` | `args:0` | ✅ `args:0` 0.99 | ✅ `args:0` 0.98 | ✅ `args:0` 0.99 |
| `PAYROLL.cbl` | 21 | `CALL "TAXCALC" USING WS-GROSS WS-TAX (start)` | `Withholding.tax(BigDecimal)` | ✅ `Withholding.tax(BigDecimal)` 0.97 | ✅ `Withholding.tax(BigDecimal)` 0.97 | ✅ `Withholding.tax(BigDecimal)` 0.98 |
| `PAYROLL.cbl` | 22 | `SUBTRACT WS-TAX FROM WS-GROSS GIVING WS-NET` | `subtract` | ✅ `subtract` 0.99 | ✅ `subtract` 0.99 | ✅ `subtract` 0.99 |
| `PAYROLL.cbl` | 23 | `ADD WS-GROSS TO TOT-GROSS` | `add` | ✅ `add` 0.99 | ✅ `add` 0.99 | ✅ `add` 0.99 |
| `PAYROLL.cbl` | 24 | `ADD WS-TAX TO TOT-TAX` | `add` | ✅ `add` 0.99 | ✅ `add` 0.99 | ✅ `add` 0.99 |
| `PAYROLL.cbl` | 25 | `ADD WS-NET TO TOT-NET` | `add` | ✅ `add` 0.99 | ✅ `add` 0.99 | ✅ `add` 0.99 |
| `PAYROLL.cbl` | 33 | `ADD 1 TO WS-COUNT` | `add_jdk` | ✅ `add_jdk` 0.81 | ✅ `add_jdk` 0.83 | ✅ `add_jdk` 0.87 |
| `PAYROLL.cbl` | 35 | `DISPLAY "Employees paid: " WS-COUNT` | `display_jdk` | ✅ `display_jdk` 0.55 | ✅ `display_jdk` 0.52 | ✅ `display_jdk` 0.58 |
| `PAYROLL.cbl` | 36 | `DISPLAY "Totals gross " TOT-GROSS " tax " TOT-TA` | `display` | ✅ `display` 0.85 | ✅ `display` 0.87 | ✅ `display` 0.86 |
| `PAYSLIP.cbl` | 16 | `INSPECT WS-NAME REPLACING ALL "-" BY " "` | `inspect_jdk` | ✅ `inspect_jdk` 0.89 | ✅ `inspect_jdk` 0.89 | ✅ `inspect_jdk` 0.90 |
| `PAYSLIP.cbl` | 21 | `STRING SLIP-ID " " WS-NAME " gross " WS-GROSS-ED` | `string` / `string_jdk` | ✅ `string` 0.79 | ✅ `string` 0.75 | ✅ `string` 0.73 |
| `PAYSLIP.cbl` | 24 | `DISPLAY WS-LINE` | `display` / `display_jdk` | ✅ `display` 0.79 | ✅ `display` 0.74 | ✅ `display` 0.75 |
| `common/tax/TAXCALC.cbl` | 13 | `IF LK-GROSS > WS-ALLOWANCE COMPUTE LK-TAX ROUNDE` | `if` | ✅ `if` 0.96 | ✅ `if` 0.96 | ✅ `if` 0.95 |
| `common/tax/TAXCALC.cbl` | 14 | `COMPUTE LK-TAX ROUNDED = (LK-GROSS - WS-ALLOWANC` | `compute_gnucobol` | ✅ `compute_gnucobol` 0.96 | ✅ `compute_gnucobol` 0.97 | ✅ `compute_gnucobol` 0.96 |

## Decisions: decider

Pick and confidence per run against the rubric (✅ acceptable, ❌ not, · unscored).

| File | Line | Statement | Expected | Run 1 | Run 2 | Run 3 |
|---|---|---|---|---|---|---|
| `PAYROLL.cbl` | 17 | `DISPLAY "Payroll run started"` | `display_jdk` | ✅ `display_jdk` 0.89 | ✅ `display_jdk` 0.80 | ✅ `display_jdk` 0.80 |
| `PAYROLL.cbl` | 20 | `COMPUTE WS-GROSS = EMP-HOURS(WS-I) * EMP-RATE(WS` | `compute_gnucobol` | ✅ `compute_gnucobol` 0.90 | ✅ `compute_gnucobol` 0.93 | ✅ `compute_gnucobol` 0.93 |
| `PAYROLL.cbl` | 21 | `CALL "TAXCALC" USING WS-GROSS WS-TAX (args)` | `args:0` | ✅ `args:0` 0.90 | ✅ `args:0` 0.90 | ✅ `args:0` 0.93 |
| `PAYROLL.cbl` | 21 | `CALL "TAXCALC" USING WS-GROSS WS-TAX (start)` | `Withholding.tax(BigDecimal)` | ✅ `Withholding.tax(BigDecimal)` 0.90 | ✅ `Withholding.tax(BigDecimal)` 0.82 | ✅ `Withholding.tax(BigDecimal)` 0.92 |
| `PAYROLL.cbl` | 22 | `SUBTRACT WS-TAX FROM WS-GROSS GIVING WS-NET` | `subtract` | ✅ `subtract` 0.93 | ✅ `subtract` 0.95 | ✅ `subtract` 0.93 |
| `PAYROLL.cbl` | 23 | `ADD WS-GROSS TO TOT-GROSS` | `add` | ✅ `add` 0.93 | ✅ `add` 0.93 | ✅ `add` 0.93 |
| `PAYROLL.cbl` | 24 | `ADD WS-TAX TO TOT-TAX` | `add` | ✅ `add` 0.90 | ✅ `add` 0.93 | ✅ `add` 0.95 |
| `PAYROLL.cbl` | 25 | `ADD WS-NET TO TOT-NET` | `add` | ✅ `add` 0.93 | ✅ `add` 0.93 | ✅ `add` 0.90 |
| `PAYROLL.cbl` | 33 | `ADD 1 TO WS-COUNT` | `add_jdk` | ✅ `add_jdk` 0.90 | ✅ `add_jdk` 0.87 | ✅ `add_jdk` 0.87 |
| `PAYROLL.cbl` | 35 | `DISPLAY "Employees paid: " WS-COUNT` | `display_jdk` | ✅ `display_jdk` 0.80 | ✅ `display_jdk` 0.72 | ✅ `display_jdk` 0.78 |
| `PAYROLL.cbl` | 36 | `DISPLAY "Totals gross " TOT-GROSS " tax " TOT-TA` | `display` | ✅ `display` 0.90 | ✅ `display` 0.90 | ✅ `display` 0.93 |
| `PAYSLIP.cbl` | 16 | `INSPECT WS-NAME REPLACING ALL "-" BY " "` | `inspect_jdk` | ✅ `inspect_jdk` 0.85 | ✅ `inspect_jdk` 0.82 | ✅ `inspect_jdk` 0.85 |
| `PAYSLIP.cbl` | 21 | `STRING SLIP-ID " " WS-NAME " gross " WS-GROSS-ED` | `string` / `string_jdk` | ✅ `string_jdk` 0.70 | ✅ `string_jdk` 0.75 | ✅ `string_jdk` 0.65 |
| `PAYSLIP.cbl` | 24 | `DISPLAY WS-LINE` | `display` / `display_jdk` | ✅ `display` 0.60 | ✅ `display` 0.60 | ✅ `display` 0.72 |
| `common/tax/TAXCALC.cbl` | 13 | `IF LK-GROSS > WS-ALLOWANCE COMPUTE LK-TAX ROUNDE` | `if` | ❌ `if_jdk` 0.65 | ✅ `if` 0.80 | ✅ `if` 0.62 |
| `common/tax/TAXCALC.cbl` | 14 | `COMPUTE LK-TAX ROUNDED = (LK-GROSS - WS-ALLOWANC` | `compute_gnucobol` | ✅ `compute_gnucobol` 0.93 | ✅ `compute_gnucobol` 0.93 | ✅ `compute_gnucobol` 0.93 |

## LLM checks

| File | Check | Run 1 | Run 2 | Run 3 |
|---|---|---|---|---|
| `PAYROLL.cbl` | calls the acme-tax library | ✅ Withholding.tax(...) | ✅ Withholding.tax(...) | ✅ Withholding.tax(...) |
| `PAYROLL.cbl` | does not call the translated TAXCALC instead | ✅ library only | ✅ library only | ✅ library only |
| `PAYROLL.cbl` | money without floating point | ✅ BigDecimal / integers only | ✅ BigDecimal / integers only | ✅ BigDecimal / integers only |
| `PAYROLL.cbl` | gross truncated to the cent | ✅ setScale(2, DOWN/FLOOR) present | ✅ setScale(2, DOWN/FLOOR) present | ✅ setScale(2, DOWN/FLOOR) present |
| `PAYROLL.cbl` | compiles | ✅ | ✅ | ✅ |
| `PAYSLIP.cbl` | hyphens in the name replaced | ✅ replace('-', ' ') | ✅ replace('-', ' ') | ✅ replace('-', ' ') |
| `PAYSLIP.cbl` | amounts without floating point | ✅ BigDecimal / integers only | ✅ BigDecimal / integers only | ✅ BigDecimal / integers only |
| `PAYSLIP.cbl` | compiles | ✅ | ✅ | ✅ |
| `common/tax/TAXCALC.cbl` | tax without floating point | ✅ BigDecimal / integers only | ✅ BigDecimal / integers only | ✅ BigDecimal / integers only |
| `common/tax/TAXCALC.cbl` | tax rounded half up | ✅ delegated to Withholding.tax | ✅ delegated to Withholding.tax | ✅ delegated to Withholding.tax |
| `common/tax/TAXCALC.cbl` | allowance compared as a decimal | ✅ delegated to Withholding.tax | ✅ delegated to Withholding.tax | ✅ delegated to Withholding.tax |
| `common/tax/TAXCALC.cbl` | compiles | ✅ | ✅ | ✅ |

## Output differences

Expected (GnuCOBOL 3.2):

```
Payroll run started
E001 ALICE      gross  1,000.00 tax    100.00 net    900.00 
E002 BOB        gross    690.00 tax     38.00 net    652.00 
E003 CAROL      gross  1,417.32 tax    183.46 net  1,233.86 
E004 MARY JO    gross    189.00 tax      0.00 net    189.00 
Employees paid: 04
Totals gross 003296.32 tax 000321.46 net 002974.86
```

llm run 1:

```
Payroll run started
E001 ALICE      gross  1,000.00 tax    100.00 net    900.00 
E002 BOB        gross    690.00 tax     38.00 net    652.00 
E003 CAROL      gross  1,417.32 tax    183.46 net  1,233.86 
E004 MARY JO    gross    189.00 tax      0.00 net    189.00 
Employees paid: 04
Totals gross 00329632 tax 00032146 net 00297486
```

llm run 2:

```
Payroll run started
E001 ALICE      gross  1,000.00 tax    100.00 net    900.00 
E002 BOB        gross    690.00 tax     38.00 net    652.00 
E003 CAROL      gross  1,417.32 tax    183.46 net  1,233.86 
E004 MARY JO    gross    189.00 tax      0.00 net    189.00 
Employees paid: 04
Totals gross 00329632 tax 00032146 net 00297486
```

llm run 3:

```
Payroll run started
E001 ALICE      gross  1,000.00 tax    100.00 net    900.00 
E002 BOB        gross    690.00 tax     38.00 net    652.00 
E003 CAROL      gross  1,417.32 tax    183.46 net  1,233.86 
E004 MARY JO    gross    189.00 tax      0.00 net    189.00 
Employees paid: 04
Totals gross 00329632 tax 00032146 net 00297486
```

Raw results and every run's Java: `build/benchmarks/jev-vs-llm/` (git-ignored).

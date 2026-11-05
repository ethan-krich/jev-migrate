# Jev dialect inference benchmark

Live runs of `./scripts/jev-smoke.sh`: Jev infers `Dialect.collating_sequence` for the two demo
projects in `examples/cobol-to-java-jev` (see `projects.py`). Each configuration ran 3 times.
"Margin" is the right value's probability minus the runner-up's. With no gate configured, the
top candidate is always applied. The example gate in `providers/jev.py` (0.85 for `normal`, plus
a 0.15 margin) would hold every run below.

## 2026-10-31: source comments in the context

Endpoint: OpenRouter, model `jev-latest`, context budget `max_depth 2`, `max_chars 4000`.

- **Baseline** (at `30f2cd7`): the context graph only. ProLeap drops comments, so Jev never saw any.
- **Comments**: the worker reads comments from the original source. Paragraph and data nodes
  carry the comments above and inside them (`refs[].comments`). `context.program` holds the
  program's header comments and IDENTIFICATION DIVISION paragraphs. `SETTING_TASK` now names
  those fields. For the demo programs this adds only the header comment, for example PARTCHK's
  "Checks the part codes of the stock file: two letters, then two digits."

### mainframe (PARTCHK, written for `ebcdic`)

| Run | Config | Pick | ebcdic | ascii | native | abstain | need_context | Output |
|---|---|---|---|---|---|---|---|---|
| 1 | baseline | ebcdic ✅ | 0.45 | 0.41 | 0.04 | 0.08 | 0.02 | matches |
| 2 | baseline | ebcdic ✅ | 0.49 | 0.37 | 0.04 | 0.08 | 0.02 | matches |
| 3 | baseline | ebcdic ✅ | 0.49 | 0.36 | 0.05 | 0.08 | 0.02 | matches |
| 1 | comments | ebcdic ✅ | 0.48 | 0.39 | 0.05 | 0.07 | 0.01 | matches |
| 2 | comments | ebcdic ✅ | 0.44 | 0.41 | 0.05 | 0.08 | 0.02 | matches |
| 3 | comments | ebcdic ✅ | 0.50 | 0.38 | 0.05 | 0.06 | 0.01 | matches |

### workstation (TICKETS, written for `ascii`)

| Run | Config | Pick | ascii | ebcdic | native | abstain | need_context | Output |
|---|---|---|---|---|---|---|---|---|
| 1 | baseline | ascii ✅ | 0.64 | 0.23 | 0.05 | 0.06 | 0.02 | matches |
| 2 | baseline | ascii ✅ | 0.63 | 0.24 | 0.06 | 0.05 | 0.02 | matches |
| 3 | baseline | ascii ✅ | 0.63 | 0.23 | 0.06 | 0.06 | 0.02 | matches |
| 1 | comments | ascii ✅ | 0.69 | 0.20 | 0.05 | 0.05 | 0.01 | matches |
| 2 | comments | ascii ✅ | 0.69 | 0.20 | 0.05 | 0.05 | 0.01 | matches |
| 3 | comments | ascii ✅ | 0.70 | 0.19 | 0.06 | 0.04 | 0.01 | matches |

### Summary (mean of 3)

| Project | Config | Correct | Confidence | Margin | Review priority |
|---|---|---|---|---|---|
| mainframe | baseline | 3/3 | 0.477 | 0.097 | business ×3 |
| mainframe | comments | 3/3 | 0.473 | 0.080 | business ×3 |
| workstation | baseline | 3/3 | 0.633 | 0.400 | visible ×3 |
| workstation | comments | 3/3 | 0.693 | 0.497 | business ×3 |

### Findings

- **workstation improved**: confidence rose by 0.06 and the margin by 0.10. The spread between
  runs also shrank, to 0.69–0.70. The comment ("a digit, an upper-case and a lower-case
  letter") matches the cascade's ASCII reading.
- **mainframe did not change**: 0.473 vs 0.477, well inside run-to-run noise (0.44–0.50). The
  comment says what the code should accept but not which machine it ran on. Turning "two
  letters, then two digits" plus `< "A" OR > "9"` into "EBCDIC" still takes the reasoning Jev
  does weakly. Comments help when they agree with the evidence Jev already reads. They are not
  a substitute for spelling out what each candidate value would do.
- Comments also moved the review priority of workstation from `visible` to `business`, which is
  more accurate for a validation rule.
- No run would pass the example gate. The margin on mainframe stays below 0.15.

## 2026-10-31: re-run after candidate docs

This adds Javadoc on builtin candidates and the method-candidate constructor. Setting decisions
have no Java preview, so the request Jev receives did not change: the fingerprints (`6d1850b9…`
mainframe, `f4bf0639…` workstation) and `used_chars` (1218 and 2932) equal the comments runs.
These 3 runs therefore repeat the comments configuration and measure run-to-run noise.
`./scripts/run.sh` (the offline example) also still passes.

| Project | Run | Pick | Right | Runner-up | Margin | Review priority | Output |
|---|---|---|---|---|---|---|---|
| mainframe | 1 | ebcdic ✅ | 0.45 | ascii 0.43 | 0.02 | business | matches |
| mainframe | 2 | ebcdic ✅ | 0.47 | ascii 0.39 | 0.08 | business | matches |
| mainframe | 3 | ebcdic ✅ | 0.53 | ascii 0.34 | 0.19 | business | matches |
| workstation | 1 | ascii ✅ | 0.65 | ebcdic 0.23 | 0.42 | business | matches |
| workstation | 2 | ascii ✅ | 0.70 | ebcdic 0.18 | 0.52 | business | matches |
| workstation | 3 | ascii ✅ | 0.68 | ebcdic 0.21 | 0.47 | business | matches |

Comments configuration pooled over all 6 runs (vs. baseline, 3 runs):

| Project | Correct | Confidence | Margin | Baseline confidence | Baseline margin |
|---|---|---|---|---|---|
| mainframe | 6/6 | 0.478 (0.44–0.53) | 0.088 (0.02–0.19) | 0.477 | 0.097 |
| workstation | 6/6 | 0.685 (0.65–0.70) | 0.483 | 0.633 | 0.400 |

- The earlier findings hold. Workstation stays clearly above baseline in every one of the 6 runs
  (lowest 0.65 vs. baseline highest 0.64). Mainframe remains indistinguishable from baseline.
- Mainframe is the fragile case. Run 1 won by 0.02, and the same request varied from 0.02 to
  0.19 in margin. One run (run 3, margin 0.19) would clear the example gate's margin, but none
  clears its 0.85 confidence floor.
- The `Rationale` column of `review.md` is still `-`: Jev returns no rationale for settings.

## Reproducing

```bash
./scripts/jev-smoke.sh     # needs OPENROUTER_API_KEY or TYPESAFE_API_KEY (or .env)
```

Read `settings[0].resolution.probabilities` from
`build/examples/cobol-to-java-jev/<project>/transpile-report.json` after each run.

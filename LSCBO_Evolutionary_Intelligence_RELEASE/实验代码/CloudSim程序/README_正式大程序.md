# LSCBO Cloud-Scheduling Program Notes

This folder contains the full cloud-scheduling experiment program. Earlier
manuscript versions used broader 12,780-row outputs from this program. Those
outputs are retained for audit history, but they are **not** the authoritative
data for the current Evolutionary Intelligence submission.

## Current EI Manuscript Data

Use this canonical dataset for the current paper:

```text
results/formal-cloudsim_20260619_canonical/combined/final_outputs/claim_full_raw.csv
```

Current scope:

- 5,310 rows total.
- 1,050 main-comparison rows.
- 1,200 ablation rows.
- 2,250 weight-sensitivity rows.
- 810 VM-heterogeneity rows.
- Seeds 43-72.

The current manuscript does not claim that every formal row was fully replayed
through CloudSim Plus. It reports the analytic multi-cost objective and keeps
CloudSim Plus as a replay/smoke-validation path.

## Verification Output

```text
results/formal-cloudsim_20260619_canonical/combined/final_outputs/analysis/CANONICAL_VERIFICATION_REPORT.md
```

The verification checks row count, duplicate keys, non-finite values, paired
main blocks, evaluation budgets, and the no-regression invariant.

## Recommended Commands

From the repository root:

```bat
03_analyze_canonical_data.bat
08_verify_default_workflow.bat
```

`08_verify_default_workflow.bat` does not rerun the full CEC2017 benchmark and
does not run the slow full CloudSim E4 replay.

## Historical Runs

Older directories such as `results/formal_20260609_231814/` and older
`formal-cloudsim_*` folders may contain 12,780-row or development-era outputs.
Do not use them for the current EI manuscript unless the manuscript is revised
again and the manifest is updated accordingly.

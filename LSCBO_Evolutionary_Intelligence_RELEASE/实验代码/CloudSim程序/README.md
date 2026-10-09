# LSCBO Cloud-Scheduling Experiment Program

This directory contains the cloud-scheduling experiment program used to generate
and analyze LSCBO scheduling data.

For the current Evolutionary Intelligence submission, the authoritative cloud
dataset is **not** the older broad 12,780-row run. The current manuscript uses the
canonical 5,310-row analytic dataset:

```text
results/formal-cloudsim_20260619_canonical/combined/final_outputs/claim_full_raw.csv
```

## Current Canonical Dataset

Scope:

- 5,310 rows.
- 30 seeds, 43-72.
- Main comparison, ablation, weight sensitivity, and VM heterogeneity.
- Main comparison: 150 paired blocks.
- Population methods use 6,000 objective evaluations.
- Greedy-LPT uses one deterministic schedule evaluation.

Verification:

```text
results/formal-cloudsim_20260619_canonical/combined/final_outputs/analysis/CANONICAL_VERIFICATION_REPORT.md
```

Key current result:

- Pareto safety: PASS.
- Objective wins/ties/losses versus Greedy-LPT: 150/0/0.
- Component losses versus Greedy-LPT: 0.
- Mean objective improvement: 1.647329% on the reference-normalized scale.
- Mean LBR improvement: 6.680058%.

## Reanalyze Current Data

From the repository root, run:

```bat
03_analyze_canonical_data.bat
```

Or run the Python entry directly:

```bash
python scripts/analyze_canonical_results.py \
  --raw results/formal-cloudsim_20260619_canonical/combined/final_outputs/claim_full_raw.csv \
  --out results/formal-cloudsim_20260619_canonical/combined/final_outputs/analysis \
  --cloudsim results/formal-cloudsim_20260619_canonical/cloudsim_smoke/claim_smoke_raw.csv
```

## Program Modes

The program still contains historical run modes used during development. Treat
new full reruns as optional reproduction work, not as the source of the current
manuscript tables unless their outputs are explicitly promoted to the canonical
path above.

Useful quick checks:

```text
run_claim_preserving_smoke_windows.bat
run_claim_preserving_pilot_windows.bat
```

Full CloudSim replay is intentionally slow and should only be launched when
needed for additional validation. The current manuscript reports analytic
multi-cost tables and describes CloudSim Plus as a replay/smoke-validation path.

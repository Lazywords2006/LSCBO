# LSCBO Evolutionary Intelligence Submission Package

This repository contains the reproducibility materials for the current
Evolutionary Intelligence submission:

**Pareto-Safe Pair Reassignment for LSCBO-Based Multi-Cost Cloud Task
Scheduling**

Public repository: https://github.com/Lazywords2006/LSCBO

## Current Claim Boundary

The current submission does **not** claim broad or large overall superiority of
the LSCBO optimizer in cloud scheduling. The supported claim is narrower:

- Pareto-safe task-pair reassignment can refine a strong Greedy-LPT schedule.
- The refinement is small in scalar objective value and mainly improves load
  balance.
- No reported component is worsened relative to Greedy-LPT in the canonical
  paired cloud study.
- The cloud-domain gain is attributed to the pair-reassignment neighborhood, not
  to the CBO host or the Levy trial.
- CEC2017 evidence is reported separately: LSCBO has the best average rank among
  nine optimizers, but it is not distinguished from simulated annealing after
  Holm correction.

## Submission Files

The Evolutionary Intelligence submission package is under:

```text
各个期刊格式的论文/待发布版本/01_Evolutionary_Intelligence/
```

Main files:

- `ANONYMIZED_REPRODUCIBILITY_README.md`
- `LSCBO_Evolutionary_Intelligence.tex`
- `LSCBO_Evolutionary_Intelligence.pdf`
- `LSCBO_Evolutionary_Intelligence_Supplementary.tex`
- `LSCBO_Evolutionary_Intelligence_Supplementary.pdf`
- `LSCBO_Evolutionary_Intelligence.zip`
- `LSCBO_Anonymized_Reproducibility.zip`

## Current Authoritative Data

### Cloud scheduling canonical dataset

Authoritative raw CSV:

```text
论文程序及图表/LSCBO_FULL_EXPERIMENT_PROGRAM_20260609/results/formal-cloudsim_20260619_canonical/combined/final_outputs/claim_full_raw.csv
```

Scope:

- 5,310 analytic rows.
- 30 seeds, 43-72.
- Main comparison: 150 complete paired blocks.
- Families: main comparison, mechanism ablation, weight sensitivity, and VM
  heterogeneity.
- CloudSim Plus is retained as a replay/smoke validation path; the manuscript's
  formal numerical tables use the stated analytic multi-cost model.

Verification report:

```text
论文程序及图表/LSCBO_FULL_EXPERIMENT_PROGRAM_20260609/results/formal-cloudsim_20260619_canonical/combined/final_outputs/analysis/CANONICAL_VERIFICATION_REPORT.md
```

Key verified result:

- Pareto safety: PASS.
- Objective wins/ties/losses versus Greedy-LPT: 150/0/0.
- Makespan, energy, cost, and load-balance losses versus Greedy-LPT: 0.
- Mean objective improvement: 1.647329% on the reference-normalized scale.
- Mean load-balance improvement: 6.680058%.

### CEC2017 official rerun

For the GitHub/submission surface, the authoritative CEC2017 rerun results are
inside:

```text
各个期刊格式的论文/待发布版本/01_Evolutionary_Intelligence/LSCBO_Anonymized_Reproducibility.zip
```

Archive path after extraction:

```text
LSCBO_Anonymized_Reproducibility/cec2017/results/
```

Files:

- `per_run_results.csv` - 7,830 rows.
- `per_function_statistics.csv`
- `friedman_ranking.csv`
- `pairwise_wilcoxon_holm.csv`
- `convergence_checkpoints.csv`
- `manifest.json`

Scope:

- Official CEC2017 functions F1 and F3-F30, 29 functions total.
- 9 algorithms: LSCBO, CBO, PSO, GWO, WOA, SA, AOA, HLBO, and GWCA.
- 30 runs per algorithm-function cell.
- 300,000 evaluations per run.

The local expanded Windows rerun package may exist at
`LSCBO_CEC2017_WINDOWS_16C32G/`, but it is intentionally ignored and is not
part of the GitHub source surface.

Key verified result:

- LSCBO average rank: 1.689655.
- SA average rank: 2.655172.
- LSCBO vs SA Holm-adjusted p-value: 0.0660665.

## Reproducing the Current Tables

Recommended lightweight check sequence:

```bat
01_verify_environment.bat
03_analyze_canonical_data.bat
07_text_risk_scan.bat
02_compile_paper_pdf.bat
```

This checks the environment, regenerates the canonical cloud analysis, scans the
paper text for overclaiming risk, and compiles the PDF. It does **not** rerun the
full CEC2017 benchmark or the slow full CloudSim E4 replay.

To rerun only the canonical cloud analysis:

```bat
03_analyze_canonical_data.bat
```

To compile the Evolutionary Intelligence PDF:

```bat
02_compile_paper_pdf.bat
```

## Historical Materials

Older 12,780-row CloudSim-oriented outputs, exploratory reruns, and local test
artifacts are intentionally excluded from the GitHub source surface for this
submission. Use the paths listed above for review, submission, and reproduction
of the current paper.

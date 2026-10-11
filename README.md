# LSCBO experiments

Current experiment release: **2026-10-12**. This repository contains experiment code, raw records, numerical summaries, result plots, and reproduction instructions.

| Experiment material | Location |
| --- | --- |
| Cloud-scheduling program and commands | [CloudSim program](实验代码/CloudSim程序/README.md) |
| Cloud raw data, parameters and numerical summaries | [CloudSim data](实验数据/CloudSim/data/) |
| Current objective, component and sensitivity plots | [CloudSim figures](实验数据/CloudSim/figures/) |
| CEC2017 experiment program | [CEC2017 program](实验代码/CEC2017程序/README_中文.md) |
| CEC2017 results and plots | [CEC2017 figures](实验数据/CEC2017/figures/) and [data index](实验数据/README.md) |
| Shared plot typography and legend standard | [figure standard](实验代码/FIGURE_STYLE.md) |
| Current file hashes | [RELEASE_MANIFEST.json](RELEASE_MANIFEST.json) |

## Cloud protocol

Population 100; all initial population vectors are random; recorded iterations 100; candidate budget 10,000; 50 VMs; seeds 43–72. The primary task counts are 100–1000, with extended scales through 10,000. The main experiment contains 3990 records, weight sensitivity 2250 records, and VM-capacity sensitivity 810 records. Direct-refinement diagnostics contain 4500 timing records and 900 unique quality records. Fresh-seed cost-model checks use seeds 103–132 and contain 4200 records across two models.

The selected model is `batch_reserved_seconds`: `cost = makespan * sum(all VM rates)`. All VMs are reserved at time zero and released together. Rates are synthetic cost units per second. Normalized cost equals normalized makespan within an instance; equal nominal weights therefore imply effective weights 0.50/0.25/0.25 on makespan/energy/load imbalance. Greedy-LPT raw records provide the shared normalization reference. Direct pair-search controls are Greedy-initialized.

LSCBO has the lowest mean objective among the six metaheuristics at all ten primary scales, with pooled mean F = 19.953855. The normalization reference has F = 1. The mean paired cost reduction against PSO is 25.1840% on the main seeds and 25.6809% on the separate confirmation seeds. WOA has a lower mean objective at task counts 4000–10,000. All underlying records and numerical summaries are available in the data directory.

## Reproduce and verify

Use JDK 21 and Python 3.12. Detailed build, run, analysis, and plotting commands are in the [CloudSim README](实验代码/CloudSim程序/README.md). On Windows, keep runtime outputs and dependency caches under `D:/CodexRuntime`.

```powershell
python -m pip install --cache-dir D:/CodexRuntime/cache/pip -r 实验代码/CloudSim程序/requirements.txt
python 实验代码/CloudSim程序/scripts/verify_latest.py --runtime D:/CodexRuntime/runs/lscbo-release-audit
python 实验代码/CloudSim程序/scripts/render_current_figures.py --out D:/CodexRuntime/runs/lscbo-rendered-figures
python 实验代码/generate_cec_figures.py --output-dir D:/CodexRuntime/runs/lscbo-cec-figures
python 实验代码/verify_figure_style.py --report D:/CodexRuntime/runs/lscbo-figure-style-audit.json
```

The audit checks release hashes, raw objective values and evaluation budgets, numerical summaries, seed-cluster intervals, direct diagnostics, confirmation statistics, and vector plots. It writes its report outside the repository. It does not rerun the full simulations.

The frozen cloud Java source has SHA-256 `a52444877768a42129eb9dcd637f9a3d18f25eb94a8441e21b541ff773854b15`. CEC2017 algorithm source and all numerical results are retained unchanged. All data plots use the Fig7 template: axis labels 9.5 pt, ticks 8.5 pt, panel titles 12 pt, and legend labels 7.5 pt. Every plot and panel has a square, opaque, two-row legend at the upper left, 12 pt from the left axes frame and 2.625 pt below the top. Six methods use three columns, nine use five columns, and two sensitivity categories use one column. Shared method colors and line widths match their legends. CEC2017 preserves its redundant dash patterns and exports each selected function separately to retain readable type sizes.

Download the current experiment set through GitHub **Code → Download ZIP**.

Fig6 uses a linear objective axis from 0 to 65, with equally spaced ticks from 0 to 60 every 10 and extra headroom above the top tick. PSO is blue (#0072B2) and GWO green (#2CA02C), with matching legend strokes in all cloud component panels.

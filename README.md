# LSCBO Scheduler — Reproducibility Package

Code, data, and LaTeX sources for *"LSCBO Scheduler: Task-Reassignment Local Search
for CBO-Based Multi-Cost Cloud Task Scheduling."*

## Layout
- `论文主稿/主稿工程/` — manuscript (LaTeX), figures, figure scripts, and the formal
  experiment data (`data/formal_20260609_231814/claim_full_raw.csv`, 12,780 rows).
- `论文程序及图表/`
  - `LSCBO_FULL_EXPERIMENT_PROGRAM_20260609/` — CloudSim Plus scheduling experiment
    program; `results/formal-cloudsim_*` are the full CloudSim-validation runs.
  - `reproducibility/cec2017_program/` — CEC2017 continuous-benchmark program (incl. DE, SA)
    with ranking/detailed results.
  - `reproducibility/MANIFEST.md` — authoritative map: each paper figure/table → script → data.
  - `src/`, `scripts/` — Java optimizers/brokers and Python figure scripts.

## Reproduce
See `论文程序及图表/reproducibility/MANIFEST.md` for the exact figure/table → script → data
mapping and commands to regenerate the figures and recompile the manuscript.

## Notes
- Cited reference PDFs are omitted (copyright).
- Statistics: two-sided Wilcoxon signed-rank with Holm-Bonferroni; Friedman + Iman-Davenport.

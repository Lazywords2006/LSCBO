# Canonical Cloud-Scheduling Verification

## Data integrity

- Total rows: 5310
- Experiment rows: {'E4_main': 1050, 'E2_ablation': 1200, 'weight_sensitivity': 2250, 'vm_heterogeneity': 810}
- Duplicate rows: 0
- Non-finite numeric values: 0
- Main paired blocks: 150 complete blocks
- Metaheuristic evaluation budget: 6,000 per run
- Deterministic greedy baseline evaluations: 1 per run

## Main comparison against Greedy-LPT

- makespan: mean improvement 0.000624%; wins/ties/losses 34/116/0
- energy: mean improvement 0.005289%; wins/ties/losses 150/0/0
- cost: mean improvement 0.001810%; wins/ties/losses 150/0/0
- imbalance: mean improvement 6.680058%; wins/ties/losses 150/0/0
- objective: mean improvement 1.647329%; wins/ties/losses 150/0/0

- Friedman test on objective: chi-square=887.615094, p=1.787095e-188
- Holm-adjusted pairwise results are stored in `table_pairwise_wilcoxon.csv`.
- The gain is statistically consistent but practically small; it must not be described as a large overall improvement.
- The Pareto-safe pair search never worsens makespan, energy, cost, or LBR relative to Greedy-LPT in the 150 main blocks.

## Mechanism boundary

- LSCBO_full, no_levy, and ls_only are numerically identical in the ablation.
- Therefore the cloud-domain gain is attributable to the Pareto-safe pair-reassignment search, not to Lévy perturbation or the CBO host.
- Objective-only pair search obtains a lower scalar objective but allows small component regressions; the Pareto-safe acceptance rule trades some scalar gain for no-regression guarantees.
- Single-task Pareto relocation does not improve the greedy reference under this budget; pair reassignment is the effective neighborhood.

## CloudSim

- CloudSim replay rows supplied: 12
- Completed rows: 12/12
- Mean analytic/CloudSim makespan relative error: 1.5174%
- Maximum analytic/CloudSim makespan relative error: 2.0420%

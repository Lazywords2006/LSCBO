# Current Paper Recomputed Summary

Generated from the current manuscript CSV files.

## Phase Ablation

| Variant | Runs | Mean Makespan | Delta vs FULL (%) | Wilcoxon p | Paired d |
|---|---:|---:|---:|---:|---:|
| FULL | 150 | 503.4 | 0.00 | NA | NA |
| LS-only | 150 | 512.8 | 1.87 | 0.0059 | 0.07 |
| noPhase1 | 150 | 925.6 | 83.86 | 1.15e-26 | 0.89 |
| noPhase2 | 150 | 1074.7 | 113.47 | 1.16e-26 | 0.76 |
| noPhase3 | 150 | 1007.1 | 100.04 | 1.16e-26 | 0.93 |
| base | 150 | 924.8 | 83.71 | 1.16e-26 | 0.95 |

Wilcoxon source: scipy_wilcoxon.

## Evolution Path

| Stage | Runs | Mean Makespan | Change vs V1 (%) |
|---|---:|---:|---:|
| V1_CBO_orig | 150 | 831.4 | 0.00 |
| V2_addLevy | 150 | 724.5 | -12.85 |
| V3_addDynW | 150 | 736.3 | -11.44 |
| V4_addStaged | 150 | 924.8 | 11.25 |
| V5_addLS | 150 | 503.4 | -39.44 |

## Main CloudSim Comparison

| Algorithm | Runs | Mean Makespan | Average Rank |
|---|---:|---:|---:|
| LSCBO | 150 | 491.1 | 1.060 |
| GTO | 150 | 836.1 | 3.277 |
| WOA | 150 | 815.8 | 4.170 |
| HHO | 150 | 823.3 | 4.300 |
| GWO | 150 | 978.3 | 5.010 |
| DBO | 150 | 1094.0 | 5.353 |
| CBO | 150 | 1254.6 | 7.170 |
| AOA | 150 | 1103.3 | 7.230 |
| PSO | 150 | 1126.9 | 7.430 |

## Baseline Note

E3 V1_CBO_orig mean makespan is 831.4.
E4 CBO mean makespan is 1254.6.
These values come from different CSV files and should not be described as the same baseline run.

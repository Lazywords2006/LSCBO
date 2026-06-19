# LSCBO Formal Full Experiment Report

- Total raw rows: 5310
- Seed range in data: 43-72 (30 seed values)
- CloudSim validation in these rows: not run for formal rows

## Experiment Families

- E2_ablation: 1200 rows
- E4_main: 1050 rows
- vm_heterogeneity: 810 rows
- weight_sensitivity: 2250 rows

## Generated Outputs

- `final_outputs/claim_full_raw.csv`
- `final_outputs/tables/table_main_multicost_by_scale.csv`
- `final_outputs/tables/table_main_improvement_vs_greedy_by_scale.csv`
- `final_outputs/tables/table_main_average_ranks.csv`
- `final_outputs/tables/table_component_ablation.csv`
- `final_outputs/tables/table_evolution_path.csv`
- `final_outputs/tables/table_weight_sensitivity.csv`
- `final_outputs/tables/table_vm_heterogeneity.csv`
- `final_outputs/tables/table_perturbation.csv`
- `final_outputs/figures/fig_main_multicost_improvement.png`
- `final_outputs/figures/fig_main_paired_improvement.png`
- `final_outputs/figures/fig_main_objective_by_scale.png`

## Notes

- The formal default run produces complete deterministic multi-cost experiment data.
- Use `formal-cloudsim` only when you intentionally want CloudSim validation for every generated row; it is much slower.
- Use `smoke-cloudsim` for a quick CloudSim sanity check before long runs.

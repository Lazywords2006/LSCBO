# LSCBO Formal Full Experiment Report

- Total raw rows: 426
- Seed range in data: 43-43 (1 seed values)
- CloudSim validation in these rows: not run for formal rows

## Experiment Families

- E2_ablation: 35 rows
- E3_evolution: 25 rows
- E4_main: 45 rows
- perturbation: 15 rows
- vm_heterogeneity: 81 rows
- weight_sensitivity: 225 rows

## Generated Outputs

- `final_outputs/claim_full_raw.csv`
- `final_outputs/tables/table_main_multicost_by_scale.csv`
- `final_outputs/tables/table_main_relative_to_cbo_by_scale.csv`
- `final_outputs/tables/table_main_average_ranks.csv`
- `final_outputs/tables/table_component_ablation.csv`
- `final_outputs/tables/table_evolution_path.csv`
- `final_outputs/tables/table_weight_sensitivity.csv`
- `final_outputs/tables/table_vm_heterogeneity.csv`
- `final_outputs/tables/table_perturbation.csv`
- `final_outputs/figures/fig_main_multicost_relative_to_cbo.png`
- `final_outputs/figures/fig_main_objective_rank.png`
- `final_outputs/figures/fig_main_objective_by_scale.png`

## Notes

- The formal default run produces complete deterministic multi-cost experiment data.
- Use `formal-cloudsim` only when you intentionally want CloudSim validation for every generated row; it is much slower.
- Use `smoke-cloudsim` for a quick CloudSim sanity check before long runs.

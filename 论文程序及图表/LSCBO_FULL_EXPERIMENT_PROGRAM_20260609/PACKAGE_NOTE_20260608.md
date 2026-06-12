# Package Note

This package contains the full LSCBO claim-preserving data generator.

Local smoke validation has already been run:

```text
results/smoke_local_20260608_fixed/
```

Validation status:

- 12 raw result rows were generated.
- All CloudSim validation rows completed all tasks.
- LSCBO rows are marked with LEVY.
- The CSV includes makespan, energy, cost, imbalance, objective, evaluation counts, and CloudSim finished-task counts.

For Windows full data generation, run:

```text
run_claim_preserving_full_windows.bat
```

Recommended order:

1. Run `run_claim_preserving_smoke_windows.bat`.
2. Run `run_claim_preserving_pilot_windows.bat`.
3. Run `run_claim_preserving_full_windows.bat`.

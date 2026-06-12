# LSCBO Experiment Results — Mandatory Revision Package

Generated: 2026-06-01  
Purpose: Supporting data for manuscript revisions (IEEE-level journal)

---

## Directory Structure

```
experiment_results/
├── E1_N5000/                    # N=5000 scalability (30 seeds)
│   └── N5000_validation_*.csv
├── E2_budget_matched/           # Fair budget comparison (all pop=20, iter=20)
│   └── BudgetMatched_*.csv
├── E3_E4_E5_postprocessing/     # Python statistical analysis outputs
│   ├── ablation_effects.txt     # E3: Cohen's d + Holm-Wilcoxon
│   ├── holm_main_comparison.txt # E4: Holm correction on main table
│   └── focused_effects.txt      # E5: Focused validation effect sizes
└── E7_latex_tables/             # Ready-to-paste LaTeX tables
    └── latex_tables_output.txt
```

---

## Experiment Summary

### E1 — N=5000 Scalability Validation (Must-Run)
- **Config**: LSCBO, CBO, WOA × 30 seeds (43–72) × N=5000, M=50 VMs
- **VM MIPS**: 500–2000 (heterogeneous)
- **Task MI**: 1000–20000 (heterogeneous)
- **Metric**: Makespan (s), Load Balance Ratio (CoV of per-VM load)
- **Purpose**: Replace single-seed N=5000 result with statistically robust evaluation

### E2 — Budget-Matched Validation (Recommended)
- **Config**: LSCBO, CBO, WOA × 30 seeds × N=500/1000/2000, M=50 VMs
- **Budget**: ALL algorithms use pop=20, iter=20 (equal 400 evaluations)
- **Purpose**: Demonstrate LSCBO advantage holds under fair evaluation budget

### E3 — Ablation Effect Sizes (Post-processing, No Re-run)
- **Data**: `experiments/revisions/ablation_cloudsim/cloudsim_ablation_results.csv`
- **Results**:
  - N=500: LSCBO vs CBO d=1.77***, Friedman p=8.5e-9
  - N=1000: LSCBO vs CBO d=1.02***, Friedman p=8.2e-5

### E4 — Holm Correction on Main Comparison (Post-processing)
- **After Holm correction**: N=100 and N=1500 remain significant (2/7 scales)
- **Note**: Mention in paper that N=500/800/1000/2000 are "nominally significant before correction"

### E5 — Focused Validation Effect Sizes (Post-processing)
- LSCBO vs CBO: large effect (d=2.07–2.28) at all scales
- LSCBO vs WOA: medium at N=500 (d=0.72), small at N=1000/2000

### E7 — LaTeX Tables
- N=5000 validation table (from E1)
- Budget-matched table (from E2)
- Ablation effect size table (from E3)
- Holm correction table (from E4)
- Focused validation effect sizes table (from E5)

---

## How to Reproduce

```bash
# Post-processing (no re-run needed)
cd scripts
python compute_ablation_effects.py    # E3
python compute_holm_main_comparison.py # E4
python compute_focused_effects.py      # E5

# Java experiments (requires Maven 3.9+, Java 11+)
mvn compile exec:java -Dexec.mainClass="com.edcbo.research.N5000ValidationTest"        # E1
mvn compile exec:java -Dexec.mainClass="com.edcbo.research.BudgetMatchedValidationTest" # E2

# LaTeX table generation
cd scripts && python generate_all_latex_tables.py  # E7
```

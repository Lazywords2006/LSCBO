# LSCBO Anonymized Reproducibility Archive

This archive contains the source code, fixed experiment settings, canonical
results, and analysis scripts used by the anonymous manuscript.

## Contents

- `cloud/`: Java 21/Maven cloud-scheduling program, Python analysis scripts,
  5,310-row canonical analytic dataset, and 12-row CloudSim Plus smoke replay.
- `cec2017/`: C++ source, official CEC2017 input data, locked configuration,
  and the 7,830-run canonical benchmark results.
- `SHA256SUMS.txt`: checksums for every other file in the archive.

Historical exploratory runs, build products, machine-specific files, and local
paths are intentionally excluded.

## Cloud-Scheduling Reanalysis

Requirements: Java 21, Maven 3.9 or later, and Python 3.12. Install the tested
Python dependencies with `python -m pip install -r requirements.txt`.

From `cloud/`, run:

```bash
python scripts/analyze_canonical_results.py \
  --raw results/canonical/combined/final_outputs/claim_full_raw.csv \
  --out results/canonical/combined/final_outputs/analysis \
  --cloudsim results/canonical/cloudsim_smoke/claim_smoke_raw.csv
```

Run the program self-test with:

```bash
mvn -q test
```

The canonical report is at
`cloud/results/canonical/combined/final_outputs/analysis/CANONICAL_VERIFICATION_REPORT.md`.

## CEC2017 Results

The locked protocol is at `cec2017/config/protocol.json`. The primary result
files are:

- `cec2017/results/per_run_results.csv`
- `cec2017/results/convergence_checkpoints.csv`
- `cec2017/results/per_function_statistics.csv`
- `cec2017/results/friedman_ranking.csv`
- `cec2017/results/pairwise_wilcoxon_holm.csv`

The complete result table contains 9 algorithms, 29 functions, and 30 runs per
algorithm-function pair (7,830 rows). The supplied source and official function
data permit an independent rebuild and rerun on a C++17 toolchain.

## Verification

On macOS or Linux, verify all packaged files from the archive root with:

```bash
shasum -a 256 -c SHA256SUMS.txt
```

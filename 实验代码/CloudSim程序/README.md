# Cloud-scheduling experiments

The frozen main Java source uses `batch_reserved_seconds` and has SHA-256 `a52444877768a42129eb9dcd637f9a3d18f25eb94a8441e21b541ff773854b15`. Published experiment records are in `实验数据/CloudSim/data/`; result plots are in `实验数据/CloudSim/figures/`.

Use **JDK 21**, **Python 3.12**, and the versions in `requirements.txt`. The Maven POM specifies CloudSim Plus 8.0.0 and Commons Math 3.6.1. Run commands from the repository root. On Windows, runtime outputs belong under `D:/CodexRuntime`; Maven dependencies fetched by the wrapper use `D:/CodexRuntime/cache/maven`.

## Verify the published experiments

```powershell
python -m pip install --cache-dir D:/CodexRuntime/cache/pip -r 实验代码/CloudSim程序/requirements.txt
python 实验代码/CloudSim程序/scripts/verify_latest.py --runtime D:/CodexRuntime/runs/lscbo-release-audit
```

The audit checks every manifest hash, recomputes main and sensitivity summaries and seed-cluster intervals, validates runtime and direct-refinement records, checks fresh-seed confirmation statistics, and reads back PDF/SVG curves and legends. The report is written outside the repository. A full simulation replay is a separate run.

## Build or run

```powershell
python 实验代码/CloudSim程序/scripts/run_current.py --phase build
python 实验代码/CloudSim程序/scripts/run_current.py --phase smoke
python 实验代码/CloudSim程序/scripts/run_current.py --phase primary --seed-range 43-72
python 实验代码/CloudSim程序/scripts/run_current.py --phase analytic --seed-range 43-72
python 实验代码/CloudSim程序/scripts/run_current.py --phase direct
```

- `build`: compile the frozen main source.
- `smoke`: a small serialized CloudSim-backed check.
- `primary`: 100–1000 tasks, seven methods and 30 seeds, yielding 2100 records.
- `analytic`: the 19-scale main run and the two sensitivity experiments, yielding 3990 and 3060 records.
- `direct`: diagnostic controls with two warm-up cycles and five timing repetitions. `full_cold` is an internal raw-record baseline.

Population 100; all initial population vectors random; recorded iterations 100; candidate budget 10,000. Each new run gets a separate runtime directory; use `--run-name` to choose its identifier. Existing run directories are rejected.

`run_current.py` builds a Maven dependency classpath unless `--classpath-file` is provided. Existing JAR paths can be supplied using `;` on Windows or `:` on Unix:

```powershell
python 实验代码/CloudSim程序/scripts/run_current.py --phase smoke --java-home 'C:/path/to/jdk-21' --classpath-file D:/CodexRuntime/runs/dependency-classpath.txt
```

Direct diagnostics create and verify an instrumented source copy outside the repository.

## Analyze and render

```powershell
$env:LSCBO_RUNTIME='D:/CodexRuntime/runs/lscbo-analysis'
python 实验代码/CloudSim程序/scripts/analysis_helpers.py --out D:/CodexRuntime/runs/lscbo-analysis/statistics --figures D:/CodexRuntime/runs/lscbo-analysis/figures
python 实验代码/CloudSim程序/scripts/render_current_figures.py --out D:/CodexRuntime/runs/lscbo-rendered-figures
```

Analysis produces CSV/JSON statistics and sensitivity plots. Rendering reads the frozen raw records and produces the current Fig6–8 designs in an external directory. PDF timestamps may differ; vector values, colors, strokes and legend layout are checked against the data.

## Cost and reference

`C(s) = H_max(s) * sum(r_j)` for all 50 VMs, reserved from time zero until common release. Rates are synthetic cost units per second. No billing-time rounding is applied. Instance-wise normalized cost equals normalized makespan, so equal nominal weights imply effective weights 0.50/0.25/0.25 on makespan/energy/imbalance.

Greedy-LPT raw records supply the shared normalization reference. Direct pair-search controls are Greedy-initialized. Nine candidate cost formulas were screened on the original seeds; the frozen model and a completion-cost control were checked on fresh seeds 103–132. Their records remain in `实验数据/CloudSim/data/成本模型追加验证/`. The current runner fixes `batch_reserved_seconds`.

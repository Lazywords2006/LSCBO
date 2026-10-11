# Current cloud-scheduling reproduction

This program matches the 2026-10-11 Cost and Section 4.2 delivery in `论文/Cost与云调度两个板块/`. The frozen main Java source defaults to `batch_reserved_seconds` and has SHA-256 `a52444877768a42129eb9dcd637f9a3d18f25eb94a8441e21b541ff773854b15`.

Use **JDK 21**, **Python 3.12**, and the versions in `requirements.txt`. The Maven POM specifies CloudSim Plus 8.0.0 and Commons Math 3.6.1. `run_current.py` uses Maven to construct a dependency classpath if no `--classpath-file` is provided. A classpath file may instead point to already-installed JARs, separated by `;` on Windows or `:` on Unix. Installed tools need not be relocated.

Run the commands below from the repository root. On Windows, runtime outputs and new caches go to `D:/CodexRuntime`; Maven dependencies fetched by the wrapper use `D:/CodexRuntime/cache/maven`. On other systems, supply an absolute `--runtime` path. No command below overwrites the published data or PDFs.

## Verify the published delivery

Install the Python dependencies using a runtime cache, then run the audit:

```powershell
python -m pip install --cache-dir D:/CodexRuntime/cache/pip -r 实验代码/CloudSim程序/requirements.txt
python 实验代码/CloudSim程序/scripts/verify_latest.py --runtime D:/CodexRuntime/runs/lscbo-release-audit
```

The audit first checks every release-manifest hash. It then recomputes all eight Section 4.2 tables and numerical claims, objective normalization, sensitivity seed-cluster intervals, and confirmation statistics from the raw records. It checks the PDF/SVG curves against the exact plotted means, line identities and legend strokes; Fig. 7 captions must be below and centered. The PDF text is read back. The new audit result is written to the external runtime directory.

The delivery includes the earlier passing assignment-level recomputation evidence; the current hash check establishes that its raw records and parameters have not changed. This does not mean a fresh full simulation replay is performed by the audit command.

## Build or run

```powershell
python 实验代码/CloudSim程序/scripts/run_current.py --phase build
python 实验代码/CloudSim程序/scripts/run_current.py --phase smoke
python 实验代码/CloudSim程序/scripts/run_current.py --phase primary --seed-range 43-72
python 实验代码/CloudSim程序/scripts/run_current.py --phase analytic --seed-range 43-72
python 实验代码/CloudSim程序/scripts/run_current.py --phase direct
```

- `build`: compile the frozen main source.
- `smoke`: a small, serialized CloudSim-backed check. This is not a replacement of the unmodified manuscript Section 4.3 or its old full replay.
- `primary`: 100–1000 tasks, seven methods, 30 seeds: 2100 rows.
- `analytic`: the 19-scale main run (3990 rows) and both sensitivity experiments (3060 rows), using respectively eight and six workers as in the published run. Scheduling ablation is not selected.
- `direct`: the frozen diagnostic controls, with two warm-up cycles and five timing repetitions, compared against the published main-run outcomes. `full_cold` is an internal audit baseline in the raw records; it is not a displayed manuscript row.

All simulations use the new cost by default. The primary population is fully random; the candidate budget is 10,000 with population 100 and recorded iterations 100. A new run has its own output directory under the runtime path. Use `--run-name` for a chosen identifier; an existing output directory is rejected.

When using existing tools and dependencies:

```powershell
python 实验代码/CloudSim程序/scripts/run_current.py --phase smoke --java-home 'C:/path/to/jdk-21' --classpath-file D:/CodexRuntime/runs/dependency-classpath.txt
```

For direct diagnostics, the wrapper creates an instrumented copy in the runtime directory and checks its frozen hash. It does not edit the main source.

## Render the same figure designs

```powershell
python 实验代码/CloudSim程序/scripts/render_current_figures.py --out D:/CodexRuntime/runs/lscbo-rendered-figures
```

This reads the published data and statistics and renders Fig. 6–8 into the specified external directory. PDF timestamps and provenance paths may differ between renders; the already-audited publication PDFs are frozen in the delivery directory. The render preserves the current curve definitions, style and panel layout.

## Cost interpretation

The adopted formula is `C(s) = H_max(s) * sum(r_j)` for all 50 available VMs, reserved together from time zero to the common release time. These are continuous-time synthetic rental units. Since the rate sum is fixed within each instance, `C/C_Greedy = H_max/H_max_Greedy`. The Cost block discloses this dependence: equal nominal weights imply effective weights 0.50/0.25/0.25 on makespan/energy/imbalance. The formula is a fixed-fleet common-release adaptation of reservation-cost reasoning; it is not presented as ERTH's exact formula or a commercial tariff.

Nine candidate formulas were screened on the original seeds; the selected model was checked on fresh seeds 103–132. Those records and the completion-cost control are retained under `数据/成本模型追加验证/`. The main figures continue to use the original main seeds. The source retains the screened formulas for traceability, but the current runner fixes `batch_reserved_seconds`. The frozen Java metadata names the adopted batch model; overriding `cost.model` manually for a historical candidate requires separately recording the actual model and must not be confused with the current batch-model release.

The manuscript tables omit standalone Greedy-LPT rows/columns. The raw reference records remain unchanged for normalization and reproducibility; `数据/论文展示表/` contains the eight actual display tables. Greedy-initialized pair-search controls retain their true initialization labels.

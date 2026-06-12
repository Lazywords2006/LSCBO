# LSCBO 论文复现清单（MANIFEST）

整理时间：2026-06-12。本清单是**权威**的"图表 → 脚本 → 数据 → 程序"映射，取代旧的 `当前论文图表程序清单.md`（其指向的 `E4_LSCBOFinal` 等已被取代）。

---

## 1. 两套实验程序（权威）

| 实验系列 | 程序位置 | 入口 | 产出 |
|---|---|---|---|
| CloudSim 调度（主实验，12780 行） | `论文程序及图表/LSCBO_FULL_EXPERIMENT_PROGRAM_20260609/` | `ClaimPreservingCloudSimExperiment.java` + `scripts/run_lscbo_full_program.py` | `results/formal_20260609_231814/final_outputs/{claim_full_raw.csv, tables/}` |
| CEC2017 连续基准（含 DE/SA） | `论文程序及图表/reproducibility/cec2017_program/`（2026-06-12 从外部硬盘 CEC2017_Archive 导入） | `src/.../CEC2017_BatchTest.java`（含 `DE_/SA_/LSCBO_/CBO_/GTO_/GWO_/PSO_ContinuousOptimizer`） | `results/cec2017_{ranking,results,detailed}_20260120.csv` |

CloudSim 全量 CloudSim Plus 验证运行：`results/formal-cloudsim_20260610_162238/`、`formal-cloudsim_20260611_010338/`（`cloudsimValidation=true`），佐证正文"12780 行全部过 CloudSim 验证"。

## 2. 图（8 张）→ 脚本 → 数据

权威脚本两个：
- **A** = `论文主稿/主稿工程/scripts/generate_lscbo_evidence_figures.py`
- **B** = `论文程序及图表/scripts/regenerate_consistent_lscbo_figures.py`

| 图（.tex includegraphics） | 脚本 | 数据源（权威） |
|---|---|---|
| `fig_lscbo_scheduling_model.png` | A | 手绘示意，无数据 |
| `fig_lscbo_workflow.png` | A | 手绘示意，无数据 |
| `fig_claim_multicost_profile.png` | A | `论文主稿/主稿工程/data/formal_20260609_231814/claim_full_raw.csv`（E4_main/W0_equal） |
| `fig_lscbo_convergence_trace.png` | A | `论文程序及图表/实验数据/CloudSim/convergence/convergence_LSCBO_M*_seed42.csv` |
| `fig1_cec_ranking.png` | B | `论文主稿/主稿工程/data/cec2017_friedman_ranking.csv`（= CEC 程序 `cec2017_ranking_20260120`） |
| `fig_E4_final_ranking.png` | B | `claim_full_raw.csv`（E4_main/W0_equal）★2026-06-11 已从旧 E4_LSCBOFinal 重指向 |
| `fig_E4_makespan_scale.png` | B | `claim_full_raw.csv`（E4_main/W0_equal）★同上 |
| `fig_E1_rank_heatmap.png` | B | `论文主稿/主稿工程/data/weight_rank_values_metaheuristic.csv` |

**复现命令**（务必显式传 claim-raw，避免脚本默认 glob 到旧切片）：
```bash
cd 论文主稿/主稿工程
python3 scripts/generate_lscbo_evidence_figures.py \
  --claim-raw data/formal_20260609_231814/claim_full_raw.csv \
  --convergence-dir ../../论文程序及图表/实验数据/CloudSim/convergence
python3 ../../论文程序及图表/scripts/regenerate_consistent_lscbo_figures.py
latexmk -pdf LF-CBO_Manuscript.tex
```

## 3. 表 → 权威数据

所有主表来自 CloudSim 正式程序 `final_outputs/tables/`（由 `claim_full_raw.csv` 生成）：

| 表 | 来源 CSV |
|---|---|
| `tab:main_makespan` / `tab:main_lbr` | `table_main_multicost_by_scale.csv`（已核对：LSCBO@200 makespan 46.87→46.9） |
| `tab:per_scale_cbo` | `table_main_relative_to_cbo_by_scale.csv` |
| `tab:phase_ablation` | `table_component_ablation.csv` |
| `tab:evolution` | `table_evolution_path.csv` |
| `tab:perturbation` | `table_perturbation.csv` |
| `tab:vm_heterogeneity` | `table_vm_heterogeneity.csv` |
| `tab:weight_configs` / 热力图 | `table_weight_sensitivity.csv` |
| `tab:cec_ranking` | CEC 程序 `cec2017_ranking_20260120.csv`（LSCBO 1.86…） |
| `tab:wilcoxon` | 由 `claim_full_raw.csv` 配对检验得出 |
| `tab:notation` / `tab:param_*` | 静态设定，无数据文件 |

## 4. 已弃用（superseded）—— 切勿再用于生成图表（会重新引入图表脱钩/旧样式）

| 类型 | 文件 | 被谁取代 |
|---|---|---|
| 数据 | `论文主稿/主稿工程/data/E4_LSCBOFinal_20260605_101152.csv` | `claim_full_raw.csv`（E4_main/W0_equal） |
| 数据 | `论文主稿/主稿工程/data/E2_Ablation_20260605_000119.csv` | `claim_full_raw.csv`（E2_ablation） |
| 数据 | `论文主稿/主稿工程/data/E3_Evolution_20260605_100334.csv` | `claim_full_raw.csv`（E3_evolution） |
| 数据 | `论文程序及图表/实验数据/CEC2017/ranking_ablation/cec_7algo_ranking.csv` | 与论文无关的旧消融（WOA/CBO-Levy/Random 集），非论文 CEC 排名 |
| 脚本 | `论文程序及图表/scripts/recompute_current_paper_tables.py` | 正式程序 `final_outputs/tables/` |
| 脚本 | `论文主稿/主稿工程/scripts/regenerate_consistent_lscbo_figures.py`（**stale 重复**，仍读 E4+旧样式） | `论文程序及图表/scripts/` 内的同名权威版（已移入 `reproducibility/_superseded/`） |
| 脚本 | `论文程序及图表/原始程序/当前论文数据生成工程/scripts/analyze_*.py` | 正式程序复算 |

> 旧数据/旧分析脚本被历史 review harness（`reviews/.../run.sh`）引用，故保留在原处仅标注弃用；如需彻底归档，把上表"数据/脚本"移入 `reproducibility/_superseded/` 即可（当前图表生成不依赖它们）。

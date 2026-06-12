# LSCBO 论文程序及图表

这个目录保存当前论文需要的代码、原始数据生成程序、当前数据副本和图表生成程序。

## 当前结构

| 目录/文件 | 用途 |
|---|---|
| `src/` | 当前主 Maven 代码 |
| `experiments/` | 补充实验和分析脚本 |
| `原始程序/当前论文数据生成工程/` | 直接生成当前论文 E2/E3/E4 数据 |
| `原始程序/CloudSim_Project_原始实验工程/` | 早期 CloudSim 原始实验工程 |
| `实验数据/当前论文数据/` | 当前论文使用的核心 CSV 和复算结果 |
| `图表/` | 当前论文正在使用的图片 |
| `scripts/` | 当前图表生成和表格复算脚本 |
| `当前论文图表程序清单.md` | 当前使用内容总清单 |

## 当前论文数据

当前论文正在使用的数据副本放在：

```text
实验数据/当前论文数据/
```

包括：

```text
E2_Ablation_20260605_000119.csv
E3_Evolution_20260605_100334.csv
E4_LSCBOFinal_20260605_101152.csv
cec2017_friedman_ranking.csv
weight_rank_values_metaheuristic.csv
```

## 重新生成 E2/E3/E4 数据

进入：

```text
原始程序/当前论文数据生成工程/
```

Windows 直接运行：

```text
run_current_paper_data_windows.bat
```

macOS/Linux 运行：

```bash
mvn exec:java -Dexec.mainClass="com.edcbo.research.FullAblation"
mvn exec:java -Dexec.mainClass="com.edcbo.research.CBOEvolutionPath"
mvn exec:java -Dexec.mainClass="com.edcbo.research.LSCBOFinalCorrect"
```

新生成的 CSV 会进入该工程自己的 `results/` 目录。

## 复算当前论文表格

在本目录运行：

```bash
python3 scripts/recompute_current_paper_tables.py
```

Windows 运行：

```text
scripts\recompute_current_paper_tables_windows.bat
```

输出：

```text
实验数据/当前论文数据/current_paper_recomputed_summary.md
实验数据/当前论文数据/current_phase_ablation_summary.csv
实验数据/当前论文数据/current_evolution_summary.csv
实验数据/当前论文数据/current_main_comparison_summary.csv
```

## 重新生成当前论文图片

在本目录运行：

```bash
python3 scripts/regenerate_consistent_lscbo_figures.py
```

Windows 运行：

```text
scripts\regenerate_consistent_lscbo_figures_windows.bat
```

图片会同步到：

```text
图表/
../论文主稿/主稿工程/figures/
```

## 快速检查

在本目录运行：

```bash
mvn test -q
python3 scripts/recompute_current_paper_tables.py
python3 scripts/regenerate_consistent_lscbo_figures.py
```

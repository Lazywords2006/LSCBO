# LSCBO 审稿补充实验 —— 实现文件

**生成日期**：2026-06-05  
**审稿轮次**：第 3 轮（Major Revision）  
**目标期刊**：Swarm and Evolutionary Computation  
**代码仓库**：https://github.com/lazywords/LSCBO

---

## 目录结构

```
experiments/
├── EXPERIMENT_PLAN.md           ← 完整实验计划（含背景、设计、参数）
├── README.md                    ← 本文件
├── shared/                      ← 共享工具库
│   ├── statistical_tests.py     ← Friedman, Wilcoxon, Cohen's d, 多重比较校正
│   └── plotting_utils.py        ← 论文级图表的统一绘图函数
├── E1_weight_sensitivity/       ← 权重敏感性分析
│   ├── WeightSensitivityExperiment.java
│   └── analyze_weight_sensitivity.py
├── E2_convergence/              ← 全算法收敛分析
│   └── analyze_convergence.py
├── E3_correlation/              ← Energy-Makespan 相关性分析
│   └── energy_makespan_correlation.py
├── E4_ls_transferability/       ← LS 可迁移性验证
│   └── analyze_ls_transferability.py
├── E5_perturbation/             ← Lévy vs 替代扰动
│   └── analyze_perturbation.py
├── E6_vm_heterogeneity/         ← VM 异质性鲁棒性
│   └── VMHeterogeneityExperiment.java
├── E7_phase_ablation/           ← Phase 2-3 消融
│   └── analyze_phase_ablation.py
└── E8_minmin_runtime/           ← Min-Min 运行时间匹配
    └── analyze_minmin.py
```

---

## 快速开始

### 前提条件

- **Java 17+**（运行 CloudSim Plus 仿真）
- **Python 3.9+**（数据分析与绘图）
- **Maven 3.x**（编译 Java 实验）

```bash
# 安装 Python 依赖
pip install numpy pandas scipy scikit-learn matplotlib seaborn

# 编译 Java 项目
cd CloudSim_Project
mvn clean package -DskipTests
```

### 运行实验

每个实验有其独立的入口点：

```bash
# E1: 权重敏感性（需要先编译）
java -cp target/classes:target/dependency/* \
  com.edcbo.research.WeightSensitivityExperiment

# E3: 相关性分析（纯数据分析，直接用现有 CSV）
cd experiments/E3_correlation
python energy_makespan_correlation.py \
  ../../experiment_results/JournalExperiment/journal_FINAL_1800.csv

# 其他实验 .java 文件需复制到 CloudSim_Project/src/main/java/com/edcbo/research/ 下
# 然后 mvn package 重新编译
```

---

## 实验清单

| # | 实验 | 优先级 | 类型 | 预估时间 |
|---|------|--------|------|---------|
| E1 | 权重敏感性分析 | **CRITICAL** | Java + Python | 5-7 天 |
| E2 | 全算法收敛分析 | **CRITICAL** | Python analysis | 1-2 天 |
| E3 | Energy-Makespan 相关性 | **CRITICAL** | Python analysis | 0.5 天 |
| E4 | LS 可迁移性 | MAJOR | Java + Python | 2-3 天 |
| E5 | 扰动机制比较 | MAJOR | Java + Python | 2-3 天 |
| E6 | VM 异质性鲁棒性 | MAJOR | Java + Python | 3-4 天 |
| E7 | Phase 2-3 消融 | MAJOR | Java + Python | 1-2 天 |
| E8 | Min-Min 运行时间匹配 | SUGGESTED | Java + Python | 0.5 天 |

### 优先级说明

- **CRITICAL**：三位审稿人一致要求，必须完成方可重新提交
- **MAJOR**：显著增强论文质量，强烈建议完成
- **SUGGESTED**：有加分但非必需

---

## 文件说明

### shared/ —— 共享工具

- `statistical_tests.py`：
  - `friedman_test(data)` — Friedman 秩和检验（含并列处理）
  - `wilcoxon_paired(baseline, improved)` — Wilcoxon 配对检验
  - `cohens_d(baseline, improved)` — Cohen's d 效应量
  - `holm_correction(p_values)` / `bonferroni_correction(p_values)` — 多重比较校正
  - `print_friedman_table(result, names)` — 格式化输出

- `plotting_utils.py`：
  - `plot_convergence_curves()` — 多算法收敛曲线子图
  - `plot_rank_heatmap()` — 跨配置排名热力图
  - `plot_scatter_correlation()` — 散点图 + 回归线 + 相关系数
  - `plot_effect_size_forest()` — Cohen's d 森林图
  - `plot_rank_stability_matrix()` — Spearman ρ 排名稳定性矩阵

### 实验文件

每个实验目录包含：
- **Java 源文件**：扩展现有的 `JournalExperiment.java` 模式
- **Python 分析脚本**：读取 CSV → 统计检验 → 生成图表
- **输出**：`.png` 图表 + `.csv` 汇总表

---

## 数据流

```
Java 实验 (CloudSim Plus)
  └→ CSV (raw_results)
      └→ Python 分析脚本
          ├→ 统计检验输出 (.txt)
          ├→ 汇总表 (.csv)
          └→ 论文级图表 (.png)
```

---

## 论文整合

每个实验完成后，将：

1. **图表** 插入论文对应 Section
2. **数字** 写入 LaTeX 表格
3. **结论** 更新 Discussion

详见 `EXPERIMENT_PLAN.md` 中各实验的"预期结果与论文位置"部分。

---

## 与现有代码的关系

本目录包含 **补充实验** 的实现代码。现有的主体实验代码在：
- `CloudSim_Project/src/main/java/com/edcbo/research/` — 主体 Broker 和实验类
- `scripts/` — 现有一轮审稿的 Python 分析脚本
- `experiment_results/` — 现有实验的 CSV 结果文件

补充实验文件通过以下方式与现有代码兼容：
- Java 文件扩展自同一 `JournalExperiment` 模式
- 复用 `utils/` 中的 CostCalculator、EnergyCalculator、LoadBalanceCalculator
- Python 脚本读取与现有 CSV 相同的列格式

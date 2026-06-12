# LSCBO 论文——审稿后补充实验计划

**生成日期**：2026-06-05  
**审稿轮次**：第 3 轮（Major Revision）  
**目标期刊**：Swarm and Evolutionary Computation  
**当前代码仓库**：https://github.com/lazywords/LSCBO  

---

## 目录

1. [实验优先级总览](#1-实验优先级总览)
2. [实验 E1：权重敏感性分析（CRITICAL）](#2-实验-e1权重敏感性分析)
3. [实验 E2：全算法收敛分析（CRITICAL）](#3-实验-e2全算法收敛分析)
4. [实验 E3：Energy-Makespan 相关性分析（CRITICAL）](#4-实验-e3energy-makespan-相关性分析)
5. [实验 E4：局部搜索可迁移性验证（MAJOR）](#5-实验-e4局部搜索可迁移性验证)
6. [实验 E5：Lévy vs 替代扰动机制比较（MAJOR）](#6-实验-e5levy-vs-替代扰动机制比较)
7. [实验 E6：额外 VM 异质性配置鲁棒性（MAJOR）](#7-实验-e6额外-vm-异质性配置鲁棒性)
8. [实验 E7：Phase 2-3 独立贡献消融（MAJOR）](#8-实验-e7phase-2-3-独立贡献消融)
9. [实验 E8：Min-Min 运行时间匹配比较（SUGGESTED）](#9-实验-e8min-min-运行时间匹配比较)
10. [总体时间估计与执行顺序](#10-总体时间估计与执行顺序)

---

## 1. 实验优先级总览

| 编号 | 实验名称 | 优先级 | Trial 数 | 预估时间 | 状态 |
|------|---------|--------|----------|---------|------|
| E1 | 权重敏感性分析 | **CRITICAL** | ~4050 | 5-7 天 | ⬜ 待执行 |
| E2 | 全算法收敛分析 | **CRITICAL** | ~45 条曲线 | 1-2 天 | ⬜ 待执行 |
| E3 | Energy-Makespan 相关性 | **CRITICAL** | 数据分析 | 0.5 天 | ⬜ 待执行 |
| E4 | LS 可迁移性 | MAJOR | ~450 | 2-3 天 | ⬜ 待执行 |
| E5 | Lévy vs 替代扰动 | MAJOR | ~450 | 2-3 天 | ⬜ 待执行 |
| E6 | VM 异质性鲁棒性 | MAJOR | ~1350 | 3-4 天 | ⬜ 待执行 |
| E7 | Phase 2-3 消融 | MAJOR | ~450 | 1-2 天 | ⬜ 待执行 |
| E8 | Min-Min 运行时间匹配 | SUGGESTED | 5 次运行 | 0.5 天 | ⬜ 待执行 |

**预估总 Trial 数**：约 6750+（不含原 1350）  
**预估总时间**：2-3 周（取决于可用算力）

---

## 2. 实验 E1：权重敏感性分析

### 背景
审稿人一致指出（EIC W3, R3 W2, DA CRITICAL #2），LSCBO 的"灵活性"价值主张——即元启发式可通过适应变化的目标权重来补偿确定性启发式的弱点——在所有实验中仅使用了等权标量化（$w_k=0.25$），未经任何实证检验。

此外，LSCBO 的局部搜索算子直接针对负载均衡（通过将任务从过载 VM 迁移到欠载 VM），在当前等权配置下 $w_{lb}=0.25$ 可能与 LS 产生结构性偏向。在负载均衡权重较低的配置下，LSCBO 的相对优势可能缩小。

### 目标
验证以下假设：
- **H1**：LSCBO 的排名优势在能源-成本平衡配置下保持稳健（因 Energy ∝ Makespan 在线性功耗模型下，排名变化有限）
- **H2**：LSCBO 的排名优势在 makespan 主导配置下可能缩小（因 LS 偏向负载均衡，其权重降低）
- **H3**：算法间排名在极端权重配置（仅 makespan、仅负载均衡）下发散，LSCBO 可能在纯 makespan 模式下被 GTO/WOA 超越

### 权重配置

| 配置 | $w_{ms}$ | $w_{en}$ | $w_{cost}$ | $w_{lb}$ | 描述 |
|------|----------|----------|------------|----------|------|
| **W0（当前）** | 0.25 | 0.25 | 0.25 | 0.25 | 等权基准 |
| **W1** | 0.60 | 0.10 | 0.10 | 0.20 | Makespan 主导 |
| **W2** | 0.10 | 0.40 | 0.40 | 0.10 | 能源-成本平衡 |
| **W3** | 0.85 | 0.05 | 0.05 | 0.05 | 纯 Makespan |
| **W4** | 0.05 | 0.05 | 0.05 | 0.85 | 纯负载均衡 |

### 实验设计
- **算法**：全部 9 种（LSCBO, GTO, WOA, GWO, HHO, DBO, CBO, PSO, AOA）+ Min-Min（作为 makespan 上限基准）
- **任务规模**：$N=200, 500, 1000, 2000, 5000$
- **种子数**：30 个独立运行（seeds 43-72）
- **配置数**：4 种新权重配置（W1-W4）
- **总 Trial 数**：$4 \times 5 \times 30 \times 9 = 5400$（减去 W0 已有的 1350，**新增约 4050 trial**）
- **评估预算**：400 次函数评估（pop=20, iter=20）
- **SPV+MFD 解码器**：所有算法共享

### 输出指标
- 每种权重配置下的 Friedman 排名表
- 各配置间的排名稳定性矩阵（Spearman $\rho$）
- LSCBO 在每种配置下的相对优势热力图
- Makespan、Energy、Cost、LBR 的 per-config 分解表

### 预期结果与论文位置
- 插入 Section 6 作为新子节 "Weight Sensitivity Analysis"
- 在 Abstract 中更新"conditional on equal-weight scalarization"的表述
- 替换 Section 7（Discussion）中关于"alternative weight configuration ranking"的定性推测为定量结果

### 代码修改点
- `objective_function.py`：添加多权重配置支持
- `experiment_runner.py`：循环遍历权重向量
- `results_aggregator.py`：聚合多配置结果
- 新建 `figures/fig_weight_sensitivity.py`：生成权重敏感性热力图

---

## 3. 实验 E2：全算法收敛分析

### 背景
当前收敛分析仅覆盖 3 种算法（LSCBO, CBO, GTO）。R1 W1 指出：不同算法的收敛速度不同，有些可能远未收敛于 400 次评估预算。需要全部 9 种算法的收敛曲线和不同评估预算下的排名稳定性。

### 目标
- 为全部 9 种算法生成 per-iteration makespan 收敛曲线
- 评估在 200、400、800、1600 次评估预算下的排名稳定性

### 实验设计

#### Part A：收敛曲线（全部 9 种算法）
- **算法**：全部 9 种
- **规模**：$N=200, 500, 1000, 2000, 5000$
- **种子数**：30
- **数据要求**：每迭代记录 best-so-far makespan（mean ± std）
- **总 Trial 数**：复用已有数据（需在代码中添加 per-iteration 日志）
- **额外运行**：如果现有数据未包含 per-iteration 轨迹，需重新运行：$9 \times 5 \times 30 = 1350$ trial

#### Part B：评估预算敏感性
- 在 200、400、800、1600 次评估预算下计算 Friedman 排名
- 分析排名稳定性：计算相邻预算间的 Spearman $\rho$

### 输出
- 全部 9 种算法的收敛曲线图（5 个子图，按任务规模）
- 评估预算 vs 排名稳定性表

### 预期结果与论文位置
- 更新 Section 6.3（Convergence Analysis），将原有的 3 种算法扩展至全部 9 种
- 新增 "Evaluation Budget Sensitivity" 子节

### 代码修改点
- 所有算法 wrapper 中添加 per-iteration best-so-far 日志
- `convergence_analyzer.py`：汇总并对齐各算法的 per-iteration 轨迹
- `figures/fig_convergence_all9.py`：生成收敛曲线图

---

## 4. 实验 E3：Energy-Makespan 相关性分析

### 背景
CloudSim Plus 使用线性功耗模型 $P_j(u) = P_j^{idle} + (P_j^{max} - P_j^{idle}) \times u$，导致 Makespan 和 Energy 之间存在结构性依赖。DA CRITICAL #3 指出声称"四目标优化"在 $r > 0.9$ 的情况下是过度声明。

### 目标
- 从现有实验数据中提取 Makespan 和 Energy 的 per-run 值
- 计算 Pearson 和 Spearman 相关系数
- 评估有效目标维度

### 实验设计（纯数据分析，无需额外运行）
- **数据来源**：已有 1350 trial 的 per-run Makespan 和 Energy 值
- **分析**：
  1. 汇总统计：Makespan vs Energy 散点图（全部 1350 点）
  2. Per-scale 相关性：5 个规模的 $r_{Pearson}$ 和 $\rho_{Spearman}$
  3. 线性回归：Energy = $\beta_0 + \beta_1 \times$ Makespan，报告 $R^2$
  4. PCA 分析：四目标的主成分方差解释比例

### 输出
- Makespan-Energy 散点图（按任务规模着色）
- Per-scale 相关性表
- PCA 方差解释表

### 预期结果与论文位置
- 替换 Section 7 中关于 Energy-Makespan 相关性的定性讨论为定量结果
- 修正 Abstract 中 "four-objective" → "multi-criteria" 或明确说明有效维度的表述
- 在 Section 4.2 中添加脚注说明线性功耗模型的含义

### 代码修改点
- 新建 `analysis/correlation_analysis.py`
- 从已有结果文件中提取 Makespan 和 Energy per-run 数据
- `figures/fig_energy_makespan_correlation.py`

---

## 5. 实验 E4：局部搜索可迁移性验证

### 背景
如果 LS 应用于 GTO、PSO、GWO 也产生类似的 50%+ 改进，LSCBO 的贡献将从"特定算法"降级为"通用方法发现"。反之，如果 LS 仅与 CBO 协同有效，这种特异性本身就是一个有意义的发现。

### 目标
回答：任务迁移局部搜索是与 CBO 特定协同，还是一个通用的云调度增强算子？

### 实验设计
- **基准算法**：GTO、PSO、GWO（分别代表 top-performer、经典、中游算法）
- **对比**：原始算法 vs 原始算法+LS（每 10 次迭代调用）
- **任务规模**：$N=200, 500, 1000, 2000, 5000$
- **种子数**：30
- **总 Trial 数**：$3 \times 2 \times 5 \times 30 = 900$ trial
- **评估预算**：400 次函数评估

### 输出
- 每种基准算法的 LS 前后 makespan 对比表
- Per-baseline 改进百分比（类似 per-scale CBO improvement 表）
- 统计检验：原始 vs +LS 的 Wilcoxon paired test

### 预期结果与论文位置
- 新增 Section 6 "Local Search Transferability" 子节
- 如果结果为正面（LS 广泛有效）：论文贡献升级为"通用调度增强算子的发现"
- 如果结果为负面（LS 仅对 CBO 有效）：强调 CBO-LS 协同的特异性
- 无论结果如何，都是可发表的有价值发现

### 代码修改点
- `ls_operator.py`：确保 LS 算子与算法无关（接受任意 schedule 作为输入）
- `experiment_runner.py`：添加 GTO+LS、PSO+LS、GWO+LS 变体
- `figures/fig_ls_transferability.py`

---

## 6. 实验 E5：Lévy vs 替代扰动机制比较

### 背景
DA MAJOR #6 和 R1 相关评论指出：Lévy flight 的 +12.9% 贡献可能部分反映"tanh 替换"效应（即任何随机扰动替换弱确定性 tanh 算子都会提供某种收益），而非 Lévy 特异优势。此外，Abstract 和 Conclusion 中引用的 +12.9% 是在无 LS 环境中测量的；在最终 LSCBO 配置下的实际边际贡献仅为 +1.6 个百分点。

### 目标
确定 Lévy flight 的贡献是否特定于重尾分布，还是高斯、柯西或随机重启也能实现类似增益。

### 实验设计
- **基准算法**：CBO+LS（不含 Lévy flight）
- **扰动机制**：
  1. Lévy flight（$\beta=1.5$，当前方案）
  2. 高斯扰动（$\sigma$ 匹配 Lévy 的 scale 参数）
  3. 柯西扰动（scale 参数匹配 Lévy）
  4. 随机重启（每 K 次迭代以概率 $p$ 随机重置位置）
  5. 无扰动（仅 CBO+LS，作为对照）
- **任务规模**：$N=200, 500, 1000, 2000, 5000$
- **种子数**：30
- **总 Trial 数**：$5 \times 5 \times 30 = 750$ trial（或 $4 \times 5 \times 30 = 600$，如果复用 CBO+LS 结果）
- **评估预算**：400 次函数评估

### 输出
- 5 种扰动机制的 makespan 对比表
- Per-perturbation vs 无扰动的 Wilcoxon paired test
- 实际效应量（Cohen's d）对比

### 预期结果与论文位置
- 新增 Section 6 "Perturbation Mechanism Comparison" 子节
- 修正 Abstract 和 Conclusion 中 Lévy flight 的数字表述，使用最终 LSCBO 配置下的实际边际贡献（+1.6 pp），而非无 LS 环境下的 +12.9%

### 代码修改点
- `perturbations.py`：实现 Gaussian、Cauchy、RandomRestart 扰动
- `experiment_runner.py`：添加扰动变体
- `figures/fig_perturbation_comparison.py`

---

## 7. 实验 E6：额外 VM 异质性配置鲁棒性

### 背景
当前实验仅使用一种 VM 异构配置（MIPS 500-2000，4:1 比例）和一种任务分布（MI 1000-20000，均匀分布）。R1 W4 质疑场景覆盖是否足以支撑"跨规模鲁棒性"的声明。

### 实验设计

#### Part A：VM 异质性扩展
- **配置 A**（当前）：MIPS ~ U(500, 2000)，4:1 比例
- **配置 B**（高异质性）：MIPS ~ U(200, 4000)，20:1 比例（模拟 GPU vs 通用实例差异）
- **配置 C**（低异质性）：MIPS ~ U(800, 1200)，1.5:1 比例

#### Part B（可选）：任务分布扩展
- **分布 A**（当前）：MI ~ U(1000, 20000)
- **分布 B**（高方差）：MI ~ LogNormal($\mu=9$, $\sigma=1.5$)，长尾大任务
- **分布 C**（双峰）：50% MI ~ U(1000, 5000)，50% MI ~ U(15000, 20000)

### 实验规模（仅 Part A）
- **算法**：全部 9 种
- **配置数**：2 种新配置
- **规模**：$N=200, 500, 1000, 2000, 5000$
- **种子数**：30
- **总 Trial 数**：$2 \times 9 \times 5 \times 30 = 2700$ trial

### 实验规模（Part A+B 完整）
- **总 Trial 数**：$4 \times 9 \times 5 \times 30 = 5400$ trial（建议仅做 Part A，Part B 作为后续）

### 输出
- 每种 VM 配置下的 Friedman 排名表
- 跨配置排名稳定性矩阵
- LSCBO 在不同 VM 异质性下的改进百分比变化

### 预期结果与论文位置
- 新增 Section 6 "Robustness to VM Heterogeneity" 子节
- 或者作为 Supplementary Materials 中的额外分析

### 代码修改点
- `cloudsim_config.py`：添加多 VM 配置支持
- `experiment_runner.py`：循环遍历 VM 配置

---

## 8. 实验 E7：Phase 2-3 独立贡献消融

### 背景
当前消融实验仅测试了 Phase 1 的修改（Lévy 替换、动态权重）、staging 移除和 LS 的加入。Phases 2（旋转矩阵包围）和 3（静态攻击，$w=0.5$）被保留但从未被独立验证。Discussion 中承认了这一点，但缺乏实验数据。DA 的"加法设计偏见"观察指出：最优设计可能只是"局部搜索 + 最小随机扰动"。

### 目标
通过消融 Phase 2 和 Phase 3 的独立贡献来回答："如果 Phases 2-3 的贡献可忽略，LSCBO 将退化为什么？"

### 实验设计
- **配置**：
  1. CBO+Lévy+LS（完整 LSCBO，对照）
  2. CBO+Lévy+LS - Phase 2（移除旋转矩阵包围）
  3. CBO+Lévy+LS - Phase 3（移除静态攻击）
  4. CBO+Lévy+LS - Phase 2 - Phase 3（仅 Phase 1 + LS）
  5. 随机搜索 + LS（下界基准）
- **任务规模**：$N=200, 500, 1000, 2000, 5000$
- **种子数**：30
- **总 Trial 数**：$5 \times 5 \times 30 = 750$ trial
- **评估预算**：400 次函数评估

### 输出
- 5 种配置的 makespan 对比表
- Per-config 消融的 Friedman 测试
- "最小有效配置"的确定

### 预期结果与论文位置
- 新增 Section 6 "Phase 2-3 Isolation Ablation" 子节
- 解决 DA 的"Frame-Lock"检测：验证"减法"设计哲学的可行性

### 代码修改点
- `cbo_phases.py`：添加标志位控制 Phase 2 和 Phase 3 的启用/禁用
- `experiment_runner.py`：添加消融变体

---

## 9. 实验 E8：Min-Min 运行时间匹配比较

### 背景
当前 Min-Min 比较基于"调度质量"而非"运行时间匹配"条件。Min-Min 执行 $O(N^2 M)$ 次调度评估（$N=5000$ 时约 $2.6\times10^7$ 次），而元启发式仅使用 400 次评估。R3 W3 和 R1 W5 建议进行运行时间匹配的比较或明确将 Min-Min 定性为上限基准。

### 实验设计
- 将 Min-Min 限制为仅评估前 400 个任务（模拟 400 次评估预算）
- 在 $N=200, 1000, 5000$ 上运行
- 每个规模 10 次运行（Min-Min 是确定性的，种子影响 VM 配置采样）

### 输出
- 运行时间匹配下的 Min-Min vs LSCBO makespan 对比表
- 讨论：Min-Min 在 400 次评估下是否仍优于 LSCBO

### 预期结果与论文位置
- 补充 Section 6.4（Min-Min 比较）中的讨论
- 或者作为 Supplementary Materials

---

## 10. 总体时间估计与执行顺序

### 推荐执行顺序

```
第 1 周：E3（0.5 天）→ E1 开始（5-7 天并行运行）
第 2 周：E1 继续 + E2 Part A（1 天，代码修改）
第 2 周：E4（2-3 天）+ E7（1-2 天）并行
第 3 周：E5（2-3 天）+ E6 Part A（3-4 天）并行
第 3 周：E8（0.5 天）+ 数据分析与写作整合
```

### 关键路径依赖

| 实验 | 依赖 | 可并行 |
|------|------|--------|
| E1 | 无 | 是 |
| E2 | 需要 per-iteration 日志（可能需要修改代码重新运行 E1 部分数据） | 部分 |
| E3 | E1 数据（数据分析，无需额外运行） | 是 |
| E4 | 需要 LS 算子与算法解耦 | 是 |
| E5 | 无 | 是 |
| E6 | 无 | 是 |
| E7 | 无 | 是 |
| E8 | 无 | 是 |

### 优先级分类

**必须完成（CRITICAL，审稿人一致要求）**：
- E1：权重敏感性分析
- E2：全算法收敛分析  
- E3：Energy-Makespan 相关性

**强烈建议（MAJOR，显著增强论文）**：
- E4：LS 可迁移性
- E5：Lévy vs 替代扰动
- E7：Phase 2-3 消融

**锦上添花（有加分，非必需）**：
- E6：额外 VM 配置
- E8：Min-Min 运行时间匹配

---

## 附录 A：实验环境

| 项目 | 配置 |
|------|------|
| 仿真平台 | CloudSim Plus 8.0.0 |
| JDK 版本 | Java 17+ |
| Python 版本 | 3.9+（数据分析与绘图） |
| 单个 Trial 平均耗时 | ~5-15 秒（取决于 $N$） |
| 1350 Trial 估计耗时 | ~3-5 小时（单机） |
| 4050 Trial 估计耗时 | ~10-15 小时（单机） |
| 推荐并行度 | 8-16 核（按规模/种子并行） |

## 附录 B：代码仓库修改清单

```
LSCBO/
├── experiments/
│   ├── EXP001_weight_sensitivity/
│   │   ├── config.json
│   │   └── run.sh
│   ├── EXP002_convergence/
│   ├── EXP004_ls_transferability/
│   ├── EXP005_perturbation_comparison/
│   ├── EXP006_vm_heterogeneity/
│   ├── EXP007_phase_ablation/
│   └── EXP008_minmin_runtime/
├── src/
│   ├── objective_function.py    ← 多权重配置
│   ├── perturbations.py         ← 新增：扰动机制
│   ├── experiment_runner.py     ← 新增变体
│   ├── results_aggregator.py    ← 多配置聚合
│   └── convergence_analyzer.py  ← per-iteration 日志
├── analysis/
│   ├── correlation_analysis.py  ← E3
│   └── weight_sensitivity.py    ← E1 后分析
└── figures/
    ├── fig_weight_sensitivity.py
    ├── fig_convergence_all9.py
    ├── fig_energy_makespan_correlation.py
    ├── fig_ls_transferability.py
    ├── fig_perturbation_comparison.py
    └── fig_phase_ablation.py
```

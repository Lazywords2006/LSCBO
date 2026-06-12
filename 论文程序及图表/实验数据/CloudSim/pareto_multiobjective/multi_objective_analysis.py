"""
CloudSim 多目标优化分析脚本
评估5个指标：Makespan, Cost, Energy, LoadBalanceIndex, ImbalanceDegree
"""
import pandas as pd
import numpy as np
from scipy import stats
import os

# 读取所有数据
results_dir = r"d:\论文\new\edcbo-cloudsim\CloudSim_Project\results"
files = [f for f in os.listdir(results_dir) if f.startswith("parallel_group") and "201846" in f or "201845" in f]
files = [f for f in os.listdir(results_dir) if f.startswith("parallel_group") and f.endswith(".csv")]
# 获取最新的文件
files = sorted([f for f in files if "201846" in f or "201845" in f])

dfs = []
for f in files:
    df = pd.read_csv(os.path.join(results_dir, f))
    dfs.append(df)

data = pd.concat(dfs, ignore_index=True)
print(f"加载数据: {len(data)} 条记录")
print(f"算法: {data['Algorithm'].unique()}")
print(f"任务规模: {sorted(data['TaskCount'].unique())}")
print()

# 定义指标 (小值更优)
metrics = {
    'Makespan': '完成时间 (越小越好)',
    'Cost': '成本 (越小越好)', 
    'Energy': '能耗 (越小越好)',
    'LoadBalanceIndex': '负载均衡指数 (越小越好)',
    'ImbalanceDegree': '不均衡度 (越小越好)'
}

# ==================== 1. 全指标平均表现 ====================
print("=" * 100)
print("1. 各算法多目标平均表现")
print("=" * 100)

summary = data.groupby('Algorithm')[list(metrics.keys())].mean()
summary = summary.sort_values('Makespan')
print(summary.round(4))
print()

# ==================== 2. 各指标排名统计 ====================
print("=" * 100)
print("2. 各指标排名 (1=最优)")
print("=" * 100)

rankings = {}
for metric in metrics.keys():
    rankings[metric] = summary[metric].rank().astype(int)

rank_df = pd.DataFrame(rankings)
rank_df['平均排名'] = rank_df.mean(axis=1)
rank_df = rank_df.sort_values('平均排名')
print(rank_df)
print()

# ==================== 3. LSCBO vs 其他算法 (所有指标) ====================
print("=" * 100)
print("3. LSCBO相对其他算法的提升/下降百分比")
print("=" * 100)

lscbo_means = summary.loc['LSCBO']
comparison = []

for algo in summary.index:
    if algo != 'LSCBO':
        row = {'算法': algo}
        for metric in metrics.keys():
            other_val = summary.loc[algo, metric]
            lscbo_val = lscbo_means[metric]
            # 正值=LSCBO更优
            improvement = (other_val - lscbo_val) / other_val * 100
            row[f'{metric}提升%'] = improvement
        comparison.append(row)

comp_df = pd.DataFrame(comparison)
print(comp_df.round(2).to_string(index=False))
print()

# ==================== 4. 元启发式算法内部对比 ====================
print("=" * 100)
print("4. 元启发式算法内部对比 (LSCBO vs CBO/PSO/AOA/GWO)")
print("=" * 100)

meta_algos = ['LSCBO', 'CBO', 'PSO', 'AOA', 'GWO']
meta_summary = summary.loc[summary.index.isin(meta_algos)]
print(meta_summary.round(4))
print()

# 元启发式排名
meta_rankings = {}
for metric in metrics.keys():
    meta_rankings[metric] = meta_summary[metric].rank().astype(int)
meta_rank_df = pd.DataFrame(meta_rankings)
meta_rank_df['平均排名'] = meta_rank_df.mean(axis=1)
meta_rank_df = meta_rank_df.sort_values('平均排名')
print("元启发式算法排名:")
print(meta_rank_df)
print()

# ==================== 5. 统计显著性检验 (Wilcoxon) ====================
print("=" * 100)
print("5. 统计显著性检验 (LSCBO vs 其他, Wilcoxon rank-sum test)")
print("=" * 100)

lscbo_data = data[data['Algorithm'] == 'LSCBO']
sig_results = []

for algo in summary.index:
    if algo != 'LSCBO':
        other_data = data[data['Algorithm'] == algo]
        row = {'算法': algo}
        
        for metric in ['Makespan', 'Cost', 'Energy']:
            try:
                stat, p = stats.mannwhitneyu(
                    lscbo_data[metric], 
                    other_data[metric], 
                    alternative='less'  # LSCBO < Other
                )
                sig = "***" if p < 0.001 else "**" if p < 0.01 else "*" if p < 0.05 else ""
                row[f'{metric}_p'] = f"{p:.4f}{sig}"
            except:
                row[f'{metric}_p'] = "N/A"
        
        sig_results.append(row)

sig_df = pd.DataFrame(sig_results)
print(sig_df.to_string(index=False))
print("\n注: *** p<0.001, ** p<0.01, * p<0.05")
print()

# ==================== 6. 按任务规模细分 ====================
print("=" * 100)
print("6. LSCBO在不同任务规模下的表现")
print("=" * 100)

lscbo_by_scale = data[data['Algorithm'] == 'LSCBO'].groupby('TaskCount')[list(metrics.keys())].mean()
print(lscbo_by_scale.round(4))
print()

# ==================== 7. 保存完整报告 ====================
output_file = os.path.join(results_dir, "multi_objective_analysis.txt")
with open(output_file, 'w', encoding='utf-8') as f:
    f.write("CloudSim 多目标优化实验分析报告\n")
    f.write("=" * 60 + "\n\n")
    f.write(f"总实验数: {len(data)}\n")
    f.write(f"算法数: {len(data['Algorithm'].unique())}\n")
    f.write(f"任务规模: {sorted(data['TaskCount'].unique())}\n\n")
    
    f.write("评估指标:\n")
    for metric, desc in metrics.items():
        f.write(f"  - {metric}: {desc}\n")
    
    f.write("\n" + "=" * 60 + "\n")
    f.write("元启发式算法综合排名:\n")
    f.write(meta_rank_df.to_string())
    
    f.write("\n\n" + "=" * 60 + "\n")
    f.write("结论:\n")
    best_meta = meta_rank_df.index[0]
    f.write(f"  在元启发式算法中，{best_meta} 综合表现最优。\n")

print(f"完整报告已保存: {output_file}")

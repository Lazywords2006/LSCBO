import pandas as pd
import numpy as np
from scipy import stats
import os

# 读取合并的数据
results_dir = r"d:\论文\new\edcbo-cloudsim\CloudSim_Project\results"
csv_files = [f for f in os.listdir(results_dir) if f.startswith("merged_all_")]
latest_file = sorted(csv_files)[-1]
df = pd.read_csv(os.path.join(results_dir, latest_file))

print(f"加载数据: {latest_file}")
print(f"总记录数: {len(df)}")
print(f"算法: {df['Algorithm'].unique()}")
print(f"任务规模: {sorted(df['TaskCount'].unique())}")
print()

# ==================== 1. 各算法各指标的平均值 ====================
print("=" * 80)
print("1. 各算法平均性能 (across all task counts)")
print("=" * 80)

metrics = ['Makespan', 'Cost', 'Energy', 'LoadBalanceIndex', 'ImbalanceDegree']
summary = df.groupby('Algorithm')[metrics].agg(['mean', 'std'])
print(summary.round(4))
print()

# ==================== 2. LSCBO vs 其他算法对比 ====================
print("=" * 80)
print("2. LSCBO vs 其他算法 (Makespan 对比)")
print("=" * 80)

lscbo_data = df[df['Algorithm'] == 'LSCBO']['Makespan']
lscbo_mean = lscbo_data.mean()

comparison = []
for algo in df['Algorithm'].unique():
    if algo != 'LSCBO':
        other_data = df[df['Algorithm'] == algo]['Makespan']
        other_mean = other_data.mean()
        improvement = (other_mean - lscbo_mean) / other_mean * 100
        
        # Wilcoxon 检验
        try:
            stat, p_value = stats.mannwhitneyu(lscbo_data, other_data, alternative='less')
            sig = "***" if p_value < 0.001 else "**" if p_value < 0.01 else "*" if p_value < 0.05 else ""
        except:
            p_value = 1.0
            sig = ""
        
        comparison.append({
            'Algorithm': algo,
            'Mean Makespan': other_mean,
            'LSCBO Improvement %': improvement,
            'p-value': p_value,
            'Sig': sig
        })

comp_df = pd.DataFrame(comparison)
comp_df = comp_df.sort_values('Mean Makespan', ascending=False)
print(f"LSCBO Mean Makespan: {lscbo_mean:.4f}")
print()
print(comp_df.to_string(index=False))
print()

# ==================== 3. 按任务规模分析 ====================
print("=" * 80)
print("3. 按任务规模分析 (Makespan)")
print("=" * 80)

pivot = df.pivot_table(values='Makespan', index='Algorithm', columns='TaskCount', aggfunc='mean')
print(pivot.round(2))
print()

# ==================== 4. 算法排名 ====================
print("=" * 80)
print("4. 综合排名 (Makespan越小越好)")
print("=" * 80)

ranking = df.groupby('Algorithm')['Makespan'].mean().sort_values()
print("排名 | 算法 | 平均Makespan")
for i, (algo, val) in enumerate(ranking.items(), 1):
    marker = "🥇" if i == 1 else "🥈" if i == 2 else "🥉" if i == 3 else f"{i}."
    print(f"{marker:4s} | {algo:12s} | {val:.4f}")

# ==================== 5. 保存统计结果 ====================
output_file = os.path.join(results_dir, "statistical_analysis.txt")
with open(output_file, 'w', encoding='utf-8') as f:
    f.write("CloudSim 实验统计分析报告\n")
    f.write("=" * 60 + "\n\n")
    f.write(f"数据文件: {latest_file}\n")
    f.write(f"总实验数: {len(df)}\n")
    f.write(f"算法数: {len(df['Algorithm'].unique())}\n")
    f.write(f"任务规模: {sorted(df['TaskCount'].unique())}\n\n")
    
    f.write("各算法Makespan平均值:\n")
    for algo, val in ranking.items():
        f.write(f"  {algo}: {val:.4f}\n")
    
    f.write(f"\n最优算法: {ranking.index[0]}\n")

print(f"\n分析结果已保存: {output_file}")

#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
生成Q1/Q2实验的可视化图表

图表列表：
1. 算法性能对比柱状图（9算法×7规模）
2. 算法排名对比图
3. LSCBO-Fixed vs CBO改进率折线图
4. 负载均衡对比图
5. 统计显著性热力图
"""

import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
import seaborn as sns
from pathlib import Path
from scipy import stats

# 设置中文字体和样式
plt.rcParams['font.sans-serif'] = ['SimHei', 'DejaVu Sans']
plt.rcParams['axes.unicode_minus'] = False
plt.rcParams['figure.dpi'] = 300
sns.set_style("whitegrid")

# 算法顺序和颜色
ALGORITHMS = ["PSO", "LSCBO-Fixed", "GTO", "WOA", "HHO", "GWO", "CBO", "AOA", "Random"]
SCALES = [50, 100, 200, 300, 500, 1000, 2000]

# 算法颜色映射
COLORS = {
    "PSO": "#1f77b4",           # 蓝色
    "LSCBO-Fixed": "#ff7f0e",   # 橙色
    "GTO": "#2ca02c",           # 绿色
    "WOA": "#d62728",           # 红色
    "HHO": "#9467bd",           # 紫色
    "GWO": "#8c564b",           # 棕色
    "CBO": "#e377c2",           # 粉色
    "AOA": "#7f7f7f",           # 灰色
    "Random": "#bcbd22"         # 黄绿色
}

def load_data():
    """加载合并后的数据"""
    df = pd.read_csv("results/q1q2_full_3150_merged.csv")
    return df

def plot_performance_comparison(df, output_dir):
    """图1: 算法性能对比柱状图（9算法×7规模）"""
    print("Generating Figure 1: Performance Comparison Bar Chart...")

    # 计算每个算法在每个规模上的平均Makespan
    summary = df.groupby(['Algorithm', 'TaskCount'])['InternalMakespan'].mean().reset_index()

    # 创建子图（7个规模）
    fig, axes = plt.subplots(2, 4, figsize=(20, 10))
    axes = axes.flatten()

    for idx, scale in enumerate(SCALES):
        ax = axes[idx]
        scale_data = summary[summary['TaskCount'] == scale].sort_values('InternalMakespan')

        # 绘制柱状图
        bars = ax.bar(range(len(scale_data)), scale_data['InternalMakespan'],
                      color=[COLORS[algo] for algo in scale_data['Algorithm']])

        ax.set_title(f'M={scale}', fontsize=14, fontweight='bold')
        ax.set_xlabel('Algorithm', fontsize=12)
        ax.set_ylabel('Makespan (s)', fontsize=12)
        ax.set_xticks(range(len(scale_data)))
        ax.set_xticklabels(scale_data['Algorithm'], rotation=45, ha='right', fontsize=10)
        ax.grid(axis='y', alpha=0.3)

        # 标注前3名
        for i in range(min(3, len(scale_data))):
            height = scale_data.iloc[i]['InternalMakespan']
            ax.text(i, height, f'{height:.1f}', ha='center', va='bottom', fontsize=9)

    # 删除多余的子图
    fig.delaxes(axes[7])

    plt.tight_layout()
    plt.savefig(output_dir / "fig1_performance_comparison.png", dpi=300, bbox_inches='tight')
    plt.close()
    print("  [OK] Saved: fig1_performance_comparison.png")

def plot_algorithm_ranking(df, output_dir):
    """图2: 算法排名对比图"""
    print("Generating Figure 2: Algorithm Ranking Comparison...")

    # 计算每个算法在每个规模上的排名
    rankings = []
    for scale in SCALES:
        scale_data = df[df['TaskCount'] == scale].groupby('Algorithm')['InternalMakespan'].mean()
        ranks = scale_data.rank(method='min')
        for algo in ALGORITHMS:
            rankings.append({'Algorithm': algo, 'Scale': scale, 'Rank': ranks[algo]})

    ranking_df = pd.DataFrame(rankings)

    # 计算平均排名
    avg_ranks = ranking_df.groupby('Algorithm')['Rank'].mean().sort_values()

    # 创建图表
    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(16, 6))

    # 子图1: 平均排名柱状图
    bars = ax1.barh(range(len(avg_ranks)), avg_ranks.values,
                    color=[COLORS[algo] for algo in avg_ranks.index])
    ax1.set_yticks(range(len(avg_ranks)))
    ax1.set_yticklabels(avg_ranks.index, fontsize=12)
    ax1.set_xlabel('Average Rank (lower is better)', fontsize=12)
    ax1.set_title('Algorithm Average Ranking', fontsize=14, fontweight='bold')
    ax1.invert_yaxis()
    ax1.grid(axis='x', alpha=0.3)

    # 标注数值
    for i, (algo, rank) in enumerate(avg_ranks.items()):
        ax1.text(rank, i, f'{rank:.2f}', va='center', ha='left', fontsize=10)

    # 子图2: 排名热力图
    pivot_data = ranking_df.pivot(index='Algorithm', columns='Scale', values='Rank')
    pivot_data = pivot_data.reindex(avg_ranks.index)  # 按平均排名排序

    sns.heatmap(pivot_data, annot=True, fmt='.1f', cmap='RdYlGn_r',
                cbar_kws={'label': 'Rank'}, ax=ax2, vmin=1, vmax=9)
    ax2.set_title('Ranking Heatmap (by Scale)', fontsize=14, fontweight='bold')
    ax2.set_xlabel('Task Scale (M)', fontsize=12)
    ax2.set_ylabel('Algorithm', fontsize=12)

    plt.tight_layout()
    plt.savefig(output_dir / "fig2_algorithm_ranking.png", dpi=300, bbox_inches='tight')
    plt.close()
    print("  [OK] Saved: fig2_algorithm_ranking.png")

def plot_lscbo_vs_cbo(df, output_dir):
    """图3: LSCBO-Fixed vs CBO改进率折线图"""
    print("Generating Figure 3: LSCBO-Fixed vs CBO Improvement...")

    # 计算每个规模上的平均Makespan
    lscbo_data = df[df['Algorithm'] == 'LSCBO-Fixed'].groupby('TaskCount')['InternalMakespan'].mean()
    cbo_data = df[df['Algorithm'] == 'CBO'].groupby('TaskCount')['InternalMakespan'].mean()

    improvement = ((cbo_data - lscbo_data) / cbo_data * 100).values

    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(16, 6))

    # 子图1: Makespan对比
    x = range(len(SCALES))
    ax1.plot(x, lscbo_data.values, marker='o', linewidth=2, markersize=8,
             label='LSCBO-Fixed', color=COLORS['LSCBO-Fixed'])
    ax1.plot(x, cbo_data.values, marker='s', linewidth=2, markersize=8,
             label='CBO', color=COLORS['CBO'])

    ax1.set_xticks(x)
    ax1.set_xticklabels(SCALES)
    ax1.set_xlabel('Task Scale (M)', fontsize=12)
    ax1.set_ylabel('Makespan (s)', fontsize=12)
    ax1.set_title('LSCBO-Fixed vs CBO: Makespan Comparison', fontsize=14, fontweight='bold')
    ax1.legend(fontsize=11)
    ax1.grid(alpha=0.3)

    # 子图2: 改进率
    bars = ax2.bar(x, improvement, color=['green' if imp > 0 else 'red' for imp in improvement])
    ax2.axhline(y=0, color='black', linestyle='-', linewidth=0.8)
    ax2.set_xticks(x)
    ax2.set_xticklabels(SCALES)
    ax2.set_xlabel('Task Scale (M)', fontsize=12)
    ax2.set_ylabel('Improvement (%)', fontsize=12)
    ax2.set_title('LSCBO-Fixed Improvement over CBO', fontsize=14, fontweight='bold')
    ax2.grid(axis='y', alpha=0.3)

    # 标注数值
    for i, imp in enumerate(improvement):
        ax2.text(i, imp, f'{imp:.1f}%', ha='center',
                va='bottom' if imp > 0 else 'top', fontsize=10)

    plt.tight_layout()
    plt.savefig(output_dir / "fig3_lscbo_vs_cbo.png", dpi=300, bbox_inches='tight')
    plt.close()
    print("  [OK] Saved: fig3_lscbo_vs_cbo.png")

def plot_load_balance(df, output_dir):
    """图4: 负载均衡对比图"""
    print("Generating Figure 4: Load Balance Comparison...")

    # 计算每个算法的平均LoadBalanceRatio
    lb_summary = df.groupby('Algorithm')['LoadBalanceRatio'].agg(['mean', 'std']).reset_index()
    lb_summary = lb_summary.sort_values('mean')

    fig, ax = plt.subplots(figsize=(12, 6))

    x = range(len(lb_summary))
    bars = ax.bar(x, lb_summary['mean'],
                  yerr=lb_summary['std'],
                  color=[COLORS[algo] for algo in lb_summary['Algorithm']],
                  capsize=5, alpha=0.8)

    ax.set_xticks(x)
    ax.set_xticklabels(lb_summary['Algorithm'], rotation=45, ha='right', fontsize=11)
    ax.set_ylabel('Load Balance Ratio (lower is better)', fontsize=12)
    ax.set_title('Load Balance Comparison (Mean ± Std)', fontsize=14, fontweight='bold')
    ax.grid(axis='y', alpha=0.3)

    # 标注数值
    for i, row in lb_summary.iterrows():
        ax.text(list(lb_summary.index).index(i), row['mean'],
                f"{row['mean']:.2f}", ha='center', va='bottom', fontsize=9)

    plt.tight_layout()
    plt.savefig(output_dir / "fig4_load_balance.png", dpi=300, bbox_inches='tight')
    plt.close()
    print("  [OK] Saved: fig4_load_balance.png")

def plot_statistical_significance(df, output_dir):
    """图5: 统计显著性热力图（Wilcoxon p-values）"""
    print("Generating Figure 5: Statistical Significance Heatmap...")

    # 合并所有规模的数据
    all_data = {}
    for algo in ALGORITHMS:
        all_data[algo] = df[df['Algorithm'] == algo]['InternalMakespan'].values

    # 计算两两之间的p-value
    n = len(ALGORITHMS)
    pvalue_matrix = np.ones((n, n))

    for i in range(n):
        for j in range(i+1, n):
            algo1 = ALGORITHMS[i]
            algo2 = ALGORITHMS[j]

            # Wilcoxon检验
            try:
                stat, pval = stats.mannwhitneyu(all_data[algo1], all_data[algo2],
                                               alternative='two-sided')
                pvalue_matrix[i, j] = pval
                pvalue_matrix[j, i] = pval
            except:
                pvalue_matrix[i, j] = 1.0
                pvalue_matrix[j, i] = 1.0

    # 创建热力图
    fig, ax = plt.subplots(figsize=(12, 10))

    # 使用-log10(p-value)使显著性更明显
    log_pvalues = -np.log10(pvalue_matrix + 1e-300)  # 避免log(0)
    log_pvalues[log_pvalues > 15] = 15  # 截断极小的p-value

    sns.heatmap(log_pvalues, annot=pvalue_matrix, fmt='.3f',
                xticklabels=ALGORITHMS, yticklabels=ALGORITHMS,
                cmap='RdYlGn', cbar_kws={'label': '-log10(p-value)'},
                ax=ax, vmin=0, vmax=15)

    ax.set_title('Statistical Significance Heatmap (Wilcoxon p-values)\n' +
                'Green = Significant (p<0.05), Red = Not Significant',
                fontsize=14, fontweight='bold')

    # 添加显著性标记
    for i in range(n):
        for j in range(n):
            if i != j and pvalue_matrix[i, j] < 0.05:
                ax.add_patch(plt.Rectangle((j, i), 1, 1, fill=False,
                                          edgecolor='blue', lw=2))

    plt.tight_layout()
    plt.savefig(output_dir / "fig5_statistical_significance.png", dpi=300, bbox_inches='tight')
    plt.close()
    print("  [OK] Saved: fig5_statistical_significance.png")

def main():
    print("=" * 80)
    print("   Q1/Q2 Visualization Generation")
    print("=" * 80)
    print()

    # 创建输出目录
    output_dir = Path("figures/cloudsim")
    output_dir.mkdir(parents=True, exist_ok=True)

    # 加载数据
    print("Loading data...")
    df = load_data()
    print(f"  Data loaded: {len(df)} rows")
    print()

    # 生成图表
    plot_performance_comparison(df, output_dir)
    plot_algorithm_ranking(df, output_dir)
    plot_lscbo_vs_cbo(df, output_dir)
    plot_load_balance(df, output_dir)
    plot_statistical_significance(df, output_dir)

    print()
    print("=" * 80)
    print("   All Figures Generated Successfully!")
    print("=" * 80)
    print(f"Output directory: {output_dir.absolute()}")
    print()
    print("Generated files:")
    for fig_file in sorted(output_dir.glob("fig*.png")):
        print(f"  - {fig_file.name}")

if __name__ == "__main__":
    main()

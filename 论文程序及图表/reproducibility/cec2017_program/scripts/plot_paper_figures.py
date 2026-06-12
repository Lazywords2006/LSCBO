#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
论文图表生成脚本 (Paper Figures Generator)
生成所有用于论文的高质量图表

Generates all publication-quality figures for the ICBO paper:
1. Algorithm Ranking Comparison (Fixed vs Heterogeneous)
2. M=2000 Bar Chart (ICBO Breakthrough)
3. ICBO Series Improvement Rate Line Chart
4. Heterogeneity Impact Comparison
"""

import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
import seaborn as sns
from matplotlib.patches import Rectangle

# 设置matplotlib参数
plt.rcParams['font.size'] = 12
plt.rcParams['figure.dpi'] = 300
plt.rcParams['axes.linewidth'] = 1.5

# 颜色方案（色盲友好）
COLORS = {
    'ICBO-Enhanced': '#2E86AB',  # 蓝色
    'ICBO': '#A23B72',           # 紫色
    'CBO': '#F18F01',            # 橙色
    'PSO': '#C73E1D',            # 红色
    'GWO': '#6A994E',            # 绿色
    'WOA': '#BC4B51',            # 棕红色
    'Random': '#999999'          # 灰色
}

def plot_ranking_comparison():
    """
    Figure 1: 算法排名对比图（固定参数 vs 异构参数）
    横向柱状图，显示7算法在两种参数设置下的平均排名
    """
    print("\n[1/4] Generating Algorithm Ranking Comparison...")

    # 数据来自实验结果
    algorithms = ['Random', 'CBO', 'WOA', 'GWO', 'ICBO', 'PSO', 'ICBO-E']
    fixed_ranks = [7.00, 4.60, 4.20, 4.00, 4.40, 2.20, 1.60]      # 固定参数
    hetero_ranks = [7.00, 4.86, 4.57, 4.29, 2.86, 1.71, 2.71]     # 异构参数

    # 创建图表
    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(14, 6))

    y_pos = np.arange(len(algorithms))

    # 固定参数（左图）
    bars1 = ax1.barh(y_pos, fixed_ranks, color=[COLORS.get(alg, '#666666') for alg in algorithms])
    ax1.set_yticks(y_pos)
    ax1.set_yticklabels(algorithms)
    ax1.set_xlabel('Average Rank (Lower is Better)', fontweight='bold')
    ax1.set_title('Fixed Parameters (5 Scales)', fontsize=14, fontweight='bold')
    ax1.invert_xaxis()  # 左侧显示，排名小的在右边
    ax1.grid(axis='x', alpha=0.3, linestyle='--')

    # 标注数值
    for i, (bar, rank) in enumerate(zip(bars1, fixed_ranks)):
        ax1.text(rank - 0.2, i, f'{rank:.2f}', va='center', ha='right', fontweight='bold')

    # 突出显示ICBO-E排名第1
    ax1.patches[6].set_edgecolor('#FFD700')
    ax1.patches[6].set_linewidth(3)

    # 异构参数（右图）
    bars2 = ax2.barh(y_pos, hetero_ranks, color=[COLORS.get(alg, '#666666') for alg in algorithms])
    ax2.set_yticks(y_pos)
    ax2.set_yticklabels(algorithms)
    ax2.set_xlabel('Average Rank (Lower is Better)', fontweight='bold')
    ax2.set_title('Heterogeneous Parameters (7 Scales)', fontsize=14, fontweight='bold')
    ax2.grid(axis='x', alpha=0.3, linestyle='--')

    # 标注数值
    for i, (bar, rank) in enumerate(zip(bars2, hetero_ranks)):
        ax2.text(rank + 0.2, i, f'{rank:.2f}', va='center', ha='left', fontweight='bold')

    # 突出显示PSO排名第1
    ax2.patches[5].set_edgecolor('#FFD700')
    ax2.patches[5].set_linewidth(3)

    plt.tight_layout()
    output_file = 'results/algorithm_ranking_comparison.png'
    plt.savefig(output_file, dpi=300, bbox_inches='tight')
    print(f"  Saved: {output_file}")
    plt.close()


def plot_m2000_bar_chart():
    """
    Figure 2: M=2000超大规模柱状图
    突出显示ICBO在超大规模下的突破性表现
    """
    print("\n[2/4] Generating M=2000 Bar Chart...")

    # M=2000数据（异构参数实验）
    algorithms = ['Random', 'CBO', 'WOA', 'GWO', 'ICBO-E', 'PSO', 'ICBO']
    makespans = [5800, 3302.74, 3450, 3600, 3307.50, 3496.92, 2800.12]

    # 创建图表
    fig, ax = plt.subplots(figsize=(12, 7))

    # 绘制柱状图
    bars = ax.bar(algorithms, makespans,
                   color=[COLORS.get(alg, '#666666') for alg in algorithms],
                   edgecolor='black', linewidth=1.5)

    # 突出显示ICBO（冠军）
    bars[6].set_edgecolor('#FFD700')
    bars[6].set_linewidth(3)
    bars[6].set_hatch('///')

    # 添加数值标签
    for i, (bar, makespan) in enumerate(zip(bars, makespans)):
        height = bar.get_height()
        ax.text(bar.get_x() + bar.get_width()/2., height + 100,
                f'{makespan:.2f}',
                ha='center', va='bottom', fontweight='bold', fontsize=11)

    # 添加改进率标注（相对于PSO）
    pso_makespan = 3496.92
    icbo_makespan = 2800.12
    improvement = ((pso_makespan - icbo_makespan) / pso_makespan) * 100

    # 绘制对比箭头
    ax.annotate('', xy=(5, icbo_makespan), xytext=(5, pso_makespan),
                arrowprops=dict(arrowstyle='<->', lw=2, color='red'))
    ax.text(5.5, (icbo_makespan + pso_makespan) / 2,
            f'ICBO improves\n{improvement:.1f}%',
            fontsize=12, fontweight='bold', color='red',
            bbox=dict(boxstyle='round,pad=0.5', facecolor='yellow', alpha=0.7))

    # 设置标签
    ax.set_xlabel('Algorithm', fontsize=14, fontweight='bold')
    ax.set_ylabel('Makespan (Lower is Better)', fontsize=14, fontweight='bold')
    ax.set_title('M=2000 Ultra-Large Scale Performance (Heterogeneous Parameters)',
                 fontsize=15, fontweight='bold', pad=20)
    ax.grid(axis='y', alpha=0.3, linestyle='--')

    # 设置y轴范围
    ax.set_ylim(0, 6200)

    plt.tight_layout()
    output_file = 'results/M2000_bar_chart.png'
    plt.savefig(output_file, dpi=300, bbox_inches='tight')
    print(f"  Saved: {output_file}")
    plt.close()


def plot_icbo_improvement_rate():
    """
    Figure 3: ICBO系列改进率折线图
    显示ICBO和ICBO-E相对于CBO的改进率随规模变化
    """
    print("\n[3/4] Generating ICBO Improvement Rate Line Chart...")

    # 数据来自异构参数实验（7规模）
    scales = [50, 100, 200, 300, 500, 1000, 2000]

    # CBO Makespan
    cbo_makespans = [691.28, 971.98, 1178.67, 1499.52, 2006.63, 2726.39, 3302.74]

    # ICBO Makespan
    icbo_makespans = [690.79, 878.88, 1142.32, 1374.25, 1663.77, 2523.84, 2800.12]

    # ICBO-Enhanced Makespan
    icbo_e_makespans = [596.44, 803.44, 974.33, 1184.50, 1848.58, 1972.39, 3307.50]

    # 计算改进率
    icbo_improvements = [((cbo - icbo) / cbo) * 100
                         for cbo, icbo in zip(cbo_makespans, icbo_makespans)]
    icbo_e_improvements = [((cbo - icbo_e) / cbo) * 100
                           for cbo, icbo_e in zip(cbo_makespans, icbo_e_makespans)]

    # 创建图表
    fig, ax = plt.subplots(figsize=(12, 7))

    # 绘制折线图
    line1 = ax.plot(scales, icbo_improvements, marker='o', linewidth=2.5,
                    markersize=10, label='ICBO vs CBO', color=COLORS['ICBO'])
    line2 = ax.plot(scales, icbo_e_improvements, marker='s', linewidth=2.5,
                    markersize=10, label='ICBO-Enhanced vs CBO', color=COLORS['ICBO-Enhanced'])

    # 添加数值标签
    for i, (scale, imp1, imp2) in enumerate(zip(scales, icbo_improvements, icbo_e_improvements)):
        ax.text(scale, imp1 + 1, f'{imp1:.1f}%', ha='center', va='bottom',
                fontweight='bold', fontsize=10, color=COLORS['ICBO'])
        ax.text(scale, imp2 - 1, f'{imp2:.1f}%', ha='center', va='top',
                fontweight='bold', fontsize=10, color=COLORS['ICBO-Enhanced'])

    # 突出显示M=2000的ICBO突破
    ax.plot(2000, icbo_improvements[6], marker='*', markersize=20,
            color='gold', markeredgecolor='red', markeredgewidth=2, zorder=10)
    ax.annotate('ICBO Breakthrough\nat M=2000',
                xy=(2000, icbo_improvements[6]), xytext=(1500, 20),
                arrowprops=dict(arrowstyle='->', lw=2, color='red'),
                fontsize=12, fontweight='bold', color='red',
                bbox=dict(boxstyle='round,pad=0.5', facecolor='yellow', alpha=0.7))

    # 添加0%基准线
    ax.axhline(y=0, color='black', linestyle='--', linewidth=1.5, alpha=0.5)

    # 设置标签
    ax.set_xlabel('Task Scale (M)', fontsize=14, fontweight='bold')
    ax.set_ylabel('Improvement Rate over CBO (%)', fontsize=14, fontweight='bold')
    ax.set_title('ICBO Series Improvement Rate vs CBO (Heterogeneous Parameters)',
                 fontsize=15, fontweight='bold', pad=20)
    ax.set_xscale('log')
    ax.set_xticks(scales)
    ax.set_xticklabels(scales)
    ax.grid(True, alpha=0.3, linestyle='--')
    ax.legend(loc='upper left', fontsize=12, framealpha=0.9)

    plt.tight_layout()
    output_file = 'results/icbo_improvement_rate.png'
    plt.savefig(output_file, dpi=300, bbox_inches='tight')
    print(f"  Saved: {output_file}")
    plt.close()


def plot_heterogeneity_impact():
    """
    Figure 4: 异构度影响对比图
    显示算法在固定参数 vs 异构参数下的排名变化
    """
    print("\n[4/4] Generating Heterogeneity Impact Comparison...")

    # 数据
    algorithms = ['PSO', 'ICBO-E', 'GWO', 'WOA', 'ICBO', 'CBO', 'Random']
    fixed_ranks = [2.20, 1.60, 4.00, 4.20, 4.40, 4.60, 7.00]
    hetero_ranks = [1.71, 2.71, 4.29, 4.57, 2.86, 4.86, 7.00]

    # 创建图表
    fig, ax = plt.subplots(figsize=(12, 8))

    x_pos = [0, 1]  # 固定参数、异构参数
    width = 0.8 / len(algorithms)  # 柱宽

    # 绘制分组柱状图
    for i, (alg, fixed, hetero) in enumerate(zip(algorithms, fixed_ranks, hetero_ranks)):
        x = [p + i * width for p in x_pos]
        bars = ax.bar(x, [fixed, hetero], width,
                      label=alg, color=COLORS.get(alg, '#666666'),
                      edgecolor='black', linewidth=1)

        # 绘制箭头显示排名变化
        change = hetero - fixed
        if abs(change) > 0.1:  # 只显示显著变化
            mid_x = x_pos[0] + (x_pos[1] - x_pos[0]) / 2 + i * width
            if change < 0:  # 排名提升（数值降低）
                arrow_color = 'green'
                arrow_style = '->'
                text_y = min(fixed, hetero) - 0.3
            else:  # 排名下降（数值升高）
                arrow_color = 'red'
                arrow_style = '->'
                text_y = max(fixed, hetero) + 0.3

            # 绘制箭头
            ax.annotate('', xy=(x[1], hetero), xytext=(x[0], fixed),
                       arrowprops=dict(arrowstyle=arrow_style, lw=1.5,
                                     color=arrow_color, alpha=0.7))

            # 添加变化值标签
            ax.text(mid_x, text_y, f'{change:+.2f}',
                   ha='center', va='center', fontsize=9,
                   fontweight='bold', color=arrow_color,
                   bbox=dict(boxstyle='round,pad=0.3',
                           facecolor='white', edgecolor=arrow_color, alpha=0.8))

    # 设置标签
    ax.set_ylabel('Average Rank (Lower is Better)', fontsize=14, fontweight='bold')
    ax.set_title('Algorithm Performance: Fixed vs Heterogeneous Parameters',
                 fontsize=15, fontweight='bold', pad=20)
    ax.set_xticks([p + width * len(algorithms) / 2 for p in x_pos])
    ax.set_xticklabels(['Fixed Parameters\n(5 Scales)', 'Heterogeneous Parameters\n(7 Scales)'],
                       fontsize=12, fontweight='bold')
    ax.legend(loc='upper right', fontsize=11, ncol=2, framealpha=0.9)
    ax.grid(axis='y', alpha=0.3, linestyle='--')
    ax.set_ylim(0, 8)

    # 添加注释框
    textstr = 'Key Findings:\n' \
              '- PSO: 2.20 -> 1.71 (Improved)\n' \
              '- ICBO-E: 1.60 -> 2.71 (Degraded)\n' \
              '- ICBO: 4.40 -> 2.86 (Improved)'
    props = dict(boxstyle='round', facecolor='wheat', alpha=0.8)
    ax.text(0.02, 0.98, textstr, transform=ax.transAxes, fontsize=11,
            verticalalignment='top', bbox=props)

    plt.tight_layout()
    output_file = 'results/heterogeneity_impact.png'
    plt.savefig(output_file, dpi=300, bbox_inches='tight')
    print(f"  Saved: {output_file}")
    plt.close()


def main():
    print("=" * 70)
    print("Paper Figures Generator".center(70))
    print("=" * 70)
    print("\nGenerating 4 publication-quality figures for ICBO paper...")

    # 生成4个图表
    plot_ranking_comparison()
    plot_m2000_bar_chart()
    plot_icbo_improvement_rate()
    plot_heterogeneity_impact()

    print("\n" + "=" * 70)
    print("All Figures Generated Successfully!".center(70))
    print("=" * 70)
    print("\nOutput files:")
    print("  1. results/algorithm_ranking_comparison.png")
    print("  2. results/M2000_bar_chart.png")
    print("  3. results/icbo_improvement_rate.png")
    print("  4. results/heterogeneity_impact.png")
    print("\nAll figures saved at 300 DPI (publication quality)")


if __name__ == '__main__':
    main()

#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Q4期刊投稿图表生成器
生成Cluster Computing期刊所需的高质量图表（300 DPI）

生成的图表：
1. 5算法Makespan对比柱状图（M=100）
2. 5算法收敛曲线对比（M=100, Seed=42）
3. 多目标优化对比（4规模）
4. Load Balance Ratio对比
"""

import pandas as pd
import matplotlib.pyplot as plt
import numpy as np
from pathlib import Path
import seaborn as sns

# 设置绘图风格
plt.style.use('seaborn-v0_8-whitegrid')
sns.set_palette("husl")

# 配置matplotlib
plt.rcParams['figure.dpi'] = 300
plt.rcParams['savefig.dpi'] = 300
plt.rcParams['font.family'] = 'DejaVu Sans'
plt.rcParams['font.size'] = 10
plt.rcParams['axes.labelsize'] = 11
plt.rcParams['axes.titlesize'] = 12
plt.rcParams['xtick.labelsize'] = 9
plt.rcParams['ytick.labelsize'] = 9
plt.rcParams['legend.fontsize'] = 9

# 文件路径配置
BASE_DIR = Path(__file__).parent.parent
RESULTS_DIR = BASE_DIR / "results"
OUTPUT_DIR = BASE_DIR / "paper_figures" / "q4_submission"
OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

def figure1_five_algorithm_bar_chart():
    """
    图1: 5算法Makespan对比柱状图（M=100）

    突出显示LSCBO-Fixed在M=100的优势（+40.48%）
    """
    print("\n生成图1: 5算法Makespan对比柱状图（M=100）...")

    # 读取数据
    df = pd.read_csv(RESULTS_DIR / "five_algorithm_comparison_20251214_113909.csv")

    # 筛选M=100的数据
    df_m100 = df[df['TaskCount'] == 100]

    # 计算每个算法的平均Makespan
    avg_makespan = df_m100.groupby('Algorithm')['Makespan'].mean().sort_values()

    # 算法颜色映射（LSCBO-Fixed使用特殊颜色）
    colors = []
    for algo in avg_makespan.index:
        if algo == 'LSCBO-Fixed':
            colors.append('#d62728')  # 红色，突出显示
        else:
            colors.append('#1f77b4')  # 蓝色

    # 创建图表
    fig, ax = plt.subplots(figsize=(10, 6))

    bars = ax.bar(range(len(avg_makespan)), avg_makespan.values, color=colors, alpha=0.8)

    # 设置x轴标签
    ax.set_xticks(range(len(avg_makespan)))
    ax.set_xticklabels(avg_makespan.index, rotation=0, ha='center')

    # 设置标签和标题
    ax.set_xlabel('Algorithm', fontsize=11, fontweight='bold')
    ax.set_ylabel('Average Makespan (seconds)', fontsize=11, fontweight='bold')
    ax.set_title('Five-Algorithm Makespan Comparison (M=100 tasks)',
                 fontsize=12, fontweight='bold', pad=15)

    # 在柱状图上方添加数值
    for i, (algo, value) in enumerate(avg_makespan.items()):
        ax.text(i, value, f'{value:.2e}',
                ha='center', va='bottom', fontsize=8)

    # 添加网格线
    ax.grid(axis='y', alpha=0.3, linestyle='--')
    ax.set_axisbelow(True)

    # 添加CBO基线对比
    cbo_makespan = avg_makespan['CBO']
    lscbo_makespan = avg_makespan['LSCBO-Fixed']
    improvement = (cbo_makespan - lscbo_makespan) / cbo_makespan * 100

    # 在图表上添加改进率标注
    ax.text(0.02, 0.98, f'LSCBO-Fixed vs CBO: +{improvement:.2f}% improvement',
            transform=ax.transAxes, fontsize=10, fontweight='bold',
            verticalalignment='top',
            bbox=dict(boxstyle='round', facecolor='wheat', alpha=0.5))

    plt.tight_layout()

    # 保存图表
    output_file = OUTPUT_DIR / "figure1_five_algorithm_makespan_m100.png"
    plt.savefig(output_file, dpi=300, bbox_inches='tight')
    print(f"  保存至: {output_file}")
    print(f"  文件大小: {output_file.stat().st_size / 1024:.1f} KB")

    plt.close()

    return avg_makespan

def figure2_convergence_curves():
    """
    图2: 5算法收敛曲线对比（M=100, Seed=42）

    展示LSCBO-Fixed的快速收敛能力
    """
    print("\n生成图2: 5算法收敛曲线对比（M=100, Seed=42）...")

    algorithms = ['CBO', 'LSCBO-Fixed', 'HHO', 'AOA', 'GTO']
    colors = ['#1f77b4', '#d62728', '#2ca02c', '#ff7f0e', '#9467bd']
    linestyles = ['-', '-', '--', '-.', ':']

    fig, ax = plt.subplots(figsize=(10, 6))

    # 读取每个算法的收敛曲线数据
    found_any = False
    for algo, color, linestyle in zip(algorithms, colors, linestyles):
        try:
            # 尝试读取收敛曲线文件
            if algo == 'LSCBO-Fixed':
                conv_file = RESULTS_DIR / f"convergence_LSCBO_M100_seed42.csv"
            else:
                conv_file = RESULTS_DIR / f"convergence_{algo}_M100_seed42.csv"

            if conv_file.exists():
                df_conv = pd.read_csv(conv_file)
                iterations = df_conv['Iteration'].values
                fitness = df_conv['BestFitness'].values

                ax.plot(iterations, fitness, label=algo,
                       color=color, linestyle=linestyle, linewidth=2, alpha=0.8)
                found_any = True
            else:
                print(f"  警告: 未找到 {conv_file}")
        except Exception as e:
            print(f"  警告: 读取{algo}收敛曲线失败: {e}")

    if not found_any:
        print("  警告: 未找到任何收敛曲线数据，跳过此图表")
        plt.close()
        return

    # 设置标签和标题
    ax.set_xlabel('Iteration', fontsize=11, fontweight='bold')
    ax.set_ylabel('Best Fitness (Makespan)', fontsize=11, fontweight='bold')
    ax.set_title('Convergence Curves Comparison (M=100, Seed=42)',
                 fontsize=12, fontweight='bold', pad=15)

    # 设置对数刻度（如果数据范围较大）
    # ax.set_yscale('log')

    # 添加图例
    ax.legend(loc='best', frameon=True, shadow=True)

    # 添加网格
    ax.grid(alpha=0.3, linestyle='--')

    plt.tight_layout()

    # 保存图表
    output_file = OUTPUT_DIR / "figure2_convergence_curves_m100.png"
    plt.savefig(output_file, dpi=300, bbox_inches='tight')
    print(f"  保存至: {output_file}")
    print(f"  文件大小: {output_file.stat().st_size / 1024:.1f} KB")

    plt.close()

def figure3_multi_objective_comparison():
    """
    图3: 多目标优化对比（4规模）

    展示单目标 vs 多目标的Makespan对比，突出M=1000的-3.88%改进
    """
    print("\n生成图3: 多目标优化对比（4规模）...")

    # 读取单目标和多目标数据
    df_single = pd.read_csv(RESULTS_DIR / "multi_objective_scalability_part1_SingleObjective.csv")
    df_multi = pd.read_csv(RESULTS_DIR / "multi_objective_scalability_part2_MultiObjective.csv")

    # 计算每个规模的平均Makespan
    scales = [100, 500, 1000, 2000]
    single_makespans = []
    multi_makespans = []

    for scale in scales:
        single_avg = df_single[df_single['Scale'] == scale]['Makespan'].mean()
        multi_avg = df_multi[df_multi['Scale'] == scale]['Makespan'].mean()
        single_makespans.append(single_avg)
        multi_makespans.append(multi_avg)

    # 计算改进率
    improvements = [(s - m) / s * 100 for s, m in zip(single_makespans, multi_makespans)]

    # 创建图表
    fig, ax = plt.subplots(figsize=(12, 6))

    x = np.arange(len(scales))
    width = 0.35

    bars1 = ax.bar(x - width/2, single_makespans, width, label='Single Objective',
                   color='#1f77b4', alpha=0.8)
    bars2 = ax.bar(x + width/2, multi_makespans, width, label='Multi Objective',
                   color='#2ca02c', alpha=0.8)

    # 设置x轴标签
    ax.set_xticks(x)
    ax.set_xticklabels([f'M={s}' for s in scales])

    # 设置标签和标题
    ax.set_xlabel('Task Scale', fontsize=11, fontweight='bold')
    ax.set_ylabel('Average Makespan (seconds)', fontsize=11, fontweight='bold')
    ax.set_title('Single-Objective vs Multi-Objective Optimization Comparison',
                 fontsize=12, fontweight='bold', pad=15)

    # 添加数值标签
    for i, (s, m, imp) in enumerate(zip(single_makespans, multi_makespans, improvements)):
        # 在柱状图上方添加数值
        ax.text(i - width/2, s, f'{s:.1f}', ha='center', va='bottom', fontsize=8)
        ax.text(i + width/2, m, f'{m:.1f}', ha='center', va='bottom', fontsize=8)

        # 在两个柱状图之间添加改进率
        if imp > 0:
            ax.text(i, max(s, m) * 1.05, f'{imp:+.2f}%',
                   ha='center', va='bottom', fontsize=9, fontweight='bold',
                   color='green')
        else:
            ax.text(i, max(s, m) * 1.05, f'{imp:+.2f}%',
                   ha='center', va='bottom', fontsize=9, fontweight='bold',
                   color='red')

    # 添加图例
    ax.legend(loc='upper left', frameon=True, shadow=True)

    # 添加网格
    ax.grid(axis='y', alpha=0.3, linestyle='--')
    ax.set_axisbelow(True)

    # 突出M=1000的结果
    best_idx = improvements.index(max([imp for imp in improvements if imp > 0]))
    ax.add_patch(plt.Rectangle((best_idx - 0.5, 0), 1, ax.get_ylim()[1],
                               facecolor='yellow', alpha=0.1, zorder=0))
    ax.text(best_idx, ax.get_ylim()[1] * 0.95, 'Best improvement',
           ha='center', fontsize=9, fontweight='bold',
           bbox=dict(boxstyle='round', facecolor='wheat', alpha=0.5))

    plt.tight_layout()

    # 保存图表
    output_file = OUTPUT_DIR / "figure3_multi_objective_comparison.png"
    plt.savefig(output_file, dpi=300, bbox_inches='tight')
    print(f"  保存至: {output_file}")
    print(f"  文件大小: {output_file.stat().st_size / 1024:.1f} KB")

    plt.close()

    # 输出改进率统计
    print(f"\n多目标优化改进率：")
    for scale, imp in zip(scales, improvements):
        status = "[OK]" if imp > 0 else "[--]"
        print(f"  M={scale:4d}: {imp:+.2f}% {status}")
    print(f"  平均改进率: {np.mean(improvements):+.2f}%")

def figure4_load_balance_comparison():
    """
    图4: Load Balance Ratio对比（M=100）

    展示5算法的负载均衡性能
    """
    print("\n生成图4: Load Balance Ratio对比（M=100）...")

    # 读取数据
    df = pd.read_csv(RESULTS_DIR / "five_algorithm_comparison_20251214_113909.csv")

    # 筛选M=100的数据
    df_m100 = df[df['TaskCount'] == 100]

    # 计算每个算法的平均Load Balance Ratio
    avg_lbr = df_m100.groupby('Algorithm')['LoadBalanceRatio'].mean().sort_values()

    # 算法颜色映射
    colors = ['#2ca02c' if lbr < 1.0 else '#ff7f0e' for lbr in avg_lbr.values]

    # 创建图表
    fig, ax = plt.subplots(figsize=(10, 6))

    bars = ax.bar(range(len(avg_lbr)), avg_lbr.values, color=colors, alpha=0.8)

    # 设置x轴标签
    ax.set_xticks(range(len(avg_lbr)))
    ax.set_xticklabels(avg_lbr.index, rotation=0, ha='center')

    # 设置标签和标题
    ax.set_xlabel('Algorithm', fontsize=11, fontweight='bold')
    ax.set_ylabel('Average Load Balance Ratio (Lower is Better)',
                  fontsize=11, fontweight='bold')
    ax.set_title('Load Balance Ratio Comparison (M=100 tasks)',
                 fontsize=12, fontweight='bold', pad=15)

    # 在柱状图上方添加数值
    for i, (algo, value) in enumerate(avg_lbr.items()):
        ax.text(i, value, f'{value:.4f}',
                ha='center', va='bottom', fontsize=8)

    # 添加理想线（LBR = 1.0）
    ax.axhline(y=1.0, color='r', linestyle='--', linewidth=1, alpha=0.5, label='Ideal Balance')

    # 添加图例
    ax.legend(loc='upper right', frameon=True, shadow=True)

    # 添加网格
    ax.grid(axis='y', alpha=0.3, linestyle='--')
    ax.set_axisbelow(True)

    plt.tight_layout()

    # 保存图表
    output_file = OUTPUT_DIR / "figure4_load_balance_ratio_m100.png"
    plt.savefig(output_file, dpi=300, bbox_inches='tight')
    print(f"  保存至: {output_file}")
    print(f"  文件大小: {output_file.stat().st_size / 1024:.1f} KB")

    plt.close()

def generate_summary_report():
    """
    生成图表汇总报告
    """
    print("\n" + "=" * 80)
    print("Q4期刊投稿图表生成完成！")
    print("=" * 80)

    # 统计生成的图表
    figures = list(OUTPUT_DIR.glob("*.png"))
    total_size = sum(f.stat().st_size for f in figures) / 1024

    print(f"\n生成的图表数量: {len(figures)}")
    print(f"总文件大小: {total_size:.1f} KB")
    print(f"\n图表列表:")
    for i, fig in enumerate(sorted(figures), 1):
        size_kb = fig.stat().st_size / 1024
        print(f"  {i}. {fig.name} ({size_kb:.1f} KB)")

    print(f"\n所有图表保存在: {OUTPUT_DIR}")
    print("\n使用建议:")
    print("  - 所有图表均为300 DPI高质量PNG格式")
    print("  - 适合Cluster Computing期刊投稿要求")
    print("  - 可直接插入LaTeX文档")
    print("  - 建议在论文中按编号顺序使用")

    return len(figures)

def main():
    """
    主函数：生成所有Q4投稿图表
    """
    print("=" * 80)
    print("Q4期刊投稿图表生成器")
    print("目标期刊: Cluster Computing (IF ~5.0)")
    print("=" * 80)

    try:
        # 生成图表
        figure1_five_algorithm_bar_chart()
        figure2_convergence_curves()
        figure3_multi_objective_comparison()
        figure4_load_balance_comparison()

        # 生成汇总报告
        figure_count = generate_summary_report()

        print(f"\n[OK] 成功生成 {figure_count} 张图表！")

    except Exception as e:
        print(f"\n[ERROR] 图表生成失败: {e}")
        import traceback
        traceback.print_exc()
        return 1

    return 0

if __name__ == "__main__":
    exit(main())

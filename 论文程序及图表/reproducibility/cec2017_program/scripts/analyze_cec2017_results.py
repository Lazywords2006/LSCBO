#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
CEC2017基准测试结果综合分析

功能：
1. 读取CEC2017统计数据
2. 计算算法排名
3. Wilcoxon符号秩检验
4. 生成综合分析报告
"""

import pandas as pd
import numpy as np
from scipy import stats
from pathlib import Path

# 算法列表
ALGORITHMS = ["Random", "PSO", "GWO", "WOA", "CBO", "LSCBO-Fixed"]

def load_cec2017_data():
    """加载CEC2017统计数据"""
    csv_file = "CEC2017_SixAlgorithm_FullExperiment_20251214_161023_Statistics.csv"
    df = pd.read_csv(csv_file)
    return df

def calculate_rankings(df):
    """计算每个函数上的算法排名"""
    functions = df['Function'].unique()
    rankings = []

    for func in functions:
        func_data = df[df['Function'] == func]
        # 按平均适应度排名（越小越好）
        func_data_sorted = func_data.sort_values('AvgFitness')

        for rank, (idx, row) in enumerate(func_data_sorted.iterrows(), 1):
            rankings.append({
                'Function': func,
                'Algorithm': row['Algorithm'],
                'Rank': rank,
                'AvgFitness': row['AvgFitness']
            })

    return pd.DataFrame(rankings)

def calculate_average_rankings(ranking_df):
    """计算算法的平均排名"""
    avg_ranks = ranking_df.groupby('Algorithm')['Rank'].mean().sort_values()
    return avg_ranks

def wilcoxon_test(df, algo1, algo2):
    """对两个算法进行Wilcoxon符号秩检验"""
    functions = df['Function'].unique()

    algo1_scores = []
    algo2_scores = []

    for func in functions:
        func_data = df[df['Function'] == func]
        score1 = func_data[func_data['Algorithm'] == algo1]['AvgFitness'].values[0]
        score2 = func_data[func_data['Algorithm'] == algo2]['AvgFitness'].values[0]
        algo1_scores.append(score1)
        algo2_scores.append(score2)

    # Wilcoxon符号秩检验
    try:
        statistic, pvalue = stats.wilcoxon(algo1_scores, algo2_scores, alternative='two-sided')
        return pvalue
    except:
        return 1.0

def generate_report():
    """生成综合分析报告"""
    print("=" * 80)
    print("   CEC2017 Benchmark Test - Comprehensive Analysis")
    print("=" * 80)
    print()

    # 加载数据
    print("Loading data...")
    df = load_cec2017_data()
    print(f"  Loaded: {len(df)} records")
    print(f"  Functions: {df['Function'].nunique()}")
    print(f"  Algorithms: {df['Algorithm'].nunique()}")
    print()

    # 计算排名
    print("Calculating rankings...")
    ranking_df = calculate_rankings(df)
    avg_ranks = calculate_average_rankings(ranking_df)
    print()

    # 显示平均排名
    print("=" * 80)
    print("   Algorithm Average Rankings (lower is better)")
    print("=" * 80)
    for rank, (algo, avg_rank) in enumerate(avg_ranks.items(), 1):
        medal = "[1]" if rank == 1 else "[2]" if rank == 2 else "[3]" if rank == 3 else "   "
        print(f"{medal} {rank}. {algo:<15} Average Rank: {avg_rank:.4f}")
    print()

    # Wilcoxon检验
    print("=" * 80)
    print("   Wilcoxon Signed-Rank Test (p-values)")
    print("=" * 80)
    print()

    # 重点对比：LSCBO-Fixed vs 其他算法
    print("LSCBO-Fixed vs Other Algorithms:")
    print()

    for algo in ALGORITHMS:
        if algo == "LSCBO-Fixed":
            continue
        pvalue = wilcoxon_test(df, "LSCBO-Fixed", algo)
        significance = "***" if pvalue < 0.001 else "**" if pvalue < 0.01 else "*" if pvalue < 0.05 else "ns"
        print(f"  LSCBO-Fixed vs {algo:<15} p-value: {pvalue:.4e} {significance}")
    print()

    # CBO vs 其他算法
    print("CBO vs Other Algorithms:")
    print()

    for algo in ALGORITHMS:
        if algo == "CBO":
            continue
        pvalue = wilcoxon_test(df, "CBO", algo)
        significance = "***" if pvalue < 0.001 else "**" if pvalue < 0.01 else "*" if pvalue < 0.05 else "ns"
        print(f"  CBO vs {algo:<15} p-value: {pvalue:.4e} {significance}")
    print()

    # 按函数类型统计
    print("=" * 80)
    print("   Performance by Function Type")
    print("=" * 80)
    print()

    # 统计每个算法获得第1名的次数
    first_place_counts = ranking_df[ranking_df['Rank'] == 1]['Algorithm'].value_counts()
    print("Number of 1st Place Finishes:")
    for algo in ALGORITHMS:
        count = first_place_counts.get(algo, 0)
        print(f"  {algo:<15} {count:>2} times")
    print()

    # 统计每个算法获得前3名的次数
    top3_counts = ranking_df[ranking_df['Rank'] <= 3]['Algorithm'].value_counts()
    print("Number of Top-3 Finishes:")
    for algo in ALGORITHMS:
        count = top3_counts.get(algo, 0)
        print(f"  {algo:<15} {count:>2} times")
    print()

    # 保存详细排名数据
    output_file = "CEC2017_Detailed_Rankings.csv"
    ranking_df.to_csv(output_file, index=False)
    print(f"Detailed rankings saved to: {output_file}")
    print()

    # 生成Markdown报告
    generate_markdown_report(df, ranking_df, avg_ranks)

    print("=" * 80)
    print("   Analysis Complete!")
    print("=" * 80)

def generate_markdown_report(df, ranking_df, avg_ranks):
    """生成Markdown格式的报告"""
    output_file = "CEC2017_Comprehensive_Analysis_Report.md"

    with open(output_file, 'w', encoding='utf-8') as f:
        f.write("# CEC2017 Benchmark Test - Comprehensive Analysis Report\n\n")
        f.write("**Generated**: 2025-12-16\n\n")
        f.write("**Experiment Configuration**:\n")
        f.write("- Algorithms: 6 (Random, PSO, GWO, WOA, CBO, LSCBO-Fixed)\n")
        f.write("- Functions: 30 (CEC2017 benchmark suite)\n")
        f.write("- Runs per function: 30\n")
        f.write("- Total tests: 5,400\n\n")

        f.write("---\n\n")

        # 算法排名
        f.write("## 1. Algorithm Average Rankings\n\n")
        f.write("| Rank | Algorithm | Average Rank | Interpretation |\n")
        f.write("|------|-----------|--------------|----------------|\n")

        for rank, (algo, avg_rank) in enumerate(avg_ranks.items(), 1):
            medal = "🥇" if rank == 1 else "🥈" if rank == 2 else "🥉" if rank == 3 else ""
            interp = "Best" if rank == 1 else "Excellent" if rank == 2 else "Good" if rank == 3 else "Average" if rank <= 4 else "Poor"
            f.write(f"| {medal} {rank} | **{algo}** | **{avg_rank:.4f}** | {interp} |\n")

        f.write("\n")

        # Wilcoxon检验结果
        f.write("## 2. Statistical Significance (Wilcoxon Test)\n\n")
        f.write("### LSCBO-Fixed vs Other Algorithms\n\n")
        f.write("| Comparison | p-value | Significance |\n")
        f.write("|------------|---------|-------------|\n")

        for algo in ALGORITHMS:
            if algo == "LSCBO-Fixed":
                continue
            pvalue = wilcoxon_test(df, "LSCBO-Fixed", algo)
            significance = "***" if pvalue < 0.001 else "**" if pvalue < 0.01 else "*" if pvalue < 0.05 else "ns"
            f.write(f"| LSCBO-Fixed vs {algo} | {pvalue:.4e} | {significance} |\n")

        f.write("\n### CBO vs Other Algorithms\n\n")
        f.write("| Comparison | p-value | Significance |\n")
        f.write("|------------|---------|-------------|\n")

        for algo in ALGORITHMS:
            if algo == "CBO":
                continue
            pvalue = wilcoxon_test(df, "CBO", algo)
            significance = "***" if pvalue < 0.001 else "**" if pvalue < 0.01 else "*" if pvalue < 0.05 else "ns"
            f.write(f"| CBO vs {algo} | {pvalue:.4e} | {significance} |\n")

        f.write("\n")

        # 获胜统计
        f.write("## 3. Win Statistics\n\n")

        first_place_counts = ranking_df[ranking_df['Rank'] == 1]['Algorithm'].value_counts()
        f.write("### Number of 1st Place Finishes (out of 30 functions)\n\n")
        f.write("| Algorithm | Count | Percentage |\n")
        f.write("|-----------|-------|------------|\n")

        for algo in ALGORITHMS:
            count = first_place_counts.get(algo, 0)
            percentage = (count / 30) * 100
            f.write(f"| {algo} | {count} | {percentage:.1f}% |\n")

        f.write("\n")

        top3_counts = ranking_df[ranking_df['Rank'] <= 3]['Algorithm'].value_counts()
        f.write("### Number of Top-3 Finishes (out of 30 functions)\n\n")
        f.write("| Algorithm | Count | Percentage |\n")
        f.write("|-----------|-------|------------|\n")

        for algo in ALGORITHMS:
            count = top3_counts.get(algo, 0)
            percentage = (count / 30) * 100
            f.write(f"| {algo} | {count} | {percentage:.1f}% |\n")

        f.write("\n")

        # 关键发现
        f.write("## 4. Key Findings\n\n")
        f.write("### Algorithm Performance Summary\n\n")

        best_algo = avg_ranks.index[0]
        second_algo = avg_ranks.index[1]
        worst_algo = avg_ranks.index[-1]

        f.write(f"1. **Best Algorithm**: {best_algo} (Average Rank: {avg_ranks[best_algo]:.4f})\n")
        f.write(f"2. **Second Best**: {second_algo} (Average Rank: {avg_ranks[second_algo]:.4f})\n")
        f.write(f"3. **Baseline**: {worst_algo} (Average Rank: {avg_ranks[worst_algo]:.4f})\n\n")

        # LSCBO-Fixed vs CBO对比
        lscbo_rank = avg_ranks["LSCBO-Fixed"]
        cbo_rank = avg_ranks["CBO"]
        lscbo_wins = first_place_counts.get("LSCBO-Fixed", 0)
        cbo_wins = first_place_counts.get("CBO", 0)

        f.write("### LSCBO-Fixed vs CBO Comparison\n\n")
        f.write(f"- **Average Rank**: LSCBO-Fixed ({lscbo_rank:.4f}) vs CBO ({cbo_rank:.4f})\n")
        f.write(f"- **1st Place Wins**: LSCBO-Fixed ({lscbo_wins}) vs CBO ({cbo_wins})\n")

        pvalue_lscbo_cbo = wilcoxon_test(df, "LSCBO-Fixed", "CBO")
        f.write(f"- **Wilcoxon p-value**: {pvalue_lscbo_cbo:.4e}\n")

        if lscbo_rank < cbo_rank:
            improvement = ((cbo_rank - lscbo_rank) / cbo_rank) * 100
            f.write(f"- **Conclusion**: LSCBO-Fixed outperforms CBO by {improvement:.2f}% in average ranking\n")
        else:
            f.write(f"- **Conclusion**: CBO outperforms LSCBO-Fixed\n")

        f.write("\n---\n\n")
        f.write("**Report End**\n")

    print(f"Markdown report saved to: {output_file}")

if __name__ == "__main__":
    generate_report()

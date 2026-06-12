#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
CEC2017鲁棒性测试统计分析脚本
分析3个算法（LSCBO, CBO, PSO）在6个函数上的鲁棒性表现
"""

import pandas as pd
import numpy as np
from pathlib import Path

def load_data(csv_file):
    """加载CSV数据"""
    df = pd.read_csv(csv_file)
    return df

def calculate_statistics(df):
    """计算每个算法×函数组合的统计数据"""
    stats = df.groupby(['Algorithm', 'Function'])['BestFitness'].agg([
        ('Mean', 'mean'),
        ('Std', 'std'),
        ('Min', 'min'),
        ('Max', 'max'),
        ('Median', 'median'),
        ('CV', lambda x: np.std(x) / np.mean(x) if np.mean(x) != 0 else 0)  # 变异系数
    ]).reset_index()

    return stats

def identify_outliers(df):
    """识别异常值（使用IQR方法）"""
    outliers = []

    for algo in df['Algorithm'].unique():
        for func in df['Function'].unique():
            subset = df[(df['Algorithm'] == algo) & (df['Function'] == func)]
            values = subset['BestFitness'].values

            Q1 = np.percentile(values, 25)
            Q3 = np.percentile(values, 75)
            IQR = Q3 - Q1

            lower_bound = Q1 - 1.5 * IQR
            upper_bound = Q3 + 1.5 * IQR

            for idx, row in subset.iterrows():
                if row['BestFitness'] < lower_bound or row['BestFitness'] > upper_bound:
                    outliers.append({
                        'Algorithm': algo,
                        'Function': func,
                        'Run': row['Run'],
                        'Seed': row['Seed'],
                        'Value': row['BestFitness'],
                        'Q1': Q1,
                        'Q3': Q3,
                        'Lower': lower_bound,
                        'Upper': upper_bound
                    })

    return pd.DataFrame(outliers)

def rank_algorithms(stats):
    """对每个函数的算法进行排名（基于均值）"""
    rankings = []

    for func in stats['Function'].unique():
        func_data = stats[stats['Function'] == func].copy()
        func_data['Rank'] = func_data['Mean'].rank()
        rankings.append(func_data)

    return pd.concat(rankings)

def generate_latex_table(stats):
    """生成LaTeX格式的对比表格"""
    latex_lines = []
    latex_lines.append("\\begin{table}[htbp]")
    latex_lines.append("\\centering")
    latex_lines.append("\\caption{CEC2017鲁棒性测试结果（均值±标准差）}")
    latex_lines.append("\\label{tab:robustness}")
    latex_lines.append("\\begin{tabular}{l|ccc}")
    latex_lines.append("\\hline")
    latex_lines.append("\\textbf{函数} & \\textbf{LSCBO} & \\textbf{CBO} & \\textbf{PSO} \\\\")
    latex_lines.append("\\hline")

    functions = ['Sphere', 'Rastrigin', 'Ackley', 'Rosenbrock', 'Griewank', 'Schwefel']
    algorithms = ['LSCBO', 'CBO', 'PSO']

    for func in functions:
        row = [func]
        for algo in algorithms:
            data = stats[(stats['Function'] == func) & (stats['Algorithm'] == algo)]
            if not data.empty:
                mean_val = data['Mean'].values[0]
                std_val = data['Std'].values[0]

                # 使用科学计数法格式化
                if abs(mean_val) < 0.01 or abs(mean_val) > 1000:
                    cell = f"${mean_val:.2e} \\pm {std_val:.2e}$"
                else:
                    cell = f"${mean_val:.2f} \\pm {std_val:.2f}$"

                row.append(cell)
            else:
                row.append("-")

        latex_lines.append(" & ".join(row) + " \\\\")

    latex_lines.append("\\hline")
    latex_lines.append("\\end{tabular}")
    latex_lines.append("\\end{table}")

    return "\n".join(latex_lines)

def main():
    # 设置路径
    results_dir = Path(__file__).parent.parent / 'results'
    csv_file = results_dir / 'cec2017_robustness_20251217_115155.csv'

    print("=" * 80)
    print("CEC2017鲁棒性测试统计分析")
    print("=" * 80)

    # 加载数据
    print(f"\n📂 加载数据: {csv_file.name}")
    df = load_data(csv_file)
    print(f"   数据行数: {len(df)}")
    print(f"   算法数量: {df['Algorithm'].nunique()}")
    print(f"   函数数量: {df['Function'].nunique()}")

    # 计算统计数据
    print("\n📊 计算统计数据...")
    stats = calculate_statistics(df)

    # 排名
    print("\n🏆 算法排名（基于均值）...")
    ranked_stats = rank_algorithms(stats)

    # 识别异常值
    print("\n⚠️  识别异常值...")
    outliers = identify_outliers(df)
    print(f"   发现 {len(outliers)} 个异常值")

    # 保存统计结果
    output_stats = results_dir / 'robustness_statistics.csv'
    ranked_stats.to_csv(output_stats, index=False)
    print(f"\n💾 统计结果已保存: {output_stats.name}")

    # 保存异常值
    if not outliers.empty:
        output_outliers = results_dir / 'robustness_outliers.csv'
        outliers.to_csv(output_outliers, index=False)
        print(f"💾 异常值已保存: {output_outliers.name}")

    # 生成LaTeX表格
    latex_table = generate_latex_table(stats)
    output_latex = results_dir / 'robustness_table.tex'
    with open(output_latex, 'w', encoding='utf-8') as f:
        f.write(latex_table)
    print(f"💾 LaTeX表格已保存: {output_latex.name}")

    # 打印关键统计结果
    print("\n" + "=" * 80)
    print("关键统计结果")
    print("=" * 80)

    for func in ['Sphere', 'Rastrigin', 'Ackley', 'Rosenbrock', 'Griewank', 'Schwefel']:
        print(f"\n【{func}】")
        func_stats = ranked_stats[ranked_stats['Function'] == func].sort_values('Rank')
        for idx, row in func_stats.iterrows():
            winner_mark = "🏆 " if row['Rank'] == 1 else "   "
            print(f"{winner_mark}{row['Algorithm']:8s}: 均值={row['Mean']:12.2e}, "
                  f"标准差={row['Std']:12.2e}, CV={row['CV']:6.2f}, 排名={int(row['Rank'])}")

    # 打印异常值摘要
    if not outliers.empty:
        print("\n" + "=" * 80)
        print("异常值摘要")
        print("=" * 80)

        for algo in outliers['Algorithm'].unique():
            algo_outliers = outliers[outliers['Algorithm'] == algo]
            print(f"\n【{algo}】共 {len(algo_outliers)} 个异常值：")
            for idx, row in algo_outliers.iterrows():
                print(f"   {row['Function']:12s} Run={row['Run']:2d}, Seed={row['Seed']:5d}, "
                      f"Value={row['Value']:12.2e} (正常范围: [{row['Lower']:12.2e}, {row['Upper']:12.2e}])")

    # 总排名
    print("\n" + "=" * 80)
    print("总排名（基于6个函数的平均排名）")
    print("=" * 80)

    overall_ranking = ranked_stats.groupby('Algorithm')['Rank'].mean().sort_values()
    for rank, (algo, avg_rank) in enumerate(overall_ranking.items(), 1):
        medal = "🥇" if rank == 1 else ("🥈" if rank == 2 else ("🥉" if rank == 3 else "  "))
        wins = len(ranked_stats[(ranked_stats['Algorithm'] == algo) & (ranked_stats['Rank'] == 1)])
        print(f"{medal} #{rank} {algo:8s}: 平均排名={avg_rank:.2f}, 夺冠次数={wins}/6")

    print("\n" + "=" * 80)
    print("分析完成！")
    print("=" * 80)

if __name__ == '__main__':
    main()

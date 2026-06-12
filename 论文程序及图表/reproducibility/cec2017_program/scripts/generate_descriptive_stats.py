#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
生成Q1/Q2实验的描述性统计报告
"""

import pandas as pd
import numpy as np
from pathlib import Path

def main():
    # 读取合并后的数据
    data_file = Path("results/q1q2_full_3150_merged.csv")
    df = pd.read_csv(data_file)

    print("=" * 80)
    print("   Q1/Q2 实验描述性统计报告")
    print("=" * 80)
    print(f"总数据量: {len(df)} 行")
    print(f"算法数量: {df['Algorithm'].nunique()} 个")
    print(f"规模数量: {df['TaskCount'].nunique()} 个")
    print(f"种子数量: {df['Seed'].nunique()} 个")
    print()

    # 按算法和规模分组统计 Internal Makespan
    print("=" * 80)
    print("   Internal Makespan 统计（按算法和规模）")
    print("=" * 80)

    summary = df.groupby(['Algorithm', 'TaskCount'])['InternalMakespan'].agg([
        ('Mean', 'mean'),
        ('Std', 'std'),
        ('Min', 'min'),
        ('Max', 'max'),
        ('Count', 'count')
    ]).reset_index()

    # 计算每个规模下的排名
    summary['Rank'] = summary.groupby('TaskCount')['Mean'].rank(method='min')

    # 保存详细统计
    output_file = Path("results/descriptive_statistics.csv")
    summary.to_csv(output_file, index=False)
    print(f"[OK] Detailed statistics saved to: {output_file}")
    print()

    # 显示每个规模的排名
    for scale in sorted(df['TaskCount'].unique()):
        print(f"\nScale M={scale}:")
        scale_data = summary[summary['TaskCount'] == scale].sort_values('Mean')

        for idx, row in scale_data.iterrows():
            rank = int(row['Rank'])
            medal = "[1]" if rank == 1 else "[2]" if rank == 2 else "[3]" if rank == 3 else "   "
            print(f"  {medal} {rank}. {row['Algorithm']:<15} "
                  f"Mean={row['Mean']:>8.2f}  Std={row['Std']:>7.2f}  "
                  f"Min={row['Min']:>8.2f}  Max={row['Max']:>8.2f}")

    # 计算算法总排名（跨所有规模的平均排名）
    print("\n" + "=" * 80)
    print("   算法总排名（平均排名）")
    print("=" * 80)

    avg_ranks = summary.groupby('Algorithm')['Rank'].mean().sort_values()

    for rank, (algo, avg_rank) in enumerate(avg_ranks.items(), 1):
        medal = "[1]" if rank == 1 else "[2]" if rank == 2 else "[3]" if rank == 3 else "   "
        print(f"{medal} {rank}. {algo:<15} Average Rank: {avg_rank:.2f}")

    # LoadBalanceRatio 统计
    print("\n" + "=" * 80)
    print("   LoadBalanceRatio 统计（按算法）")
    print("=" * 80)

    lbr_summary = df.groupby('Algorithm')['LoadBalanceRatio'].agg([
        ('Mean', 'mean'),
        ('Std', 'std'),
        ('Min', 'min'),
        ('Max', 'max')
    ]).sort_values('Mean')

    for algo, row in lbr_summary.iterrows():
        print(f"{algo:<15} Mean={row['Mean']:.4f}  Std={row['Std']:.4f}  "
              f"Min={row['Min']:.4f}  Max={row['Max']:.4f}")

    # 执行时间统计
    print("\n" + "=" * 80)
    print("   执行时间统计（毫秒，按算法）")
    print("=" * 80)

    time_summary = df.groupby('Algorithm')['ExecutionTime_ms'].agg([
        ('Mean', 'mean'),
        ('Std', 'std'),
        ('Min', 'min'),
        ('Max', 'max')
    ]).sort_values('Mean')

    for algo, row in time_summary.iterrows():
        print(f"{algo:<15} Mean={row['Mean']:>7.1f}ms  Std={row['Std']:>6.1f}ms  "
              f"Min={row['Min']:>5.0f}ms  Max={row['Max']:>6.0f}ms")

    print("\n" + "=" * 80)
    print("   报告生成完成！")
    print("=" * 80)

if __name__ == "__main__":
    main()

#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
5算法对比实验结果分析
生成统计摘要、排名分析和关键发现
"""

import pandas as pd
import numpy as np
from pathlib import Path

def analyze_results(csv_file):
    """分析实验结果"""

    # 读取数据
    df = pd.read_csv(csv_file)

    print("=" * 80)
    print("5算法大规模对比实验结果分析（Task 2.4）")
    print("=" * 80)
    print(f"\n数据文件: {csv_file}")
    print(f"总实验数: {len(df)} 次")
    print(f"算法: {df['Algorithm'].unique().tolist()}")
    print(f"规模: {sorted(df['TaskCount'].unique().tolist())}")
    print(f"种子: {sorted(df['Seed'].unique().tolist())}")

    # 1. 按规模和算法计算平均指标
    print("\n" + "=" * 80)
    print("1. 平均Makespan对比（按规模）")
    print("=" * 80)

    avg_makespan = df.groupby(['TaskCount', 'Algorithm'])['Makespan'].mean().unstack()
    print("\n平均Makespan (单位: 秒):")
    print(avg_makespan.to_string())

    # 2. 平均Load Balance Ratio
    print("\n" + "=" * 80)
    print("2. 平均Load Balance Ratio对比（按规模）")
    print("=" * 80)

    avg_lbr = df.groupby(['TaskCount', 'Algorithm'])['LoadBalanceRatio'].mean().unstack()
    print("\n平均Load Balance Ratio (越小越好):")
    print(avg_lbr.to_string())

    # 3. 算法排名分析（每个规模）
    print("\n" + "=" * 80)
    print("3. 算法排名（按Makespan，每个规模）")
    print("=" * 80)

    rankings = {}
    for scale in sorted(df['TaskCount'].unique()):
        scale_df = df[df['TaskCount'] == scale]
        avg_by_algo = scale_df.groupby('Algorithm')['Makespan'].mean().sort_values()

        print(f"\n规模 M={scale}:")
        for rank, (algo, makespan) in enumerate(avg_by_algo.items(), 1):
            medal = "[1st]" if rank == 1 else "[2nd]" if rank == 2 else "[3rd]" if rank == 3 else "     "
            print(f"  {medal} {rank}. {algo:15s} Makespan={makespan:.2e}")

            if scale not in rankings:
                rankings[scale] = {}
            rankings[scale][algo] = rank

    # 4. 总体平均排名
    print("\n" + "=" * 80)
    print("4. 总体平均排名（跨所有规模）")
    print("=" * 80)

    all_algorithms = df['Algorithm'].unique()
    overall_rankings = {}

    for algo in all_algorithms:
        ranks = [rankings[scale][algo] for scale in sorted(rankings.keys())]
        overall_rankings[algo] = np.mean(ranks)

    sorted_overall = sorted(overall_rankings.items(), key=lambda x: x[1])

    print("\n总体排名 (平均排名越低越好):")
    for rank, (algo, avg_rank) in enumerate(sorted_overall, 1):
        medal = "[1st]" if rank == 1 else "[2nd]" if rank == 2 else "[3rd]" if rank == 3 else "     "
        print(f"  {medal} {rank}. {algo:15s} 平均排名={avg_rank:.2f}")

    # 5. LSCBO-Fixed vs CBO 改进率分析
    print("\n" + "=" * 80)
    print("5. LSCBO-Fixed vs CBO 改进率分析")
    print("=" * 80)

    print("\n规模别改进率:")
    for scale in sorted(df['TaskCount'].unique()):
        scale_df = df[df['TaskCount'] == scale]
        cbo_avg = scale_df[scale_df['Algorithm'] == 'CBO']['Makespan'].mean()
        lscbo_avg = scale_df[scale_df['Algorithm'] == 'LSCBO-Fixed']['Makespan'].mean()
        improvement = (cbo_avg - lscbo_avg) / cbo_avg * 100

        status = "[OK]" if improvement > 0 else "[--]"
        print(f"  M={scale:4d}: CBO={cbo_avg:.2e}, LSCBO={lscbo_avg:.2e}, "
              f"改进率={improvement:+.2f}% {status}")

    # 整体改进率
    cbo_overall = df[df['Algorithm'] == 'CBO']['Makespan'].mean()
    lscbo_overall = df[df['Algorithm'] == 'LSCBO-Fixed']['Makespan'].mean()
    overall_improvement = (cbo_overall - lscbo_overall) / cbo_overall * 100

    print(f"\n整体平均改进率: {overall_improvement:+.2f}%")

    # 6. 运行时间分析
    print("\n" + "=" * 80)
    print("6. 算法运行时间分析（平均执行时间，单位: ms）")
    print("=" * 80)

    avg_time = df.groupby(['TaskCount', 'Algorithm'])['ExecutionTime_ms'].mean().unstack()
    print("\n平均执行时间:")
    print(avg_time.to_string())

    # 7. 关键发现总结
    print("\n" + "=" * 80)
    print("7. 关键发现总结")
    print("=" * 80)

    best_algo = sorted_overall[0][0]
    best_rank = sorted_overall[0][1]

    print(f"\n[OK] 最佳算法: {best_algo} (平均排名 {best_rank:.2f})")

    # LSCBO-Fixed在哪些规模表现最好
    lscbo_wins = []
    for scale, ranks in rankings.items():
        if ranks.get('LSCBO-Fixed', 999) == 1:
            lscbo_wins.append(scale)

    if lscbo_wins:
        print(f"[OK] LSCBO-Fixed夺冠规模: M={lscbo_wins}")
    else:
        print("[!!] LSCBO-Fixed未在任何规模夺冠")

    # 新算法表现
    new_algos = ['HHO', 'AOA', 'GTO']
    print(f"\n新增算法表现:")
    for algo in new_algos:
        if algo in overall_rankings:
            rank = overall_rankings[algo]
            wins = sum(1 for scale, ranks in rankings.items() if ranks.get(algo, 999) == 1)
            print(f"  - {algo}: 平均排名={rank:.2f}, 夺冠次数={wins}/4")

    # 稳定性分析（标准差）
    print(f"\nMakespan稳定性分析（标准差，越小越稳定）:")
    for algo in all_algorithms:
        algo_df = df[df['Algorithm'] == algo]
        std = algo_df['Makespan'].std()
        cv = std / algo_df['Makespan'].mean() * 100  # 变异系数
        print(f"  - {algo:15s}: StdDev={std:.2e}, CV={cv:.1f}%")

    print("\n" + "=" * 80)
    print("分析完成！")
    print("=" * 80)

    return df

if __name__ == "__main__":
    # 查找最新的结果文件
    results_dir = Path(__file__).parent.parent / "results"
    csv_files = list(results_dir.glob("five_algorithm_comparison_*.csv"))

    if not csv_files:
        print("[!!] 未找到结果文件")
        exit(1)

    latest_csv = max(csv_files, key=lambda p: p.stat().st_mtime)

    analyze_results(latest_csv)

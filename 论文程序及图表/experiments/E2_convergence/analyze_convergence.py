#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
E2: 全算法收敛分析 & 评估预算敏感性

从 per-iteration 日志中读取收敛轨迹，绘制全部 9 种算法的收敛曲线。
如果 per-iteration 数据不可用，使用评估预算分段的 makespan 数据代替。
"""

import sys, os
import numpy as np
import pandas as pd
from pathlib import Path
sys.path.insert(0, str(Path(__file__).parent.parent / 'shared'))
from statistical_tests import friedman_test, print_friedman_table, spearman_rank_correlation, format_p_value
from plotting_utils import plot_convergence_curves, plot_rank_heatmap, setup_paper_style


META_ALGOS = ['LSCBO', 'CBO', 'PSO', 'AOA', 'WOA', 'GWO', 'GTO', 'HHO', 'DBO']
TASK_SCALES = [100, 300, 500, 800, 1000, 2000]


def analyze_budget_sensitivity(csv_path: str, output_dir: str):
    """分析不同评估预算下的排名稳定性（使用聚合 CSV）"""
    os.makedirs(output_dir, exist_ok=True)
    df = pd.read_csv(csv_path)

    print("=" * 70)
    print("  E2 Part B: Evaluation Budget Sensitivity")
    print("=" * 70)

    # 假设 CSV 包含 Budget 列（评估预算数）
    if 'Budget' not in df.columns:
        print("  CSV 不含 Budget 列，仅显示单一预算结果")
        return

    budgets = sorted(df['Budget'].unique())
    algos = sorted(set(df['Algorithm'].unique()) & set(META_ALGOS))

    print(f"  预算: {budgets}")
    print(f"  算法: {algos}")

    # 每种预算下的 Friedman 排名
    all_ranks = []
    for budget in budgets:
        sub = df[df['Budget'] == budget]
        data = np.zeros((len(algos), len(TASK_SCALES)))
        for i, algo in enumerate(algos):
            for j, N in enumerate(TASK_SCALES):
                vals = sub[(sub['Algorithm'] == algo) & (sub['TaskCount'] == N)]['Makespan'].values
                data[i, j] = np.mean(vals) if len(vals) > 0 else np.nan

        valid_cols = ~np.isnan(data).any(axis=0)
        data_v = data[:, valid_cols]

        if data_v.shape[1] >= 2:
            result = friedman_test(data_v)
            all_ranks.append(result['avg_ranks'])
            print(f"\n  Budget = {budget}:")
            print_friedman_table(result, algos)
        else:
            all_ranks.append(np.zeros(len(algos)))

    # 排名稳定性 Spearman ρ
    print(f"\n  --- 预算间排名稳定性 ---")
    for i in range(len(budgets)):
        for j in range(i+1, len(budgets)):
            rho, p = spearman_rank_correlation(all_ranks[i], all_ranks[j])
            print(f"  Budget {budgets[i]} vs {budgets[j]}: ρ={rho:.4f} (p={format_p_value(p)})")


def analyze_convergence_trajectories(trajectory_dir: str, output_dir: str):
    """
    分析 per-iteration 收敛轨迹。

    期望文件格式: convergence_<algo>_N<scale>_seed<seed>.csv
    Columns: Iteration, BestMakespan
    """
    os.makedirs(output_dir, exist_ok=True)

    print("=" * 70)
    print("  E2 Part A: Full Convergence Analysis")
    print("=" * 70)

    # Gather data per algo per scale
    conv_data = {}  # algo -> {N: [(means, stds) per iteration]}

    for algo in META_ALGOS:
        conv_data[algo] = {}
        for N in TASK_SCALES:
            all_trajs = []
            for seed in range(43, 73):  # seeds 43-72
                fpath = f"{trajectory_dir}/convergence_{algo}_N{N}_seed{seed}.csv"
                if not os.path.exists(fpath):
                    continue
                traj = pd.read_csv(fpath)
                all_trajs.append(traj['BestMakespan'].values)

            if all_trajs:
                # Align to longest trajectory
                max_len = max(len(t) for t in all_trajs)
                padded = np.full((len(all_trajs), max_len), np.nan)
                for i, t in enumerate(all_trajs):
                    padded[i, :len(t)] = t
                means = np.nanmean(padded, axis=0)
                stds = np.nanstd(padded, axis=0)
                conv_data[algo][N] = (means, stds)

    if any(len(v) > 0 for v in conv_data.values()):
        plot_convergence_curves(
            conv_data, TASK_SCALES,
            output_path=f'{output_dir}/E2_convergence_all9.png',
            title="Convergence Analysis - All 9 Algorithms",
            xlabel="Iteration",
            ylabel="Best-so-far Makespan (s)",
            y_log=True
        )


def main():
    import argparse
    parser = argparse.ArgumentParser(description='E2: Convergence Analysis')
    parser.add_argument('--csv', help='CSV with Budget column for budget sensitivity')
    parser.add_argument('--trajectories', help='Directory with per-iteration convergence CSVs')
    parser.add_argument('-o', '--output', default='./E2_output')
    args = parser.parse_args()

    if args.csv:
        analyze_budget_sensitivity(args.csv, args.output)
    if args.trajectories:
        analyze_convergence_trajectories(args.trajectories, args.output)
    if not args.csv and not args.trajectories:
        print("Usage: --csv <budget_sensitivity.csv> or --trajectories <conv_dir>")


if __name__ == '__main__':
    main()

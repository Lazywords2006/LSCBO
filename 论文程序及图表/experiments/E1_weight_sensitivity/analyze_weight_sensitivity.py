#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
E1: 权重敏感性分析 — CSV 后处理脚本

处理 WeightSensitivityExperiment.java 输出的 CSV 文件，
生成 Friedman 排名表、排名稳定性矩阵、makespan/energy/cost/LBR 分解图。
"""

import sys
import os
import numpy as np
import pandas as pd
from pathlib import Path

# 添加共享模块路径
sys.path.insert(0, str(Path(__file__).parent.parent / 'shared'))
from statistical_tests import friedman_test, print_friedman_table, spearman_rank_correlation
from plotting_utils import plot_rank_heatmap, plot_rank_stability_matrix, setup_paper_style

# ==================== 配置 ====================

WEIGHT_CONFIGS = {
    'W0': {'w_ms': 0.25, 'w_en': 0.25, 'w_cost': 0.25, 'w_lb': 0.25, 'label': 'Equal (current)'},
    'W1': {'w_ms': 0.60, 'w_en': 0.10, 'w_cost': 0.10, 'w_lb': 0.20, 'label': 'Makespan-dominant'},
    'W2': {'w_ms': 0.10, 'w_en': 0.40, 'w_cost': 0.40, 'w_lb': 0.10, 'label': 'Energy-Cost Balanced'},
    'W3': {'w_ms': 0.85, 'w_en': 0.05, 'w_cost': 0.05, 'w_lb': 0.05, 'label': 'Pure Makespan'},
    'W4': {'w_ms': 0.05, 'w_en': 0.05, 'w_cost': 0.05, 'w_lb': 0.85, 'label': 'Pure LoadBalance'},
}

METRICS = ['Makespan', 'Cost', 'Energy', 'LoadBalanceIndex']
TASK_SCALES = [100, 300, 500, 800, 1000, 2000]


def load_csv(csv_path: str) -> pd.DataFrame:
    """加载 CSV 并解析 WeightConfig 和 TaskScale"""
    df = pd.read_csv(csv_path)

    # 如果 CSV 包含 weight config 列
    if 'WeightConfig' not in df.columns:
        print("Warning: CSV 不含 WeightConfig 列，假设为单配置")
        df['WeightConfig'] = 'W0'

    return df


def compute_friedman_per_config(df: pd.DataFrame, config: str, metric: str = 'Makespan') -> dict:
    """对指定权重配置执行 Friedman 检验"""
    sub = df[df['WeightConfig'] == config]
    algos = sorted(sub['Algorithm'].unique())
    n_algo = len(algos)

    data = np.zeros((n_algo, len(TASK_SCALES)))
    for i, algo in enumerate(algos):
        for j, N in enumerate(TASK_SCALES):
            vals = sub[(sub['Algorithm'] == algo) & (sub['TaskCount'] == N)][metric].values
            data[i, j] = np.mean(vals) if len(vals) > 0 else np.nan

    # 移除 NaN 列
    valid_cols = ~np.isnan(data).any(axis=0)
    data = data[:, valid_cols]

    result = friedman_test(data)
    result['algo_names'] = algos
    return result


def analyze_all_configs(df: pd.DataFrame, output_dir: str):
    """对所有权重配置进行分析"""
    os.makedirs(output_dir, exist_ok=True)

    configs = sorted(df['WeightConfig'].unique())
    algos = sorted(df['Algorithm'].unique())

    print(f"配置数: {len(configs)}, 算法数: {len(algos)}")

    # ========== 1. 每种配置的 Friedman 排名 ==========
    all_ranks = []  # 收集各配置的平均排名
    rank_data = []  # for heatmap

    for cfg in configs:
        print(f"\n--- Configuration {cfg} ---")
        result = compute_friedman_per_config(df, cfg)
        print_friedman_table(result, result['algo_names'])
        all_ranks.append(result['avg_ranks'])
        rank_data.append(result['avg_ranks'])

    rank_matrix = np.array(rank_data)
    plot_rank_heatmap(
        rank_matrix,
        config_names=configs,
        algo_names=result['algo_names'],
        output_path=f"{output_dir}/E1_rank_heatmap.png",
        title=f"Algorithm Rank Stability Across Weight Configurations"
    )

    # ========== 2. 排名稳定性 Spearman ρ 矩阵 ==========
    plot_rank_stability_matrix(
        all_ranks, config_names, result['algo_names'],
        output_path=f"{output_dir}/E1_rank_stability.png",
        title="Cross-Configuration Rank Stability (Spearman ρ)"
    )

    # ========== 3. LSCBO relative advantage 分解表 ==========
    print("\n\n--- LSCBO Relative Advantage Decomposition ---")
    print(f"{'Config':<8} {'Metric':<20} {'LSCBO Mean':<14} {'Best Competitor':<14} {'Δ%':<10}")

    for cfg in configs:
        sub = df[df['WeightConfig'] == cfg]
        lscbo_data = sub[sub['Algorithm'] == 'LSCBO']
        competitors = [a for a in algos if a != 'LSCBO']

        for metric in METRICS:
            lscbo_mean = lscbo_data[metric].mean()
            best_comp_mean = min(
                sub[sub['Algorithm'] == c][metric].mean() for c in competitors
            )
            delta_pct = ((best_comp_mean - lscbo_mean) / best_comp_mean * 100) if best_comp_mean > 0 else 0
            print(f"{cfg:<8} {metric:<20} {lscbo_mean:<14.4f} {best_comp_mean:<14.4f} {delta_pct:+.2f}%")

    print("\n  权重敏感性分析完成.")


def main():
    import argparse
    parser = argparse.ArgumentParser(description='E1: Weight Sensitivity Analysis')
    parser.add_argument('csv', help='CSV results file from WeightSensitivityExperiment')
    parser.add_argument('-o', '--output', default='./E1_output', help='Output directory')
    args = parser.parse_args()

    df = load_csv(args.csv)
    analyze_all_configs(df, args.output)


if __name__ == '__main__':
    main()

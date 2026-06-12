#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
E7: Phase 2-3 消融分析

处理 PhaseAblationExperiment.java 输出的 CSV。
对比完整 LSCBO 与移除 Phase 2/3 的消融变体。
"""

import sys, os
import numpy as np
import pandas as pd
from pathlib import Path
from scipy import stats as scipy_stats
sys.path.insert(0, str(Path(__file__).parent.parent / 'shared'))
from statistical_tests import friedman_test, print_friedman_table, wilcoxon_paired, cohens_d, format_p_value
from plotting_utils import plot_effect_size_forest, setup_paper_style


VARIANTS = [
    'LSCBO_Full',
    'LSCBO_NoPhase2',
    'LSCBO_NoPhase3',
    'LSCBO_Phase1Only',
    'RandomSearch+LS',
]


def analyze_ablation(csv_path: str, output_dir: str):
    os.makedirs(output_dir, exist_ok=True)
    df = pd.read_csv(csv_path)

    algorithms = sorted(df['Algorithm'].unique())
    scales = sorted(df['TaskCount'].unique())

    print("=" * 70)
    print("  E7: Phase 2-3 Isolation Ablation")
    print("=" * 70)
    print(f"\n  Variants: {algorithms}")
    print(f"  Scales: {scales}")

    # ========== Friedman 检验 ==========
    algos_found = [a for a in VARIANTS if a in algorithms]
    n_algo = len(algos_found)
    data = np.zeros((n_algo, len(scales)))

    for i, algo in enumerate(algos_found):
        for j, N in enumerate(scales):
            vals = df[(df['Algorithm'] == algo) & (df['TaskCount'] == N)]['Makespan'].values
            data[i, j] = np.mean(vals) if len(vals) > 0 else np.nan

    valid_cols = ~np.isnan(data).any(axis=0)
    data = data[:, valid_cols]
    valid_scales = [s for s, v in zip(scales, valid_cols) if v]

    if data.shape[1] >= 2:
        result = friedman_test(data)
        print_friedman_table(result, algos_found)

    # ========== Per-variant vs Full LSCBO ==========
    full_vals = df[df['Algorithm'] == 'LSCBO_Full']['Makespan'].values
    effects = []
    labels = []

    print(f"\n  --- Ablation vs LSCBO_Full ---")
    print(f"  {'Variant':<25} {'Mean':<12} {'Δ vs Full':<10} {'Wilcoxon p':<14} {'Cohens d':<10}")

    lscbo_mean = np.mean(full_vals)
    print(f"  {'LSCBO_Full':<25} {lscbo_mean:<12.4f} {'-':<10} {'-':<14} {'-':<10}")

    for var in VARIANTS[1:]:  # Skip Full itself
        if var not in algorithms:
            continue
        var_vals = df[df['Algorithm'] == var]['Makespan'].values
        if len(var_vals) == 0:
            continue

        w_test = wilcoxon_paired(full_vals, var_vals)
        d_val = cohens_d(full_vals, var_vals)  # positive = Full better
        delta_pct = ((np.mean(var_vals) - lscbo_mean) / lscbo_mean * 100)

        print(f"  {var:<25} {np.mean(var_vals):<12.4f} {delta_pct:+.2f}%{'':>4} {format_p_value(w_test['p_value']):<14} {d_val:<10.4f}")

        effects.append(d_val)
        labels.append(var.replace('LSCBO_', ''))

    plot_effect_size_forest(
        effects, labels,
        output_path=f'{output_dir}/E7_phase_ablation_effects.png',
        title="Phase Accumulation Effect: Cohen's d vs Full LSCBO"
    )

    # 判断最小有效配置
    print(f"\n  >>> 累积消融完成。检查 Phase 2/3 的独立贡献。")

    # Check if LSCBO_Phase1Only (just Phase1 + LS) vs Full is significantly worse
    if 'LSCBO_Phase1Only' in algorithms:
        p1_vals = df[df['Algorithm'] == 'LSCBO_Phase1Only']['Makespan'].values
        w = wilcoxon_paired(full_vals, p1_vals)
        d = cohens_d(full_vals, p1_vals)
        print(f"\n  Phase1Only vs Full: d={d:.4f}, p={format_p_value(w['p_value'])}")
        if w['p_value'] < 0.05:
            print(f"  >>> Phases 2+3 联合贡献统计显著 (p={format_p_value(w['p_value'])})")
        else:
            print(f"  >>> Phases 2+3 联合贡献不显著 (p={format_p_value(w['p_value'])})")
            print(f"  >>> 最小有效配置 = Phase 1 + LS")


def main():
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument('csv', help='CSV from PhaseAblationExperiment')
    parser.add_argument('-o', '--output', default='./E7_output')
    args = parser.parse_args()
    analyze_ablation(args.csv, args.output)

if __name__ == '__main__':
    main()

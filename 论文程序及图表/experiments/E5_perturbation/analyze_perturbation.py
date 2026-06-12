#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
E5: Lévy vs 替代扰动机制分析

处理 PerturbationExperiment.java 输出的 CSV。
对比 Lévy flight、Gaussian、Cauchy、RandomRestart、无扰动 的 makespan 差异。
"""

import sys, os
import numpy as np
import pandas as pd
from pathlib import Path
from scipy import stats as scipy_stats
sys.path.insert(0, str(Path(__file__).parent.parent / 'shared'))
from statistical_tests import wilcoxon_paired, cohens_d, holm_correction, format_p_value
from plotting_utils import plot_effect_size_forest, plot_grouped_bars, setup_paper_style


CBO_LS_BASELINE = 'CBO+LS'  # CBO + LS (no perturbation)
VARIANTS = ['CBO+LS+Lévy', 'CBO+LS+Gaussian', 'CBO+LS+Cauchy', 'CBO+LS+Restart']


def analyze_perturbation(csv_path: str, output_dir: str):
    os.makedirs(output_dir, exist_ok=True)
    df = pd.read_csv(csv_path)

    algorithms = sorted(df['Algorithm'].unique())
    scales = sorted(df['TaskCount'].unique())

    print("=" * 70)
    print("  E5: Perturbation Mechanism Comparison")
    print("=" * 70)
    print(f"\n  Algorithms: {algorithms}")
    print(f"  Task Scales: {scales}")

    # 获取 baseline (CBO+LS, no perturbation)
    base_vals = df[df['Algorithm'] == CBO_LS_BASELINE]['Makespan'].values

    effects = []
    labels = []
    p_values = []
    results = []

    for var in VARIANTS:
        if var not in algorithms:
            continue
        var_vals = df[df['Algorithm'] == var]['Makespan'].values
        if len(var_vals) == 0:
            continue

        w_test = wilcoxon_paired(base_vals, var_vals)
        d = cohens_d(base_vals, var_vals)
        improvement_pct = (np.mean(base_vals) - np.mean(var_vals)) / np.mean(base_vals) * 100

        print(f"\n  {var} vs {CBO_LS_BASELINE}:")
        print(f"    Baseline mean: {np.mean(base_vals):.4f}, Variant mean: {np.mean(var_vals):.4f}")
        print(f"    Improvement: {improvement_pct:+.2f}%")
        print(f"    Wilcoxon p = {format_p_value(w_test['p_value'])}")
        print(f"    Cohen's d = {d:.4f}")

        effects.append(d)
        labels.append(var.replace('CBO+LS+', ''))
        p_values.append(w_test['p_value'])
        results.append({
            'Variant': var,
            'Improvement_%': improvement_pct,
            'Wilcoxon_p': w_test['p_value'],
            'Cohens_d': d,
            'Baseline_Mean': np.mean(base_vals),
            'Variant_Mean': np.mean(var_vals),
        })

    # Holm 校正
    if len(p_values) > 0:
        corrected = holm_correction(p_values)
        for i, r in enumerate(results):
            r['Holm_corrected_p'] = corrected[i]

    # 效应量森林图
    plot_effect_size_forest(
        effects, labels,
        output_path=f'{output_dir}/E5_perturbation_effects.png',
        title="Perturbation Mechanism Effect Size vs No Perturbation"
    )

    # 汇总
    results_df = pd.DataFrame(results)
    print("\n--- Summary ---")
    print(results_df.to_string(index=False))
    results_df.to_csv(f'{output_dir}/E5_perturbation_comparison.csv', index=False)

    # 判断 L\'evy 是否为最佳
    best_idx = np.argmax(effects)
    print(f"\n  >>> Best perturbation: {labels[best_idx]} (Cohen's d = {effects[best_idx]:.4f})")
    print(f"  >>> Lévy improvement: {effects[0]:.4f}" if 'Levy' in labels[0] else f"  >>> Lévy not in variants")


def main():
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument('csv', help='CSV from PerturbationExperiment')
    parser.add_argument('-o', '--output', default='./E5_output')
    args = parser.parse_args()
    analyze_perturbation(args.csv, args.output)

if __name__ == '__main__':
    main()

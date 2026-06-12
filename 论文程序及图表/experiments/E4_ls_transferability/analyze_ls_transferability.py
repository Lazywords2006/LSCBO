#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
E4: LS 可迁移性分析

处理 LSTransferabilityExperiment.java 输出的 CSV。
对比原始算法 vs 原始算法+LS 的 makespan 差异。
"""

import sys, os
import numpy as np
import pandas as pd
from pathlib import Path
from scipy import stats as scipy_stats
sys.path.insert(0, str(Path(__file__).parent.parent / 'shared'))
from statistical_tests import wilcoxon_paired, cohens_d, format_p_value
from plotting_utils import plot_effect_size_forest, plot_grouped_bars, setup_paper_style


def analyze_transferability(csv_path: str, output_dir: str):
    os.makedirs(output_dir, exist_ok=True)
    df = pd.read_csv(csv_path)

    algorithms = sorted(df['Algorithm'].unique())
    scales = sorted(df['TaskCount'].unique())

    print("=" * 70)
    print("  E4: LS Transferability Analysis")
    print("=" * 70)

    # 识别基线和 +LS 变体
    baselines = [a for a in algorithms if '+LS' not in a and 'Random' not in a and 'FCFS' not in a and 'RoundRobin' not in a and 'MinMin' not in a and 'MaxMin' not in a]
    ls_variants = [a for a in algorithms if '+LS' in a]

    print(f"\n  Baselines: {baselines}")
    print(f"  +LS Variants: {ls_variants}")

    effects = []
    labels = []
    results = []

    for base in baselines:
        ls_name = f"{base}+LS"
        if ls_name not in algorithms:
            continue

        base_vals = df[df['Algorithm'] == base]['Makespan'].values
        ls_vals = df[df['Algorithm'] == ls_name]['Makespan'].values

        if len(base_vals) == 0 or len(ls_vals) == 0:
            continue

        # Wilcoxon 检验
        w_test = wilcoxon_paired(base_vals, ls_vals)
        d = cohens_d(base_vals, ls_vals)

        improvement_pct = (np.mean(base_vals) - np.mean(ls_vals)) / np.mean(base_vals) * 100

        print(f"\n  {base} vs {ls_name}:")
        print(f"    Base mean: {np.mean(base_vals):.4f}, LS mean: {np.mean(ls_vals):.4f}")
        print(f"    Improvement: {improvement_pct:+.2f}%")
        print(f"    Wilcoxon p = {format_p_value(w_test['p_value'])}")
        print(f"    Cohen's d = {d:.4f}")

        effects.append(d)
        labels.append(base)
        results.append({
            'Algorithm': base,
            'LS_Improvement_%': improvement_pct,
            'Wilcoxon_p': w_test['p_value'],
            'Cohens_d': d,
            'Base_Mean': np.mean(base_vals),
            'LS_Mean': np.mean(ls_vals),
        })

    # 绘制效应量森林图
    plot_effect_size_forest(
        effects, labels,
        output_path=f'{output_dir}/E4_effect_sizes.png',
        title="LS Transferability: Cohen's d (positive = LS improves)"
    )

    # 汇总表
    results_df = pd.DataFrame(results)
    print("\n--- Summary ---")
    print(results_df.to_string(index=False))
    results_df.to_csv(f'{output_dir}/E4_ls_transferability.csv', index=False)

    # 判断: LS 是通用算子还是 CBO 特异
    sig_count = sum(1 for r in results if r['Wilcoxon_p'] < 0.05)
    print(f"\n  >>> {sig_count}/{len(results)} algorithms show significant improvement with LS")
    print(f"  >>> Conclusion: LS is {'a general-purpose' if sig_count > len(results)/2 else 'a CBO-specific'} enhancement operator")


def main():
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument('csv', help='CSV results from LSTransferabilityExperiment')
    parser.add_argument('-o', '--output', default='./E4_output')
    args = parser.parse_args()
    analyze_transferability(args.csv, args.output)

if __name__ == '__main__':
    main()

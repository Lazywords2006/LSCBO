#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
E8: Min-Min 运行时间匹配比较

处理 MinMinBudgetExperiment.java 输出的 CSV。
对比 budget-limited Min-Min 与 LSCBO 的 makespan。
"""

import sys, os
import numpy as np
import pandas as pd
from pathlib import Path
sys.path.insert(0, str(Path(__file__).parent.parent / 'shared'))
from statistical_tests import wilcoxon_paired, cohens_d, format_p_value


def analyze_minmin(csv_path: str, output_dir: str):
    os.makedirs(output_dir, exist_ok=True)
    df = pd.read_csv(csv_path)

    print("=" * 70)
    print("  E8: Min-Min Budget-Limited Comparison")
    print("=" * 70)

    scales = sorted(df['TaskCount'].unique())
    print(f"  Scales: {scales}")

    print(f"\n  {'Scale':<10} {'MinMin Budget':<16} {'LSCBO':<16} {'Δ%':<12} {'Wilcoxon p':<14} {'Cohens d':<10}")
    print("  " + "-" * 78)

    for N in scales:
        sub = df[df['TaskCount'] == N]
        mm_vals = sub[sub['Algorithm'] == 'MinMin_Budget']['Makespan'].values
        lscbo_vals = sub[sub['Algorithm'] == 'LSCBO']['Makespan'].values

        if len(mm_vals) == 0 or len(lscbo_vals) == 0:
            continue

        mm_mean = np.mean(mm_vals)
        lscbo_mean = np.mean(lscbo_vals)
        delta = ((mm_mean - lscbo_mean) / mm_mean * 100)
        w = wilcoxon_paired(lscbo_vals, mm_vals)
        d = cohens_d(lscbo_vals, mm_vals)

        print(f"  {N:<10} {mm_mean:<16.4f} {lscbo_mean:<16.4f} {delta:+.2f}%{'':>5} {format_p_value(w['p_value']):<14} {d:<10.4f}")

    print(f"\n  >>> 运行时间匹配比较完成。")
    print(f"  >>> 如果 MinMin_Budget 在匹配预算下仍优于 LSCBO，论文应承认确定性启发式的下限仍有价值。")


def main():
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument('csv', help='CSV from MinMinBudgetExperiment')
    parser.add_argument('-o', '--output', default='./E8_output')
    args = parser.parse_args()
    analyze_minmin(args.csv, args.output)

if __name__ == '__main__':
    main()

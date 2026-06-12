#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
E3: Energy-Makespan Correlation Analysis

从已有实验数据（E4_LSCBOFinal 或 E1_merged）中提取 Makespan 和 Energy，
计算 Pearson 和 Spearman 相关系数，验证 r > 0.9 的声明。

用法:
  python3 energy_makespan_correlation.py <csv_file> [-o output_dir]
"""

import csv, sys, os
from collections import defaultdict
import math

def mean(vals):
    return sum(vals) / len(vals)

def pearson_r(xs, ys):
    n = len(xs)
    mx, my = mean(xs), mean(ys)
    sx = math.sqrt(sum((x - mx)**2 for x in xs))
    sy = math.sqrt(sum((y - my)**2 for y in ys))
    if sx == 0 or sy == 0:
        return 0
    return sum((x - mx)*(y - my) for x, y in zip(xs, ys)) / (sx * sy)

def spearman_rho(xs, ys):
    n = len(xs)
    rank_x = {v: i+1 for i, v in enumerate(sorted(set(xs)))}
    rank_y = {v: i+1 for i, v in enumerate(sorted(set(ys)))}
    rx = [rank_x[x] for x in xs]
    ry = [rank_y[y] for y in ys]
    return pearson_r(rx, ry)

def r_squared(xs, ys):
    n = len(xs)
    mx, my = mean(xs), mean(ys)
    # Simple linear regression: y = b0 + b1*x
    ss_xy = sum((x - mx)*(y - my) for x, y in zip(xs, ys))
    ss_xx = sum((x - mx)**2 for x in xs)
    b1 = ss_xy / ss_xx if ss_xx != 0 else 0
    b0 = my - b1 * mx
    y_pred = [b0 + b1*x for x in xs]
    ss_res = sum((y - yp)**2 for y, yp in zip(ys, y_pred))
    ss_tot = sum((y - my)**2 for y in ys)
    r2 = 1 - ss_res / ss_tot if ss_tot != 0 else 0
    return r2, b0, b1

def main():
    if len(sys.argv) < 2:
        print("Usage: python3 energy_makespan_correlation.py <csv_file> [-o output_dir]")
        sys.exit(1)

    csv_path = sys.argv[1]
    output_dir = sys.argv[sys.argv.index('-o') + 1] if '-o' in sys.argv else './E3_output'
    os.makedirs(output_dir, exist_ok=True)

    # Read data
    rows = []
    with open(csv_path) as f:
        reader = csv.DictReader(f)
        for row in reader:
            rows.append(row)

    # Detect column names (tolerant of variations)
    mk_col = None
    en_col = None
    scale_col = None
    algo_col = None
    for col in rows[0].keys():
        cl = col.lower().replace(' ', '').replace('_', '')
        if 'makespan' in cl:
            mk_col = col
        elif 'energy' in cl:
            en_col = col
        elif 'taskcount' in cl or 'scale' in cl or 'm' == cl.lower():
            scale_col = col
        elif 'algorithm' in cl or 'algo' in cl:
            algo_col = col

    if not mk_col or not en_col:
        print(f"ERROR: Cannot find Makespan/Energy columns. Available: {list(rows[0].keys())}")
        sys.exit(1)

    print(f"Columns: Makespan='{mk_col}', Energy='{en_col}', Scale='{scale_col}', Algo='{algo_col}'")
    print(f"Total data points: {len(rows)}")

    # Group by scale if available
    scales = {}
    for row in rows:
        s = row.get(scale_col, 'all')
        if s not in scales:
            scales[s] = {'mk': [], 'en': []}
        scales[s]['mk'].append(float(row[mk_col]))
        scales[s]['en'].append(float(row[en_col]))

    # Per-scale correlation
    print("\n" + "="*70)
    print("  Per-Scale Energy-Makespan Correlation")
    print("="*70)
    print(f"  {'Scale':<12} {'N':<8} {'Pearson r':<12} {'Spearman rho':<14} {'R²':<10}")
    print("  " + "-"*60)

    all_mk, all_en = [], []
    for s in sorted(scales.keys()):
        mks = scales[s]['mk']
        ens = scales[s]['en']
        n = len(mks)
        r = pearson_r(mks, ens)
        rho = spearman_rho(mks, ens)
        r2, _, _ = r_squared(mks, ens)
        print(f"  {str(s):<12} {n:<8} {r:<12.4f} {rho:<14.4f} {r2:<10.4f}")
        all_mk.extend(mks)
        all_en.extend(ens)

    # Overall correlation
    print("  " + "-"*60)
    r_all = pearson_r(all_mk, all_en)
    rho_all = spearman_rho(all_mk, all_en)
    r2_all, b0, b1 = r_squared(all_mk, all_en)
    print(f"  {'ALL':<12} {len(all_mk):<8} {r_all:<12.4f} {rho_all:<14.4f} {r2_all:<10.4f}")

    print(f"\n  Linear regression: Energy = {b0:.4f} + {b1:.4f} × Makespan")
    print(f"  Correlation level: {'STRONG (r > 0.9)' if r_all > 0.9 else 'MODERATE (0.7-0.9)' if r_all > 0.7 else 'WEAK (r < 0.7)'}")

    # Summary file
    with open(f'{output_dir}/E3_correlation_summary.txt', 'w') as f:
        f.write(f"Energy-Makespan Correlation Analysis\n")
        f.write(f"Source: {csv_path}\n")
        f.write(f"Data points: {len(rows)}\n")
        f.write(f"Pearson r (overall): {r_all:.4f}\n")
        f.write(f"Spearman rho (overall): {rho_all:.4f}\n")
        f.write(f"R² (overall): {r2_all:.4f}\n")
        f.write(f"Linear model: Energy = {b0:.4f} + {b1:.4f} × Makespan\n")
        f.write(f"Verdict: r > 0.9 claim is {'SUPPORTED' if r_all > 0.9 else 'NOT SUPPORTED'}\n")

    print(f"\n  Summary saved to: {output_dir}/E3_correlation_summary.txt")

if __name__ == '__main__':
    main()

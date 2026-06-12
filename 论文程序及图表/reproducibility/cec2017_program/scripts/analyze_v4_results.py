#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Analysis of LSCBO v4 Parameter Optimization - Robustness Test Results
Comparison: LSCBO (v4 ultra-aggressive) vs CBO (non-standard) vs PSO (standard)
"""

import pandas as pd
import numpy as np
import sys

# Force UTF-8 output
sys.stdout.reconfigure(encoding='utf-8')

# Read results (use absolute path)
import os
script_dir = os.path.dirname(os.path.abspath(__file__))

# 自动检测最新的CEC2017结果文件
results_dir = os.path.join(script_dir, '..', 'results')
cec2017_files = [f for f in os.listdir(results_dir) if f.startswith('cec2017_') and f.endswith('.csv')]
if not cec2017_files:
    # 回退到旧的文件名
    results_path = os.path.join(results_dir, 'simple_robustness_test.csv')
else:
    # 使用最新的CEC2017文件
    cec2017_files.sort(key=lambda x: os.path.getmtime(os.path.join(results_dir, x)), reverse=True)
    results_path = os.path.join(results_dir, cec2017_files[0])
    print(f"读取数据文件: {cec2017_files[0]}\n")

df = pd.read_csv(results_path)

# Calculate statistics by algorithm and function
results_summary = df.groupby(['Algorithm', 'Function'])['BestFitness'].agg([
    ('Mean', 'mean'),
    ('Std', 'std'),
    ('Min', 'min'),
    ('Max', 'max')
]).reset_index()

print("=" * 100)
print("  LSCBO v4 Ultra-Aggressive Parameter Optimization - Robustness Test Results")
print("=" * 100)
print()
print("Algorithm Configurations:")
print("  - LSCBO v4: LEVY_ALPHA=0.25, SPIRAL_B=1.2, W_MAX=0.95, GAUSSIAN_PROB=0.25")
print("  - CBO (Non-standard): ATTACK_WEIGHT=0.2 (overly aggressive attack)")
print("  - PSO (Standard): w=0.9->0.4 linear decay, c1=c2=2.0")
print()

# Display results by function
functions = df['Function'].unique()
algorithms = ['LSCBO', 'CBO', 'PSO']

print("=" * 100)
print("  Algorithm Performance Comparison (Mean Values)")
print("=" * 100)
print()
print(f"{'Function':<15} {'LSCBO':<18} {'CBO':<18} {'PSO':<18} {'Winner':<10}")
print("-" * 100)

for func in functions:
    means = {}
    for alg in algorithms:
        mean_val = results_summary[(results_summary['Algorithm'] == alg) &
                                   (results_summary['Function'] == func)]['Mean'].values[0]
        means[alg] = mean_val

    # Find best
    best_alg = min(means, key=means.get)

    # Print results
    lscbo_str = f"{means['LSCBO']:.6e}"
    cbo_str = f"{means['CBO']:.6e}"
    pso_str = f"{means['PSO']:.6e}"

    if best_alg == 'LSCBO':
        lscbo_str += " *WIN*"
    elif best_alg == 'CBO':
        cbo_str += " *WIN*"
    else:
        pso_str += " *WIN*"

    print(f"{func:<15} {lscbo_str:<18} {cbo_str:<18} {pso_str:<18} {best_alg:<10}")

print()
print("=" * 100)
print("  Algorithm Victory Statistics")
print("=" * 100)
print()

# Count wins
wins = {'LSCBO': 0, 'CBO': 0, 'PSO': 0}
for func in functions:
    means = {}
    for alg in algorithms:
        mean_val = results_summary[(results_summary['Algorithm'] == alg) &
                                   (results_summary['Function'] == func)]['Mean'].values[0]
        means[alg] = mean_val
    best_alg = min(means, key=means.get)
    wins[best_alg] += 1

print(f"{'Algorithm':<10} {'Wins':<10} {'Win Rate':<10}")
print("-" * 100)
for alg in algorithms:
    win_rate = wins[alg] / len(functions) * 100
    print(f"{alg:<10} {wins[alg]}/{len(functions):<8} {win_rate:.1f}%")

print()
print("=" * 100)
print("  Performance Difference Analysis (vs LSCBO v4)")
print("=" * 100)
print()

for func in functions:
    means = {}
    for alg in algorithms:
        mean_val = results_summary[(results_summary['Algorithm'] == alg) &
                                   (results_summary['Function'] == func)]['Mean'].values[0]
        means[alg] = mean_val

    print(f"{func}:")

    # CBO vs LSCBO
    if means['LSCBO'] > 0:
        cbo_diff = (means['CBO'] / means['LSCBO'] - 1.0) * 100.0
        if cbo_diff > 10.0:
            status = "[MUCH WORSE]"
        elif cbo_diff > 0.0:
            status = "[SLIGHTLY WORSE]"
        else:
            status = "[BETTER]"
        print(f"  CBO vs LSCBO: {cbo_diff:+.2f}% {status}")

    # PSO vs LSCBO
    if means['LSCBO'] > 0:
        pso_diff = (means['PSO'] / means['LSCBO'] - 1.0) * 100.0
        if pso_diff > 10.0:
            status = "[MUCH WORSE]"
        elif pso_diff > 0.0:
            status = "[SLIGHTLY WORSE]"
        else:
            status = "[BETTER]"
        print(f"  PSO vs LSCBO: {pso_diff:+.2f}% {status}")

    print()

print("=" * 100)
print("  Detailed Statistics")
print("=" * 100)
print()
print(results_summary.to_string(index=False))

print()
print("=" * 100)
print("  v4 Parameter Optimization Summary")
print("=" * 100)
print()
print("Parameter Changes (v3 -> v4):")
print("  - LEVY_ALPHA_COEF: 0.20 -> 0.25 (+25%)")
print("  - SPIRAL_B: 1.0 -> 1.2 (+20%)")
print("  - W_MAX: 0.90 -> 0.95 (+6%)")
print("  - GAUSSIAN_PROB: 0.20 -> 0.25 (+25%)")
print("  - SIGMA_MAX: 0.25 -> 0.30 (+20%)")
print()
print(f"LSCBO wins: {wins['LSCBO']}/6 ({wins['LSCBO']/6*100:.1f}%)")
print(f"CBO wins: {wins['CBO']}/6 ({wins['CBO']/6*100:.1f}%)")
print(f"PSO wins: {wins['PSO']}/6 ({wins['PSO']/6*100:.1f}%)")
print()

# Key findings
print("=" * 100)
print("  Key Findings")
print("=" * 100)
print()
if wins['LSCBO'] > wins['CBO'] and wins['LSCBO'] > wins['PSO']:
    print("[SUCCESS] LSCBO v4 achieved the highest win rate!")
elif wins['LSCBO'] >= 2:
    print("[COMPETITIVE] LSCBO v4 shows competitive performance.")
else:
    print("[NEEDS IMPROVEMENT] LSCBO v4 needs further parameter tuning.")

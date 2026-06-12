#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
E3: Energy-Makespan 相关性分析 —— 纯数据分析（无需额外实验运行）

从现有实验 CSV 中提取 Makespan 和 Energy 的 per-run 值，
计算 Pearson/Spearman 相关系数，执行 PCA 分析。
"""

import sys
import os
import numpy as np
import pandas as pd
from pathlib import Path
from scipy import stats as scipy_stats
from sklearn.decomposition import PCA
from sklearn.preprocessing import StandardScaler

sys.path.insert(0, str(Path(__file__).parent.parent / 'shared'))
from plotting_utils import plot_scatter_correlation, setup_paper_style
from statistical_tests import format_p_value


def load_data(csv_path: str) -> pd.DataFrame:
    """加载实验 CSV 数据"""
    return pd.read_csv(csv_path)


def analyze_correlation(df: pd.DataFrame, output_dir: str):
    """分析 Makespan-Energy 相关性"""
    os.makedirs(output_dir, exist_ok=True)

    print("=" * 70)
    print("  E3: Energy-Makespan Correlation Analysis")
    print("=" * 70)

    # 移除 FCFS/RoundRobin/Random（它们使相关性虚假升高）
    meta_algos = ['LSCBO', 'CBO', 'PSO', 'AOA', 'WOA', 'GWO', 'GTO', 'HHO']
    df_meta = df[df['Algorithm'].isin(meta_algos)].copy()

    makespan = df_meta['Makespan'].values
    energy = df_meta['Energy'].values

    # 只保留有效数据
    mask = ~(np.isnan(makespan) | np.isnan(energy) | (makespan <= 0) | (energy <= 0))
    ms = makespan[mask]
    en = energy[mask]

    print(f"\n  有效数据点数: {len(ms)}")
    print(f"  Makespan: mean={ms.mean():.4f}, std={ms.std():.4f}")
    print(f"  Energy:   mean={en.mean():.6f}, std={en.std():.6f}")

    # ========== 1. 全量散点图 ==========
    pearson_r, pearson_p = scipy_stats.pearsonr(ms, en)
    spearman_r, spearman_p = scipy_stats.spearmanr(ms, en)

    print(f"\n  --- 全量相关性 ---")
    print(f"  Pearson r  = {pearson_r:.4f} (p = {format_p_value(pearson_p)})")
    print(f"  Spearman ρ = {spearman_r:.4f} (p = {format_p_value(spearman_p)})")
    print(f"  R² (线性模型) = {pearson_r**2:.4f}")

    plot_scatter_correlation(
        ms, en,
        xlabel='Makespan (s)', ylabel='Energy (kWh)',
        output_path=f'{output_dir}/E3_correlation_scatter.png',
        title=f'Energy vs Makespan (n={len(ms)}, Pearson r={pearson_r:.4f})'
    )

    # ========== 2. Per-scale 相关性 ==========
    scales = sorted(df_meta['TaskCount'].unique())
    print(f"\n  --- 各任务规模相关性 ---")
    print(f"  {'Scale':<10} {'Pearson r':<12} {'Spearman ρ':<12} {'R²':<10} {'n':<8}")

    for N in scales:
        sub = df_meta[df_meta['TaskCount'] == N]
        ms_n = sub['Makespan'].values
        en_n = sub['Energy'].values
        mask_n = ~(np.isnan(ms_n) | np.isnan(en_n) | (ms_n <= 0) | (en_n <= 0))
        if mask_n.sum() < 10:
            continue
        pr, _ = scipy_stats.pearsonr(ms_n[mask_n], en_n[mask_n])
        sr, _ = scipy_stats.spearmanr(ms_n[mask_n], en_n[mask_n])
        print(f"  {N:<10} {pr:<12.4f} {sr:<12.4f} {pr**2:<10.4f} {mask_n.sum():<8}")

    # ========== 3. Linear regression Energy ~ Makespan ==========
    slope, intercept, r_val, p_val, std_err = scipy_stats.linregress(ms, en)
    print(f"\n  --- 线性回归: Energy = β₀ + β₁ × Makespan ---")
    print(f"  β₀ (intercept) = {intercept:.6e}")
    print(f"  β₁ (slope)     = {slope:.6e}")
    print(f"  R²             = {r_val**2:.4f}")
    print(f"  p-value        = {format_p_value(p_val)}")

    # ========== 4. PCA analysis ==========
    metrics = ['Makespan', 'Cost', 'Energy', 'LoadBalanceIndex']
    data_4d = df_meta[metrics].dropna().values
    scaler = StandardScaler()
    data_scaled = scaler.fit_transform(data_4d)

    pca = PCA()
    pca.fit(data_scaled)

    print(f"\n  --- PCA 方差解释（四目标） ---")
    print(f"  {'Component':<12} {'Explained Var':<16} {'Cumulative':<12}")
    cumsum = 0
    for i, ev in enumerate(pca.explained_variance_ratio_):
        cumsum += ev
        print(f"  PC{i+1:<11} {ev:<16.4f} {cumsum:<12.4f}")

    print(f"\n  --- 主成分载荷 ---")
    for i, comp in enumerate(pca.components_):
        print(f"  PC{i+1}: ", end='')
        for m, v in zip(metrics, comp):
            print(f"{m}={v:.4f}  ", end='')
        print()

    # 有效目标维度：累计解释 > 99% 所需的 PC 数
    effective_dim = 1
    cumsum = 0
    for ev in pca.explained_variance_ratio_:
        cumsum += ev
        effective_dim += 1
        if cumsum >= 0.99:
            break

    print(f"\n  >>> 有效目标维度 (99% 方差): {effective_dim}")
    print(f"  >>> 第一主成分方差占比: {pca.explained_variance_ratio_[0]:.4f}")
    print(f"  >>> 前两主成分累计: {pca.explained_variance_ratio_[:2].sum():.4f}")

    if pca.explained_variance_ratio_[0] > 0.9:
        print("\n  ⚠️  结论：四目标优化实质上是单目标优化（第一 PC > 90% 方差）。")
        print("  建议论文中将 'four-objective' 改为 'multi-criteria' 或说明有效维度。")
    elif pca.explained_variance_ratio_[:2].sum() > 0.9:
        print("\n  ⚠️  结论：四目标可降维为两目标（前两 PC > 90% 方差）。")


def main():
    import argparse
    parser = argparse.ArgumentParser(description='E3: Energy-Makespan Correlation Analysis')
    parser.add_argument('csv', help='CSV results file (e.g., journal_FINAL_1800.csv)')
    parser.add_argument('-o', '--output', default='./E3_output', help='Output directory')
    args = parser.parse_args()

    df = load_data(args.csv)
    analyze_correlation(df, args.output)


if __name__ == '__main__':
    main()

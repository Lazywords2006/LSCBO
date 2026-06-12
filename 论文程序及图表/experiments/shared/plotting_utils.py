#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
LSCBO 审稿补充实验 —— 共享绘图工具

论文级图表样式，统一使用 matplotlib + seaborn。
"""

import numpy as np
import pandas as pd
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import matplotlib.ticker as ticker
from pathlib import Path
from typing import List, Dict, Optional, Tuple


# ==================== 全局样式 ====================

# 论文级配色方案 (色盲友好)
COLORS = {
    'LSCBO': '#1f77b4',   # 蓝色
    'CBO': '#ff7f0e',     # 橙色
    'PSO': '#2ca02c',     # 绿色
    'AOA': '#d62728',     # 红色
    'WOA': '#9467bd',     # 紫色
    'GWO': '#8c564b',     # 棕色
    'HHO': '#e377c2',     # 粉色
    'DBO': '#7f7f7f',     # 灰色
    'GTO': '#bcbd22',     # 黄绿
    'FCFS': '#17becf',    # 青色
    'RoundRobin': '#1a1a1a',
    'Random': '#aaaaaa',
    'MinMin': '#e41a1c',
    'MaxMin': '#377eb8',
}


def setup_paper_style(figsize=(8, 5)):
    """设置论文级 matplotlib 样式"""
    plt.rcParams.update({
        'font.family': 'sans-serif',
        'font.sans-serif': ['Arial', 'Helvetica', 'DejaVu Sans'],
        'font.size': 11,
        'axes.labelsize': 13,
        'axes.titlesize': 14,
        'legend.fontsize': 10,
        'xtick.labelsize': 10,
        'ytick.labelsize': 10,
        'figure.dpi': 150,
        'savefig.dpi': 300,
        'savefig.bbox': 'tight',
        'savefig.pad_inches': 0.1,
        'axes.grid': True,
        'grid.alpha': 0.3,
        'grid.linestyle': '--',
        'axes.spines.top': False,
        'axes.spines.right': False,
    })


def get_color(algo_name: str) -> str:
    """获取算法配色"""
    for key, color in COLORS.items():
        if key.lower() in algo_name.lower():
            return color
    # fallback: hash-based color
    import hashlib
    h = int(hashlib.md5(algo_name.encode()).hexdigest()[:6], 16)
    return f'#{(h % 0xFFFFFF):06x}'


# ==================== 收敛曲线图 ====================

def plot_convergence_curves(
    data: Dict[str, Dict],  # algo_name -> {N: (means, stds)} per scale
    task_scales: List[int],
    output_path: str,
    title: str = "Convergence Analysis",
    xlabel: str = "Function Evaluations",
    ylabel: str = "Best-so-far Makespan (s)",
    y_log: bool = False,
):
    """
    绘制多算法收敛曲线 (分 scale 子图)。

    data 格式:
        {'LSCBO': {200: (mean_array, std_array), 500: ...}, ...}
    """
    setup_paper_style(figsize=(14, 3 * len(task_scales)))

    n_scales = len(task_scales)
    fig, axes = plt.subplots(1, n_scales, figsize=(4 * n_scales, 3.5), squeeze=False)
    axes = axes[0]

    for idx, N in enumerate(task_scales):
        ax = axes[idx]
        for algo_name, scales_data in data.items():
            if N in scales_data:
                means, stds = scales_data[N]
                x = np.arange(len(means))
                color = get_color(algo_name)
                ax.plot(x, means, color=color, linewidth=1.5, label=algo_name)
                ax.fill_between(x, means - stds, means + stds, color=color, alpha=0.1)

        ax.set_title(f"N = {N}")
        ax.set_xlabel(xlabel)
        if idx == 0:
            ax.set_ylabel(ylabel)
        if y_log:
            ax.set_yscale('log')
        ax.legend(fontsize=8, loc='best')

    fig.suptitle(title, fontsize=14, fontweight='bold')
    plt.tight_layout()
    plt.savefig(output_path)
    plt.close()
    print(f"  → 收敛曲线图已保存: {output_path}")


# ==================== 排名热力图 ====================

def plot_rank_heatmap(
    rank_matrix: np.ndarray,   # (n_configs, n_algorithms)
    config_names: List[str],
    algo_names: List[str],
    output_path: str,
    title: str = "Algorithm Rank Stability Across Configurations",
    cmap: str = 'RdYlGn_r',
):
    """绘制跨配置的排名稳定性热力图"""
    setup_paper_style(figsize=(len(algo_names) * 0.8 + 3, len(config_names) * 0.5 + 2))

    fig, ax = plt.subplots()
    im = ax.imshow(rank_matrix, cmap=cmap, aspect='auto', vmin=1, vmax=len(algo_names))

    ax.set_xticks(range(len(algo_names)))
    ax.set_xticklabels(algo_names, rotation=45, ha='right')
    ax.set_yticks(range(len(config_names)))
    ax.set_yticklabels(config_names)
    ax.set_title(title, fontweight='bold')

    # 标注排名值
    for i in range(len(config_names)):
        for j in range(len(algo_names)):
            text = ax.text(j, i, f'{rank_matrix[i, j]:.1f}',
                          ha='center', va='center', fontsize=10,
                          fontweight='bold',
                          color='white' if rank_matrix[i, j] > len(algo_names) / 2 else 'black')

    cbar = plt.colorbar(im, ax=ax)
    cbar.set_label('Average Rank (lower = better)')
    plt.tight_layout()
    plt.savefig(output_path)
    plt.close()
    print(f"  → 排名热力图已保存: {output_path}")


# ==================== 散点图（相关性分析） ====================

def plot_scatter_correlation(
    x: np.ndarray, y: np.ndarray,
    xlabel: str, ylabel: str,
    output_path: str,
    title: str = "",
    groups: Optional[np.ndarray] = None,
    group_labels: Optional[List[str]] = None,
):
    """绘制散点图 + 回归线"""
    setup_paper_style(figsize=(7, 5))

    fig, ax = plt.subplots()

    if groups is not None:
        for i, label in enumerate(group_labels or []):
            mask = groups == i
            ax.scatter(x[mask], y[mask], alpha=0.4, s=15, label=label)
        ax.legend()
    else:
        ax.scatter(x, y, alpha=0.3, s=15, color='#1f77b4')

    # 回归线
    from numpy.polynomial.polynomial import polyfit
    mask = ~(np.isnan(x) | np.isnan(y))
    b, m = polyfit(x[mask], y[mask], 1)
    x_line = np.linspace(x[mask].min(), x[mask].max(), 100)
    ax.plot(x_line, b + m * x_line, 'r--', linewidth=1.5, alpha=0.8)

    # 标注相关系数
    pears_r = np.corrcoef(x[mask], y[mask])[0, 1]
    from scipy import stats
    spear_r, _ = stats.spearmanr(x[mask], y[mask])
    ax.text(0.05, 0.95, f'Pearson r = {pears_r:.4f}\nSpearman ρ = {spear_r:.4f}',
            transform=ax.transAxes, verticalalignment='top',
            bbox=dict(boxstyle='round', facecolor='wheat', alpha=0.5),
            fontsize=10)

    ax.set_xlabel(xlabel)
    ax.set_ylabel(ylabel)
    if title:
        ax.set_title(title)
    plt.tight_layout()
    plt.savefig(output_path)
    plt.close()
    print(f"  → 散点图已保存: {output_path}")


# ==================== 分组柱状图 ====================

def plot_grouped_bars(
    data: pd.DataFrame,       # columns: Config, Algorithm, Value
    x_col: str,
    y_col: str,
    hue_col: str,
    output_path: str,
    title: str = "",
    ylabel: str = "",
):
    """绘制分组柱状图"""
    import seaborn as sns
    setup_paper_style(figsize=(10, 5))

    fig, ax = plt.subplots()
    sns.barplot(data=data, x=x_col, y=y_col, hue=hue_col, ax=ax,
                palette='Set2', edgecolor='black', linewidth=0.5)

    ax.set_xlabel(x_col)
    ax.set_ylabel(ylabel)
    if title:
        ax.set_title(title)
    ax.legend(loc='best', title=hue_col)
    plt.tight_layout()
    plt.savefig(output_path)
    plt.close()
    print(f"  → 柱状图已保存: {output_path}")


# ==================== 效应量森林图 ====================

def plot_effect_size_forest(
    effects: List[float],
    labels: List[str],
    output_path: str,
    title: str = "Effect Size (Cohen's d)",
):
    """绘制效应量森林图"""
    setup_paper_style(figsize=(8, len(effects) * 0.4 + 2))

    fig, ax = plt.subplots()
    y_pos = range(len(effects))
    colors = ['#2ca02c' if e > 0 else '#d62728' for e in effects]

    ax.barh(y_pos, effects, color=colors, alpha=0.7, height=0.5)
    ax.axvline(x=0, color='black', linestyle='-', linewidth=0.8)
    ax.axvline(x=0.2, color='gray', linestyle='--', alpha=0.5, label='Small (|d|=0.2)')
    ax.axvline(x=-0.2, color='gray', linestyle='--', alpha=0.5)
    ax.axvline(x=0.5, color='gray', linestyle=':', alpha=0.5, label='Medium (|d|=0.5)')
    ax.axvline(x=-0.5, color='gray', linestyle=':', alpha=0.5)
    ax.axvline(x=0.8, color='gray', linestyle='-.', alpha=0.5, label='Large (|d|=0.8)')
    ax.axvline(x=-0.8, color='gray', linestyle='-.', alpha=0.5)

    ax.set_yticks(y_pos)
    ax.set_yticklabels(labels)
    ax.set_xlabel("Cohen's d")
    ax.set_title(title)
    ax.legend(loc='best', fontsize=8)
    ax.invert_yaxis()
    plt.tight_layout()
    plt.savefig(output_path)
    plt.close()
    print(f"  → 效应量图已保存: {output_path}")


# ==================== 多配置排名稳定性矩阵 ====================

def plot_rank_stability_matrix(
    rank_matrices: List[np.ndarray],  # [(n_algo,), ...] per config
    config_names: List[str],
    algo_names: List[str],
    output_path: str,
    title: str = "Rank Stability Matrix (Spearman ρ)",
):
    """绘制配置间排名稳定性矩阵"""
    setup_paper_style(figsize=(len(config_names) * 1.2, len(config_names) * 1.2))

    n = len(config_names)
    rho_matrix = np.zeros((n, n))
    for i in range(n):
        for j in range(n):
            rho, _ = scipy_stats.spearmanr(rank_matrices[i], rank_matrices[j])
            rho_matrix[i, j] = rho

    fig, ax = plt.subplots()
    im = ax.imshow(rho_matrix, cmap='RdYlGn', aspect='auto', vmin=0.0, vmax=1.0)

    ax.set_xticks(range(n))
    ax.set_xticklabels(config_names, rotation=45, ha='right')
    ax.set_yticks(range(n))
    ax.set_yticklabels(config_names)
    ax.set_title(title, fontweight='bold')

    for i in range(n):
        for j in range(n):
            ax.text(j, i, f'{rho_matrix[i, j]:.3f}', ha='center', va='center', fontsize=8)

    cbar = plt.colorbar(im, ax=ax)
    cbar.set_label("Spearman ρ")
    plt.tight_layout()
    plt.savefig(output_path)
    plt.close()
    print(f"  → 稳定性矩阵已保存: {output_path}")

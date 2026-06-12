#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
LSCBO 审稿补充实验 —— 共享统计检验函数

包含：
  - Friedman 检验 + Nemenyi 事后检验
  - Wilcoxon 配对检验
  - Spearman 排名相关性
  - Cohen's d 效应量
  - Bonferroni / Holm 多重比较校正
"""

import numpy as np
import pandas as pd
from scipy import stats as scipy_stats
from typing import List, Dict, Tuple, Optional


# ==================== Friedman Test ====================

def friedman_test(data: np.ndarray) -> Dict:
    """
    执行 Friedman 检验并计算平均排名。

    参数
    ----
    data : np.ndarray, shape (n_algorithms, n_problems)
        行 = 算法, 列 = 问题实例

    返回
    ----
    dict : {'chi2': ..., 'p_value': ..., 'avg_ranks': ..., 'is_significant': ...}
    """
    n_algo, n_problem = data.shape

    # 计算 per-problem 排名 (值越小排名越前, 排名从1开始)
    ranks = np.zeros_like(data)
    for j in range(n_problem):
        col = data[:, j]
        # argsort 升序 -> 最小值的索引
        order = np.argsort(col)
        rank_vals = np.empty(n_algo)
        # 处理并列：平均排名
        i = 0
        while i < n_algo:
            j2 = i
            while j2 < n_algo and col[order[j2]] == col[order[i]]:
                j2 += 1
            avg_rank = (i + 1 + j2) / 2.0  # rank 从 1 开始
            for k in range(i, j2):
                rank_vals[order[k]] = avg_rank
            i = j2
        ranks[:, j] = rank_vals

    # 平均排名
    avg_ranks = ranks.mean(axis=1)

    # Friedman χ² 统计量
    R_sum_sq = (ranks.sum(axis=1) ** 2).sum()
    chi2 = (12.0 * n_problem) / (n_algo * (n_algo + 1.0)) * R_sum_sq - 3.0 * n_problem * (n_algo + 1.0)

    # p-value (χ² 近似, df = n_algo - 1)
    p_value = 1.0 - scipy_stats.chi2.cdf(chi2, n_algo - 1)

    return {
        'chi2': chi2,
        'p_value': p_value,
        'avg_ranks': avg_ranks,
        'ranks': ranks,
        'is_significant': p_value < 0.05,
        'n_algo': n_algo,
        'n_problem': n_problem,
    }


def nemenyi_cd(n_algo: int, n_problem: int, alpha: float = 0.05) -> float:
    """
    计算 Nemenyi 检验的临界差值 (Critical Difference).

    来源: Demsar (2006), JMLR 7:1-30.
    """
    # q_alpha 表 (alpha=0.05)
    q_table_005 = {2: 1.960, 3: 2.343, 4: 2.569, 5: 2.728, 6: 2.850,
                   7: 2.949, 8: 3.031, 9: 3.102, 10: 3.164}
    q_table_010 = {2: 1.645, 3: 2.052, 4: 2.291, 5: 2.459, 6: 2.589,
                   7: 2.693, 8: 2.780, 9: 2.855, 10: 2.920}

    if alpha == 0.05 and n_algo in q_table_005:
        q_alpha = q_table_005[n_algo]
    elif alpha == 0.10 and n_algo in q_table_010:
        q_alpha = q_table_010[n_algo]
    else:
        z = scipy_stats.norm.ppf(1 - alpha / 2)
        q_alpha = z * np.sqrt(2)

    return q_alpha * np.sqrt(n_algo * (n_algo + 1) / (6.0 * n_problem))


def wilcoxon_paired(baseline: np.ndarray, improved: np.ndarray) -> Dict:
    """Wilcoxon 符号秩检验 (配对)"""
    stat, p = scipy_stats.wilcoxon(baseline, improved)
    return {'statistic': stat, 'p_value': p, 'is_significant': p < 0.05}


def cohens_d(baseline: np.ndarray, improved: np.ndarray) -> float:
    """Cohen's d 效应量 (paired)"""
    diff = baseline - improved
    d = np.mean(diff) / np.std(diff, ddof=1)
    return d


def spearman_rank_correlation(ranks_a: np.ndarray, ranks_b: np.ndarray) -> Dict:
    """Spearman 排名相关系数"""
    rho, p = scipy_stats.spearmanr(ranks_a, ranks_b)
    return {'rho': rho, 'p_value': p, 'is_significant': p < 0.05}


def holm_correction(p_values: List[float]) -> np.ndarray:
    """Holm 多重比较校正"""
    p = np.array(p_values)
    order = np.argsort(p)
    m = len(p)
    corrected = np.ones(m)
    for i, idx in enumerate(order):
        corrected[idx] = min(p[idx] * (m - i), 1.0)
    return corrected


def bonferroni_correction(p_values: List[float]) -> np.ndarray:
    """Bonferroni 多重比较校正"""
    p = np.array(p_values)
    return np.minimum(p * len(p), 1.0)


# ==================== 格式化输出 ====================

def format_p_value(p: float) -> str:
    """格式化 p-value"""
    if p < 1e-10:
        return "< 1e-10"
    elif p < 0.001:
        return f"{p:.2e}"
    elif p < 0.05:
        return f"{p:.4f} *"
    else:
        return f"{p:.4f}"


def print_friedman_table(result: Dict, algo_names: List[str]):
    """打印 Friedman 检验结果表"""
    print("\n" + "=" * 70)
    print("  Friedman Test Results")
    print("=" * 70)
    print(f"  χ² = {result['chi2']:.4f}")
    print(f"  df = {result['n_algo'] - 1}")
    print(f"  p-value = {format_p_value(result['p_value'])}")
    print(f"  Significant: {'Yes' if result['is_significant'] else 'No'}")
    print()

    # CD
    cd = nemenyi_cd(result['n_algo'], result['n_problem'])
    print(f"  Nemenyi Critical Difference (α=0.05): {cd:.4f}")
    print()

    # 排名表
    rank_order = np.argsort(result['avg_ranks'])
    print(f"  {'Algorithm':<20} {'Avg Rank':<12} {'Rank':<6}")
    print("  " + "-" * 38)
    for i, idx in enumerate(rank_order):
        marker = "*" if i == 0 else ""
        print(f"  {algo_names[idx]:<20} {result['avg_ranks'][idx]:.4f}       {i+1}{marker}")


if __name__ == "__main__":
    # 测试
    np.random.seed(42)
    data = np.random.randn(5, 8)  # 5 algorithms, 8 problems
    result = friedman_test(data)
    print_friedman_table(result, [f"Algo_{i}" for i in range(5)])

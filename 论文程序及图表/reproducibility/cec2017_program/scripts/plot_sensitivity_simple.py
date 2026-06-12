#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
参数敏感性热力图绘制脚本（简化版）
生成中英文版本的热力图
"""

import pandas as pd
import matplotlib.pyplot as plt
import seaborn as sns
import sys

# 设置matplotlib参数
plt.rcParams['font.size'] = 12
plt.rcParams['figure.dpi'] = 300

def plot_sensitivity_heatmap(csv_file, lang='en'):
    """
    绘制参数敏感性热力图

    Args:
        csv_file: CSV文件路径
        lang: 语言 ('en' 或 'zh')
    """
    # 读取数据
    df = pd.read_csv(csv_file)
    print(f"Loaded data: {len(df)} records")
    print(f"k range: {sorted(df['k'].unique())}")
    print(f"lambda range: {sorted(df['lambda'].unique())}")

    # 创建k×lambda矩阵
    pivot = df.pivot(index='lambda', columns='k', values='MeanMakespan')

    # 创建图表
    fig, ax = plt.subplots(figsize=(10, 8))

    # 绘制热力图
    sns.heatmap(pivot,
                annot=True,
                fmt='.2f',
                cmap='RdYlGn_r',  # 红=差，绿=好
                cbar_kws={'label': 'Mean Makespan'},
                linewidths=0.5,
                ax=ax)

    # 设置标题和标签（根据语言）
    if lang == 'zh':
        title = 'ICBO-Enhanced 参数敏感性分析（k × λ）'
        xlabel = 'k (动态权重衰减指数)'
        ylabel = 'λ (Bernoulli混沌参数)'
    else:
        title = 'ICBO-Enhanced Parameter Sensitivity Analysis (k × λ)'
        xlabel = 'k (Dynamic Weight Decay Exponent)'
        ylabel = 'λ (Bernoulli Chaotic Parameter)'

    ax.set_title(title, fontsize=16, fontweight='bold', pad=20)
    ax.set_xlabel(xlabel, fontsize=14, fontweight='bold')
    ax.set_ylabel(ylabel, fontsize=14, fontweight='bold')

    # 标注最优配置
    best_idx = pivot.stack().idxmin()
    best_lambda, best_k = best_idx
    print(f"\nBest configuration: k={best_k}, lambda={best_lambda}")
    print(f"Best Makespan: {pivot.loc[best_lambda, best_k]:.2f}")

    # 保存图表
    output_file = f'results/parameter_sensitivity_heatmap_{lang}.png'
    plt.tight_layout()
    plt.savefig(output_file, dpi=300, bbox_inches='tight')
    print(f"Saved: {output_file}")
    plt.close()

if __name__ == '__main__':
    csv_file = 'results/sensitivity_results_20251210_223839.csv'

    print("="*70)
    print("Parameter Sensitivity Heatmap Generator".center(70))
    print("="*70 + "\n")

    try:
        # 生成英文版
        print("[1/2] Generating English version...")
        plot_sensitivity_heatmap(csv_file, lang='en')

        # 生成中文版
        print("\n[2/2] Generating Chinese version...")
        plot_sensitivity_heatmap(csv_file, lang='zh')

        print("\n" + "="*70)
        print("All Done!".center(70))
        print("="*70)

    except FileNotFoundError as e:
        print(f"Error: {e}")
        print("Please ensure sensitivity_results_*.csv exists in results/ directory")
    except Exception as e:
        print(f"Error: {e}")
        import traceback
        traceback.print_exc()

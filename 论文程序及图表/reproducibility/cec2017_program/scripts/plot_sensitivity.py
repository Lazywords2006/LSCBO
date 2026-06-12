#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
参数敏感性热力图绘制脚本 - plot_sensitivity.py

功能：
1. 读取sensitivity_results_*.csv文件
2. 绘制k×λ热力图（Mean Makespan）
3. 生成论文级别的高质量图片（300 DPI）

使用方法：
    python plot_sensitivity.py

输出：
    - sensitivity_heatmap_mean.png  # 平均Makespan热力图
    - sensitivity_heatmap_std.png   # 标准差热力图
    - sensitivity_heatmap_cv.png    # 变异系数热力图

依赖：
    pip install matplotlib pandas numpy seaborn
"""

import os
import glob
import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
import seaborn as sns
from pathlib import Path

# 配置matplotlib中文显示
plt.rcParams['font.sans-serif'] = ['SimHei', 'DejaVu Sans']
plt.rcParams['axes.unicode_minus'] = False


class SensitivityPlotter:
    """参数敏感性热力图绘制器"""

    def __init__(self, data_file=None, output_dir="results"):
        """
        初始化绘图器

        Args:
            data_file: CSV文件路径（如果为None，自动查找最新文件）
            output_dir: 输出目录
        """
        self.output_dir = Path(output_dir)
        self.output_dir.mkdir(exist_ok=True)

        # 自动查找最新的sensitivity_results文件
        if data_file is None:
            files = glob.glob("sensitivity_results_*.csv")
            if not files:
                raise FileNotFoundError("未找到sensitivity_results_*.csv文件")
            data_file = max(files, key=os.path.getctime)
            print(f"✓ 自动选择文件: {data_file}")

        self.data_file = data_file
        self.data = None

    def load_data(self):
        """加载CSV数据"""
        self.data = pd.read_csv(self.data_file)
        print(f"✓ 加载数据: {len(self.data)} 条记录")
        print(f"  k范围: {self.data['k'].unique()}")
        print(f"  λ范围: {self.data['lambda'].unique()}")
        return self.data

    def plot_heatmap(self, metric='MeanMakespan', title=None, filename=None):
        """
        绘制参数敏感性热力图

        Args:
            metric: 要绘制的指标（MeanMakespan/StdMakespan/CV）
            title: 图表标题
            filename: 输出文件名
        """
        if self.data is None:
            self.load_data()

        # 创建k×λ矩阵
        pivot_table = self.data.pivot(index='lambda', columns='k', values=metric)

        # 创建图表
        fig, ax = plt.subplots(figsize=(10, 8))

        # 绘制热力图
        sns.heatmap(pivot_table,
                    annot=True,  # 显示数值
                    fmt='.2f',   # 数值格式
                    cmap='RdYlGn_r',  # 颜色映射（红色=差，黄色=中，绿色=好）
                    cbar_kws={'label': metric},
                    linewidths=0.5,
                    ax=ax)

        # 设置标题和标签
        if title is None:
            title = f'Parameter Sensitivity Analysis - {metric}'
        ax.set_title(title, fontsize=16, fontweight='bold', pad=20)
        ax.set_xlabel('k (Dynamic Weight Decay Exponent)', fontsize=14, fontweight='bold')
        ax.set_ylabel('λ (Bernoulli Chaotic Parameter)', fontsize=14, fontweight='bold')

        # 标记默认配置（k=3, λ=0.4）
        self._mark_default_config(ax, pivot_table)

        # 保存图片
        if filename is None:
            metric_name = metric.lower().replace('makespan', '')
            filename = f"sensitivity_heatmap_{metric_name}.png"

        output_path = self.output_dir / filename
        plt.tight_layout()
        plt.savefig(output_path, dpi=300, bbox_inches='tight')
        plt.close()

        print(f"✓ 生成热力图: {output_path}")

    def _mark_default_config(self, ax, pivot_table):
        """在热力图上标记默认配置（k=3, λ=0.4）"""
        try:
            # 找到k=3, λ=0.4的位置
            k_index = list(pivot_table.columns).index(3)
            lambda_index = list(pivot_table.index).index(0.4)

            # 绘制红色边框
            rect = plt.Rectangle((k_index, lambda_index), 1, 1,
                                 fill=False, edgecolor='red',
                                 linewidth=3, linestyle='--')
            ax.add_patch(rect)

            # 添加文本标注
            ax.text(k_index + 0.5, lambda_index + 0.5,
                   '★',  # 星号标记
                   ha='center', va='center',
                   color='red', fontsize=24, fontweight='bold')
        except (ValueError, KeyError):
            pass  # 如果找不到默认配置，跳过标记

    def plot_all_metrics(self):
        """生成所有指标的热力图"""
        print("\n" + "="*70)
        print("生成参数敏感性热力图".center(70))
        print("="*70 + "\n")

        if self.data is None:
            self.load_data()

        # 1. 平均Makespan热力图
        print("步骤1: 生成平均Makespan热力图...")
        self.plot_heatmap(
            metric='MeanMakespan',
            title='Parameter Sensitivity: Mean Makespan (Lower is Better)',
            filename='sensitivity_heatmap_mean.png'
        )

        # 2. 标准差热力图
        print("\n步骤2: 生成标准差热力图...")
        self.plot_heatmap(
            metric='StdMakespan',
            title='Parameter Sensitivity: Std Makespan (Lower is Better)',
            filename='sensitivity_heatmap_std.png'
        )

        # 3. 变异系数热力图
        print("\n步骤3: 生成变异系数热力图...")
        self.plot_heatmap(
            metric='CV',
            title='Parameter Sensitivity: Coefficient of Variation (Lower is Better)',
            filename='sensitivity_heatmap_cv.png'
        )

    def generate_analysis_report(self):
        """生成敏感性分析报告"""
        if self.data is None:
            self.load_data()

        report_lines = [
            "# 参数敏感性分析报告\n",
            f"生成时间: {pd.Timestamp.now().strftime('%Y-%m-%d %H:%M:%S')}\n",
            f"数据文件: {self.data_file}\n\n",

            "## 1. 参数配置\n",
            f"- k（动态权重衰减指数）: {sorted(self.data['k'].unique())}\n",
            f"- λ（Bernoulli混沌参数）: {sorted(self.data['lambda'].unique())}\n",
            f"- 总配置数: {len(self.data)}\n\n",

            "## 2. 最佳配置（按平均Makespan排序）\n",
            "| 排名 | k | λ | Mean Makespan | Std | CV% |\n",
            "|------|---|---|---------------|-----|-----|\n"
        ]

        # 按Mean Makespan排序
        sorted_data = self.data.sort_values('MeanMakespan')

        # Top 5配置
        for i, row in sorted_data.head(5).iterrows():
            report_lines.append(
                f"| {i+1} | {int(row['k'])} | {row['lambda']:.1f} | "
                f"{row['MeanMakespan']:.2f} | {row['StdMakespan']:.2f} | "
                f"{row['CV']:.2f}% |\n"
            )

        # 默认配置分析
        default_config = self.data[(self.data['k'] == 3) &
                                   (self.data['lambda'].between(0.39, 0.41))]
        if not default_config.empty:
            row = default_config.iloc[0]
            rank = (sorted_data.index == default_config.index[0]).argmax() + 1

            report_lines.extend([
                "\n## 3. 默认配置 (k=3, λ=0.4) 分析\n",
                f"- 排名: {rank}/{len(self.data)}\n",
                f"- Mean Makespan: {row['MeanMakespan']:.2f}\n",
                f"- Std Makespan: {row['StdMakespan']:.2f}\n",
                f"- CV: {row['CV']:.2f}%\n",
                f"- Min: {row['MinMakespan']:.2f}\n",
                f"- Max: {row['MaxMakespan']:.2f}\n\n"
            ])

        # 参数影响分析
        report_lines.extend([
            "## 4. 参数影响分析\n\n",
            "### 4.1 k参数影响\n"
        ])

        for k_val in sorted(self.data['k'].unique()):
            k_data = self.data[self.data['k'] == k_val]
            mean_perf = k_data['MeanMakespan'].mean()
            report_lines.append(f"- k={k_val}: 平均Makespan={mean_perf:.2f}\n")

        report_lines.append("\n### 4.2 λ参数影响\n")

        for lambda_val in sorted(self.data['lambda'].unique()):
            lambda_data = self.data[self.data['lambda'] == lambda_val]
            mean_perf = lambda_data['MeanMakespan'].mean()
            report_lines.append(f"- λ={lambda_val:.1f}: 平均Makespan={mean_perf:.2f}\n")

        # 保存报告
        report_file = self.output_dir / "sensitivity_analysis_report.md"
        with open(report_file, 'w', encoding='utf-8') as f:
            f.writelines(report_lines)

        print(f"\n✓ 生成分析报告: {report_file}")

    def plot_parameter_trends(self):
        """绘制参数趋势线图"""
        if self.data is None:
            self.load_data()

        fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(16, 6))

        # 1. k参数趋势
        for lambda_val in sorted(self.data['lambda'].unique()):
            lambda_data = self.data[self.data['lambda'] == lambda_val]
            ax1.plot(lambda_data['k'], lambda_data['MeanMakespan'],
                    marker='o', label=f'λ={lambda_val:.1f}')

        ax1.set_xlabel('k (Dynamic Weight Decay Exponent)', fontsize=12, fontweight='bold')
        ax1.set_ylabel('Mean Makespan', fontsize=12, fontweight='bold')
        ax1.set_title('Impact of k Parameter', fontsize=14, fontweight='bold')
        ax1.legend()
        ax1.grid(True, alpha=0.3)

        # 2. λ参数趋势
        for k_val in sorted(self.data['k'].unique()):
            k_data = self.data[self.data['k'] == k_val]
            ax2.plot(k_data['lambda'], k_data['MeanMakespan'],
                    marker='s', label=f'k={k_val}')

        ax2.set_xlabel('λ (Bernoulli Chaotic Parameter)', fontsize=12, fontweight='bold')
        ax2.set_ylabel('Mean Makespan', fontsize=12, fontweight='bold')
        ax2.set_title('Impact of λ Parameter', fontsize=14, fontweight='bold')
        ax2.legend()
        ax2.grid(True, alpha=0.3)

        # 保存图片
        output_path = self.output_dir / "sensitivity_trends.png"
        plt.tight_layout()
        plt.savefig(output_path, dpi=300, bbox_inches='tight')
        plt.close()

        print(f"✓ 生成趋势图: {output_path}")


def main():
    """主函数"""
    print("\n" + "="*70)
    print("参数敏感性热力图绘制工具".center(70))
    print("="*70 + "\n")

    try:
        # 初始化绘图器
        plotter = SensitivityPlotter(output_dir="results")

        # 加载数据
        plotter.load_data()

        # 生成所有热力图
        plotter.plot_all_metrics()

        # 生成趋势图
        print("\n步骤4: 生成参数趋势图...")
        plotter.plot_parameter_trends()

        # 生成分析报告
        print("\n步骤5: 生成敏感性分析报告...")
        plotter.generate_analysis_report()

        print("\n" + "="*70)
        print("✅ 全部完成！".center(70))
        print("="*70 + "\n")

    except FileNotFoundError as e:
        print(f"❌ 错误: {e}")
        print("请先运行ParameterSensitivityTest.java生成数据文件")
    except Exception as e:
        print(f"❌ 发生错误: {e}")
        import traceback
        traceback.print_exc()


if __name__ == "__main__":
    main()

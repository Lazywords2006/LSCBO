#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
收敛曲线绘图脚本 - plot_convergence.py

功能：
1. 读取收敛曲线CSV文件（convergence_[算法]_[规模]_seed[种子].csv）
2. 绘制单个算法的收敛曲线
3. 绘制多个算法的对比曲线（含均值±标准差置信区间）
4. 生成论文级别的高质量图片（300 DPI）

使用方法：
    python plot_convergence.py

输出：
    - convergence_comparison_M100.png  # 所有算法对比图
    - convergence_individual_CBO_M100.png  # 单个算法详细图
    - convergence_individual_ICBO-Enhanced_M100.png

依赖：
    pip install matplotlib pandas numpy
"""

import os
import glob
import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
from pathlib import Path

# 配置matplotlib中文显示
plt.rcParams['font.sans-serif'] = ['SimHei', 'DejaVu Sans']  # 中文字体
plt.rcParams['axes.unicode_minus'] = False  # 解决负号显示问题


class ConvergencePlotter:
    """收敛曲线绘图器"""

    def __init__(self, data_dir=".", output_dir="results"):
        """
        初始化绘图器

        Args:
            data_dir: CSV文件所在目录
            output_dir: 输出图片目录
        """
        self.data_dir = Path(data_dir)
        self.output_dir = Path(output_dir)
        self.output_dir.mkdir(exist_ok=True)

        # 算法配色方案（区分度高的颜色）
        self.colors = {
            'Random': '#95a5a6',      # 灰色 - 基线
            'CBO': '#3498db',         # 蓝色 - 基础算法
            'ICBO-Enhanced': '#e74c3c',  # 红色 - 改进算法（突出）
            'GWO': '#f39c12',         # 橙色
            'WOA': '#9b59b6',         # 紫色
            'PSO': '#2ecc71',         # 绿色
        }

        # 线型
        self.linestyles = {
            'Random': '--',
            'CBO': '-',
            'ICBO-Enhanced': '-',
            'GWO': '-.',
            'WOA': ':',
            'PSO': '-',
        }

    def load_convergence_data(self, algorithm, scale):
        """
        加载指定算法和规模的所有种子收敛数据

        Args:
            algorithm: 算法名称（如"CBO"）
            scale: 规模（如"M100"）

        Returns:
            DataFrame包含列：Iteration, BestFitness, Seed
        """
        pattern = f"convergence_{algorithm}_{scale}_seed*.csv"
        files = list(self.data_dir.glob(pattern))

        if not files:
            print(f"⚠️  未找到文件: {pattern}")
            return None

        all_data = []
        for file in files:
            # 从文件名提取种子
            seed = int(file.stem.split('seed')[-1])
            df = pd.read_csv(file)
            df['Seed'] = seed
            all_data.append(df)

        combined = pd.concat(all_data, ignore_index=True)
        print(f"✓ 加载 {algorithm}-{scale}: {len(files)} seeds, {len(combined)} 数据点")
        return combined

    def plot_single_algorithm(self, algorithm, scale):
        """
        绘制单个算法的收敛曲线（含多条种子曲线）

        Args:
            algorithm: 算法名称
            scale: 规模
        """
        data = self.load_convergence_data(algorithm, scale)
        if data is None:
            return

        fig, ax = plt.subplots(figsize=(10, 6))

        # 绘制每个种子的曲线（半透明）
        for seed in data['Seed'].unique():
            seed_data = data[data['Seed'] == seed]
            ax.plot(seed_data['Iteration'], seed_data['BestFitness'],
                   alpha=0.3, linewidth=1, color=self.colors.get(algorithm, '#000000'))

        # 计算并绘制均值曲线（加粗）
        mean_data = data.groupby('Iteration')['BestFitness'].mean()
        std_data = data.groupby('Iteration')['BestFitness'].std()

        ax.plot(mean_data.index, mean_data.values,
               label=f'{algorithm} Mean',
               linewidth=2.5,
               color=self.colors.get(algorithm, '#000000'))

        # 添加置信区间阴影（±1 std）
        ax.fill_between(mean_data.index,
                        mean_data - std_data,
                        mean_data + std_data,
                        alpha=0.2,
                        color=self.colors.get(algorithm, '#000000'),
                        label='±1 Std')

        # 图表美化
        ax.set_xlabel('Iteration', fontsize=12)
        ax.set_ylabel('Best Makespan', fontsize=12)
        ax.set_title(f'{algorithm} Convergence Curve - {scale}', fontsize=14, fontweight='bold')
        ax.legend(loc='upper right', fontsize=10)
        ax.grid(True, alpha=0.3, linestyle='--')

        # 保存图片
        output_file = self.output_dir / f"convergence_individual_{algorithm}_{scale}.png"
        plt.tight_layout()
        plt.savefig(output_file, dpi=300, bbox_inches='tight')
        plt.close()

        print(f"✓ 生成单算法图: {output_file}")

    def plot_comparison(self, algorithms, scale):
        """
        绘制多个算法的收敛对比图

        Args:
            algorithms: 算法列表（如["CBO", "ICBO-Enhanced"]）
            scale: 规模
        """
        fig, ax = plt.subplots(figsize=(12, 7))

        for algorithm in algorithms:
            data = self.load_convergence_data(algorithm, scale)
            if data is None:
                continue

            # 计算均值和标准差
            mean_data = data.groupby('Iteration')['BestFitness'].mean()
            std_data = data.groupby('Iteration')['BestFitness'].std()

            # 绘制均值曲线
            ax.plot(mean_data.index, mean_data.values,
                   label=algorithm,
                   linewidth=2.5,
                   linestyle=self.linestyles.get(algorithm, '-'),
                   color=self.colors.get(algorithm, '#000000'))

            # 添加置信区间阴影
            ax.fill_between(mean_data.index,
                            mean_data - std_data,
                            mean_data + std_data,
                            alpha=0.15,
                            color=self.colors.get(algorithm, '#000000'))

        # 图表美化
        ax.set_xlabel('Iteration', fontsize=14, fontweight='bold')
        ax.set_ylabel('Best Makespan', fontsize=14, fontweight='bold')
        ax.set_title(f'Algorithm Convergence Comparison - {scale}',
                    fontsize=16, fontweight='bold')
        ax.legend(loc='upper right', fontsize=12, framealpha=0.9)
        ax.grid(True, alpha=0.3, linestyle='--')

        # 保存图片
        output_file = self.output_dir / f"convergence_comparison_{scale}.png"
        plt.tight_layout()
        plt.savefig(output_file, dpi=300, bbox_inches='tight')
        plt.close()

        print(f"✓ 生成对比图: {output_file}")

    def generate_convergence_report(self, algorithms, scale):
        """
        生成收敛性能报告（Markdown格式）

        Args:
            algorithms: 算法列表
            scale: 规模
        """
        report_lines = [
            f"# 收敛曲线分析报告 - {scale}\n",
            f"生成时间: {pd.Timestamp.now().strftime('%Y-%m-%d %H:%M:%S')}\n",
            "\n## 算法收敛性能对比\n",
            "| 算法 | 初始Makespan | 最终Makespan | 总改进率 | 收敛速度(50%) |\n",
            "|------|-------------|-------------|---------|---------------|\n"
        ]

        for algorithm in algorithms:
            data = self.load_convergence_data(algorithm, scale)
            if data is None:
                continue

            # 计算统计指标
            mean_data = data.groupby('Iteration')['BestFitness'].mean()
            initial = mean_data.iloc[0]
            final = mean_data.iloc[-1]
            improvement = (initial - final) / initial * 100

            # 收敛速度：前50%迭代的改进比例
            half_point = len(mean_data) // 2
            half_improvement = (initial - mean_data.iloc[half_point]) / (initial - final) * 100

            report_lines.append(
                f"| {algorithm} | {initial:.2f} | {final:.2f} | "
                f"{improvement:.2f}% | {half_improvement:.2f}% |\n"
            )

        report_lines.append("\n## 图表说明\n")
        report_lines.append("- 实线：算法平均收敛曲线\n")
        report_lines.append("- 阴影区域：±1标准差置信区间\n")
        report_lines.append("- 迭代次数：100次（MAX_ITERATIONS）\n")

        # 保存报告
        report_file = self.output_dir / f"convergence_report_{scale}.md"
        with open(report_file, 'w', encoding='utf-8') as f:
            f.writelines(report_lines)

        print(f"✓ 生成分析报告: {report_file}")


def main():
    """主函数"""
    print("\n" + "="*70)
    print("收敛曲线绘图工具".center(70))
    print("="*70 + "\n")

    # 初始化绘图器
    plotter = ConvergencePlotter(data_dir=".", output_dir="results")

    # 配置：要绘制的算法和规模
    algorithms = ["Random", "GWO", "WOA", "CBO", "ICBO-Enhanced"]
    scale = "M100"  # 可根据实验修改

    print(f"目标规模: {scale}")
    print(f"对比算法: {', '.join(algorithms)}\n")

    # 1. 绘制每个算法的单独图
    print("步骤1: 生成单算法收敛曲线...")
    for algorithm in algorithms:
        plotter.plot_single_algorithm(algorithm, scale)

    # 2. 绘制算法对比图
    print("\n步骤2: 生成算法对比图...")
    plotter.plot_comparison(algorithms, scale)

    # 3. 生成分析报告
    print("\n步骤3: 生成收敛分析报告...")
    plotter.generate_convergence_report(algorithms, scale)

    print("\n" + "="*70)
    print("✅ 全部完成！".center(70))
    print("="*70 + "\n")


if __name__ == "__main__":
    main()

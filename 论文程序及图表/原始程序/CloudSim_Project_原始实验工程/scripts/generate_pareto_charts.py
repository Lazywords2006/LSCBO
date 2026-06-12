"""
Publication-Quality Charts for CloudSim Multi-Objective Experiments
生成期刊级别的科研图表

Author: LSCBO Research Team
Date: 2025-12-23
"""
import pandas as pd
import matplotlib.pyplot as plt
import numpy as np
import os
import glob

# 设置期刊级别的图表风格
plt.style.use('seaborn-v0_8-whitegrid')
plt.rcParams.update({
    'font.size': 12,
    'font.family': 'serif',
    'axes.labelsize': 14,
    'axes.titlesize': 16,
    'xtick.labelsize': 11,
    'ytick.labelsize': 11,
    'legend.fontsize': 10,
    'figure.dpi': 300,
    'savefig.dpi': 300,
    'savefig.bbox': 'tight',
    'axes.grid': True,
    'grid.alpha': 0.3,
})

# 算法颜色映射 (期刊友好的配色)
COLORS = {
    'LSCBO': '#E74C3C',    # 红色 - 突出主算法
    'CBO': '#3498DB',      # 蓝色
    'PSO': '#2ECC71',      # 绿色
    'AOA': '#9B59B6',      # 紫色
    'GWO': '#F39C12',      # 橙色
}

# 算法标记
MARKERS = {
    'LSCBO': 'o',
    'CBO': 's',
    'PSO': '^',
    'AOA': 'D',
    'GWO': 'v',
}

def get_latest_pareto_file(results_dir):
    """获取最新的Pareto实验数据文件"""
    files = glob.glob(os.path.join(results_dir, "pareto_experiment_*.csv"))
    if not files:
        raise FileNotFoundError(f"No pareto experiment files found in {results_dir}")
    latest = max(files, key=os.path.getmtime)
    print(f"Loading: {latest}")
    return latest

def plot_metric_barplot(df, metric, ylabel, title, output_path, lower_is_better=True):
    """绘制柱状图对比"""
    fig, ax = plt.subplots(figsize=(12, 6))
    
    task_counts = sorted(df['TaskCount'].unique())
    algorithms = df['Algorithm'].unique()
    n_algo = len(algorithms)
    width = 0.15
    x = np.arange(len(task_counts))
    
    for i, algo in enumerate(algorithms):
        algo_data = df[df['Algorithm'] == algo].groupby('TaskCount')[metric].mean()
        values = [algo_data.get(tc, 0) for tc in task_counts]
        offset = (i - n_algo/2 + 0.5) * width
        bars = ax.bar(x + offset, values, width, label=algo, color=COLORS.get(algo, 'gray'), edgecolor='black', linewidth=0.5)
    
    ax.set_xlabel('Number of Tasks (M)')
    ax.set_ylabel(ylabel)
    ax.set_title(title)
    ax.set_xticks(x)
    ax.set_xticklabels(task_counts)
    ax.legend(loc='upper left', framealpha=0.9)
    
    # 添加注释
    direction = "↓ Lower is better" if lower_is_better else "↑ Higher is better"
    ax.annotate(direction, xy=(0.98, 0.98), xycoords='axes fraction', ha='right', va='top', fontsize=10, style='italic')
    
    plt.tight_layout()
    plt.savefig(output_path)
    print(f"Saved: {output_path}")
    plt.close()

def plot_metric_lineplot(df, metric, ylabel, title, output_path, lower_is_better=True):
    """绘制折线图对比 (任务规模趋势)"""
    fig, ax = plt.subplots(figsize=(10, 6))
    
    task_counts = sorted(df['TaskCount'].unique())
    
    for algo in df['Algorithm'].unique():
        algo_data = df[df['Algorithm'] == algo].groupby('TaskCount')[metric].agg(['mean', 'std'])
        means = [algo_data.loc[tc, 'mean'] if tc in algo_data.index else 0 for tc in task_counts]
        stds = [algo_data.loc[tc, 'std'] if tc in algo_data.index else 0 for tc in task_counts]
        
        ax.errorbar(task_counts, means, yerr=stds, label=algo, 
                   color=COLORS.get(algo, 'gray'), marker=MARKERS.get(algo, 'o'),
                   markersize=8, capsize=3, linewidth=2)
    
    ax.set_xlabel('Number of Tasks (M)')
    ax.set_ylabel(ylabel)
    ax.set_title(title)
    ax.legend(loc='upper left', framealpha=0.9)
    
    direction = "↓ Lower is better" if lower_is_better else "↑ Higher is better"
    ax.annotate(direction, xy=(0.98, 0.98), xycoords='axes fraction', ha='right', va='top', fontsize=10, style='italic')
    
    plt.tight_layout()
    plt.savefig(output_path)
    print(f"Saved: {output_path}")
    plt.close()

def plot_heatmap_ranking(df, output_path):
    """绘制算法排名热力图"""
    metrics = ['Makespan', 'Cost', 'Energy', 'LoadBalanceIndex', 'ImbalanceDegree']
    algorithms = df['Algorithm'].unique()
    
    # 计算每个指标的平均排名
    rankings = pd.DataFrame(index=algorithms, columns=metrics)
    
    for metric in metrics:
        means = df.groupby('Algorithm')[metric].mean().sort_values()
        for rank, algo in enumerate(means.index, 1):
            rankings.loc[algo, metric] = rank
    
    rankings = rankings.astype(float)
    rankings['Average'] = rankings.mean(axis=1)
    
    fig, ax = plt.subplots(figsize=(10, 6))
    
    # 创建热力图
    im = ax.imshow(rankings.values, cmap='RdYlGn_r', aspect='auto')
    
    ax.set_xticks(np.arange(len(rankings.columns)))
    ax.set_yticks(np.arange(len(rankings.index)))
    ax.set_xticklabels(rankings.columns)
    ax.set_yticklabels(rankings.index)
    
    plt.setp(ax.get_xticklabels(), rotation=45, ha='right')
    
    # 添加数值标签
    for i in range(len(rankings.index)):
        for j in range(len(rankings.columns)):
            text = ax.text(j, i, f'{rankings.values[i, j]:.1f}',
                          ha='center', va='center', color='black', fontsize=11)
    
    ax.set_title('Algorithm Ranking Heatmap (1 = Best)')
    fig.colorbar(im, ax=ax, label='Rank')
    
    plt.tight_layout()
    plt.savefig(output_path)
    print(f"Saved: {output_path}")
    plt.close()

def plot_radar_chart(df, output_path):
    """绘制雷达图 (多目标综合对比)"""
    metrics = ['Makespan', 'Cost', 'Energy', 'LoadBalanceIndex', 'ImbalanceDegree']
    algorithms = df['Algorithm'].unique()
    
    # 归一化到0-1（反转使得更好=更大=更外圈）
    normalized = pd.DataFrame(index=algorithms, columns=metrics)
    
    for metric in metrics:
        means = df.groupby('Algorithm')[metric].mean()
        min_val, max_val = means.min(), means.max()
        for algo in algorithms:
            # 反转：更小的值映射到更大的归一化值
            normalized.loc[algo, metric] = 1 - (means[algo] - min_val) / (max_val - min_val + 1e-10)
    
    normalized = normalized.astype(float)
    
    # 雷达图
    angles = np.linspace(0, 2*np.pi, len(metrics), endpoint=False).tolist()
    angles += angles[:1]  # 闭合
    
    fig, ax = plt.subplots(figsize=(8, 8), subplot_kw=dict(polar=True))
    
    for algo in algorithms:
        values = normalized.loc[algo].values.tolist()
        values += values[:1]  # 闭合
        ax.plot(angles, values, 'o-', linewidth=2, label=algo, color=COLORS.get(algo, 'gray'))
        ax.fill(angles, values, alpha=0.1, color=COLORS.get(algo, 'gray'))
    
    ax.set_xticks(angles[:-1])
    ax.set_xticklabels(metrics)
    ax.set_ylim(0, 1)
    ax.set_title('Multi-Objective Performance Radar\n(Outer = Better)', pad=20)
    ax.legend(loc='upper right', bbox_to_anchor=(1.3, 1.0))
    
    plt.tight_layout()
    plt.savefig(output_path)
    print(f"Saved: {output_path}")
    plt.close()

def generate_summary_table(df, output_path):
    """生成统计摘要表格"""
    metrics = ['Makespan', 'Cost', 'Energy', 'LoadBalanceIndex', 'ImbalanceDegree']
    
    summary = df.groupby('Algorithm')[metrics].agg(['mean', 'std'])
    
    # 创建LaTeX格式表格
    with open(output_path.replace('.png', '.tex'), 'w') as f:
        f.write("% Auto-generated LaTeX table\n")
        f.write("\\begin{table}[htbp]\n")
        f.write("\\centering\n")
        f.write("\\caption{Algorithm Performance Comparison}\n")
        f.write("\\begin{tabular}{l" + "cc" * len(metrics) + "}\n")
        f.write("\\hline\n")
        
        header = "Algorithm"
        for m in metrics:
            header += f" & {m[:8]} Mean & Std"
        f.write(header + " \\\\\n")
        f.write("\\hline\n")
        
        for algo in summary.index:
            row = algo
            for m in metrics:
                mean = summary.loc[algo, (m, 'mean')]
                std = summary.loc[algo, (m, 'std')]
                row += f" & {mean:.4f} & {std:.4f}"
            f.write(row + " \\\\\n")
        
        f.write("\\hline\n")
        f.write("\\end{tabular}\n")
        f.write("\\end{table}\n")
    
    print(f"Saved LaTeX table: {output_path.replace('.png', '.tex')}")

def main():
    # 路径配置
    script_dir = os.path.dirname(os.path.abspath(__file__))
    results_dir = os.path.join(script_dir, '..', 'results')
    charts_dir = os.path.join(results_dir, 'charts')
    os.makedirs(charts_dir, exist_ok=True)
    
    # 加载数据
    csv_file = get_latest_pareto_file(results_dir)
    df = pd.read_csv(csv_file)
    
    print(f"\nData loaded: {len(df)} records")
    print(f"Algorithms: {df['Algorithm'].unique()}")
    print(f"Task scales: {sorted(df['TaskCount'].unique())}")
    print()
    
    # 生成图表
    print("=== Generating Charts ===")
    
    # 1. Makespan对比
    plot_metric_barplot(df, 'Makespan', 'Makespan (seconds)', 
                       'Algorithm Comparison: Makespan',
                       os.path.join(charts_dir, 'makespan_barplot.png'))
    
    plot_metric_lineplot(df, 'Makespan', 'Makespan (seconds)',
                        'Makespan Trend with Task Scale',
                        os.path.join(charts_dir, 'makespan_lineplot.png'))
    
    # 2. Cost对比
    plot_metric_barplot(df, 'Cost', 'Cost (USD)',
                       'Algorithm Comparison: Cost',
                       os.path.join(charts_dir, 'cost_barplot.png'))
    
    # 3. Energy对比
    plot_metric_barplot(df, 'Energy', 'Energy (J)',
                       'Algorithm Comparison: Energy',
                       os.path.join(charts_dir, 'energy_barplot.png'))
    
    # 4. LoadBalance对比
    plot_metric_barplot(df, 'LoadBalanceIndex', 'Load Balance Index',
                       'Algorithm Comparison: Load Balance',
                       os.path.join(charts_dir, 'loadbalance_barplot.png'))
    
    # 5. 排名热力图
    plot_heatmap_ranking(df, os.path.join(charts_dir, 'ranking_heatmap.png'))
    
    # 6. 雷达图
    plot_radar_chart(df, os.path.join(charts_dir, 'radar_chart.png'))
    
    # 7. 统计表格
    generate_summary_table(df, os.path.join(charts_dir, 'summary_table.png'))
    
    print(f"\n=== All charts saved to: {charts_dir} ===")

if __name__ == "__main__":
    main()

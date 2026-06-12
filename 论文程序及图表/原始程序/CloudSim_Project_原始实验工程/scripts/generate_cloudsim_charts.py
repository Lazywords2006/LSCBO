import pandas as pd
import matplotlib.pyplot as plt
import seaborn as sns
import os
import glob

# 设置风格
plt.style.use('seaborn-v0_8-whitegrid')
plt.rcParams['font.sans-serif'] = ['SimHei']  # 用来正常显示中文标签
plt.rcParams['axes.unicode_minus'] = False  # 用来正常显示负号

def get_latest_results_file(results_dir):
    # 查找符合模式的CSV文件
    files = glob.glob(os.path.join(results_dir, "five_algorithm_comparison_fixed_*.csv"))
    if not files:
        raise FileNotFoundError(f"No result files found in {results_dir}")
    # 按修改时间排序，取最新的
    latest_file = max(files, key=os.path.getmtime)
    print(f"Analyzing latest file: {latest_file}")
    return latest_file

def plot_makespan_comparison(df, output_dir):
    plt.figure(figsize=(10, 6))
    
    # 过滤掉不需要的数据（如果有）
    # df = df[df['TaskCount'].isin([100, 200])]
    
    # 创建柱状图
    sns.barplot(x='TaskCount', y='InternalMakespan', hue='Algorithm', data=df, palette='viridis', errorbar=None)
    
    plt.title('Algorithm Comparison: Makespan (Lower is Better)', fontsize=14)
    plt.xlabel('Number of Tasks (M)', fontsize=12)
    plt.ylabel('Internal Makespan', fontsize=12)
    plt.legend(title='Algorithm', bbox_to_anchor=(1.05, 1), loc='upper left')
    
    # 标注数值
    for container in plt.gca().containers:
        plt.gca().bar_label(container, fmt='%.1f', padding=3)

    plt.tight_layout()
    output_path = os.path.join(output_dir, 'makespan_comparison.png')
    plt.savefig(output_path, dpi=300)
    print(f"Saved Makespan chart to {output_path}")

def plot_lbr_comparison(df, output_dir):
    plt.figure(figsize=(10, 6))
    
    sns.barplot(x='TaskCount', y='LoadBalanceRatio', hue='Algorithm', data=df, palette='magma', errorbar=None)
    
    plt.title('Algorithm Comparison: Load Balance Ratio (Lower is Better)', fontsize=14)
    plt.xlabel('Number of Tasks (M)', fontsize=12)
    plt.ylabel('Load Balance Ratio', fontsize=12)
    plt.legend(title='Algorithm', bbox_to_anchor=(1.05, 1), loc='upper left')

    # 标注数值
    for container in plt.gca().containers:
        plt.gca().bar_label(container, fmt='%.2f', padding=3)
        
    plt.tight_layout()
    output_path = os.path.join(output_dir, 'lbr_comparison.png')
    plt.savefig(output_path, dpi=300)
    print(f"Saved LBR chart to {output_path}")

def main():
    # 路径配置
    script_dir = os.path.dirname(os.path.abspath(__file__))
    project_root = os.path.dirname(script_dir)
    results_dir = os.path.join(project_root, 'results')
    
    try:
        csv_file = get_latest_results_file(results_dir)
        df = pd.read_csv(csv_file)
        
        # 确保TaskCount是分类变量，保证绘图顺序
        df['TaskCount'] = df['TaskCount'].astype(int)
        df = df.sort_values(by=['TaskCount', 'Algorithm'])
        
        print("Data Summary:")
        print(df.groupby(['TaskCount', 'Algorithm'])['InternalMakespan'].mean())
        
        # 绘图
        plot_makespan_comparison(df, results_dir)
        plot_lbr_comparison(df, results_dir)
        
    except Exception as e:
        print(f"Error: {e}")

if __name__ == "__main__":
    main()

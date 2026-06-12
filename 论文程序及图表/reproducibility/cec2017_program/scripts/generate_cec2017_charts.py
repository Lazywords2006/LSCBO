import pandas as pd
import matplotlib.pyplot as plt
import seaborn as sns
import os
import glob

def generate_charts():
    # 结果目录
    RESULT_DIR = 'CEC2017'
    FIGURES_DIR = os.path.join(RESULT_DIR, 'figures')
    
    if not os.path.exists(FIGURES_DIR):
        os.makedirs(FIGURES_DIR)

    # Academic Style Settings
    plt.rcParams.update({
        'font.size': 14,
        'axes.labelsize': 14,
        'axes.titlesize': 16,
        'xtick.labelsize': 12,
        'ytick.labelsize': 12,
        'legend.fontsize': 12,
        'font.family': 'sans-serif'
    })

    # 1. 找到最新的 ranking 文件以确定时间戳
    rank_files = glob.glob(os.path.join(RESULT_DIR, 'cec2017_ranking_*.csv'))
    if not rank_files:
        print("No ranking files found.")
        return

    latest_rank_file = max(rank_files, key=os.path.getmtime)
    timestamp = latest_rank_file.split('_')[-1].replace('.csv', '')
    print(f"Processing timestamp: {timestamp}")

    # 2. 生成排名图表
    df_rank = pd.read_csv(latest_rank_file)
    
    # 算法列表和颜色定义
    algorithms = ['LSCBO', 'CBO', 'PSO', 'GWO', 'HHO', 'AOA', 'GTO']
    palette = {
        'LSCBO': '#2ca02c', # Green (Hero)
        'CBO': '#ff7f0e',   # Orange (Second)
        'PSO': '#1f77b4',   # Blue
        'GWO': '#9467bd',   # Purple
        'HHO': '#e377c2',   # Pink
        'AOA': '#7f7f7f',   # Gray
        'GTO': '#bcbd22'    # Olive
    }
    
    existing_algos = [algo for algo in algorithms if algo in df_rank.columns]
    colors = [palette.get(algo, '#333333') for algo in existing_algos]

    # Average Ranking Chart
    if 'Average Rank' in df_rank['Function'].values:
        avg_ranks = df_rank[df_rank['Function'] == 'Average Rank'][existing_algos].iloc[0]
        
        plt.figure(figsize=(10, 6))
        bars = plt.bar(existing_algos, avg_ranks, color=colors, edgecolor='black', alpha=0.8)
        
        plt.title('Average Ranking (Lower is Better)', fontsize=16, fontweight='bold')
        plt.ylabel('Rank', fontsize=14)
        plt.ylim(0, len(existing_algos) + 1)
        
        for bar in bars:
            height = bar.get_height()
            plt.text(bar.get_x() + bar.get_width()/2., height + 0.1,
                     f'{height:.2f}',
                     ha='center', va='bottom', fontweight='bold', fontsize=12)
    
        plt.grid(axis='y', linestyle='--', alpha=0.5)
        plt.tight_layout()
        
        plt.savefig(os.path.join(FIGURES_DIR, 'average_ranking.eps'), format='eps', dpi=300)
        plt.savefig(os.path.join(FIGURES_DIR, 'average_ranking.pdf'), format='pdf', dpi=300)
        plt.savefig(os.path.join(FIGURES_DIR, 'average_ranking.png'), dpi=300)
        plt.close()
        print("Generated average_ranking (EPS/PDF/PNG)")

    # 3. 生成箱线图 (Box Plots)
    # 尝试找到对应的 detailed 文件
    detailed_files = glob.glob(os.path.join(RESULT_DIR, f'cec2017_detailed_*{timestamp}*.csv'))
    if detailed_files:
        detailed_file = detailed_files[0]
        print(f"Found detailed file: {detailed_file}")
        
        df_detail = pd.read_csv(detailed_file)
        
        # Melt columns Run1...Run30
        run_cols = [f'Run{i}' for i in range(1, 31)]
        df_melted = df_detail.melt(id_vars=['Algorithm', 'Function'], value_vars=run_cols, 
                                   value_name='Fitness', var_name='Run')
        
        functions = df_detail['Function'].unique()
        
        for func in functions:
            data_func = df_melted[df_melted['Function'] == func]
            
            plt.figure(figsize=(10, 6))
            # 使用 boxplot
            sns.boxplot(x='Algorithm', y='Fitness', data=data_func, order=existing_algos, palette=palette)
            
            plt.title(f'Fitness Distribution: {func}', fontsize=16, fontweight='bold')
            plt.yscale('log') # Log scale is crucial for CEC
            plt.ylabel('Fitness (Log Scale)', fontsize=14)
            plt.grid(True, linestyle='--', alpha=0.5, which='both')
            
            plt.tight_layout()
            
            plt.savefig(os.path.join(FIGURES_DIR, f'boxplot_{func}.eps'), format='eps', dpi=300)
            plt.savefig(os.path.join(FIGURES_DIR, f'boxplot_{func}.pdf'), format='pdf', dpi=300)
            plt.savefig(os.path.join(FIGURES_DIR, f'boxplot_{func}.png'), dpi=300)
            plt.close()
            print(f"Generated boxplot_{func} (EPS/PDF/PNG)")
    else:
        print(f"No detailed file found for timestamp {timestamp}")

if __name__ == "__main__":
    generate_charts()

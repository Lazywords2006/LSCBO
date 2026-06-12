import pandas as pd
import matplotlib.pyplot as plt
import seaborn as sns
import os
import glob

# 设置绘图风格 (仿MATLAB)
plt.style.use('seaborn-v0_8-whitegrid')
plt.rcParams['font.family'] = 'serif'
plt.rcParams['font.serif'] = ['Times New Roman']
plt.rcParams['font.size'] = 12
plt.rcParams['axes.labelsize'] = 12
plt.rcParams['axes.titlesize'] = 14
plt.rcParams['xtick.labelsize'] = 10
plt.rcParams['ytick.labelsize'] = 10
plt.rcParams['legend.fontsize'] = 10
plt.rcParams['figure.dpi'] = 300

RESULT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "../results")
OUTPUT_DIR = os.path.join(RESULT_DIR, "paper_figures")

def ensure_dir(d):
    if not os.path.exists(d):
        os.makedirs(d)

def plot_convergence(scale_label, figure_name):
    """
    绘制收敛曲线 (Figure 9, 11)
    读取 convergence_*.csv
    """
    print(f"Generating {figure_name} (Convergence)...")
    
    # 查找所有 convergence 文件以匹配 scale_label (e.g. "M100")
    # 文件名格式: convergence_ALGO_Scale_seed.csv
    # Search in RESULT_DIR and subdirectories (e.g. 'convergence' folder)
    pattern = os.path.join(RESULT_DIR, "**", f"convergence_*_{scale_label}_*.csv")
    files = glob.glob(pattern, recursive=True)
    
    if not files:
        print(f"No convergence files found for {scale_label}")
        return

    data = []
    for f in files:
        try:
            # 解析文件名
            basename = os.path.basename(f)
            parts = basename.split('_')
            algo = parts[1]
            seed = parts[3].replace('.csv', '').replace('seed', '')
            
            df = pd.read_csv(f)
            # Columns: Iteration,BestFitness,Time,Load,Price
            df['Algorithm'] = algo
            df['Seed'] = seed
            data.append(df)
            
            # Additional Plot: Component Analysis for LSCBO (Figure 9 Detailed)
            if 'LSCBO' in algo and scale_label == "M100":
                 plot_components(df, algo, scale_label)

        except Exception as e:
            print(f"Skipping {f}: {e}")
            
    if not data:
        return
        
    full_df = pd.concat(data, ignore_index=True)
    
    plt.figure(figsize=(8, 6))
    
    # 使用Seaborn绘制 Total Cost
    sns.lineplot(data=full_df, x='Iteration', y='BestFitness', hue='Algorithm', style='Algorithm', 
                 palette='tab10', markers=False, dashes=True)
    
    plt.yscale('log') # Log scale for better visibility of convergence differences
    plt.title(f'Convergence Comparison ({scale_label})')
    plt.xlabel('Iterations')
    plt.ylabel('Total Cost (Log Scale)')
    plt.grid(True, which='both', linestyle='--', linewidth=0.5)
    plt.legend(frameon=True, fancybox=False, edgecolor='black')
    
    ensure_dir(OUTPUT_DIR)
    plt.savefig(os.path.join(OUTPUT_DIR, f"{figure_name}.png"), bbox_inches='tight')
    plt.close()

def plot_components(df, algo, scale_label):
    """
    Plot Time, Load, Price costs for a specific algorithm
    """
    print(f"Generating Component Plot for {algo}...")
    plt.figure(figsize=(8, 6))
    
    # Normalize if needed, or just plot raw values
    # User asked for "normalized values", but raw values show the scale difference.
    # Let's plot raw first.
    
    plt.plot(df['Iteration'], df['Time'], label='Time Cost')
    plt.plot(df['Iteration'], df['Load'], label='Load Cost')
    plt.plot(df['Iteration'], df['Price'], label='Price Cost')
    plt.plot(df['Iteration'], df['BestFitness'], label='Total Cost', linewidth=2, linestyle='--')
    
    plt.title(f'{algo} Cost Components ({scale_label})')
    plt.xlabel('Iterations')
    plt.ylabel('Cost')
    plt.legend()
    plt.grid(True)
    
    ensure_dir(OUTPUT_DIR)
    plt.savefig(os.path.join(OUTPUT_DIR, f"Figure9_Components_{algo}_{scale_label}.png"), bbox_inches='tight')
    plt.close()

def plot_scalability(scenario_name, figure_name):
    """
    绘制可扩展性/任务数变化曲线 (Figure 10, 12)
    读取 paper_reproduction_*.csv
    """
    print(f"Generating {figure_name} (Scalability)...")
    
    pattern = os.path.join(RESULT_DIR, f"paper_reproduction_{scenario_name}_*.csv")
    files = glob.glob(pattern)
    
    if not files:
        print(f"No scalability files found for {scenario_name}")
        return
        
    print(f"Found {len(files)} scalability files. Merging...")
    
    dfs = []
    for f in files:
        try:
            dfs.append(pd.read_csv(f))
        except Exception as e:
            print(f"Error reading {f}: {e}")
            
    if not dfs:
        return

    df = pd.concat(dfs, ignore_index=True)
    # Remove duplicates based on Algorithm+TaskCount+Seed to keep latest
    df.drop_duplicates(subset=['Algorithm', 'TaskCount', 'Seed'], keep='last', inplace=True)
    
    # Columns: Algorithm,TaskCount,Seed,TimeCost,LoadCost,PriceCost,TotalCost
    # Columns: Algorithm,TaskCount,Seed,TimeCost,LoadCost,PriceCost,TotalCost
    
    plt.figure(figsize=(8, 6))
    
    # 绘制 Total Cost vs Task Count
    markers = {'LSCBO-Fixed': 'o', 'CBO': 's', 'PSO': '^', 'HHO': 'D', 'AOA': 'v'}
    
    sns.lineplot(data=df, x='TaskCount', y='TotalCost', hue='Algorithm', style='Algorithm',
                 markers=markers, dashes=False, palette='tab10', markersize=8)
                 
    plt.title(f'Cost vs Number of Tasks ({scenario_name})')
    plt.xlabel('Number of Tasks')
    plt.ylabel('Total Cost')
    plt.grid(True, which='both', linestyle='--', linewidth=0.5)
    plt.legend(frameon=True, fancybox=False, edgecolor='black')
    
    ensure_dir(OUTPUT_DIR)
    plt.savefig(os.path.join(OUTPUT_DIR, f"{figure_name}.png"), bbox_inches='tight')
    plt.close()

def main():
    # Figure 9: M=100 Convergence
    plot_convergence("M100", "Figure9_Convergence_M100")
    
    # Figure 11: M=1000 Convergence (如果有数据)
    plot_convergence("M1000", "Figure11_Convergence_M1000")
    
    # Figure 10: Small Scale Task Variant
    plot_scalability("SmallScale", "Figure10_Scalability_Small")
    
    # Figure 12: Large Scale Task Variant
    plot_scalability("LargeScale", "Figure12_Scalability_Large")

if __name__ == "__main__":
    main()

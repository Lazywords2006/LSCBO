import pandas as pd
import matplotlib.pyplot as plt
import seaborn as sns
import os
import glob
import numpy as np
import matplotlib.gridspec as gridspec

def generate_qualitative_charts():
    DATA_DIR = 'CEC2017/qualitative'
    FIGURES_DIR = 'CEC2017/figures/qualitative'
    
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

    # Define a color palette for algorithms
    algo_colors = {
        'HHO': '#e377c2',   # Pink
        'AOA': '#7f7f7f',   # Gray
        'GTO': '#bcbd22'    # Olive
    }

    # Find latest files
    curve_files = glob.glob(os.path.join(DATA_DIR, 'qualitative_curves_*.csv'))
    hist_files = glob.glob(os.path.join(DATA_DIR, 'qualitative_history_*.csv'))

    if not curve_files or not hist_files:
        print("No qualitative data found.")
        return

    latest_curve = max(curve_files, key=os.path.getmtime)
    latest_hist = max(hist_files, key=os.path.getmtime)
    
    print(f"Reading {latest_curve}...")
    df_curves = pd.read_csv(latest_curve)
    print(f"Reading {latest_hist}...")
    df_hist = pd.read_csv(latest_hist)

    # Get unique combinations
    algos = df_curves['Algorithm'].unique()
    funcs = df_curves['Function'].unique()

    for algo in algos:
        for func in funcs:
            print(f"Generating chart for {algo} on {func}...")
            
            # Filter data
            subset_curve = df_curves[(df_curves['Algorithm'] == algo) & (df_curves['Function'] == func)]
            subset_hist = df_hist[(df_hist['Algorithm'] == algo) & (df_hist['Function'] == func)]

            if subset_curve.empty:
                continue

            # Setup Figure
            fig = plt.figure(figsize=(15, 6)) # Taller figure
            gs = gridspec.GridSpec(1, 4, width_ratios=[1, 1, 1, 1])
            fig.suptitle(f'Qualitative Analysis: {algo} on {func}', fontsize=18, fontweight='bold')

            # 1. 1st Dim Trajectory
            ax1 = plt.subplot(gs[0])
            sns.lineplot(data=subset_curve, x='Iteration', y='Trajectory', ax=ax1, color='#1f77b4', linewidth=1)
            ax1.set_title('Trajectory (1st Dim)', fontsize=14)
            ax1.set_xlabel('Iteration')
            ax1.set_ylabel('Position')
            ax1.grid(True, linestyle='--', alpha=0.5)

            # 2. Average Fitness
            ax2 = plt.subplot(gs[1])
            sns.lineplot(data=subset_curve, x='Iteration', y='AvgFitness', ax=ax2, color='#ff7f0e', linewidth=1)
            ax2.set_title('Average Fitness', fontsize=14)
            ax2.set_xlabel('Iteration')
            ax2.set_ylabel('Fitness (Avg)')
            ax2.set_yscale('log')
            ax2.grid(True, linestyle='--', alpha=0.5)

            # 3. Search History (Scatter)
            ax3 = plt.subplot(gs[2])
            if not subset_hist.empty:
                # Limit density if needed, or decrease size
                sns.scatterplot(data=subset_hist, x='Dim0', y='Dim1', hue='SnapshotIter', 
                                palette='viridis', ax=ax3, s=30, edgecolor='w', alpha=0.8, linewidth=0.5)
                ax3.legend(title='Iter', fontsize='small')
            ax3.set_title('Search History (2D)', fontsize=14)
            ax3.set_xlabel('Dim 0')
            ax3.set_ylabel('Dim 1')
            ax3.grid(True, linestyle='--', alpha=0.5)
            
            # 4. Empty/Placeholder? Or duplicate?
            # Reviewer asked for readable fonts. 3 panels is good.
            # I will leave 4th column blank or remove it?
            # Reconfiguring GridSpec to 3 columns to make them wider and clearer.
            
            plt.close(fig) # Close previous setup
            fig = plt.figure(figsize=(18, 6)) # Wider
            gs = gridspec.GridSpec(1, 3, width_ratios=[1, 1, 1])
            fig.suptitle(f'Qualitative Analysis: {algo} on {func}', fontsize=20, fontweight='bold')

            # Re-plot 1
            ax1 = plt.subplot(gs[0])
            sns.lineplot(data=subset_curve, x='Iteration', y='Trajectory', ax=ax1, color='#1f77b4', linewidth=1)
            ax1.set_title('Trajectory (1st Dim)', fontsize=16)
            ax1.set_xlabel('Iteration')
            ax1.set_ylabel('Position')
            ax1.grid(True, linestyle='--', alpha=0.5)

            # Re-plot 2
            ax2 = plt.subplot(gs[1])
            sns.lineplot(data=subset_curve, x='Iteration', y='AvgFitness', ax=ax2, color='#ff7f0e', linewidth=1)
            ax2.set_title('Average Fitness', fontsize=16)
            ax2.set_xlabel('Iteration')
            ax2.set_ylabel('Fitness (Avg)')
            ax2.set_yscale('log')
            ax2.grid(True, linestyle='--', alpha=0.5)

            # Re-plot 3
            ax3 = plt.subplot(gs[2])
            if not subset_hist.empty:
                sns.scatterplot(data=subset_hist, x='Dim0', y='Dim1', hue='SnapshotIter', 
                                palette='viridis', ax=ax3, s=40, edgecolor='w', alpha=0.8)
                ax3.legend(title='Iter', fontsize=10, loc='upper right')
            ax3.set_title('Search History (2D)', fontsize=16)
            ax3.set_xlabel('Dim 0')
            ax3.set_ylabel('Dim 1')
            ax3.grid(True, linestyle='--', alpha=0.5)

            plt.tight_layout(rect=[0, 0.03, 1, 0.95])
            
            out_file_base = os.path.join(FIGURES_DIR, f'qual_{algo}_{func}')
            plt.savefig(out_file_base + '.eps', format='eps', dpi=300)
            plt.savefig(out_file_base + '.pdf', format='pdf', dpi=300)
            plt.savefig(out_file_base + '.png', dpi=300)
            plt.close()

if __name__ == "__main__":
    generate_qualitative_charts()

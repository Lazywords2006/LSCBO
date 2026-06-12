import pandas as pd
import matplotlib.pyplot as plt
import seaborn as sns
import os
import glob
import numpy as np
from mpl_toolkits.axes_grid1.inset_locator import inset_axes

def generate_advanced_charts():
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

    # Palette
    palette = {
        'LSCBO': '#2ca02c', # Green (Hero)
        'CBO': '#ff7f0e',   # Orange (Second)
        'PSO': '#1f77b4',   # Blue
        'GWO': '#9467bd',   # Purple
        'HHO': '#e377c2',   # Pink
        'AOA': '#7f7f7f',   # Gray
        'GTO': '#bcbd22'    # Olive
    }
    algorithms = ['LSCBO', 'CBO', 'PSO', 'GWO', 'HHO', 'AOA', 'GTO']

    # 1. Convergence Curves
    conv_files = glob.glob(os.path.join(RESULT_DIR, 'cec2017_convergence_*.csv'))
    if conv_files:
        latest_conv = max(conv_files, key=os.path.getmtime)
        print(f"Processing Convergence: {latest_conv}")
        df_conv = pd.read_csv(latest_conv)
        
        functions = df_conv['Function'].unique()
        for func in functions:
            data = df_conv[df_conv['Function'] == func]
            
            fig, ax = plt.subplots(figsize=(10, 6))
            sns.lineplot(data=data, x='Iteration', y='AvgFitness', hue='Algorithm', 
                         palette=palette, hue_order=algorithms, linewidth=2, ax=ax)
            
            ax.set_title(f'Convergence Curve: {func}', fontsize=16, fontweight='bold')
            ax.set_yscale('log')
            ax.set_xlabel('Iteration', fontsize=14)
            ax.set_ylabel('Average Fitness (Log Scale)', fontsize=14)
            ax.grid(True, linestyle='--', alpha=0.5)
            ax.legend(bbox_to_anchor=(1.05, 1), loc='upper left')

            # Zoom-in Inset (Iterations 0-500)
            ax_ins = inset_axes(ax, width="40%", height="30%", loc='lower left', 
                                bbox_to_anchor=(0.1, 0.1, 1, 1), bbox_transform=ax.transAxes)
            
            # Filter early data
            early_data = data[data['Iteration'] <= 500]
            sns.lineplot(data=early_data, x='Iteration', y='AvgFitness', hue='Algorithm',
                         palette=palette, hue_order=algorithms, linewidth=1.5, ax=ax_ins, legend=False)
            
            ax_ins.set_yscale('log')
            ax_ins.set_title('Early Convergence (0-500)', fontsize=10)
            ax_ins.set_xlabel('')
            ax_ins.set_ylabel('')
            ax_ins.grid(True, linestyle=':', alpha=0.5)
            # Remove tick labels on inset to save space? Keep them but small.
            ax_ins.tick_params(axis='both', which='major', labelsize=8)

            plt.tight_layout()
            
            # Save Vector Graphics (EPS/PDF) and PNG
            plt.savefig(os.path.join(FIGURES_DIR, f'convergence_{func}.eps'), format='eps', dpi=300)
            plt.savefig(os.path.join(FIGURES_DIR, f'convergence_{func}.pdf'), format='pdf', dpi=300)
            plt.savefig(os.path.join(FIGURES_DIR, f'convergence_{func}.png'), dpi=300) # Keep PNG for preview
            plt.close()
            print(f"Generated convergence_{func} (EPS/PDF/PNG)")
    else:
        print("No convergence files found.")

    # 2. Diversity Curves
    div_files = glob.glob(os.path.join(RESULT_DIR, 'cec2017_diversity_*.csv'))
    if div_files:
        latest_div = max(div_files, key=os.path.getmtime)
        print(f"Processing Diversity: {latest_div}")
        df_div = pd.read_csv(latest_div)
        
        for func in df_div['Function'].unique():
            data = df_div[df_div['Function'] == func]
            
            plt.figure(figsize=(10, 6))
            sns.lineplot(data=data, x='Iteration', y='Diversity', hue='Algorithm', 
                         palette=palette, hue_order=algorithms, linewidth=2)
            
            plt.title(f'Population Diversity: {func}', fontsize=16, fontweight='bold')
            plt.yscale('log') 
            plt.xlabel('Iteration')
            plt.ylabel('Diversity (Log Scale)')
            plt.grid(True, linestyle='--', alpha=0.5)
            plt.legend(bbox_to_anchor=(1.05, 1), loc='upper left')
            plt.tight_layout()
            
            plt.savefig(os.path.join(FIGURES_DIR, f'diversity_{func}.eps'), format='eps', dpi=300)
            plt.savefig(os.path.join(FIGURES_DIR, f'diversity_{func}.pdf'), format='pdf', dpi=300)
            plt.savefig(os.path.join(FIGURES_DIR, f'diversity_{func}.png'), dpi=300)
            plt.close()
            print(f"Generated diversity_{func} (EPS/PDF/PNG)")
    else:
        print("No diversity files found.")

if __name__ == "__main__":
    generate_advanced_charts()

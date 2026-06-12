#!/usr/bin/env python3
"""
CEC2017 Charts Generator - LF-CBO Version
Only generates PNG files with LF-CBO naming (replacing LSCBO)
"""

import os
import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
from scipy import stats

# Configuration
PROJECT_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA_DIR = os.path.join(PROJECT_DIR, "CEC2017")
OUTPUT_DIR = os.path.join(DATA_DIR, "figures_lfcbo")

# Algorithm order (LF-CBO replaces LSCBO)
ALGORITHM_ORDER = ['LF-CBO', 'GTO', 'DE', 'CBO', 'PSO', 'SA', 'GWO']

# Colors
ALGORITHM_COLORS = {
    'LF-CBO': '#E63946',
    'GTO': '#457B9D',
    'DE': '#2A9D8F',
    'CBO': '#E9C46A',
    'PSO': '#8338EC',
    'SA': '#FF6B35',
    'GWO': '#7FB069',
}

# Markers
ALGORITHM_MARKERS = {
    'LF-CBO': 'o', 'GTO': 's', 'DE': '^', 'CBO': 'D',
    'PSO': 'v', 'SA': 'p', 'GWO': 'h',
}

def ensure_dir(path):
    os.makedirs(path, exist_ok=True)

def load_results():
    """Load results with LSCBO renamed to LF-CBO"""
    files = [f for f in os.listdir(DATA_DIR) if f.startswith('cec2017_results_') and f.endswith('.csv')]
    if not files:
        return None
    df = pd.read_csv(os.path.join(DATA_DIR, sorted(files)[-1]))
    df['Algorithm'] = df['Algorithm'].replace('LSCBO', 'LF-CBO')
    return df

def load_ranking():
    """Load ranking with LSCBO renamed to LF-CBO"""
    files = [f for f in os.listdir(DATA_DIR) if f.startswith('cec2017_ranking_') and f.endswith('.csv')]
    if not files:
        return None
    df = pd.read_csv(os.path.join(DATA_DIR, sorted(files)[-1]))
    if 'LSCBO' in df.columns:
        df = df.rename(columns={'LSCBO': 'LF-CBO'})
    return df

def load_convergence(func_name='Sphere'):
    """Load convergence with LSCBO renamed to LF-CBO"""
    files = [f for f in os.listdir(DATA_DIR) if f.startswith('cec2017_convergence_') and f.endswith('.csv')]
    if not files:
        return None
    df = pd.read_csv(os.path.join(DATA_DIR, sorted(files)[-1]))
    if 'Algorithm' in df.columns:
        df['Algorithm'] = df['Algorithm'].replace('LSCBO', 'LF-CBO')
        df = df[df['Function'] == func_name]
        df = df.pivot(index='Iteration', columns='Algorithm', values='AvgFitness').reset_index()
    if 'LSCBO' in df.columns:
        df = df.rename(columns={'LSCBO': 'LF-CBO'})
    return df

def plot_friedman_ranking(df):
    """Plot Friedman test ranking"""
    algorithms = [a for a in ALGORITHM_ORDER if a in df['Algorithm'].unique()]
    functions = df['Function'].unique()
    
    # Build ranking matrix
    rank_matrix = []
    for func in functions:
        func_data = df[df['Function'] == func]
        means = {row['Algorithm']: row['Mean'] for _, row in func_data.iterrows()}
        sorted_algos = sorted(means.keys(), key=lambda x: means[x])
        ranks = {algo: i+1 for i, algo in enumerate(sorted_algos)}
        rank_matrix.append([ranks.get(a, len(algorithms)) for a in algorithms])
    
    rank_matrix = np.array(rank_matrix)
    avg_ranks = rank_matrix.mean(axis=0)
    
    # Friedman test
    stat, p_value = stats.friedmanchisquare(*[rank_matrix[:, i] for i in range(len(algorithms))])
    
    # Plot
    fig, ax = plt.subplots(figsize=(10, 6))
    colors = [ALGORITHM_COLORS.get(a, '#999') for a in algorithms]
    bars = ax.bar(algorithms, avg_ranks, color=colors, edgecolor='black', linewidth=1.5)
    
    for bar, rank in zip(bars, avg_ranks):
        ax.text(bar.get_x() + bar.get_width()/2, bar.get_height() + 0.1, 
                f'{rank:.2f}', ha='center', va='bottom', fontsize=11, fontweight='bold')
    
    ax.set_ylabel('Average Rank', fontsize=14)
    ax.set_title(f'Friedman Test Average Ranking\n(χ² = {stat:.2f}, p-value = {p_value:.4f})', fontsize=14, fontweight='bold')
    ax.set_ylim(0, max(avg_ranks) + 1)
    
    if p_value < 0.05:
        ax.text(0.98, 0.98, '* p < 0.05', transform=ax.transAxes, ha='right', va='top', color='red', fontsize=12)
    
    plt.tight_layout()
    ensure_dir(OUTPUT_DIR)
    plt.savefig(os.path.join(OUTPUT_DIR, 'friedman_ranking.png'), dpi=300)
    plt.close()
    print("Saved: friedman_ranking.png")

def plot_convergence(df, func_name='Sphere'):
    """Plot convergence curve"""
    fig, ax = plt.subplots(figsize=(10, 6))
    
    for algo in ALGORITHM_ORDER:
        if algo in df.columns:
            ax.semilogy(df['Iteration'], df[algo], 
                       label=algo, color=ALGORITHM_COLORS.get(algo, '#999'),
                       marker=ALGORITHM_MARKERS.get(algo, 'o'), markevery=1000, linewidth=2)
    
    ax.set_xlabel('Iterations', fontsize=14)
    ax.set_ylabel('Fitness Value (log scale)', fontsize=14)
    ax.set_title(f'Convergence Curve - {func_name}', fontsize=14, fontweight='bold')
    ax.legend(loc='upper right')
    ax.grid(True, alpha=0.3)
    
    plt.tight_layout()
    ensure_dir(OUTPUT_DIR)
    plt.savefig(os.path.join(OUTPUT_DIR, f'semilog_convergence_{func_name}.png'), dpi=300)
    plt.close()
    print(f"Saved: semilog_convergence_{func_name}.png")

def plot_boxplot(df):
    """Plot boxplot by category"""
    categories = {
        'Unimodal (F1-F3)': ['Sphere', 'Zakharov'],
        'Simple Multimodal (F4-F10)': ['Rosenbrock', 'Rastrigin', 'Ackley', 'Griewank', 'Levy', 'Schwefel', 'Michalewicz'],
        'Hybrid (F11-F20)': ['Bent Cigar', 'Discus', 'High Conditioned Elliptic', 'Dixon-Price', 'HappyCat', 'Step', 'Bohachevsky', 'Quartic', 'Exponential', 'Alpine'],
        'Composition (F21-F30)': ['Hybrid Function 1', 'Hybrid Function 2', 'Expanded Schaffer F6', 'Pathological', 'Periodic', 'Salomon', 'Styblinski-Tang', 'Weierstrass', 'Xin-She Yang', 'Sum Of Different Powers']
    }
    
    fig, axes = plt.subplots(2, 2, figsize=(14, 10))
    algorithms = [a for a in ALGORITHM_ORDER if a in df['Algorithm'].unique()]
    colors = [ALGORITHM_COLORS.get(a, '#999') for a in algorithms]
    
    for ax, (cat_name, funcs) in zip(axes.flat, categories.items()):
        cat_data = df[df['Function'].isin(funcs)]
        data = [cat_data[cat_data['Algorithm'] == a]['Mean'].values for a in algorithms]
        data = [d[~np.isnan(d)] if len(d) > 0 else [0] for d in data]
        
        bp = ax.boxplot(data, labels=algorithms, patch_artist=True)
        for patch, color in zip(bp['boxes'], colors):
            patch.set_facecolor(color)
            patch.set_alpha(0.7)
        
        ax.set_yscale('log')
        ax.set_title(cat_name, fontsize=12, fontweight='bold')
        ax.set_ylabel('Error Value')
        ax.tick_params(axis='x', rotation=45)
    
    fig.suptitle('Algorithm Performance by Function Category', fontsize=14, fontweight='bold')
    plt.tight_layout()
    ensure_dir(OUTPUT_DIR)
    plt.savefig(os.path.join(OUTPUT_DIR, 'boxplot_by_category.png'), dpi=300)
    plt.close()
    print("Saved: boxplot_by_category.png")

def plot_wilcoxon_heatmap(df):
    """Plot Wilcoxon signed-rank test heatmap"""
    algorithms = [a for a in ALGORITHM_ORDER if a in df['Algorithm'].unique()]
    n = len(algorithms)
    p_matrix = np.ones((n, n))
    
    for i, algo1 in enumerate(algorithms):
        for j, algo2 in enumerate(algorithms):
            if i != j:
                data1 = df[df['Algorithm'] == algo1].sort_values('Function')['Mean'].values
                data2 = df[df['Algorithm'] == algo2].sort_values('Function')['Mean'].values
                if len(data1) == len(data2) and len(data1) > 0:
                    try:
                        _, p = stats.wilcoxon(data1, data2)
                        p_matrix[i, j] = p
                    except:
                        pass
    
    fig, ax = plt.subplots(figsize=(10, 8))
    
    # Color coding
    colors = np.zeros((n, n, 3))
    for i in range(n):
        for j in range(n):
            if i == j:
                colors[i, j] = [0.9, 0.9, 0.9]  # Gray for diagonal
            elif p_matrix[i, j] < 0.05:
                data1 = df[df['Algorithm'] == algorithms[i]].sort_values('Function')['Mean'].mean()
                data2 = df[df['Algorithm'] == algorithms[j]].sort_values('Function')['Mean'].mean()
                if data1 < data2:
                    colors[i, j] = [0.2, 0.6, 0.6]  # Teal - row wins
                else:
                    colors[i, j] = [0.8, 0.3, 0.3]  # Red - column wins
            else:
                colors[i, j] = [0.9, 0.9, 0.9]  # Gray - no difference
    
    ax.imshow(colors, aspect='auto')
    
    for i in range(n):
        for j in range(n):
            if i != j:
                text = f'{p_matrix[i,j]:.3f}'
                if p_matrix[i,j] < 0.05:
                    text += '**'
                ax.text(j, i, text, ha='center', va='center', fontsize=9)
    
    ax.set_xticks(range(n))
    ax.set_yticks(range(n))
    ax.set_xticklabels(algorithms)
    ax.set_yticklabels(algorithms)
    ax.set_xlabel('Algorithm', fontsize=12)
    ax.set_ylabel('Algorithm', fontsize=12)
    ax.set_title('Wilcoxon Signed-Rank Test Heatmap\n(Green: row wins, Red: column wins)', fontsize=14, fontweight='bold')
    
    plt.tight_layout()
    ensure_dir(OUTPUT_DIR)
    plt.savefig(os.path.join(OUTPUT_DIR, 'wilcoxon_heatmap.png'), dpi=300)
    plt.close()
    print("Saved: wilcoxon_heatmap.png")

def plot_cd_diagram(df):
    """Plot Critical Difference diagram"""
    algorithms = [a for a in ALGORITHM_ORDER if a in df['Algorithm'].unique()]
    functions = df['Function'].unique()
    n_funcs = len(functions)
    n_algos = len(algorithms)
    
    # Calculate average ranks
    rank_matrix = []
    for func in functions:
        func_data = df[df['Function'] == func]
        means = {row['Algorithm']: row['Mean'] for _, row in func_data.iterrows()}
        sorted_algos = sorted(means.keys(), key=lambda x: means[x])
        ranks = {algo: i+1 for i, algo in enumerate(sorted_algos)}
        rank_matrix.append([ranks.get(a, n_algos) for a in algorithms])
    
    avg_ranks = np.array(rank_matrix).mean(axis=0)
    
    # Critical difference (Nemenyi)
    q_alpha = 2.949  # q for alpha=0.05, k=7
    cd = q_alpha * np.sqrt(n_algos * (n_algos + 1) / (6 * n_funcs))
    
    fig, ax = plt.subplots(figsize=(12, 4))
    
    # Sort by rank
    sorted_idx = np.argsort(avg_ranks)
    sorted_algos = [algorithms[i] for i in sorted_idx]
    sorted_ranks = avg_ranks[sorted_idx]
    
    # Plot
    y_positions = np.linspace(0.9, 0.1, n_algos)
    
    for i, (algo, rank) in enumerate(zip(sorted_algos, sorted_ranks)):
        color = ALGORITHM_COLORS.get(algo, '#999')
        ax.plot(rank, y_positions[i], 'o', markersize=10, color=color)
        ax.plot([rank, rank + 0.5], [y_positions[i], y_positions[i]], '-', color=color, linewidth=2)
        ax.text(rank + 0.6, y_positions[i], f'{algo} ({rank:.2f})', va='center', fontsize=11)
    
    # CD bar
    ax.plot([1, 1 + cd], [0.98, 0.98], 'k-', linewidth=2)
    ax.text(1 + cd/2, 1.02, f'CD = {cd:.3f}', ha='center', fontsize=10)
    
    ax.set_xlim(0.5, n_algos + 1)
    ax.set_ylim(0, 1.1)
    ax.set_xlabel('Average Rank', fontsize=12)
    ax.set_title('Critical Difference Diagram (Nemenyi Post-hoc Test)', fontsize=14, fontweight='bold')
    ax.axhline(y=0, color='gray', linewidth=0.5)
    
    plt.tight_layout()
    ensure_dir(OUTPUT_DIR)
    plt.savefig(os.path.join(OUTPUT_DIR, 'cd_diagram.png'), dpi=300)
    plt.close()
    print("Saved: cd_diagram.png")

def plot_radar_chart(rank_df):
    """Plot radar chart"""
    if rank_df is None:
        return
    
    rank_df = rank_df[rank_df['Function'] != 'Average Rank'].copy()
    algorithms = [a for a in ALGORITHM_ORDER if a in rank_df.columns]
    
    # Select representative functions
    selected = ['Sphere', 'Rosenbrock', 'Rastrigin', 'Ackley', 'Griewank', 'Schwefel',
                'Bent Cigar', 'Discus', 'Hybrid Function 1', 'Weierstrass', 'Styblinski-Tang', 'Sum Of Different Powers']
    rank_df = rank_df[rank_df['Function'].isin(selected)]
    functions = rank_df['Function'].tolist()
    
    if len(functions) == 0:
        return
    
    fig, ax = plt.subplots(figsize=(10, 10), subplot_kw=dict(projection='polar'))
    
    angles = np.linspace(0, 2 * np.pi, len(functions), endpoint=False).tolist()
    angles += angles[:1]
    
    for algo in algorithms:
        if algo in rank_df.columns:
            values = rank_df[algo].astype(float).tolist()
            values += values[:1]
            ax.plot(angles, values, 'o-', linewidth=2, label=algo, 
                   color=ALGORITHM_COLORS.get(algo, '#999'), markersize=5)
            ax.fill(angles, values, alpha=0.1, color=ALGORITHM_COLORS.get(algo, '#999'))
    
    ax.set_xticks(angles[:-1])
    short_names = [f[:10] if len(f) > 10 else f for f in functions]
    ax.set_xticklabels(short_names, fontsize=9)
    ax.set_title('Algorithm Ranking by Function\n(Lower is Better)', fontsize=14, fontweight='bold', y=1.08)
    ax.legend(loc='upper right', bbox_to_anchor=(1.35, 1.0), fontsize=10)
    ax.set_ylim(0, len(algorithms) + 1)
    
    plt.tight_layout()
    ensure_dir(OUTPUT_DIR)
    plt.savefig(os.path.join(OUTPUT_DIR, 'radar_chart.png'), dpi=300, bbox_inches='tight')
    plt.close()
    print("Saved: radar_chart.png")

def main():
    print("=" * 60)
    print("CEC2017 Charts Generator - LF-CBO Version")
    print("=" * 60)
    
    # Load data
    df = load_results()
    if df is None:
        print("Error: No results data found!")
        return
    
    rank_df = load_ranking()
    conv_df = load_convergence('Sphere')
    
    print(f"Loaded {len(df)} results, generating charts...")
    print(f"Output directory: {OUTPUT_DIR}")
    
    # Generate charts
    plot_friedman_ranking(df)
    
    if conv_df is not None and len(conv_df) > 0:
        plot_convergence(conv_df, 'Sphere')
    
    plot_boxplot(df)
    plot_wilcoxon_heatmap(df)
    plot_cd_diagram(df)
    plot_radar_chart(rank_df)
    
    print("=" * 60)
    print("All PNG charts generated successfully!")
    print(f"Output: {OUTPUT_DIR}")
    print("=" * 60)

if __name__ == "__main__":
    main()

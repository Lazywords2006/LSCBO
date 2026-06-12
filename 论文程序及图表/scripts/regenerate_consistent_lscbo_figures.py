#!/usr/bin/env python3
"""Regenerate manuscript figures with a single public display name: LSCBO.

This script is intentionally dependency-light for Windows use. It reads the
existing CSV data under the manuscript folder, maps internal experiment labels
such as "LSCBO-LS-final" to the paper-facing label "LSCBO", excludes internal
diagnostic variants, and rewrites the PNG figures used by the manuscript.
"""

from __future__ import annotations

import argparse
import csv
import math
import statistics
from collections import defaultdict
from pathlib import Path
from typing import Optional

import matplotlib.pyplot as plt


PUBLIC_NAME = "LSCBO"
FINAL_INTERNAL_NAME = "LSCBO-LS-final"
EXCLUDED_INTERNAL_NAMES = {"LSCBO-LS-wrong"}
WEIGHT_RANK_FILENAME = "weight_rank_values_metaheuristic.csv"
CEC_RANK_FILENAME = "cec2017_friedman_ranking.csv"

META_ALGOS = ["LSCBO", "GTO", "WOA", "HHO", "GWO", "DBO", "CBO", "AOA", "PSO"]
META_ALGOS_HEATMAP = ["AOA", "CBO", "GTO", "GWO", "HHO", "LSCBO", "PSO", "WOA"]
CEC_ALGOS = ["LSCBO", "GTO", "DE", "CBO", "PSO", "SA", "GWO"]

COLORS = {
    "LSCBO": "#C0392B",
    "GTO": "#3A8B3A",
    "DE": "#5BBCCA",
    "WOA": "#E8845A",
    "HHO": "#6AA6D8",
    "GWO": "#7E57C2",
    "DBO": "#8C6D62",
    "CBO": "#A9A9A9",
    "AOA": "#707070",
    "PSO": "#BCBCBC",
    "SA": "#D3D3D3",
}
MARKERS = {
    "LSCBO": "o",
    "GTO": "s",
    "WOA": "^",
    "HHO": "D",
    "GWO": "v",
    "DBO": "P",
    "CBO": "X",
    "AOA": "*",
    "PSO": "h",
}


def set_bar_grouped_hatch_style() -> None:
    """Paper-plot-skills bar_grouped_hatch inspired style."""
    plt.rcParams.update(
        {
            "text.usetex": False,
            "font.family": "serif",
            "font.serif": ["Computer Modern Roman", "STIX Two Text", "DejaVu Serif"],
            "axes.unicode_minus": False,
            "hatch.color": "white",
            "hatch.linewidth": 1.4,
        }
    )


def set_line_confidence_style() -> None:
    """Paper-plot-skills line_confidence_band inspired style."""
    plt.rcParams.update(
        {
            "text.usetex": False,
            "font.family": "serif",
            "font.serif": ["Computer Modern Roman", "STIX Two Text", "DejaVu Serif"],
            "axes.unicode_minus": False,
        }
    )


def set_training_curve_style() -> None:
    """Paper-plot-skills line_training_curve inspired style."""
    plt.rcParams.update(
        {
            "text.usetex": False,
            "font.family": "sans-serif",
            "font.sans-serif": ["DejaVu Sans", "Arial", "Helvetica"],
            "axes.unicode_minus": False,
        }
    )


def style_bar_axis(ax) -> None:
    ax.yaxis.grid(True, color="#EBEBEB", linewidth=0.7, linestyle="--", zorder=0)
    ax.set_axisbelow(True)
    for side, spine in ax.spines.items():
        if side in ("top", "right"):
            spine.set_visible(False)
        else:
            spine.set_linewidth(0.9)
            spine.set_color("#333333")
    ax.tick_params(length=0, labelsize=10.2)


def style_confidence_axis(ax) -> None:
    for side, spine in ax.spines.items():
        spine.set_visible(side in ("left", "bottom"))
        if spine.get_visible():
            spine.set_linewidth(0.9)
            spine.set_color("#333333")
    ax.tick_params(direction="in", length=4.5, width=1.0, labelsize=10.5)
    ax.grid(False)


def style_training_axis(ax) -> None:
    for spine in ax.spines.values():
        spine.set_visible(True)
        spine.set_linewidth(1.0)
        spine.set_color("#333333")
    ax.tick_params(direction="out", length=4, width=0.8, labelsize=10)
    ax.grid(False)


def parse_args() -> argparse.Namespace:
    script_dir = Path(__file__).resolve().parent
    default_manuscript_dir = default_manuscript_project_dir(script_dir)
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--manuscript-dir",
        type=Path,
        default=default_manuscript_dir,
        help="Path to the manuscript project folder containing data/ and figures/.",
    )
    return parser.parse_args()


def default_manuscript_project_dir(script_dir: Path) -> Path:
    direct = script_dir.parent
    if (direct / "LF-CBO_Manuscript.tex").exists() and (direct / "data").is_dir():
        return direct
    for parent in script_dir.parents:
        candidate = parent / "论文主稿" / "主稿工程"
        if (candidate / "LF-CBO_Manuscript.tex").exists() and (candidate / "data").is_dir():
            return candidate
    return direct


def mirror_figures_to_program_folder(manuscript_dir: Path, figure_names: list[str]) -> None:
    for parent in manuscript_dir.parents:
        target_dir = parent / "论文程序及图表" / "图表"
        if target_dir.is_dir():
            for name in figure_names:
                source = manuscript_dir / "figures" / name
                target = target_dir / name
                target.write_bytes(source.read_bytes())
            return


def public_algo_name(name: str) -> Optional[str]:
    if name == FINAL_INTERNAL_NAME:
        return PUBLIC_NAME
    if name in EXCLUDED_INTERNAL_NAMES:
        return None
    return name


def read_main_rows(data_file: Path) -> list[dict[str, object]]:
    """Read E4 main-comparison rows from the authoritative claim_full_raw.csv.

    claim_full_raw mixes every experiment and weight profile, so filter to the
    E4 main comparison under the equal-weight profile; this is the same data that
    backs tab:main_makespan / tab:main_lbr, keeping figures and tables consistent.
    """
    rows: list[dict[str, object]] = []
    with data_file.open(newline="", encoding="utf-8-sig") as f:
        reader = csv.DictReader(f)
        for row in reader:
            if row.get("experiment") != "E4_main" or row.get("weightProfile") != "W0_equal":
                continue
            algo = public_algo_name(row["algorithm"])
            if algo is None or algo not in META_ALGOS:
                continue
            rows.append(
                {
                    "Algorithm": algo,
                    "TaskCount": int(row["taskCount"]),
                    "Seed": int(row["seed"]),
                    "Makespan": float(row["makespan"]),
                    "LoadBalanceRatio": float(row["imbalance"]),
                }
            )
    return rows


def read_weight_rank_values(weight_file: Path) -> dict[str, dict[str, float]]:
    values: dict[str, dict[str, float]] = {}
    required_columns = ["Config"] + META_ALGOS_HEATMAP
    with weight_file.open(newline="", encoding="utf-8-sig") as f:
        reader = csv.DictReader(f)
        missing = [column for column in required_columns if column not in (reader.fieldnames or [])]
        if missing:
            raise ValueError(f"Missing column(s) in {weight_file}: {', '.join(missing)}")
        for row in reader:
            config = row["Config"].strip()
            values[config] = {algo: float(row[algo]) for algo in META_ALGOS_HEATMAP}
    if not values:
        raise ValueError(f"No rows found in {weight_file}")
    return values


def read_cec_ranking(cec_file: Path) -> list[tuple[str, float]]:
    rows: list[tuple[str, float]] = []
    with cec_file.open(newline="", encoding="utf-8-sig") as f:
        reader = csv.DictReader(f)
        missing = [column for column in ["Algorithm", "AverageRank"] if column not in (reader.fieldnames or [])]
        if missing:
            raise ValueError(f"Missing column(s) in {cec_file}: {', '.join(missing)}")
        for row in reader:
            algo = row["Algorithm"].strip()
            if algo in CEC_ALGOS:
                rows.append((algo, float(row["AverageRank"])))
    if sorted(algo for algo, _ in rows) != sorted(CEC_ALGOS):
        raise ValueError(f"{cec_file} must contain exactly these algorithms: {', '.join(CEC_ALGOS)}")
    return sorted(rows, key=lambda item: item[1])


def average_tie_ranks(values: dict[str, float]) -> dict[str, float]:
    ordered = sorted(values.items(), key=lambda item: item[1])
    ranks: dict[str, float] = {}
    i = 0
    while i < len(ordered):
        j = i + 1
        while j < len(ordered) and math.isclose(ordered[j][1], ordered[i][1], rel_tol=0.0, abs_tol=1e-12):
            j += 1
        avg_rank = (i + 1 + j) / 2.0
        for k in range(i, j):
            ranks[ordered[k][0]] = avg_rank
        i = j
    return ranks


def compute_average_ranks(rows: list[dict[str, object]]) -> dict[str, float]:
    blocks: dict[tuple[int, int], dict[str, float]] = defaultdict(dict)
    for row in rows:
        blocks[(int(row["TaskCount"]), int(row["Seed"]))][str(row["Algorithm"])] = float(row["Makespan"])

    rank_lists: dict[str, list[float]] = defaultdict(list)
    for values in blocks.values():
        if all(algo in values for algo in META_ALGOS):
            for algo, rank in average_tie_ranks(values).items():
                rank_lists[algo].append(rank)

    return {algo: statistics.mean(rank_lists[algo]) for algo in META_ALGOS}


def friedman_chi_square(avg_ranks: dict[str, float], block_count: int) -> float:
    k = len(avg_ranks)
    return (12 * block_count / (k * (k + 1))) * sum(rank * rank for rank in avg_ranks.values()) - 3 * block_count * (k + 1)


def plot_cec_ranking(cec_ranks: list[tuple[str, float]], fig_dir: Path) -> None:
    labels = [algo for algo, _ in cec_ranks]
    values = [rank for _, rank in cec_ranks]

    set_bar_grouped_hatch_style()
    fig, ax = plt.subplots(figsize=(8.8, 5.0))
    bars = ax.bar(
        labels,
        values,
        color=[COLORS[algo] for algo in labels],
        edgecolor="white",
        linewidth=0.8,
        zorder=2,
    )
    for bar, algo, value in zip(bars, labels, values):
        ax.text(
            bar.get_x() + bar.get_width() / 2,
            bar.get_height() + 0.08,
            f"{value:.2f}",
            ha="center",
            va="bottom",
            fontsize=8.7,
            fontweight="normal",
            color="black",
            zorder=3,
        )

    ax.text(
        0.04,
        0.92,
        "Friedman chi-square=156.04\np<0.0001\nNemenyi CD=1.673",
        transform=ax.transAxes,
        ha="left",
        va="top",
        fontsize=9,
        bbox={"boxstyle": "round,pad=0.25", "facecolor": "#fff7df", "edgecolor": "#b59b5b", "alpha": 0.9},
    )
    ax.set_ylabel("Friedman average rank (lower is better)")
    ax.set_xlabel("Continuous optimizer")
    ax.set_title("CEC2017 Benchmark Ranking")
    ax.set_ylim(0, max(values) + 1.0)
    style_bar_axis(ax)
    fig.tight_layout()
    fig.savefig(fig_dir / "fig1_cec_ranking.png", dpi=300, bbox_inches="tight")
    plt.close(fig)


def plot_final_ranking(rows: list[dict[str, object]], fig_dir: Path) -> None:
    avg_ranks = compute_average_ranks(rows)
    ordered = sorted(avg_ranks, key=avg_ranks.get)
    block_count = len({(row["TaskCount"], row["Seed"]) for row in rows})
    chi2 = friedman_chi_square(avg_ranks, block_count)

    set_bar_grouped_hatch_style()
    fig, ax = plt.subplots(figsize=(9.2, 5.2))
    values = [avg_ranks[algo] for algo in ordered]
    bars = ax.bar(
        ordered,
        values,
        color=[COLORS[algo] for algo in ordered],
        edgecolor="white",
        linewidth=0.8,
        zorder=2,
    )
    for bar, algo, value in zip(bars, ordered, values):
        ax.text(
            bar.get_x() + bar.get_width() / 2,
            bar.get_height() + 0.08,
            f"{value:.3f}",
            ha="center",
            va="bottom",
            fontsize=8.7,
            fontweight="normal",
            color="black",
            zorder=3,
        )

    ax.text(
        0.02,
        0.92,
        f"Friedman chi-square={chi2:.1f}\np<1e-70\nNemenyi CD~0.98",
        transform=ax.transAxes,
        ha="left",
        va="top",
        fontsize=9,
        bbox={"boxstyle": "round,pad=0.25", "facecolor": "#fff7df", "edgecolor": "#b59b5b", "alpha": 0.9},
    )
    ax.set_ylabel("Friedman average rank (lower is better)")
    ax.set_xlabel("Meta-heuristic scheduler")
    ax.set_title("Main Comparison: LSCBO vs Meta-Heuristic Cloud Schedulers")
    ax.set_ylim(0, max(values) + 1.0)
    style_bar_axis(ax)
    fig.autofmt_xdate(rotation=25, ha="right")
    fig.tight_layout()
    fig.savefig(fig_dir / "fig_E4_final_ranking.png", dpi=300, bbox_inches="tight")
    plt.close(fig)


def plot_makespan_scale(rows: list[dict[str, object]], fig_dir: Path) -> None:
    grouped: dict[str, dict[int, list[float]]] = defaultdict(lambda: defaultdict(list))
    for row in rows:
        grouped[str(row["Algorithm"])][int(row["TaskCount"])].append(float(row["Makespan"]))

    task_counts = sorted({int(row["TaskCount"]) for row in rows})
    set_line_confidence_style()
    fig, ax = plt.subplots(figsize=(10.2, 5.6))

    for algo in META_ALGOS:
        means = [statistics.mean(grouped[algo][task]) for task in task_counts]
        stds = [statistics.stdev(grouped[algo][task]) if len(grouped[algo][task]) > 1 else 0.0 for task in task_counts]
        lower = [mean - std for mean, std in zip(means, stds)]
        upper = [mean + std for mean, std in zip(means, stds)]
        ax.plot(
            task_counts,
            means,
            marker=MARKERS[algo],
            linewidth=1.8,
            markersize=5.4,
            color=COLORS[algo],
            label=algo,
        )
        ax.fill_between(task_counts, lower, upper, color=COLORS[algo], alpha=0.08)

    ax.set_xlabel("Task count (N)")
    ax.set_ylabel("Makespan (s)")
    ax.set_title("Makespan vs Task Scale (mean +/- std, 30 seeds)")
    ax.set_xticks(task_counts)
    style_confidence_axis(ax)
    ax.legend(framealpha=0, edgecolor="none", ncol=3, loc="upper left")
    fig.tight_layout()
    fig.savefig(fig_dir / "fig_E4_makespan_scale.png", dpi=300, bbox_inches="tight")
    plt.close(fig)


def subset_ranks_from_weight_values(weight_values: dict[str, dict[str, float]]) -> tuple[list[str], list[list[float]]]:
    configs = sorted(weight_values)
    matrix: list[list[float]] = []
    for cfg in configs:
        ranks = average_tie_ranks(weight_values[cfg])
        matrix.append([ranks[algo] for algo in META_ALGOS_HEATMAP])
    return configs, matrix


def plot_weight_rank_heatmap(weight_values: dict[str, dict[str, float]], fig_dir: Path) -> None:
    configs, matrix = subset_ranks_from_weight_values(weight_values)

    set_training_curve_style()
    fig, ax = plt.subplots(figsize=(8.2, 4.8))
    image = ax.imshow(matrix, cmap="RdYlGn_r", vmin=1, vmax=len(META_ALGOS_HEATMAP), aspect="auto")
    ax.set_xticks(range(len(META_ALGOS_HEATMAP)))
    ax.set_xticklabels(META_ALGOS_HEATMAP, rotation=35, ha="right")
    ax.set_yticks(range(len(configs)))
    ax.set_yticklabels(configs)

    for i, row in enumerate(matrix):
        for j, value in enumerate(row):
            color = "white" if value >= 6.5 else "black"
            ax.text(j, i, f"{value:.1f}", ha="center", va="center", color=color, fontweight="bold", fontsize=10)

    ax.set_title("Meta-Heuristic Rank Stability Across Weight Configurations", pad=10)
    cbar = fig.colorbar(image, ax=ax)
    cbar.set_label("Subset average rank (lower is better)")
    style_training_axis(ax)
    fig.tight_layout()
    fig.savefig(fig_dir / "fig_E1_rank_heatmap.png", dpi=300, bbox_inches="tight")
    plt.close(fig)


def main() -> int:
    args = parse_args()
    manuscript_dir = args.manuscript_dir.resolve()
    data_file = manuscript_dir / "data" / "formal_20260609_231814" / "claim_full_raw.csv"
    weight_file = manuscript_dir / "data" / WEIGHT_RANK_FILENAME
    cec_file = manuscript_dir / "data" / CEC_RANK_FILENAME
    fig_dir = manuscript_dir / "figures"
    if not data_file.exists():
        raise FileNotFoundError(f"Cannot find input data file: {data_file}")
    if not weight_file.exists():
        raise FileNotFoundError(f"Cannot find weight-rank data file: {weight_file}")
    if not cec_file.exists():
        raise FileNotFoundError(f"Cannot find CEC ranking data file: {cec_file}")
    fig_dir.mkdir(parents=True, exist_ok=True)

    rows = read_main_rows(data_file)
    weight_values = read_weight_rank_values(weight_file)
    cec_ranks = read_cec_ranking(cec_file)
    plot_cec_ranking(cec_ranks, fig_dir)
    plot_final_ranking(rows, fig_dir)
    plot_makespan_scale(rows, fig_dir)
    plot_weight_rank_heatmap(weight_values, fig_dir)
    output_names = [
        "fig1_cec_ranking.png",
        "fig_E4_final_ranking.png",
        "fig_E4_makespan_scale.png",
        "fig_E1_rank_heatmap.png",
    ]
    mirror_figures_to_program_folder(manuscript_dir, output_names)

    print("Regenerated figures with public label 'LSCBO':")
    print(f"  CEC data: {cec_file}")
    print(f"  weight data: {weight_file}")
    print(f"  {fig_dir / 'fig1_cec_ranking.png'}")
    print(f"  {fig_dir / 'fig_E4_final_ranking.png'}")
    print(f"  {fig_dir / 'fig_E4_makespan_scale.png'}")
    print(f"  {fig_dir / 'fig_E1_rank_heatmap.png'}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

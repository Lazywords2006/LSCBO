#!/usr/bin/env python3
"""Generate additional LSCBO manuscript evidence figures."""

from __future__ import annotations

import argparse
import csv
import math
import shutil
from collections import defaultdict
from pathlib import Path

import matplotlib.pyplot as plt
from matplotlib.patches import FancyArrowPatch, Rectangle


COLORS = {
    "LSCBO": "#C0392B",
    "CBO": "#A9A9A9",
    "GTO": "#3A8B3A",
    "WOA": "#E8845A",
    "HHO": "#6AA6D8",
    "GWO": "#7E57C2",
    "DBO": "#8C6D62",
    "PSO": "#BCBCBC",
    "AOA": "#707070",
}
MARKERS = {
    "LSCBO": "o",
    "CBO": "X",
    "GTO": "s",
    "WOA": "^",
    "HHO": "D",
    "GWO": "v",
    "DBO": "P",
    "PSO": "h",
    "AOA": "*",
}


def set_line_confidence_style() -> None:
    plt.rcParams.update(
        {
            "text.usetex": False,
            "font.family": "serif",
            "font.serif": ["Computer Modern Roman", "STIX Two Text", "DejaVu Serif"],
            "axes.unicode_minus": False,
        }
    )


def set_training_curve_style() -> None:
    plt.rcParams.update(
        {
            "text.usetex": False,
            "font.family": "sans-serif",
            "font.sans-serif": ["DejaVu Sans", "Arial", "Helvetica"],
            "axes.unicode_minus": False,
        }
    )


def style_confidence_axis(ax) -> None:
    for side, spine in ax.spines.items():
        spine.set_visible(side in ("left", "bottom"))
        if spine.get_visible():
            spine.set_linewidth(0.9)
            spine.set_color("#333333")
    ax.tick_params(direction="in", length=4.5, width=1.0, labelsize=9.5)
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
    manuscript_dir = script_dir.parent
    default_claim = latest_claim_raw(manuscript_dir)
    parser = argparse.ArgumentParser()
    parser.add_argument("--manuscript-dir", type=Path, default=manuscript_dir)
    parser.add_argument("--claim-raw", type=Path, default=default_claim)
    parser.add_argument("--convergence-dir", type=Path, default=default_convergence_dir(manuscript_dir))
    return parser.parse_args()


def default_convergence_dir(manuscript_dir: Path) -> Path:
    for parent in manuscript_dir.parents:
        candidate = parent / "论文程序及图表" / "实验数据" / "CloudSim" / "convergence"
        if candidate.is_dir():
            return candidate
    return manuscript_dir / "data"


def latest_claim_raw(manuscript_dir: Path) -> Path | None:
    for parent in manuscript_dir.parents:
        root = parent / "论文程序及图表" / "原始程序" / "主张一致数据生成工程" / "results"
        if not root.is_dir():
            continue
        files = sorted(root.glob("full*/claim_full_raw.csv"), key=lambda p: p.stat().st_mtime, reverse=True)
        if files:
            return files[0]
    return None


def add_box(ax, xy: tuple[float, float], width: float, height: float, text: str,
            face: str = "#f7f7f7", edge: str = "#333333") -> None:
    box = Rectangle(xy, width, height, facecolor=face, edgecolor=edge, linewidth=1.2)
    ax.add_patch(box)
    ax.text(xy[0] + width / 2, xy[1] + height / 2, text, ha="center", va="center", fontsize=10)


def add_arrow(ax, start: tuple[float, float], end: tuple[float, float]) -> None:
    arrow = FancyArrowPatch(start, end, arrowstyle="->", mutation_scale=14, linewidth=1.2, color="#333333")
    ax.add_patch(arrow)


def plot_scheduling_model(fig_dir: Path) -> None:
    set_training_curve_style()
    plt.rcParams.update({"font.size": 10})
    fig, ax = plt.subplots(figsize=(10.2, 4.6))
    ax.set_xlim(0, 10)
    ax.set_ylim(0, 4.6)
    ax.axis("off")

    add_box(ax, (0.2, 2.9), 1.8, 0.9, "Tasks\nMI, seed", "#eaf2ff")
    add_box(ax, (0.2, 0.8), 1.8, 0.9, "VMs\nMIPS, cost", "#eaf2ff")
    add_box(ax, (2.6, 1.85), 1.7, 0.9, "SPV+MFD\nschedule", "#fff4df")
    add_box(ax, (4.8, 1.85), 1.85, 0.9, "Multi-cost\nobjective", "#f1f8e9")
    add_box(ax, (7.1, 2.75), 1.8, 0.9, "Task\nreassignment", "#fdecea")
    add_box(ax, (7.1, 0.95), 1.8, 0.9, "CloudSim Plus\nvalidation", "#eef7f4")

    add_arrow(ax, (2.0, 3.35), (2.6, 2.55))
    add_arrow(ax, (2.0, 1.25), (2.6, 2.05))
    add_arrow(ax, (4.3, 2.3), (4.8, 2.3))
    add_arrow(ax, (6.65, 2.3), (7.1, 3.1))
    add_arrow(ax, (6.65, 2.3), (7.1, 1.4))
    add_arrow(ax, (8.9, 3.2), (9.55, 3.2))
    ax.text(9.65, 3.2, "selected schedule", ha="left", va="center", fontsize=10, fontweight="bold")
    fig.tight_layout()
    fig.savefig(fig_dir / "fig_lscbo_scheduling_model.png", dpi=300, bbox_inches="tight")
    plt.close(fig)


def plot_lscbo_workflow(fig_dir: Path) -> None:
    set_training_curve_style()
    plt.rcParams.update({"font.size": 10})
    fig, ax = plt.subplots(figsize=(10.2, 4.6))
    ax.set_xlim(0, 10)
    ax.set_ylim(0, 4.6)
    ax.axis("off")

    boxes = [
        ((0.25, 2.0), "Initialize\npopulation"),
        ((2.0, 2.0), "Levy-assisted\nsearch"),
        ((3.75, 2.0), "Encircling\nphase"),
        ((5.5, 2.0), "Attacking\nphase"),
        ((7.25, 2.0), "SPV+MFD\nmapping"),
        ((7.25, 0.55), "Multi-cost\nacceptance"),
        ((5.5, 0.55), "Local search\nevery 10 iters"),
        ((3.75, 0.55), "Update\nbest schedule"),
    ]
    for xy, text in boxes:
        add_box(ax, xy, 1.35, 0.85, text, "#f7f7f7")
    for x in [1.6, 3.35, 5.1, 6.85]:
        add_arrow(ax, (x, 2.42), (x + 0.35, 2.42))
    add_arrow(ax, (7.92, 2.0), (7.92, 1.4))
    add_arrow(ax, (7.25, 0.98), (6.85, 0.98))
    add_arrow(ax, (5.5, 0.98), (5.1, 0.98))
    add_arrow(ax, (4.42, 1.4), (4.42, 2.0))
    ax.text(0.25, 3.55, "Continuous search loop", ha="left", fontsize=11, fontweight="bold")
    ax.text(5.5, 0.15, "Scheduling-specific improvement loop", ha="left", fontsize=11, fontweight="bold")
    fig.tight_layout()
    fig.savefig(fig_dir / "fig_lscbo_workflow.png", dpi=300, bbox_inches="tight")
    plt.close(fig)


def read_claim_rows(claim_raw: Path) -> list[dict[str, str]]:
    if claim_raw is None or not claim_raw.exists():
        raise FileNotFoundError("Claim-preserving full raw CSV was not found.")
    with claim_raw.open(newline="", encoding="utf-8-sig") as f:
        return list(csv.DictReader(f))


def mean(values: list[float]) -> float:
    return sum(values) / len(values)


def plot_multicost_profile(claim_rows: list[dict[str, str]], fig_dir: Path) -> None:
    metrics = [
        ("makespan", "Makespan"),
        ("energy", "Energy"),
        ("cost", "Cost"),
        ("imbalance", "Load imbalance"),
        ("objective", "Overall objective"),
    ]
    algorithms = ["LSCBO", "GTO", "WOA", "HHO", "GWO", "DBO", "CBO", "AOA", "PSO"]
    grouped: dict[str, dict[int, dict[str, list[float]]]] = defaultdict(lambda: defaultdict(lambda: defaultdict(list)))
    for row in claim_rows:
        if row["experiment"] != "E4_main" or row["weightProfile"] != "W0_equal":
            continue
        algo = row["algorithm"]
        if algo not in algorithms:
            continue
        task_count = int(row["taskCount"])
        for key, _ in metrics:
            grouped[algo][task_count][key].append(float(row[key]))

    task_counts = sorted({task for algo in algorithms for task in grouped[algo]})
    if not task_counts:
        raise ValueError("No E4_main rows found in claim-preserving full CSV.")

    set_line_confidence_style()
    fig, axes = plt.subplots(2, 3, figsize=(11.6, 6.8))
    flat_axes = list(axes.ravel())
    for ax, (metric, label) in zip(flat_axes, metrics):
        for algo in algorithms:
            values = []
            for task in task_counts:
                base = mean(grouped["CBO"][task][metric])
                current = mean(grouped[algo][task][metric])
                values.append(current / base if base else math.nan)
            ax.plot(
                task_counts,
                values,
                marker=MARKERS[algo],
                color=COLORS[algo],
                linewidth=1.8,
                markersize=5.4,
                label=algo,
            )
        ax.axhline(1.0, color="#999999", linewidth=1.4, linestyle=(0, (1, 2)))
        ax.set_title(label)
        ax.set_xlabel("Task count")
        ax.set_ylabel("Relative to CBO")
        style_confidence_axis(ax)
    flat_axes[-1].axis("off")
    handles, labels = flat_axes[0].get_legend_handles_labels()
    flat_axes[-1].legend(handles, labels, loc="center", framealpha=0, edgecolor="none")
    fig.suptitle("Multi-Cost Profile (E4 main comparison, lower is better)", y=0.98, fontsize=12)
    fig.tight_layout(rect=(0, 0, 1, 0.96))
    fig.savefig(fig_dir / "fig_claim_multicost_profile.png", dpi=300, bbox_inches="tight")
    plt.close(fig)


def read_convergence_file(file_path: Path) -> tuple[list[int], list[float]]:
    iterations: list[int] = []
    values: list[float] = []
    with file_path.open(newline="", encoding="utf-8-sig") as f:
        for row in csv.DictReader(f):
            iterations.append(int(row["Iteration"]))
            values.append(float(row["BestFitness"]))
    return iterations, values


def plot_convergence_profile(convergence_dir: Path, fig_dir: Path) -> None:
    """LSCBO's own convergence across task scales (single algorithm, fixed seed).

    The cross-algorithm CloudSim convergence logs are heterogeneous (differing
    iteration counts and iteration-0 conventions across algorithms) and are not
    suitable for a fair side-by-side trace. The headline comparison is carried by
    the paired repeated-run tables and the Friedman/Wilcoxon tests instead. This
    figure is a diagnostic view of LSCBO's search behaviour as the problem grows.
    """
    scales = [100, 200, 300, 400, 500, 600, 700, 800, 900, 1000]
    set_training_curve_style()
    fig, ax = plt.subplots(figsize=(9.2, 5.2))
    cmap = plt.get_cmap("viridis")
    denom = max(1, len(scales) - 1)
    for idx, scale in enumerate(scales):
        path = convergence_dir / f"convergence_LSCBO_M{scale}_seed42.csv"
        if not path.exists():
            continue
        iterations, values = read_convergence_file(path)
        if not iterations:
            continue
        first = values[0] if values[0] else 1.0
        normalized = [value / first for value in values]
        ax.plot(
            iterations,
            normalized,
            color=cmap(idx / denom),
            linewidth=1.6,
            label=f"N={scale}",
        )
    ax.axhline(1.0, color="#999999", linewidth=1.2, linestyle="--")
    ax.set_xlabel("Iteration")
    ax.set_ylabel("Best fitness relative to iteration 0")
    ax.set_title("LSCBO Convergence Across Task Scales (seed=42)")
    style_training_axis(ax)
    ax.legend(
        frameon=True,
        facecolor="white",
        edgecolor="#AAAAAA",
        framealpha=1.0,
        ncol=2,
        loc="upper right",
        fontsize=9,
        title="Task scale",
    )
    fig.tight_layout()
    fig.savefig(fig_dir / "fig_lscbo_convergence_trace.png", dpi=300, bbox_inches="tight")
    plt.close(fig)


def mirror_figures(manuscript_dir: Path, names: list[str]) -> None:
    for parent in manuscript_dir.parents:
        target_dir = parent / "论文程序及图表" / "图表"
        if target_dir.is_dir():
            for name in names:
                shutil.copy2(manuscript_dir / "figures" / name, target_dir / name)
            return


def main() -> int:
    args = parse_args()
    manuscript_dir = args.manuscript_dir.resolve()
    fig_dir = manuscript_dir / "figures"
    fig_dir.mkdir(parents=True, exist_ok=True)
    claim_rows = read_claim_rows(args.claim_raw)

    plot_scheduling_model(fig_dir)
    plot_lscbo_workflow(fig_dir)
    plot_multicost_profile(claim_rows, fig_dir)
    plot_convergence_profile(args.convergence_dir.resolve(), fig_dir)
    names = [
        "fig_lscbo_scheduling_model.png",
        "fig_lscbo_workflow.png",
        "fig_claim_multicost_profile.png",
        "fig_lscbo_convergence_trace.png",
    ]
    mirror_figures(manuscript_dir, names)
    print("Generated LSCBO evidence figures:")
    print(f"  claim raw: {args.claim_raw}")
    for name in names:
        print(f"  {fig_dir / name}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

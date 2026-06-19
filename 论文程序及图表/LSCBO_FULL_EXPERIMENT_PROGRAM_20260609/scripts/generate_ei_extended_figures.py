#!/usr/bin/env python3
"""Generate the extended Evolutionary Intelligence manuscript figures.

The script reads only the locked CEC2017 convergence archive and the canonical
cloud-scheduling CSV. It does not rerun either experiment family.
"""

from __future__ import annotations

import argparse
import math
import re
import zipfile
from pathlib import Path

import matplotlib

matplotlib.use("Agg")

import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
import seaborn as sns
from matplotlib.colors import TwoSlopeNorm
from matplotlib.lines import Line2D
from matplotlib.patches import FancyArrowPatch, FancyBboxPatch, Polygon, Patch


RED = "#C0392B"
DARK_RED = "#8E2A20"
BLUE = "#3B6BB5"
GREEN = "#3A8B3A"
ORANGE = "#D9822B"
PURPLE = "#7A5195"
TEAL = "#2A9D8F"
GRAY = "#6F6F6F"
LIGHT_GRAY = "#E8E8E8"
TEXT = "#242424"


def configure_style() -> None:
    plt.rcParams.update(
        {
            "font.family": "sans-serif",
            "font.sans-serif": ["Arial", "Helvetica", "DejaVu Sans"],
            "font.size": 9,
            "axes.titlesize": 11,
            "axes.labelsize": 10,
            "xtick.labelsize": 8,
            "ytick.labelsize": 8,
            "legend.fontsize": 8,
            "figure.dpi": 150,
            "savefig.dpi": 300,
            "savefig.bbox": "tight",
            "savefig.facecolor": "white",
            "axes.spines.top": False,
            "axes.spines.right": False,
            "axes.unicode_minus": False,
            "hatch.color": "white",
        }
    )


def add_box(
    ax: plt.Axes,
    x: float,
    y: float,
    width: float,
    height: float,
    text: str,
    facecolor: str,
    edgecolor: str = "#555555",
    fontsize: float = 8.7,
    linewidth: float = 1.0,
    radius: float = 0.018,
    weight: str = "normal",
) -> FancyBboxPatch:
    patch = FancyBboxPatch(
        (x, y),
        width,
        height,
        boxstyle=f"round,pad=0.012,rounding_size={radius}",
        facecolor=facecolor,
        edgecolor=edgecolor,
        linewidth=linewidth,
        zorder=2,
    )
    ax.add_patch(patch)
    ax.text(
        x + width / 2,
        y + height / 2,
        text,
        ha="center",
        va="center",
        fontsize=fontsize,
        color=TEXT,
        weight=weight,
        linespacing=1.25,
        zorder=3,
    )
    return patch


def add_arrow(
    ax: plt.Axes,
    start: tuple[float, float],
    end: tuple[float, float],
    color: str = "#555555",
    linewidth: float = 1.2,
    style: str = "-|>",
    connectionstyle: str = "arc3",
    linestyle: str = "-",
    mutation_scale: float = 12,
    zorder: int = 1,
) -> FancyArrowPatch:
    arrow = FancyArrowPatch(
        start,
        end,
        arrowstyle=style,
        color=color,
        linewidth=linewidth,
        mutation_scale=mutation_scale,
        connectionstyle=connectionstyle,
        linestyle=linestyle,
        shrinkA=1,
        shrinkB=1,
        zorder=zorder,
    )
    ax.add_patch(arrow)
    return arrow


def save_figure(fig: plt.Figure, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    fig.savefig(path, dpi=300, bbox_inches="tight", facecolor="white")
    plt.close(fig)
    print(f"wrote {path}")


def draw_scheduling_model(output: Path) -> None:
    fig, ax = plt.subplots(figsize=(12.8, 5.15))
    ax.set_xlim(0, 1)
    ax.set_ylim(0, 1)
    ax.axis("off")

    ax.text(
        0.5,
        0.96,
        "Multi-cost cloud scheduling model and evidence boundary",
        ha="center",
        va="top",
        fontsize=12,
        weight="bold",
        color=TEXT,
    )

    add_box(ax, 0.025, 0.63, 0.12, 0.16, "Task set\n$MI_i$, seed", "#E7EFFA")
    add_box(ax, 0.025, 0.31, 0.12, 0.16, "VM set\n$MIPS_j$, rate", "#E7EFFA")
    add_box(
        ax,
        0.19,
        0.46,
        0.15,
        0.20,
        "Greedy-LPT\nreported baseline\nreference $c_k$\nand warm start",
        "#FFF0D5",
        edgecolor=ORANGE,
        weight="bold",
    )
    add_box(
        ax,
        0.39,
        0.63,
        0.19,
        0.18,
        "Population search\n29 random + 1 Greedy-LPT\nCBO / PSO / GWO / WOA / AOA\nLSCBO core",
        "#E7F4E8",
        edgecolor=GREEN,
    )
    add_box(
        ax,
        0.39,
        0.30,
        0.19,
        0.18,
        "Direct assignment decoder\n$X_i\\in[0,1]$\n$VM_i=\\min(M-1,\\lfloor MX_i\\rfloor)$",
        "#E8F3F4",
        edgecolor=TEAL,
    )
    add_box(
        ax,
        0.63,
        0.46,
        0.16,
        0.20,
        "Multi-cost evaluation\nMakespan | Energy\nCost | LBR\nNormalized objective $F$",
        "#F1EAF7",
        edgecolor=PURPLE,
        weight="bold",
    )
    add_box(
        ax,
        0.835,
        0.64,
        0.14,
        0.17,
        "Pareto-safe\npair reassignment\nLSCBO only",
        "#FBE5E2",
        edgecolor=RED,
        weight="bold",
    )
    add_box(
        ax,
        0.835,
        0.34,
        0.14,
        0.15,
        "Selected schedule\ncomponent-wise\nnon-worsening",
        "#E7F4E8",
        edgecolor=GREEN,
        weight="bold",
    )
    add_box(
        ax,
        0.63,
        0.13,
        0.16,
        0.14,
        "CloudSim Plus\nsmoke replay\n(executability check)",
        "#EFEFEF",
        edgecolor=GRAY,
    )

    add_arrow(ax, (0.145, 0.70), (0.19, 0.59))
    add_arrow(ax, (0.145, 0.39), (0.19, 0.52))
    add_arrow(ax, (0.34, 0.59), (0.39, 0.70))
    add_arrow(ax, (0.485, 0.63), (0.485, 0.48))
    add_arrow(ax, (0.58, 0.39), (0.63, 0.53))
    add_arrow(ax, (0.79, 0.57), (0.835, 0.70), color=RED)
    add_arrow(ax, (0.905, 0.64), (0.905, 0.49), color=RED)
    add_arrow(ax, (0.835, 0.41), (0.79, 0.23), color=GRAY)
    add_arrow(
        ax,
        (0.63, 0.50),
        (0.58, 0.73),
        color=GREEN,
        connectionstyle="arc3,rad=0.28",
        linewidth=1.0,
    )
    add_arrow(
        ax,
        (0.34, 0.49),
        (0.63, 0.49),
        color=ORANGE,
        linestyle="--",
        linewidth=1.0,
    )
    ax.text(0.49, 0.505, "normalizers $c_k$", ha="center", va="bottom", fontsize=7.8, color=ORANGE)
    ax.text(0.61, 0.79, "evaluate candidates", ha="center", fontsize=7.8, color=GREEN)
    ax.text(
        0.5,
        0.045,
        "Formal tables use the stated analytical model; CloudSim Plus is retained as a replay-capable implementation check.",
        ha="center",
        va="bottom",
        fontsize=8.2,
        color=GRAY,
    )

    save_figure(fig, output / "fig_lscbo_scheduling_model.png")


def draw_mechanism_workflow(output: Path) -> None:
    fig, axes = plt.subplots(1, 2, figsize=(13.2, 5.7), gridspec_kw={"wspace": 0.12})

    for ax in axes:
        ax.set_xlim(0, 1)
        ax.set_ylim(0, 1)
        ax.axis("off")

    left, right = axes
    left.text(
        0.02,
        0.96,
        "(a) Budgeted LSCBO continuous core ($B_g=600$)",
        ha="left",
        va="top",
        fontsize=11,
        weight="bold",
        color=TEXT,
    )

    stages = [
        (0.03, "Common\npopulation\n1 Greedy-LPT\n+ 29 random", "#FFF0D5", ORANGE),
        (0.23, "Phase I\nsearching\nCBO tanh candidate\n+ Lévy trial ($\\rho_L=0.30$)", "#FBE5E2", RED),
        (0.45, "Phase II\nencircling\nTwo-coordinate\npair rotation", "#E7EFFA", BLUE),
        (0.67, "Phase III\nattacking\nHalfway move to\npopulation leader", "#E7F4E8", GREEN),
        (0.89, "Best\ndecoded\nschedule", "#EFEFEF", GRAY),
    ]
    widths = [0.17, 0.18, 0.18, 0.18, 0.09]
    for idx, ((x, text, face, edge), width) in enumerate(zip(stages, widths)):
        add_box(left, x, 0.51, width, 0.27, text, face, edgecolor=edge, fontsize=6.8, weight="bold" if idx in (0, 4) else "normal")
        if idx:
            prev_x = stages[idx - 1][0]
            prev_w = widths[idx - 1]
            add_arrow(left, (prev_x + prev_w, 0.645), (x, 0.645), color=edge)
    left.text(0.33, 0.39, "first third", ha="center", fontsize=8, color=RED)
    left.text(0.55, 0.39, "second third", ha="center", fontsize=8, color=BLUE)
    left.text(0.77, 0.39, "final third", ha="center", fontsize=8, color=GREEN)
    left.text(
        0.5,
        0.18,
        "Every original and auxiliary candidate consumes one objective evaluation.\nThe best-so-far schedule cannot be worse than the common Greedy-LPT warm start.",
        ha="center",
        va="center",
        fontsize=8.2,
        color=GRAY,
        linespacing=1.35,
    )

    right.text(
        0.02,
        0.96,
        "(b) Pareto-safe pair reassignment ($B_{ls}=5400$)",
        ha="left",
        va="top",
        fontsize=11,
        weight="bold",
        color=TEXT,
    )
    add_box(right, 0.02, 0.66, 0.14, 0.16, "Incumbent\nschedule $s^*$", "#FFF0D5", edgecolor=ORANGE, weight="bold")
    add_box(right, 0.205, 0.66, 0.15, 0.16, "Sample tasks\n$p$ and $q$ on\ndifferent VMs", "#E7EFFA", edgecolor=BLUE)
    add_box(right, 0.40, 0.66, 0.13, 0.16, "Swap their\nVM assignments", "#E8F3F4", edgecolor=TEAL)
    add_box(right, 0.575, 0.66, 0.17, 0.16, "Evaluate\n$T_{ms}$, $E$, $C$, LBR, $F$", "#F1EAF7", edgecolor=PURPLE)

    diamond = Polygon(
        [[0.84, 0.74], [0.91, 0.84], [0.98, 0.74], [0.91, 0.64]],
        closed=True,
        facecolor="#FBE5E2",
        edgecolor=RED,
        linewidth=1.2,
        zorder=2,
    )
    right.add_patch(diamond)
    right.text(0.91, 0.74, "All four\nno worse?", ha="center", va="center", fontsize=8.0, weight="bold", zorder=3)

    add_box(right, 0.76, 0.32, 0.14, 0.14, "Round-best\nadmissible trial\n(minimum $F$)", "#E7F4E8", edgecolor=GREEN)
    add_box(right, 0.47, 0.32, 0.17, 0.14, "After up to 400\nevaluated trials:\nupdate $s^*$", "#E7F4E8", edgecolor=GREEN, weight="bold")
    add_box(right, 0.85, 0.10, 0.11, 0.11, "Reject\ntrial", "#EFEFEF", edgecolor=GRAY)

    add_arrow(right, (0.16, 0.74), (0.205, 0.74), color=BLUE)
    add_arrow(right, (0.355, 0.74), (0.40, 0.74), color=TEAL)
    add_arrow(right, (0.53, 0.74), (0.575, 0.74), color=PURPLE)
    add_arrow(right, (0.745, 0.74), (0.84, 0.74), color=RED)
    add_arrow(right, (0.91, 0.64), (0.84, 0.46), color=GREEN)
    right.text(0.865, 0.54, "yes", ha="right", fontsize=8, color=GREEN, weight="bold")
    add_arrow(right, (0.965, 0.68), (0.91, 0.21), color=GRAY, connectionstyle="arc3,rad=-0.15")
    right.text(0.975, 0.49, "no", ha="right", fontsize=8, color=GRAY, weight="bold")
    add_arrow(right, (0.76, 0.39), (0.64, 0.39), color=GREEN)
    add_arrow(
        right,
        (0.47, 0.39),
        (0.09, 0.66),
        color=GREEN,
        connectionstyle="arc3,rad=-0.30",
        linewidth=1.3,
    )
    right.text(
        0.31,
        0.18,
        "The local random stream is fixed by workload, VM profile, seed, and weight profile,\nso host variants receive the same sampled neighborhood.",
        ha="center",
        va="center",
        fontsize=8.0,
        color=GRAY,
        linespacing=1.3,
    )

    save_figure(fig, output / "fig_lscbo_mechanism_workflow.png")


def parse_convergence(archive: Path) -> pd.DataFrame:
    member = "LSCBO_Anonymized_Reproducibility/cec2017/results/convergence_checkpoints.csv"
    with zipfile.ZipFile(archive) as zf:
        with zf.open(member) as stream:
            data = pd.read_csv(stream)

    parsed = data["task_key"].str.extract(r"cec2017-d30-(.+?)-f(\d+)-r(\d+)-s")
    if parsed.isna().any().any():
        raise ValueError("Unexpected CEC2017 task key format")
    data["algorithm"] = parsed[0]
    data["function"] = parsed[1].astype(int)
    data["run"] = parsed[2].astype(int)

    if len(data) != 783_000:
        raise ValueError(f"Expected 783000 convergence rows, found {len(data)}")
    if data["function"].nunique() != 29 or data["run"].nunique() != 30:
        raise ValueError("CEC2017 convergence coverage is incomplete")
    return data


def draw_cec_convergence(archive: Path, output: Path) -> None:
    data = parse_convergence(archive)
    means = data.groupby(["evaluations", "function", "algorithm"], as_index=False)["error"].mean()
    means["rank"] = means.groupby(["evaluations", "function"])["error"].rank(method="average")
    summary = (
        means.groupby(["evaluations", "algorithm"])["rank"]
        .agg(["mean", "std", "count"])
        .reset_index()
    )
    summary["ci95"] = 1.96 * summary["std"] / np.sqrt(summary["count"])

    expected = {
        "LSCBO": 1.6896551724,
        "SA": 2.6551724138,
        "PSO": 3.3448275862,
        "HLBO": 4.6206896552,
        "GWCA": 4.7931034483,
        "CBO": 5.0344827586,
        "GWO": 6.8620689655,
        "AOA": 7.4482758621,
        "WOA": 8.5517241379,
    }
    final = summary[summary["evaluations"] == summary["evaluations"].max()].set_index("algorithm")["mean"]
    for algorithm, value in expected.items():
        if not math.isclose(float(final[algorithm]), value, rel_tol=0.0, abs_tol=1e-9):
            raise ValueError(f"Final rank mismatch for {algorithm}: {final[algorithm]} vs {value}")

    colors = {
        "LSCBO": RED,
        "SA": BLUE,
        "PSO": GREEN,
        "HLBO": ORANGE,
        "GWCA": PURPLE,
        "CBO": "#555555",
        "GWO": "#8C6D31",
        "AOA": "#7F7F7F",
        "WOA": "#B0B0B0",
    }
    line_styles = {"CBO": "--", "AOA": ":", "WOA": ":"}
    widths = {algorithm: 1.35 for algorithm in expected}
    widths.update({"LSCBO": 2.5, "SA": 2.0, "CBO": 1.9})

    fig, ax = plt.subplots(figsize=(7.5, 4.65))
    draw_order = ["WOA", "AOA", "GWO", "GWCA", "HLBO", "PSO", "CBO", "SA", "LSCBO"]
    for algorithm in draw_order:
        subset = summary[summary["algorithm"] == algorithm].sort_values("evaluations")
        x = subset["evaluations"].to_numpy() / 1000.0
        y = subset["mean"].to_numpy()
        ci = subset["ci95"].to_numpy()
        if algorithm in {"LSCBO", "CBO"}:
            ax.fill_between(x, y - ci, y + ci, color=colors[algorithm], alpha=0.10, linewidth=0)
        ax.plot(
            x,
            y,
            color=colors[algorithm],
            linewidth=widths[algorithm],
            linestyle=line_styles.get(algorithm, "-"),
            label=algorithm,
            zorder=4 if algorithm == "LSCBO" else 2,
        )

    ax.set_xlabel("Objective evaluations (thousands)")
    ax.set_ylabel("Mean cross-function rank (lower is better)")
    ax.set_xlim(3, 300)
    ax.set_ylim(9.35, 0.65)
    ax.set_yticks(np.arange(1, 10))
    ax.grid(axis="y", color="#E6E6E6", linewidth=0.7, linestyle="--", zorder=0)
    ax.spines["left"].set_color("#444444")
    ax.spines["bottom"].set_color("#444444")
    ax.set_title("CEC2017 convergence under the complete 29-function protocol", pad=8)
    legend = ax.legend(ncol=3, frameon=False, loc="lower left", bbox_to_anchor=(0.0, 0.005))
    for label in legend.get_texts():
        if label.get_text() == "LSCBO":
            label.set_fontweight("bold")
            label.set_color(DARK_RED)
    ax.text(
        0.99,
        0.98,
        "Mean ranks use per-function mean error over 30 runs\nShading: 95% interval across 29 functions (LSCBO, CBO)",
        transform=ax.transAxes,
        ha="right",
        va="top",
        fontsize=7.7,
        color=GRAY,
    )

    save_figure(fig, output / "fig_cec_convergence_rank.png")


def draw_cloud_multi_algorithm(cloud_csv: Path, output: Path) -> None:
    data = pd.read_csv(cloud_csv)
    if len(data) != 5_310:
        raise ValueError(f"Expected 5310 canonical cloud rows, found {len(data)}")

    main = data[data["experiment"] == "E4_main"].copy()
    algorithms = ["GREEDY_LPT", "AOA", "WOA", "GWO", "PSO", "CBO", "LSCBO"]
    task_counts = [200, 500, 1000, 2000, 5000]
    if len(main) != 1_050:
        raise ValueError(f"Expected 1050 E4_main rows, found {len(main)}")
    if set(main["algorithm"]) != set(algorithms):
        raise ValueError("Unexpected E4_main algorithm set")
    if sorted(main["taskCount"].unique().tolist()) != task_counts:
        raise ValueError("Unexpected E4_main task-count coverage")

    metrics = ["objective", "makespan", "energy", "cost", "imbalance"]
    means = main.groupby(["taskCount", "algorithm"])[metrics].mean()
    expected_lscbo = {
        200: 0.982632,
        500: 0.982617,
        1000: 0.983700,
        2000: 0.983444,
        5000: 0.985240,
    }
    for task_count, expected in expected_lscbo.items():
        value = float(means.loc[(task_count, "LSCBO"), "objective"])
        if not math.isclose(value, expected, rel_tol=0.0, abs_tol=5e-7):
            raise ValueError(
                f"LSCBO objective mismatch at N={task_count}: {value} vs {expected}"
            )

    labels = {
        "LSCBO": "LSCBO + pair",
        "CBO": "CBO",
        "PSO": "PSO",
        "GWO": "GWO",
        "WOA": "WOA",
        "AOA": "AOA",
        "GREEDY_LPT": "Greedy-LPT",
    }
    colors = {
        "LSCBO": RED,
        "CBO": "#4D4D4D",
        "PSO": BLUE,
        "GWO": GREEN,
        "WOA": PURPLE,
        "AOA": ORANGE,
        "GREEDY_LPT": "#9A9A9A",
    }
    markers = {
        "LSCBO": "s",
        "CBO": "o",
        "PSO": "^",
        "GWO": "x",
        "WOA": "D",
        "AOA": "v",
        "GREEDY_LPT": None,
    }
    linestyles = {
        "LSCBO": "-",
        "CBO": "--",
        "PSO": "-.",
        "GWO": ":",
        "WOA": "--",
        "AOA": "-.",
        "GREEDY_LPT": ":",
    }
    draw_order = ["GREEDY_LPT", "AOA", "WOA", "GWO", "PSO", "CBO", "LSCBO"]
    x = np.arange(len(task_counts))

    fig, axes = plt.subplots(
        2,
        3,
        figsize=(11.2, 7.2),
        gridspec_kw={"wspace": 0.34, "hspace": 0.40},
    )
    plot_axes = axes.ravel()[:5]
    legend_ax = axes.ravel()[5]
    panel_specs = [
        ("objective", "(a) Reference-normalized objective", "Objective (lower is better)", (0.978, 1.002)),
        ("makespan", "(b) Makespan", "Improvement over Greedy-LPT (%)", (-0.018, 0.015)),
        ("energy", "(c) Energy", "Improvement over Greedy-LPT (%)", (-0.008, 0.078)),
        ("cost", "(d) Execution cost", "Improvement over Greedy-LPT (%)", (-0.003, 0.029)),
        ("imbalance", "(e) Load-balance ratio", "Improvement over Greedy-LPT (%)", (-0.4, 7.3)),
    ]

    for ax, (metric, title, ylabel, ylim) in zip(plot_axes, panel_specs):
        greedy = np.asarray(
            [means.loc[(task_count, "GREEDY_LPT"), metric] for task_count in task_counts],
            dtype=float,
        )
        for algorithm in draw_order:
            values = np.asarray(
                [means.loc[(task_count, algorithm), metric] for task_count in task_counts],
                dtype=float,
            )
            if metric != "objective":
                values = 100.0 * (greedy - values) / greedy
            ax.plot(
                x,
                values,
                color=colors[algorithm],
                linestyle=linestyles[algorithm],
                linewidth=2.4 if algorithm == "LSCBO" else 1.25,
                marker=markers[algorithm],
                markersize=5.2 if algorithm == "LSCBO" else 4.2,
                markerfacecolor="white" if markers[algorithm] not in {None, "x"} else colors[algorithm],
                markeredgewidth=1.0,
                label=labels[algorithm],
                zorder=5 if algorithm == "LSCBO" else 2,
            )
        ax.set_title(title, loc="left", pad=6, weight="bold", fontsize=11.5)
        ax.set_ylabel(ylabel, fontsize=10.5)
        ax.set_xlabel("Number of tasks", fontsize=10.5)
        ax.set_xticks(x)
        ax.set_xticklabels([str(value) for value in task_counts])
        ax.set_ylim(*ylim)
        ax.axhline(
            1.0 if metric == "objective" else 0.0,
            color="#777777",
            linestyle="--",
            linewidth=0.8,
            zorder=0,
        )
        ax.grid(axis="y", color="#E6E6E6", linestyle="--", linewidth=0.7, zorder=0)
        for spine in ax.spines.values():
            spine.set_visible(True)
            spine.set_linewidth(0.8)
            spine.set_color("#555555")
        ax.tick_params(direction="out", length=3.5, width=0.8, labelsize=9.2)

    plot_axes[0].text(
        0.98,
        0.10,
        "GWO, WOA, and AOA overlap Greedy-LPT",
        transform=plot_axes[0].transAxes,
        ha="right",
        va="bottom",
        fontsize=8.2,
        color=GRAY,
    )

    legend_ax.axis("off")
    handles = [
        Line2D(
            [0],
            [0],
            color=colors[algorithm],
            linestyle=linestyles[algorithm],
            linewidth=2.4 if algorithm == "LSCBO" else 1.4,
            marker=markers[algorithm],
            markersize=5.5,
            markerfacecolor="white" if markers[algorithm] not in {None, "x"} else colors[algorithm],
            label=labels[algorithm],
        )
        for algorithm in reversed(draw_order)
    ]
    legend_ax.legend(
        handles=handles,
        loc="upper left",
        frameon=True,
        facecolor="white",
        edgecolor="#BBBBBB",
        ncol=2,
        title="Compared methods",
        fontsize=9.0,
        title_fontsize=10,
    )
    legend_ax.text(
        0.0,
        0.48,
        "All points are means over 30 paired seeds.\n"
        "LSCBO + pair includes the Pareto-safe pair search;\n"
        "the other population methods use only their global search.\n"
        "Metric-specific vertical ranges expose the small differences.",
        transform=legend_ax.transAxes,
        ha="left",
        va="top",
        fontsize=9.0,
        color=TEXT,
        linespacing=1.45,
    )
    fig.suptitle(
        "Cloud scheduling comparison across algorithms and task scales",
        fontsize=13.5,
        weight="bold",
        y=0.995,
    )

    save_figure(fig, output / "fig_cloud_multi_algorithm_profiles.png")


def paired_improvement(
    data: pd.DataFrame,
    experiment: str,
    grouping: str,
    ordered_values: list[str],
) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
    subset = data[(data["experiment"] == experiment) & data["algorithm"].isin(["LSCBO", "GREEDY_LPT"])].copy()
    keys = list(dict.fromkeys([grouping, "taskCount", "seed", "vmProfile", "weightProfile"]))
    pivot = subset.pivot_table(index=keys, columns="algorithm", values="objective", aggfunc="first").reset_index()
    if pivot[["LSCBO", "GREEDY_LPT"]].isna().any().any():
        raise ValueError(f"Incomplete paired rows for {experiment}")
    pivot["improvement"] = 100.0 * (pivot["GREEDY_LPT"] - pivot["LSCBO"]) / pivot["GREEDY_LPT"]

    means = []
    intervals = []
    counts = []
    for value in ordered_values:
        values = pivot.loc[pivot[grouping] == value, "improvement"].to_numpy(dtype=float)
        if values.size == 0:
            raise ValueError(f"Missing {grouping}={value} in {experiment}")
        means.append(float(values.mean()))
        intervals.append(float(1.96 * values.std(ddof=1) / math.sqrt(values.size)))
        counts.append(values.size)
    return np.asarray(means), np.asarray(intervals), np.asarray(counts)


def draw_ablation_robustness(cloud_csv: Path, output: Path) -> None:
    data = pd.read_csv(cloud_csv)
    if len(data) != 5_310:
        raise ValueError(f"Expected 5310 canonical cloud rows, found {len(data)}")

    metrics = ["makespan", "energy", "cost", "imbalance", "objective"]
    ablation = data[data["experiment"] == "E2_ablation"].groupby("variant")[metrics].mean()
    greedy = ablation.loc["greedy_reference"]
    selected = [
        "objective_only_pair_ls",
        "LSCBO_full",
        "no_levy",
        "ls_only",
        "no_local_search",
        "pareto_relocate_ls",
    ]
    labels = [
        "Objective-only pair",
        "Pareto-safe pair (LSCBO)",
        "CBO + pair (no Levy)",
        "Pair search only",
        "No local search",
        "Single relocation",
    ]
    improvement = np.vstack([100.0 * (greedy - ablation.loc[name]) / greedy for name in selected])
    display = np.vectorize(lambda value: f"{value:.3f}")(improvement)
    clipped = np.clip(improvement, -0.05, 10.0)

    weight_values = [
        "W0_equal",
        "W1_makespan_priority",
        "W2_energy_priority",
        "W3_cost_priority",
        "W4_balance_priority",
    ]
    weight_labels = ["Equal", "Makespan", "Energy", "Cost", "Balance"]
    weight_mean, weight_ci, weight_n = paired_improvement(
        data, "weight_sensitivity", "weightProfile", weight_values
    )

    vm_values = [
        "near_homogeneous_1_5_to_1",
        "medium_5_to_1",
        "heterogeneous_20_to_1",
    ]
    vm_labels = ["VM 1.5:1", "VM 5:1", "VM 20:1"]
    vm_mean, vm_ci, vm_n = paired_improvement(data, "vm_heterogeneity", "vmProfile", vm_values)

    fig, (left, right) = plt.subplots(1, 2, figsize=(13.0, 5.35), gridspec_kw={"width_ratios": [1.24, 0.92], "wspace": 0.32})

    cmap = sns.diverging_palette(230, 15, as_cmap=True)
    norm = TwoSlopeNorm(vmin=-0.05, vcenter=0.0, vmax=10.0)
    sns.heatmap(
        clipped,
        annot=display,
        fmt="",
        cmap=cmap,
        norm=norm,
        linewidths=0.7,
        linecolor="white",
        xticklabels=["Makespan", "Energy", "Cost", "LBR", "Objective"],
        yticklabels=labels,
        cbar_kws={"label": "Improvement over Greedy-LPT (%)", "shrink": 0.84},
        annot_kws={"fontsize": 8},
        ax=left,
    )
    left.set_title("(a) Mechanism ablation: component-wise change", loc="left", pad=9)
    left.set_xlabel("")
    left.set_ylabel("")
    left.tick_params(axis="x", rotation=25)
    left.tick_params(axis="y", rotation=0)
    left.text(
        0.0,
        -0.20,
        "Positive values favor the variant; exact labels are shown. Color intensity is clipped at +10%.",
        transform=left.transAxes,
        ha="left",
        va="top",
        fontsize=7.8,
        color=GRAY,
    )

    category_labels = weight_labels + vm_labels
    values = np.concatenate([weight_mean, vm_mean])
    errors = np.concatenate([weight_ci, vm_ci])
    y = np.arange(len(values))
    colors = [RED] * len(weight_labels) + [BLUE] * len(vm_labels)
    bars = right.barh(
        y,
        values,
        xerr=errors,
        color=colors,
        alpha=0.88,
        edgecolor="white",
        linewidth=0.8,
        capsize=3,
        error_kw={"elinewidth": 0.9, "ecolor": "#333333", "capthick": 0.9},
    )
    for index, bar in enumerate(bars):
        if index < len(weight_labels):
            bar.set_hatch("//")
        right.text(
            values[index] + errors[index] + 0.04,
            bar.get_y() + bar.get_height() / 2,
            f"{values[index]:.2f}",
            va="center",
            ha="left",
            fontsize=8,
            color=DARK_RED if index < len(weight_labels) else BLUE,
            weight="bold",
        )
    right.set_yticks(y)
    right.set_yticklabels(category_labels)
    right.invert_yaxis()
    right.axvline(0, color="#444444", linewidth=0.8)
    right.axhline(len(weight_labels) - 0.5, color="#999999", linestyle="--", linewidth=0.8)
    right.grid(axis="x", color="#E6E6E6", linestyle="--", linewidth=0.7, zorder=0)
    right.set_axisbelow(True)
    right.set_xlabel("Paired objective improvement over Greedy-LPT (%)")
    right.set_title("(b) Robustness across priorities and VM profiles", loc="left", pad=9)
    right.legend(
        handles=[
            Patch(facecolor=RED, edgecolor="white", hatch="//", label="Weight profiles (150 blocks)"),
            Patch(facecolor=BLUE, edgecolor="white", label="VM profiles (90 blocks)"),
        ],
        frameon=False,
        loc="lower right",
    )
    right.text(
        0.0,
        -0.20,
        "Bars show paired means; error bars are 95% intervals across paired blocks.",
        transform=right.transAxes,
        ha="left",
        va="top",
        fontsize=7.8,
        color=GRAY,
    )

    save_figure(fig, output / "fig_ablation_robustness.png")


def parse_args() -> argparse.Namespace:
    script = Path(__file__).resolve()
    default_repo = script.parents[3]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo-root", type=Path, default=default_repo)
    parser.add_argument("--output-dir", type=Path)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    repo = args.repo_root.resolve()
    submission = repo / "各个期刊格式的论文" / "待发布版本" / "01_Evolutionary_Intelligence"
    output = args.output_dir.resolve() if args.output_dir else submission / "figures"
    archive = submission / "LSCBO_Anonymized_Reproducibility.zip"
    cloud_csv = (
        repo
        / "论文程序及图表"
        / "LSCBO_FULL_EXPERIMENT_PROGRAM_20260609"
        / "results"
        / "formal-cloudsim_20260619_canonical"
        / "analytic"
        / "claim_formalfull_raw.csv"
    )

    for required in (archive, cloud_csv):
        if not required.is_file():
            raise FileNotFoundError(required)

    configure_style()
    draw_scheduling_model(output)
    draw_mechanism_workflow(output)
    draw_cec_convergence(archive, output)
    draw_cloud_multi_algorithm(cloud_csv, output)
    draw_ablation_robustness(cloud_csv, output)


if __name__ == "__main__":
    main()

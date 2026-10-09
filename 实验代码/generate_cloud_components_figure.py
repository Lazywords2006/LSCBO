#!/usr/bin/env python3
"""Reproduce Fig7 in actual component units from the checked E4 records.

All five task scales are shown in their original component units. The task-count
axis is logarithmic; every method uses the same numerical task coordinates.
No cross-scale reference indexing or artificial coordinate offsets are applied.
Run from any directory with Python 3, numpy, pandas, and matplotlib installed.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.ticker import NullLocator
import pandas as pd

from generate_cloud_objective_figure import METRICS, SCALES, configure_style, load_data

DISPLAY_METHODS = ["GREEDY_LPT", "PSO", "CBO", "LSCBO"]
# Larger hollow symbols under smaller symbols make coincident means legible
# without altering either horizontal or vertical data coordinates.
STYLES = {
    "LSCBO": ("#D55E00", "o", "-", "LSCBO + pair", 3.6, 5),
    "CBO": ("#333333", "v", "--", "Adapted CBO", 4.8, 4),
    "PSO": ("#009E73", "^", "-.", "PSO", 6.0, 3),
    "GREEDY_LPT": ("#666666", "s", ":", "Tied group", 7.0, 2),
}
TITLES = ["(a) Makespan", "(b) Energy", "(c) Execution cost", "(d) Load imbalance"]
Y_LABELS = [r"$H_{\mathrm{max}}$ (s)", r"$E$ (Wh)", r"$C$ (synthetic cost units)",
            r"$\mathcal{I}$ (dimensionless)"]


def summarize(frame: pd.DataFrame) -> pd.DataFrame:
    """Keep every method and sample SD in the reproducible numerical output."""
    means = frame[METRICS].groupby(["taskCount", "algorithm"]).mean()
    standard_deviations = frame[METRICS].groupby(["taskCount", "algorithm"]).std(ddof=1)
    rows = []
    for (scale, method), values in means.iterrows():
        if scale not in SCALES:
            continue
        for metric in METRICS:
            rows.append({"taskCount": scale, "algorithm": method, "metric": metric,
                         "n": 30, "mean": values[metric],
                         "sample_sd": standard_deviations.loc[(scale, method), metric]})
    return pd.DataFrame(rows)


def draw(summary: pd.DataFrame, output: Path) -> None:
    configure_style()
    fig, axes = plt.subplots(2, 2, figsize=(130.8 / 25.4, 4.75))
    fig.subplots_adjust(left=.15, right=.965, bottom=.115, top=.775,
                        wspace=.72, hspace=.69)
    for axis, metric, title, ylabel in zip(axes.flat, METRICS, TITLES, Y_LABELS):
        for method in DISPLAY_METHODS:
            color, marker, linestyle, label, markersize, zorder = STYLES[method]
            group = summary[summary.algorithm.eq(method) & summary.metric.eq(metric)]
            group = group.set_index("taskCount").loc[SCALES]
            axis.plot(SCALES, group["mean"], color=color, marker=marker,
                      markerfacecolor="white", markeredgewidth=.85,
                      markersize=markersize, linestyle=linestyle,
                      linewidth=1.2, label=label, zorder=zorder)
        axis.set_title(title, loc="left", fontsize=8.7, pad=7)
        axis.set_ylabel(ylabel, labelpad=4)
        axis.set_xscale("log")
        axis.set_xlabel(r"Tasks $N$ (log scale)", labelpad=4)
        axis.set_xticks(SCALES, ["200", "500", "1k", "2k", "5k"])
        axis.xaxis.set_minor_locator(NullLocator())
        axis.set_xlim(170, 6000)
        axis.set_ylim(bottom=0)
        axis.grid(axis="y", color="#DDDDDD", linestyle="--", linewidth=.5)
        axis.set_axisbelow(True)
        axis.spines[["top", "right"]].set_visible(False)
        axis.tick_params(length=3, pad=3)
    axes[0, 0].set_ylim(0, 920)
    axes[0, 0].set_yticks([0, 300, 600, 900])
    axes[0, 1].set_ylim(0, 3300)
    axes[0, 1].set_yticks([0, 1000, 2000, 3000])
    axes[1, 0].set_ylim(0, 7000)
    axes[1, 0].set_yticks([0, 2000, 4000, 6000])
    axes[1, 1].set_ylim(0, .014)
    axes[1, 1].set_yticks([0, .004, .008, .012])
    axes[1, 1].ticklabel_format(axis="y", style="sci", scilimits=(-3, -3), useMathText=True)
    handles, labels = axes[0, 0].get_legend_handles_labels()
    fig.legend(handles[::-1], labels[::-1], loc="upper center",
               bbox_to_anchor=(.55, .975), ncol=4, frameon=False,
               handlelength=1.7, columnspacing=1.1, handletextpad=.45)
    fig.text(.055, .876, "Tied group: GWO / WOA / Adapted AOA / Greedy-LPT.\n"
             "Means over 30 seeds; overlapping means retain identical coordinates.",
             fontsize=8, va="center", linespacing=1.6)
    output.parent.mkdir(parents=True, exist_ok=True)
    fig.savefig(output, dpi=300, facecolor="white")
    plt.close(fig)


def main() -> None:
    release = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data", type=Path, default=release / "实验数据/数据/cloud_main_E4_20260619.csv")
    parser.add_argument("--provenance", type=Path, default=release / "实验数据/数据/cloud_main_E4_20260619.provenance.json")
    parser.add_argument("--pdf", type=Path, default=release / "论文/figures/publication/Fig7.pdf")
    parser.add_argument("--svg", type=Path, default=release / "实验数据/图表/Fig7_cloud_components.svg")
    parser.add_argument("--png", type=Path)
    parser.add_argument("--summary", type=Path)
    args = parser.parse_args()
    frame = load_data(args.data, args.provenance)
    summary = summarize(frame)
    for output in [args.pdf, args.svg, args.png]:
        if output is not None:
            draw(summary, output)
    if args.summary is not None:
        args.summary.parent.mkdir(parents=True, exist_ok=True)
        summary.to_csv(args.summary, index=False, float_format="%.12g")
    print(json.dumps({"source_rows": len(frame), "plotted_scales": SCALES,
                      "methods": int(summary.algorithm.nunique()),
                      "metric_cells": len(summary), "units": ["s", "Wh", "synthetic cost units", "dimensionless"],
                      "task_axis": "logarithmic with all five task-count ticks",
                      "reference_indexing": False, "coordinate_offsets": False}, indent=2))


if __name__ == "__main__":
    main()

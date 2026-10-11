#!/usr/bin/env python3
"""Fig.6 and Fig.7 for the 100-1000 task range, ERTH-mode setting.

Data: random initialisation, population 100, 100 iterations, 50 VMs fixed,
30 seeds per scale (the source run also covers 2000-10000; only the small-scale
panel is plotted here). The six metaheuristics are drawn as curves.

  Fig6.pdf  scalar objective F versus number of tasks
  Fig7.pdf  absolute component means versus number of tasks
"""
from __future__ import annotations

import argparse, csv, hashlib, json, sys
from collections import defaultdict
from pathlib import Path

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.lines import Line2D
from matplotlib.ticker import MaxNLocator, NullLocator, ScalarFormatter
import numpy as np

RELEASE = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(Path(__file__).resolve().parent))
from publication_style import WIDTH_INCHES, grid  # noqa: E402

OUT = RELEASE / "论文/Cost与云调度两个板块/图"
DATA = RELEASE / "论文/Cost与云调度两个板块/数据/冷启动_3990行.csv"

SCALES = [100, 200, 300, 400, 500, 600, 700, 800, 900, 1000]
METRICS = ["makespan", "energy", "cost", "imbalance"]
TITLES = ["(a) Makespan", "(b) Energy", "(c) Batch reservation cost", "(d) Load imbalance"]
UNITS = {"makespan": "Makespan (s)", "energy": "Energy (Wh)",
         "cost": "Batch reservation cost (units)", "imbalance": "Load imbalance"}
STYLES = {
    "LSCBO": ("#E8302F", "LSCBO", 1.7),
    "CBO": ("#5B4373", "Adapted CBO", 1.0),
    "PSO": ("#35506E", "PSO", 1.0),
    "GWO": ("#2E6B5E", "GWO", 1.0),
    "WOA": ("#8A6420", "WOA", 1.0),
    "AOA": ("#A05A78", "Adapted AOA", 1.0),
    "GREEDY_LPT": ("#2B2F33", "Greedy-LPT", 1.4),
}
ORDER = ["LSCBO", "WOA", "PSO", "CBO", "AOA", "GWO"]   # Greedy-LPT line omitted; its values are disclosed in the caption





def load():
    acc = defaultdict(list)
    for r in csv.DictReader(open(DATA, encoding="utf-8")):
        s = int(r["taskCount"])
        if s not in SCALES:
            continue
        for c in METRICS + ["objective"]:
            acc[(s, r["algorithm"], c)].append(float(r[c]))
    return {k: float(np.mean(v)) for k, v in acc.items()}


def line_panel(ax, means, metric, ylabel, logy=False):
    for m in ORDER:
        c, lb, lw = STYLES[m]
        ax.plot(SCALES, [means[(s, m, metric)] for s in SCALES], color=c,
                linewidth=lw, label=lb, zorder=7 if m == "LSCBO" else 4)
    ax.set_xlabel("Number of tasks", labelpad=3)
    ax.set_ylabel(ylabel, labelpad=3)
    ax.xaxis.set_major_locator(MaxNLocator(nbins=6))
    if logy:
        ax.set_yscale("log")
        ax.yaxis.set_major_formatter(ScalarFormatter())
        ax.yaxis.set_minor_locator(NullLocator())
    else:
        ax.yaxis.set_major_locator(MaxNLocator(nbins=5))
    grid(ax, which="y")
    ax.tick_params(labelsize=7.6, length=2.6, pad=2.5)
    ax.margins(x=.03, y=.08)


def legend(ax, ncol=2, loc="upper left"):
    # Use actual curves so legend strokes match the plotted method styles.
    handles = ax.lines[:len(ORDER)]
    ax.legend(handles=handles, loc=loc, ncol=ncol, fontsize=5.8,
              frameon=True, facecolor="white", framealpha=1., edgecolor="#D5D9DE",
              borderpad=.42, labelspacing=.36, handlelength=1.9,
              columnspacing=.9, handletextpad=.35, borderaxespad=.4,
              labelcolor="linecolor")


def relative_cost_panel(ax, means):
    for m in ORDER:
        color, label, width = STYLES[m]
        values = [100 * (means[(s, m, "cost")] / means[(s, "LSCBO", "cost")] - 1)
                  for s in SCALES]
        ax.plot(SCALES, values, color=color, linewidth=width, linestyle="-",
                marker=None, label=label, zorder=7 if m == "LSCBO" else 4)
    ax.set_xlabel("Number of tasks", labelpad=3)
    ax.set_ylabel("Cost excess over LSCBO (%)", labelpad=3, fontsize=9.4)
    xpad = .03 * (SCALES[-1] - SCALES[0])
    ax.set_xlim(SCALES[0] - xpad, SCALES[-1] + xpad)
    ax.set_ylim(-.65, 18.4)
    ax.set_xticks([200, 400, 600, 800, 1000])
    ax.set_yticks([0, 4, 8, 12, 16])
    grid(ax, which="y")
    ax.tick_params(labelsize=7.6, length=2.6, pad=2.5)


def subcaptions(fig, axes, titles, columns):
    """Place each panel title below its x labels, centered on the panel."""
    fig.canvas.draw()
    renderer = fig.canvas.get_renderer()
    panels = []
    for first in range(0, len(axes), columns):
        row = axes[first:first + columns]
        bottom = min(text.get_window_extent(renderer).y0
                     for ax in row
                     for text in [ax.xaxis.label, *ax.get_xticklabels()]
                     if text.get_text().strip())
        y = bottom / fig.bbox.height - 5 / (72 * fig.get_figheight())
        for offset, ax in enumerate(row):
            box = ax.get_position()
            caption = fig.text((box.x0 + box.x1) / 2, y,
                               titles[first + offset], ha="center", va="top",
                               fontsize=9.0)
            panels.append((ax, caption))
    return panels


def save_figure(fig, name, panels):
    fig.canvas.draw()
    renderer = fig.canvas.get_renderer()
    records = []
    for ax, caption in panels:
        box = ax.get_position()
        records.append({"title": caption.get_text(),
                        "panel_center_x_pt": (box.x0 + box.x1) / 2 * fig.get_figwidth() * 72,
                        "panel_bottom_from_top_pt": (1 - box.y0) * fig.get_figheight() * 72,
                        "caption_bbox_from_bottom_pt": [v * 72 / fig.dpi for v in caption.get_window_extent(renderer).extents]})
    curves = []
    for panel_index, (ax, _) in enumerate(panels):
        for method, line in zip(ORDER, ax.lines):
            gid = f"curve_{name}_{panel_index}_{method}"
            line.set_gid(gid)
            coordinates = np.column_stack((line.get_xdata(), line.get_ydata()))
            display = ax.transData.transform(coordinates) * 72 / fig.dpi
            display[:, 1] = fig.get_figheight() * 72 - display[:, 1]
            curves.append({"gid": gid, "panel": panel_index, "algorithm": method,
                           "x": np.asarray(line.get_xdata()).tolist(), "y": np.asarray(line.get_ydata()).tolist(),
                           "color": line.get_color(), "linewidth": line.get_linewidth(),
                           "linestyle": line.get_linestyle(), "marker": line.get_marker(),
                           "expected_vector_points_pt": display.tolist()})
    fig.savefig(OUT / f"{name}.png", dpi=300, facecolor="white")
    fig.savefig(OUT / f"{name}.pdf", facecolor="white")
    fig.savefig(OUT / f"{name}.svg", facecolor="white")
    (OUT / f"{name}_layout.json").write_text(json.dumps({
        "data_sha256": hashlib.sha256(DATA.read_bytes()).hexdigest(),
        "panel_title_position": "below x labels; centered on each panel",
        "panels": records, "curves": curves, "absolute_cost_zoom": None}, indent=2), encoding="utf-8")


def main():
    global OUT, DATA
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", type=Path, default=OUT)
    parser.add_argument("--data", type=Path, default=DATA)
    parser.add_argument("--figure", choices=["6", "7", "all"], default="all")
    parser.add_argument("--cost-display", choices=["relative", "absolute"], default="absolute")
    args = parser.parse_args()
    OUT, DATA = args.out, args.data
    plt.rcParams.update({"pdf.fonttype": 42, "svg.fonttype": "none", "path.simplify": False})
    means = load()
    OUT.mkdir(parents=True, exist_ok=True)

    # ---------------- Fig 7: four component panels ----------------
    if args.figure in ["7", "all"]:
        fig, axes = plt.subplots(2, 2, figsize=(WIDTH_INCHES, 5.2))
        fig.subplots_adjust(left=.15, right=.98, bottom=.145, top=.955, wspace=.36, hspace=.42)
        titles = list(TITLES)
        for ax, metric in zip(axes.flat, METRICS):
            if metric == "cost" and args.cost_display == "relative":
                relative_cost_panel(ax, means)
            else:
                line_panel(ax, means, metric, UNITS[metric])
        if args.cost_display == "relative":
            titles[2] = "(c) Relative completion-time cost"
        legend(axes.flat[0])
        panels = subcaptions(fig, list(axes.flat), titles, 2)
        save_figure(fig, "Fig7", panels)
        plt.close(fig)

    # ---------------- Fig 6: scalar objective, single panel ----------------
    if args.figure in ["6", "all"]:
        fig, ax = plt.subplots(figsize=(WIDTH_INCHES, 3.15))
        fig.subplots_adjust(left=.14, right=.985, bottom=.22, top=.96)
        line_panel(ax, means, "objective", "Mean objective $F$ (log scale)", logy=True)
        legend(ax, loc="upper left")
        panels = subcaptions(fig, [ax], ["Final scalar objective"], 1)
        save_figure(fig, "Fig6", panels)
        plt.close(fig)

    print("wrote figure selection", args.figure, "to", OUT)
    print("LSCBO objective by scale:", [round(means[(s, 'LSCBO', 'objective')], 2) for s in SCALES])


if __name__ == "__main__":
    main()

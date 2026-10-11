#!/usr/bin/env python3
"""Fig.6 and Fig.7 for the 100-1000 task range, cold-start setting inspired by ERTH population/scales.

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
TITLES = ["(a) Makespan", "(b) Energy", "(c) Cost", "(d) Load imbalance"]
UNITS = {"makespan": "Makespan (s)", "energy": "Energy (Wh)",
         "cost": "Cost (units)", "imbalance": "Load imbalance"}
STYLES = {
    "LSCBO": ("#E8302F", "LSCBO", 1.7),
    "CBO": ("#5B4373", "CBO", 1.0),
    "PSO": ("#35506E", "PSO", 1.0),
    "GWO": ("#2E6B5E", "GWO", 1.0),
    "WOA": ("#8A6420", "WOA", 1.0),
    "AOA": ("#A05A78", "AOA", 1.0),
    "GREEDY_LPT": ("#2B2F33", "Greedy-LPT", 1.4),
}
ORDER = ["LSCBO", "WOA", "PSO", "CBO", "AOA", "GWO"]
FIGURE_WIDTH_INCHES = WIDTH_INCHES * 1.22





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
    ax.tick_params(labelsize=9.2, length=2.6, pad=2.5)
    ax.margins(x=.03, y=.08)


def legend(ax, location="upper left", anchor=(.08,1.), columns=2):
    """Determine Fig7(d) first, then keep that shared location in every plot."""
    handles = ax.lines[:len(ORDER)]
    original_limits = ax.get_ylim()
    locations = [location]*12
    for step, location in enumerate(locations):
        leg = ax.legend(handles=handles, loc=location, bbox_to_anchor=anchor,
                        ncol=columns, fontsize=7.5, frameon=True, fancybox=False,
                        facecolor="white", framealpha=1., edgecolor="#6D7380",
                        borderpad=.30, labelspacing=.30, handlelength=1.6,
                        handletextpad=.35, columnspacing=.70, borderaxespad=.35,
                        labelcolor="linecolor")
        leg.get_frame().set_linewidth(.65)
        ax.figure.canvas.draw()
        box = leg.get_window_extent(ax.figure.canvas.get_renderer())
        covered = sum(line.get_path().transformed(line.get_transform()).intersects_bbox(box, filled=False)
                      for line in handles)
        if covered == 0:
            leg._chosen_location = location
            leg._covered_data_artists = 0
            leg._headroom_steps = step if len(locations)>2 else 0
            leg._original_y_limits = original_limits
            leg._anchor = anchor
            leg._columns = columns
            return leg
        leg.remove()
        if len(locations)>2:
            low, high = ax.get_ylim()
            if ax.get_yscale()=="log":
                high = np.exp(np.log(low)+(np.log(high)-np.log(low))*1.12)
            else:
                high = low+(high-low)*1.12
            ax.set_ylim(low, high)
    raise ValueError("Shared legend position would obscure data")


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
    ax.tick_params(labelsize=9.2, length=2.6, pad=2.5)


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
                               fontsize=11.0)
            panels.append((ax, caption))
    return panels


def save_figure(fig, name, panels):
    fig.canvas.draw()
    renderer = fig.canvas.get_renderer()
    records = []
    for panel_index, (ax, caption) in enumerate(panels):
        box = ax.get_position()
        leg = ax.get_legend()
        assert leg is not None
        leg.set_gid(f"legend_{name}_{panel_index}")
        for method, line in zip(ORDER, leg.get_lines()):
            line.set_gid(f"legend_line_{name}_{panel_index}_{method}")
        legend_box = leg.get_window_extent(renderer).extents * 72 / fig.dpi
        axis_box = np.array([box.x0, box.y0, box.x1, box.y1]) * np.array([
            fig.get_figwidth()*72, fig.get_figheight()*72,
            fig.get_figwidth()*72, fig.get_figheight()*72])
        assert axis_box[0] <= legend_box[0] < legend_box[2] <= axis_box[2]
        assert axis_box[1] <= legend_box[1] < legend_box[3] <= axis_box[3]
        records.append({"title": caption.get_text() if caption is not None else None,
                        "ylabel": ax.get_ylabel(),
                        "panel_center_x_pt": (box.x0 + box.x1) / 2 * fig.get_figwidth() * 72,
                        "panel_bottom_from_top_pt": (1 - box.y0) * fig.get_figheight() * 72,
                        "caption_bbox_from_bottom_pt": [v * 72 / fig.dpi for v in caption.get_window_extent(renderer).extents] if caption is not None else None,
                        "axes_bbox_from_bottom_pt": axis_box.tolist(),
                        "legend_bbox_from_bottom_pt": legend_box.tolist(),
                        "legend_gid": leg.get_gid(),
                        "legend_labels": [t.get_text() for t in leg.get_texts()],
                        "legend_position": leg._chosen_location,
                        "legend_boxed": True, "legend_anchor": list(leg._anchor),
                        "legend_rows": int(np.ceil(len(ORDER)/leg._columns)), "legend_columns": leg._columns,
                        "covered_data_artists": leg._covered_data_artists,
                        "legend_headroom_steps": leg._headroom_steps,
                        "original_y_limits": list(leg._original_y_limits),
                        "display_y_limits": list(ax.get_ylim())})
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
        "panel_title_position": "below x labels; centered on each panel" if any(caption is not None for _, caption in panels) else "none",
        "panels": records, "curves": curves, "absolute_cost_zoom": None,
        "legend_policy": "boxed shared position determined first from Fig7(d)",
        "each_panel_has_legend": True}, indent=2), encoding="utf-8")


def main():
    global OUT, DATA
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", type=Path, default=OUT)
    parser.add_argument("--data", type=Path, default=DATA)
    parser.add_argument("--figure", choices=["6", "7", "all"], default="all")
    parser.add_argument("--cost-display", choices=["relative", "absolute"], default="absolute")
    args = parser.parse_args()
    OUT, DATA = args.out, args.data
    plt.rcParams.update({"pdf.fonttype": 42, "svg.fonttype": "none", "path.simplify": False,
                         "font.family": "DejaVu Sans", "font.size": 10.5, "axes.labelsize": 10.5})
    means = load()
    OUT.mkdir(parents=True, exist_ok=True)

    # ---------------- Fig 7: four component panels ----------------
    # Always derive the shared position from panel (d), even for Fig6-only export.
    fig, axes = plt.subplots(2, 2, figsize=(FIGURE_WIDTH_INCHES, 6.5))
    fig.subplots_adjust(left=.12, right=.98, bottom=.145, top=.955, wspace=.32, hspace=.45)
    titles = list(TITLES)
    for ax, metric in zip(axes.flat, METRICS):
        if metric == "cost" and args.cost_display == "relative":
            relative_cost_panel(ax, means)
        else:
            line_panel(ax, means, metric, UNITS[metric])
    if args.cost_display == "relative":
        titles[2] = "(c) Relative completion-time cost"
    reference_legend = legend(axes.flat[3], columns=3)
    shared_position = reference_legend._chosen_location
    for ax in list(axes.flat)[:3]:
        legend(ax, shared_position, columns=3)
    panels = subcaptions(fig, list(axes.flat), titles, 2)
    if args.figure in ["7", "all"]:
        save_figure(fig, "Fig7", panels)
    plt.close(fig)

    # ---------------- Fig 6: scalar objective, single panel ----------------
    if args.figure in ["6", "all"]:
        fig, ax = plt.subplots(figsize=(FIGURE_WIDTH_INCHES, 3.35))
        fig.subplots_adjust(left=.125, right=.985, bottom=.23, top=.96)
        line_panel(ax, means, "objective", "Mean objective $F$ (log scale)", logy=True)
        legend(ax, shared_position, anchor=(.01,1.), columns=3)
        panels = [(ax, None)]
        save_figure(fig, "Fig6", panels)
        plt.close(fig)

    print("wrote figure selection", args.figure, "to", OUT)
    print("LSCBO objective by scale:", [round(means[(s, 'LSCBO', 'objective')], 2) for s in SCALES])
    print("Shared boxed legend position:", shared_position)
    return shared_position


if __name__ == "__main__":
    main()

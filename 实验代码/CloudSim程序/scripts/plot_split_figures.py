#!/usr/bin/env python3
"""Manuscript Fig6-Fig8: cloud results split into 100-1000 and 1000-3000 tasks.

Fig6: mean objective F, panel (a) 100-1000 tasks, panel (b) 1000-3000 tasks.
Fig7: four criteria, 100-1000 tasks.   Fig8: four criteria, 1000-3000 tasks.
Same data, styles and legend rules as plot_fig6_fig7.py (imported); nothing is recomputed.
"""
import argparse, sys
from pathlib import Path
RELEASE = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(RELEASE / "实验代码/CloudSim程序/scripts"))
import matplotlib.pyplot as plt
from matplotlib.ticker import FixedLocator, NullLocator
import plot_fig6_fig7 as p

SMALL = list(range(100, 1001, 100))
LARGE = [1000, 2000, 3000]


def criteria_figure(means, scales, name):
    fig, axes = plt.subplots(2, 2, figsize=(p.FIGURE_WIDTH_INCHES, 6.5))
    fig.subplots_adjust(left=.12, right=.98, bottom=.145, top=.955, wspace=.32, hspace=.45)
    for ax, metric in zip(axes.flat, p.METRICS):
        p.line_panel(ax, means, metric, p.UNITS[metric], scales=scales)
        if scales == LARGE:
            ax.set_xticks(LARGE)
    ref = p.legend(axes.flat[3], columns=3)
    for ax in list(axes.flat)[:3]:
        p.legend(ax, ref._chosen_location, columns=3)
    panels = p.subcaptions(fig, list(axes.flat), p.TITLES, 2)
    p.save_figure(fig, name, panels)
    plt.close(fig)


def main():
    ap = argparse.ArgumentParser(); ap.add_argument("--out", type=Path, required=True)
    a = ap.parse_args()
    p.OUT = a.out; p.OUT.mkdir(parents=True, exist_ok=True)
    p.configure(); means = p.load()
    criteria_figure(means, SMALL, "Fig7")
    criteria_figure(means, LARGE, "Fig8")
    fig, axes = plt.subplots(1, 2, figsize=(p.FIGURE_WIDTH_INCHES, 3.7))
    fig.subplots_adjust(left=.085, right=.985, bottom=.30, top=.96, wspace=.26)
    for ax, scales in zip(axes, (SMALL, LARGE)):
        p.line_panel(ax, means, "objective", "Mean objective $F$", scales=scales)
    axes[0].set_ylim(0, 65); axes[0].yaxis.set_major_locator(FixedLocator(range(0, 61, 10)))
    axes[1].set_ylim(0, 162); axes[1].yaxis.set_major_locator(FixedLocator(range(0, 151, 30))); axes[1].set_xticks(LARGE)
    for ax in axes: ax.yaxis.set_minor_locator(NullLocator())
    ref = p.legend(axes[0], columns=3); p.legend(axes[1], ref._chosen_location, columns=3)
    panels = p.subcaptions(fig, list(axes), ["(a) 100–1000 tasks", "(b) 1000–3000 tasks"], 2)
    p.save_figure(fig, "Fig6", panels)
    print("ok")

if __name__ == "__main__":
    main()

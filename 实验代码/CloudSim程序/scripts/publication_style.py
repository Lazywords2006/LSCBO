"""Shared physical typography and method identities for manuscript figures."""
from __future__ import annotations

import matplotlib.pyplot as plt

WIDTH_INCHES = 130.8 / 25.4
COLORS = {
    "LSCBO": "#D62728", "CBO": "#9467BD", "PSO": "#0072B2",
    "SA": "#56B4E9", "HLBO": "#E69F00", "GWCA": "#CC79A7",
    "GWO": "#009E73", "AOA": "#7F7F7F", "WOA": "#8C564B",
    "GREEDY_LPT": "#454545", "direct5400": "#56B4E9",
    "direct6000": "#009E73", "weights": "#D62728", "vms": "#0072B2",
}
GRID_COLOR = "#E4E4E4"


def configure() -> None:
    plt.rcParams.update({
        "font.family": "DejaVu Sans", "font.size": 9,
        "axes.labelsize": 9, "axes.titlesize": 10,
        "xtick.labelsize": 8.5, "ytick.labelsize": 8.5,
        "legend.fontsize": 8.5, "axes.linewidth": .8,
        "axes.spines.top": True, "axes.spines.right": True,
        "xtick.direction": "out", "ytick.direction": "out",
        "pdf.fonttype": 42, "ps.fonttype": 42, "svg.fonttype": "none",
        "savefig.facecolor": "white", "svg.hashsalt": "lscbo-unified-20261010",
    })


def grid(axis: plt.Axes, which: str = "both") -> None:
    axis.grid(axis=which, color=GRID_COLOR, linewidth=.55)
    axis.set_axisbelow(True)
    axis.tick_params(length=3, pad=3)

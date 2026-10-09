#!/usr/bin/env python3
"""Replot Fig8 profile sensitivity, checking the archived seed-cluster intervals."""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import Patch
import numpy as np
import pandas as pd

def load_checked(release: Path) -> pd.DataFrame:
    inputs = release / "实验数据" / "数据"
    manifest = json.loads((inputs / "fig8_provenance.json").read_text(encoding="utf-8"))
    for name in ("fig8_profile_bootstrap.csv", "fig8_seed_cluster_means.csv"):
        record = manifest[name]
        if hashlib.sha256((inputs / name).read_bytes()).hexdigest() != record["sha256"]:
            raise ValueError(f"Changed Fig8 input: {name}")
    ci = pd.read_csv(inputs / "fig8_profile_bootstrap.csv")
    clusters = pd.read_csv(inputs / "fig8_seed_cluster_means.csv")
    if len(ci) != 8 or len(clusters) != 240:
        raise ValueError("Unexpected Fig8 dimensions")
    for _, row in ci.iterrows():
        group = clusters[(clusters.experiment == row.experiment) &
                         (clusters.profile == row.profile)].sort_values("seed")
        if len(group) != 30 or set(group.seed) != set(range(43, 73)):
            raise ValueError("Incomplete seed clusters")
        expected_scales = 5 if row.experiment == "weight_sensitivity" else 3
        if not group.scale_count.eq(expected_scales).all():
            raise ValueError("Incorrect scale count within a seed cluster")
        data = group.mean_improvement_percent.to_numpy()
        indices = np.random.Generator(np.random.PCG64(20260912)).integers(0, 30, (50000, 30))
        low, high = np.quantile(data[indices].mean(axis=1), [.025, .975])
        np.testing.assert_allclose([data.mean(), low, high],
                                   [row["mean"], row.low, row.high], rtol=1e-12, atol=1e-12)
    return ci

def draw(ci: pd.DataFrame, output: Path) -> None:
    plt.rcParams.update({"font.family": "DejaVu Sans", "font.size": 8.5,
                         "axes.titlesize": 9, "axes.labelsize": 8.5,
                         "xtick.labelsize": 8, "ytick.labelsize": 8,
                         "pdf.fonttype": 42, "svg.fonttype": "none"})
    fig = plt.figure(figsize=(130.8 / 25.4, 3.65))
    ax2 = fig.add_axes([.255, .255, .71, .61])
    ypos = np.array([0, 1, 2, 3, 4, 5.35, 6.35, 7.35])
    for i, row in ci.iterrows():
        color = "#B9443A" if i < 5 else "#3B6BB5"
        ax2.barh(ypos[i], row["mean"], height=.68, color=color, edgecolor="white",
                 hatch="//" if i < 5 else None, linewidth=.5, zorder=2)
        ax2.errorbar(row["mean"], ypos[i],
                     xerr=np.array([[row["mean"]-row.low], [row.high-row["mean"]]]),
                     fmt="none", ecolor="#333333", capsize=2.5, elinewidth=.9, zorder=3)
        ax2.text(row.high+.055, ypos[i], f'{row["mean"]:.2f}', va="center",
                 fontsize=8, color=color)
    ax2.set_yticks(ypos, ci.label)
    ax2.set_xlim(0, 3.85)
    ax2.set_ylim(7.95, -.65)
    ax2.set_xticks(np.arange(0, 3.6, .5))
    ax2.axhline(4.68, color="#AAAAAA", ls="--", linewidth=.7)
    ax2.grid(axis="x", color="#DDDDDD", linestyle="--", linewidth=.55, zorder=0)
    ax2.spines[["top", "right"]].set_visible(False)
    ax2.set_xlabel(r"Mean paired reduction in $F$ (%)", labelpad=5)
    fig.text(.035, .935, "Weight priorities and VM-capacity profiles", fontsize=9)
    fig.legend(handles=[Patch(facecolor="#B9443A", edgecolor="white", hatch="//",
                              label="Weights: 5 scales × 30 seeds"),
                        Patch(facecolor="#3B6BB5", label="VMs: 3 scales × 30 seeds")],
               loc="lower center", bbox_to_anchor=(.50, .014), ncol=1,
               frameon=False, fontsize=8, handlelength=1.5, labelspacing=.45)
    output.parent.mkdir(parents=True, exist_ok=True)
    fig.savefig(output, dpi=300, facecolor="white")
    plt.close(fig)

def main() -> None:
    release = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pdf", type=Path, default=release / "论文/figures/publication/Fig8.pdf")
    parser.add_argument("--svg", type=Path, default=release / "实验数据/图表/Fig8_ablation_robustness.svg")
    parser.add_argument("--png", type=Path)
    args = parser.parse_args()
    ci = load_checked(release)
    for output in [args.pdf, args.svg, args.png]:
        if output is not None:
            draw(ci, output)
    print(json.dumps({"profiles": len(ci),
                      "seed_clusters_per_profile": 30, "bootstrap_resamples": 50000,
                      "archived_means_and_intervals_reproduced": True}, indent=2))
if __name__ == "__main__":
    main()

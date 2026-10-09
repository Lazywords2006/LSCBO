#!/usr/bin/env python3
"""Reproduce the three CEC2017 figures without rerunning an experiment.

The bundled input preserves every algorithm/function/checkpoint arithmetic
mean (26,100 cells). To rebuild that input from the recorded 783,000 rows,
pass --source /path/to/validation/formal_full_results. No smoothing, error
floor, curve interpolation, or method omission is used.
"""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.lines import Line2D
from matplotlib.ticker import LogLocator, NullFormatter
import numpy as np
import pandas as pd


ROOT = Path(__file__).resolve().parents[1]
METHODS = ["LSCBO", "SA", "PSO", "HLBO", "GWCA", "CBO", "GWO", "AOA", "WOA"]
FUNCTIONS = [1, *range(3, 31)]
CHECKPOINTS = list(range(3000, 300001, 3000))
SELECTED = [1, 3, 5, 10, 15, 20]
EXPECTED_RANKS = np.array([49, 77, 97, 134, 139, 146, 199, 216, 248]) / 29
# Okabe--Ito colors plus neutral lines; redundant dashes remain legible in gray.
STYLES = {
    "LSCBO": ("#D55E00", "-", 1.8),
    "SA": ("#0072B2", "-", 1.4),
    "PSO": ("#009E73", "--", 1.25),
    "HLBO": ("#E69F00", "-.", 1.2),
    "GWCA": ("#CC79A7", (0, (5, 1, 1, 1)), 1.2),
    "CBO": ("#333333", "--", 1.3),
    "GWO": ("#8A6D00", (0, (6, 2)), 1.15),
    "AOA": ("#666666", ":", 1.3),
    "WOA": ("#777777", (0, (1, 2, 1, 2, 4, 2)), 1.15),
}
INPUT_NAME = "cec2017_checkpoint_means.csv"
PROVENANCE_NAME = "cec2017_figures.provenance.json"
DISPLAY_LABELS = {method: f"Adapted {method}" if method in {"CBO", "AOA"} else method for method in METHODS}
DISPLAY_LABELS["LSCBO"] = "LSCBO-core"


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def prepare_input(source: Path, data_dir: Path) -> None:
    """Validate the raw protocol, then retain all means needed by the figures."""
    checkpoints = pd.read_csv(source / "convergence_checkpoints.csv")
    runs = pd.read_csv(source / "per_run_results.csv")
    statistics = pd.read_csv(source / "per_function_statistics.csv")
    ranks = pd.read_csv(source / "friedman_ranking.csv")
    if len(checkpoints) != 783000 or len(runs) != 7830:
        raise ValueError("Expected 783,000 checkpoints and 7,830 terminal records")
    parsed = checkpoints["task_key"].str.extract(
        r"^cec2017-d30-(.+?)-f(\d+)-r(\d+)-s\d+-b300000$"
    )
    if parsed.isna().any().any():
        raise ValueError("Unexpected archived task key")
    checkpoints = checkpoints.assign(
        algorithm=parsed[0], function=parsed[1].astype(int), run=parsed[2].astype(int)
    )
    keys = ["algorithm", "function", "run", "evaluations"]
    expected = pd.MultiIndex.from_product(
        [METHODS, FUNCTIONS, range(1, 31), CHECKPOINTS], names=keys
    )
    indexed = checkpoints.set_index(keys)
    if indexed.index.has_duplicates or not indexed.index.sort_values().equals(expected.sort_values()):
        raise ValueError("Incomplete or duplicated method/function/run/checkpoint coverage")
    if not np.isfinite(indexed[["error", "best_value"]].to_numpy()).all():
        raise ValueError("Nonfinite checkpoint values")
    if (indexed["error"] <= 0).any():
        raise ValueError("Log plots require strictly positive recorded errors; no floor is applied")
    run_keys = ["algorithm", "function", "run"]
    terminal = indexed.xs(300000, level="evaluations")["error"].sort_index()
    recorded = runs.set_index(run_keys)["error"].sort_index()
    if not terminal.index.equals(recorded.index):
        raise ValueError("Checkpoint and terminal run keys differ")
    if not np.allclose(terminal.to_numpy(), recorded.to_numpy(), rtol=1e-13, atol=0):
        raise ValueError("Terminal checkpoints disagree with the recorded results")
    if not (runs["dimension"].eq(30) & runs["budget"].eq(300000) & runs["evaluations"].eq(300000)).all():
        raise ValueError("Unexpected dimension or evaluation budget")
    if not np.allclose(runs["error"], runs["best_value"] - runs["optimum"], rtol=1e-12, atol=1e-9):
        raise ValueError("Stored error does not equal best value minus official optimum")
    monotone = checkpoints.sort_values(keys).groupby(run_keys)["error"].diff()
    if (monotone > 1e-8).any():
        raise ValueError("A best-so-far run increases between checkpoints")
    means = checkpoints.groupby(["evaluations", "function", "algorithm"], as_index=False).agg(
        mean_error=("error", "mean"), n_runs=("run", "size")
    )
    end = means[means["evaluations"].eq(300000)].set_index(["function", "algorithm"])
    reference = statistics.set_index(["function", "algorithm"]).reindex(end.index)
    if not np.allclose(end["mean_error"], reference["mean"], rtol=1e-13, atol=0):
        raise ValueError("Computed function means disagree with the archived summary")
    final_ranks = end["mean_error"].groupby(level="function").rank(method="average")
    avg = final_ranks.groupby(level="algorithm").mean().reindex(METHODS)
    archived = ranks.set_index("algorithm")["average_rank"].reindex(METHODS)
    if not np.allclose(avg, archived, rtol=0, atol=1e-12):
        raise ValueError("Final checkpoint ranks disagree with the archived Friedman table")
    data_dir.mkdir(parents=True, exist_ok=True)
    output = data_dir / INPUT_NAME
    means.to_csv(output, index=False, float_format="%.17g")
    provenance = {
        "description": "Complete 30-run arithmetic means at all 100 CEC2017 checkpoints; no run or function is dropped.",
        "source_directory": str(source.resolve()),
        "source_files": {
            name: {"sha256": sha256(source / name)}
            for name in ["convergence_checkpoints.csv", "per_run_results.csv", "per_function_statistics.csv", "friedman_ranking.csv"]
        },
        "subset_sha256": sha256(output),
        "source_checkpoint_rows": len(checkpoints),
        "source_terminal_rows": len(runs),
        "bundled_mean_rows": len(means),
        "functions": FUNCTIONS,
        "methods": METHODS,
        "runs_per_cell": 30,
        "checkpoints": CHECKPOINTS,
        "minimum_raw_error": float(checkpoints["error"].min()),
        "terminal_checkpoint_max_abs_difference": float(np.max(np.abs(terminal.to_numpy() - recorded.to_numpy()))),
        "archived_mean_max_relative_difference": float(np.max(np.abs(end["mean_error"].to_numpy() / reference["mean"].to_numpy() - 1))),
        "terminal_average_ranks": avg.to_dict(),
        "selected_function_ids_retained_from_previous_figure": SELECTED,
        "band_definition": "Pointwise mean rank +/- 1.96 * sample SD of 29 function ranks / sqrt(29). Descriptive across-function intervals, not run-level or simultaneous confidence bands.",
        "error_definition": "Recorded best_value minus the official per-function optimum. Arithmetic mean over 30 runs is formed before displaying a logarithmic axis. No offset or clipping.",
    }
    (data_dir / PROVENANCE_NAME).write_text(json.dumps(provenance, indent=2) + "\n", encoding="utf-8")


def load_means(data_dir: Path) -> pd.DataFrame:
    provenance = json.loads((data_dir / PROVENANCE_NAME).read_text(encoding="utf-8"))
    path = data_dir / INPUT_NAME
    if sha256(path) != provenance["subset_sha256"]:
        raise ValueError("CEC figure input hash differs from the provenance record")
    means = pd.read_csv(path)
    keys = ["evaluations", "function", "algorithm"]
    expected = pd.MultiIndex.from_product([CHECKPOINTS, FUNCTIONS, METHODS], names=keys)
    actual = means.set_index(keys).index
    if actual.has_duplicates or not actual.sort_values().equals(expected.sort_values()):
        raise ValueError("The complete 26,100-cell checkpoint grid is required")
    if not means["n_runs"].eq(30).all() or not np.isfinite(means["mean_error"]).all() or means["mean_error"].le(0).any():
        raise ValueError("Invalid run counts or nonpositive/nonfinite mean errors")
    means["rank"] = means.groupby(["evaluations", "function"])["mean_error"].rank(method="average")
    end = means[means["evaluations"].eq(300000)].groupby("algorithm")["rank"].mean().reindex(METHODS)
    if not np.allclose(end, EXPECTED_RANKS, rtol=0, atol=1e-12):
        raise ValueError("The figure endpoint does not reproduce the nine-method rank table")
    return means


def configure_style() -> None:
    plt.rcParams.update({
        "font.family": "DejaVu Sans", "font.size": 9.0,
        "axes.labelsize": 9.2, "axes.titlesize": 9.2,
        "xtick.labelsize": 8.5, "ytick.labelsize": 8.5,
        "legend.fontsize": 8.6, "axes.linewidth": 0.65,
        "axes.spines.top": False, "axes.spines.right": False,
        "xtick.direction": "out", "ytick.direction": "out",
        "pdf.fonttype": 42, "ps.fonttype": 42, "svg.fonttype": "none",
        "savefig.facecolor": "white", "svg.hashsalt": "lscbo-cec2017-v1",
    })


def save(fig: plt.Figure, name: str, output: Path, editable: Path) -> None:
    output.mkdir(parents=True, exist_ok=True)
    editable.mkdir(parents=True, exist_ok=True)
    fig.savefig(output / f"{name}.pdf", metadata={"CreationDate": None, "ModDate": None})
    fig.savefig(editable / f"{name}_cec.svg", metadata={"Date": None})
    plt.close(fig)


def handles() -> list[Line2D]:
    return [Line2D([], [], color=STYLES[a][0], linestyle=STYLES[a][1], linewidth=STYLES[a][2], label=DISPLAY_LABELS[a]) for a in METHODS]


def draw_ranks(means: pd.DataFrame, output: Path, editable: Path) -> None:
    ranks = means[means["evaluations"].eq(300000)].groupby("algorithm")["rank"].mean().reindex(METHODS)
    fig, ax = plt.subplots(figsize=(4.65, 3.1))
    fig.subplots_adjust(left=.245, right=.97, bottom=.19, top=.97)
    for pos, (method, rank) in enumerate(ranks.items()):
        ax.plot([1, rank], [pos, pos], color="#D3D7DC", linewidth=1.1, zorder=1)
        ax.scatter(rank, pos, color=STYLES[method][0], marker="D" if method == "LSCBO" else "o", s=26, zorder=3)
        ax.text(rank + .14, pos, f"{rank:.3f}", va="center", fontsize=9,
                weight="bold" if method == "LSCBO" else "normal")
    ax.set_yticks(range(9), [DISPLAY_LABELS[method] for method in METHODS])
    ax.set_ylim(8.6, -.6)
    ax.set_xlim(.85, 9.4)
    ax.set_xticks(range(1, 10))
    ax.set_xlabel("Friedman average rank (lower is better)")
    ax.grid(axis="x", color="#E5E5E5", linewidth=.5)
    ax.set_axisbelow(True)
    ax.tick_params(axis="y", length=0)
    save(fig, "Fig1", output, editable)


def draw_aggregate(means: pd.DataFrame, output: Path, editable: Path) -> None:
    summary = means.groupby(["evaluations", "algorithm"])["rank"].agg(["mean", "std"]).reset_index()
    fig, ax = plt.subplots(figsize=(5.15, 4.05))
    fig.subplots_adjust(left=.115, right=.975, bottom=.275, top=.965)
    for method in reversed(METHODS):
        data = summary[summary["algorithm"].eq(method)].sort_values("evaluations")
        x, y = data["evaluations"].to_numpy() / 1000, data["mean"].to_numpy()
        color, linestyle, width = STYLES[method]
        if method in {"LSCBO", "CBO"}:
            half_width = 1.96 * data["std"].to_numpy() / np.sqrt(29)
            ax.fill_between(x, y - half_width, y + half_width, color=color, alpha=.09, linewidth=0)
        ax.plot(x, y, color=color, linestyle=linestyle, linewidth=width)
    ax.set_xlim(0, 300)
    ax.set_xticks([0, 50, 100, 150, 200, 250, 300])
    ax.set_ylim(9.15, .65)
    ax.set_yticks(range(1, 10))
    ax.set_xlabel("Objective evaluations (thousands)")
    ax.set_ylabel("Mean function rank (lower is better)")
    ax.grid(axis="y", color="#DDDDDD", linewidth=.5, linestyle="--")
    ax.set_axisbelow(True)
    fig.legend(handles=handles(), loc="lower center", bbox_to_anchor=(.54, .015),
               ncol=3, frameon=False, columnspacing=2.2, handlelength=3)
    save(fig, "Fig2", output, editable)


def draw_functions(means: pd.DataFrame, output: Path, editable: Path) -> None:
    fig, axes = plt.subplots(3, 2, figsize=(5.15, 6.4), sharex=True)
    fig.subplots_adjust(left=.135, right=.972, bottom=.085, top=.845, wspace=.27, hspace=.31)
    for panel, (ax, function) in enumerate(zip(axes.flat, SELECTED)):
        subset = means[means["function"].eq(function)]
        for method in reversed(METHODS):
            data = subset[subset["algorithm"].eq(method)].sort_values("evaluations")
            color, linestyle, width = STYLES[method]
            ax.plot(data["evaluations"] / 1000, data["mean_error"], color=color,
                    linestyle=linestyle, linewidth=width)
        ax.set_yscale("log")
        ax.set_xlim(0, 300)
        ax.set_xticks([0, 100, 200, 300])
        ax.set_title(f"({chr(97 + panel)}) CEC F{function}", loc="left", pad=4)
        ax.grid(axis="y", color="#DDDDDD", linewidth=.5, linestyle="--")
        ax.set_axisbelow(True)
        if function in {5, 10, 20}:
            lo, hi = subset["mean_error"].min(), subset["mean_error"].max()
            ticks = np.array([70, 100, 200, 400, 600, 1000, 2000, 3000, 4000, 6000, 8000, 10000])
            ticks = ticks[(ticks >= lo * .96) & (ticks <= hi * 1.04)]
            if len(ticks) > 5:
                ticks = ticks[::2]
            ax.set_yticks(ticks, [f"{value:g}" for value in ticks])
        else:
            # Short plain-number scientific labels avoid tiny log-exponent glyphs.
            lo, hi = subset["mean_error"].min(), subset["mean_error"].max()
            exponents = np.arange(int(np.ceil(np.log10(lo))), int(np.floor(np.log10(hi))) + 1)
            if len(exponents) > 4:
                exponents = exponents[::2]
            ax.set_yticks(10.0 ** exponents, [f"1e{int(e)}" for e in exponents])
        ax.yaxis.set_minor_locator(LogLocator(base=10, subs=[2, 3, 4, 5, 6, 7, 8, 9]))
        ax.yaxis.set_minor_formatter(NullFormatter())
        ax.tick_params(axis="y", which="minor", length=1.5)
    fig.supylabel("30-run mean benchmark error (log scale)", x=.02, fontsize=9.2)
    fig.supxlabel("Objective evaluations (thousands)", y=.027, fontsize=9.2)
    fig.legend(handles=handles(), loc="upper center", bbox_to_anchor=(.55, .985),
               ncol=3, frameon=False, columnspacing=2, handlelength=3)
    save(fig, "Fig3", output, editable)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, help="Optional original formal_full_results directory; validates and regenerates bundled means")
    parser.add_argument("--data-dir", type=Path, default=ROOT / "实验数据" / "数据")
    parser.add_argument("--output-dir", type=Path, default=ROOT / "论文" / "figures" / "publication")
    parser.add_argument("--editable-dir", type=Path, default=ROOT / "实验数据" / "图表")
    args = parser.parse_args()
    if args.source:
        prepare_input(args.source, args.data_dir)
    means = load_means(args.data_dir)
    configure_style()
    draw_ranks(means, args.output_dir, args.editable_dir)
    draw_aggregate(means, args.output_dir, args.editable_dir)
    draw_functions(means, args.output_dir, args.editable_dir)
    print("Verified 26,100 mean cells and all nine final ranks; wrote Fig1--Fig3 PDF and editable SVG.")


if __name__ == "__main__":
    main()

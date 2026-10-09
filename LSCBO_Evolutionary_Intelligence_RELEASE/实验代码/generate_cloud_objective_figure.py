#!/usr/bin/env python3
"""Reproduce Fig6 from historical cloud results and the separate direct-pair study.

Run from any directory with Python 3, numpy, pandas, and matplotlib installed.
Defaults resolve relative to this release. No experiment is rerun. The objective
panel uses the recorded per-instance F, never F reconstructed from metric means.
"""

from __future__ import annotations

import argparse
import hashlib
import io
import json
from pathlib import Path
import zipfile

import matplotlib

matplotlib.use("Agg")

import matplotlib.pyplot as plt
import numpy as np
import pandas as pd


SCALES = [200, 500, 1000, 2000, 5000]
SEEDS = list(range(43, 73))
METHODS = ["LSCBO", "CBO", "PSO", "GWO", "WOA", "AOA", "GREEDY_LPT"]
METRICS = ["makespan", "energy", "cost", "imbalance"]
NORMALIZERS = ["normMakespan", "normEnergy", "normCost", "normImbalance"]
TABLE_MEANS = {
    "LSCBO": [0.982632, 0.982617, 0.983700, 0.983444, 0.985240],
    "CBO": [1.000000, 0.999937, 1.000000, 1.000000, 1.000000],
    "PSO": [0.999929, 1.000000, 1.000000, 1.000000, 1.000000],
    **{method: [1.0] * 5 for method in ["GWO", "WOA", "AOA", "GREEDY_LPT"]},
}
BOOTSTRAP_RESAMPLES = 50_000
BOOTSTRAP_SEED = 20260912
WEIGHT = 0.25
PAIR_METHODS = ["full_archived", "pair_only_5400", "pair_only_6000"]


def load_pair_study(release: Path) -> tuple[pd.DataFrame, dict]:
    """Validate the separate rerun and count deterministic quality only once."""
    summary = json.loads((release / "实验数据/数据/pair_only_summary.json").read_text(encoding="utf-8"))
    with zipfile.ZipFile(release / "实验数据/数据/pair_only_evidence.zip") as archive:
        raw = archive.read("pair_only_evidence/results/pair_only_raw.csv")
    digest = hashlib.sha256(raw).hexdigest()
    if digest != summary["rawSha256"]:
        raise ValueError("Pair-study raw data hash differs from the released summary")
    frame = pd.read_csv(io.BytesIO(raw))
    keys = ["taskCount", "seed", "method"]
    expected = pd.MultiIndex.from_product([SCALES, SEEDS, PAIR_METHODS], names=keys)
    groups = frame.groupby(keys)
    if len(frame) != 2250 or not groups.size().eq(5).all():
        raise ValueError("Expected 150 blocks x 3 methods x 5 timed repetitions")
    if frame.duplicated(keys + ["repetition"]).any() or set(frame.repetition) != set(range(5)):
        raise ValueError("Missing or duplicate pair-study repetitions")
    quality = METRICS + NORMALIZERS + ["objective", "actualScoringCalls", "coreCandidates",
        "localCandidates", "acceptedExchanges", "netChangedTasks", "localStartSHA256", "finalSHA256"]
    if not groups[quality].nunique(dropna=False).eq(1).all().all():
        raise ValueError("Quality differs across deterministic timed repetitions")
    unique = frame[frame.repetition.eq(0)].set_index(keys)
    if not unique.index.sort_values().equals(expected.sort_values()):
        raise ValueError("Unexpected pair-study block coverage")
    unique = unique.reindex(expected)
    numeric = METRICS + NORMALIZERS + ["objective"]
    if not np.isfinite(unique[numeric]).all().all() or not unique[NORMALIZERS].gt(0).all().all():
        raise ValueError("Invalid pair-study metrics or normalization references")
    full = unique.xs("full_archived", level="method")
    same = unique.xs("pair_only_5400", level="method")
    if not full[quality].drop(columns=["actualScoringCalls", "coreCandidates"]).equals(
        same[quality].drop(columns=["actualScoringCalls", "coreCandidates"])
    ):
        raise ValueError("Direct 5400 must reproduce full rerun quality and assignment hashes")
    for method in PAIR_METHODS:
        rows = unique.xs(method, level="method")
        calls = 5402 if method == "pair_only_5400" else 6002
        if not rows.actualScoringCalls.eq(calls).all():
            raise ValueError(f"Unexpected actual scoring calls for {method}")
        if not rows[NORMALIZERS].equals(full[NORMALIZERS]):
            raise ValueError("Pair-study methods use different reference normalizers")
    direct = unique.xs("pair_only_6000", level="method")
    checks = {"raw_sha256": digest, "raw_rows": len(frame), "quality_rows": len(unique),
              "blocks_per_method": 150, "timed_repetitions_per_block": 5,
              "quality_observations_per_block": 1, "full_direct5400_identical_assignments": 150,
              "direct6000_higher_components_than_full": {
                  metric: int((direct[metric] > full[metric]).sum()) for metric in METRICS}}
    return unique, checks


def summarize_pair_study(frame: pd.DataFrame) -> pd.DataFrame:
    """Pointwise intervals retain the same 30 seed resamples across all scales."""
    indices = np.random.Generator(np.random.PCG64(BOOTSTRAP_SEED)).integers(
        0, 30, size=(BOOTSTRAP_RESAMPLES, 30))
    rows = []
    for scale in SCALES:
        for method in PAIR_METHODS:
            group = frame.xs((scale, method), level=("taskCount", "method")).sort_index()
            values = group.objective.to_numpy()
            low, high = np.quantile(values[indices].mean(axis=1), [.025, .975])
            rows.append({"taskCount": scale, "algorithm": method, "n": 30,
                         "mean": values.mean(), "ci_low": low, "ci_high": high,
                         "source": "pair_only_20260924", "timed_repetitions_per_block": 5,
                         "quality_observations_per_block": 1,
                         "actual_scoring_calls": int(group.actualScoringCalls.iloc[0])})
    return pd.DataFrame(rows)


def load_data(csv_path: Path, provenance_path: Path) -> pd.DataFrame:
    """Reject changed records, duplicate rows, or incomplete paired blocks."""
    provenance = json.loads(provenance_path.read_text(encoding="utf-8"))
    digest = hashlib.sha256(csv_path.read_bytes()).hexdigest()
    if digest != provenance["subset_sha256"]:
        raise ValueError("Data hash does not match the released provenance record")
    frame = pd.read_csv(csv_path)
    if len(frame) != 1_050 or set(frame["algorithm"]) != set(METHODS):
        raise ValueError("Expected 1,050 rows covering all seven main-comparison methods")
    expected = {
        "experiment": {"E4_main"},
        "weightProfile": {"W0_equal"},
        "vmProfile": {"default_4_to_1"},
        "vmCount": {50},
        "taskCount": set(SCALES),
        "seed": set(SEEDS),
    }
    for column, values in expected.items():
        if set(frame[column]) != values:
            raise ValueError(f"Unexpected values in {column}")
    keys = ["taskCount", "seed", "algorithm"]
    if frame.duplicated(keys).any():
        raise ValueError("Duplicate method / scale / seed rows")
    expected_index = pd.MultiIndex.from_product([SCALES, SEEDS, METHODS], names=keys)
    indexed = frame.set_index(keys).reindex(expected_index)
    if indexed.isna().any().any():
        raise ValueError("Missing paired records or values")
    if not np.isfinite(indexed[METRICS + NORMALIZERS + ["objective"]].to_numpy()).all():
        raise ValueError("Nonfinite metrics, references, or objective")
    if (indexed[NORMALIZERS] <= 0).any().any():
        raise ValueError("Normalization references must be positive")
    for method in METHODS:
        expected_calls = 1 if method == "GREEDY_LPT" else 6000
        if not (indexed.xs(method, level="algorithm")["totalEvaluations"] == expected_calls).all():
            raise ValueError(f"Unexpected recorded evaluation counts for {method}")
    baseline = indexed.xs("GREEDY_LPT", level="algorithm")
    if not np.array_equal(baseline["objective"].to_numpy(), np.ones(150)):
        raise ValueError("Recorded Greedy-LPT reference must have F = 1")
    for method in METHODS:
        other = indexed.xs(method, level="algorithm")
        if not np.array_equal(other[NORMALIZERS].to_numpy(), baseline[NORMALIZERS].to_numpy()):
            raise ValueError(f"Non-shared reference normalization for {method}")
    for method in ["GWO", "WOA", "AOA"]:
        other = indexed.xs(method, level="algorithm")
        if not np.array_equal(other[METRICS + ["objective"]].to_numpy(), baseline[METRICS + ["objective"]].to_numpy()):
            raise ValueError(f"Cannot group {method}: its records differ from Greedy-LPT")
    return indexed


def summarize(frame: pd.DataFrame) -> tuple[pd.DataFrame, pd.DataFrame, dict]:
    """Use seed clusters for CIs; keep fixed scales and all methods together."""
    cube = frame["objective"].to_numpy(dtype=float).reshape(5, 30, 7)
    generator = np.random.Generator(np.random.PCG64(BOOTSTRAP_SEED))
    indices = generator.integers(0, 30, size=(BOOTSTRAP_RESAMPLES, 30))
    rows = []
    maximum_table_error = 0.0
    # The same cluster indices are used at every scale and for every method.
    # Processing one cell at a time bounds memory without breaking that pairing.
    for scale_index, scale in enumerate(SCALES):
        for method_index, method in enumerate(METHODS):
            values = cube[scale_index, :, method_index]
            mean = float(values.mean())
            low, high = np.quantile(values[indices].mean(axis=1), [0.025, 0.975])
            error = abs(mean - TABLE_MEANS[method][scale_index])
            maximum_table_error = max(maximum_table_error, error)
            if error > 0.5e-6:
                raise ValueError(f"Objective mean does not round to the manuscript table: {method}, N={scale}")
            rows.append({"taskCount": scale, "algorithm": method, "n": 30,
                         "mean": mean, "ci_low": float(low), "ci_high": float(high)})
    summary = pd.DataFrame(rows)
    baseline = frame.xs("GREEDY_LPT", level="algorithm")
    lscbo = frame.xs("LSCBO", level="algorithm")
    reductions = 100 * WEIGHT * (
        baseline[METRICS].to_numpy() - lscbo[METRICS].to_numpy()
    ) / baseline[NORMALIZERS].to_numpy()
    objective_reductions = 100 * (baseline["objective"].to_numpy() - lscbo["objective"].to_numpy())
    decomposition_error = float(np.max(np.abs(reductions.sum(axis=1) - objective_reductions)))
    # Each component and F was independently rounded to eight decimals in the archive.
    if decomposition_error > 1e-3:
        raise ValueError("Weighted decomposition differs beyond archived rounding precision")
    contributions = pd.DataFrame(reductions, index=baseline.index, columns=METRICS)
    contributions = contributions.groupby(level="taskCount").mean().reset_index()
    improvements = {}
    for method in METHODS[1:]:
        difference = lscbo["objective"] - frame.xs(method, level="algorithm")["objective"]
        improvements[method] = {"wins": int((difference < -1e-12).sum()),
                                "ties": int((difference.abs() <= 1e-12).sum()),
                                "losses": int((difference > 1e-12).sum())}
    checks = {"rows": len(frame), "paired_blocks": 150, "seeds": 30,
              "max_abs_error_against_six_decimal_table": maximum_table_error,
              "max_decomposition_rounding_error_percentage_points": decomposition_error,
              "lscbo_objective_comparisons": improvements}
    return summary, contributions, checks


def configure_style() -> None:
    """Use physical point sizes at the manuscript's 130.8 mm insertion width."""
    plt.rcParams.update({"font.family": "DejaVu Sans", "font.size": 8.5,
                         "axes.labelsize": 8.5, "axes.titlesize": 9,
                         "legend.fontsize": 8, "xtick.labelsize": 8,
                         "ytick.labelsize": 8, "pdf.fonttype": 42,
                         "ps.fonttype": 42, "svg.fonttype": "none"})


def paired_reductions(frame: pd.DataFrame) -> pd.DataFrame:
    """Keep each scale/seed pair and its recorded objective difference in pp."""
    baseline = frame.xs("GREEDY_LPT", level="algorithm")["objective"]
    lscbo = frame.xs("LSCBO", level="algorithm")["objective"]
    pairs = pd.DataFrame({"reference_F": baseline, "lscbo_F": lscbo})
    pairs["reduction_pp"] = 100 * (pairs["reference_F"] - pairs["lscbo_F"])
    if not np.isfinite(pairs.to_numpy()).all() or len(pairs) != 150:
        raise ValueError("Expected 150 finite recorded objective pairs")
    return pairs.reset_index()


def draw(summary: pd.DataFrame, contributions: pd.DataFrame,
         pairs: pd.DataFrame, output: Path) -> None:
    configure_style()
    # The canvas itself has the final insertion width: save without bbox cropping.
    figure = plt.figure(figsize=(130.8 / 25.4, 6.75))
    objective_ax = figure.add_axes([0.15, 0.765, 0.815, 0.190])
    component_ax = figure.add_axes([0.15, 0.310, 0.815, 0.235])
    table_ax = figure.add_axes([0.027, 0.036, 0.946, 0.182])
    table_ax.set_axis_off()
    x = np.arange(len(SCALES))
    styles = {
        "LSCBO": ("#D55E00", "o", "-", "LSCBO + pair (archive)", 5),
        "CBO": ("#333333", "v", "--", "Adapted CBO", 4),
        "PSO": ("#009E73", "^", "-.", "PSO", 3),
        "GREEDY_LPT": ("#666666", "s", ":", "Tied group", 2),
        "pair_only_5400": ("#0072B2", "s", "--", "Direct 5400 (new)", 4),
        "pair_only_6000": ("#CC79A7", "D", "-.", "Direct 6000 (new)", 6),
    }
    for method, (color, marker, line, label, zorder) in styles.items():
        subset = summary[summary["algorithm"].eq(method)].set_index("taskCount").loc[SCALES]
        mean = subset["mean"].to_numpy()
        error = np.vstack([mean - subset["ci_low"], subset["ci_high"] - mean])
        objective_ax.errorbar(x, mean, yerr=error, color=color, marker=marker,
                             linestyle=line, markersize=7.5 if method == "pair_only_5400" else (4.7 if method == "LSCBO" else 5.5),
                             markerfacecolor="white", markeredgewidth=.9, linewidth=1.35,
                             capsize=2.5, elinewidth=.9, label=label, zorder=zorder)
    objective_ax.set_ylabel(r"Mean objective $F$")
    objective_ax.set_title(r"(a) Final objective $F$ (expanded vertical scale)", loc="left", pad=8)
    objective_ax.set_ylim(.976, 1.0025)
    objective_ax.set_yticks([.98, .985, .99, .995, 1.0])
    figure.legend(*objective_ax.get_legend_handles_labels(),
                  loc="center", bbox_to_anchor=(.555, .665),
                  ncol=3, frameon=False, handlelength=1.7,
                  columnspacing=1.0, handletextpad=.45, borderaxespad=0)
    objective_ax.text(.035, .76,
                      "Adapted CBO: 0.999937 at 500; PSO: 0.999929 at 200.\n"
                      "GWO / WOA / Adapted AOA / Greedy-LPT means = 1.000000.",
                      transform=objective_ax.transAxes, fontsize=8,
                      ha="left", va="top", color="#444444")
    figure.text(.15, .620, "Tied group: GWO / WOA / Adapted AOA / Greedy-LPT.", fontsize=8)

    colors = ["#4C78A8", "#E18A25", "#4F9854", "#D55E00"]
    labels = [r"$H_{\mathrm{max}}$", r"$E$", r"$C$", r"$\mathcal{I}$"]
    bottom = np.zeros(len(SCALES))
    for metric, color, label in zip(METRICS, colors, labels):
        values = contributions[metric].to_numpy()
        if (values < 0).any():
            raise ValueError("This nonnegative stack requires nonnegative component means")
        # No white outlines: they would cover the genuinely tiny component terms.
        component_ax.bar(x, values, bottom=bottom, width=.23,
                         color=color, linewidth=0, label=label, zorder=3)
        bottom += values
    # Horizontal spreading only separates seed markers inside each task category.
    # Vertical coordinates always remain the original paired differences.
    offsets = np.concatenate([np.linspace(-.33, -.16, 15), np.linspace(.16, .33, 15)])
    for position, scale in zip(x, SCALES):
        values = pairs[pairs["taskCount"].eq(scale)].sort_values("seed")["reduction_pp"].to_numpy()
        component_ax.scatter(position + offsets, values, s=10, facecolors="none",
                             edgecolors="#555555", linewidths=.55, alpha=.85,
                             label="Instances" if position == 0 else None, zorder=4)
    component_ax.axhline(0, color="#555555", linewidth=.8, zorder=2)
    component_ax.set_ylabel(r"$F$ reduction vs. Greedy-LPT (pp)")
    component_ax.set_title("(b) Paired reductions and mean contributions", loc="left", pad=22)
    component_ax.set_ylim(-.15, max(6., float(pairs["reduction_pp"].max()) * 1.08))
    component_ax.set_yticks([0, 2, 4, 6])
    component_ax.legend(loc="lower center", bbox_to_anchor=(.5, 1.005), ncol=5,
                        frameon=False, handlelength=1.0, columnspacing=1.1,
                        handletextpad=.4, borderaxespad=0)

    # The aligned numerical strip exposes nonzero components that are below
    # printed line resolution; no axis break or magnification changes bar area.
    table_rows = [[label] + [f"{value:.5f}" for value in contributions[metric]]
                  for label, metric in zip(labels, METRICS)]
    table = table_ax.table(cellText=table_rows,
                           colLabels=["Term (pp)"] + [str(n) for n in SCALES],
                           cellLoc="center", loc="center",
                           colWidths=[.21] + [.158] * 5)
    table.auto_set_font_size(False)
    table.set_fontsize(8)
    table.scale(1, 1.24)
    for (row, col), cell in table.get_celld().items():
        cell.set_linewidth(.35)
        cell.set_edgecolor("#DADADA")
        cell.set_facecolor("#F2F2F2" if row == 0 else "white")
        if row > 0 and col == 0:
            cell.get_text().set_color(colors[row - 1])
    figure.text(.027, .013,
                r"Mean of $100\,w_k\,[f_k(s_g)-f_k(s)]/c_k$ (pp), over 30 seeds; $w_k=0.25$.",
                fontsize=8)
    for axis in (objective_ax, component_ax):
        axis.set_xticks(x, [str(scale) for scale in SCALES])
        axis.set_xlabel(r"Number of tasks $N$")
        axis.set_xlim(-.5, len(SCALES) - .5)
        axis.grid(axis="y", color="#DDDDDD", linestyle="--", linewidth=.55, zorder=0)
        axis.set_axisbelow(True)
        axis.spines[["top", "right"]].set_visible(False)
        axis.tick_params(direction="out", length=3, width=.8)
    output.parent.mkdir(parents=True, exist_ok=True)
    figure.savefig(output, facecolor="white", dpi=300)
    plt.close(figure)


def main() -> None:
    release = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data", type=Path, default=release / "实验数据/数据/cloud_main_E4_20260619.csv")
    parser.add_argument("--provenance", type=Path, default=release / "实验数据/数据/cloud_main_E4_20260619.provenance.json")
    parser.add_argument("--pdf", type=Path, default=release / "论文/figures/publication/Fig6.pdf")
    parser.add_argument("--svg", type=Path, default=release / "实验数据/图表/Fig6_cloud_objective.svg")
    parser.add_argument("--png", type=Path, help="Optional 300 dpi preview")
    parser.add_argument("--summary", type=Path, help="Optional full mean / CI CSV")
    parser.add_argument("--contributions", type=Path, help="Optional weighted component reduction CSV, in percentage points")
    parser.add_argument("--pairs", type=Path, help="Optional 150-row paired objective reduction CSV")
    args = parser.parse_args()
    frame = load_data(args.data, args.provenance)
    summary, contributions, checks = summarize(frame)
    pair_frame, pair_checks = load_pair_study(release)
    full_rerun = pair_frame.xs("full_archived", level="method")
    history = frame.xs("LSCBO", level="algorithm")
    if not np.allclose(full_rerun[METRICS + ["objective"]],
                       history[METRICS + ["objective"]], rtol=0, atol=5.1e-9):
        raise ValueError("New full rerun does not reproduce historical quality at archive precision")
    summary["source"] = "historical_E4_20260619"
    summary["timed_repetitions_per_block"] = 1
    summary["quality_observations_per_block"] = 1
    summary["actual_scoring_calls"] = summary.algorithm.map(
        lambda method: 1 if method == "GREEDY_LPT" else (6002 if method == "LSCBO" else 6001))
    summary = pd.concat([summary, summarize_pair_study(pair_frame)], ignore_index=True)
    checks["separate_pair_study"] = pair_checks
    pairs = paired_reductions(frame)
    checks["paired_reduction_pp"] = {
        "count": len(pairs), "min": float(pairs["reduction_pp"].min()),
        "mean": float(pairs["reduction_pp"].mean()),
        "max": float(pairs["reduction_pp"].max()),
        "positive": int((pairs["reduction_pp"] > 0).sum()),
    }
    for output in [args.pdf, args.svg, args.png]:
        if output is not None:
            draw(summary, contributions, pairs, output)
    if args.summary is not None:
        args.summary.parent.mkdir(parents=True, exist_ok=True)
        summary.to_csv(args.summary, index=False, float_format="%.12g")
    if args.contributions is not None:
        args.contributions.parent.mkdir(parents=True, exist_ok=True)
        contributions.to_csv(args.contributions, index=False, float_format="%.12g")
    if args.pairs is not None:
        args.pairs.parent.mkdir(parents=True, exist_ok=True)
        pairs.to_csv(args.pairs, index=False, float_format="%.12g")
    print(json.dumps(checks, indent=2))


if __name__ == "__main__":
    main()

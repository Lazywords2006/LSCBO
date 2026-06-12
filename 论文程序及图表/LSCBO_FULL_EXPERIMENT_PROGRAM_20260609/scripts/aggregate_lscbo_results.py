#!/usr/bin/env python3
"""Aggregate LSCBO formal experiment shards into tables, figures, and reports."""

from __future__ import annotations

import argparse
import csv
import math
import statistics
from collections import defaultdict
from pathlib import Path


ALGORITHMS = ["LSCBO", "CBO", "GTO", "WOA", "HHO", "GWO", "DBO", "PSO", "AOA"]
METRICS = ["makespan", "energy", "cost", "imbalance", "objective"]
PLOT_COLORS = {
    "LSCBO": "#D00000",
    "CBO": "#A9A9A9",
    "GTO": "#3A8B3A",
    "WOA": "#E8845A",
    "HHO": "#6AA6D8",
    "GWO": "#7E57C2",
    "DBO": "#8C6D62",
    "PSO": "#BCBCBC",
    "AOA": "#707070",
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--run-dir", type=Path, required=True)
    return parser.parse_args()


def read_csv(path: Path) -> list[dict[str, str]]:
    with path.open(newline="", encoding="utf-8-sig") as f:
        return list(csv.DictReader(f))


def write_csv(path: Path, fieldnames: list[str], rows: list[dict[str, object]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        for row in rows:
            writer.writerow(row)


def mean(values: list[float]) -> float:
    return statistics.fmean(values) if values else math.nan


def fmt(value: float, digits: int = 8) -> float:
    if math.isnan(value):
        return value
    return round(value, digits)


def average_tie_ranks(values: dict[str, float]) -> dict[str, float]:
    ordered = sorted(values.items(), key=lambda item: item[1])
    ranks: dict[str, float] = {}
    i = 0
    while i < len(ordered):
        j = i + 1
        while j < len(ordered) and math.isclose(ordered[j][1], ordered[i][1], abs_tol=1e-12):
            j += 1
        rank = (i + 1 + j) / 2.0
        for k in range(i, j):
            ranks[ordered[k][0]] = rank
        i = j
    return ranks


def load_rows(run_dir: Path) -> list[dict[str, str]]:
    raw_files = sorted((run_dir / "shards").glob("*/claim_*_raw.csv"))
    rows: list[dict[str, str]] = []
    for file in raw_files:
        rows.extend(read_csv(file))
    if not rows:
        raise RuntimeError(f"No raw shard CSV files found under {run_dir / 'shards'}")
    return rows


def combine_raw(final_dir: Path, rows: list[dict[str, str]]) -> None:
    fieldnames = list(rows[0].keys())
    write_csv(final_dir / "claim_full_raw.csv", fieldnames, rows)


def group_means(rows: list[dict[str, str]], keys: list[str], metrics: list[str]) -> list[dict[str, object]]:
    grouped: dict[tuple[str, ...], dict[str, list[float]]] = defaultdict(lambda: defaultdict(list))
    for row in rows:
        key = tuple(row[k] for k in keys)
        for metric in metrics:
            grouped[key][metric].append(float(row[metric]))
    output: list[dict[str, object]] = []
    for key in sorted(grouped):
        record: dict[str, object] = {name: value for name, value in zip(keys, key)}
        record["n"] = len(next(iter(grouped[key].values())))
        for metric in metrics:
            record[f"mean_{metric}"] = fmt(mean(grouped[key][metric]))
        output.append(record)
    return output


def main_multicost_table(rows: list[dict[str, str]], tables_dir: Path) -> None:
    selected = [
        row for row in rows
        if row["experiment"] == "E4_main"
        and row["weightProfile"] == "W0_equal"
        and row["vmProfile"] == "default_4_to_1"
    ]
    table = group_means(selected, ["algorithm", "taskCount"], METRICS)
    write_csv(
        tables_dir / "table_main_multicost_by_scale.csv",
        ["algorithm", "taskCount", "n", *[f"mean_{m}" for m in METRICS]],
        table,
    )

    by_group = {(r["algorithm"], r["taskCount"]): r for r in table}
    relative: list[dict[str, object]] = []
    for row in table:
        base = by_group.get(("CBO", row["taskCount"]))
        if not base:
            continue
        record: dict[str, object] = {"algorithm": row["algorithm"], "taskCount": row["taskCount"], "n": row["n"]}
        for metric in METRICS:
            record[f"relative_{metric}_to_CBO"] = fmt(float(row[f"mean_{metric}"]) / float(base[f"mean_{metric}"]))
        relative.append(record)
    write_csv(
        tables_dir / "table_main_relative_to_cbo_by_scale.csv",
        ["algorithm", "taskCount", "n", *[f"relative_{m}_to_CBO" for m in METRICS]],
        relative,
    )


def rank_table(rows: list[dict[str, str]], tables_dir: Path) -> None:
    selected = [
        row for row in rows
        if row["experiment"] == "E4_main"
        and row["weightProfile"] == "W0_equal"
        and row["vmProfile"] == "default_4_to_1"
    ]
    rank_lists: dict[str, dict[str, list[float]]] = {
        metric: defaultdict(list) for metric in METRICS
    }
    blocks: dict[tuple[int, int], list[dict[str, str]]] = defaultdict(list)
    for row in selected:
        blocks[(int(row["taskCount"]), int(row["seed"]))].append(row)
    for block_rows in blocks.values():
        algos = {row["algorithm"] for row in block_rows}
        if not all(algo in algos for algo in ALGORITHMS):
            continue
        for metric in METRICS:
            values = {row["algorithm"]: float(row[metric]) for row in block_rows}
            for algo, rank in average_tie_ranks(values).items():
                rank_lists[metric][algo].append(rank)

    output: list[dict[str, object]] = []
    for algo in ALGORITHMS:
        record: dict[str, object] = {"algorithm": algo}
        for metric in METRICS:
            ranks = rank_lists[metric][algo]
            record[f"rank_{metric}"] = fmt(mean(ranks), 6)
            record[f"blocks_{metric}"] = len(ranks)
        output.append(record)
    output.sort(key=lambda r: float(r["rank_objective"]))
    write_csv(
        tables_dir / "table_main_average_ranks.csv",
        ["algorithm", *sum(([f"rank_{m}", f"blocks_{m}"] for m in METRICS), [])],
        output,
    )


def experiment_family_tables(rows: list[dict[str, str]], tables_dir: Path) -> None:
    specs = [
        ("E2_ablation", ["variant"], "table_component_ablation.csv"),
        ("E3_evolution", ["variant"], "table_evolution_path.csv"),
        ("weight_sensitivity", ["weightProfile", "algorithm"], "table_weight_sensitivity.csv"),
        ("vm_heterogeneity", ["vmProfile", "algorithm"], "table_vm_heterogeneity.csv"),
        ("perturbation", ["perturbation"], "table_perturbation.csv"),
    ]
    for experiment, keys, filename in specs:
        selected = [row for row in rows if row["experiment"] == experiment]
        table = group_means(selected, keys, METRICS)
        write_csv(tables_dir / filename, [*keys, "n", *[f"mean_{m}" for m in METRICS]], table)


def try_import_matplotlib():
    try:
        import matplotlib.pyplot as plt  # type: ignore
        return plt
    except Exception:
        return None


def set_bar_grouped_hatch_style(plt) -> None:
    plt.rcParams.update(
        {
            "text.usetex": False,
            "font.family": "serif",
            "font.serif": ["Computer Modern Roman", "STIX Two Text", "DejaVu Serif"],
            "axes.unicode_minus": False,
            "hatch.color": "white",
            "hatch.linewidth": 1.4,
        }
    )


def set_line_confidence_style(plt) -> None:
    plt.rcParams.update(
        {
            "text.usetex": False,
            "font.family": "serif",
            "font.serif": ["Computer Modern Roman", "STIX Two Text", "DejaVu Serif"],
            "axes.unicode_minus": False,
        }
    )


def style_bar_axis(ax) -> None:
    ax.yaxis.grid(True, color="#EBEBEB", linewidth=0.7, linestyle="--", zorder=0)
    ax.set_axisbelow(True)
    for side, spine in ax.spines.items():
        if side in ("top", "right"):
            spine.set_visible(False)
        else:
            spine.set_linewidth(0.9)
            spine.set_color("#333333")
    ax.tick_params(length=0, labelsize=10.2)


def style_confidence_axis(ax) -> None:
    for side, spine in ax.spines.items():
        spine.set_visible(side in ("left", "bottom"))
        if spine.get_visible():
            spine.set_linewidth(0.9)
            spine.set_color("#333333")
    ax.tick_params(direction="in", length=4.5, width=1.0, labelsize=10)
    ax.grid(False)


def style_legend_main_bold(legend) -> None:
    if legend is None:
        return
    for text in legend.get_texts():
        if text.get_text() == "LSCBO":
            text.set_fontweight("bold")


def make_figures(rows: list[dict[str, str]], figures_dir: Path) -> list[str]:
    plt = try_import_matplotlib()
    if plt is None:
        return ["matplotlib not available; figures skipped"]
    figures_dir.mkdir(parents=True, exist_ok=True)
    warnings: list[str] = []
    selected = [
        row for row in rows
        if row["experiment"] == "E4_main"
        and row["weightProfile"] == "W0_equal"
        and row["vmProfile"] == "default_4_to_1"
    ]

    grouped: dict[str, dict[int, dict[str, list[float]]]] = defaultdict(lambda: defaultdict(lambda: defaultdict(list)))
    for row in selected:
        for metric in METRICS:
            grouped[row["algorithm"]][int(row["taskCount"])][metric].append(float(row[metric]))
    task_counts = sorted({int(row["taskCount"]) for row in selected})

    set_line_confidence_style(plt)
    fig, axes = plt.subplots(2, 3, figsize=(12, 7))
    axes_flat = list(axes.ravel())
    for ax, metric in zip(axes_flat, METRICS):
        for algo in ["LSCBO", "CBO", "GTO", "WOA", "GWO", "PSO", "AOA"]:
            values = []
            for task in task_counts:
                base = mean(grouped["CBO"][task][metric])
                current = mean(grouped[algo][task][metric])
                values.append(current / base if base else math.nan)
            ax.plot(task_counts, values, marker="o", linewidth=2.3 if algo == "LSCBO" else 1.3,
                    color=PLOT_COLORS.get(algo, "#333333"), label=algo)
        ax.axhline(1.0, color="#999999", linewidth=1.4, linestyle=(0, (1, 2)))
        ax.set_title(metric)
        ax.set_xlabel("Task count")
        ax.set_ylabel("Relative to CBO")
        style_confidence_axis(ax)
    axes_flat[-1].axis("off")
    handles, labels = axes_flat[0].get_legend_handles_labels()
    legend = axes_flat[-1].legend(handles, labels, framealpha=0, edgecolor="none", loc="center")
    style_legend_main_bold(legend)
    fig.tight_layout()
    fig.savefig(figures_dir / "fig_main_multicost_relative_to_cbo.png", dpi=300, bbox_inches="tight")
    plt.close(fig)

    rank_rows = read_csv(figures_dir.parent / "tables" / "table_main_average_ranks.csv")
    ordered = sorted(rank_rows, key=lambda r: float(r["rank_objective"]))
    set_bar_grouped_hatch_style(plt)
    fig, ax = plt.subplots(figsize=(9.5, 5.2))
    ax.bar([r["algorithm"] for r in ordered], [float(r["rank_objective"]) for r in ordered],
           color=[PLOT_COLORS.get(r["algorithm"], "#888888") for r in ordered],
           edgecolor="white",
           hatch=["//" if r["algorithm"] == "LSCBO" else "" for r in ordered],
           linewidth=0.8,
           zorder=2)
    for i, row in enumerate(ordered):
        value = float(row["rank_objective"])
        ax.text(i, value + 0.08, f"{value:.3f}", ha="center", va="bottom",
                fontsize=8.7,
                fontweight="bold" if row["algorithm"] == "LSCBO" else "normal",
                color="#8B0000" if row["algorithm"] == "LSCBO" else "black")
    ax.set_ylabel("Average objective rank (lower is better)")
    ax.set_xlabel("Scheduler")
    ax.set_title("Formal Full Run: Objective Average Rank")
    style_bar_axis(ax)
    fig.autofmt_xdate(rotation=25, ha="right")
    fig.tight_layout()
    fig.savefig(figures_dir / "fig_main_objective_rank.png", dpi=300, bbox_inches="tight")
    plt.close(fig)

    set_line_confidence_style(plt)
    fig, ax = plt.subplots(figsize=(10, 5.6))
    for algo in ["LSCBO", "CBO", "GTO", "WOA", "GWO", "PSO", "AOA"]:
        values = [mean(grouped[algo][task]["objective"]) for task in task_counts]
        ax.plot(task_counts, values, marker="o", linewidth=2.3 if algo == "LSCBO" else 1.3,
                color=PLOT_COLORS.get(algo, "#333333"), label=algo)
    ax.set_xlabel("Task count")
    ax.set_ylabel("Mean overall objective")
    ax.set_title("Formal Full Run: Overall Objective by Task Scale")
    style_confidence_axis(ax)
    legend = ax.legend(framealpha=0, edgecolor="none", ncol=3)
    style_legend_main_bold(legend)
    fig.tight_layout()
    fig.savefig(figures_dir / "fig_main_objective_by_scale.png", dpi=300, bbox_inches="tight")
    plt.close(fig)
    return warnings


def write_report(run_dir: Path, rows: list[dict[str, str]], warnings: list[str]) -> None:
    report = run_dir / "final_outputs" / "FORMAL_RUN_REPORT.md"
    experiments = defaultdict(int)
    for row in rows:
        experiments[row["experiment"]] += 1
    seeds = sorted({int(row["seed"]) for row in rows})
    cloudsim_values = {row["cloudsimFinishedCloudlets"] for row in rows}
    cloudsim_mode = "enabled" if any(value != "-1" for value in cloudsim_values) else "not run for formal rows"

    lines = [
        "# LSCBO Formal Full Experiment Report",
        "",
        f"- Total raw rows: {len(rows)}",
        f"- Seed range in data: {seeds[0]}-{seeds[-1]} ({len(seeds)} seed values)",
        f"- CloudSim validation in these rows: {cloudsim_mode}",
        "",
        "## Experiment Families",
        "",
    ]
    for experiment, count in sorted(experiments.items()):
        lines.append(f"- {experiment}: {count} rows")
    lines.extend([
        "",
        "## Generated Outputs",
        "",
        "- `final_outputs/claim_full_raw.csv`",
        "- `final_outputs/tables/table_main_multicost_by_scale.csv`",
        "- `final_outputs/tables/table_main_relative_to_cbo_by_scale.csv`",
        "- `final_outputs/tables/table_main_average_ranks.csv`",
        "- `final_outputs/tables/table_component_ablation.csv`",
        "- `final_outputs/tables/table_evolution_path.csv`",
        "- `final_outputs/tables/table_weight_sensitivity.csv`",
        "- `final_outputs/tables/table_vm_heterogeneity.csv`",
        "- `final_outputs/tables/table_perturbation.csv`",
        "- `final_outputs/figures/fig_main_multicost_relative_to_cbo.png`",
        "- `final_outputs/figures/fig_main_objective_rank.png`",
        "- `final_outputs/figures/fig_main_objective_by_scale.png`",
        "",
        "## Notes",
        "",
        "- The formal default run produces complete deterministic multi-cost experiment data.",
        "- Use `formal-cloudsim` only when you intentionally want CloudSim validation for every generated row; it is much slower.",
        "- Use `smoke-cloudsim` for a quick CloudSim sanity check before long runs.",
    ])
    if warnings:
        lines.extend(["", "## Warnings", ""])
        lines.extend(f"- {warning}" for warning in warnings)
    report.write_text("\n".join(lines) + "\n", encoding="utf-8")


def cleanup_appledouble_files(run_dir: Path) -> int:
    removed = 0
    for path in run_dir.rglob("._*"):
        if path.is_file():
            try:
                path.unlink()
                removed += 1
            except OSError:
                pass
    return removed


def main() -> int:
    args = parse_args()
    run_dir = args.run_dir.resolve()
    final_dir = run_dir / "final_outputs"
    tables_dir = final_dir / "tables"
    figures_dir = final_dir / "figures"
    rows = load_rows(run_dir)
    final_dir.mkdir(parents=True, exist_ok=True)

    combine_raw(final_dir, rows)
    main_multicost_table(rows, tables_dir)
    rank_table(rows, tables_dir)
    experiment_family_tables(rows, tables_dir)
    warnings = make_figures(rows, figures_dir)
    write_report(run_dir, rows, warnings)
    removed = cleanup_appledouble_files(run_dir)

    print(f"[aggregate] rows={len(rows)}")
    print(f"[aggregate] final={final_dir}")
    if removed:
        print(f"[aggregate] cleaned_appledouble_files={removed}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

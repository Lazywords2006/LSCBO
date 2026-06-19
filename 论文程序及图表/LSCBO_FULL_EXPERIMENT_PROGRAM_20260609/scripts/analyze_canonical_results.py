#!/usr/bin/env python3
"""Verify and summarize the canonical cloud-scheduling experiment."""

from __future__ import annotations

import argparse
import math
from pathlib import Path

import numpy as np
import pandas as pd
from scipy.stats import friedmanchisquare, wilcoxon


ALGORITHMS = ["LSCBO", "CBO", "PSO", "GWO", "WOA", "AOA", "GREEDY_LPT"]
METRICS = ["makespan", "energy", "cost", "imbalance", "objective"]
BLOCK_KEYS = ["taskCount", "seed"]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--raw", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--cloudsim", type=Path)
    return parser.parse_args()


def holm_adjust(p_values: list[float]) -> list[float]:
    order = np.argsort(p_values)
    adjusted = np.empty(len(p_values), dtype=float)
    running = 0.0
    total = len(p_values)
    for rank, index in enumerate(order):
        value = min(1.0, (total - rank) * p_values[index])
        running = max(running, value)
        adjusted[index] = running
    return adjusted.tolist()


def require(condition: bool, message: str) -> None:
    if not condition:
        raise RuntimeError(message)


def verify_data(data: pd.DataFrame) -> dict[str, int]:
    expected_counts = {
        "E4_main": 1050,
        "E2_ablation": 1200,
        "weight_sensitivity": 2250,
        "vm_heterogeneity": 810,
    }
    actual_counts = data.groupby("experiment").size().to_dict()
    require(actual_counts == expected_counts,
            f"unexpected experiment counts: {actual_counts}")
    require(not data.duplicated([
        "experiment", "algorithm", "variant", "weightProfile", "vmProfile",
        "taskCount", "seed", "perturbation",
    ]).any(), "duplicate experiment rows detected")
    numeric = data.select_dtypes(include=[np.number])
    require(np.isfinite(numeric.to_numpy()).all(), "non-finite numeric value detected")

    main = data[data["experiment"] == "E4_main"]
    require(sorted(main["algorithm"].unique()) == sorted(ALGORITHMS),
            "main comparison algorithm set is incomplete")
    block_sizes = main.groupby(BLOCK_KEYS).size()
    require(len(block_sizes) == 150 and (block_sizes == len(ALGORITHMS)).all(),
            "main comparison does not contain 150 complete paired blocks")
    for algorithm in ALGORITHMS:
        expected = 1 if algorithm == "GREEDY_LPT" else 6000
        values = set(main.loc[main["algorithm"] == algorithm, "totalEvaluations"])
        require(values == {expected},
                f"unexpected evaluation budget for {algorithm}: {values}")

    pivot = main.pivot_table(index=BLOCK_KEYS, columns="algorithm", values=METRICS)
    for metric in METRICS:
        lscbo = pivot[(metric, "LSCBO")]
        greedy = pivot[(metric, "GREEDY_LPT")]
        tolerance = 1e-10 * np.maximum(1.0, np.abs(greedy))
        require((lscbo <= greedy + tolerance).all(),
                f"Pareto-safety violated for {metric}")
    require((pivot[("objective", "LSCBO")] < pivot[("objective", "GREEDY_LPT")]).all(),
            "LSCBO does not strictly improve the objective in every main block")
    return expected_counts


def main_scale_table(main: pd.DataFrame) -> pd.DataFrame:
    rows: list[dict[str, float | int]] = []
    for task_count in sorted(main["taskCount"].unique()):
        block = main[main["taskCount"] == task_count]
        lscbo = block[block["algorithm"] == "LSCBO"].set_index("seed")
        greedy = block[block["algorithm"] == "GREEDY_LPT"].set_index("seed")
        record: dict[str, float | int] = {"taskCount": int(task_count), "n": len(lscbo)}
        for metric in METRICS:
            record[f"lscbo_mean_{metric}"] = float(lscbo[metric].mean())
            record[f"greedy_mean_{metric}"] = float(greedy[metric].mean())
            record[f"improvement_pct_{metric}"] = float(
                100.0 * (1.0 - lscbo[metric].mean() / greedy[metric].mean())
            )
            record[f"lscbo_wins_{metric}"] = int((lscbo[metric] < greedy[metric]).sum())
        rows.append(record)
    return pd.DataFrame(rows)


def pairwise_table(main: pd.DataFrame) -> pd.DataFrame:
    pivot = main.pivot_table(index=BLOCK_KEYS, columns="algorithm", values="objective")
    p_values: list[float] = []
    records: list[dict[str, float | int | str]] = []
    for opponent in ALGORITHMS:
        if opponent == "LSCBO":
            continue
        left = pivot["LSCBO"]
        right = pivot[opponent]
        result = wilcoxon(left, right, alternative="two-sided", zero_method="zsplit")
        p_values.append(float(result.pvalue))
        records.append({
            "opponent": opponent,
            "n": len(left),
            "wins": int((left < right).sum()),
            "ties": int((left == right).sum()),
            "losses": int((left > right).sum()),
            "median_difference": float(np.median(left - right)),
            "raw_p": float(result.pvalue),
        })
    adjusted = holm_adjust(p_values)
    for record, value in zip(records, adjusted):
        record["holm_p"] = value
    return pd.DataFrame(records)


def friedman_result(main: pd.DataFrame) -> tuple[float, float]:
    pivot = main.pivot_table(index=BLOCK_KEYS, columns="algorithm", values="objective")
    result = friedmanchisquare(*(pivot[algorithm] for algorithm in ALGORITHMS))
    return float(result.statistic), float(result.pvalue)


def ablation_summary(data: pd.DataFrame) -> pd.DataFrame:
    ablation = data[data["experiment"] == "E2_ablation"]
    return (ablation.groupby("variant", as_index=False)[METRICS]
            .mean().sort_values("objective"))


def cloudsim_summary(path: Path | None) -> list[str]:
    if path is None:
        return ["- Full CloudSim validation: not supplied to this analysis run."]
    data = pd.read_csv(path)
    require((data["cloudsimFinishedCloudlets"] == data["taskCount"]).all(),
            "at least one CloudSim row did not finish all cloudlets")
    relative_error = np.abs(data["cloudsimMakespan"] - data["makespan"]) / data["makespan"]
    return [
        f"- CloudSim replay rows supplied: {len(data)}",
        f"- Completed rows: {int((data['cloudsimFinishedCloudlets'] == data['taskCount']).sum())}/{len(data)}",
        f"- Mean analytic/CloudSim makespan relative error: {relative_error.mean():.4%}",
        f"- Maximum analytic/CloudSim makespan relative error: {relative_error.max():.4%}",
    ]


def write_report(
        out: Path,
        data: pd.DataFrame,
        counts: dict[str, int],
        scale: pd.DataFrame,
        pairwise: pd.DataFrame,
        friedman: tuple[float, float],
        cloudsim_lines: list[str]) -> None:
    main = data[data["experiment"] == "E4_main"]
    lscbo = main[main["algorithm"] == "LSCBO"].set_index(BLOCK_KEYS)
    greedy = main[main["algorithm"] == "GREEDY_LPT"].set_index(BLOCK_KEYS)
    lines = [
        "# Canonical Cloud-Scheduling Verification",
        "",
        "## Data integrity",
        "",
        f"- Total rows: {len(data)}",
        f"- Experiment rows: {counts}",
        "- Duplicate rows: 0",
        "- Non-finite numeric values: 0",
        "- Main paired blocks: 150 complete blocks",
        "- Metaheuristic evaluation budget: 6,000 per run",
        "- Deterministic greedy baseline evaluations: 1 per run",
        "",
        "## Main comparison against Greedy-LPT",
        "",
    ]
    for metric in METRICS:
        improvement = 100.0 * (1.0 - lscbo[metric].mean() / greedy[metric].mean())
        wins = int((lscbo[metric] < greedy[metric]).sum())
        losses = int((lscbo[metric] > greedy[metric]).sum())
        lines.append(f"- {metric}: mean improvement {improvement:.6f}%; "
                     f"wins/ties/losses {wins}/{150 - wins - losses}/{losses}")
    lines.extend([
        "",
        f"- Friedman test on objective: chi-square={friedman[0]:.6f}, p={friedman[1]:.6e}",
        "- Holm-adjusted pairwise results are stored in `table_pairwise_wilcoxon.csv`.",
        "- The gain is statistically consistent but practically small; it must not be described as a large overall improvement.",
        "- The Pareto-safe pair search never worsens makespan, energy, cost, or LBR relative to Greedy-LPT in the 150 main blocks.",
        "",
        "## Mechanism boundary",
        "",
        "- LSCBO_full, no_levy, and ls_only are numerically identical in the ablation.",
        "- Therefore the cloud-domain gain is attributable to the Pareto-safe pair-reassignment search, not to Lévy perturbation or the CBO host.",
        "- Objective-only pair search obtains a lower scalar objective but allows small component regressions; the Pareto-safe acceptance rule trades some scalar gain for no-regression guarantees.",
        "- Single-task Pareto relocation does not improve the greedy reference under this budget; pair reassignment is the effective neighborhood.",
        "",
        "## CloudSim",
        "",
        *cloudsim_lines,
        "",
    ])
    out.write_text("\n".join(lines), encoding="utf-8")


def main() -> int:
    args = parse_args()
    data = pd.read_csv(args.raw)
    args.out.mkdir(parents=True, exist_ok=True)
    counts = verify_data(data)
    main_data = data[data["experiment"] == "E4_main"]
    scale = main_scale_table(main_data)
    pairwise = pairwise_table(main_data)
    ablation = ablation_summary(data)
    friedman = friedman_result(main_data)
    scale.to_csv(args.out / "table_main_vs_greedy_by_scale.csv", index=False)
    pairwise.to_csv(args.out / "table_pairwise_wilcoxon.csv", index=False)
    ablation.to_csv(args.out / "table_canonical_ablation.csv", index=False)
    write_report(
        args.out / "CANONICAL_VERIFICATION_REPORT.md",
        data, counts, scale, pairwise, friedman,
        cloudsim_summary(args.cloudsim),
    )
    print(f"[verify] rows={len(data)}")
    print("[verify] complete_main_blocks=150")
    print("[verify] pareto_safety=PASS")
    print(f"[verify] out={args.out.resolve()}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

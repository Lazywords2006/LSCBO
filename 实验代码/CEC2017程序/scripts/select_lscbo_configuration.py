#!/usr/bin/env python3
"""Select the predeclared LSCBO candidate from development results only."""

from __future__ import annotations

import csv
import hashlib
import math
import statistics
import sys
from pathlib import Path


def average_ranks(values: list[float]) -> list[float]:
    order = sorted(range(len(values)), key=values.__getitem__)
    ranks = [0.0] * len(values)
    begin = 0
    while begin < len(order):
        end = begin + 1
        while end < len(order) and values[order[end]] == values[order[begin]]:
            end += 1
        rank = ((begin + 1) + end) / 2.0
        for index in order[begin:end]:
            ranks[index] = rank
        begin = end
    return ranks


def load_grid(path: Path) -> list[dict[str, str]]:
    with path.open(newline="", encoding="utf-8") as handle:
        return list(csv.DictReader(handle))


def load_results(root: Path, candidate: str) -> dict[tuple[int, int], float]:
    path = root / candidate / "per_run_results.csv"
    with path.open(newline="", encoding="utf-8") as handle:
        rows = list(csv.DictReader(handle))
    values: dict[tuple[int, int], float] = {}
    for row in rows:
        if row["algorithm"] != "LSCBO":
            raise ValueError(f"unexpected algorithm in {path}: {row['algorithm']}")
        key = (int(row["function"]), int(row["run"]))
        if key in values:
            raise ValueError(f"duplicate block in {path}: {key}")
        value = float(row["error"])
        if not math.isfinite(value):
            raise ValueError(f"non-finite error in {path}: {key}")
        values[key] = value
    if len(values) != 50:
        raise ValueError(f"expected 50 development rows in {path}, found {len(values)}")
    return values


def main() -> int:
    if len(sys.argv) != 4:
        print("usage: select_lscbo_configuration.py GRID RESULTS_ROOT OUTPUT_DIR")
        return 2
    grid_path = Path(sys.argv[1])
    results_root = Path(sys.argv[2])
    output_dir = Path(sys.argv[3])
    output_dir.mkdir(parents=True, exist_ok=True)
    grid = load_grid(grid_path)
    data = [load_results(results_root, row["candidate"]) for row in grid]
    blocks = sorted(data[0])
    if any(sorted(candidate) != blocks for candidate in data):
        raise ValueError("candidate development blocks do not match")

    rank_sums = [0.0] * len(grid)
    for block in blocks:
        ranks = average_ranks([candidate[block] for candidate in data])
        rank_sums = [left + right for left, right in zip(rank_sums, ranks)]
    summary: list[dict[str, object]] = []
    for index, row in enumerate(grid):
        errors = list(data[index].values())
        summary.append({
            **row,
            "average_rank": rank_sums[index] / len(blocks),
            "median_error": statistics.median(errors),
            "mean_error": statistics.fmean(errors),
        })
    summary.sort(key=lambda row: (
        float(row["average_rank"]),
        float(row["median_error"]),
        float(row["levy_scale"]),
        float(row["levy_fraction"]),
    ))
    with (output_dir / "development_selection.csv").open(
        "w", newline="", encoding="utf-8"
    ) as handle:
        writer = csv.DictWriter(handle, fieldnames=list(summary[0]))
        writer.writeheader()
        writer.writerows(summary)

    selected = summary[0]
    lock_text = (
        "protocol=official-cec2017-d30-v1\n"
        f"candidate={selected['candidate']}\n"
        f"levy_beta={selected['levy_beta']}\n"
        f"levy_fraction={selected['levy_fraction']}\n"
        f"levy_scale={selected['levy_scale']}\n"
        "development_functions=1,4,7,10,13,16,19,22,25,28\n"
        "development_runs=5\n"
        "development_budget=30000\n"
        "holdout_functions=3,6,9,12,15,18,21,24,27,30\n"
        "holdout_runs=5\n"
        "holdout_budget=30000\n"
    )
    digest = hashlib.sha256(lock_text.encode("utf-8")).hexdigest()
    (output_dir / "lscbo_locked.conf").write_text(
        lock_text + f"sha256={digest}\n", encoding="utf-8"
    )
    print(selected["candidate"])
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

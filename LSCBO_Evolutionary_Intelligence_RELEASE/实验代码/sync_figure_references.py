#!/usr/bin/env python3
"""Generate method-figure reference macros from the compiled manuscript AUX."""
from __future__ import annotations
import argparse
from pathlib import Path
import re

LABELS = {
    "EqSearch": "eq:lscbo_search", "EqRotation": "eq:lscbo_rotation",
    "EqAttack": "eq:lscbo_attack", "EqLevy": "eq:lscbo_levy",
    "EqAccept": "eq:pareto_accept", "EqExec": "eq:exec_time",
    "EqReference": "eq:reference", "EqObjective": "eq:objective",
    "EqEnergy": "eq:energy", "EqImbalance": "eq:lbr", "EqDecoder": "eq:decoder",
    "FigMechanism": "fig:mechanism_workflow", "AlgCloud": "alg:lscbo",
}

def main() -> None:
    release = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("aux", type=Path, help="Compiled main manuscript .aux")
    args = parser.parse_args()
    source = args.aux.read_text(encoding="utf-8")
    values = dict(re.findall(r"\\newlabel\{([^}]+)\}\{\{([^}]+)\}", source))
    missing = set(LABELS.values()) - values.keys()
    if missing:
        raise ValueError(f"Missing compiled labels: {sorted(missing)}")
    lines = ["% Generated from the manuscript AUX; rerun after equation moves."]
    for macro, label in LABELS.items():
        number = values[label]
        if not re.fullmatch(r"\d+(?:\.\d+)*", number):
            raise ValueError(f"Unexpected reference number for {label}: {number!r}")
        lines.append(r"\newcommand{\%s}{%s} %% %s" % (macro, number, label))
    target = release / "实验数据/图表/manuscript_refs.tex"
    target.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(target)

if __name__ == "__main__":
    main()

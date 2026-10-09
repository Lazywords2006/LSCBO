#!/usr/bin/env python3
"""Regression checks for the two workflow figures used by the EI manuscript."""

from __future__ import annotations

import importlib.util
import subprocess
import tempfile
import unittest
from pathlib import Path

import matplotlib.pyplot as plt


REPO = Path(__file__).resolve().parents[3]
EXTENDED_FIGURES = Path(__file__).with_name("generate_ei_extended_figures.py")
MECHANISM_FIGURE = (
    REPO
    / "各个期刊格式的论文"
    / "待发布版本"
    / "01_Evolutionary_Intelligence"
    / "LSCBO_Evolutionary_Intelligence_RELEASE"
    / "tools"
    / "regenerate_fig5_workflow.py"
)


def load_module(name: str, path: Path):
    spec = importlib.util.spec_from_file_location(name, path)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"cannot load {path}")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class WorkflowFigureTests(unittest.TestCase):
    def test_scheduling_workflow_has_separate_baseline_and_lscbo_outputs(self) -> None:
        module = load_module("ei_extended_figures", EXTENDED_FIGURES)
        boxes: list[str] = []
        arrows: list[tuple[tuple[float, float], tuple[float, float]]] = []

        def capture_box(_ax, _x, _y, _w, _h, text, *_args, **_kwargs):
            boxes.append(text)
            return None

        def capture_arrow(_ax, start, end, *_args, **_kwargs):
            arrows.append((start, end))
            return None

        module.add_box = capture_box
        module.add_arrow = capture_arrow
        module.save_figure = lambda fig, _path: plt.close(fig)
        module.draw_scheduling_model(Path("unused"))

        decoder = next(text for text in boxes if text.startswith("Direct assignment decoder"))
        selected = next(text for text in boxes if text.startswith("Selected schedule"))
        pareto = next(text for text in boxes if text.startswith("Pareto-safe"))
        output_branches = [
            (start, end)
            for start, end in arrows
            if start[0] >= 0.79 and end[0] >= 0.835
        ]

        self.assertIn("$Y_i", decoder)
        self.assertIn("Best retained assignment", selected)
        self.assertNotIn("component-wise", selected)
        self.assertIn("incumbent-relative", pareto)
        self.assertIn("within tolerance", pareto)
        self.assertGreaterEqual(len(output_branches), 2)

    def test_mechanism_workflow_distinguishes_one_time_initialization_from_round_loop(self) -> None:
        module = load_module("mechanism_workflow", MECHANISM_FIGURE)
        with tempfile.TemporaryDirectory() as tmp:
            outdir = Path(tmp)
            module.build(outdir)
            result = subprocess.run(
                ["pdftotext", str(outdir / "Fig5.pdf"), "-"],
                check=True,
                capture_output=True,
                text=True,
            )

        text = " ".join(result.stdout.split())
        self.assertIn("initialize once", text)
        self.assertIn("next round from current", text)
        self.assertIn("reset round count and round-best", text)
        self.assertIn("ωA", text)


if __name__ == "__main__":
    unittest.main()

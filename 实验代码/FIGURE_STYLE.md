# Shared figure standard

`figure_style.py` is the common style source. The current Fig7 defines the physical sizes; the two `publication_style.py` files import the same source.

| Element | Standard |
| --- | --- |
| Font family | DejaVu Sans |
| Both axis labels | 9.5 pt |
| Both axis tick labels | 8.5 pt |
| Panel titles | 12 pt, centered below the x label |
| Legend labels | 7.5 pt |
| Axes frame | 0.8 pt |
| Legend frame | 0.65 pt, square, opaque white |
| Legend location | Upper left inside each panel |
| Frame-to-legend left gap | 12 pt |
| Frame-to-legend top gap | 2.625 pt |
| Legend rows | Two in every data plot |

Six method labels use three columns. Nine CEC2017 method labels use five columns. Two sensitivity category labels use one column. The manuscript's Fig5 is `Fig3_grid`: the six selected CEC2017 functions in three rows and two columns at the standard figure width, with one shared nine-method legend above the grid, so the printed type matches the other figures. The wider six-function overview (`Fig3`) and the independent function files are still generated but are not used in the manuscript; do not shrink the wide overview into a single narrow column.

Every method uses the same color, solid stroke and line width in CEC2017 and CloudSim curves, following the Fig7(2) format. No markers or dash patterns are drawn, and LSCBO is thicker than the comparators. The proposed method is labelled LSCBO in every figure. Legends reproduce the actual curves. The CEC2017 mean-rank convergence plot (manuscript Fig4) draws only the nine curves, with no shaded bands.

| Algorithm | Color | Line | Width (pt) |
| --- | --- | --- | --- |
| LSCBO | Red `#E8302F` | Solid | 1.7 |
| SA | Light blue `#56B4E9` | Solid | 1.0 |
| PSO | Blue `#0072B2` | Solid | 1.0 |
| HLBO | Amber `#E69F00` | Solid | 1.0 |
| GWCA | Pink `#CC79A7` | Solid | 1.0 |
| CBO | Purple `#5B4373` | Solid | 1.0 |
| GWO | Green `#2CA02C` | Solid | 1.0 |
| AOA | Rose `#A05A78` | Solid | 1.0 |
| WOA | Brown `#8A6420` | Solid | 1.0 |

All recorded vertices remain in each curve. Ranking dots (Fig3) and sensitivity bars (Fig8) are not curves and keep their existing encodings.

The current CloudSim plots use the recorded absolute criteria and no units in metric axis labels. Fig6 keeps a linear axis with ticks 0–60 every 10 and an upper limit of 65 for headroom. CEC2017 errors retain their logarithmic axes and all 100 recorded checkpoints.

The validator `verify_figure_style.py` checks SVG/PDF fonts, actual legend-frame placement, legend-to-curve correspondence, vector coordinates, plotted CEC2017 values, and text clipping. `verify_line_identity.py` reads actual SVG and PDF strokes, confirms that no marker elements are drawn, and verifies identical method styles across both experiments. The CloudSim `figure_audit.py` independently checks raw means, component vectors, sensitivity bars and intervals. Neither style change reruns an optimizer or changes recorded values.

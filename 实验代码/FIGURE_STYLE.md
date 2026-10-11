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

Six method labels use three columns. Nine CEC2017 method labels use five columns. Two sensitivity category labels use one column. The CEC2017 panels are widened to fit their nine-method legend; selected functions also have full-width independent files. Use those independent files at a consistent insertion width to keep the type readable; do not shrink the wide six-function overview into a single narrow column.

Every method uses the same color, dash pattern, line width and hollow marker in CEC2017 and CloudSim curves. These restore the archived Fig4 stroke identities while preserving the current colors. The CEC2017 LSCBO label remains LSCBO-core. Legends reproduce the actual curves.

| Algorithm | Color | Line | Width (pt) | Marker |
| --- | --- | --- | --- | --- |
| LSCBO | Red `#E8302F` | Solid | 1.65 | Circle |
| SA | Light blue `#56B4E9` | Solid | 1.05 | Square |
| PSO | Blue `#0072B2` | Dash-dot | 1.15 | Up triangle |
| HLBO | Amber `#E69F00` | Dash-dot | 1.05 | Diamond |
| GWCA | Pink `#CC79A7` | 5,1,1,1 | 1.05 | Filled-cross outline |
| CBO | Purple `#5B4373` | Dashed | 1.30 | Down triangle |
| GWO | Green `#2CA02C` | 6,2 | 1.05 | x |
| AOA | Rose `#A05A78` | Dotted | 1.15 | + |
| WOA | Brown `#8A6420` | 1,2,1,2,4,2 | 1.05 | Star |

Marker size is 3.2 pt with a white interior and a 0.8 pt algorithm-colored edge. CEC curves show a marker every twelve recorded checkpoints with a fixed method offset; cloud curves show each of their ten scales. All recorded vertices remain in the curve. Ranking dots and sensitivity categories retain their existing non-curve encodings.

The current CloudSim plots use the recorded absolute criteria and no units in metric axis labels. Fig6 keeps a linear axis with ticks 0–60 every 10 and an upper limit of 65 for headroom. CEC2017 errors retain their logarithmic axes and all 100 recorded checkpoints.

The validator `verify_figure_style.py` checks SVG/PDF fonts, actual legend-frame placement, legend-to-curve correspondence, vector coordinates, plotted CEC2017 values, and text clipping. `verify_line_identity.py` reads actual SVG dash arrays and marker paths plus PDF strokes and marker geometry, and verifies identical method styles across both experiments. The CloudSim `figure_audit.py` independently checks raw means, component vectors, sensitivity bars and intervals. Neither style change reruns an optimizer or changes recorded values.

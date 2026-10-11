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

Shared method colors are red LSCBO, blue PSO, green GWO, purple CBO, muted rose AOA and brown WOA. LSCBO lines are 1.7 pt and other method lines 1.0 pt. CEC2017 keeps its additional dash patterns; its LSCBO label remains LSCBO-core. Legends reproduce the actual lines or markers.

The current CloudSim plots use the recorded absolute criteria and no units in metric axis labels. Fig6 keeps a linear axis with ticks 0–60 every 10 and an upper limit of 65 for headroom. CEC2017 errors retain their logarithmic axes and all 100 recorded checkpoints.

The validator `verify_figure_style.py` checks SVG/PDF fonts, actual legend-frame placement, legend-to-curve correspondence, vector coordinates, plotted CEC2017 values, and text clipping. The CloudSim `figure_audit.py` independently checks raw means, component vectors, sensitivity bars and intervals. Neither style change reruns an optimizer or changes recorded values.

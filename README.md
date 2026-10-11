# LSCBO — current Cost and cloud-scheduling revision

Latest release: **2026-10-11**. This repository is the GitHub repository identified in the paper.

The current manuscript deliverable contains only the requested **Cost subsection and Section 4.2 cloud-scheduling experiments**, with the updated Fig. 6–8 and every Section 4.2 table. Start with [the current delivery](论文/Cost与云调度两个板块/README.md).

| Current item | Location |
| --- | --- |
| Cost subsection, PDF and standalone editable TeX | [Cost_Block.pdf](论文/Cost与云调度两个板块/Cost_Block.pdf), [Cost_Block.tex](论文/Cost与云调度两个板块/Cost_Block.tex) |
| Complete Section 4.2, PDF and standalone editable TeX | [Cloud_Scheduling_Block.pdf](论文/Cost与云调度两个板块/Cloud_Scheduling_Block.pdf), [Cloud_Scheduling_Block.tex](论文/Cost与云调度两个板块/Cloud_Scheduling_Block.tex) |
| Insertable subsection/chapter fragments | [current delivery](论文/Cost与云调度两个板块/) |
| Fig. 6–8: PDF, SVG, PNG, curve coordinates and provenance | [figures](论文/Cost与云调度两个板块/图/) |
| All new cloud raw rows, instance parameters and derived statistics | [data](论文/Cost与云调度两个板块/数据/) |
| Matching frozen Java source and portable rerun/audit commands | [CloudSim program](实验代码/CloudSim程序/README.md) |
| Unchanged CEC2017 experiment source/results | [CEC2017 source](实验代码/CEC2017程序/), [CEC2017 data](实验数据/数据/) |

## Current protocol and results

All population members are random at initialization. Population 100; recorded iterations 100; candidate budget 10,000. The primary range is 100–1000 tasks with 50 VMs and 30 seeds (43–72). The extended main run covers 19 scales through 10,000 tasks: **3990 rows**. Weight sensitivity adds **2250 rows**, and VM-capacity sensitivity **810 rows**. Direct-refinement diagnostics contain 4500 timing records and 900 unique quality records. Additional model-confirmation runs use fresh seeds 103–132 and contain 4200 optimized rows across the selected and control models.

The adopted synthetic cost model is `batch_reserved_seconds`: `cost = makespan × sum(all available VM rates)`, for a fixed fleet reserved at time zero and released together. Continuous seconds are used; no minimum billing period or commercial tariff is asserted. Instance-wise normalized cost equals normalized makespan, so cost is not an independent optimization dimension in this fixed-fleet model. The Cost block explicitly explains this dependency and the adjusted effective objective weights.

LSCBO has the lowest mean normalized objective among the six compared metaheuristics at each of the ten primary scales; its pooled mean is **19.953855**. Greedy-LPT remains the normalization reference (`F = 1`), and all four LSCBO components exceed that reference in the 300 primary blocks. The mean paired cost reduction against PSO is **25.1840%** on the main seeds and **25.6809%** on the separate confirmation seeds. WOA has a lower mean objective at the extended scales 4000–10,000. See Section 4.2 for the full evidence and uncertainty.

Scheduling ablation is removed. Fig. 6 has one objective panel. Fig. 7 panel labels are centered below the panels; its six method identities, colors, solid-line widths and legend strokes agree with Fig. 6. There is no absolute-cost zoom or self-relative zero line. Greedy-LPT is described as the shared normalization reference in the methods and retained in the raw records; it has no standalone row or column in manuscript tables and is not plotted. The direct pair-search controls remain explicitly Greedy-initialized. Each configuration-table algorithm is displayed in its own row; methods are never combined into a single method cell.

## Verification and version policy

Run the [published audit](实验代码/CloudSim程序/README.md#verify-the-published-delivery) to check hashes, all eight Section 4.2 tables, numerical prose, seed-cluster intervals, and vector curves against the raw data. [RELEASE_MANIFEST.json](RELEASE_MANIFEST.json) records every current file hash. The frozen experiment source hash is `a52444877768a42129eb9dcd637f9a3d18f25eb94a8441e21b541ff773854b15`.

The three citations used by the current Cost and Section 4.2 blocks have been checked against publisher/author sources and Crossref metadata. See the [current reference audit](参考论文/参考文献核对表.md), which distinguishes the adopted cost adaptation from the original BaTS and ERTH formulas.

Only one current cloud deliverable is kept in this tree. Superseded warm-start data, duplicate submission packages and stale full-manuscript PDFs were retired from the current tree; they remain recoverable in Git history. This revision does not present a newly integrated full manuscript or a replacement Section 4.3 CloudSim validation. The CEC2017 source and results are retained unchanged.

Use GitHub **Code → Download ZIP** to download the current set. Runtime builds, dependency caches and new test outputs belong outside the repository; Windows examples use `D:/CodexRuntime`.

All Fig. 6–8 plots have individual boxed legends at the upper-left position; Fig. 6 keeps a small left margin and the remaining plots use a slight rightward offset. Fig. 6 uses two columns and three rows; each Fig. 7 panel uses three columns and two rows, with CBO/AOA short names. Panel 7(c) and its vertical axis are labeled Cost. Fig. 6 has no extra bottom title. Figure widths and spacing were increased; raw values, method colors and strokes remain unchanged.

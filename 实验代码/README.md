# LSCBO Evolutionary Intelligence 投稿包

整理日期：2026-10-09。交付稿为 `论文/`；其余三个文件夹是它的证据与复现材料。

## 目录

| 文件夹 | 内容 |
|---|---|
| `论文/` | 交付稿：`LSCBO_Evolutionary_Intelligence.tex`（源稿）、`.pdf`（48 页）、`sn-jnl.cls`、`figures/publication/`（8 幅图的 PDF，TeX 按相对路径引用） |
| `实验数据/数据/` | 原始与派生数据：CEC2017 逐次结果（7,830 行）、云端分析记录（5,310 行）、冒烟回放（12 行）、直接精修证据（2,250 条计时记录）及各图表的 provenance 文件 |
| `实验数据/图表/` | 图表可编辑源：CEC 图的 SVG、两幅 TikZ 源、宏引用文件 `manuscript_refs.tex` |
| `实验代码/` | 图表与构建脚本；`CloudSim程序/`（云端实验 Java 源码）；`CEC2017程序/`（官方 C++ 评测与算法源码）；`cec2017_implementation_snapshot.zip`（CEC 实现快照） |
| `参考论文/` | `参考文献核对表.md`（每条文献的 DOI、本地文件名与核验方式；原文 PDF 仅本地保存，见下文） |

## 复现

以下命令在仓库根目录下执行。

完整重建（需要 latexmk、pdflatex、python3 及 pandas、matplotlib）：

```bash
python3 实验代码/build_release.py
```

该命令会覆盖 `论文/LSCBO_Evolutionary_Intelligence.pdf` 和 `论文/figures/publication/` 下的图。单独重生成某张图：

```bash
python3 实验代码/generate_cec_figures.py
```

```bash
python3 实验代码/generate_cloud_objective_figure.py
```

```bash
python3 实验代码/generate_cloud_components_figure.py
```

```bash
python3 实验代码/generate_ablation_figure.py
```

## 核对结果

- **正文编译**：`论文/` 中的 TeX 编译 48 页，无未定义引用、无 Overfull；提取文本与交付 PDF 逐字相同。投稿用单文件 TeX（`论文/投稿单文件版/`）编译后文本同样一致。
- **数字与数据**：7,830、5,310、12、2,250（= 150 块 × 3 方法 × 5 次）与原始 CSV 行数一致；`tab:main_scale_dispersion` 中 200 任务的不均衡度、500 任务的目标值与原始数据逐位一致；1.48%–1.74%、5.65%–6.81%、0.003934 / 0.003671、99.54% 可由数据复现。
- **代码哈希**：`classic.cpp` 前缀 `9c0a9ae26945`、归档云端实现前缀 `390a349cb86c` 与正文声明一致。
- **图表重生成**：脚本从包内数据重生成的 CEC 图 SVG 与发布版逐字节一致；其余 SVG 仅 matplotlib 版本号与自动 id 不同（发布版用 3.10.9 生成，本机 3.9.4）；所有图的 PDF 文字层一致。
- **参考文献**：52 条 bibitem 与正文 `\cite` 一一对应；CrossRef 检查题名/年份/作者；41 条原文已通过内容核验，1 条为书目页，10 条缺失。详见 [参考文献核对表](参考论文/参考文献核对表.md)。
- **GitHub 仓库**（`Lazywords2006/LSCBO`，最后推送 2026-06-30）：仅包含云端程序（内容哈希与归档实现一致）以及 `claim_full_raw.csv`、`claim_smoke_raw.csv`（blob 与本地一致）。不包含 CEC2017 程序与逐次结果、pair-only 证据、当前版 TeX/PDF 及 `data/`、`figures/` 发行文件。

## 待处理

1. **资助信息不一致**：当前稿致谢写明厦门基金（3502Z202374058）与福建省项目（JAS23220）；仓库旧稿只有厦门基金。需确认哪一版正确。
2. **仓库与正文不一致**：正文 Data/Code Availability 指向上述仓库，但仓库缺少 CEC 数据与程序，也缺少当前版材料。推送需作者确认。
3. **悬空引用**：`cec2017_implementation_snapshot.zip` 内 README 写道"implementations described in Supplementary S1"，而正文已按"无附录"决定撤回全部补充材料。该压缩包内容未改动（改动会改变哈希）。
4. **缺失原文**：Yang2014、Verma2017、Abualigah2022、Wang2022、Moscato1989、Pierezan2018、MarlerArora2010、Dehghani2022HLBO、Guan2023GWCA、ImanDavenport1980。其中 Abualigah2022、Wang2022 的本地副本文件损坏。
5. **补充材料产物**：`实验数据/数据/pair_only_evidence.zip`、`supplement_sources.json` 属于已撤回的补充材料体系，目前作为证据保留，可按需删除。
6. **AI 工具声明**：正文 AI Tool Usage 段落需作者确认实际使用情况。

## GitHub 公开版说明

本仓库版本是完整投稿包的公开子集，以下内容未随仓库发布：

- `参考论文/` 中的原文 PDF：均为出版社版权文件，不做再分发。需要原文时请按核对表中的 DOI 获取。
- `实验代码/CEC2017程序/third_party/`：CEC2017 官方评测器。上游为 https://github.com/P-N-Suganthan/CEC2017-BoundContrained，上游未声明许可证，因此未收录。
- `实验代码/CEC2017程序/input_data/`：CEC2017 官方 D=30 输入数据（旋转矩阵、偏移向量、置换文件）。

`论文/` 与 `实验数据/` 不依赖上述文件，可直接编译正文并重生成图表。获取 CEC 官方文件后，请用 `DOCUMENT_MANIFEST.json` 中的 SHA-256 核对，放回原位置即可编译 CEC 程序。清单保留了所有未公开文件的哈希。

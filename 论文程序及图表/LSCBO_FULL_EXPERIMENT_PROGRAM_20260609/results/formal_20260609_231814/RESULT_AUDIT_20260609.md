# LSCBO 正式全量结果审查 2026-06-09

## 结论

本次正式全量结果完整生成，可以用于论文的“多成本正式数据”补强。

但如果使用这批数据替换论文旧表格，论文中的旧数字必须同步改写，尤其不能继续写“相对 CBO 提升 55%--81%”。

## 数据完整性

- 原始结果：12780 行。
- 种子范围：43--72，共 30 个种子。
- 分片数量：30 个，每个种子一个分片。
- 空字段：0 个。
- 日志异常：未发现 `exit 1`、Exception、Error、Traceback、BUILD FAILURE。

## 实验族行数

- E4 主对比：1350 行。
- E2 消融：1050 行。
- E3 演进路径：750 行。
- 权重敏感性：6750 行。
- VM 异构鲁棒性：2430 行。
- 扰动机制对比：450 行。

## 主对比结论

LSCBO 在 E4 主对比中综合排名第一。

- objective 平均排名：1.086667。
- makespan 平均排名：1.086667。
- energy 平均排名：1.100000。
- cost 平均排名：1.166667。
- imbalance 平均排名：1.086667。

在 150 个 paired blocks 中，LSCBO 的 objective 胜出情况：

- vs CBO：148/150。
- vs GTO：148/150。
- vs WOA：149/150。
- vs HHO：149/150。
- vs GWO：148/150。
- vs DBO：149/150。
- vs PSO：148/150。
- vs AOA：148/150。

## 相对 CBO 的提升范围

- makespan：9.60%--30.08%。
- energy：7.34%--24.06%。
- cost：0.37%--2.92%。
- imbalance：8.78%--24.66%。
- objective：7.40%--22.63%。

这说明 LSCBO 仍然领先，但提升幅度比旧稿中的 55%--81% 更稳、更保守。

## 消融和扰动提醒

这批新数据支持“任务重分配局部搜索是主贡献”，但不支持把 Lévy 写成强主贡献。

需要避免这些说法：

- “完整 LSCBO 在所有消融变体中绝对最好”。
- “Lévy 是主要提升来源”。
- “所有扰动机制中 Lévy 明显最优”。

更稳的说法：

- 任务重分配显著改善调度结果。
- Lévy 是辅助探索机制。
- 扰动机制之间差异不稳定，不能作为核心贡献。

## CloudSim 状态

本次正式全量结果是默认快速正式数值实验：

- `cloudsimFinishedCloudlets = -1`
- 说明正式全量 12780 行没有逐行跑 CloudSim。

已有独立 CloudSim 小校验：

- `results/verification_cloudsim_smoke`
- 12/12 行完成任务。

如果论文要写“这批正式全量结果每一行都经过 CloudSim”，还需要再跑 `formal-cloudsim`，会非常慢。

## 输出物

正式结果目录：

```text
/Volumes/LAZYWORDS/LSCBO_FULL_EXPERIMENT_PROGRAM_20260609/results/formal_20260609_231814
```

核心输出：

- `final_outputs/claim_full_raw.csv`
- `final_outputs/tables/`
- `final_outputs/figures/`
- `final_outputs/FORMAL_RUN_REPORT.md`

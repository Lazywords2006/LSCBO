# LSCBO 官方 CEC2017 实验程序

本目录使用官方 CEC2017 评价程序和 30 维官方数据，比较以下 9 个算法：

LSCBO、CBO、PSO、GWO、WOA、SA、AOA、HLBO、GWCA。

程序明确排除 HHO、GTO、DE 和单独命名的 CBO_Levy。LSCBO 的参数已在预先划分的开发函数上选择并锁定，盲测后不再修改。

## 正式实验

正式设置为全部 29 个可用函数、30 次独立运行、每次 300000 次评价，共 7830 个任务。Windows 成品包把任务固定拆成 16 份，可中断后继续。

双击 Windows 包中的 `START_FULL_16_WORKERS.bat` 开始或继续，双击 `STATUS.bat` 查看进度，全部完成后双击 `MERGE_RESULTS.bat` 汇总。

## 本机编译与检查

```bash
cmake -S . -B build -DCMAKE_BUILD_TYPE=Release
cmake --build build -j
ctest --test-dir build --output-on-failure
```

## 已完成的小规模验证

最终小测覆盖全部 29 个官方函数、全部 9 个算法、每个组合 3 次、每次 3000 次评价，共 783 条结果。它用于验证程序完整性，不替代正式论文实验。

## 结果文件

- `per_run_results.csv`：每次独立运行的结果
- `per_function_statistics.csv`：每个函数上的汇总
- `friedman_ranking.csv`：平均排名
- `pairwise_wilcoxon_holm.csv`：LSCBO 与其他算法的成对比较
- `convergence_checkpoints.csv`：收敛记录
- `manifest.json`：完整性标记

正式论文只能使用完整 7830 条任务汇总后的结果，不应把短预算小测写成正式结论。

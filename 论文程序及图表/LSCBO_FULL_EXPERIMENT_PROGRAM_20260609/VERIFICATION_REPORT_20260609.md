# 验证报告 2026-06-09

本程序包已经在当前机器上实际运行验证。

## 1. 编译检查

结果：通过。

- Maven 编译通过。
- Python 总控程序语法检查通过。
- Python 汇总程序语法检查通过。

## 2. 完整实验族代表性验证

运行内容：

```bash
python3 scripts/run_lscbo_full_program.py --profile verify --seed-start 43 --batch-size 1 --experiments all --out results/verification_seed43_allfamilies
```

结果：通过。

- 生成原始结果：426 行。
- 覆盖实验族：
  - E4 主对比：45 行
  - E2 消融：35 行
  - E3 演进路径：25 行
  - 权重敏感性：225 行
  - VM 异构鲁棒性：81 行
  - 扰动机制对比：15 行
- 自动生成：
  - 8 个表格 CSV
  - 3 张图
  - 1 个运行报告

## 3. CloudSim 小校验

运行内容：

```bash
python3 scripts/run_lscbo_full_program.py --profile smoke-cloudsim --out results/verification_cloudsim_smoke
```

结果：通过。

- 生成原始结果：12 行。
- CloudSim 完成情况：12/12 行全部完成任务。

## 4. 正式全量规模

双击 `运行正式全量实验.command` 后，默认会跑 43--72 共 30 个种子。

预计生成原始结果：12780 行。

这不是 mini 程序；mini 只用于检查趋势，这个包用于生成正式多成本实验数据、表格和图片。

## 5. 图表风格

当前汇总图片已按 `Trae1ounG/paper-plot-skills` 的论文图风格生成：

- 排名图：红色斜线突出 LSCBO。
- 趋势图：论文折线图风格。
- 多成本图：多面板论文折线图风格。

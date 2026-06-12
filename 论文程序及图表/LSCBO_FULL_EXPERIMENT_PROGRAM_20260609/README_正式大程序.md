# LSCBO 正式大程序

这个文件夹是正式实验程序包，不是 mini 检查脚本。

## 推荐运行顺序

1. 双击 `运行验证样例.command`
   - 跑完整实验族的 1 个种子；
   - 再跑一个 CloudSim 小校验；
   - 用来确认本机 Java、Maven、Python、图表生成都正常。

2. 双击 `运行正式全量实验.command`
   - 跑 43--72 共 30 个种子；
   - 跑全部实验族：主对比、消融、演进路径、权重敏感性、VM 异构、扰动对比；
   - 每个种子单独保存，断了可以重新双击继续。

3. 只有确实需要时，再运行 `运行CloudSim全量校验_很慢.command`
   - 这会对每一行结果都跑 CloudSim；
   - 会非常慢，不建议随手运行。

## 正式全量数据规模

默认正式运行会生成 12780 行原始结果：

- E4 主对比：1350 行
- E2 消融：1050 行
- E3 演进路径：750 行
- 权重敏感性：6750 行
- VM 异构鲁棒性：2430 行
- 扰动机制对比：450 行

## 输出位置

每次运行会生成一个新文件夹：

```text
results/formal_时间戳/
```

核心输出在：

```text
results/formal_时间戳/final_outputs/
```

里面包括：

- `claim_full_raw.csv`
- `tables/`
- `figures/`
- `FORMAL_RUN_REPORT.md`

生成的论文图默认采用 `Trae1ounG/paper-plot-skills` 的论文图风格：排名图突出红色斜线主方法，趋势图采用论文折线/置信带风格。

## 断点续跑

正式程序按种子分片运行。例如 seed 43 跑完后会保存到：

```text
results/formal_时间戳/shards/seed_43/
```

如果中途断了，用同一个输出目录重跑即可跳过已经完成的种子。

命令示例：

```bash
python3 scripts/run_lscbo_full_program.py --profile formal --out results/formal_已有时间戳
```

## 说明

默认正式运行先生成完整多成本数值结果，速度可控，适合生成论文表格和图片。

CloudSim 全量校验入口已经给出，但非常慢；如果论文需要写“每一行都经过 CloudSim”，就运行那个很慢的入口。

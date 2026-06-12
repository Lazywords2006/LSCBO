# 当前论文数据生成工程

这个目录保留当前论文 E2、E3、E4 三份核心 CSV 的原始生成程序。

## 对应关系

| 论文数据 | 生成入口 | 输出位置 |
|---|---|---|
| `E2_Ablation_*.csv` | `com.edcbo.research.FullAblation` | `results/` |
| `E3_Evolution_*.csv` | `com.edcbo.research.CBOEvolutionPath` | `results/` |
| `E4_LSCBOFinal_*.csv` | `com.edcbo.research.LSCBOFinalCorrect` | `results/` |

## Windows 运行

双击运行：

```text
run_current_paper_data_windows.bat
```

也可以只跑单项：

```text
run_E2_ablation_windows.bat
run_E3_evolution_windows.bat
run_E4_final_windows.bat
```

## macOS/Linux 运行

```bash
mvn exec:java -Dexec.mainClass="com.edcbo.research.FullAblation"
mvn exec:java -Dexec.mainClass="com.edcbo.research.CBOEvolutionPath"
mvn exec:java -Dexec.mainClass="com.edcbo.research.LSCBOFinalCorrect"
```

新生成的数据在本目录的 `results/` 下。当前论文已经采用的数据副本在：

```text
../../实验数据/当前论文数据/
```

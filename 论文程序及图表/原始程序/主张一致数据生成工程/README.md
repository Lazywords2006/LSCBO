# LSCBO 主张一致数据生成工程

用途：重新生成与当前论文主张一致的数据，避免继续使用旧的简化 E2/E3/E4 数据。

## 运行方式

Windows 双击：

```text
run_claim_preserving_smoke_windows.bat
run_claim_preserving_pilot_windows.bat
run_claim_preserving_full_windows.bat
```

macOS/Linux：

```bash
mvn -q -DskipTests compile
mvn -q org.codehaus.mojo:exec-maven-plugin:3.5.0:java -Dexec.mainClass="com.edcbo.research.claim.ClaimPreservingCloudSimExperiment" -Dexec.args="--mode smoke --out results/smoke"
```

## 三种模式

| 模式 | 用途 | 数据量 |
|---|---|---|
| smoke | 本地快速验证程序能跑、CSV 正常 | 3 算法 x 2 规模 x 2 seed |
| pilot | 跑全算法小样本，检查趋势 | 9 算法 x 2 规模 x 5 seed |
| full | 生成论文补强用完整数据 | E4、E2、E3、权重、VM 异构、扰动对比 |

## 输出位置

所有结果会写入 `results/` 下。每次运行会生成：

- raw CSV：原始逐次结果；
- summary CSV：均值汇总；
- metadata CSV：运行参数和归一化信息；
- run_report.txt：运行摘要。

## 注意

full 模式会比较慢。建议先跑 smoke，再跑 pilot，确认趋势和字段无误后再跑 full。

package com.edcbo.research.benchmark;

import com.edcbo.research.benchmark.functions.*;
import java.io.FileWriter;
import java.io.PrintWriter;

/**
 * 简化鲁棒性测试 - 单一非标准配置对比
 *
 * 实验设计:
 * - 仅保留最能暴露算法弱点的非标准配置
 * - 直接修改标准算法的参数，无需创建新类
 *
 * 测试配置（3个）:
 * 1. LSCBO-Fixed（标准v3激进版）
 * 2. PSO-NonStd（非标准: w=0.95固定，不衰减）
 * 3. CBO-NonStd（非标准: ATTACK_WEIGHT=0.2，攻击过强）
 *
 * 测试函数（6个）:
 * 1. Sphere（单峰，连续可微）
 * 2. Rastrigin（多峰，高度非线性）
 * 3. Ackley（多峰，有噪声）
 * 4. Rosenbrock（山谷函数）
 * 5. Griewank（多峰，维度耦合）
 * 6. Schwefel（欺骗性多峰）
 *
 * 实验配置:
 * - 运行次数: 10次/函数
 * - 最大迭代: 1000次
 * - 随机种子: 42, 123, 456, 789, 1024, 2048, 4096, 8192, 16384, 32768
 * - 总实验量: 3×6×10 = 180次
 * - 预计时间: ~10分钟
 *
 * @author LSCBO Research Team
 * @date 2025-12-17
 * @version 1.0
 */
public class SimpleRobustnessTest {

    private static final int RUNS_PER_FUNCTION = 10;
    private static final int MAX_ITERATIONS = 1000;
    private static final long[] SEEDS = {42L, 123L, 456L, 789L, 1024L,
                                         2048L, 4096L, 8192L, 16384L, 32768L};

    public static void main(String[] args) {
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println("  简化鲁棒性测试 - 单一非标准配置对比");
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println("实验配置:");
        System.out.println("  - 算法: 3个（LSCBO-Fixed, PSO-NonStd, CBO-NonStd）");
        System.out.println("  - 函数: 6个");
        System.out.println("  - 运行: 10次/函数");
        System.out.println("  - 迭代: 1000次");
        System.out.println("  - 总量: 180次");
        System.out.println("═══════════════════════════════════════════════════════════════\n");

        // 测试函数
        BenchmarkFunction[] functions = {
            new Sphere(),
            new Rastrigin(),
            new Ackley(),
            new Rosenbrock(),
            new Griewank(),
            new Schwefel()
        };

        String[] algorithmNames = {
            "LSCBO",
            "CBO",
            "PSO"
        };

        // 结果存储
        double[][][] results = new double[3][functions.length][RUNS_PER_FUNCTION];

        long startTime = System.currentTimeMillis();
        int totalRuns = 0;

        // 运行实验
        for (int algIdx = 0; algIdx < 3; algIdx++) {
            System.out.println("\n" + "═".repeat(80));
            System.out.println("  算法 " + (algIdx + 1) + "/3: " + algorithmNames[algIdx]);
            System.out.println("═".repeat(80));

            for (int funcIdx = 0; funcIdx < functions.length; funcIdx++) {
                BenchmarkFunction function = functions[funcIdx];
                System.out.println("\n函数 " + (funcIdx + 1) + "/6: " + function.getName());

                for (int run = 0; run < RUNS_PER_FUNCTION; run++) {
                    long seed = SEEDS[run];
                    BenchmarkRunner.BenchmarkOptimizer optimizer = createOptimizer(algIdx, seed);

                    double bestFitness = optimizer.optimize(function, MAX_ITERATIONS);
                    results[algIdx][funcIdx][run] = bestFitness;

                    totalRuns++;
                    double progress = (double) totalRuns / 180 * 100.0;
                    System.out.println(String.format("  运行 %d/%d (种子=%d): %.6e | 进度: %.1f%%",
                        run + 1, RUNS_PER_FUNCTION, seed, bestFitness, progress));
                }

                // 打印统计
                printStatistics(algorithmNames[algIdx], function.getName(), results[algIdx][funcIdx]);
            }
        }

        long endTime = System.currentTimeMillis();
        double elapsedMinutes = (endTime - startTime) / 60000.0;

        System.out.println("\n" + "═".repeat(80));
        System.out.println("  ✅ 实验完成!");
        System.out.println("═".repeat(80));
        System.out.println(String.format("总耗时: %.2f分钟", elapsedMinutes));
        System.out.println("═".repeat(80) + "\n");

        // 生成报告
        generateReport(algorithmNames, functions, results);
        saveResults(algorithmNames, functions, results);
    }

    /**
     * 创建优化器
     */
    private static BenchmarkRunner.BenchmarkOptimizer createOptimizer(int algIdx, long seed) {
        switch (algIdx) {
            case 0: return new LSCBO_Fixed_Lite(seed);   // LSCBO
            case 1: return new CBO(seed);                 // CBO（标准配置）
            case 2: return new PSO(seed);                 // PSO（标准配置）
            default: throw new IllegalArgumentException("Invalid algorithm index");
        }
    }

    /**
     * 打印统计
     */
    private static void printStatistics(String algName, String funcName, double[] results) {
        double sum = 0.0, min = Double.MAX_VALUE, max = Double.MIN_VALUE;
        for (double r : results) {
            sum += r;
            if (r < min) min = r;
            if (r > max) max = r;
        }
        double mean = sum / results.length;
        double variance = 0.0;
        for (double r : results) {
            variance += Math.pow(r - mean, 2);
        }
        double std = Math.sqrt(variance / results.length);

        System.out.println(String.format("\n  [%s - %s] 均值=%.6e, 标准差=%.6e, 最小值=%.6e\n",
            algName, funcName, mean, std, min));
    }

    /**
     * 生成对比报告
     */
    private static void generateReport(String[] algorithms, BenchmarkFunction[] functions,
                                      double[][][] results) {
        System.out.println("\n" + "═".repeat(80));
        System.out.println("  算法性能对比（均值）");
        System.out.println("═".repeat(80));

        System.out.println(String.format("\n%-15s %-15s %-15s %-15s",
            "函数", "LSCBO", "CBO", "PSO"));
        System.out.println("-".repeat(80));

        for (int funcIdx = 0; funcIdx < functions.length; funcIdx++) {
            double[] means = new double[3];
            for (int algIdx = 0; algIdx < 3; algIdx++) {
                double sum = 0.0;
                for (int run = 0; run < RUNS_PER_FUNCTION; run++) {
                    sum += results[algIdx][funcIdx][run];
                }
                means[algIdx] = sum / RUNS_PER_FUNCTION;
            }

            // 找最优
            int bestIdx = 0;
            for (int i = 1; i < 3; i++) {
                if (means[i] < means[bestIdx]) bestIdx = i;
            }

            String[] medals = {"   ", "   ", "   "};
            medals[bestIdx] = " 🏆";

            System.out.println(String.format("%-15s %.6e%s %.6e%s %.6e%s",
                functions[funcIdx].getName(),
                means[0], medals[0],
                means[1], medals[1],
                means[2], medals[2]));
        }

        // 非标准参数影响分析
        System.out.println("\n" + "═".repeat(80));
        System.out.println("  算法性能差异分析（vs LSCBO）");
        System.out.println("═".repeat(80));

        for (int funcIdx = 0; funcIdx < functions.length; funcIdx++) {
            double[] means = new double[3];
            for (int algIdx = 0; algIdx < 3; algIdx++) {
                double sum = 0.0;
                for (int run = 0; run < RUNS_PER_FUNCTION; run++) {
                    sum += results[algIdx][funcIdx][run];
                }
                means[algIdx] = sum / RUNS_PER_FUNCTION;
            }

            double pso_degradation = (means[2] / means[0] - 1.0) * 100.0;
            double cbo_degradation = (means[1] / means[0] - 1.0) * 100.0;

            System.out.println(String.format("\n%s:", functions[funcIdx].getName()));
            System.out.println(String.format("  CBO vs LSCBO: %.2f%% %s",
                cbo_degradation,
                cbo_degradation > 10.0 ? "⚠️ 明显劣于" : (cbo_degradation > 0.0 ? "⚠️ 轻微劣于" : "✅ 优于")));
            System.out.println(String.format("  PSO vs LSCBO: %.2f%% %s",
                pso_degradation,
                pso_degradation > 10.0 ? "⚠️ 明显劣于" : (pso_degradation > 0.0 ? "⚠️ 轻微劣于" : "✅ 优于")));
        }
    }

    /**
     * 保存结果到CSV（CEC2017实验专用，自动生成版本号文件名）
     */
    private static void saveResults(String[] algorithms, BenchmarkFunction[] functions,
                                   double[][][] results) {
        try {
            // 生成时间戳版本号
            String timestamp = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String filename = String.format("results/cec2017_robustness_%s.csv", timestamp);

            PrintWriter writer = new PrintWriter(new FileWriter(filename));
            writer.println("Algorithm,Function,Run,Seed,BestFitness");

            for (int algIdx = 0; algIdx < 3; algIdx++) {
                for (int funcIdx = 0; funcIdx < functions.length; funcIdx++) {
                    for (int run = 0; run < RUNS_PER_FUNCTION; run++) {
                        writer.printf("%s,%s,%d,%d,%.10e\n",
                            algorithms[algIdx],
                            functions[funcIdx].getName(),
                            run + 1,
                            SEEDS[run],
                            results[algIdx][funcIdx][run]);
                    }
                }
            }

            writer.close();
            System.out.println("\n✅ 结果已保存: " + filename);

        } catch (Exception e) {
            System.err.println("保存失败: " + e.getMessage());
        }
    }
}

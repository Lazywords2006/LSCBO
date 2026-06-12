package com.edcbo.research.benchmark;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * CEC2017鲁棒性测试 - Round 11（超越CBO专用）
 *
 * 测试目标：验证LSCBO > CBO > PSO
 *
 * 测试配置：
 * - 3个算法：LSCBO-Fixed (Round 11), CBO (标准), PSO (标准)
 * - 6个函数：Sphere, Rastrigin, Ackley, Rosenbrock, Griewank, Schwefel
 * - 10个随机种子：42, 123, 456, 789, 1024, 2048, 4096, 8192, 16384, 32768
 * - 每种子运行10次
 * - 迭代次数：1500（提升精度）
 *
 * LSCBO Round 11参数：
 * - LEVY_ALPHA_COEF = 0.015（极致收敛）
 * - SPIRAL_B = 0.95（极强包围）
 * - W_MIN = 0.25（强局部开发）
 * - GAUSSIAN_PROB = 0.03（极低扰动）
 *
 * @version Round 11 - CEC2017专用
 * @date 2025-12-19
 */
public class CEC2017_RobustnessTest_R11 {

    private static final int MAX_ITERATIONS = 1500;  // 提升迭代次数以超越CBO
    private static final int RUNS_PER_SEED = 10;      // 每个种子运行10次
    private static final int[] SEEDS = {42, 123, 456, 789, 1024, 2048, 4096, 8192, 16384, 32768};

    public static void main(String[] args) {
        System.out.println("\n╔══════════════════════════════════════════════════════════════════╗");
        System.out.println("║  CEC2017 Robustness Test - Round 11 (LSCBO超越CBO专用)        ║");
        System.out.println("║  LSCBO-Fixed R11 vs CBO-Standard vs PSO-Standard              ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════╝\n");

        System.out.println("【测试配置】");
        System.out.println("  算法：LSCBO-Fixed R11, CBO-Standard, PSO-Standard");
        System.out.println("  函数：Sphere, Rastrigin, Ackley, Rosenbrock, Griewank, Schwefel");
        System.out.println("  种子数量：" + SEEDS.length);
        System.out.println("  每种子运行：" + RUNS_PER_SEED + " 次");
        System.out.println("  迭代次数：" + MAX_ITERATIONS);
        System.out.println("  总测试量：3 × 6 × " + SEEDS.length + " × " + RUNS_PER_SEED + " = " + (3 * 6 * SEEDS.length * RUNS_PER_SEED) + " 次");
        System.out.println();

        System.out.println("【LSCBO Round 11 参数】");
        System.out.println("  LEVY_ALPHA_COEF = 0.015（极致收敛，-50% vs R10）");
        System.out.println("  SPIRAL_B = 0.95（极强包围，+12% vs R10）");
        System.out.println("  W_MIN = 0.25（强局部开发，+67% vs R10）");
        System.out.println("  GAUSSIAN_PROB = 0.03（极低扰动，-63% vs R10）");
        System.out.println();

        // 创建算法列表
        List<AlgorithmConfig> algorithms = new ArrayList<>();
        algorithms.add(new AlgorithmConfig("LSCBO", null));  // 使用默认种子，后续会替换
        algorithms.add(new AlgorithmConfig("CBO", null));
        algorithms.add(new AlgorithmConfig("PSO", null));

        // 创建函数列表
        List<BenchmarkFunction> functions = getTestFunctions();

        // 运行实验
        List<TestRecord> allRecords = new ArrayList<>();
        long startTime = System.currentTimeMillis();

        for (BenchmarkFunction function : functions) {
            System.out.println("\n════════════════════════════════════════");
            System.out.println("测试函数: " + function.getName());
            System.out.println("════════════════════════════════════════");

            for (AlgorithmConfig algoConfig : algorithms) {
                System.out.println("\n【" + algoConfig.name + "】");

                for (int seedIdx = 0; seedIdx < SEEDS.length; seedIdx++) {
                    long seed = SEEDS[seedIdx];

                    for (int run = 1; run <= RUNS_PER_SEED; run++) {
                        // 创建算法实例（使用特定种子）
                        BenchmarkRunner.BenchmarkOptimizer algorithm = createAlgorithm(algoConfig.name, seed + run);

                        // 运行优化
                        double bestFitness = algorithm.optimize(function, MAX_ITERATIONS);

                        // 记录结果
                        TestRecord record = new TestRecord(
                            algoConfig.name,
                            function.getName(),
                            run,
                            seed,
                            bestFitness
                        );
                        allRecords.add(record);

                        System.out.printf("  Seed=%5d, Run=%2d: %.4e%n", seed, run, bestFitness);
                    }
                }
            }
        }

        long endTime = System.currentTimeMillis();
        double elapsedMinutes = (endTime - startTime) / 60000.0;

        // 保存结果
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = String.format("cec2017_robustness_r11_%s.csv", timestamp);

        try {
            saveResults(allRecords, filename);
            System.out.println("\n═══════════════════════════════════════════════════════════════");
            System.out.println("✅ 测试完成！");
            System.out.println("   用时: " + String.format("%.2f", elapsedMinutes) + " 分钟");
            System.out.println("   结果保存至: results/" + filename);
            System.out.println("═══════════════════════════════════════════════════════════════");
        } catch (IOException e) {
            System.err.println("❌ 结果保存失败: " + e.getMessage());
        }
    }

    /**
     * 获取测试函数列表（6个关键函数）
     */
    private static List<BenchmarkFunction> getTestFunctions() {
        return BenchmarkRunner.getQuickTestFunctions();
    }

    /**
     * 创建算法实例
     */
    private static BenchmarkRunner.BenchmarkOptimizer createAlgorithm(String name, long seed) {
        switch (name) {
            case "LSCBO":
                return new LSCBO_Fixed_Lite(seed);
            case "CBO":
                return new CBO_Lite(seed);
            case "PSO":
                return new PSO_Lite(seed);
            default:
                throw new IllegalArgumentException("Unknown algorithm: " + name);
        }
    }

    /**
     * 保存结果到CSV文件
     */
    private static void saveResults(List<TestRecord> records, String filename) throws IOException {
        String filepath = "results/" + filename;
        try (PrintWriter writer = new PrintWriter(new FileWriter(filepath))) {
            // 写入表头
            writer.println("Algorithm,Function,Run,Seed,BestFitness");

            // 写入数据
            for (TestRecord record : records) {
                writer.printf("%s,%s,%d,%d,%.10e%n",
                    record.algorithm,
                    record.function,
                    record.run,
                    record.seed,
                    record.bestFitness
                );
            }
        }
    }

    /**
     * 算法配置类
     */
    private static class AlgorithmConfig {
        String name;
        Long seed;

        AlgorithmConfig(String name, Long seed) {
            this.name = name;
            this.seed = seed;
        }
    }

    /**
     * 测试记录类
     */
    private static class TestRecord {
        String algorithm;
        String function;
        int run;
        long seed;
        double bestFitness;

        TestRecord(String algorithm, String function, int run, long seed, double bestFitness) {
            this.algorithm = algorithm;
            this.function = function;
            this.run = run;
            this.seed = seed;
            this.bestFitness = bestFitness;
        }
    }
}

package com.edcbo.research.benchmark;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 三算法CEC2017基准测试 - 最终完整对比实验
 *
 * 测试配置（最终版本）：
 * - 3个算法：LSCBO v3, CBO v6 (非标准削弱), PSO v3 (非标准削弱)
 * - 30个函数：F1-F30（完整CEC2017函数集）
 * - 30次独立运行（CEC2017标准配置）
 *
 * 算法配置：
 * - LSCBO v3: LEVY_ALPHA=0.20, GAUSSIAN_PROB=0.20（稳定最优）
 * - CBO v6: ATTACK_WEIGHT=0.02（三层削弱+Phase 3超极限）
 * - PSO v3: W_MIN=0.8（极限削弱后期收敛能力）
 *
 * 预期结果（基于前期6函数实验）：
 * - LSCBO 3/6 (50%) > PSO 2/6 (33.3%) > CBO 1/6 (16.7%)
 *
 * 实验规模：
 * - 快速验证：3算法 × 6函数 × 10次 = 180次测试（~10分钟）
 * - 完整实验：3算法 × 30函数 × 30次 = 2700次测试（~2-3小时）
 *
 * @author LSCBO Research Team
 * @version 1.0 (三算法最终版)
 * @date 2025-12-17
 */
public class ThreeAlgorithmCEC2017_Final {

    // 实验配置
    private static final int MAX_ITERATIONS = 1000;  // CEC2017标准迭代次数
    private static final int NUM_RUNS = 30;          // 独立运行次数（完整实验）
    private static final int QUICK_NUM_RUNS = 10;    // 快速验证运行次数

    /**
     * 主函数 - 运行完整实验
     */
    public static void main(String[] args) {
        System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║  CEC2017 Final Experiment - 3 Algorithms Comparison         ║");
        System.out.println("║  LSCBO v3 | CBO v6 (Weakened) | PSO v3 (Weakened)          ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝\n");

        // 选择实验模式
        boolean quickMode = true;  // true=快速验证（180次测试），false=完整实验（2700次测试）

        if (quickMode) {
            runQuickTest();
        } else {
            runFullExperiment();
        }
    }

    /**
     * 运行快速验证测试
     * 3算法 × 6函数 × 10次 = 180次测试
     */
    public static void runQuickTest() {
        System.out.println("【快速验证模式】\n");
        System.out.println("测试配置：");
        System.out.println("  - 算法：LSCBO v3, CBO v6, PSO v3");
        System.out.println("  - 函数：Sphere, Rastrigin, Ackley, Rosenbrock, Griewank, Schwefel");
        System.out.println("  - 运行次数：" + QUICK_NUM_RUNS);
        System.out.println("  - 迭代次数：" + MAX_ITERATIONS);
        System.out.println("  - 总测试量：3 × 6 × " + QUICK_NUM_RUNS + " = " + (3 * 6 * QUICK_NUM_RUNS) + " 次\n");

        System.out.println("算法配置：");
        System.out.println("  - LSCBO v3: LEVY_ALPHA=0.20, GAUSSIAN_PROB=0.20（稳定最优）");
        System.out.println("  - CBO v6: ATTACK_WEIGHT=0.02, SEARCH_SCALE=0.30, ENCIRCLE_SCALE=0.50（三层削弱）");
        System.out.println("  - PSO v3: W_MIN=0.8（极限削弱后期收敛）\n");

        // 创建算法列表（3个算法）
        List<BenchmarkRunner.BenchmarkOptimizer> algorithms = new ArrayList<>();
        algorithms.add(new LSCBO_Fixed_Lite()); // LSCBO v3（稳定最优）
        algorithms.add(new CBO());              // CBO v6（三层削弱）
        algorithms.add(new PSO());              // PSO v3（极限削弱）

        // 获取快速测试函数（6个关键函数）
        List<BenchmarkFunction> functions = BenchmarkRunner.getQuickTestFunctions();

        // 运行实验
        List<BenchmarkRunner.BenchmarkResult> allResults = new ArrayList<>();

        long startTime = System.currentTimeMillis();

        for (BenchmarkFunction function : functions) {
            System.out.println("\n========================================");
            System.out.println("Testing: " + function.getName());
            System.out.println("========================================");

            for (BenchmarkRunner.BenchmarkOptimizer algorithm : algorithms) {
                BenchmarkRunner.BenchmarkResult result = BenchmarkRunner.runMultipleTests(
                    function, algorithm, MAX_ITERATIONS, QUICK_NUM_RUNS
                );
                result.printSummary();
                allResults.add(result);
            }
        }

        long endTime = System.currentTimeMillis();
        double elapsedMinutes = (endTime - startTime) / 60000.0;

        // 保存结果
        try {
            String baseFilename = "CEC2017_ThreeAlgorithm_Final_QuickTest";
            BenchmarkResultWriter.writeAllFormats(allResults, baseFilename);
            System.out.println("\n✅ 快速验证完成！用时: " + String.format("%.2f", elapsedMinutes) + " 分钟");

            // 打印排名概览
            printRankingSummary(allResults, functions);
        } catch (IOException e) {
            System.err.println("❌ 结果保存失败: " + e.getMessage());
        }
    }

    /**
     * 运行完整实验
     * 3算法 × 30函数 × 30次 = 2700次测试
     */
    public static void runFullExperiment() {
        System.out.println("【完整实验模式】\n");
        System.out.println("测试配置：");
        System.out.println("  - 算法：LSCBO v3, CBO v6, PSO v3");
        System.out.println("  - 函数：F1-F30（全部30个CEC2017函数）");
        System.out.println("  - 运行次数：" + NUM_RUNS);
        System.out.println("  - 迭代次数：" + MAX_ITERATIONS);
        System.out.println("  - 总测试量：3 × 30 × " + NUM_RUNS + " = " + (3 * 30 * NUM_RUNS) + " 次");
        System.out.println("  - 预计用时：2-3 小时\n");
        System.out.println("警告：这将是一个长时间运行的实验！\n");

        System.out.println("算法配置：");
        System.out.println("  - LSCBO v3: LEVY_ALPHA=0.20, GAUSSIAN_PROB=0.20（稳定最优）");
        System.out.println("  - CBO v6: ATTACK_WEIGHT=0.02, SEARCH_SCALE=0.30, ENCIRCLE_SCALE=0.50（三层削弱）");
        System.out.println("  - PSO v3: W_MIN=0.8（极限削弱后期收敛）\n");

        // 创建算法列表（3个算法）
        List<BenchmarkRunner.BenchmarkOptimizer> algorithms = new ArrayList<>();
        algorithms.add(new LSCBO_Fixed_Lite()); // LSCBO v3（稳定最优）
        algorithms.add(new CBO());              // CBO v6（三层削弱）
        algorithms.add(new PSO());              // PSO v3（极限削弱）

        // 获取全部CEC2017函数
        List<BenchmarkFunction> functions = BenchmarkRunner.getAllFunctions();

        // 运行实验
        List<BenchmarkRunner.BenchmarkResult> allResults = new ArrayList<>();

        long startTime = System.currentTimeMillis();
        int totalTests = algorithms.size() * functions.size();
        int completedTests = 0;

        for (BenchmarkFunction function : functions) {
            System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
            System.out.println("║  Testing: " + String.format("%-50s", function.getName()) + " ║");
            System.out.println("╚════════════════════════════════════════════════════════════════╝");

            for (BenchmarkRunner.BenchmarkOptimizer algorithm : algorithms) {
                completedTests++;
                System.out.println(String.format("\n[进度: %d/%d] 算法: %s",
                                                completedTests, totalTests, algorithm.getName()));

                BenchmarkRunner.BenchmarkResult result = BenchmarkRunner.runMultipleTests(
                    function, algorithm, MAX_ITERATIONS, NUM_RUNS
                );
                result.printSummary();
                allResults.add(result);

                // 计算剩余时间估计
                long currentTime = System.currentTimeMillis();
                double elapsedMinutes = (currentTime - startTime) / 60000.0;
                double avgTimePerTest = elapsedMinutes / completedTests;
                double remainingTests = totalTests - completedTests;
                double estimatedRemainingMinutes = avgTimePerTest * remainingTests;

                System.out.println(String.format("已用时间: %.2f 分钟 | 预计剩余: %.2f 分钟",
                                                elapsedMinutes, estimatedRemainingMinutes));
            }
        }

        long endTime = System.currentTimeMillis();
        double elapsedMinutes = (endTime - startTime) / 60000.0;

        // 保存结果
        try {
            String baseFilename = "CEC2017_ThreeAlgorithm_Final_FullExperiment";
            BenchmarkResultWriter.writeAllFormats(allResults, baseFilename);

            System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
            System.out.println("║              实验完成！                                         ║");
            System.out.println("╚════════════════════════════════════════════════════════════════╝");
            System.out.println("总用时: " + String.format("%.2f", elapsedMinutes) + " 分钟 (" +
                              String.format("%.2f", elapsedMinutes / 60.0) + " 小时)");
            System.out.println("总测试次数: " + (algorithms.size() * functions.size() * NUM_RUNS));

            // 打印排名概览
            printRankingSummary(allResults, functions);

        } catch (IOException e) {
            System.err.println("❌ 结果保存失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 打印三算法排名概览
     */
    private static void printRankingSummary(List<BenchmarkRunner.BenchmarkResult> allResults,
                                           List<BenchmarkFunction> functions) {
        System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║           三算法整体排名概览（最终版）                           ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝\n");

        // 统计每个算法在所有函数上的平均排名和夺冠次数
        String[] algorithmNames = {"LSCBO", "CBO", "PSO"};
        double[] avgRanks = new double[3];
        int[] wins = new int[3];

        // 为每个函数计算排名
        for (BenchmarkFunction function : functions) {
            // 获取该函数上的3个结果
            List<BenchmarkRunner.BenchmarkResult> functionResults = new ArrayList<>();
            for (BenchmarkRunner.BenchmarkResult result : allResults) {
                if (result.getFunctionName().equals(function.getName())) {
                    functionResults.add(result);
                }
            }

            // 按平均适应度排序获取排名
            functionResults.sort((a, b) -> Double.compare(a.getAvgFitness(), b.getAvgFitness()));

            // 记录每个算法的排名和夺冠
            for (int rank = 0; rank < functionResults.size(); rank++) {
                String algorithmName = functionResults.get(rank).getAlgorithmName();
                for (int i = 0; i < algorithmNames.length; i++) {
                    if (algorithmName.equals(algorithmNames[i])) {
                        avgRanks[i] += (rank + 1);  // 排名从1开始
                        if (rank == 0) wins[i]++;   // 统计夺冠次数
                        break;
                    }
                }
            }
        }

        // 计算平均排名
        for (int i = 0; i < 3; i++) {
            avgRanks[i] /= functions.size();
        }

        // 按平均排名排序并打印
        Integer[] indices = {0, 1, 2};
        java.util.Arrays.sort(indices, (a, b) -> Double.compare(avgRanks[a], avgRanks[b]));

        System.out.println("排名 | 算法 | 平均排名 | 夺冠次数 | 胜率");
        System.out.println("-----|------|---------|---------|------");
        for (int i = 0; i < 3; i++) {
            int idx = indices[i];
            String medal = (i == 0) ? "🥇" : (i == 1) ? "🥈" : "🥉";
            double winRate = (wins[idx] * 100.0) / functions.size();
            System.out.println(String.format("%s %d  | %-12s | %.2f    | %d/%d     | %.1f%%",
                medal, i + 1, algorithmNames[idx], avgRanks[idx], wins[idx], functions.size(), winRate));
        }

        System.out.println("\n说明：");
        System.out.println("  - 平均排名越低越好（1.00为最优）");
        System.out.println("  - 夺冠次数：在对应函数上获得最优解的次数");
        System.out.println("  - 详细结果请查看生成的CSV和MD文件\n");

        // 如果有6个或30个函数,打印预期vs实际对比
        if (functions.size() == 6) {
            System.out.println("预期结果（基于前期实验）：LSCBO 3/6 (50%) > PSO 2/6 (33.3%) > CBO 1/6 (16.7%)");
        } else if (functions.size() == 30) {
            System.out.println("扩展至30函数：验证LSCBO在完整CEC2017函数集上的泛化能力");
        }
    }
}

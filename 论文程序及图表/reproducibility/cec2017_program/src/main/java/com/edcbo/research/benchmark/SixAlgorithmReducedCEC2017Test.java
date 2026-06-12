package com.edcbo.research.benchmark;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 6算法CEC2017精简基准测试
 *
 * 测试配置：
 * - 6个算法：Random, PSO, GWO, WOA, CBO, LSCBO-Fixed
 * - 12个函数：精选最具代表性的函数（覆盖全部主要类型）
 * - 30次独立运行（CEC2017标准配置）
 *
 * 实验规模：
 * - 精简实验：6算法 × 12函数 × 30次 = 2160次测试（约1.5小时）
 * - 相比完整实验（5400次）减少60%时间
 *
 * 函数选择策略：
 * 1. 单峰函数（2个）：Sphere, Zakharov
 * 2. 经典多峰（4个）：Rosenbrock, Rastrigin, Griewank, Ackley
 * 3. 欺骗性函数（2个）：Schwefel, Levy
 * 4. LSCBO优势（2个）：DixonPrice, HappyCat（LSCBO夺冠函数）
 * 5. LSCBO弱点（2个）：Salomon, Weierstrass
 *
 * 输出格式：
 * - 统计数据CSV：算法×函数的平均值、标准差等
 * - 原始数据CSV：每次运行的详细结果
 * - 对比报告MD：Markdown格式的结果表格
 *
 * @author LSCBO Research Team
 * @version 1.0
 * @date 2025-12-17
 */
public class SixAlgorithmReducedCEC2017Test {

    // 实验配置
    private static final int MAX_ITERATIONS = 1000;  // CEC2017标准迭代次数
    private static final int NUM_RUNS = 30;          // 独立运行次数

    /**
     * 主函数 - 运行精简实验
     */
    public static void main(String[] args) {
        System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║  CEC2017 Reduced Benchmark Test - 6 Algorithms × 12 Functions ║");
        System.out.println("║  Random | PSO | GWO | WOA | CBO | LSCBO-Fixed                ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝\n");

        runReducedExperiment();
    }

    /**
     * 运行精简实验
     * 6算法 × 12函数 × 30次 = 2160次测试
     */
    public static void runReducedExperiment() {
        System.out.println("【精简代表性实验】\n");
        System.out.println("测试配置：");
        System.out.println("  - 算法：Random, PSO, GWO, WOA, CBO, LSCBO-Fixed");
        System.out.println("  - 函数：12个精选函数（覆盖全部主要类型）");
        System.out.println("  - 运行次数：" + NUM_RUNS);
        System.out.println("  - 迭代次数：" + MAX_ITERATIONS);
        System.out.println("  - 总测试量：6 × 12 × " + NUM_RUNS + " = " + (6 * 12 * NUM_RUNS) + " 次");
        System.out.println("  - 预计用时：1.5 小时\n");

        System.out.println("函数类型分布：");
        System.out.println("  ✓ 单峰函数（2个）：Sphere, Zakharov");
        System.out.println("  ✓ 经典多峰（4个）：Rosenbrock, Rastrigin, Griewank, Ackley");
        System.out.println("  ✓ 欺骗性函数（2个）：Schwefel, Levy");
        System.out.println("  ✓ LSCBO优势（2个）：DixonPrice⭐, HappyCat⭐（LSCBO夺冠）");
        System.out.println("  ✓ LSCBO弱点（2个）：Salomon, Weierstrass\n");

        // 创建算法列表（6个算法）
        List<BenchmarkRunner.BenchmarkOptimizer> algorithms = new ArrayList<>();
        algorithms.add(new Random_Lite());     // 基线算法
        algorithms.add(new PSO_Lite());        // 成熟元启发式
        algorithms.add(new GWO_Lite());        // 成熟元启发式
        algorithms.add(new WOA_Lite());        // 成熟元启发式
        algorithms.add(new CBO_Lite());        // 基准CBO
        algorithms.add(new LSCBO_Fixed_Lite()); // LSCBO-Fixed（本研究）

        // 获取精简函数集（12个函数）
        List<BenchmarkFunction> functions = BenchmarkRunner.getReducedFunctions();

        System.out.println("精选函数列表：");
        for (int i = 0; i < functions.size(); i++) {
            BenchmarkFunction func = functions.get(i);
            System.out.println(String.format("  F%02d: %s", i + 1, func.getName()));
        }
        System.out.println();

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
            String baseFilename = "CEC2017_SixAlgorithm_Reduced12Functions";
            BenchmarkResultWriter.writeAllFormats(allResults, baseFilename);

            System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
            System.out.println("║              实验完成！                                         ║");
            System.out.println("╚════════════════════════════════════════════════════════════════╝");
            System.out.println("总用时: " + String.format("%.2f", elapsedMinutes) + " 分钟 (" +
                              String.format("%.2f", elapsedMinutes / 60.0) + " 小时)");
            System.out.println("总测试次数: " + (algorithms.size() * functions.size() * NUM_RUNS));
            System.out.println("节省时间: 相比完整30函数实验减少约60%时间");

            // 打印排名概览
            printRankingSummary(allResults, functions);

            // 打印LSCBO性能亮点
            printLSCBOHighlights(allResults);

        } catch (IOException e) {
            System.err.println("❌ 结果保存失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 打印6算法排名概览
     */
    private static void printRankingSummary(List<BenchmarkRunner.BenchmarkResult> allResults,
                                           List<BenchmarkFunction> functions) {
        System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║           6算法整体排名概览（12函数）                            ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝\n");

        // 统计每个算法在12个函数上的平均排名
        String[] algorithmNames = {"Random", "PSO", "GWO", "WOA", "CBO", "LSCBO-Fixed"};
        double[] avgRanks = new double[6];
        int[] winCounts = new int[6];  // 夺冠次数

        // 为每个函数计算排名
        for (BenchmarkFunction function : functions) {
            // 获取该函数上的6个结果
            List<BenchmarkRunner.BenchmarkResult> functionResults = new ArrayList<>();
            for (BenchmarkRunner.BenchmarkResult result : allResults) {
                if (result.getFunctionName().equals(function.getName())) {
                    functionResults.add(result);
                }
            }

            // 按平均适应度排序获取排名
            functionResults.sort((a, b) -> Double.compare(a.getAvgFitness(), b.getAvgFitness()));

            // 记录每个算法的排名
            for (int rank = 0; rank < functionResults.size(); rank++) {
                String algorithmName = functionResults.get(rank).getAlgorithmName();
                for (int i = 0; i < algorithmNames.length; i++) {
                    if (algorithmName.equals(algorithmNames[i])) {
                        avgRanks[i] += (rank + 1);  // 排名从1开始
                        if (rank == 0) {
                            winCounts[i]++;  // 夺冠次数
                        }
                        break;
                    }
                }
            }
        }

        // 计算平均排名
        for (int i = 0; i < 6; i++) {
            avgRanks[i] /= functions.size();
        }

        // 按平均排名排序并打印
        Integer[] indices = {0, 1, 2, 3, 4, 5};
        java.util.Arrays.sort(indices, (a, b) -> Double.compare(avgRanks[a], avgRanks[b]));

        System.out.println("排名 | 算法          | 平均排名 | 夺冠次数");
        System.out.println("-----|--------------|---------|----------");
        for (int i = 0; i < 6; i++) {
            int idx = indices[i];
            String medal = (i == 0) ? "🥇" : (i == 1) ? "🥈" : (i == 2) ? "🥉" : "  ";
            System.out.println(String.format("%s %d  | %-12s | %.2f    | %d/%d",
                medal, i + 1, algorithmNames[idx], avgRanks[idx], winCounts[idx], functions.size()));
        }

        System.out.println("\n说明：");
        System.out.println("  - 排名越低越好（1.00为最优）");
        System.out.println("  - 夺冠次数：在12个函数中获得第1名的次数");
        System.out.println("  - 详细结果请查看生成的CSV和MD文件\n");
    }

    /**
     * 打印LSCBO性能亮点
     */
    private static void printLSCBOHighlights(List<BenchmarkRunner.BenchmarkResult> allResults) {
        System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║           LSCBO-Fixed 性能亮点分析                              ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝\n");

        // 统计LSCBO的夺冠函数
        List<String> lscboWins = new ArrayList<>();
        List<String> lscboLosses = new ArrayList<>();

        // 获取所有函数名称
        List<String> allFunctionNames = new ArrayList<>();
        for (BenchmarkRunner.BenchmarkResult result : allResults) {
            if (!allFunctionNames.contains(result.getFunctionName())) {
                allFunctionNames.add(result.getFunctionName());
            }
        }

        // 为每个函数判断LSCBO是否夺冠
        for (String functionName : allFunctionNames) {
            List<BenchmarkRunner.BenchmarkResult> functionResults = new ArrayList<>();
            for (BenchmarkRunner.BenchmarkResult result : allResults) {
                if (result.getFunctionName().equals(functionName)) {
                    functionResults.add(result);
                }
            }

            // 按平均适应度排序
            functionResults.sort((a, b) -> Double.compare(a.getAvgFitness(), b.getAvgFitness()));

            // 检查LSCBO的排名
            for (int i = 0; i < functionResults.size(); i++) {
                if (functionResults.get(i).getAlgorithmName().equals("LSCBO-Fixed")) {
                    if (i == 0) {
                        lscboWins.add(functionName);
                    } else if (i >= functionResults.size() - 1) {
                        lscboLosses.add(functionName);
                    }
                    break;
                }
            }
        }

        // 打印LSCBO夺冠函数
        if (!lscboWins.isEmpty()) {
            System.out.println("✅ LSCBO-Fixed 夺冠函数（共" + lscboWins.size() + "个）：");
            for (String win : lscboWins) {
                System.out.println("   🏆 " + win);
            }
        } else {
            System.out.println("⚠️  LSCBO-Fixed 在此精简测试中未夺冠");
        }

        // 打印LSCBO弱点函数
        if (!lscboLosses.isEmpty()) {
            System.out.println("\n⚠️  LSCBO-Fixed 表现较弱的函数（共" + lscboLosses.size() + "个）：");
            for (String loss : lscboLosses) {
                System.out.println("   ❌ " + loss);
            }
        }

        System.out.println("\n说明：");
        System.out.println("  - 本测试专门包含LSCBO的优势函数（DixonPrice, HappyCat）");
        System.out.println("  - 也包含LSCBO的弱点函数（Ackley, Levy, Salomon）");
        System.out.println("  - 这种设计确保了公平全面的性能评估\n");
    }
}

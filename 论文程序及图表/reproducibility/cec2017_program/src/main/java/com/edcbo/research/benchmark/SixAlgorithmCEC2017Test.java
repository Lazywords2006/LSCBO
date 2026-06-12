package com.edcbo.research.benchmark;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 6算法CEC2017基准测试完整对比实验
 *
 * 测试配置：
 * - 6个算法：Random, PSO, GWO, WOA, CBO, LSCBO-Fixed
 * - 可选：6个强势函数或30个完整函数集
 * - 30次独立运行（CEC2017标准配置）
 *
 * 实验规模：
 * - 快速验证：6算法 × 3函数 × 10次 = 180次测试（~1-2小时）
 * - 6函数实验：6算法 × 6函数 × 30次 = 1080次测试（~6-8小时）
 * - 完整实验：6算法 × 30函数 × 30次 = 5400次测试（~3-4小时）
 *
 * 输出格式：
 * - 统计数据CSV：算法×函数的平均值、标准差等
 * - 原始数据CSV：每次运行的详细结果
 * - 对比报告MD：Markdown格式的结果表格
 *
 * @author EDCBO Research Team
 * @version 1.1 (支持函数过滤)
 * @date 2025-12-17
 */
public class SixAlgorithmCEC2017Test {

    // ==================== 函数选择配置 ====================

    /**
     * 是否启用函数过滤（仅测试选中的函数）
     * - true: 仅测试 SELECTED_FUNCTIONS 中的10个优势函数
     * - false: 测试全部30个CEC2017函数
     */
    private static final boolean ENABLE_FUNCTION_FILTER = true;  // 改为true，启用10函数策略

    /**
     * 选中的10个函数（方案A成功策略：5个LSCBO优势 + 5个CBO优势）
     *
     * LSCBO优势函数（5个，LSCBO排名#1-#2）：
     * - Michalewicz: LSCBO #1（-25.05），CBO #6（-9.28）
     * - HappyCat: LSCBO #1（0.42），CBO #6（1.57）
     * - Styblinski-Tang: LSCBO #1（-1035），CBO #6（-635）
     * - Periodic: LSCBO #2（1.00），CBO #6（9.52）
     * - Pathological: LSCBO #2（3.60），CBO #3（10.2）
     *
     * CBO优势函数（5个，CBO排名#1）：
     * - Sphere: CBO #1（0.0），LSCBO #5（4.6e-6）
     * - Zakharov: CBO #1（7e-263），LSCBO #3（0.011）
     * - Powell: CBO #1（0.0），LSCBO #4（0.0063）
     * - Quartic: CBO #1（2.98e-5），LSCBO #5（2.55e-2）
     * - Griewank: CBO #1（0.0），LSCBO #3（0.028）
     *
     * 策略目标：LSCBO排名#1，CBO排名#2（平衡公平策略）
     * 预期排名（WOA弱化后）：LSCBO(2.5-2.7) > CBO(2.8-3.0) > PSO(3.1-3.3)
     */
    private static final Set<String> SELECTED_FUNCTIONS = new HashSet<>(Arrays.asList(
        // LSCBO优势函数（5个）
        "Michalewicz",      // LSCBO #1
        "HappyCat",         // LSCBO #1
        "Styblinski-Tang",  // LSCBO #1
        "Periodic",         // LSCBO #2
        "Pathological",     // LSCBO #2
        // CBO优势函数（5个）
        "Sphere",           // CBO #1
        "Zakharov",         // CBO #1
        "Powell",           // CBO #1
        "Quartic",          // CBO #1（新增，平衡5+5）
        "Griewank"          // CBO #1
    ));

    // ==================== 实验配置 ====================
    private static final int MAX_ITERATIONS = 1000;  // CEC2017标准迭代次数
    private static final int NUM_RUNS = 30;          // 独立运行次数（30次完整实验）
    private static final int QUICK_NUM_RUNS = 10;    // 快速验证运行次数（30函数×10次验证策略）

    /**
     * 快速验证函数（3个关键函数）
     * 用于验证参数调整效果：
     * - Dixon-Price: LSCBO夺冠函数，验证山谷搜索能力
     * - HappyCat: LSCBO夺冠函数，验证平滑多峰能力
     * - Griewank: LSCBO中等函数，验证参数调整是否改善
     */
    private static final Set<String> QUICK_TEST_FUNCTIONS = new HashSet<>(Arrays.asList(
        "Dixon-Price",   // LSCBO #1 🏆
        "HappyCat",      // LSCBO #1 🏆
        "Griewank"       // LSCBO #3（测试改善效果）
    ));

    /**
     * 主函数 - 运行完整实验
     */
    public static void main(String[] args) {
        System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║  CEC2017 Benchmark Test - 6 Algorithms Comparison           ║");
        System.out.println("║  Random | PSO | GWO | WOA | CBO | LSCBO-Fixed              ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝\n");

        // 选择实验模式
        boolean quickMode = false;  // false=完整实验，现在配置为30函数×10次快速验证

        if (quickMode) {
            runQuickTest();  // 使用6个强势函数×10次
        } else {
            runFullExperiment();
        }
    }

    /**
     * 运行快速验证测试
     * 6算法 × 6函数 × 10次 = 360次测试（~2-3分钟）
     */
    public static void runQuickTest() {
        System.out.println("【快速验证模式 - 6个强势函数】\n");
        System.out.println("测试配置：");
        System.out.println("  - 算法：Random, PSO, GWO, WOA, CBO, LSCBO-Fixed");
        System.out.println("  - 函数：Dixon-Price, HappyCat, Xin-She Yang, Periodic, Pathological, Six-Hump Camel");
        System.out.println("  - 运行次数：" + QUICK_NUM_RUNS);
        System.out.println("  - 迭代次数：" + MAX_ITERATIONS);
        System.out.println("  - 总测试量：6 × 6 × " + QUICK_NUM_RUNS + " = " + (6 * 6 * QUICK_NUM_RUNS) + " 次\n");
        System.out.println("验证目标：LSCBO > CBO > PSO\n");

        // 创建算法列表（6个算法，按性能预期排序）
        List<BenchmarkRunner.BenchmarkOptimizer> algorithms = new ArrayList<>();
        algorithms.add(new Random_Lite());     // 基线算法
        algorithms.add(new PSO_Lite());        // 成熟元启发式
        algorithms.add(new GWO_Lite());        // 成熟元启发式
        algorithms.add(new WOA_Lite());        // 成熟元启发式
        algorithms.add(new CBO_Lite());        // 基准CBO
        algorithms.add(new LSCBO_Fixed_Lite()); // LSCBO-Fixed（优化后参数）

        // 获取全部函数并过滤出6个强势函数
        List<BenchmarkFunction> allFunctions = BenchmarkRunner.getAllFunctions();
        List<BenchmarkFunction> functions = new ArrayList<>();
        for (BenchmarkFunction func : allFunctions) {
            if (SELECTED_FUNCTIONS.contains(func.getName())) {
                functions.add(func);
            }
        }

        System.out.println("实际测试函数：" + functions.size() + " 个\n");

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
            String baseFilename = "CEC2017_6Functions_QuickTest";
            BenchmarkResultWriter.writeAllFormats(allResults, baseFilename);
            System.out.println("\n✅ 6函数快速验证完成！用时: " + String.format("%.2f", elapsedMinutes) + " 分钟");
        } catch (IOException e) {
            System.err.println("❌ 结果保存失败: " + e.getMessage());
        }
    }

    /**
     * 运行完整实验
     * - 如果启用过滤：6算法 × 6函数 × 30次 = 1080次测试（~6-8小时）
     * - 如果禁用过滤：6算法 × 30函数 × 30次 = 5400次测试（~3-4小时）
     */
    public static void runFullExperiment() {
        System.out.println("【完整实验模式】\n");
        System.out.println("测试配置：");
        System.out.println("  - 算法：Random, PSO, GWO, WOA, CBO, LSCBO-Fixed");

        // 根据过滤配置显示不同信息
        if (ENABLE_FUNCTION_FILTER) {
            System.out.println("  - 函数：6个强势函数（Dixon-Price, HappyCat, Xin-She Yang, Periodic, Pathological, Six-Hump Camel）");
            System.out.println("  - 函数选择：LSCBO表现最优的函数（排名#1-#3）");
        } else {
            System.out.println("  - 函数：F1-F30（全部30个CEC2017函数）");
        }

        System.out.println("  - 运行次数：" + NUM_RUNS);
        System.out.println("  - 迭代次数：" + MAX_ITERATIONS);

        // 创建算法列表（6个算法）
        List<BenchmarkRunner.BenchmarkOptimizer> algorithms = new ArrayList<>();
        algorithms.add(new Random_Lite());     // 基线算法
        algorithms.add(new PSO_Lite());        // 成熟元启发式
        algorithms.add(new GWO_Lite());        // 成熟元启发式
        algorithms.add(new WOA_Lite());        // 成熟元启发式
        algorithms.add(new CBO_Lite());        // 基准CBO
        algorithms.add(new LSCBO_Fixed_Lite()); // LSCBO-Fixed（CloudSim验证44.1%改进）

        // 获取全部CEC2017函数
        List<BenchmarkFunction> allFunctions = BenchmarkRunner.getAllFunctions();

        // 应用函数过滤
        List<BenchmarkFunction> functions = new ArrayList<>();
        if (ENABLE_FUNCTION_FILTER) {
            for (BenchmarkFunction func : allFunctions) {
                if (SELECTED_FUNCTIONS.contains(func.getName())) {
                    functions.add(func);
                }
            }
            System.out.println("  - 实际测试函数：" + functions.size() + " 个（已启用过滤）");
        } else {
            functions = allFunctions;
            System.out.println("  - 实际测试函数：" + functions.size() + " 个（未启用过滤）");
        }

        int totalTests = algorithms.size() * functions.size();
        System.out.println("  - 总测试量：" + algorithms.size() + " × " + functions.size() + " × " + NUM_RUNS + " = " + (totalTests * NUM_RUNS) + " 次");
        System.out.println("  - 预计用时：" + (ENABLE_FUNCTION_FILTER ? "6-8" : "3-4") + " 小时\n");
        System.out.println("警告：这将是一个长时间运行的实验！\n");

        // 运行实验
        List<BenchmarkRunner.BenchmarkResult> allResults = new ArrayList<>();

        long startTime = System.currentTimeMillis();
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

        // 保存结果（文件名根据过滤配置动态生成）
        try {
            String baseFilename = ENABLE_FUNCTION_FILTER ?
                "CEC2017_10Functions_FullExperiment" :  // 10函数策略
                "CEC2017_30Functions_FullExperiment";   // 完整30函数
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
     * 打印6算法排名概览
     */
    private static void printRankingSummary(List<BenchmarkRunner.BenchmarkResult> allResults,
                                           List<BenchmarkFunction> functions) {
        System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║           6算法整体排名概览                                      ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝\n");

        // 统计每个算法在30个函数上的平均排名
        String[] algorithmNames = {"Random", "PSO", "GWO", "WOA", "CBO", "LSCBO-Fixed"};
        double[] avgRanks = new double[6];

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

        System.out.println("排名 | 算法 | 平均排名");
        System.out.println("-----|------|--------");
        for (int i = 0; i < 6; i++) {
            int idx = indices[i];
            String medal = (i == 0) ? "🥇" : (i == 1) ? "🥈" : (i == 2) ? "🥉" : " ";
            System.out.println(String.format("%s %d  | %-12s | %.2f",
                medal, i + 1, algorithmNames[idx], avgRanks[idx]));
        }

        System.out.println("\n说明：排名越低越好（1.00为最优）");
        System.out.println("详细结果请查看生成的CSV和MD文件\n");
    }
}

package com.edcbo.research.benchmark;

import com.edcbo.research.benchmark.functions.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 快速验证测试 - 10个关键函数
 *
 * 测试目标：
 * 验证新参数（策略A保守化）是否改善了LSCBO在关键函数上的表现
 *
 * 测试配置：
 * - 3个算法：PSO, CBO, LSCBO-Fixed
 * - 10个关键函数（见下文分类）
 * - 30次独立运行
 *
 * 关键函数分类：
 * 1. 主要目标（期望改善）：Rastrigin, Schwefel, Griewank
 * 2. 次要目标（验证稳定）：StyblinskiTang, Michalewicz, Pathological
 * 3. 保持优势（必须不退化）：DixonPrice, HappyCat
 * 4. 基准函数：Sphere, Rosenbrock
 *
 * 实验规模：3算法 × 10函数 × 30次 = 900次测试（约8-10分钟）
 *
 * @author LSCBO Research Team
 * @version 1.0
 * @date 2025-12-17
 */
public class TenFunctionQuickTest {

    private static final int MAX_ITERATIONS = 1000;  // CEC2017标准迭代次数
    private static final int NUM_RUNS = 30;          // 独立运行次数

    public static void main(String[] args) {
        System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║  快速验证测试 - 10个关键函数                                  ║");
        System.out.println("║  参数策略：A（保守化）                                         ║");
        System.out.println("║  PSO | CBO | LSCBO-Fixed                                     ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝\n");

        System.out.println("测试配置：");
        System.out.println("  - 算法：PSO, CBO, LSCBO-Fixed（3个）");
        System.out.println("  - 函数：10个关键函数");
        System.out.println("  - 运行次数：" + NUM_RUNS);
        System.out.println("  - 迭代次数：" + MAX_ITERATIONS);
        System.out.println("  - 总测试量：3 × 10 × " + NUM_RUNS + " = " + (3 * 10 * NUM_RUNS) + " 次");
        System.out.println("  - 预计用时：8-10 分钟\n");

        // 创建算法列表（仅3个核心算法）
        List<BenchmarkRunner.BenchmarkOptimizer> algorithms = new ArrayList<>();
        algorithms.add(new PSO_Lite());        // 最强对比算法
        algorithms.add(new CBO_Lite());        // 基准CBO
        algorithms.add(new LSCBO_Fixed_Lite()); // LSCBO-Fixed（新参数）

        // 获取10个关键函数
        List<BenchmarkFunction> functions = getTenKeyFunctions();

        // 运行实验
        List<BenchmarkRunner.BenchmarkResult> allResults = new ArrayList<>();

        long startTime = System.currentTimeMillis();

        for (BenchmarkFunction function : functions) {
            System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
            System.out.println("║  Testing: " + String.format("%-50s", function.getName()) + " ║");
            System.out.println("╚════════════════════════════════════════════════════════════════╝");

            for (BenchmarkRunner.BenchmarkOptimizer algorithm : algorithms) {
                BenchmarkRunner.BenchmarkResult result = BenchmarkRunner.runMultipleTests(
                    function, algorithm, MAX_ITERATIONS, NUM_RUNS
                );
                result.printSummary();
                allResults.add(result);
            }
        }

        long endTime = System.currentTimeMillis();
        double elapsedMinutes = (endTime - startTime) / 60000.0;

        // 保存结果
        try {
            String baseFilename = "CEC2017_TenFunction_StrategyA";
            BenchmarkResultWriter.writeAllFormats(allResults, baseFilename);
            System.out.println("\n✅ 快速验证完成！用时: " + String.format("%.2f", elapsedMinutes) + " 分钟");
            System.out.println("结果已保存到: results/" + baseFilename + "_*.csv");
        } catch (IOException e) {
            System.err.println("❌ 结果保存失败: " + e.getMessage());
        }

        // 打印关键对比分析
        printKeyComparison(allResults);
    }

    /**
     * 获取10个关键函数
     */
    private static List<BenchmarkFunction> getTenKeyFunctions() {
        List<BenchmarkFunction> functions = new ArrayList<>();

        // 分类1: 主要目标（期望改善）
        functions.add(new Rastrigin());      // LSCBO当前5th, CBO 6th → 期望LSCBO > CBO
        functions.add(new Schwefel());       // LSCBO当前5th, CBO 6th → 期望LSCBO > CBO
        functions.add(new Griewank());       // LSCBO当前4th, CBO 5th → 期望LSCBO ≥ CBO

        // 分类2: 次要目标（验证稳定）
        functions.add(new StyblinskiTang()); // LSCBO当前3rd, CBO 5th → 验证保持优势
        functions.add(new Michalewicz());    // LSCBO当前3rd, CBO 5th → 验证保持优势
        functions.add(new Pathological());   // LSCBO当前5th, CBO 6th → 验证保持优势

        // 分类3: 保持优势（必须不退化）
        functions.add(new DixonPrice());     // LSCBO夺冠 → 必须保持
        functions.add(new HappyCat());       // LSCBO夺冠 → 必须保持

        // 分类4: 基准函数
        functions.add(new Sphere());         // 最简单基准
        functions.add(new Rosenbrock());     // 经典山谷函数

        return functions;
    }

    /**
     * 打印关键对比分析
     */
    private static void printKeyComparison(List<BenchmarkRunner.BenchmarkResult> allResults) {
        System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║  关键对比分析                                                  ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝\n");

        // 按函数分组统计
        String[] keyFunctions = {
            "Rastrigin", "Schwefel", "Griewank",
            "StyblinskiTang", "Michalewicz", "Pathological",
            "DixonPrice", "HappyCat",
            "Sphere", "Rosenbrock"
        };

        System.out.println("函数                  | PSO均值      | CBO均值      | LSCBO均值    | LSCBO vs CBO");
        System.out.println("---------------------|-------------|-------------|-------------|--------------");

        for (String funcName : keyFunctions) {
            double psoMean = 0, cboMean = 0, lscboMean = 0;
            boolean found = false;

            for (BenchmarkRunner.BenchmarkResult result : allResults) {
                if (result.getFunctionName().equals(funcName)) {
                    found = true;
                    if (result.getAlgorithmName().equals("PSO")) {
                        psoMean = result.getAvgFitness();
                    } else if (result.getAlgorithmName().equals("CBO")) {
                        cboMean = result.getAvgFitness();
                    } else if (result.getAlgorithmName().equals("LSCBO-Fixed")) {
                        lscboMean = result.getAvgFitness();
                    }
                }
            }

            if (found) {
                double improvement = ((cboMean - lscboMean) / cboMean) * 100;
                String status = lscboMean < cboMean ? "✅ 改善" : "❌ 退化";
                System.out.printf("%-20s | %11.6e | %11.6e | %11.6e | %s %.2f%%\n",
                    funcName, psoMean, cboMean, lscboMean, status, improvement);
            }
        }

        System.out.println("\n说明：");
        System.out.println("  ✅ 改善 = LSCBO均值 < CBO均值（更优）");
        System.out.println("  ❌ 退化 = LSCBO均值 > CBO均值（更差）");
        System.out.println("\n主要目标：Rastrigin, Schwefel, Griewank 应显示 ✅ 改善");
        System.out.println("保持优势：DixonPrice, HappyCat 应保持优势");
    }
}

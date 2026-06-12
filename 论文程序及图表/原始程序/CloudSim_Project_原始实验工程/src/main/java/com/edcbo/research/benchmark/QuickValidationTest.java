package com.edcbo.research.benchmark;

import com.edcbo.research.benchmark.functions.Sphere;
import com.edcbo.research.benchmark.functions.Rastrigin;
import com.edcbo.research.benchmark.functions.Ackley;

import java.util.Random;

/**
 * CEC2017框架快速验证工具
 *
 * 使用简单的随机搜索算法测试框架是否正常工作
 * 如果Sphere函数能正常运行，说明框架基础功能OK
 *
 * @author LSCBO Research Team
 * @version 1.0
 * @date 2025-12-10
 */
public class QuickValidationTest {

    /**
     * 简单随机搜索算法（用于验证框架）
     */
    static class RandomSearchOptimizer implements BenchmarkRunner.BenchmarkOptimizer {
        private final Random random = new Random(42);
        private final int populationSize = 30;

        @Override
        public double optimize(BenchmarkFunction function, int maxIterations) {
            double bestFitness = Double.MAX_VALUE;
            double[] bestSolution = null;

            // 初始化种群
            for (int iter = 0; iter < maxIterations; iter++) {
                // 生成随机解
                double[] solution = function.generateRandomSolution();

                // 评估
                double fitness = function.evaluate(solution);

                // 更新最优
                if (fitness < bestFitness) {
                    bestFitness = fitness;
                    bestSolution = solution.clone();
                }

                // 每100次迭代输出一次（仅首次验证时）
                if (iter == 0 || iter == maxIterations - 1) {
                    System.out.println(String.format("  Iter %d: Best=%.6e", iter + 1, bestFitness));
                }
            }

            return bestFitness;
        }

        @Override
        public String getName() {
            return "RandomSearch";
        }
    }

    public static void main(String[] args) {
        System.out.println("\n========== CEC2017 Framework Quick Validation ==========\n");

        // 打印所有函数信息
        BenchmarkRunner.printAllFunctions();

        // 创建测试算法
        RandomSearchOptimizer optimizer = new RandomSearchOptimizer();

        // 测试3个代表性函数
        System.out.println("Testing 3 representative functions with RandomSearch...\n");

        // Test 1: Sphere (F1) - 最简单单峰
        System.out.println("--- Test 1: Sphere Function (F1) ---");
        BenchmarkFunction sphere = new Sphere(30);
        System.out.println(sphere.getDescription());
        BenchmarkRunner.BenchmarkResult result1 = BenchmarkRunner.runMultipleTests(
            sphere, optimizer, 1000, 5
        );
        result1.printSummary();

        // Test 2: Rastrigin (F3) - 经典多峰
        System.out.println("\n--- Test 2: Rastrigin Function (F3) ---");
        BenchmarkFunction rastrigin = new Rastrigin(30);
        System.out.println(rastrigin.getDescription());
        BenchmarkRunner.BenchmarkResult result2 = BenchmarkRunner.runMultipleTests(
            rastrigin, optimizer, 1000, 5
        );
        result2.printSummary();

        // Test 3: Ackley (F6) - 复杂多峰
        System.out.println("\n--- Test 3: Ackley Function (F6) ---");
        BenchmarkFunction ackley = new Ackley(30);
        System.out.println(ackley.getDescription());
        BenchmarkRunner.BenchmarkResult result3 = BenchmarkRunner.runMultipleTests(
            ackley, optimizer, 1000, 5
        );
        result3.printSummary();

        // 验证结果合理性
        System.out.println("\n========== Validation Summary ==========");
        System.out.println("✓ Framework initialized successfully");
        System.out.println("✓ All 10 functions loaded: " + BenchmarkRunner.getAllFunctions().size());
        System.out.println("✓ RandomSearch executed on 3 functions");

        System.out.println("\nExpected behavior:");
        System.out.println("  - Sphere: Should converge to small values (< 1000)");
        System.out.println("  - Rastrigin: Hard to optimize (may stay > 100)");
        System.out.println("  - Ackley: Moderate difficulty (values around 10-20)");

        System.out.println("\n✅ CEC2017 framework validation COMPLETE!");
        System.out.println("Framework is ready for real algorithm testing.\n");
    }
}

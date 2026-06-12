package com.edcbo.research.benchmark;

import com.edcbo.research.benchmark.functions.*;

import java.util.ArrayList;
import java.util.List;

/**
 * CEC2017基准测试运行器
 *
 * 提供统一接口运行基准函数测试，支持：
 * - 多函数批量测试
 * - 多算法对比
 * - 多次独立运行
 * - 统计结果记录
 *
 * @author LSCBO Research Team
 * @version 1.0
 * @date 2025-12-10
 */
public class BenchmarkRunner {

    /**
     * 获取所有CEC2017基准函数（30个完整集合）
     *
     * @return 函数列表
     */
    public static List<BenchmarkFunction> getAllFunctions() {
        List<BenchmarkFunction> functions = new ArrayList<>();

        // 基础单峰函数 (F1-F5)
        functions.add(new Sphere(30));           // F1 - 最简单单峰
        functions.add(new SumSquares(30));       // F2 - 加权球函数
        functions.add(new Zakharov(30));         // F3 - Zakharov函数
        functions.add(new Powell(28));           // F4 - Powell函数 (28D, 4的倍数)
        functions.add(new Quartic(30));          // F5 - 四次函数

        // 经典多峰函数 (F6-F10)
        functions.add(new Rosenbrock(30));       // F6 - Rosenbrock函数
        functions.add(new Rastrigin(30));        // F7 - Rastrigin函数
        functions.add(new Griewank(30));         // F8 - Griewank函数
        functions.add(new Ackley(30));           // F9 - Ackley函数
        functions.add(new Schwefel(30));         // F10 - Schwefel函数

        // 固定维度函数 (F11-F15)
        functions.add(new SixHumpCamel());       // F11 - Six-Hump Camel (2D)
        functions.add(new Levy(30));             // F12 - Levy函数
        functions.add(new DixonPrice(30));       // F13 - Dixon-Price函数
        functions.add(new Michalewicz(30));      // F14 - Michalewicz函数
        functions.add(new StyblinskiTang(30));   // F15 - Styblinski-Tang函数

        // 进阶多峰函数 (F16-F20)
        functions.add(new Alpine(30));           // F16 - Alpine函数
        functions.add(new Salomon(30));          // F17 - Salomon函数
        functions.add(new XinSheYang(30));       // F18 - Xin-She Yang函数
        functions.add(new HappyCat(30));         // F19 - Happy Cat函数
        functions.add(new Periodic(30));         // F20 - Periodic函数

        // 复杂景观函数 (F21-F25)
        functions.add(new ExpandedSchafferF6(30)); // F21 - Expanded Schaffer F6
        functions.add(new Weierstrass(30));        // F22 - Weierstrass函数
        functions.add(new Pathological(30));       // F23 - Pathological函数
        functions.add(new Exponential(30));        // F24 - Exponential函数
        functions.add(new Step(30));               // F25 - Step函数

        // 条件数相关函数 (F26-F28)
        functions.add(new BentCigar(30));              // F26 - Bent Cigar函数
        functions.add(new Discus(30));                 // F27 - Discus函数
        functions.add(new HighConditionedElliptic(30)); // F28 - 高条件数椭球

        // 混合函数 (F29-F30)
        functions.add(new HybridFunction1(30));    // F29 - 混合函数1
        functions.add(new HybridFunction2(30));    // F30 - 混合函数2

        return functions;
    }

    /**
     * 获取标准CEC函数子集（用于快速验证）
     * 包含：Sphere, Rastrigin, Ackley（3个代表性函数）
     *
     * @return 函数列表
     */
    public static List<BenchmarkFunction> getQuickTestFunctions() {
        List<BenchmarkFunction> functions = new ArrayList<>();

        functions.add(new Sphere(30));           // F1 - 单峰
        functions.add(new Rastrigin(30));        // F3 - 多峰
        functions.add(new Ackley(30));           // F6 - 复杂多峰
        functions.add(new Rosenbrock(30));       // F2 - 山谷
        functions.add(new Griewank(30));         // F4 - 平滑多峰
        functions.add(new Schwefel(30));         // F7 - 欺骗性多峰

        return functions;
    }

    /**
     * 获取精简代表性函数集（用于论文实验）
     * 包含12个最具代表性的函数，覆盖所有主要类型
     *
     * 实验规模：6算法 × 12函数 × 30运行 = 2160次测试（约1.5小时）
     * 相比完整实验减少60%时间，仍保持充分覆盖性
     *
     * @return 函数列表
     */
    public static List<BenchmarkFunction> getReducedFunctions() {
        List<BenchmarkFunction> functions = new ArrayList<>();

        // 单峰函数 (2个) - 测试收敛速度
        functions.add(new Sphere(30));           // F1 - 最简单单峰
        functions.add(new Zakharov(30));         // F3 - 单峰带交叉项

        // 经典多峰函数 (4个) - 核心测试集
        functions.add(new Rosenbrock(30));       // F6 - 山谷函数（LSCBO表现优异）
        functions.add(new Rastrigin(30));        // F7 - 高频多峰
        functions.add(new Griewank(30));         // F8 - 平滑多峰
        functions.add(new Ackley(30));           // F9 - 复杂多峰（LSCBO弱点）

        // 欺骗性函数 (2个)
        functions.add(new Schwefel(30));         // F10 - 欺骗性多峰
        functions.add(new Levy(30));             // F12 - Levy函数（LSCBO弱点）

        // 特殊景观函数 (2个) - LSCBO优势函数
        functions.add(new DixonPrice(30));       // F13 - Dixon-Price（LSCBO夺冠）
        functions.add(new HappyCat(30));         // F19 - HappyCat（LSCBO夺冠）

        // 复杂景观函数 (2个)
        functions.add(new Salomon(30));          // F17 - Salomon（LSCBO弱点）
        functions.add(new Weierstrass(30));      // F22 - 分形景观

        return functions;
    }

    /**
     * 单个函数单次运行
     *
     * @param function 基准函数
     * @param optimizer 优化器接口（算法实现）
     * @param maxIterations 最大迭代次数
     * @return 最终适应度值
     */
    public static double runSingleTest(BenchmarkFunction function,
                                      BenchmarkOptimizer optimizer,
                                      int maxIterations) {
        // 调用优化器执行优化
        double bestFitness = optimizer.optimize(function, maxIterations);

        return bestFitness;
    }

    /**
     * 单个函数多次独立运行
     *
     * @param function 基准函数
     * @param optimizer 优化器接口
     * @param maxIterations 最大迭代次数
     * @param numRuns 独立运行次数
     * @return 结果统计对象
     */
    public static BenchmarkResult runMultipleTests(BenchmarkFunction function,
                                                  BenchmarkOptimizer optimizer,
                                                  int maxIterations,
                                                  int numRuns) {
        List<Double> results = new ArrayList<>();

        System.out.println(String.format("\n[%s] Testing %s on %s (%d runs, %d iterations)",
                                        optimizer.getName(),
                                        optimizer.getName(),
                                        function.getName(),
                                        numRuns,
                                        maxIterations));

        for (int run = 0; run < numRuns; run++) {
            double fitness = runSingleTest(function, optimizer, maxIterations);
            results.add(fitness);

            // 每5次运行输出一次进度
            if ((run + 1) % 5 == 0 || run == numRuns - 1) {
                System.out.println(String.format("  Run %d/%d: Best=%.6f",
                                                run + 1, numRuns, fitness));
            }
        }

        return new BenchmarkResult(
            function.getName(),
            optimizer.getName(),
            maxIterations,
            numRuns,
            results
        );
    }

    /**
     * 优化器接口（算法需要实现此接口）
     */
    public interface BenchmarkOptimizer {
        /**
         * 执行优化
         *
         * @param function 基准函数
         * @param maxIterations 最大迭代次数
         * @return 最优适应度值
         */
        double optimize(BenchmarkFunction function, int maxIterations);

        /**
         * 获取算法名称
         *
         * @return 算法名称
         */
        String getName();
    }

    /**
     * 基准测试结果
     */
    public static class BenchmarkResult {
        private final String functionName;
        private final String algorithmName;
        private final int maxIterations;
        private final int numRuns;
        private final List<Double> rawResults;

        // 统计指标
        private final double avgFitness;
        private final double stdFitness;
        private final double minFitness;
        private final double maxFitness;
        private final double medianFitness;

        public BenchmarkResult(String functionName, String algorithmName,
                              int maxIterations, int numRuns,
                              List<Double> rawResults) {
            this.functionName = functionName;
            this.algorithmName = algorithmName;
            this.maxIterations = maxIterations;
            this.numRuns = numRuns;
            this.rawResults = new ArrayList<>(rawResults);

            // 计算统计指标
            this.avgFitness = calculateAverage(rawResults);
            this.stdFitness = calculateStdDev(rawResults, avgFitness);
            this.minFitness = rawResults.stream().min(Double::compare).orElse(0.0);
            this.maxFitness = rawResults.stream().max(Double::compare).orElse(0.0);
            this.medianFitness = calculateMedian(rawResults);
        }

        private double calculateAverage(List<Double> values) {
            return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        }

        private double calculateStdDev(List<Double> values, double mean) {
            double variance = values.stream()
                .mapToDouble(v -> Math.pow(v - mean, 2))
                .average()
                .orElse(0.0);
            return Math.sqrt(variance);
        }

        private double calculateMedian(List<Double> values) {
            List<Double> sorted = new ArrayList<>(values);
            sorted.sort(Double::compare);
            int n = sorted.size();
            if (n % 2 == 0) {
                return (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
            } else {
                return sorted.get(n / 2);
            }
        }

        // Getters

        public String getFunctionName() {
            return functionName;
        }

        public String getAlgorithmName() {
            return algorithmName;
        }

        public int getMaxIterations() {
            return maxIterations;
        }

        public int getNumRuns() {
            return numRuns;
        }

        public List<Double> getRawResults() {
            return new ArrayList<>(rawResults);
        }

        public double getAvgFitness() {
            return avgFitness;
        }

        public double getStdFitness() {
            return stdFitness;
        }

        public double getMinFitness() {
            return minFitness;
        }

        public double getMaxFitness() {
            return maxFitness;
        }

        public double getMedianFitness() {
            return medianFitness;
        }

        /**
         * 打印结果摘要
         */
        public void printSummary() {
            System.out.println(String.format("\n--- %s on %s (Iter=%d, Runs=%d) ---",
                                            algorithmName, functionName, maxIterations, numRuns));
            System.out.println(String.format("Average: %.6e", avgFitness));
            System.out.println(String.format("StdDev:  %.6e", stdFitness));
            System.out.println(String.format("Min:     %.6e", minFitness));
            System.out.println(String.format("Max:     %.6e", maxFitness));
            System.out.println(String.format("Median:  %.6e", medianFitness));
        }

        /**
         * 转换为CSV行（用于结果写入）
         *
         * @return CSV格式字符串
         */
        public String toCSVRow() {
            return String.format("%s,%s,%d,%d,%.6e,%.6e,%.6e,%.6e,%.6e",
                                functionName, algorithmName, maxIterations, numRuns,
                                avgFitness, stdFitness, minFitness, maxFitness, medianFitness);
        }

        /**
         * CSV头部
         *
         * @return CSV头部字符串
         */
        public static String getCSVHeader() {
            return "Function,Algorithm,MaxIterations,NumRuns,AvgFitness,StdFitness,MinFitness,MaxFitness,MedianFitness";
        }
    }

    /**
     * 打印所有函数信息
     */
    public static void printAllFunctions() {
        System.out.println("\n========== CEC2017 Benchmark Functions ==========");
        List<BenchmarkFunction> functions = getAllFunctions();
        for (BenchmarkFunction func : functions) {
            System.out.println("\n" + func.getDescription());
        }
        System.out.println("\n=================================================\n");
    }
}

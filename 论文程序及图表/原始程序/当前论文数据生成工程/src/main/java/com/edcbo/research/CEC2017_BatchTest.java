package com.edcbo.research;

import com.edcbo.research.benchmark.BenchmarkFunction;
import com.edcbo.research.benchmark.functions.*;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * CEC2017基准测试批量实验（参照ERTH论文方法）
 *
 * 实验配置（参照ERTH论文Table 4-5）：
 * - 算法：LSCBO, CBO, PSO, GWO, WOA, HHO, AOA, GTO
 * - 函数：5个CEC2017基准函数（F1-F5）
 * - 维度：D=30
 * - 运行次数：30次独立运行
 * - 评估次数：300,000（N=30 × MaxIter=10,000）
 *
 * 输出指标（参照ERTH论文Table 5）：
 * - Mean（平均适应度）
 * - Std（标准差）
 * - Rank（算法排名）
 * - Convergence Curve (平均收敛曲线)
 * - Diversity Curve (平均多样性曲线)
 */
public class CEC2017_BatchTest {

    // 实验参数（参照ERTH论文）
    private static final int DIMENSIONS = 30; // D=30
    private static final int NUM_RUNS = 30; // 30次独立运行
    private static final int MAX_ITERATIONS = 10000;
    private static final long[] SEEDS = generateSeeds(); // 30个随机种子

    // 算法名称
    private static final String[] ALGORITHM_NAMES = {
            "LSCBO", "CBO", "PSO",
            "GWO", "HHO", "AOA", "GTO"
    };

    // 基准函数（使用functions包下的类）
    private static final BenchmarkFunction[] BENCHMARK_FUNCTIONS = {
            new Sphere(), // F1: 单峰函数
            new Rosenbrock(), // F2: 多峰函数
            new Rastrigin(), // F3: 高度多峰函数
            new Ackley(), // F4: 多峰函数
            new Schwefel() // F5: 欺骗性函数
    };

    /**
     * 生成30个随机种子（确保可重复性）
     */
    private static long[] generateSeeds() {
        long[] seeds = new long[NUM_RUNS];
        long baseSeed = 20251216L; // 基准种子：2025-12-16
        for (int i = 0; i < NUM_RUNS; i++) {
            seeds[i] = baseSeed + i * 1000;
        }
        return seeds;
    }

    /**
     * 主函数
     */
    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("CEC2017基准测试批量实验 (数据增强版)");
        System.out.println("参照ERTH论文实验方法");
        System.out.println("========================================");

        List<ExperimentResult> allResults = new ArrayList<>();
        List<CurveResult> curveResults = new ArrayList<>();

        int totalExperiments = ALGORITHM_NAMES.length * BENCHMARK_FUNCTIONS.length * NUM_RUNS;
        int completedExperiments = 0;

        long startTime = System.currentTimeMillis();

        for (String algorithmName : ALGORITHM_NAMES) {
            for (BenchmarkFunction function : BENCHMARK_FUNCTIONS) {
                System.out.println("\n>>> 测试: " + algorithmName + " on " + function.getName());

                double[] fitnessResults = new double[NUM_RUNS];
                double[] sumConvergence = new double[MAX_ITERATIONS];
                double[] sumDiversity = new double[MAX_ITERATIONS];

                for (int run = 0; run < NUM_RUNS; run++) {
                    long seed = SEEDS[run];

                    // 运行算法
                    RecordableOptimizer optimizer = runAlgorithm(algorithmName, function, seed);
                    double fitness = optimizer.optimize();
                    fitnessResults[run] = fitness;

                    // 收集历史数据
                    double[] conv = optimizer.getConvergenceCurve();
                    double[] div = optimizer.getDiversityCurve();

                    if (run == 0) {
                        saveQualitativeData(optimizer, algorithmName, function.getName(),
                                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")));
                    }

                    for (int t = 0; t < MAX_ITERATIONS; t++) {
                        sumConvergence[t] += conv[t];
                        sumDiversity[t] += div[t];
                    }

                    completedExperiments++;
                    double progress = (completedExperiments * 100.0) / totalExperiments;

                    if (run % 5 == 0 || run == NUM_RUNS - 1) { // 减少控制台输出频率
                        System.out.printf("  Run %2d/%d: Fitness=%.4e  [%.1f%%]\n",
                                run + 1, NUM_RUNS, fitness, progress);
                    }
                }

                // 计算统计指标
                double mean = calculateMean(fitnessResults);
                double std = calculateStd(fitnessResults, mean);

                ExperimentResult result = new ExperimentResult(
                        algorithmName, function.getName(), mean, std, fitnessResults);
                allResults.add(result);

                // 计算平均曲线
                double[] avgConvergence = new double[MAX_ITERATIONS];
                double[] avgDiversity = new double[MAX_ITERATIONS];
                for (int t = 0; t < MAX_ITERATIONS; t++) {
                    avgConvergence[t] = sumConvergence[t] / NUM_RUNS;
                    avgDiversity[t] = sumDiversity[t] / NUM_RUNS;
                }
                curveResults.add(new CurveResult(algorithmName, function.getName(), avgConvergence, avgDiversity));

                System.out.printf("  >>> 结果: Mean=%.6e, Std=%.6e\n", mean, std);
            }
        }

        long endTime = System.currentTimeMillis();
        double duration = (endTime - startTime) / 1000.0;
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

        // 保存结果
        saveResults(allResults, duration, timestamp);
        saveCurveData(curveResults, timestamp);
    }

    private static void saveQualitativeData(RecordableOptimizer optimizer, String algo, String func, String timestamp) {
        String dir = "CEC2017/qualitative";
        new java.io.File(dir).mkdirs();

        // 1. Trajectory & Average Fitness (Time Series)
        String curvesFile = dir + "/qualitative_curves_" + timestamp + ".csv";
        boolean append = new java.io.File(curvesFile).exists();
        try (PrintWriter writer = new PrintWriter(new FileWriter(curvesFile, true))) {
            if (!append)
                writer.println("Algorithm,Function,Iteration,Trajectory,AvgFitness");
            double[] traj = optimizer.getTrajectoryCurve();
            double[] avgFit = optimizer.getAverageFitnessCurve();
            for (int t = 0; t < MAX_ITERATIONS; t++) {
                // Save sparse to save space (every 10th iter + early phase)
                if (t < 100 || t % 10 == 0 || t == MAX_ITERATIONS - 1) {
                    writer.printf("%s,%s,%d,%.10e,%.10e\n", algo, func, t, traj[t], avgFit[t]);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // 2. Search History (Snapshots)
        String historyFile = dir + "/qualitative_history_" + timestamp + ".csv";
        boolean appendHist = new java.io.File(historyFile).exists();
        try (PrintWriter writer = new PrintWriter(new FileWriter(historyFile, true))) {
            if (!appendHist)
                writer.println("Algorithm,Function,SnapshotIter,AgentId,Dim0,Dim1");
            List<double[][]> history = optimizer.getSearchHistory();
            int[] snapshotIters = { 0, 10, 50, 100, 500, 1000, 5000, 9999 }; // Must match optimizer

            for (int i = 0; i < history.size(); i++) {
                if (i >= snapshotIters.length)
                    break;
                int iter = snapshotIters[i];
                double[][] positions = history.get(i);
                for (int agent = 0; agent < positions.length; agent++) {
                    // Only save first 2 dims for visualization
                    writer.printf("%s,%s,%d,%d,%.10e,%.10e\n",
                            algo, func, iter, agent, positions[agent][0], positions[agent][1]);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 运行指定算法
     */
    private static RecordableOptimizer runAlgorithm(String algorithmName, BenchmarkFunction function, long seed) {
        switch (algorithmName) {
            case "LSCBO":
                return new LSCBO_ContinuousOptimizer(function, DIMENSIONS, seed);
            case "CBO":
                return new CBO_ContinuousOptimizer(function, DIMENSIONS, seed);
            case "PSO":
                return new PSO_ContinuousOptimizer(function, DIMENSIONS, seed);
            case "GWO":
                return new GWO_ContinuousOptimizer(function, DIMENSIONS, seed);
            case "HHO":
                return new HHO_ContinuousOptimizer(function, DIMENSIONS, seed);
            case "AOA":
                return new AOA_ContinuousOptimizer(function, DIMENSIONS, seed);
            case "GTO":
                return new GTO_ContinuousOptimizer(function, DIMENSIONS, seed);
            default:
                throw new IllegalArgumentException("Unknown algorithm: " + algorithmName);
        }
    }

    private static double calculateMean(double[] values) {
        return Arrays.stream(values).average().orElse(0.0);
    }

    private static double calculateStd(double[] values, double mean) {
        double sumSquares = 0.0;
        for (double value : values) {
            sumSquares += Math.pow(value - mean, 2);
        }
        return Math.sqrt(sumSquares / values.length);
    }

    /**
     * 保存结果表格
     */
    private static void saveResults(List<ExperimentResult> results, double duration, String timestamp) {
        String filename = "CEC2017/cec2017_results_" + timestamp + ".csv";
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            writer.println("Algorithm,Function,Mean,Std");
            for (ExperimentResult result : results) {
                writer.printf("%s,%s,%.10e,%.10e\n",
                        result.algorithm, result.function, result.mean, result.std);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Detailed
        String detailedFilename = "CEC2017/cec2017_detailed_" + timestamp + ".csv";
        try (PrintWriter writer = new PrintWriter(new FileWriter(detailedFilename))) {
            writer.print("Algorithm,Function");
            for (int i = 1; i <= NUM_RUNS; i++)
                writer.print(",Run" + i);
            writer.println(",Mean,Std");
            for (ExperimentResult result : results) {
                writer.print(result.algorithm + "," + result.function);
                for (double f : result.allRuns)
                    writer.printf(",%.10e", f);
                writer.printf(",%.10e,%.10e\n", result.mean, result.std);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Ranking
        saveRanking(results, timestamp);
    }

    private static void saveRanking(List<ExperimentResult> results, String timestamp) {
        String filename = "CEC2017/cec2017_ranking_" + timestamp + ".csv";
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            writer.println("Function," + String.join(",", ALGORITHM_NAMES) + ",BestAlgorithm");

            for (BenchmarkFunction function : BENCHMARK_FUNCTIONS) {
                String funcName = function.getName();
                List<ExperimentResult> funcRes = new ArrayList<>();
                for (ExperimentResult r : results)
                    if (r.function.equals(funcName))
                        funcRes.add(r);
                funcRes.sort((a, b) -> Double.compare(a.mean, b.mean)); // Sort by mean fitness

                writer.print(funcName);
                for (String algo : ALGORITHM_NAMES) {
                    for (int rank = 0; rank < funcRes.size(); rank++) {
                        if (funcRes.get(rank).algorithm.equals(algo)) {
                            writer.print("," + (rank + 1));
                            break;
                        }
                    }
                }
                writer.println("," + funcRes.get(0).algorithm);
            }

            // Average Rank
            writer.print("Average Rank");
            for (String algo : ALGORITHM_NAMES) {
                double avgRank = 0;
                int count = 0;
                for (BenchmarkFunction f : BENCHMARK_FUNCTIONS) {
                    String funcName = f.getName();
                    List<ExperimentResult> funcRes = new ArrayList<>();
                    for (ExperimentResult r : results)
                        if (r.function.equals(funcName))
                            funcRes.add(r);
                    funcRes.sort((a, b) -> Double.compare(a.mean, b.mean));
                    for (int rank = 0; rank < funcRes.size(); rank++) {
                        if (funcRes.get(rank).algorithm.equals(algo)) {
                            avgRank += (rank + 1);
                            count++;
                            break;
                        }
                    }
                }
                writer.printf(",%.2f", avgRank / count);
            }
            writer.println(",");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 保存曲线数据 (Convergence & Diversity)
     */
    private static void saveCurveData(List<CurveResult> results, String timestamp) {
        // 保存收敛曲线 (抽样保存以减小文件体积，每100代存一次，前100代全存)
        String convFile = "CEC2017/cec2017_convergence_" + timestamp + ".csv";
        try (PrintWriter writer = new PrintWriter(new FileWriter(convFile))) {
            writer.println("Function,Algorithm,Iteration,AvgFitness");
            for (CurveResult r : results) {
                for (int t = 0; t < MAX_ITERATIONS; t++) {
                    // Save detailed early iterations, then sparse
                    if (t < 100 || t % 50 == 0 || t == MAX_ITERATIONS - 1) {
                        writer.printf("%s,%s,%d,%.10e\n", r.function, r.algorithm, t + 1, r.avgConvergence[t]);
                    }
                }
            }
            System.out.println("Convergence data saved to " + convFile);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // 保存多样性曲线
        String divFile = "CEC2017/cec2017_diversity_" + timestamp + ".csv";
        try (PrintWriter writer = new PrintWriter(new FileWriter(divFile))) {
            writer.println("Function,Algorithm,Iteration,Diversity");
            for (CurveResult r : results) {
                for (int t = 0; t < MAX_ITERATIONS; t++) {
                    if (t < 100 || t % 50 == 0 || t == MAX_ITERATIONS - 1) {
                        writer.printf("%s,%s,%d,%.10e\n", r.function, r.algorithm, t + 1, r.avgDiversity[t]);
                    }
                }
            }
            System.out.println("Diversity data saved to " + divFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class ExperimentResult {
        String algorithm;
        String function;
        double mean;
        double std;
        double[] allRuns;

        ExperimentResult(String algorithm, String function, double mean, double std, double[] allRuns) {
            this.algorithm = algorithm;
            this.function = function;
            this.mean = mean;
            this.std = std;
            this.allRuns = allRuns;
        }
    }

    private static class CurveResult {
        String algorithm;
        String function;
        double[] avgConvergence;
        double[] avgDiversity;

        CurveResult(String algo, String func, double[] conv, double[] div) {
            this.algorithm = algo;
            this.function = func;
            this.avgConvergence = conv;
            this.avgDiversity = div;
        }
    }
}

package com.edcbo.research;

import com.edcbo.research.benchmark.BenchmarkFunction;
import com.edcbo.research.benchmark.functions.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * CEC2017基准测试批量消融实验 (Ablation Study)
 * 仅运行: CBO, CBO-noStatic, CBO+Levy, LSCBO(即LF-CBO)
 * 结果输出到指定的 revisions_data/ablation_cec2017/ 目录下
 */
public class CEC2017_BatchTest_Ablation {

    private static final int DIMENSIONS = 30;
    private static final int NUM_RUNS = 30;
    private static final int MAX_ITERATIONS = 10000;
    private static final long[] SEEDS = generateSeeds();

    // 消融实验专用的 4 种算法
    private static final String[] ALGORITHM_NAMES = {
            "CBO", "CBO_noStatic", "CBO_Levy", "LSCBO"
    };

    // 输出数据的根目录 (强制绝对路径)
    private static final String OUTPUT_ROOT = "d:/论文/new/revisions_data/ablation_cec2017";

    private static final BenchmarkFunction[] BENCHMARK_FUNCTIONS = {
            new Sphere(), // F1
            new Zakharov(), // F3
            new Rosenbrock(), // F4
            new Rastrigin(), // F5
            new Ackley(), // F6
            new Griewank(), // F7
            new Levy(), // F8
            new Schwefel(), // F9
            new Michalewicz(), // F10
            new BentCigar(), // F11
            new Discus(), // F12
            new HighConditionedElliptic(), // F13
            new DixonPrice(), // F14
            new HappyCat(), // F15
            new Step(), // F16
            new Bohachevsky(), // F17
            new Quartic(), // F18
            new Exponential(), // F19
            new Alpine(), // F20
            new HybridFunction1(), // F21
            new HybridFunction2(), // F22
            new ExpandedSchafferF6(), // F23
            new Pathological(), // F24
            new Periodic(), // F25
            new Salomon(), // F26
            new StyblinskiTang(), // F27
            new Weierstrass(), // F28
            new XinSheYang(), // F29
            new SumOfDifferentPowers() // F30
    };

    private static long[] generateSeeds() {
        long[] seeds = new long[NUM_RUNS];
        long baseSeed = 20251216L;
        for (int i = 0; i < NUM_RUNS; i++) {
            seeds[i] = baseSeed + i * 1000;
        }
        return seeds;
    }

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("CEC2017 消融实验批量测试 (Ablation Study)");
        System.out.println("输出目录: " + OUTPUT_ROOT);
        System.out.println("========================================");

        new File(OUTPUT_ROOT).mkdirs();

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
                    RecordableOptimizer optimizer = runAlgorithm(algorithmName, function, seed);
                    double fitness = optimizer.optimize();
                    fitnessResults[run] = fitness;

                    double[] conv = optimizer.getConvergenceCurve();
                    double[] div = optimizer.getDiversityCurve();

                    for (int t = 0; t < MAX_ITERATIONS; t++) {
                        sumConvergence[t] += conv[t];
                        sumDiversity[t] += div[t];
                    }

                    completedExperiments++;
                    double progress = (completedExperiments * 100.0) / totalExperiments;

                    if (run % 5 == 0 || run == NUM_RUNS - 1) {
                        System.out.printf("  Run %2d/%d: Fitness=%.4e  [%.1f%%]\n",
                                run + 1, NUM_RUNS, fitness, progress);
                    }
                }

                double mean = calculateMean(fitnessResults);
                double std = calculateStd(fitnessResults, mean);

                allResults.add(new ExperimentResult(algorithmName, function.getName(), mean, std, fitnessResults));

                double[] avgConvergence = new double[MAX_ITERATIONS];
                double[] avgDiversity = new double[MAX_ITERATIONS];
                for (int t = 0; t < MAX_ITERATIONS; t++) {
                    avgConvergence[t] = sumConvergence[t] / NUM_RUNS;
                    avgDiversity[t] = sumDiversity[t] / NUM_RUNS;
                }
                curveResults.add(new CurveResult(algorithmName, function.getName(), avgConvergence, avgDiversity));
            }
        }

        long endTime = System.currentTimeMillis();
        double duration = (endTime - startTime) / 1000.0;
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

        saveResults(allResults, duration, timestamp);
        saveCurveData(curveResults, timestamp);
        System.out.println("\n所有消融实验完成！总耗时: " + duration + " 秒.");
    }

    private static RecordableOptimizer runAlgorithm(String algorithmName, BenchmarkFunction function, long seed) {
        switch (algorithmName) {
            case "CBO":
                return new CBO_ContinuousOptimizer(function, DIMENSIONS, seed);
            case "CBO_noStatic":
                return new CBO_noStatic_ContinuousOptimizer(function, DIMENSIONS, seed);
            case "CBO_Levy":
                return new CBO_Levy_ContinuousOptimizer(function, DIMENSIONS, seed);
            case "LSCBO":
                // LF-CBO在代码中原名为 LSCBO_ContinuousOptimizer
                return new LSCBO_ContinuousOptimizer(function, DIMENSIONS, seed);
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

    private static void saveResults(List<ExperimentResult> results, double duration, String timestamp) {
        String filename = OUTPUT_ROOT + "/cec2017_ablation_results.csv";
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            writer.println("Algorithm,Function,Mean,Std");
            for (ExperimentResult result : results) {
                writer.printf("%s,%s,%.10e,%.10e\n",
                        result.algorithm, result.function, result.mean, result.std);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        String detailedFilename = OUTPUT_ROOT + "/cec2017_ablation_detailed.csv";
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

        saveRanking(results);
    }

    private static void saveRanking(List<ExperimentResult> results) {
        String filename = OUTPUT_ROOT + "/cec2017_ablation_ranking.csv";
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            writer.println("Function," + String.join(",", ALGORITHM_NAMES) + ",BestAlgorithm");

            for (BenchmarkFunction function : BENCHMARK_FUNCTIONS) {
                String funcName = function.getName();
                List<ExperimentResult> funcRes = new ArrayList<>();
                for (ExperimentResult r : results)
                    if (r.function.equals(funcName))
                        funcRes.add(r);
                funcRes.sort((a, b) -> Double.compare(a.mean, b.mean));

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

    private static void saveCurveData(List<CurveResult> results, String timestamp) {
        String convFile = OUTPUT_ROOT + "/cec2017_ablation_convergence.csv";
        try (PrintWriter writer = new PrintWriter(new FileWriter(convFile))) {
            writer.println("Function,Algorithm,Iteration,AvgFitness");
            for (CurveResult r : results) {
                for (int t = 0; t < MAX_ITERATIONS; t++) {
                    // 只保存部分稀疏数据以防文件太大
                    if (t < 100 || t % 50 == 0 || t == MAX_ITERATIONS - 1) {
                        writer.printf("%s,%s,%d,%.10e\n", r.function, r.algorithm, t + 1, r.avgConvergence[t]);
                    }
                }
            }
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

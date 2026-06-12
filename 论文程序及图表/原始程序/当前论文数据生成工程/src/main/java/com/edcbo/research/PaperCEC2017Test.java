package com.edcbo.research;

import com.edcbo.research.benchmark.BenchmarkFunction;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

/**
 * 论文CEC2017实验复现 (Paper CEC2017 Reproduction)
 * 用于生成图6（收敛对比）、图7（定性分析）、图8（种群多样性）的数据
 */
public class PaperCEC2017Test {

    private static final int DIM = 30; // 30维测试
    private static final long SEED = 42;
    private static final String RESULT_DIR = "CEC2017/results";

    // 参与对比的算法
    private static final String[] ALGOS = { "LSCBO", "CBO", "PSO", "HHO", "AOA" };

    public static void main(String[] args) {
        new File(RESULT_DIR).mkdirs();
        new File(RESULT_DIR + "/qualitative").mkdirs();
        new File(RESULT_DIR + "/convergence").mkdirs();

        System.out.println("Starting Paper CEC2017 Reproduction...");

        // 1. 定性分析 (Figure 7 & 8) - 只跑LSCBO在F1上
        runQualitativeAnalysis(1);

        // 2. 收敛对比 (Figure 6) - 跑所有算法在代表性函数上
        int[] funcs = { 1, 10, 20, 30 }; // 单峰、多峰、混合、组合
        for (int fId : funcs) {
            runConvergenceComparison(fId);
        }

        System.out.println("CEC2017 Reproduction Finished.");
    }

    private static void runQualitativeAnalysis(int fId) {
        System.out.println("Running Qualitative Analysis on F" + fId + "...");
        BenchmarkFunction func = getFunction(fId);
        LSCBO_ContinuousOptimizer optimizer = new LSCBO_ContinuousOptimizer(func, DIM, SEED);

        optimizer.optimize();

        // Save Diversity (Figure 8)
        saveEfficiency(optimizer.getDiversityCurve(), "diversity_F" + fId + ".csv");

        // Save Trajectory (Figure 7)
        saveEfficiency(optimizer.getTrajectoryCurve(), "trajectory_F" + fId + ".csv");

        // Save Average Fitness (Figure 7)
        saveEfficiency(optimizer.getAverageFitnessCurve(), "avg_fitness_F" + fId + ".csv");

        // Save Search History (Figure 7 Scatter)
        saveSearchHistory(optimizer.getSearchHistory(), "history_F" + fId + ".csv");
    }

    private static void runConvergenceComparison(int fId) {
        System.out.println("Running Convergence Comparison on F" + fId + "...");
        BenchmarkFunction func = getFunction(fId);

        try (PrintWriter writer = new PrintWriter(
                new FileWriter(RESULT_DIR + "/convergence/F" + fId + "_comparison.csv"))) {
            writer.print("Iteration");
            for (String algo : ALGOS)
                writer.print("," + algo);
            writer.println();

            // Store curves to write column-wise
            double[][] allCurves = new double[ALGOS.length][];

            for (int i = 0; i < ALGOS.length; i++) {
                RecordableOptimizer opt = createOptimizer(ALGOS[i], func, DIM, SEED);
                opt.optimize();
                allCurves[i] = opt.getConvergenceCurve();
            }

            int iters = allCurves[0].length;
            for (int t = 0; t < iters; t++) {
                writer.print(t);
                for (int i = 0; i < ALGOS.length; i++) {
                    writer.printf(",%.6e", allCurves[i][t]);
                }
                writer.println();
            }
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void saveEfficiency(double[] curve, String name) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(RESULT_DIR + "/qualitative/" + name))) {
            writer.println("Iteration,Value");
            for (int t = 0; t < curve.length; t++) {
                writer.printf("%d,%.6e%n", t, curve[t]);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void saveSearchHistory(List<double[][]> history, String name) {
        // Format: SnapshotIdx, ParticleIdx, Dim0, Dim1 (2D visualization)
        try (PrintWriter writer = new PrintWriter(new FileWriter(RESULT_DIR + "/qualitative/" + name))) {
            writer.println("Snapshot,Particle,X,Y");
            for (int s = 0; s < history.size(); s++) {
                double[][] pop = history.get(s);
                for (int p = 0; p < pop.length; p++) {
                    writer.printf("%d,%d,%.6f,%.6f%n", s, p, pop[p][0], pop[p][1]);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static RecordableOptimizer createOptimizer(String name, BenchmarkFunction f, int dim, long seed) {
        switch (name) {
            case "LSCBO":
                return new LSCBO_ContinuousOptimizer(f, dim, seed);
            case "CBO":
                return new CBO_ContinuousOptimizer(f, dim, seed);
            case "PSO":
                return new PSO_ContinuousOptimizer(f, dim, seed);
            case "HHO":
                return new HHO_ContinuousOptimizer(f, dim, seed);
            case "AOA":
                return new AOA_ContinuousOptimizer(f, dim, seed);
            case "GTO":
                return new GTO_ContinuousOptimizer(f, dim, seed);
            // Add GWO, WOA if classes exist
            default:
                throw new IllegalArgumentException("Unknown algo: " + name);
        }
    }

    private static BenchmarkFunction getFunction(int fId) {
        switch (fId) {
            case 1:
                return new com.edcbo.research.benchmark.functions.Sphere();
            case 10:
                return new com.edcbo.research.benchmark.functions.Ackley();
            case 20:
                return new com.edcbo.research.benchmark.functions.Rastrigin();
            case 30:
                return new com.edcbo.research.benchmark.functions.Schwefel();
            default:
                throw new IllegalArgumentException("Unsupported Function ID: " + fId);
        }
    }
}

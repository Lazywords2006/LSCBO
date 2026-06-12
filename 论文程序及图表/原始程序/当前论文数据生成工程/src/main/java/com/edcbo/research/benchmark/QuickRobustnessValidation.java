package com.edcbo.research.benchmark;

import com.edcbo.research.benchmark.functions.*;

/**
 * 快速验证 - 3算法×3函数×1运行
 *
 * @author LSCBO Research Team
 * @date 2025-12-17
 */
public class QuickRobustnessValidation {

    public static void main(String[] args) {
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println("  快速鲁棒性验证");
        System.out.println("═══════════════════════════════════════════════════════════════\n");

        BenchmarkFunction[] functions = {
            new Sphere(),
            new Rastrigin(),
            new Ackley()
        };

        String[] names = {"LSCBO", "CBO", "PSO"};
        BenchmarkRunner.BenchmarkOptimizer[] algorithms = {
            new LSCBO_Fixed_Lite(42L),
            new CBO(42L),
            new PSO(42L)
        };

        double[][] results = new double[3][3];

        for (int a = 0; a < 3; a++) {
            System.out.println("\n" + "═".repeat(80));
            System.out.println("  " + names[a]);
            System.out.println("═".repeat(80));

            for (int f = 0; f < 3; f++) {
                System.out.println("\n" + functions[f].getName() + ":");
                results[a][f] = algorithms[a].optimize(functions[f], 1000);
                System.out.println(String.format(">>> 结果: %.6e\n", results[a][f]));
            }
        }

        // 对比
        System.out.println("\n" + "═".repeat(80));
        System.out.println("  对比结果");
        System.out.println("═".repeat(80));
        System.out.println(String.format("\n%-15s %-15s %-15s %-15s",
            "算法", "Sphere", "Rastrigin", "Ackley"));
        System.out.println("-".repeat(80));

        for (int a = 0; a < 3; a++) {
            System.out.println(String.format("%-15s %.6e  %.6e  %.6e",
                names[a], results[a][0], results[a][1], results[a][2]));
        }

        // 退化分析
        System.out.println("\n" + "═".repeat(80));
        System.out.println("  性能对比分析");
        System.out.println("═".repeat(80));

        String[] funcNames = {"Sphere", "Rastrigin", "Ackley"};
        for (int f = 0; f < 3; f++) {
            System.out.println(String.format("\n%s:", funcNames[f]));
            double cbo_diff = (results[1][f] / results[0][f] - 1.0) * 100.0;
            double pso_diff = (results[2][f] / results[0][f] - 1.0) * 100.0;

            System.out.println(String.format("  CBO vs LSCBO: %+.1f%% %s",
                cbo_diff, cbo_diff > 10 ? "⚠️" : "✅"));
            System.out.println(String.format("  PSO vs LSCBO: %+.1f%% %s",
                pso_diff, pso_diff > 10 ? "⚠️" : "✅"));
        }

        System.out.println("\n✅ 验证完成!");
    }
}

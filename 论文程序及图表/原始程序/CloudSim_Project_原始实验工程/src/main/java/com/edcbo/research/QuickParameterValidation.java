package com.edcbo.research;

import com.edcbo.research.benchmark.*;

/**
 * 快速参数验证 - 3算法×3函数×1次运行
 * 用于快速验证参数优化效果
 */
public class QuickParameterValidation {

    public static void main(String[] args) {
        System.out.println("\n=== 快速参数验证 (3算法×3函数×1次) ===\n");

        // 3个关键函数
        BenchmarkFunction[] functions = {
            new com.edcbo.research.benchmark.functions.Sphere(),
            new com.edcbo.research.benchmark.functions.Rastrigin(),
            new com.edcbo.research.benchmark.functions.Ackley()
        };

        // 3个核心算法
        BenchmarkRunner.BenchmarkOptimizer[] algorithms = {
            new LSCBO_Fixed_Lite(42L),
            new CBO_Lite(42L),
            new PSO_Lite(42L)
        };

        String[] algNames = {"LSCBO", "CBO", "PSO"};

        int maxIter = 1000;

        // 运行测试
        for (BenchmarkFunction func : functions) {
            System.out.println("\n【" + func.getName() + "】");
            System.out.println("───────────────────────────────");

            for (int i = 0; i < algorithms.length; i++) {
                System.out.print(algNames[i] + ": ");
                System.out.flush();

                double result = algorithms[i].optimize(func, maxIter);
                System.out.println(String.format("%.6e", result));
            }
        }

        System.out.println("\n" +"=".repeat(40));
    }
}

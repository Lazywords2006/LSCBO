package com.edcbo.research.benchmark.functions;

import com.edcbo.research.benchmark.BenchmarkFunction;

/**
 * F30: Hybrid Function 2（Rosenbrock + Ackley混合函数）
 *
 * 公式: f(x) = w1·Rosenbrock(x[0:n/2]) + w2·Ackley(x[n/2:n])
 * 其中 w1 = 0.5, w2 = 0.5
 *
 * 特点: 复合混合型，结合了狭窄山谷和多峰特性
 * 用途: 测试算法处理复杂混合地形的能力
 *
 * 搜索空间: [-30, 30]^n
 * 全局最优: x[0:n/2]=(1,1,...), x[n/2:n]=(0,0,...), f ≈ 0
 *
 * @author LSCBO Research Team
 */
public class HybridFunction2 extends BenchmarkFunction {

    private static final double W1 = 0.5;  // Rosenbrock权重
    private static final double W2 = 0.5;  // Ackley权重
    private static final double A = 20.0;  // Ackley参数a
    private static final double B = 0.2;   // Ackley参数b
    private static final double C = 2.0 * Math.PI;  // Ackley参数c

    /**
     * 构造函数（30维）
     */
    public HybridFunction2() {
        this(30);
    }

    /**
     * 构造函数（自定义维度）
     *
     * @param dimensions 问题维度
     */
    public HybridFunction2(int dimensions) {
        super(
            30,                         // ID: F30
            "Hybrid Function 2",        // 名称
            dimensions,                 // 维度
            -30.0,                      // 下界
            30.0,                       // 上界
            0.0,                        // 全局最优值（近似）
            FunctionType.MULTIMODAL     // 类型：多峰（混合）
        );
    }

    @Override
    public double evaluate(double[] x) {
        validateDimensions(x);

        int splitPoint = dimensions / 2;

        // 前半部分：Rosenbrock函数
        double rosenbrock = 0.0;
        for (int i = 0; i < splitPoint - 1; i++) {
            double term1 = x[i + 1] - x[i] * x[i];
            double term2 = x[i] - 1.0;
            rosenbrock += 100.0 * term1 * term1 + term2 * term2;
        }

        // 后半部分：Ackley函数
        int ackley_dim = dimensions - splitPoint;

        // Ackley第一项: Σx_i²
        double sum_squares = 0.0;
        for (int i = splitPoint; i < dimensions; i++) {
            sum_squares += x[i] * x[i];
        }

        // Ackley第二项: Σcos(2π·x_i)
        double sum_cos = 0.0;
        for (int i = splitPoint; i < dimensions; i++) {
            sum_cos += Math.cos(C * x[i]);
        }

        // Ackley完整公式
        double term1 = -A * Math.exp(-B * Math.sqrt(sum_squares / ackley_dim));
        double term2 = -Math.exp(sum_cos / ackley_dim);
        double ackley = term1 + term2 + A + Math.E;

        // 加权组合
        return W1 * rosenbrock + W2 * ackley;
    }

    /**
     * 获取函数详细信息
     *
     * @return 函数描述
     */
    public String getDetails() {
        return "Hybrid Function 2: f(x) = 0.5·Rosenbrock(x[0:n/2]) + 0.5·Ackley(x[n/2:n])\n" +
               "Characteristics: Complex hybrid, Narrow valley + Multimodal\n" +
               "Difficulty: Hard (combines valley navigation and multimodality)\n" +
               "Usage: Test handling complex mixed terrains\n" +
               getDescription();
    }
}

package com.edcbo.research.benchmark.functions;

import com.edcbo.research.benchmark.BenchmarkFunction;

/**
 * F3: Rastrigin函数（经典多峰函数）
 *
 * 公式: f(x) = 10n + Σ[x_i² - 10*cos(2π*x_i)]，i=1 to n
 * 特点: 高度多峰（大量局部最优），基于Sphere+余弦项
 * 用途: 测试算法跳出局部最优的能力
 *
 * 搜索空间: [-5.12, 5.12]^n
 * 全局最优: f(0,0,...,0) = 0
 *
 * @author LSCBO Research Team
 */
public class Rastrigin extends BenchmarkFunction {

    /**
     * 构造函数（30维）
     */
    public Rastrigin() {
        this(30);
    }

    /**
     * 构造函数（自定义维度）
     *
     * @param dimensions 问题维度
     */
    public Rastrigin(int dimensions) {
        super(
            3,                          // ID: F3
            "Rastrigin",                // 名称
            dimensions,                 // 维度
            -5.12,                      // 下界
            5.12,                       // 上界
            0.0,                        // 全局最优值
            FunctionType.MULTIMODAL     // 类型：多峰
        );
    }

    @Override
    public double evaluate(double[] x) {
        validateDimensions(x);

        double sum = 10.0 * dimensions;
        for (int i = 0; i < dimensions; i++) {
            sum += x[i] * x[i] - 10.0 * Math.cos(2.0 * Math.PI * x[i]);
        }

        return sum;
    }

    /**
     * 获取函数详细信息
     *
     * @return 函数描述
     */
    public String getDetails() {
        return "Rastrigin Function: f(x) = 10n + Σ[x_i² - 10*cos(2π*x_i)]\n" +
               "Characteristics: Highly Multimodal, Separable, Many local optima\n" +
               "Difficulty: Hard (numerous local optima)\n" +
               "Usage: Test escaping local optima\n" +
               getDescription();
    }
}

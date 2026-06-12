package com.edcbo.research.benchmark.functions;

import com.edcbo.research.benchmark.BenchmarkFunction;

/**
 * F7: Schwefel函数（欺骗性多峰函数）
 *
 * 公式: f(x) = 418.9829*n - Σ[x_i * sin(√|x_i|)]，i=1 to n
 * 特点: 全局最优远离搜索空间中心，极具欺骗性
 * 用途: 测试算法抵抗欺骗的能力
 *
 * 搜索空间: [-500, 500]^n
 * 全局最优: f(420.9687,...,420.9687) = 0
 *
 * @author LSCBO Research Team
 */
public class Schwefel extends BenchmarkFunction {

    /**
     * 构造函数（30维）
     */
    public Schwefel() {
        this(30);
    }

    /**
     * 构造函数（自定义维度）
     *
     * @param dimensions 问题维度
     */
    public Schwefel(int dimensions) {
        super(
            7,                          // ID: F7
            "Schwefel",                 // 名称
            dimensions,                 // 维度
            -500.0,                     // 下界
            500.0,                      // 上界
            0.0,                        // 全局最优值
            FunctionType.MULTIMODAL     // 类型：多峰
        );
    }

    @Override
    public double evaluate(double[] x) {
        validateDimensions(x);

        double sum = 0.0;
        for (int i = 0; i < dimensions; i++) {
            sum += x[i] * Math.sin(Math.sqrt(Math.abs(x[i])));
        }

        return 418.9829 * dimensions - sum;
    }

    /**
     * 获取函数详细信息
     *
     * @return 函数描述
     */
    public String getDetails() {
        return "Schwefel Function: f(x) = 418.9829*n - Σ[x_i * sin(√|x_i|)]\n" +
               "Characteristics: Highly Multimodal, Deceptive (optimum far from center)\n" +
               "Difficulty: Very Hard\n" +
               "Usage: Test resistance to deception\n" +
               "Note: Global optimum at x_i = 420.9687\n" +
               getDescription();
    }
}

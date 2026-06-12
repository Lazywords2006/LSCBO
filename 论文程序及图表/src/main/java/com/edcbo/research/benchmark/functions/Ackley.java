package com.edcbo.research.benchmark.functions;

import com.edcbo.research.benchmark.BenchmarkFunction;

/**
 * F6: Ackley函数（经典多峰函数）
 *
 * 公式: f(x) = -20*exp(-0.2*√(Σx_i²/n)) - exp(Σcos(2π*x_i)/n) + 20 + e
 * 特点: 外层近球形，内层有大量余弦波动
 * 用途: 测试算法在多峰地形中的搜索能力
 *
 * 搜索空间: [-32, 32]^n
 * 全局最优: f(0,0,...,0) = 0
 *
 * @author LSCBO Research Team
 */
public class Ackley extends BenchmarkFunction {

    /**
     * 构造函数（30维）
     */
    public Ackley() {
        this(30);
    }

    /**
     * 构造函数（自定义维度）
     *
     * @param dimensions 问题维度
     */
    public Ackley(int dimensions) {
        super(
            6,                          // ID: F6
            "Ackley",                   // 名称
            dimensions,                 // 维度
            -32.0,                      // 下界
            32.0,                       // 上界
            0.0,                        // 全局最优值
            FunctionType.MULTIMODAL     // 类型：多峰
        );
    }

    @Override
    public double evaluate(double[] x) {
        validateDimensions(x);

        double sum1 = 0.0;  // Σx_i²
        double sum2 = 0.0;  // Σcos(2π*x_i)

        for (int i = 0; i < dimensions; i++) {
            sum1 += x[i] * x[i];
            sum2 += Math.cos(2.0 * Math.PI * x[i]);
        }

        double term1 = -20.0 * Math.exp(-0.2 * Math.sqrt(sum1 / dimensions));
        double term2 = -Math.exp(sum2 / dimensions);
        double result = term1 + term2 + 20.0 + Math.E;

        return result;
    }

    /**
     * 获取函数详细信息
     *
     * @return 函数描述
     */
    public String getDetails() {
        return "Ackley Function: f(x) = -20*exp(-0.2*√(Σx_i²/n)) - exp(Σcos(2π*x_i)/n) + 20 + e\n" +
               "Characteristics: Multimodal, Non-Separable, Outer sphere + Inner waves\n" +
               "Difficulty: Hard\n" +
               "Usage: Test multimodal terrain navigation\n" +
               getDescription();
    }
}

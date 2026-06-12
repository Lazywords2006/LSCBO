package com.edcbo.research.benchmark.functions;

import com.edcbo.research.benchmark.BenchmarkFunction;

/**
 * F28: Exponential函数（指数型多峰函数）
 *
 * 公式: f(x) = -exp(-0.5·Σx_i²) + 1
 *
 * 特点: 多峰，指数衰减，全局最优在原点附近有陡峭峰值
 * 用途: 测试算法处理指数型地形的能力
 *
 * 搜索空间: [-1, 1]^n
 * 全局最优: f(0,0,...,0) = 0
 *
 * @author LSCBO Research Team
 */
public class Exponential extends BenchmarkFunction {

    /**
     * 构造函数（30维）
     */
    public Exponential() {
        this(30);
    }

    /**
     * 构造函数（自定义维度）
     *
     * @param dimensions 问题维度
     */
    public Exponential(int dimensions) {
        super(
            28,                         // ID: F28
            "Exponential",              // 名称
            dimensions,                 // 维度
            -1.0,                       // 下界
            1.0,                        // 上界
            0.0,                        // 全局最优值
            FunctionType.MULTIMODAL     // 类型：多峰
        );
    }

    @Override
    public double evaluate(double[] x) {
        validateDimensions(x);

        // 计算Σx_i²
        double sum_squares = 0.0;
        for (int i = 0; i < dimensions; i++) {
            sum_squares += x[i] * x[i];
        }

        // f(x) = -exp(-0.5·Σx_i²) + 1
        return -Math.exp(-0.5 * sum_squares) + 1.0;
    }

    /**
     * 获取函数详细信息
     *
     * @return 函数描述
     */
    public String getDetails() {
        return "Exponential Function: f(x) = -exp(-0.5·Σx_i²) + 1\n" +
               "Characteristics: Multimodal, Exponential decay, Steep peak at origin\n" +
               "Difficulty: Medium (exponential terrain)\n" +
               "Usage: Test handling exponential landscapes\n" +
               getDescription();
    }
}

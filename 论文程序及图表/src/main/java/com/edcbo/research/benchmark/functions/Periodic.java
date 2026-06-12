package com.edcbo.research.benchmark.functions;

import com.edcbo.research.benchmark.BenchmarkFunction;

/**
 * F19: Periodic函数（周期性多峰函数）
 *
 * 公式: f(x) = 1 + Σ[sin²(x_i)] - 0.1·exp(-Σx_i²)
 *
 * 特点: 多峰，周期性振荡，指数衰减项
 * 用途: 测试算法处理周期性地形的能力
 *
 * 搜索空间: [-10, 10]^n
 * 全局最优: f(0,0,...,0) = 0.9（近似）
 *
 * @author LSCBO Research Team
 */
public class Periodic extends BenchmarkFunction {

    /**
     * 构造函数（30维）
     */
    public Periodic() {
        this(30);
    }

    /**
     * 构造函数（自定义维度）
     *
     * @param dimensions 问题维度
     */
    public Periodic(int dimensions) {
        super(
            19,                         // ID: F19
            "Periodic",                 // 名称
            dimensions,                 // 维度
            -10.0,                      // 下界
            10.0,                       // 上界
            0.9,                        // 全局最优值（近似）
            FunctionType.MULTIMODAL     // 类型：多峰
        );
    }

    @Override
    public double evaluate(double[] x) {
        validateDimensions(x);

        // 计算Σ[sin²(x_i)]
        double sum_sin_squared = 0.0;
        for (int i = 0; i < dimensions; i++) {
            double sin_xi = Math.sin(x[i]);
            sum_sin_squared += sin_xi * sin_xi;
        }

        // 计算Σx_i²
        double sum_squares = 0.0;
        for (int i = 0; i < dimensions; i++) {
            sum_squares += x[i] * x[i];
        }

        // f(x) = 1 + Σ[sin²(x_i)] - 0.1·exp(-Σx_i²)
        return 1.0 + sum_sin_squared - 0.1 * Math.exp(-sum_squares);
    }

    /**
     * 获取函数详细信息
     *
     * @return 函数描述
     */
    public String getDetails() {
        return "Periodic Function: f(x) = 1 + Σ[sin²(x_i)] - 0.1·exp(-Σx_i²)\n" +
               "Characteristics: Multimodal, Periodic oscillations, Exponential decay\n" +
               "Difficulty: Medium (periodic landscape)\n" +
               "Usage: Test handling periodic terrains\n" +
               getDescription();
    }
}

package com.edcbo.research.benchmark.functions;

import com.edcbo.research.benchmark.BenchmarkFunction;

/**
 * F18: Salomon函数（多峰函数，环形波纹结构）
 *
 * 公式: f(x) = 1 - cos(2π·√(Σx_i²)) + 0.1·√(Σx_i²)
 *
 * 特点: 多峰，环形对称，全局最优在原点
 * 用途: 测试算法处理环形波纹地形的能力
 *
 * 搜索空间: [-100, 100]^n
 * 全局最优: f(0,0,...,0) = 0
 *
 * 参考文献: Salomon, R. (1996)
 *
 * @author LSCBO Research Team
 */
public class Salomon extends BenchmarkFunction {

    /**
     * 构造函数（30维）
     */
    public Salomon() {
        this(30);
    }

    /**
     * 构造函数（自定义维度）
     *
     * @param dimensions 问题维度
     */
    public Salomon(int dimensions) {
        super(
            18,                         // ID: F18
            "Salomon",                  // 名称
            dimensions,                 // 维度
            -100.0,                     // 下界
            100.0,                      // 上界
            0.0,                        // 全局最优值
            FunctionType.MULTIMODAL     // 类型：多峰
        );
    }

    @Override
    public double evaluate(double[] x) {
        validateDimensions(x);

        // 计算√(Σx_i²)
        double sum_squares = 0.0;
        for (int i = 0; i < dimensions; i++) {
            sum_squares += x[i] * x[i];
        }
        double sqrt_sum = Math.sqrt(sum_squares);

        // f(x) = 1 - cos(2π·√(Σx_i²)) + 0.1·√(Σx_i²)
        return 1.0 - Math.cos(2.0 * Math.PI * sqrt_sum) + 0.1 * sqrt_sum;
    }

    /**
     * 获取函数详细信息
     *
     * @return 函数描述
     */
    public String getDetails() {
        return "Salomon Function: f(x) = 1 - cos(2π·√(Σx_i²)) + 0.1·√(Σx_i²)\n" +
               "Characteristics: Multimodal, Circular symmetry, Ring-like ripples\n" +
               "Difficulty: Medium (radial oscillations)\n" +
               "Usage: Test handling circular ripple terrains\n" +
               getDescription();
    }
}

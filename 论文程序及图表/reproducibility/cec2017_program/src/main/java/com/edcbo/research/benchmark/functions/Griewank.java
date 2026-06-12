package com.edcbo.research.benchmark.functions;

import com.edcbo.research.benchmark.BenchmarkFunction;

/**
 * F4: Griewank函数（全局-局部结构函数）
 *
 * 公式: f(x) = 1 + Σ(x_i²/4000) - Π[cos(x_i/√i)]，i=1 to n
 * 特点: 全局为抛物线，局部有大量余弦波动
 * 用途: 测试算法平衡全局和局部搜索的能力
 *
 * 搜索空间: [-600, 600]^n
 * 全局最优: f(0,0,...,0) = 0
 *
 * @author LSCBO Research Team
 */
public class Griewank extends BenchmarkFunction {

    /**
     * 构造函数（30维）
     */
    public Griewank() {
        this(30);
    }

    /**
     * 构造函数（自定义维度）
     *
     * @param dimensions 问题维度
     */
    public Griewank(int dimensions) {
        super(
            4,                          // ID: F4
            "Griewank",                 // 名称
            dimensions,                 // 维度
            -600.0,                     // 下界
            600.0,                      // 上界
            0.0,                        // 全局最优值
            FunctionType.MULTIMODAL     // 类型：多峰
        );
    }

    @Override
    public double evaluate(double[] x) {
        validateDimensions(x);

        double sum = 0.0;
        double product = 1.0;

        for (int i = 0; i < dimensions; i++) {
            sum += x[i] * x[i] / 4000.0;
            product *= Math.cos(x[i] / Math.sqrt(i + 1));  // i从0开始，索引从1开始
        }

        return 1.0 + sum - product;
    }

    /**
     * 获取函数详细信息
     *
     * @return 函数描述
     */
    public String getDetails() {
        return "Griewank Function: f(x) = 1 + Σ(x_i²/4000) - Π[cos(x_i/√i)]\n" +
               "Characteristics: Multimodal, Non-Separable, Global parabola + Local waves\n" +
               "Difficulty: Medium-Hard\n" +
               "Usage: Test global-local search balance\n" +
               getDescription();
    }
}

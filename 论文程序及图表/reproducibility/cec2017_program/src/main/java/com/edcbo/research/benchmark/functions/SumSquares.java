package com.edcbo.research.benchmark.functions;

import com.edcbo.research.benchmark.BenchmarkFunction;

/**
 * F9: Sum Squares函数（加权平方和）
 *
 * 公式: f(x) = Σ(i * x_i²)，i=1 to n
 * 特点: 单峰，非对称（后面维度权重更大）
 * 用途: 测试算法处理维度权重差异的能力
 *
 * 搜索空间: [-10, 10]^n
 * 全局最优: f(0,0,...,0) = 0
 *
 * @author LSCBO Research Team
 */
public class SumSquares extends BenchmarkFunction {

    /**
     * 构造函数（30维）
     */
    public SumSquares() {
        this(30);
    }

    /**
     * 构造函数（自定义维度）
     *
     * @param dimensions 问题维度
     */
    public SumSquares(int dimensions) {
        super(
            9,                      // ID: F9
            "Sum Squares",          // 名称
            dimensions,             // 维度
            -10.0,                  // 下界
            10.0,                   // 上界
            0.0,                    // 全局最优值
            FunctionType.UNIMODAL   // 类型：单峰
        );
    }

    @Override
    public double evaluate(double[] x) {
        validateDimensions(x);

        double sum = 0.0;
        for (int i = 0; i < dimensions; i++) {
            sum += (i + 1) * x[i] * x[i];  // 注意：i从0开始，权重从1开始
        }

        return sum;
    }

    /**
     * 获取函数详细信息
     *
     * @return 函数描述
     */
    public String getDetails() {
        return "Sum Squares Function: f(x) = Σ(i * x_i²)\n" +
               "Characteristics: Unimodal, Non-Separable (weighted), Asymmetric\n" +
               "Difficulty: Easy-Medium\n" +
               "Usage: Test handling of dimension weights\n" +
               getDescription();
    }
}

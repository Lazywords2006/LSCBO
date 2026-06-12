package com.edcbo.research.benchmark.functions;

import com.edcbo.research.benchmark.BenchmarkFunction;

/**
 * F12: Dixon-Price函数（单峰函数，高度非可分离）
 *
 * 公式: f(x) = (x₁-1)² + Σ[i(2x_i² - x_{i-1})²]，i=2 to n
 *
 * 特点: 单峰，高度非可分离，强相关性
 * 用途: 测试算法处理强变量相关性的能力
 *
 * 搜索空间: [-10, 10]^n
 * 全局最优: x_i = 2^(-(2^i-2)/2^i)，f(x*) = 0
 *
 * 参考文献: Dixon, L. C. W., & Price, R. C. (1977)
 *
 * @author LSCBO Research Team
 */
public class DixonPrice extends BenchmarkFunction {

    /**
     * 构造函数（30维）
     */
    public DixonPrice() {
        this(30);
    }

    /**
     * 构造函数（自定义维度）
     *
     * @param dimensions 问题维度
     */
    public DixonPrice(int dimensions) {
        super(
            12,                         // ID: F12
            "Dixon-Price",              // 名称
            dimensions,                 // 维度
            -10.0,                      // 下界
            10.0,                       // 上界
            0.0,                        // 全局最优值
            FunctionType.UNIMODAL       // 类型：单峰
        );
    }

    @Override
    public double evaluate(double[] x) {
        validateDimensions(x);

        // 第一项: (x₁-1)²
        double term1 = (x[0] - 1.0) * (x[0] - 1.0);

        // 求和项: Σ[i(2x_i² - x_{i-1})²]
        double sum = 0.0;
        for (int i = 1; i < dimensions; i++) {
            double inner = 2.0 * x[i] * x[i] - x[i - 1];
            sum += (i + 1) * inner * inner;
        }

        return term1 + sum;
    }

    /**
     * 获取函数详细信息
     *
     * @return 函数描述
     */
    public String getDetails() {
        return "Dixon-Price Function: f(x) = (x₁-1)² + Σ[i(2x_i² - x_{i-1})²]\n" +
               "Characteristics: Unimodal, Highly non-separable, Strong variable correlation\n" +
               "Difficulty: Medium (requires handling variable dependencies)\n" +
               "Usage: Test handling strong variable correlations\n" +
               getDescription();
    }
}

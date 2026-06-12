package com.edcbo.research.benchmark.functions;

import com.edcbo.research.benchmark.BenchmarkFunction;

/**
 * F22: Expanded Schaffer's F6函数（扩展的多峰函数）
 *
 * 公式: f(x) = Σ[g(x_i, x_{i+1})]，i=1 to n-1，加上 g(x_n, x_1)
 * 其中 g(x,y) = 0.5 + (sin²(√(x²+y²)) - 0.5) / (1 + 0.001·(x²+y²))²
 *
 * 特点: 高度多峰，非可分离，扩展了经典Schaffer F6
 * 用途: 测试算法处理复杂耦合变量的能力
 *
 * 搜索空间: [-100, 100]^n
 * 全局最优: f(0,0,...,0) = 0
 *
 * 参考文献: CEC2017 Test Suite
 *
 * @author LSCBO Research Team
 */
public class ExpandedSchafferF6 extends BenchmarkFunction {

    /**
     * 构造函数（30维）
     */
    public ExpandedSchafferF6() {
        this(30);
    }

    /**
     * 构造函数（自定义维度）
     *
     * @param dimensions 问题维度
     */
    public ExpandedSchafferF6(int dimensions) {
        super(
            22,                         // ID: F22
            "Expanded Schaffer F6",     // 名称
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

        double sum = 0.0;

        // 对每对相邻变量计算g(x_i, x_{i+1})
        for (int i = 0; i < dimensions - 1; i++) {
            sum += schafferF6(x[i], x[i + 1]);
        }

        // 添加g(x_n, x_1)使函数循环
        sum += schafferF6(x[dimensions - 1], x[0]);

        return sum;
    }

    /**
     * Schaffer F6子函数
     *
     * g(x,y) = 0.5 + (sin²(√(x²+y²)) - 0.5) / (1 + 0.001·(x²+y²))²
     *
     * @param x 第一个变量
     * @param y 第二个变量
     * @return g(x,y)的值
     */
    private double schafferF6(double x, double y) {
        double sum_squares = x * x + y * y;
        double sqrt_sum = Math.sqrt(sum_squares);

        double sin_term = Math.sin(sqrt_sum);
        double numerator = sin_term * sin_term - 0.5;

        double denominator_term = 1.0 + 0.001 * sum_squares;
        double denominator = denominator_term * denominator_term;

        return 0.5 + numerator / denominator;
    }

    /**
     * 获取函数详细信息
     *
     * @return 函数描述
     */
    public String getDetails() {
        return "Expanded Schaffer's F6: f(x) = Σ[g(x_i, x_{i+1})] + g(x_n, x_1)\n" +
               "where: g(x,y) = 0.5 + (sin²(√(x²+y²)) - 0.5) / (1 + 0.001·(x²+y²))²\n" +
               "Characteristics: Highly multimodal, Non-separable, Variable coupling\n" +
               "Difficulty: Hard (complex variable interactions)\n" +
               "Usage: Test handling coupled variables\n" +
               getDescription();
    }
}

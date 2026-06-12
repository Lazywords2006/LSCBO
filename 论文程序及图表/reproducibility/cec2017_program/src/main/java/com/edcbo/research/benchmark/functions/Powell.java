package com.edcbo.research.benchmark.functions;

import com.edcbo.research.benchmark.BenchmarkFunction;

/**
 * F10: Powell函数（块状二次函数）
 *
 * 公式: f(x) = Σ[(x_{4i-3} + 10*x_{4i-2})² + 5*(x_{4i-1} - x_{4i})² +
 *               (x_{4i-2} - 2*x_{4i-1})⁴ + 10*(x_{4i-3} - x_{4i})⁴]
 *       其中 i=1 to n/4（要求维度为4的倍数）
 * 特点: 分块结构，每4个变量一组
 * 用途: 测试算法处理块状结构问题的能力
 *
 * 搜索空间: [-4, 5]^n
 * 全局最优: f(0,0,...,0) = 0
 *
 * @author LSCBO Research Team
 */
public class Powell extends BenchmarkFunction {

    /**
     * 构造函数（28维，最接近30的4的倍数）
     */
    public Powell() {
        this(28);
    }

    /**
     * 构造函数（自定义维度，必须是4的倍数）
     *
     * @param dimensions 问题维度（必须是4的倍数）
     * @throws IllegalArgumentException 如果维度不是4的倍数
     */
    public Powell(int dimensions) {
        super(
            10,                         // ID: F10
            "Powell",                   // 名称
            validatePowellDimensions(dimensions),  // 维度验证
            -4.0,                       // 下界
            5.0,                        // 上界
            0.0,                        // 全局最优值
            FunctionType.QUADRATIC      // 类型：二次
        );
    }

    /**
     * 验证维度必须是4的倍数
     */
    private static int validatePowellDimensions(int dimensions) {
        if (dimensions % 4 != 0) {
            throw new IllegalArgumentException(
                "Powell function requires dimensions to be a multiple of 4, got: " + dimensions
            );
        }
        return dimensions;
    }

    @Override
    public double evaluate(double[] x) {
        validateDimensions(x);

        double sum = 0.0;
        int numBlocks = dimensions / 4;

        for (int i = 0; i < numBlocks; i++) {
            int base = i * 4;  // 每块的起始索引
            double x1 = x[base];
            double x2 = x[base + 1];
            double x3 = x[base + 2];
            double x4 = x[base + 3];

            double term1 = Math.pow(x1 + 10.0 * x2, 2);
            double term2 = 5.0 * Math.pow(x3 - x4, 2);
            double term3 = Math.pow(x2 - 2.0 * x3, 4);
            double term4 = 10.0 * Math.pow(x1 - x4, 4);

            sum += term1 + term2 + term3 + term4;
        }

        return sum;
    }

    /**
     * 获取函数详细信息
     *
     * @return 函数描述
     */
    public String getDetails() {
        return "Powell Function: Block-structured quadratic function\n" +
               "f(x) = Σ[(x_{4i-3}+10*x_{4i-2})² + 5*(x_{4i-1}-x_{4i})² + ...]\n" +
               "Characteristics: Quadratic, Block-structured (4 variables per block)\n" +
               "Difficulty: Medium\n" +
               "Usage: Test handling of block-structured problems\n" +
               "Note: Requires dimensions = 4k\n" +
               getDescription();
    }
}

package com.edcbo.research.benchmark.functions;

import com.edcbo.research.benchmark.BenchmarkFunction;

/**
 * F5: Six-Hump Camel函数（六峰驼背函数）
 *
 * 公式: f(x,y) = (4-2.1*x²+x⁴/3)*x² + x*y + (-4+4*y²)*y²
 * 特点: 2维函数，6个局部最优，2个全局最优（对称）
 * 用途: 测试算法在低维多峰问题上的搜索能力
 *
 * 搜索空间: x∈[-5,5], y∈[-5,5]
 * 全局最优: f(0.0898, -0.7126) = f(-0.0898, 0.7126) = -1.0316
 *
 * @author LSCBO Research Team
 */
public class SixHumpCamel extends BenchmarkFunction {

    /**
     * 构造函数（固定2维）
     */
    public SixHumpCamel() {
        super(
            5,                          // ID: F5
            "Six-Hump Camel",           // 名称
            2,                          // 维度：固定2维
            -5.0,                       // 下界
            5.0,                        // 上界
            -1.0316,                    // 全局最优值
            FunctionType.MULTIMODAL     // 类型：多峰
        );
    }

    @Override
    public double evaluate(double[] x) {
        validateDimensions(x);

        double x1 = x[0];
        double x2 = x[1];

        double term1 = (4.0 - 2.1 * x1 * x1 + Math.pow(x1, 4) / 3.0) * x1 * x1;
        double term2 = x1 * x2;
        double term3 = (-4.0 + 4.0 * x2 * x2) * x2 * x2;

        return term1 + term2 + term3;
    }

    /**
     * 获取函数详细信息
     *
     * @return 函数描述
     */
    public String getDetails() {
        return "Six-Hump Camel Function: f(x,y) = (4-2.1*x²+x⁴/3)*x² + x*y + (-4+4*y²)*y²\n" +
               "Characteristics: 2D, 6 local optima, 2 global optima (symmetric)\n" +
               "Difficulty: Medium\n" +
               "Usage: Test low-dimensional multimodal optimization\n" +
               getDescription();
    }
}

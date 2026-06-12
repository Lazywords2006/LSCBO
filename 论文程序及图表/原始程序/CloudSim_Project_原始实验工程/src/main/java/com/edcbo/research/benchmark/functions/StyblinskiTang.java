package com.edcbo.research.benchmark.functions;

import com.edcbo.research.benchmark.BenchmarkFunction;

/**
 * F16: Styblinski-Tang函数（多峰函数）
 *
 * 公式: f(x) = Σ[x_i⁴ - 16x_i² + 5x_i] / 2，i=1 to n
 *
 * 特点: 多峰，可分离，全局最优不在原点
 * 用途: 测试算法寻找非原点全局最优的能力
 *
 * 搜索空间: [-5, 5]^n
 * 全局最优: f(-2.903534,...,-2.903534) = -39.16617n
 *
 * 参考文献: Styblinski, M., & Tang, T. (1990)
 *
 * @author LSCBO Research Team
 */
public class StyblinskiTang extends BenchmarkFunction {

    /**
     * 构造函数（30维）
     */
    public StyblinskiTang() {
        this(30);
    }

    /**
     * 构造函数（自定义维度）
     *
     * @param dimensions 问题维度
     */
    public StyblinskiTang(int dimensions) {
        super(
            16,                         // ID: F16
            "Styblinski-Tang",          // 名称
            dimensions,                 // 维度
            -5.0,                       // 下界
            5.0,                        // 上界
            -39.16617 * dimensions,     // 全局最优值（随维度线性增长）
            FunctionType.MULTIMODAL     // 类型：多峰
        );
    }

    @Override
    public double evaluate(double[] x) {
        validateDimensions(x);

        double sum = 0.0;
        for (int i = 0; i < dimensions; i++) {
            double xi2 = x[i] * x[i];
            double xi4 = xi2 * xi2;
            sum += xi4 - 16.0 * xi2 + 5.0 * x[i];
        }

        return sum / 2.0;
    }

    /**
     * 获取函数详细信息
     *
     * @return 函数描述
     */
    public String getDetails() {
        return "Styblinski-Tang Function: f(x) = Σ[x_i⁴ - 16x_i² + 5x_i] / 2\n" +
               "Characteristics: Multimodal, Separable, Global optimum not at origin\n" +
               "Difficulty: Medium (requires finding x* = -2.903534)\n" +
               "Usage: Test finding non-origin global optimum\n" +
               getDescription();
    }
}

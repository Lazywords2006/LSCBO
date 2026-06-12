package com.edcbo.research.benchmark.functions;

import com.edcbo.research.benchmark.BenchmarkFunction;

/**
 * F23: Weierstrass函数（连续但处处不可导的分形函数）
 *
 * 公式: f(x) = Σ[Σ[a^k·cos(2π·b^k·(x_i+0.5))]] - n·Σ[a^k·cos(π·b^k)]
 * 其中 a = 0.5, b = 3, k从0到kmax（通常kmax=20）
 *
 * 特点: 连续但处处不可导，分形结构，极度多峰
 * 用途: 测试算法在无梯度信息下的搜索能力
 *
 * 搜索空间: [-0.5, 0.5]^n
 * 全局最优: f(0,0,...,0) = 0
 *
 * 参考文献: Weierstrass, K. (1872) / CEC2017
 *
 * @author LSCBO Research Team
 */
public class Weierstrass extends BenchmarkFunction {

    private static final double A = 0.5;      // 振幅衰减系数
    private static final double B = 3.0;      // 频率增长系数
    private static final int KMAX = 20;       // 级数项数

    /**
     * 构造函数（30维）
     */
    public Weierstrass() {
        this(30);
    }

    /**
     * 构造函数（自定义维度）
     *
     * @param dimensions 问题维度
     */
    public Weierstrass(int dimensions) {
        super(
            23,                         // ID: F23
            "Weierstrass",              // 名称
            dimensions,                 // 维度
            -0.5,                       // 下界
            0.5,                        // 上界
            0.0,                        // 全局最优值
            FunctionType.MULTIMODAL     // 类型：多峰
        );
    }

    @Override
    public double evaluate(double[] x) {
        validateDimensions(x);

        double sum1 = 0.0;

        // 第一项: Σ_i[Σ_k[a^k·cos(2π·b^k·(x_i+0.5))]]
        for (int i = 0; i < dimensions; i++) {
            double inner_sum = 0.0;
            for (int k = 0; k <= KMAX; k++) {
                double a_k = Math.pow(A, k);
                double b_k = Math.pow(B, k);
                inner_sum += a_k * Math.cos(2.0 * Math.PI * b_k * (x[i] + 0.5));
            }
            sum1 += inner_sum;
        }

        // 第二项: n·Σ_k[a^k·cos(π·b^k)]
        double sum2 = 0.0;
        for (int k = 0; k <= KMAX; k++) {
            double a_k = Math.pow(A, k);
            double b_k = Math.pow(B, k);
            sum2 += a_k * Math.cos(Math.PI * b_k);
        }
        sum2 *= dimensions;

        return sum1 - sum2;
    }

    /**
     * 获取函数详细信息
     *
     * @return 函数描述
     */
    public String getDetails() {
        return "Weierstrass Function: Continuous but nowhere differentiable fractal\n" +
               "f(x) = Σ[Σ[a^k·cos(2π·b^k·(x_i+0.5))]] - n·Σ[a^k·cos(π·b^k)]\n" +
               "where: a=0.5, b=3, k=0 to 20\n" +
               "Characteristics: Continuous, Nowhere differentiable, Fractal, Highly multimodal\n" +
               "Difficulty: Very Hard (no gradient information)\n" +
               "Usage: Test gradient-free optimization capability\n" +
               getDescription();
    }
}

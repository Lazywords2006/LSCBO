package com.edcbo.research.benchmark.functions;

import com.edcbo.research.benchmark.BenchmarkFunction;

/**
 * F27: Pathological函数（病态振荡函数）
 *
 * 公式: f(x) = Σ[0.5 + (sin²(√(100·x_i² + x_{i+1}²)) - 0.5) / (1 + 0.001·(x_i² - 2·x_i·x_{i+1} + x_{i+1}²)²)]
 * 其中 i=1 to n-1
 *
 * 特点: 多峰，病态，振荡剧烈，对初始点敏感
 * 用途: 测试算法应对病态振荡地形的鲁棒性
 *
 * 搜索空间: [-100, 100]^n
 * 全局最优: f(0,0,...,0) ≈ 0
 *
 * @author LSCBO Research Team
 */
public class Pathological extends BenchmarkFunction {

    /**
     * 构造函数（30维）
     */
    public Pathological() {
        this(30);
    }

    /**
     * 构造函数（自定义维度）
     *
     * @param dimensions 问题维度
     */
    public Pathological(int dimensions) {
        super(
            27,                         // ID: F27
            "Pathological",             // 名称
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

        // 对每对相邻变量计算
        for (int i = 0; i < dimensions - 1; i++) {
            double xi2 = x[i] * x[i];
            double xip12 = x[i + 1] * x[i + 1];

            // 分子: sin²(√(100·x_i² + x_{i+1}²)) - 0.5
            double sqrt_term = Math.sqrt(100.0 * xi2 + xip12);
            double sin_term = Math.sin(sqrt_term);
            double numerator = sin_term * sin_term - 0.5;

            // 分母: 1 + 0.001·(x_i² - 2·x_i·x_{i+1} + x_{i+1}²)²
            double diff = xi2 - 2.0 * x[i] * x[i + 1] + xip12;
            double denominator = 1.0 + 0.001 * diff * diff;

            sum += 0.5 + numerator / denominator;
        }

        return sum;
    }

    /**
     * 获取函数详细信息
     *
     * @return 函数描述
     */
    public String getDetails() {
        return "Pathological Function: Ill-conditioned oscillating function\n" +
               "f(x) = Σ[0.5 + (sin²(√(100·x_i² + x_{i+1}²)) - 0.5) / (1 + 0.001·(...)²)]\n" +
               "Characteristics: Multimodal, Ill-conditioned, Severe oscillations\n" +
               "Difficulty: Very Hard (pathological landscape)\n" +
               "Usage: Test robustness against pathological oscillations\n" +
               getDescription();
    }
}

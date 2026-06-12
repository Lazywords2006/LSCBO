package com.edcbo.research.benchmark.functions;

import com.edcbo.research.benchmark.BenchmarkFunction;

/**
 * F15: Michalewicz函数（多峰函数，陡峭山谷）
 *
 * 公式: f(x) = -Σ[sin(x_i)·sin²ᵐ(i·x_i²/π)]，i=1 to n
 * 其中 m=10 为陡峭度参数
 *
 * 特点: 多峰，陡峭山谷，d维空间有d!个局部最优
 * 用途: 测试算法在陡峭地形的搜索能力
 *
 * 搜索空间: [0, π]^n
 * 全局最优:
 *   - d=2: f(2.20,1.57) ≈ -1.8013
 *   - d=5: f* ≈ -4.687658
 *   - d=10: f* ≈ -9.66015
 *   - d=30: f* ≈ -29.6 (估计值)
 *
 * 参考文献: Michalewicz, Z. (1996)
 *
 * @author LSCBO Research Team
 */
public class Michalewicz extends BenchmarkFunction {

    private static final int M = 10;  // 陡峭度参数

    /**
     * 构造函数（30维）
     */
    public Michalewicz() {
        this(30);
    }

    /**
     * 构造函数（自定义维度）
     *
     * @param dimensions 问题维度
     */
    public Michalewicz(int dimensions) {
        super(
            15,                         // ID: F15
            "Michalewicz",              // 名称
            dimensions,                 // 维度
            0.0,                        // 下界
            Math.PI,                    // 上界
            -29.6,                      // 全局最优值（30维估计）
            FunctionType.MULTIMODAL     // 类型：多峰
        );
    }

    @Override
    public double evaluate(double[] x) {
        validateDimensions(x);

        double sum = 0.0;
        for (int i = 0; i < dimensions; i++) {
            double sin_xi = Math.sin(x[i]);
            double sin_inner = Math.sin((i + 1) * x[i] * x[i] / Math.PI);
            double power_term = Math.pow(sin_inner, 2 * M);
            sum += sin_xi * power_term;
        }

        return -sum;
    }

    /**
     * 获取函数详细信息
     *
     * @return 函数描述
     */
    public String getDetails() {
        return "Michalewicz Function: f(x) = -Σ[sin(x_i)·sin²ᵐ(i·x_i²/π)], m=10\n" +
               "Characteristics: Highly multimodal, Steep valleys, d! local optima\n" +
               "Difficulty: Hard (steep landscape with many optima)\n" +
               "Usage: Test search capability in steep terrains\n" +
               getDescription();
    }
}

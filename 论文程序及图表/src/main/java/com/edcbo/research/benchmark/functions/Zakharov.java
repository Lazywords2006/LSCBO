package com.edcbo.research.benchmark.functions;

import com.edcbo.research.benchmark.BenchmarkFunction;

/**
 * F8: Zakharov函数（平方和+四次方和）
 *
 * 公式: f(x) = Σ(x_i²) + [Σ(0.5*i*x_i)]² + [Σ(0.5*i*x_i)]⁴
 * 特点: 单峰，带交叉项，比Sphere复杂
 * 用途: 测试算法处理高阶交叉项的能力
 *
 * 搜索空间: [-5, 10]^n
 * 全局最优: f(0,0,...,0) = 0
 *
 * @author LSCBO Research Team
 */
public class Zakharov extends BenchmarkFunction {

    /**
     * 构造函数（30维）
     */
    public Zakharov() {
        this(30);
    }

    /**
     * 构造函数（自定义维度）
     *
     * @param dimensions 问题维度
     */
    public Zakharov(int dimensions) {
        super(
            8,                      // ID: F8
            "Zakharov",             // 名称
            dimensions,             // 维度
            -5.0,                   // 下界
            10.0,                   // 上界
            0.0,                    // 全局最优值
            FunctionType.UNIMODAL   // 类型：单峰
        );
    }

    @Override
    public double evaluate(double[] x) {
        validateDimensions(x);

        double sum1 = 0.0;  // Σ(x_i²)
        double sum2 = 0.0;  // Σ(0.5*i*x_i)

        for (int i = 0; i < dimensions; i++) {
            sum1 += x[i] * x[i];
            sum2 += 0.5 * (i + 1) * x[i];  // i从0开始，索引从1开始
        }

        double result = sum1 + Math.pow(sum2, 2) + Math.pow(sum2, 4);

        return result;
    }

    /**
     * 获取函数详细信息
     *
     * @return 函数描述
     */
    public String getDetails() {
        return "Zakharov Function: f(x) = Σ(x_i²) + [Σ(0.5*i*x_i)]² + [Σ(0.5*i*x_i)]⁴\n" +
               "Characteristics: Unimodal, Non-Separable, High-order terms\n" +
               "Difficulty: Medium\n" +
               "Usage: Test handling of cross-terms and high-order interactions\n" +
               getDescription();
    }
}

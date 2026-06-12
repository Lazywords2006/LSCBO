package com.edcbo.research.benchmark.functions;

import com.edcbo.research.benchmark.BenchmarkFunction;

/**
 * F21: HappyCat函数（混合型多峰函数）
 *
 * 公式: f(x) = |Σx_i² - n|^α + (0.5·Σx_i² + Σx_i)/n + 0.5
 * 其中 α = 1/8
 *
 * 特点: 混合型，全局结构简单但局部细节复杂
 * 用途: 测试算法在混合地形的搜索能力
 *
 * 搜索空间: [-2, 2]^n
 * 全局最优: f(-1,-1,...,-1) ≈ 0
 *
 * 参考文献: CEC2017 Test Suite
 *
 * @author LSCBO Research Team
 */
public class HappyCat extends BenchmarkFunction {

    private static final double ALPHA = 1.0 / 8.0;  // 指数参数

    /**
     * 构造函数（30维）
     */
    public HappyCat() {
        this(30);
    }

    /**
     * 构造函数（自定义维度）
     *
     * @param dimensions 问题维度
     */
    public HappyCat(int dimensions) {
        super(
            21,                         // ID: F21
            "HappyCat",                 // 名称
            dimensions,                 // 维度
            -2.0,                       // 下界
            2.0,                        // 上界
            0.0,                        // 全局最优值
            FunctionType.MULTIMODAL     // 类型：多峰
        );
    }

    @Override
    public double evaluate(double[] x) {
        validateDimensions(x);

        // 计算Σx_i²
        double sum_squares = 0.0;
        for (int i = 0; i < dimensions; i++) {
            sum_squares += x[i] * x[i];
        }

        // 计算Σx_i
        double sum_x = 0.0;
        for (int i = 0; i < dimensions; i++) {
            sum_x += x[i];
        }

        // 第一项: |Σx_i² - n|^α
        double term1 = Math.pow(Math.abs(sum_squares - dimensions), ALPHA);

        // 第二项: (0.5·Σx_i² + Σx_i)/n
        double term2 = (0.5 * sum_squares + sum_x) / dimensions;

        // f(x) = term1 + term2 + 0.5
        return term1 + term2 + 0.5;
    }

    /**
     * 获取函数详细信息
     *
     * @return 函数描述
     */
    public String getDetails() {
        return "HappyCat Function: f(x) = |Σx_i² - n|^(1/8) + (0.5·Σx_i² + Σx_i)/n + 0.5\n" +
               "Characteristics: Hybrid, Simple global structure with complex local details\n" +
               "Difficulty: Medium (mixed terrain)\n" +
               "Usage: Test handling hybrid terrains\n" +
               getDescription();
    }
}

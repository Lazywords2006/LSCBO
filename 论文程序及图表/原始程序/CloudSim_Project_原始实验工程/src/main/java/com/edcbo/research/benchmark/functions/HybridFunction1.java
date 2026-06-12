package com.edcbo.research.benchmark.functions;

import com.edcbo.research.benchmark.BenchmarkFunction;

/**
 * F29: Hybrid Function 1（Sphere + Rastrigin混合函数）
 *
 * 公式: f(x) = w1·Sphere(x[0:n/2]) + w2·Rastrigin(x[n/2:n])
 * 其中 w1 = 0.4, w2 = 0.6
 *
 * 特点: 混合型，前半部分单峰，后半部分多峰
 * 用途: 测试算法处理混合地形的能力
 *
 * 搜索空间: [-100, 100]^n
 * 全局最优: f(0,0,...,0) = 0
 *
 * @author LSCBO Research Team
 */
public class HybridFunction1 extends BenchmarkFunction {

    private static final double W1 = 0.4;  // Sphere权重
    private static final double W2 = 0.6;  // Rastrigin权重

    /**
     * 构造函数（30维）
     */
    public HybridFunction1() {
        this(30);
    }

    /**
     * 构造函数（自定义维度）
     *
     * @param dimensions 问题维度
     */
    public HybridFunction1(int dimensions) {
        super(
            29,                         // ID: F29
            "Hybrid Function 1",        // 名称
            dimensions,                 // 维度
            -100.0,                     // 下界
            100.0,                      // 上界
            0.0,                        // 全局最优值
            FunctionType.MULTIMODAL     // 类型：多峰（混合）
        );
    }

    @Override
    public double evaluate(double[] x) {
        validateDimensions(x);

        int splitPoint = dimensions / 2;

        // 前半部分：Sphere函数
        double sphere = 0.0;
        for (int i = 0; i < splitPoint; i++) {
            sphere += x[i] * x[i];
        }

        // 后半部分：Rastrigin函数
        double rastrigin = 10.0 * (dimensions - splitPoint);
        for (int i = splitPoint; i < dimensions; i++) {
            rastrigin += x[i] * x[i] - 10.0 * Math.cos(2.0 * Math.PI * x[i]);
        }

        // 加权组合
        return W1 * sphere + W2 * rastrigin;
    }

    /**
     * 获取函数详细信息
     *
     * @return 函数描述
     */
    public String getDetails() {
        return "Hybrid Function 1: f(x) = 0.4·Sphere(x[0:n/2]) + 0.6·Rastrigin(x[n/2:n])\n" +
               "Characteristics: Hybrid, First half unimodal, Second half multimodal\n" +
               "Difficulty: Medium-Hard (mixed terrain)\n" +
               "Usage: Test handling hybrid unimodal+multimodal landscapes\n" +
               getDescription();
    }
}

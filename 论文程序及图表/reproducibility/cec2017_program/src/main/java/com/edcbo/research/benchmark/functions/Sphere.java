package com.edcbo.research.benchmark.functions;

import com.edcbo.research.benchmark.BenchmarkFunction;

/**
 * F1: Sphere函数（最经典的单峰函数）
 *
 * 公式: f(x) = Σ(x_i²)，i=1 to n
 * 特点: 凸函数，单峰，对称，可分离
 * 用途: 测试算法收敛速度
 *
 * 搜索空间: [-100, 100]^n
 * 全局最优: f(0,0,...,0) = 0
 *
 * @author LSCBO Research Team
 */
public class Sphere extends BenchmarkFunction {

    /**
     * 构造函数（30维）
     */
    public Sphere() {
        this(30);
    }

    /**
     * 构造函数（自定义维度）
     *
     * @param dimensions 问题维度
     */
    public Sphere(int dimensions) {
        super(
            1,                      // ID: F1
            "Sphere",               // 名称
            dimensions,             // 维度
            -100.0,                 // 下界
            100.0,                  // 上界
            0.0,                    // 全局最优值
            FunctionType.UNIMODAL   // 类型：单峰
        );
    }

    @Override
    public double evaluate(double[] x) {
        validateDimensions(x);

        double sum = 0.0;
        for (int i = 0; i < dimensions; i++) {
            sum += x[i] * x[i];
        }

        return sum;
    }

    /**
     * 获取函数详细信息
     *
     * @return 函数描述
     */
    public String getDetails() {
        return "Sphere Function: f(x) = Σ(x_i²)\n" +
               "Characteristics: Convex, Unimodal, Separable, Symmetric\n" +
               "Difficulty: Easy\n" +
               "Usage: Test convergence speed\n" +
               getDescription();
    }
}

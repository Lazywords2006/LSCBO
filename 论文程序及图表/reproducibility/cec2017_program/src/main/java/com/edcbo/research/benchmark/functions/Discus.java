package com.edcbo.research.benchmark.functions;

import com.edcbo.research.benchmark.BenchmarkFunction;

/**
 * F25: Discus函数（CEC2017标准函数，铁饼型）
 *
 * 公式: f(x) = 10^6·x_1² + Σ(x_i²)，i=2 to n
 *
 * 特点: 单峰，病态，第一维被严重放大
 * 用途: 测试算法处理不平衡缩放的能力
 *
 * 搜索空间: [-100, 100]^n
 * 全局最优: f(0,0,...,0) = 0
 *
 * 参考文献: CEC2017 Test Suite
 *
 * @author LSCBO Research Team
 */
public class Discus extends BenchmarkFunction {

    private static final double SCALE_FACTOR = 1e6;  // 放大因子

    /**
     * 构造函数（30维）
     */
    public Discus() {
        this(30);
    }

    /**
     * 构造函数（自定义维度）
     *
     * @param dimensions 问题维度
     */
    public Discus(int dimensions) {
        super(
            25,                         // ID: F25
            "Discus",                   // 名称
            dimensions,                 // 维度
            -100.0,                     // 下界
            100.0,                      // 上界
            0.0,                        // 全局最优值
            FunctionType.UNIMODAL       // 类型：单峰
        );
    }

    @Override
    public double evaluate(double[] x) {
        validateDimensions(x);

        // 第一项: 10^6·x_1² （第一维被严重放大）
        double term1 = SCALE_FACTOR * x[0] * x[0];

        // 第二项: Σ(x_i²)，i=2 to n
        double term2 = 0.0;
        for (int i = 1; i < dimensions; i++) {
            term2 += x[i] * x[i];
        }

        return term1 + term2;
    }

    /**
     * 获取函数详细信息
     *
     * @return 函数描述
     */
    public String getDetails() {
        return "Discus Function: f(x) = 10^6·x_1² + Σ(x_i²)\n" +
               "Characteristics: Unimodal, Ill-conditioned, First dimension heavily scaled\n" +
               "Difficulty: Hard (unbalanced scaling)\n" +
               "Usage: Test handling unbalanced variable scaling\n" +
               getDescription();
    }
}

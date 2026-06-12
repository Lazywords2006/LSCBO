package com.edcbo.research.benchmark.functions;

import com.edcbo.research.benchmark.BenchmarkFunction;

/**
 * F24: Bent Cigar函数（CEC2017标准函数，病态函数）
 *
 * 公式: f(x) = x_1² + 10^6·Σ(x_i²)，i=2 to n
 *
 * 特点: 单峰，严重病态（ill-conditioned），搜索方向敏感
 * 用途: 测试算法处理病态问题的能力
 *
 * 搜索空间: [-100, 100]^n
 * 全局最优: f(0,0,...,0) = 0
 *
 * 参考文献: CEC2017 Test Suite
 *
 * @author LSCBO Research Team
 */
public class BentCigar extends BenchmarkFunction {

    private static final double CONDITION_NUMBER = 1e6;  // 条件数

    /**
     * 构造函数（30维）
     */
    public BentCigar() {
        this(30);
    }

    /**
     * 构造函数（自定义维度）
     *
     * @param dimensions 问题维度
     */
    public BentCigar(int dimensions) {
        super(
            24,                         // ID: F24
            "Bent Cigar",               // 名称
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

        // 第一项: x_1²
        double term1 = x[0] * x[0];

        // 第二项: 10^6·Σ(x_i²)，i=2 to n
        double term2 = 0.0;
        for (int i = 1; i < dimensions; i++) {
            term2 += x[i] * x[i];
        }
        term2 *= CONDITION_NUMBER;

        return term1 + term2;
    }

    /**
     * 获取函数详细信息
     *
     * @return 函数描述
     */
    public String getDetails() {
        return "Bent Cigar Function: f(x) = x_1² + 10^6·Σ(x_i²)\n" +
               "Characteristics: Unimodal, Severely ill-conditioned, Direction sensitive\n" +
               "Difficulty: Hard (condition number = 10^6)\n" +
               "Usage: Test handling ill-conditioned problems\n" +
               getDescription();
    }
}

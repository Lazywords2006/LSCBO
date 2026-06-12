package com.edcbo.research.benchmark.functions;

import com.edcbo.research.benchmark.BenchmarkFunction;

/**
 * F26: High Conditioned Elliptic函数（高条件数椭圆函数）
 *
 * 公式: f(x) = Σ[10^(6·(i-1)/(n-1))·x_i²]，i=1 to n
 *
 * 特点: 单峰，高条件数（10^6），各维度缩放差异巨大
 * 用途: 测试算法处理极端病态问题的能力
 *
 * 搜索空间: [-100, 100]^n
 * 全局最优: f(0,0,...,0) = 0
 *
 * 参考文献: CEC2017 Test Suite
 *
 * @author LSCBO Research Team
 */
public class HighConditionedElliptic extends BenchmarkFunction {

    /**
     * 构造函数（30维）
     */
    public HighConditionedElliptic() {
        this(30);
    }

    /**
     * 构造函数（自定义维度）
     *
     * @param dimensions 问题维度
     */
    public HighConditionedElliptic(int dimensions) {
        super(
            26,                                 // ID: F26
            "High Conditioned Elliptic",        // 名称
            dimensions,                         // 维度
            -100.0,                             // 下界
            100.0,                              // 上界
            0.0,                                // 全局最优值
            FunctionType.UNIMODAL               // 类型：单峰
        );
    }

    @Override
    public double evaluate(double[] x) {
        validateDimensions(x);

        double sum = 0.0;
        for (int i = 0; i < dimensions; i++) {
            // 计算权重: 10^(6·(i-1)/(n-1))
            double exponent = 6.0 * i / (dimensions - 1);
            double weight = Math.pow(10.0, exponent);

            sum += weight * x[i] * x[i];
        }

        return sum;
    }

    /**
     * 获取函数详细信息
     *
     * @return 函数描述
     */
    public String getDetails() {
        return "High Conditioned Elliptic: f(x) = Σ[10^(6·(i-1)/(n-1))·x_i²]\n" +
               "Characteristics: Unimodal, High condition number (10^6), Extreme scaling\n" +
               "Difficulty: Very Hard (extreme ill-conditioning)\n" +
               "Usage: Test handling extreme ill-conditioned problems\n" +
               getDescription();
    }
}

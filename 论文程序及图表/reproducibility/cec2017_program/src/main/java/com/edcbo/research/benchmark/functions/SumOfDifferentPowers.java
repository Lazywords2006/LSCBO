package com.edcbo.research.benchmark.functions;

import com.edcbo.research.benchmark.BenchmarkFunction;

/**
 * F30: Sum of Different Powers函数
 *
 * 公式: f(x) = Σ|x_i|^{i+1}
 * 特点: 单峰函数，非对称
 * 用途: 测试算法对不同尺度的适应性
 *
 * 搜索空间: [-1, 1]^n
 * 全局最优: f(0,0,...,0) = 0
 *
 * @author LSCBO Research Team
 */
public class SumOfDifferentPowers extends BenchmarkFunction {

    /**
     * 构造函数（30维）
     */
    public SumOfDifferentPowers() {
        this(30);
    }

    /**
     * 构造函数（自定义维度）
     *
     * @param dimensions 问题维度
     */
    public SumOfDifferentPowers(int dimensions) {
        super(
                30, // ID: F30
                "Sum Of Different Powers", // 名称
                dimensions, // 维度
                -1.0, // 下界
                1.0, // 上界
                0.0, // 全局最优值
                FunctionType.UNIMODAL // 类型：单峰
        );
    }

    @Override
    public double evaluate(double[] x) {
        validateDimensions(x);

        double sum = 0.0;
        for (int i = 0; i < dimensions; i++) {
            sum += Math.pow(Math.abs(x[i]), i + 2);
        }

        return sum;
    }
}

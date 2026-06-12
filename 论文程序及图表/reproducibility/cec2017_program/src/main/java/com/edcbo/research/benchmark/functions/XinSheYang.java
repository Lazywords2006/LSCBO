package com.edcbo.research.benchmark.functions;

import com.edcbo.research.benchmark.BenchmarkFunction;

/**
 * F20: Xin-She Yang函数（复杂多峰函数）
 *
 * 公式: f(x) = (Σ|x_i|)·exp(-Σsin(x_i²))
 *
 * 特点: 多峰，非可分离，绝对值和指数项结合
 * 用途: 测试算法处理复杂地形的能力
 *
 * 搜索空间: [-2π, 2π]^n
 * 全局最优: f(0,0,...,0) = 0
 *
 * 参考文献: Yang, X. S. (2010)
 *
 * @author LSCBO Research Team
 */
public class XinSheYang extends BenchmarkFunction {

    /**
     * 构造函数（30维）
     */
    public XinSheYang() {
        this(30);
    }

    /**
     * 构造函数（自定义维度）
     *
     * @param dimensions 问题维度
     */
    public XinSheYang(int dimensions) {
        super(
            20,                         // ID: F20
            "Xin-She Yang",             // 名称
            dimensions,                 // 维度
            -2.0 * Math.PI,             // 下界
            2.0 * Math.PI,              // 上界
            0.0,                        // 全局最优值
            FunctionType.MULTIMODAL     // 类型：多峰
        );
    }

    @Override
    public double evaluate(double[] x) {
        validateDimensions(x);

        // 计算Σ|x_i|
        double sum_abs = 0.0;
        for (int i = 0; i < dimensions; i++) {
            sum_abs += Math.abs(x[i]);
        }

        // 计算Σsin(x_i²)
        double sum_sin = 0.0;
        for (int i = 0; i < dimensions; i++) {
            sum_sin += Math.sin(x[i] * x[i]);
        }

        // f(x) = (Σ|x_i|)·exp(-Σsin(x_i²))
        return sum_abs * Math.exp(-sum_sin);
    }

    /**
     * 获取函数详细信息
     *
     * @return 函数描述
     */
    public String getDetails() {
        return "Xin-She Yang Function: f(x) = (Σ|x_i|)·exp(-Σsin(x_i²))\n" +
               "Characteristics: Multimodal, Non-separable, Absolute and exponential terms\n" +
               "Difficulty: Hard (complex landscape)\n" +
               "Usage: Test handling complex terrains\n" +
               getDescription();
    }
}

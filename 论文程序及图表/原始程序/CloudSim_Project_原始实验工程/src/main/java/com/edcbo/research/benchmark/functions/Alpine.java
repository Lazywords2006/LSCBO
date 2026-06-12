package com.edcbo.research.benchmark.functions;

import com.edcbo.research.benchmark.BenchmarkFunction;

/**
 * F17: Alpine函数（多峰函数，锯齿状地形）
 *
 * 公式: f(x) = Σ|x_i·sin(x_i) + 0.1·x_i|，i=1 to n
 *
 * 特点: 多峰，锯齿状地形，绝对值函数
 * 用途: 测试算法处理非平滑地形的能力
 *
 * 搜索空间: [-10, 10]^n
 * 全局最优: f(0,0,...,0) = 0
 *
 * @author LSCBO Research Team
 */
public class Alpine extends BenchmarkFunction {

    /**
     * 构造函数（30维）
     */
    public Alpine() {
        this(30);
    }

    /**
     * 构造函数（自定义维度）
     *
     * @param dimensions 问题维度
     */
    public Alpine(int dimensions) {
        super(
            17,                         // ID: F17
            "Alpine",                   // 名称
            dimensions,                 // 维度
            -10.0,                      // 下界
            10.0,                       // 上界
            0.0,                        // 全局最优值
            FunctionType.MULTIMODAL     // 类型：多峰
        );
    }

    @Override
    public double evaluate(double[] x) {
        validateDimensions(x);

        double sum = 0.0;
        for (int i = 0; i < dimensions; i++) {
            sum += Math.abs(x[i] * Math.sin(x[i]) + 0.1 * x[i]);
        }

        return sum;
    }

    /**
     * 获取函数详细信息
     *
     * @return 函数描述
     */
    public String getDetails() {
        return "Alpine Function: f(x) = Σ|x_i·sin(x_i) + 0.1·x_i|\n" +
               "Characteristics: Multimodal, Jagged terrain, Absolute value\n" +
               "Difficulty: Medium (non-smooth landscape)\n" +
               "Usage: Test handling non-smooth terrains\n" +
               getDescription();
    }
}

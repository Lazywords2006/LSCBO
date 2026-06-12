package com.edcbo.research.benchmark.functions;

import com.edcbo.research.benchmark.BenchmarkFunction;

/**
 * F14: Quartic函数（带噪声的四次函数）
 *
 * 公式: f(x) = Σ[i·x_i⁴] + random[0,1)
 * 其中 random[0,1) 表示[0,1)范围的均匀随机噪声
 *
 * 特点: 单峰，噪声扰动，四次多项式
 * 用途: 测试算法抗噪声能力
 *
 * 搜索空间: [-1.28, 1.28]^n
 * 全局最优: f(0,0,...,0) ≈ 0 （噪声导致不确定）
 *
 * @author LSCBO Research Team
 */
public class Quartic extends BenchmarkFunction {

    /**
     * 构造函数（30维）
     */
    public Quartic() {
        this(30);
    }

    /**
     * 构造函数（自定义维度）
     *
     * @param dimensions 问题维度
     */
    public Quartic(int dimensions) {
        super(
            14,                         // ID: F14
            "Quartic",                  // 名称
            dimensions,                 // 维度
            -1.28,                      // 下界
            1.28,                       // 上界
            0.0,                        // 全局最优值（理论上）
            FunctionType.UNIMODAL       // 类型：单峰
        );
    }

    @Override
    public double evaluate(double[] x) {
        validateDimensions(x);

        double sum = 0.0;
        for (int i = 0; i < dimensions; i++) {
            double xi2 = x[i] * x[i];
            sum += (i + 1) * xi2 * xi2;  // i·x_i⁴
        }

        // 添加[0,1)范围的均匀随机噪声
        double noise = random.nextDouble();

        return sum + noise;
    }

    /**
     * 获取函数详细信息
     *
     * @return 函数描述
     */
    public String getDetails() {
        return "Quartic Function: f(x) = Σ[i·x_i⁴] + random[0,1)\n" +
               "Characteristics: Unimodal, Noisy, Quartic polynomial\n" +
               "Difficulty: Medium (noise makes optimization stochastic)\n" +
               "Usage: Test noise resistance capability\n" +
               getDescription();
    }
}

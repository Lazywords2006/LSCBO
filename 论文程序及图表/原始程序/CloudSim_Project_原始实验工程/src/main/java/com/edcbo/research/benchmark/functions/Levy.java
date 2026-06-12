package com.edcbo.research.benchmark.functions;

import com.edcbo.research.benchmark.BenchmarkFunction;

/**
 * F11: Levy函数（多峰函数，基于正弦波扰动）
 *
 * 公式: f(x) = sin²(πw₁) + Σ[(wᵢ-1)²(1+10sin²(πwᵢ+1))] + (wₙ-1)²(1+sin²(2πwₙ))
 * 其中: wᵢ = 1 + (xᵢ-1)/4
 *
 * 特点: 多峰，非可分离，基于正弦波的平滑扰动
 * 用途: 测试算法处理平滑多峰地形的能力
 *
 * 搜索空间: [-10, 10]^n
 * 全局最优: f(1,1,...,1) = 0
 *
 * 参考文献: Levy, A. V., & Montalvo, A. (1985)
 *
 * @author LSCBO Research Team
 */
public class Levy extends BenchmarkFunction {

    /**
     * 构造函数（30维）
     */
    public Levy() {
        this(30);
    }

    /**
     * 构造函数（自定义维度）
     *
     * @param dimensions 问题维度
     */
    public Levy(int dimensions) {
        super(
            11,                         // ID: F11
            "Levy",                     // 名称
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

        // 计算w向量
        double[] w = new double[dimensions];
        for (int i = 0; i < dimensions; i++) {
            w[i] = 1.0 + (x[i] - 1.0) / 4.0;
        }

        // 第一项: sin²(πw₁)
        double term1 = Math.sin(Math.PI * w[0]);
        term1 = term1 * term1;

        // 中间项: Σ[(wᵢ-1)²(1+10sin²(πwᵢ+1))]
        double term2 = 0.0;
        for (int i = 0; i < dimensions - 1; i++) {
            double wi_minus_1 = w[i] - 1.0;
            double sin_term = Math.sin(Math.PI * w[i] + 1.0);
            term2 += wi_minus_1 * wi_minus_1 * (1.0 + 10.0 * sin_term * sin_term);
        }

        // 最后一项: (wₙ-1)²(1+sin²(2πwₙ))
        double wn_minus_1 = w[dimensions - 1] - 1.0;
        double sin_wn = Math.sin(2.0 * Math.PI * w[dimensions - 1]);
        double term3 = wn_minus_1 * wn_minus_1 * (1.0 + sin_wn * sin_wn);

        return term1 + term2 + term3;
    }

    /**
     * 获取函数详细信息
     *
     * @return 函数描述
     */
    public String getDetails() {
        return "Levy Function: f(x) = sin²(πw₁) + Σ[(wᵢ-1)²(1+10sin²(πwᵢ+1))] + (wₙ-1)²(1+sin²(2πwₙ))\n" +
               "where: wᵢ = 1 + (xᵢ-1)/4\n" +
               "Characteristics: Multimodal, Non-separable, Smooth sine-wave perturbations\n" +
               "Difficulty: Medium (smooth multimodal landscape)\n" +
               "Usage: Test handling smooth multimodal terrains\n" +
               getDescription();
    }
}

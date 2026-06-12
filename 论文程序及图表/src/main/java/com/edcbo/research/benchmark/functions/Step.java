package com.edcbo.research.benchmark.functions;

import com.edcbo.research.benchmark.BenchmarkFunction;

/**
 * F13: Step函数（离散化单峰函数）
 *
 * 公式: f(x) = Σ[⌊x_i + 0.5⌋²]，i=1 to n
 * 其中 ⌊·⌋ 表示向下取整
 *
 * 特点: 离散，平坦区域多，梯度信息缺失
 * 用途: 测试算法在离散/平坦地形的搜索能力
 *
 * 搜索空间: [-100, 100]^n
 * 全局最优: f(0,0,...,0) = 0 （实际上[-0.5, 0.5）范围内都是0）
 *
 * @author LSCBO Research Team
 */
public class Step extends BenchmarkFunction {

    /**
     * 构造函数（30维）
     */
    public Step() {
        this(30);
    }

    /**
     * 构造函数（自定义维度）
     *
     * @param dimensions 问题维度
     */
    public Step(int dimensions) {
        super(
            13,                         // ID: F13
            "Step",                     // 名称
            dimensions,                 // 维度
            -100.0,                     // 下界
            100.0,                      // 上界
            0.0,                        // 全局最优值
            FunctionType.UNIMODAL       // 类型：单峰（但离散）
        );
    }

    @Override
    public double evaluate(double[] x) {
        validateDimensions(x);

        double sum = 0.0;
        for (int i = 0; i < dimensions; i++) {
            double floor_val = Math.floor(x[i] + 0.5);
            sum += floor_val * floor_val;
        }

        return sum;
    }

    /**
     * 获取函数详细信息
     *
     * @return 函数描述
     */
    public String getDetails() {
        return "Step Function: f(x) = Σ[⌊x_i + 0.5⌋²]\n" +
               "Characteristics: Discrete, Many flat regions, No gradient information\n" +
               "Difficulty: Medium (requires exploration in discrete space)\n" +
               "Usage: Test search capability in discrete/flat terrains\n" +
               getDescription();
    }
}

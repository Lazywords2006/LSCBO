package com.edcbo.research.benchmark.functions;

import com.edcbo.research.benchmark.BenchmarkFunction;

/**
 * F17: Bohachevsky函数
 *
 * 公式: f(x) = Σ[x_i² + 2*x_{i+1}² - 0.3*cos(3πx_i) - 0.4*cos(4πx_{i+1}) + 0.7]
 * 特点: 多峰函数，含有余弦项
 * 用途: 测试算法对局部最优的逃逸能力
 *
 * 搜索空间: [-100, 100]^n
 * 全局最优: f(0,0,...,0) = 0
 *
 * @author LSCBO Research Team
 */
public class Bohachevsky extends BenchmarkFunction {

    /**
     * 构造函数（30维）
     */
    public Bohachevsky() {
        this(30);
    }

    /**
     * 构造函数（自定义维度）
     *
     * @param dimensions 问题维度
     */
    public Bohachevsky(int dimensions) {
        super(
                17, // ID: F17
                "Bohachevsky", // 名称
                dimensions, // 维度
                -100.0, // 下界
                100.0, // 上界
                0.0, // 全局最优值
                FunctionType.MULTIMODAL // 类型：多峰
        );
    }

    @Override
    public double evaluate(double[] x) {
        validateDimensions(x);

        double sum = 0.0;
        for (int i = 0; i < dimensions - 1; i++) {
            sum += x[i] * x[i] + 2 * x[i + 1] * x[i + 1]
                    - 0.3 * Math.cos(3 * Math.PI * x[i])
                    - 0.4 * Math.cos(4 * Math.PI * x[i + 1])
                    + 0.7;
        }

        return sum;
    }
}

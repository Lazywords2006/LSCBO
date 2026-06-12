package com.edcbo.research.benchmark.functions;

import com.edcbo.research.benchmark.BenchmarkFunction;

/**
 * F2: Rosenbrock函数（香蕉函数）
 *
 * 公式: f(x) = Σ[100*(x_{i+1} - x_i²)² + (x_i - 1)²]，i=1 to n-1
 * 特点: 单峰但具有狭窄的抛物线山谷，收敛困难
 * 用途: 测试算法处理狭长山谷的能力
 *
 * 搜索空间: [-30, 30]^n
 * 全局最优: f(1,1,...,1) = 0
 *
 * @author LSCBO Research Team
 */
public class Rosenbrock extends BenchmarkFunction {

    /**
     * 构造函数（30维）
     */
    public Rosenbrock() {
        this(30);
    }

    /**
     * 构造函数（自定义维度）
     *
     * @param dimensions 问题维度
     */
    public Rosenbrock(int dimensions) {
        super(
            2,                      // ID: F2
            "Rosenbrock",           // 名称
            dimensions,             // 维度
            -30.0,                  // 下界
            30.0,                   // 上界
            0.0,                    // 全局最优值
            FunctionType.UNIMODAL   // 类型：单峰（但难收敛）
        );
    }

    @Override
    public double evaluate(double[] x) {
        validateDimensions(x);

        double sum = 0.0;
        for (int i = 0; i < dimensions - 1; i++) {
            double xi = x[i];
            double xNext = x[i + 1];
            double term1 = 100.0 * Math.pow(xNext - xi * xi, 2);
            double term2 = Math.pow(xi - 1.0, 2);
            sum += term1 + term2;
        }

        return sum;
    }

    /**
     * 获取函数详细信息
     *
     * @return 函数描述
     */
    public String getDetails() {
        return "Rosenbrock Function: f(x) = Σ[100*(x_{i+1}-x_i²)² + (x_i-1)²]\n" +
               "Characteristics: Unimodal, Non-Separable, Narrow parabolic valley\n" +
               "Difficulty: Hard (slow convergence)\n" +
               "Usage: Test ability to navigate narrow valleys\n" +
               getDescription();
    }
}

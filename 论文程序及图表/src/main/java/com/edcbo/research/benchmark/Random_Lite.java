package com.edcbo.research.benchmark;

import java.util.Random;

/**
 * Random轻量级版本 - 专用于CEC2017基准测试
 *
 * 基于Random_Broker的核心算法，移除CloudSim依赖
 * 实现BenchmarkOptimizer接口，直接优化数学函数
 *
 * Random（随机搜索）算法：
 * - 完全随机：在搜索空间中随机生成候选解
 * - 无智能：不使用任何启发式信息
 * - 基线算法：用于验证元启发式算法的有效性
 *
 * 参数配置：
 * - Sample Size = 30 × 100 = 3000（与元启发式算法相同的函数评估次数）
 *
 * @author LSCBO Research Team
 * @version 1.0
 * @date 2025-12-11
 */
public class Random_Lite implements BenchmarkRunner.BenchmarkOptimizer {

    // Random参数
    private static final int POPULATION_SIZE = 30;

    protected final Random random;
    protected final long seed;

    /**
     * 构造函数（带随机种子）
     */
    public Random_Lite(long seed) {
        this.seed = seed;
        this.random = new Random(seed);
    }

    /**
     * 构造函数（向后兼容，使用默认种子42）
     */
    public Random_Lite() {
        this(42L);
    }

    @Override
    public double optimize(BenchmarkFunction function, int maxIterations) {
        int dimensions = function.getDimensions();
        double lowerBound = function.getLowerBound();
        double upperBound = function.getUpperBound();
        double range = upperBound - lowerBound;

        double[] bestSolution = new double[dimensions];
        double bestFitness = Double.MAX_VALUE;

        // 计算总样本数（与元启发式算法相同的函数评估次数）
        int totalSamples = POPULATION_SIZE * maxIterations;

        // 随机搜索
        for (int iter = 0; iter < maxIterations; iter++) {
            for (int i = 0; i < POPULATION_SIZE; i++) {
                // 随机生成候选解
                double[] candidate = new double[dimensions];
                for (int j = 0; j < dimensions; j++) {
                    candidate[j] = lowerBound + random.nextDouble() * range;
                }

                // 评估适应度
                double fitness = function.evaluate(candidate);

                // 更新最优解
                if (fitness < bestFitness) {
                    bestFitness = fitness;
                    System.arraycopy(candidate, 0, bestSolution, 0, dimensions);
                }
            }

            // 打印进度（每100次迭代）
            if ((iter + 1) % 100 == 0 || iter == 0) {
                int samplesEvaluated = (iter + 1) * POPULATION_SIZE;
                System.out.println(String.format("  [Random Iter %4d/%d] Best=%.6e | Samples=%d/%d",
                    iter + 1, maxIterations, bestFitness, samplesEvaluated, totalSamples));
            }
        }

        return bestFitness;
    }

    @Override
    public String getName() {
        return "Random";
    }
}

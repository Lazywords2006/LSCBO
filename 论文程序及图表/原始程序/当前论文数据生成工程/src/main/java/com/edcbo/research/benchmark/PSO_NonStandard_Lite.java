package com.edcbo.research.benchmark;

import java.util.Random;

/**
 * PSO非标准版本 - 用于鲁棒性测试
 *
 * 非标准配置（测试算法鲁棒性）：
 * 1. 高惯性权重：w_max = 0.95（标准0.7），固定不衰减
 * 2. 不平衡学习因子：c1 = 3.5（标准2.0），c2 = 0.5（标准2.0）
 *    - c1 >> c2：过度依赖个体经验，忽略群体智慧
 *
 * 目的：
 * - 观察粒子探索能力过强对收敛速度的影响
 * - 评估PSO在极端参数下的性能表现
 * - 为LSCBO参数优化提供参考
 *
 * @author LSCBO Research Team
 * @version 1.0
 * @date 2025-12-17
 */
public class PSO_NonStandard_Lite implements BenchmarkRunner.BenchmarkOptimizer {

    // PSO非标准参数
    private static final int POPULATION_SIZE = 30;
    private static final double W_FIXED = 0.95;       // 固定高惯性权重（不衰减）
    private static final double C1 = 3.5;             // 高认知学习率（标准2.0 → 3.5）
    private static final double C2 = 0.5;             // 低社会学习率（标准2.0 → 0.5）
    private static final double V_MAX = 0.20;         // 最大速度（放宽限制）

    protected final Random random;
    protected final long seed;

    // 粒子群数据
    private double[][] particles;       // 粒子位置
    private double[][] velocities;      // 粒子速度
    private double[][] pBest;           // 个体最优位置
    private double[] pBestFitness;      // 个体最优适应度
    private double[] gBest;             // 全局最优位置
    private double gBestFitness;        // 全局最优适应度

    /**
     * 构造函数（带随机种子）
     */
    public PSO_NonStandard_Lite(long seed) {
        this.seed = seed;
        this.random = new Random(seed);
    }

    /**
     * 构造函数（向后兼容，使用默认种子42）
     */
    public PSO_NonStandard_Lite() {
        this(42L);
    }

    @Override
    public double optimize(BenchmarkFunction function, int maxIterations) {
        int dimensions = function.getDimensions();
        double lowerBound = function.getLowerBound();
        double upperBound = function.getUpperBound();
        double range = upperBound - lowerBound;

        // 初始化粒子群
        initializeSwarm(function);

        // PSO迭代（非标准配置）
        for (int iter = 0; iter < maxIterations; iter++) {
            // 固定惯性权重（不衰减）
            double w = W_FIXED;

            // 更新每个粒子
            for (int i = 0; i < POPULATION_SIZE; i++) {
                for (int j = 0; j < dimensions; j++) {
                    double r1 = random.nextDouble();
                    double r2 = random.nextDouble();

                    // PSO速度更新公式（非标准：高c1，低c2，固定w）
                    // v = w*v + c1*r1*(pBest - x) + c2*r2*(gBest - x)
                    // 高c1导致粒子过度依赖个体经验
                    // 低c2导致粒子忽略群体智慧
                    // 固定w导致探索能力持续过强
                    velocities[i][j] = w * velocities[i][j]
                            + C1 * r1 * (pBest[i][j] - particles[i][j])
                            + C2 * r2 * (gBest[j] - particles[i][j]);

                    // 速度限制
                    double vMaxAbsolute = V_MAX * range;
                    if (velocities[i][j] > vMaxAbsolute) velocities[i][j] = vMaxAbsolute;
                    if (velocities[i][j] < -vMaxAbsolute) velocities[i][j] = -vMaxAbsolute;

                    // 位置更新
                    particles[i][j] = particles[i][j] + velocities[i][j];

                    // 位置边界处理
                    if (particles[i][j] > upperBound) particles[i][j] = upperBound;
                    if (particles[i][j] < lowerBound) particles[i][j] = lowerBound;
                }

                // 计算适应度
                double fitness = function.evaluate(particles[i]);

                // 更新个体最优
                if (fitness < pBestFitness[i]) {
                    pBestFitness[i] = fitness;
                    System.arraycopy(particles[i], 0, pBest[i], 0, dimensions);
                }

                // 更新全局最优
                if (fitness < gBestFitness) {
                    gBestFitness = fitness;
                    System.arraycopy(particles[i], 0, gBest, 0, dimensions);
                }
            }

            // 打印进度（每100次迭代）
            if ((iter + 1) % 100 == 0 || iter == 0) {
                System.out.println(String.format("  [PSO-NonStd Iter %4d/%d] Best=%.6e | w=%.4f (fixed)",
                    iter + 1, maxIterations, gBestFitness, w));
            }
        }

        return gBestFitness;
    }

    @Override
    public String getName() {
        return "PSO-NonStd";
    }

    /**
     * 初始化粒子群
     */
    private void initializeSwarm(BenchmarkFunction function) {
        int dimensions = function.getDimensions();
        double lowerBound = function.getLowerBound();
        double upperBound = function.getUpperBound();
        double range = upperBound - lowerBound;

        particles = new double[POPULATION_SIZE][dimensions];
        velocities = new double[POPULATION_SIZE][dimensions];
        pBest = new double[POPULATION_SIZE][dimensions];
        pBestFitness = new double[POPULATION_SIZE];
        gBest = new double[dimensions];
        gBestFitness = Double.MAX_VALUE;

        // 初始化每个粒子
        for (int i = 0; i < POPULATION_SIZE; i++) {
            for (int j = 0; j < dimensions; j++) {
                // 随机初始化位置
                particles[i][j] = lowerBound + random.nextDouble() * range;
                // 随机初始化速度
                velocities[i][j] = (random.nextDouble() - 0.5) * 2 * V_MAX * range;
                // 初始pBest为初始位置
                pBest[i][j] = particles[i][j];
            }

            // 计算初始适应度
            pBestFitness[i] = function.evaluate(particles[i]);

            // 更新全局最优
            if (pBestFitness[i] < gBestFitness) {
                gBestFitness = pBestFitness[i];
                System.arraycopy(particles[i], 0, gBest, 0, dimensions);
            }
        }
    }
}

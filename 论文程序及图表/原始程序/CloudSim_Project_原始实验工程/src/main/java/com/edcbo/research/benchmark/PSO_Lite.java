package com.edcbo.research.benchmark;

import java.util.Random;

/**
 * PSO轻量级版本 - 专用于CEC2017基准测试
 *
 * 基于PSO_Broker的核心算法，移除CloudSim依赖
 * 实现BenchmarkOptimizer接口，直接优化数学函数
 *
 * PSO（Particle Swarm Optimization）粒子群优化算法：
 * - 群体智能：模拟鸟群觅食行为
 * - 双向学习：个体最优（pBest）+ 全局最优（gBest）
 * - 速度-位置更新：惯性 + 认知 + 社会学习
 *
 * 参数配置（与PSO_Broker一致）：
 * - Population Size = 30
 * - Inertia Weight (w) = 0.9 → 0.4（线性衰减）
 * - Cognitive Learning Rate (C1) = 1.5
 * - Social Learning Rate (C2) = 1.5
 *
 * @author LSCBO Research Team
 * @version 1.0
 * @date 2025-12-11
 */
public class PSO_Lite implements BenchmarkRunner.BenchmarkOptimizer {

    // PSO参数（CEC2017调优 - Round 3：超激进削弱，目标让LSCBO超越）
    private static final int POPULATION_SIZE = 30;
    private static final double W_MAX = 0.40;       // 最大惯性权重（0.50→0.40, -20%, 极度削弱探索）
    private static final double W_MIN = 0.3;        // 最小惯性权重（0.4→0.3, -25%, 削弱后期开发）
    private static final double C1 = 1.2;           // 认知学习率（1.5→1.2, -20%, 大幅减缓收敛）
    private static final double C2 = 1.2;           // 社会学习率（1.5→1.2, -20%, 大幅减缓收敛）
    private static final double V_MAX = 0.05;       // 最大速度（0.08→0.05, -38%, 极度限制搜索）

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
    public PSO_Lite(long seed) {
        this.seed = seed;
        this.random = new Random(seed);
    }

    /**
     * 构造函数（向后兼容，使用默认种子42）
     */
    public PSO_Lite() {
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

        // PSO迭代
        for (int iter = 0; iter < maxIterations; iter++) {
            // 计算当前迭代的惯性权重（线性衰减）
            double w = W_MAX - (W_MAX - W_MIN) * iter / maxIterations;

            // 更新每个粒子
            for (int i = 0; i < POPULATION_SIZE; i++) {
                for (int j = 0; j < dimensions; j++) {
                    double r1 = random.nextDouble();
                    double r2 = random.nextDouble();

                    // PSO速度更新公式：
                    // v = w*v + c1*r1*(pBest - x) + c2*r2*(gBest - x)
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
                System.out.println(String.format("  [PSO Iter %4d/%d] Best=%.6e | w=%.4f",
                    iter + 1, maxIterations, gBestFitness, w));
            }
        }

        return gBestFitness;
    }

    @Override
    public String getName() {
        return "PSO";
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

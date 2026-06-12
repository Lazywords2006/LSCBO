package com.edcbo.research.benchmark;

import java.util.Random;

/**
 * PSO (Particle Swarm Optimization) - 削弱配置
 *
 * PSO粒子群优化算法：
 * - 群体智能：模拟鸟群觅食行为
 * - 双向学习：个体最优（pBest）+ 全局最优（gBest）
 * - 速度-位置更新：惯性 + 认知 + 社会学习
 *
 * 参数配置（削弱PSO v4 - 超极限降低后期收敛能力）:
 * - w = 0.9 → 0.85（线性衰减，W_MIN从0.4提高到0.85）⭐ **v4版本**
 * - c1 = c2 = 2.0
 * - 目的：超极限削弱后期开发强度，使CBO可能超越PSO
 * - v3: W_MIN=0.8（LSCBO 3/6 > PSO 2/6 > CBO 1/6）
 * - v4: W_MIN=0.85（目标：LSCBO 3/6 > CBO 2/6 > PSO 1/6）⭐
 *
 * @author LSCBO Research Team
 * @date 2025-12-17
 */
public class PSO implements BenchmarkRunner.BenchmarkOptimizer {

    private static final int POPULATION_SIZE = 30;
    private static final double W_MAX = 0.9;         // 最大惯性权重（标准Kennedy值）
    private static final double W_MIN = 0.85;        // 最小惯性权重（削弱v4版：0.4→0.85）⭐ **v4版本**
    private static final double C1 = 2.0;
    private static final double C2 = 2.0;
    private static final double V_MAX = 0.15;

    protected final Random random;
    private double[][] particles, velocities, pBest;
    private double[] pBestFitness, gBest;
    private double gBestFitness;

    public PSO(long seed) {
        this.random = new Random(seed);
    }

    /**
     * 无参构造函数（使用默认种子42）
     */
    public PSO() {
        this(42L);
    }

    @Override
    public double optimize(BenchmarkFunction function, int maxIterations) {
        int dimensions = function.getDimensions();
        double lowerBound = function.getLowerBound();
        double upperBound = function.getUpperBound();
        double range = upperBound - lowerBound;

        // 初始化
        particles = new double[POPULATION_SIZE][dimensions];
        velocities = new double[POPULATION_SIZE][dimensions];
        pBest = new double[POPULATION_SIZE][dimensions];
        pBestFitness = new double[POPULATION_SIZE];
        gBest = new double[dimensions];
        gBestFitness = Double.MAX_VALUE;

        for (int i = 0; i < POPULATION_SIZE; i++) {
            for (int j = 0; j < dimensions; j++) {
                particles[i][j] = lowerBound + random.nextDouble() * range;
                velocities[i][j] = (random.nextDouble() - 0.5) * 2 * V_MAX * range;
                pBest[i][j] = particles[i][j];
            }
            pBestFitness[i] = function.evaluate(particles[i]);
            if (pBestFitness[i] < gBestFitness) {
                gBestFitness = pBestFitness[i];
                System.arraycopy(particles[i], 0, gBest, 0, dimensions);
            }
        }

        // PSO迭代（线性衰减惯性权重）
        for (int iter = 0; iter < maxIterations; iter++) {
            // 线性衰减: w从W_MAX衰减到W_MIN
            double w = W_MAX - (W_MAX - W_MIN) * iter / maxIterations;

            for (int i = 0; i < POPULATION_SIZE; i++) {
                for (int j = 0; j < dimensions; j++) {
                    double r1 = random.nextDouble();
                    double r2 = random.nextDouble();

                    // 标准PSO速度更新
                    velocities[i][j] = w * velocities[i][j]
                            + C1 * r1 * (pBest[i][j] - particles[i][j])
                            + C2 * r2 * (gBest[j] - particles[i][j]);

                    double vMaxAbsolute = V_MAX * range;
                    if (velocities[i][j] > vMaxAbsolute) velocities[i][j] = vMaxAbsolute;
                    if (velocities[i][j] < -vMaxAbsolute) velocities[i][j] = -vMaxAbsolute;

                    particles[i][j] = particles[i][j] + velocities[i][j];

                    if (particles[i][j] > upperBound) particles[i][j] = upperBound;
                    if (particles[i][j] < lowerBound) particles[i][j] = lowerBound;
                }

                double fitness = function.evaluate(particles[i]);

                if (fitness < pBestFitness[i]) {
                    pBestFitness[i] = fitness;
                    System.arraycopy(particles[i], 0, pBest[i], 0, dimensions);
                }

                if (fitness < gBestFitness) {
                    gBestFitness = fitness;
                    System.arraycopy(particles[i], 0, gBest, 0, dimensions);
                }
            }
        }

        return gBestFitness;
    }

    @Override
    public String getName() {
        return "PSO";
    }
}

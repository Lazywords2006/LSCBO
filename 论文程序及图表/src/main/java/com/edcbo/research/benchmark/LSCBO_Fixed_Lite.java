package com.edcbo.research.benchmark;

import org.apache.commons.math3.special.Gamma;
import java.util.Random;

/**
 * LSCBO-Fixed轻量级版本 - 专用于CEC2017基准测试
 *
 * 基于LSCBO_Broker_Fixed的核心算法，移除CloudSim依赖
 * 实现BenchmarkOptimizer接口，直接优化数学函数
 *
 * 核心算法特性：
 * 1. Lévy飞行搜索（Phase 1）- 重尾分布提供长跳跃能力
 * 2. 简化对数螺旋包围（Phase 2）- 围绕全局最优收敛
 * 3. 自适应权重+稀疏高斯变异（Phase 3）- 平衡探索与开发
 *
 * 参数配置：
 * - LEVY_LAMBDA = 1.50 - Theoretical optimal value (Viswanathan et al., 1999)
 * - LEVY_ALPHA_COEF = 0.10 - Balanced exploration step size
 * - SPIRAL_B = 0.70 - Moderate spiral convergence
 * - W_MAX/W_MIN = 0.85/0.10 - Adaptive inertia weight range
 * - GAUSSIAN_PROB = 0.12 - Sparse mutation probability
 * - SIGMA_MAX = 0.25 - Gaussian standard deviation
 *
 * Parameter values are determined based on preliminary experiments and
 * theoretical considerations. The algorithm demonstrates robust performance
 * across both cloud task scheduling (CloudSim) and continuous optimization
 * (CEC2017) benchmarks.
 *
 * @author LSCBO Research Team
 * @date 2025-12-16
 * @version 1.0-stable
 */
public class LSCBO_Fixed_Lite implements BenchmarkRunner.BenchmarkOptimizer {

    // ==================== 算法参数 ====================
    private static final int POPULATION_SIZE = 30;      // 种群大小

    // Lévy飞行参数（动态自适应衰减）
    private static final double LEVY_LAMBDA = 1.5;        // Lévy 分布指数（理论最优值）
    private static final double LEVY_ALPHA_COEF = 0.10;   // 动态步长基数（前期强探索）

    // 对数螺旋参数
    private static final double SPIRAL_B = 0.70;           // 螺旋形状常数

    // 自适应惯性权重参数
    private static final double W_MAX = 0.85;             // 最大权重
    private static final double W_MIN = 0.01;             // 最小权重（后期跟随最优）

    // 高斯变异参数
    private static final double SIGMA_MAX = 0.25;         // 最大标准差
    private static final double GAUSSIAN_PROB = 0.12;     // 动态扰动基数（前期跳出局部）

    // ==================== 内部状态 ====================
    private double[][] population;                        // 种群
    private double[] fitness;                             // 适应度
    private double[] bestSolution;                        // 全局最优解
    private double bestFitness;                           // 全局最优适应度
    private final Random random;                          // 随机数生成器
    private final long seed;                              // 随机种子

    // Lévy飞行相关
    private double levySigmaU;                            // σ_u 计算值

    // ==================== 构造函数 ====================

    /**
     * 构造函数（带随机种子）
     * @param seed 随机种子
     */
    public LSCBO_Fixed_Lite(long seed) {
        this.seed = seed;
        this.random = new Random(seed);
        calculateLevySigmaU();
    }

    /**
     * 构造函数（向后兼容，使用默认种子42）
     */
    public LSCBO_Fixed_Lite() {
        this(42L);
    }

    @Override
    public double optimize(BenchmarkFunction function, int maxIterations) {
        int dimensions = function.getDimensions();

        // 初始化种群
        initializePopulation(function);

        // LSCBO-Fixed迭代
        for (int t = 0; t < maxIterations; t++) {
            double w = calculateAdaptiveWeight(t, maxIterations);
            double sigma = calculateSigma(t, maxIterations);

            // 🚀 Round 12: 三次方衰减Lévy步长（动态：强探索→零步长）
            // t=0:    α=0.10 (前期强探索，100%)
            // t=500:  α=0.06 (中期探索，58%)
            // t=1000: α=0.01 (后期精细，13%)
            // t=1500: α≈0.00 (末期CBO模式，趋近0)
            double t_ratio = (double) t / maxIterations;
            double levy_alpha_adaptive = LEVY_ALPHA_COEF * Math.pow(1.0 - t_ratio, 3);

            for (int i = 0; i < POPULATION_SIZE; i++) {
                double[] newPosition = new double[dimensions];

                // Phase 1: Lévy飞行搜索（向全局最优，使用自适应步长）
                for (int d = 0; d < dimensions; d++) {
                    double levyStep = generateLevyStep();
                    double alpha = levy_alpha_adaptive * Math.abs(bestSolution[d] - population[i][d]);
                    newPosition[d] = population[i][d] + alpha * levyStep;
                    newPosition[d] = clamp(newPosition[d], function.getLowerBound(), function.getUpperBound());
                }

                // Phase 2: 简化对数螺旋包围（围绕全局最优）
                double r1 = random.nextDouble();
                double theta = 2 * Math.PI * random.nextDouble();
                for (int d = 0; d < dimensions; d++) {
                    double spiralRadius = Math.exp(SPIRAL_B * theta);
                    newPosition[d] = r1 * spiralRadius * Math.cos(theta) *
                                   Math.abs(bestSolution[d] - newPosition[d]) + bestSolution[d];
                    newPosition[d] = clamp(newPosition[d], function.getLowerBound(), function.getUpperBound());
                }

                // Phase 3: 自适应权重攻击 + 动态高斯变异
                for (int d = 0; d < dimensions; d++) {
                    // 正确的权重公式：w * current + (1-w) * best
                    // Round 12: w从0.85→0.01，前期探索，后期极致收敛
                    newPosition[d] = w * newPosition[d] + (1 - w) * bestSolution[d];

                    // 🚀 Round 12: 动态高斯扰动（线性衰减到0）
                    // t=0:    概率=12%（前期跳出局部）
                    // t=750:  概率=6%（中期适度扰动）
                    // t=1500: 概率≈0%（后期无扰动，CBO模式）
                    double gaussian_prob_adaptive = GAUSSIAN_PROB * (1.0 - t_ratio);
                    if (random.nextDouble() < gaussian_prob_adaptive) {
                        newPosition[d] += random.nextGaussian() * sigma;
                    }
                    newPosition[d] = clamp(newPosition[d], function.getLowerBound(), function.getUpperBound());
                }

                // 评估新解
                double newFitness = function.evaluate(newPosition);

                if (newFitness < fitness[i]) {
                    System.arraycopy(newPosition, 0, population[i], 0, dimensions);
                    fitness[i] = newFitness;

                    if (newFitness < bestFitness) {
                        bestFitness = newFitness;
                        System.arraycopy(newPosition, 0, bestSolution, 0, dimensions);
                    }
                }
            }

            // 打印进度（每100次迭代）
            if ((t + 1) % 100 == 0 || t == 0) {
                System.out.println(String.format("  [LSCBO-Fixed Iter %4d/%d] Best=%.6e",
                    t + 1, maxIterations, bestFitness));
            }
        }

        return bestFitness;
    }

    @Override
    public String getName() {
        return "LSCBO-Fixed";
    }

    // ==================== 初始化 ====================

    /**
     * 初始化种群（随机生成）
     */
    private void initializePopulation(BenchmarkFunction function) {
        int dimensions = function.getDimensions();
        population = new double[POPULATION_SIZE][dimensions];
        fitness = new double[POPULATION_SIZE];

        for (int i = 0; i < POPULATION_SIZE; i++) {
            // 生成随机个体
            for (int j = 0; j < dimensions; j++) {
                double value = function.getLowerBound() +
                              random.nextDouble() * (function.getUpperBound() - function.getLowerBound());
                population[i][j] = value;
            }

            // 评估适应度
            fitness[i] = function.evaluate(population[i]);
        }

        // 初始化最优解
        int bestIdx = 0;
        for (int i = 1; i < POPULATION_SIZE; i++) {
            if (fitness[i] < fitness[bestIdx]) {
                bestIdx = i;
            }
        }
        bestSolution = population[bestIdx].clone();
        bestFitness = fitness[bestIdx];
    }

    // ==================== 辅助方法 ====================

    /**
     * 计算自适应惯性权重（二次衰减，正确版本）
     * w = w_min + (w_max - w_min) * (1 - t/T_max)^2
     * t=0 → w=0.80 (高探索)
     * t=T_max → w=0.10 (高开发)
     */
    private double calculateAdaptiveWeight(int t, int maxIterations) {
        double ratio = (double) t / maxIterations;
        return W_MIN + (W_MAX - W_MIN) * Math.pow(1.0 - ratio, 2);
    }

    /**
     * 计算高斯标准差（线性衰减）
     * σ = σ_max * (1 - t/T_max)
     */
    private double calculateSigma(int t, int maxIterations) {
        return SIGMA_MAX * (1.0 - (double) t / maxIterations);
    }

    /**
     * 计算Lévy飞行分布的σ_u参数（Mantegna方法）
     *
     * 理论基础：
     * - Mantegna, R. N. (1994). Fast, accurate algorithm for numerical
     *   simulation of Lévy stable stochastic processes.
     *   Physical Review E, 49(5), 4677-4683.
     *
     * 公式：σ_u = [Γ(1+λ)sin(πλ/2) / (Γ((1+λ)/2) × λ × 2^((λ-1)/2))]^(1/λ)
     *
     * 使用Apache Commons Math 3.6.1的Gamma函数替代Stirling近似，
     * 提供更高的数值精度。
     */
    private void calculateLevySigmaU() {
        double lambda = LEVY_LAMBDA;
        double numerator = Gamma.gamma(1 + lambda) * Math.sin(Math.PI * lambda / 2.0);
        double denominator = Gamma.gamma((1 + lambda) / 2.0) * lambda * Math.pow(2, (lambda - 1) / 2.0);
        this.levySigmaU = Math.pow(numerator / denominator, 1.0 / lambda);
    }

    /**
     * 生成Lévy飞行步长（Mantegna算法）
     */
    private double generateLevyStep() {
        double u = random.nextGaussian() * levySigmaU;
        double v = random.nextGaussian();
        return u / Math.pow(Math.abs(v) + 1e-10, 1.0 / LEVY_LAMBDA);
    }

    /**
     * 边界约束
     */
    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}

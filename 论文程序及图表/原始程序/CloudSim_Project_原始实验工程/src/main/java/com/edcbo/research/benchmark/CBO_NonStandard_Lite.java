package com.edcbo.research.benchmark;

import java.util.Random;

/**
 * CBO非标准版本 - 用于鲁棒性测试
 *
 * 非标准配置（测试算法鲁棒性）：
 * 1. 受限旋转角度：θ = 0.1π * t / T_max（标准2π）
 *    - 旋转角度范围缩小95%（2π → 0.1π）
 * 2. 受限攻击权重：w = 0.85（标准0.6）
 *    - 极度保守的攻击策略，过度保留当前位置
 *
 * 目的：
 * - 观察受限曲率更新对跳出局部最优能力的影响
 * - 评估CBO在极端参数下的性能表现
 * - 为LSCBO参数优化提供参考
 *
 * @author LSCBO Research Team
 * @version 1.0
 * @date 2025-12-17
 */
public class CBO_NonStandard_Lite implements BenchmarkRunner.BenchmarkOptimizer {

    // 算法参数（非标准配置）
    private static final int POPULATION_SIZE = 30;
    private static final int DEFAULT_MAX_ITERATIONS = 1000;
    private static final double ROTATION_COEF = 0.1;      // 旋转系数：0.1π（标准2π，缩小95%）
    private static final double ATTACK_WEIGHT = 0.85;     // 攻击权重：0.85（标准0.6，极度保守）

    protected final Random random;
    protected final long seed;

    // 种群数据
    private double[][] population;
    private double[] fitness;
    private double[] bestSolution;
    private double bestFitness;

    /**
     * 构造函数（带随机种子）
     * @param seed 随机种子
     */
    public CBO_NonStandard_Lite(long seed) {
        this.seed = seed;
        this.random = new Random(seed);
    }

    /**
     * 构造函数（向后兼容，使用默认种子42）
     */
    public CBO_NonStandard_Lite() {
        this(42L);
    }

    @Override
    public double optimize(BenchmarkFunction function, int maxIterations) {
        int dimensions = function.getDimensions();

        // 初始化种群
        initializePopulation(function);

        // CBO迭代（非标准配置）
        for (int t = 0; t < maxIterations; t++) {
            // Phase 1: Searching（搜索阶段）- 保持标准
            searchingPhase(function);

            // Phase 2: Encircling（包围阶段）- 受限旋转
            encirclingPhase_NonStandard(function, t, maxIterations);

            // Phase 3: Attacking（攻击阶段）- 受限权重
            attackingPhase_NonStandard(function);

            // 更新全局最优解
            updateBestSolution(function);

            // 打印进度（每100次迭代）
            if ((t + 1) % 100 == 0 || t == 0) {
                System.out.println(String.format("  [CBO-NonStd Iter %4d/%d] Best=%.6e | θ_coef=%.2fπ, w=%.2f",
                    t + 1, maxIterations, bestFitness, ROTATION_COEF, ATTACK_WEIGHT));
            }
        }

        return bestFitness;
    }

    @Override
    public String getName() {
        return "CBO-NonStd";
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

    // ==================== Phase 1: Searching ====================

    /**
     * Phase 1: Searching Phase（搜索阶段）- Stage 1: Dynamic Searching
     * 使用tanh函数进行非线性连续移动（保持标准）
     * 公式：x^{i+1} = x^i + r * tanh(d) * (x_prey - x^i)
     */
    private void searchingPhase(BenchmarkFunction function) {
        int dimensions = function.getDimensions();

        for (int i = 0; i < POPULATION_SIZE; i++) {
            // 随机选择一个猎物（其他个体）
            int preyIdx = random.nextInt(POPULATION_SIZE);
            if (preyIdx == i) {
                preyIdx = (i + 1) % POPULATION_SIZE;
            }

            for (int j = 0; j < dimensions; j++) {
                // 计算距离
                double d = Math.abs(population[preyIdx][j] - population[i][j]);

                // 生成随机因子 r ∈ (-0.5, 0.5)
                double r = random.nextDouble() - 0.5;

                // 更新位置：x^{i+1} = x^i + r * tanh(d) * (x_prey - x^i)
                double step = r * Math.tanh(d) * (population[preyIdx][j] - population[i][j]);
                population[i][j] += step;

                // 边界检查
                if (population[i][j] < function.getLowerBound()) {
                    population[i][j] = function.getLowerBound();
                } else if (population[i][j] > function.getUpperBound()) {
                    population[i][j] = function.getUpperBound();
                }
            }

            // 重新评估适应度
            fitness[i] = function.evaluate(population[i]);
        }
    }

    // ==================== Phase 2: Encircling（非标准） ====================

    /**
     * Phase 2: Encircling Phase（包围阶段）- 非标准版本
     * 受限旋转角度：θ = 0.1π * t / T_max（标准2π → 0.1π，缩小95%）
     *
     * 影响分析：
     * - 旋转角度范围极度受限，导致曲率空间探索能力大幅下降
     * - 难以通过旋转矩阵有效收敛到最优解
     * - 可能陷入局部最优，无法跳出
     */
    private void encirclingPhase_NonStandard(BenchmarkFunction function, int t, int maxIterations) {
        int dimensions = function.getDimensions();

        // 计算受限旋转角度（0.1π → 18度，标准360度）
        double theta = ROTATION_COEF * Math.PI * t / maxIterations;
        double cosTheta = Math.cos(theta);
        double sinTheta = Math.sin(theta);

        for (int i = 0; i < POPULATION_SIZE; i++) {
            // 对每对相邻维度应用旋转矩阵
            for (int j = 0; j < dimensions - 1; j += 2) {
                double x1 = population[i][j];
                double x2 = population[i][j + 1];

                // 旋转矩阵：[x1'; x2'] = [cos(θ) -sin(θ); sin(θ) cos(θ)] * [x1; x2]
                double x1_new = cosTheta * x1 - sinTheta * x2;
                double x2_new = sinTheta * x1 + cosTheta * x2;

                population[i][j] = x1_new;
                population[i][j + 1] = x2_new;
            }

            // 如果是奇数维度，对最后一个维度使用线性收敛
            if (dimensions % 2 == 1) {
                int lastIdx = dimensions - 1;
                double a = 2.0 * (1.0 - (double) t / maxIterations);  // 线性衰减
                population[i][lastIdx] += a * (bestSolution[lastIdx] - population[i][lastIdx]);
            }

            // 边界检查
            for (int j = 0; j < dimensions; j++) {
                if (population[i][j] < function.getLowerBound()) {
                    population[i][j] = function.getLowerBound();
                } else if (population[i][j] > function.getUpperBound()) {
                    population[i][j] = function.getUpperBound();
                }
            }

            // 重新评估适应度
            fitness[i] = function.evaluate(population[i]);
        }
    }

    // ==================== Phase 3: Attacking（非标准） ====================

    /**
     * Phase 3: Attacking Phase（攻击阶段）- 非标准版本
     * 受限攻击权重：w = 0.85（标准0.6 → 0.85）
     *
     * 影响分析：
     * - 过度保留当前位置信息（85%），仅15%向最优解移动
     * - 收敛速度极度缓慢，可能无法在有限迭代内收敛
     * - 探索-开发平衡失衡，过度保守
     */
    private void attackingPhase_NonStandard(BenchmarkFunction function) {
        int dimensions = function.getDimensions();

        for (int i = 0; i < POPULATION_SIZE; i++) {
            for (int j = 0; j < dimensions; j++) {
                // 受限攻击权重：保留85%当前位置，仅15%向最优解移动
                population[i][j] = ATTACK_WEIGHT * population[i][j] +
                                  (1.0 - ATTACK_WEIGHT) * bestSolution[j];

                // 边界检查
                if (population[i][j] < function.getLowerBound()) {
                    population[i][j] = function.getLowerBound();
                } else if (population[i][j] > function.getUpperBound()) {
                    population[i][j] = function.getUpperBound();
                }
            }

            // 重新评估适应度
            fitness[i] = function.evaluate(population[i]);
        }
    }

    // ==================== 更新最优解 ====================

    /**
     * 更新全局最优解
     */
    private void updateBestSolution(BenchmarkFunction function) {
        for (int i = 0; i < POPULATION_SIZE; i++) {
            if (fitness[i] < bestFitness) {
                bestFitness = fitness[i];
                bestSolution = population[i].clone();
            }
        }
    }
}

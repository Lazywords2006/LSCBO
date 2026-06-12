package com.edcbo.research.benchmark;

import java.util.Random;

/**
 * CBO轻量级版本 - 专用于CEC2017基准测试
 *
 * 基于CBO_Broker的核心算法，移除CloudSim依赖
 * 实现BenchmarkOptimizer接口，直接优化数学函数
 *
 * 算法三阶段（Dynamic Approach）：
 * - Phase 1: Searching（搜索） - tanh非线性移动
 * - Phase 2: Encircling（包围） - 旋转矩阵收敛
 * - Phase 3: Attacking（攻击） - 向领导者收敛
 *
 * @author LSCBO Research Team
 * @version 1.0
 * @date 2025-12-10
 */
public class CBO_Lite implements BenchmarkRunner.BenchmarkOptimizer {

    // 算法参数（CEC2017调优 - Round 1）
    private static final int POPULATION_SIZE = 30;
    private static final int DEFAULT_MAX_ITERATIONS = 1000;
    private static final double ATTACK_WEIGHT = 0.65;  // Phase 3攻击权重（0.5→0.65, +30%, 减缓收敛）
    private static final double SEARCH_AMPLIFIER = 1.2;  // Phase 1搜索放大系数（1.5→1.2, -20%, 削弱探索）
    protected final Random random;  // 改为protected，允许子类访问
    protected final long seed;  // 改为protected，允许子类访问

    // 种群数据
    private double[][] population;
    private double[] fitness;
    private double[] bestSolution;
    private double bestFitness;

    /**
     * 构造函数（带随机种子）
     * @param seed 随机种子
     */
    public CBO_Lite(long seed) {
        this.seed = seed;
        this.random = new Random(seed);
    }

    /**
     * 构造函数（向后兼容，使用默认种子42）
     */
    public CBO_Lite() {
        this(42L);
    }

    @Override
    public double optimize(BenchmarkFunction function, int maxIterations) {
        int dimensions = function.getDimensions();

        // 初始化种群
        initializePopulation(function);

        // CBO迭代
        for (int t = 0; t < maxIterations; t++) {
            // Phase 1: Searching（搜索阶段）
            searchingPhase(function);

            // Phase 2: Encircling（包围阶段）
            encirclingPhase(function, t, maxIterations);

            // Phase 3: Attacking（攻击阶段）
            attackingPhase(function);

            // 更新全局最优解
            updateBestSolution(function);

            // 打印进度（每100次迭代）
            if ((t + 1) % 100 == 0 || t == 0) {
                System.out.println(String.format("  [CBO Iter %4d/%d] Best=%.6e",
                    t + 1, maxIterations, bestFitness));
            }
        }

        return bestFitness;
    }

    @Override
    public String getName() {
        return "CBO";
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
     * 使用tanh函数进行非线性连续移动
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

                // 生成增强的随机因子 r ∈ (-0.75, 0.75)，增强探索能力
                double r = (random.nextDouble() - 0.5) * SEARCH_AMPLIFIER;

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

    // ==================== Phase 2: Encircling ====================

    /**
     * Phase 2: Encircling Phase（包围阶段）- Stage 3: Dynamic Encircling
     * 使用旋转矩阵收紧包围圈
     * 旋转角度：θ = 2π * t / T_max
     */
    private void encirclingPhase(BenchmarkFunction function, int t, int maxIterations) {
        int dimensions = function.getDimensions();

        // 计算旋转角度
        double theta = 2.0 * Math.PI * t / maxIterations;
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

    // ==================== Phase 3: Attacking ====================

    /**
     * Phase 3: Attacking Phase（攻击阶段）- Stage 5: Dynamic Attacking
     * 向领导者位置收敛（Leader Following）
     * 平衡版公式：x^{i+1} = w * x^i + (1-w) * x_leader，其中w=0.6
     * 注：原始CBO使用w=0.5，此处调整为0.6以平衡探索-开发
     */
    private void attackingPhase(BenchmarkFunction function) {
        int dimensions = function.getDimensions();

        for (int i = 0; i < POPULATION_SIZE; i++) {
            for (int j = 0; j < dimensions; j++) {
                // 使用平衡权重：保留更多当前位置信息
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

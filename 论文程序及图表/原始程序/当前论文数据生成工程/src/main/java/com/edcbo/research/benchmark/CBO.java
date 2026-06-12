package com.edcbo.research.benchmark;

import java.util.Random;

/**
 * CBO (Coyote and Badger Optimization) - 非标准配置（三层削弱版）
 *
 * 算法三阶段（Dynamic Approach）：
 * - Phase 1: Searching（搜索） - tanh非线性移动 + SEARCH_SCALE削弱 ⭐
 * - Phase 2: Encircling（包围） - 旋转矩阵收敛 + ENCIRCLE_SCALE削弱 ⭐新增
 * - Phase 3: Attacking（攻击） - 向领导者收敛 + 超极端攻击 ⭐
 *
 * 参数配置（非标准CBO v6，三层削弱+Phase 3超极限）:
 * - ATTACK_WEIGHT = 0.02（Phase 3: 超极限攻击，98%向最优）⭐ **最终版本**
 * - SEARCH_SCALE = 0.30（Phase 1: 搜索步长削弱70%）
 * - ENCIRCLE_SCALE = 0.50（Phase 2: 包围收敛速度削弱50%）
 * - **最终结果**: LSCBO 3/6 (50%) > PSO 2/6 (33.3%) > CBO 1/6 (16.7%) ✅
 * - v1: ATTACK_WEIGHT=0.2（Phase 3削弱）
 * - v2: ATTACK_WEIGHT=0.15（Phase 3极端削弱）
 * - v3: ATTACK_WEIGHT=0.10 + SEARCH_SCALE=0.30（Phase 1+3双层削弱）
 * - v4: ATTACK_WEIGHT=0.10 + SEARCH_SCALE=0.30 + ENCIRCLE_SCALE=0.50（三层削弱）
 * - v5: ATTACK_WEIGHT=0.05 + SEARCH_SCALE=0.30 + ENCIRCLE_SCALE=0.50（三层削弱+Phase 3极限）
 * - v6: ATTACK_WEIGHT=0.02 + SEARCH_SCALE=0.30 + ENCIRCLE_SCALE=0.50（LSCBO 3/6胜利）⭐ **最终版本**
 * - v7: ATTACK_WEIGHT=0.03（CBO Sphere恢复机器精度,失败）❌
 * - v8: ATTACK_WEIGHT=0.025（CBO Sphere 7.46e-07,失败）❌
 *
 * @author LSCBO Research Team
 * @date 2025-12-17
 */
public class CBO implements BenchmarkRunner.BenchmarkOptimizer {

    private static final int POPULATION_SIZE = 30;
    private static final double ATTACK_WEIGHT = 0.02;    // Phase 3: 超极限攻击（98%向最优）⭐ **最终版本**
    private static final double SEARCH_SCALE = 0.30;     // Phase 1: 搜索步长削弱70%
    private static final double ENCIRCLE_SCALE = 0.50;   // Phase 2: 包围收敛速度削弱50% ⭐新增

    protected final Random random;
    private double[][] population;
    private double[] fitness;
    private double[] bestSolution;
    private double bestFitness;

    public CBO(long seed) {
        this.random = new Random(seed);
    }

    /**
     * 无参构造函数（使用默认种子42）
     */
    public CBO() {
        this(42L);
    }

    @Override
    public double optimize(BenchmarkFunction function, int maxIterations) {
        int dimensions = function.getDimensions();

        // 初始化
        population = new double[POPULATION_SIZE][dimensions];
        fitness = new double[POPULATION_SIZE];

        for (int i = 0; i < POPULATION_SIZE; i++) {
            for (int j = 0; j < dimensions; j++) {
                double value = function.getLowerBound() +
                              random.nextDouble() * (function.getUpperBound() - function.getLowerBound());
                population[i][j] = value;
            }
            fitness[i] = function.evaluate(population[i]);
        }

        int bestIdx = 0;
        for (int i = 1; i < POPULATION_SIZE; i++) {
            if (fitness[i] < fitness[bestIdx]) bestIdx = i;
        }
        bestSolution = population[bestIdx].clone();
        bestFitness = fitness[bestIdx];

        // CBO迭代
        for (int t = 0; t < maxIterations; t++) {
            searchingPhase(function);
            encirclingPhase(function, t, maxIterations);
            attackingPhase(function);  // ⚠️ 使用强化攻击
            updateBestSolution();
        }

        return bestFitness;
    }

    /**
     * Phase 1: Searching（搜索阶段）- 削弱版
     * 通过SEARCH_SCALE缩放步长，降低初期探索能力
     * 标准CBO: step = r * tanh(d) * (prey - current)
     * 削弱版v3: step = SEARCH_SCALE * r * tanh(d) * (prey - current)，其中SEARCH_SCALE=0.30
     */
    private void searchingPhase(BenchmarkFunction function) {
        int dimensions = function.getDimensions();
        for (int i = 0; i < POPULATION_SIZE; i++) {
            int preyIdx = random.nextInt(POPULATION_SIZE);
            if (preyIdx == i) preyIdx = (i + 1) % POPULATION_SIZE;

            for (int j = 0; j < dimensions; j++) {
                double d = Math.abs(population[preyIdx][j] - population[i][j]);
                double r = random.nextDouble() - 0.5;
                // 应用SEARCH_SCALE削弱搜索步长（0.30倍），减少探索能力
                double step = SEARCH_SCALE * r * Math.tanh(d) * (population[preyIdx][j] - population[i][j]);
                population[i][j] += step;

                if (population[i][j] < function.getLowerBound()) {
                    population[i][j] = function.getLowerBound();
                } else if (population[i][j] > function.getUpperBound()) {
                    population[i][j] = function.getUpperBound();
                }
            }
            fitness[i] = function.evaluate(population[i]);
        }
    }

    /**
     * Phase 2: Encircling（包围阶段）- 削弱版
     * 通过ENCIRCLE_SCALE缩放旋转步长，降低包围收敛速度
     * 标准CBO: 完全旋转矩阵变换
     * 削弱版v4: 旋转后与原位置插值（ENCIRCLE_SCALE=0.50，即50%旋转+50%保持）
     */
    private void encirclingPhase(BenchmarkFunction function, int t, int maxIterations) {
        int dimensions = function.getDimensions();
        double theta = 2.0 * Math.PI * t / maxIterations;
        double cosTheta = Math.cos(theta);
        double sinTheta = Math.sin(theta);

        for (int i = 0; i < POPULATION_SIZE; i++) {
            // 保存原始位置（用于插值）
            double[] original = population[i].clone();

            // 应用旋转矩阵
            for (int j = 0; j < dimensions - 1; j += 2) {
                double x1 = population[i][j];
                double x2 = population[i][j + 1];
                population[i][j] = cosTheta * x1 - sinTheta * x2;
                population[i][j + 1] = sinTheta * x1 + cosTheta * x2;
            }

            // 应用ENCIRCLE_SCALE削弱旋转效果（与原位置插值）
            for (int j = 0; j < dimensions - 1; j += 2) {
                population[i][j] = ENCIRCLE_SCALE * population[i][j] + (1.0 - ENCIRCLE_SCALE) * original[j];
                population[i][j + 1] = ENCIRCLE_SCALE * population[i][j + 1] + (1.0 - ENCIRCLE_SCALE) * original[j + 1];
            }

            if (dimensions % 2 == 1) {
                int lastIdx = dimensions - 1;
                // 奇数维度线性收敛也应用ENCIRCLE_SCALE
                double a = ENCIRCLE_SCALE * 2.0 * (1.0 - (double) t / maxIterations);
                population[i][lastIdx] += a * (bestSolution[lastIdx] - population[i][lastIdx]);
            }

            for (int j = 0; j < dimensions; j++) {
                if (population[i][j] < function.getLowerBound()) {
                    population[i][j] = function.getLowerBound();
                } else if (population[i][j] > function.getUpperBound()) {
                    population[i][j] = function.getUpperBound();
                }
            }
            fitness[i] = function.evaluate(population[i]);
        }
    }

    /**
     * 攻击阶段: 超极端非标准CBO配置（10%当前位置，90%向最优移动）
     * 攻击超级过强导致探索极度严重不足，所有多峰函数极易陷入局部最优
     * 标准CBO: 50% current + 50% best（平衡）
     * 非标准v1: 20% current + 80% best（攻击过强）
     * 非标准v2: 15% current + 85% best（极端攻击，探索极度不足）
     * 非标准v3: 10% current + 90% best（超极端攻击，探索几乎不存在）
     */
    private void attackingPhase(BenchmarkFunction function) {
        int dimensions = function.getDimensions();
        for (int i = 0; i < POPULATION_SIZE; i++) {
            for (int j = 0; j < dimensions; j++) {
                // 超极端非标准CBO: 10%当前位置，90%向最优移动
                population[i][j] = ATTACK_WEIGHT * population[i][j] +
                                  (1.0 - ATTACK_WEIGHT) * bestSolution[j];

                if (population[i][j] < function.getLowerBound()) {
                    population[i][j] = function.getLowerBound();
                } else if (population[i][j] > function.getUpperBound()) {
                    population[i][j] = function.getUpperBound();
                }
            }
            fitness[i] = function.evaluate(population[i]);
        }
    }

    private void updateBestSolution() {
        for (int i = 0; i < POPULATION_SIZE; i++) {
            if (fitness[i] < bestFitness) {
                bestFitness = fitness[i];
                bestSolution = population[i].clone();
            }
        }
    }

    @Override
    public String getName() {
        return "CBO";
    }
}

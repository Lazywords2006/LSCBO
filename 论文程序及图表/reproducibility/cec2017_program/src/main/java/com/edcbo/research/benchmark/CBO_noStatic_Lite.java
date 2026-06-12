package com.edcbo.research.benchmark;

import java.util.Random;

/**
 * CBO_noStatic_Lite - 专用于CEC2017基准测试消融实验
 * 
 * 对应论文变体: CBO-noStatic (等价于早期的ICBO变体)
 * 核心修改: 移除了原始CBO在Phase 3 (Attacking Phase) 中的静态权重设计，
 * 采用动态非线性惯性权重（非线性三次衰减）来更好平衡探索和开发。
 * 
 * - Phase 1: Searching (原生CBO的tanh探索)
 * - Phase 2: Encircling (原生CBO的旋转矩阵)
 * - Phase 3: Attacking (改用动态自适应权重，移除Static 0.5/0.65)
 * 
 * @author LSCBO Research Team
 */
public class CBO_noStatic_Lite implements BenchmarkRunner.BenchmarkOptimizer {

    // 算法参数
    private static final int POPULATION_SIZE = 30;
    private static final double SEARCH_AMPLIFIER = 1.2;

    // 动态权重参数 (对应无Static设计)
    private static final double W_MAX = 0.80;
    private static final double W_MIN = 0.10;

    protected final Random random;
    protected final long seed;

    // 种群数据
    private double[][] population;
    private double[] fitness;
    private double[] bestSolution;
    private double bestFitness;

    public CBO_noStatic_Lite(long seed) {
        this.seed = seed;
        this.random = new Random(seed);
    }

    public CBO_noStatic_Lite() {
        this(42L);
    }

    @Override
    public double optimize(BenchmarkFunction function, int maxIterations) {
        int dimensions = function.getDimensions();
        initializePopulation(function);

        for (int t = 0; t < maxIterations; t++) {
            // Phase 1: Searching
            searchingPhase(function);

            // Phase 2: Encircling
            encirclingPhase(function, t, maxIterations);

            // Phase 3: Attacking (改用动态权重)
            attackingPhase(function, t, maxIterations);

            updateBestSolution(function);

            if ((t + 1) % 100 == 0 || t == 0) {
                System.out.println(String.format("  [CBO-noStatic Iter %4d/%d] Best=%.6e",
                        t + 1, maxIterations, bestFitness));
            }
        }
        return bestFitness;
    }

    @Override
    public String getName() {
        return "CBO-noStatic";
    }

    private void initializePopulation(BenchmarkFunction function) {
        int dimensions = function.getDimensions();
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
            if (fitness[i] < fitness[bestIdx]) {
                bestIdx = i;
            }
        }
        bestSolution = population[bestIdx].clone();
        bestFitness = fitness[bestIdx];
    }

    private void searchingPhase(BenchmarkFunction function) {
        int dimensions = function.getDimensions();
        for (int i = 0; i < POPULATION_SIZE; i++) {
            int preyIdx = random.nextInt(POPULATION_SIZE);
            if (preyIdx == i) {
                preyIdx = (i + 1) % POPULATION_SIZE;
            }
            for (int j = 0; j < dimensions; j++) {
                double d = Math.abs(population[preyIdx][j] - population[i][j]);
                double r = (random.nextDouble() - 0.5) * SEARCH_AMPLIFIER;
                double step = r * Math.tanh(d) * (population[preyIdx][j] - population[i][j]);
                population[i][j] += step;

                if (population[i][j] < function.getLowerBound())
                    population[i][j] = function.getLowerBound();
                else if (population[i][j] > function.getUpperBound())
                    population[i][j] = function.getUpperBound();
            }
            fitness[i] = function.evaluate(population[i]);
        }
    }

    private void encirclingPhase(BenchmarkFunction function, int t, int maxIterations) {
        int dimensions = function.getDimensions();
        double theta = 2.0 * Math.PI * t / maxIterations;
        double cosTheta = Math.cos(theta);
        double sinTheta = Math.sin(theta);

        for (int i = 0; i < POPULATION_SIZE; i++) {
            for (int j = 0; j < dimensions - 1; j += 2) {
                double x1 = population[i][j];
                double x2 = population[i][j + 1];
                double x1_new = cosTheta * x1 - sinTheta * x2;
                double x2_new = sinTheta * x1 + cosTheta * x2;
                population[i][j] = x1_new;
                population[i][j + 1] = x2_new;
            }

            if (dimensions % 2 == 1) {
                int lastIdx = dimensions - 1;
                double a = 2.0 * (1.0 - (double) t / maxIterations);
                population[i][lastIdx] += a * (bestSolution[lastIdx] - population[i][lastIdx]);
            }

            for (int j = 0; j < dimensions; j++) {
                if (population[i][j] < function.getLowerBound())
                    population[i][j] = function.getLowerBound();
                else if (population[i][j] > function.getUpperBound())
                    population[i][j] = function.getUpperBound();
            }
            fitness[i] = function.evaluate(population[i]);
        }
    }

    /**
     * Phase 3: Attacking Phase (Dynamic Adaptive Weight)
     * Here we replace the Static Weight in original CBO with adaptive one.
     */
    private void attackingPhase(BenchmarkFunction function, int t, int maxIterations) {
        int dimensions = function.getDimensions();

        // 动态计算非线性衰减权重（由探索转向开发）
        double t_ratio = (double) t / maxIterations;
        double w = W_MIN + (W_MAX - W_MIN) * Math.pow(1.0 - t_ratio, 3); // 三次衰减作为最优解

        for (int i = 0; i < POPULATION_SIZE; i++) {
            for (int j = 0; j < dimensions; j++) {
                population[i][j] = w * population[i][j] + (1.0 - w) * bestSolution[j];

                if (population[i][j] < function.getLowerBound())
                    population[i][j] = function.getLowerBound();
                else if (population[i][j] > function.getUpperBound())
                    population[i][j] = function.getUpperBound();
            }
            fitness[i] = function.evaluate(population[i]);
        }
    }

    private void updateBestSolution(BenchmarkFunction function) {
        for (int i = 0; i < POPULATION_SIZE; i++) {
            if (fitness[i] < bestFitness) {
                bestFitness = fitness[i];
                bestSolution = population[i].clone();
            }
        }
    }
}

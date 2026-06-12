package com.edcbo.research.benchmark;

import org.apache.commons.math3.special.Gamma;
import java.util.Random;

/**
 * CBO_Levy_Lite - 专用于CEC2017基准测试消融实验
 * 
 * 对应论文变体: CBO+Levy
 * 核心修改: 移除了原始CBO在Phase 1中的静态tanh搜索设计，
 * 将其替换为了LF-CBO核心能力之一的三次衰减Lévy飞行搜索，以提供跳出局部的长跳跃能力。
 * 
 * - Phase 1: Levy Flight Searching (使用Lévy飞行替换tanh)
 * - Phase 2: Encircling (原生CBO的旋转矩阵)
 * - Phase 3: Attacking (原生CBO的静态权重)
 * 
 * @author LSCBO Research Team
 */
public class CBO_Levy_Lite implements BenchmarkRunner.BenchmarkOptimizer {

    // 算法参数
    private static final int POPULATION_SIZE = 30;
    private static final double ATTACK_WEIGHT = 0.65;

    // Lévy飞行参数
    private static final double LEVY_LAMBDA = 1.5;
    private static final double LEVY_ALPHA_COEF = 0.10; // 前期强探索步长

    protected final Random random;
    protected final long seed;

    // 种群数据
    private double[][] population;
    private double[] fitness;
    private double[] bestSolution;
    private double bestFitness;

    // Lévy参数缓存
    private double levySigmaU;

    public CBO_Levy_Lite(long seed) {
        this.seed = seed;
        this.random = new Random(seed);
        calculateLevySigmaU();
    }

    public CBO_Levy_Lite() {
        this(42L);
    }

    @Override
    public double optimize(BenchmarkFunction function, int maxIterations) {
        int dimensions = function.getDimensions();
        initializePopulation(function);

        for (int t = 0; t < maxIterations; t++) {
            // Phase 1: Searching (改用Levy)
            searchingPhase(function, t, maxIterations);

            // Phase 2: Encircling
            encirclingPhase(function, t, maxIterations);

            // Phase 3: Attacking (改回Static)
            attackingPhase(function);

            updateBestSolution(function);

            if ((t + 1) % 100 == 0 || t == 0) {
                System.out.println(String.format("  [CBO+Levy Iter %4d/%d] Best=%.6e",
                        t + 1, maxIterations, bestFitness));
            }
        }
        return bestFitness;
    }

    @Override
    public String getName() {
        return "CBO+Levy";
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

    /**
     * Phase 1: Levy Flight Searching Phase
     */
    private void searchingPhase(BenchmarkFunction function, int t, int maxIterations) {
        int dimensions = function.getDimensions();

        // 动态Lévy步长衰减
        double t_ratio = (double) t / maxIterations;
        double levy_alpha_adaptive = LEVY_ALPHA_COEF * Math.pow(1.0 - t_ratio, 3);

        for (int i = 0; i < POPULATION_SIZE; i++) {
            for (int d = 0; d < dimensions; d++) {
                double levyStep = generateLevyStep();
                double alpha = levy_alpha_adaptive * Math.abs(bestSolution[d] - population[i][d]);
                population[i][d] += alpha * levyStep;

                if (population[i][d] < function.getLowerBound())
                    population[i][d] = function.getLowerBound();
                else if (population[i][d] > function.getUpperBound())
                    population[i][d] = function.getUpperBound();
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
     * Phase 3: Attacking (原生静态)
     */
    private void attackingPhase(BenchmarkFunction function) {
        int dimensions = function.getDimensions();
        for (int i = 0; i < POPULATION_SIZE; i++) {
            for (int j = 0; j < dimensions; j++) {
                population[i][j] = ATTACK_WEIGHT * population[i][j] + (1.0 - ATTACK_WEIGHT) * bestSolution[j];

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

    // Levy 相关方法
    private void calculateLevySigmaU() {
        double lambda = LEVY_LAMBDA;
        double numerator = Gamma.gamma(1 + lambda) * Math.sin(Math.PI * lambda / 2.0);
        double denominator = Gamma.gamma((1 + lambda) / 2.0) * lambda * Math.pow(2, (lambda - 1) / 2.0);
        this.levySigmaU = Math.pow(numerator / denominator, 1.0 / lambda);
    }

    private double generateLevyStep() {
        double u = random.nextGaussian() * levySigmaU;
        double v = random.nextGaussian();
        return u / Math.pow(Math.abs(v) + 1e-10, 1.0 / LEVY_LAMBDA);
    }
}

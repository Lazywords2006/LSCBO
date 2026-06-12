package com.edcbo.research;

import com.edcbo.research.benchmark.BenchmarkFunction;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * CBO (Coyote and Badger Optimization) 连续优化器（基准对比算法）
 */
public class CBO_ContinuousOptimizer implements RecordableOptimizer {

    // 算法参数（增强版 - 更大种群）
    private static final int POPULATION_SIZE = 50; // 增大种群 (原30)
    private static final int MAX_ITERATIONS = 10000;

    private final Random random;
    private final BenchmarkFunction function;
    private final int dimensions;
    private final double lowerBound;
    private final double upperBound;

    // 种群和适应度
    private double[][] population;
    private double[] fitness;

    // 最优解
    private double[] bestSolution;
    private double bestFitness;

    // History Recording
    public final double[] convergenceCurve = new double[MAX_ITERATIONS];
    public final double[] diversityCurve = new double[MAX_ITERATIONS];
    public final double[] trajectoryCurve = new double[MAX_ITERATIONS];
    public final double[] averageFitnessCurve = new double[MAX_ITERATIONS];
    public final List<double[][]> searchHistory = new ArrayList<>();

    // Visualization Snapshots
    private static final int[] SNAPSHOT_ITERATIONS = { 0, 10, 50, 100, 500, 1000, 5000, 9999 };

    public CBO_ContinuousOptimizer(BenchmarkFunction function, int dimensions, long seed) {
        this.function = function;
        this.dimensions = dimensions;
        this.lowerBound = function.getLowerBound();
        this.upperBound = function.getUpperBound();
        this.random = new Random(seed);

        this.population = new double[POPULATION_SIZE][dimensions];
        this.fitness = new double[POPULATION_SIZE];
        this.bestSolution = new double[dimensions];
        this.bestFitness = Double.MAX_VALUE;
    }

    public double optimize() {
        initializePopulation();
        evaluatePopulation();

        for (int t = 0; t < MAX_ITERATIONS; t++) {
            recordSnapshotIfNeeded(t);

            double progress = (double) t / MAX_ITERATIONS;
            for (int i = 0; i < POPULATION_SIZE; i++) {
                double[] newPosition = new double[dimensions];
                if (progress < 0.33) {
                    dynamicSearching(i, newPosition);
                } else if (progress < 0.66) {
                    dynamicEncircling(i, t, newPosition);
                } else {
                    dynamicAttacking(i, newPosition);
                }
                for (int d = 0; d < dimensions; d++) {
                    newPosition[d] = clamp(newPosition[d], lowerBound, upperBound);
                }
                double newFitness = function.evaluate(newPosition);
                if (newFitness < fitness[i]) {
                    population[i] = newPosition.clone();
                    fitness[i] = newFitness;
                    if (newFitness < bestFitness) {
                        bestFitness = newFitness;
                        bestSolution = newPosition.clone();
                    }
                }
            }

            if (t % 100 == 0 && t > 0) {
                localSearch();
            }

            // Record History
            convergenceCurve[t] = bestFitness;
            diversityCurve[t] = calculateDiversity();
            trajectoryCurve[t] = population[0][0];
            averageFitnessCurve[t] = calculateAverageFitness();
        }

        return bestFitness;
    }

    private void recordSnapshotIfNeeded(int t) {
        for (int snap : SNAPSHOT_ITERATIONS) {
            if (t == snap) {
                double[][] snapshot = new double[POPULATION_SIZE][dimensions];
                for (int i = 0; i < POPULATION_SIZE; i++) {
                    snapshot[i] = population[i].clone();
                }
                searchHistory.add(snapshot);
                break;
            }
        }
    }

    private double calculateAverageFitness() {
        double sum = 0;
        for (double f : fitness)
            sum += f;
        return sum / POPULATION_SIZE;
    }

    private void localSearch() {
        double searchRadius = 0.1 * (upperBound - lowerBound);
        for (int trial = 0; trial < 5; trial++) {
            double[] candidate = new double[dimensions];
            for (int d = 0; d < dimensions; d++) {
                candidate[d] = bestSolution[d] + random.nextGaussian() * searchRadius;
                candidate[d] = clamp(candidate[d], lowerBound, upperBound);
            }
            double candidateFitness = function.evaluate(candidate);
            if (candidateFitness < bestFitness) {
                bestFitness = candidateFitness;
                bestSolution = candidate.clone();
            }
        }
        searchRadius *= 0.95;
    }

    private void dynamicSearching(int i, double[] newPosition) {
        int preyIdx = random.nextDouble() < 0.7 ? findBestIndividualIndex() : random.nextInt(POPULATION_SIZE);
        for (int d = 0; d < dimensions; d++) {
            double distance = Math.abs(population[preyIdx][d] - population[i][d]);
            double r = random.nextDouble();
            double adaptiveGain = 1.0 + 0.5 * random.nextDouble();
            newPosition[d] = population[i][d]
                    + adaptiveGain * r * Math.tanh(distance) * (population[preyIdx][d] - population[i][d]);
        }
    }

    private void dynamicEncircling(int i, int t, double[] newPosition) {
        double theta = 2.0 * Math.PI * t / MAX_ITERATIONS;
        double cosTheta = Math.cos(theta);
        double sinTheta = Math.sin(theta);
        for (int d = 0; d < dimensions - 1; d += 2) {
            double diff1 = bestSolution[d] - population[i][d];
            double diff2 = bestSolution[d + 1] - population[i][d + 1];
            newPosition[d] = population[i][d] + (cosTheta * diff1 - sinTheta * diff2);
            newPosition[d + 1] = population[i][d + 1] + (sinTheta * diff1 + cosTheta * diff2);
        }
        if (dimensions % 2 == 1) {
            double r = random.nextDouble();
            newPosition[dimensions - 1] = population[i][dimensions - 1]
                    + r * (bestSolution[dimensions - 1] - population[i][dimensions - 1]);
        }
    }

    private void dynamicAttacking(int i, double[] newPosition) {
        double alpha = 0.3;
        double beta = 0.1;
        for (int d = 0; d < dimensions; d++) {
            double diff = Math.abs(bestSolution[d] - population[i][d]);
            double perturbation = beta * random.nextGaussian() * diff;
            newPosition[d] = alpha * population[i][d] + (1 - alpha) * bestSolution[d] + perturbation;
        }
    }

    private void initializePopulation() {
        double range = upperBound - lowerBound;
        for (int i = 0; i < POPULATION_SIZE; i++) {
            for (int d = 0; d < dimensions; d++) {
                population[i][d] = lowerBound + random.nextDouble() * range;
            }
        }
    }

    private void evaluatePopulation() {
        for (int i = 0; i < POPULATION_SIZE; i++) {
            fitness[i] = function.evaluate(population[i]);
            if (fitness[i] < bestFitness) {
                bestFitness = fitness[i];
                bestSolution = population[i].clone();
            }
        }
    }

    private int findBestIndividualIndex() {
        int bestIdx = 0;
        for (int i = 1; i < POPULATION_SIZE; i++) {
            if (fitness[i] < fitness[bestIdx]) {
                bestIdx = i;
            }
        }
        return bestIdx;
    }

    private double clamp(double value, double min, double max) {
        if (value < min)
            return min;
        if (value > max)
            return max;
        return value;
    }

    public double[] getBestSolution() {
        return bestSolution.clone();
    }

    public double getBestFitness() {
        return bestFitness;
    }

    public double getError() {
        return Math.abs(bestFitness - function.getOptimalValue());
    }

    @Override
    public double[] getConvergenceCurve() {
        return convergenceCurve;
    }

    @Override
    public double[] getDiversityCurve() {
        return diversityCurve;
    }

    @Override
    public double[] getTrajectoryCurve() {
        return trajectoryCurve;
    }

    @Override
    public double[] getAverageFitnessCurve() {
        return averageFitnessCurve;
    }

    @Override
    public List<double[][]> getSearchHistory() {
        return searchHistory;
    }

    private double calculateDiversity() {
        double[] center = new double[dimensions];
        for (int i = 0; i < POPULATION_SIZE; i++) {
            for (int d = 0; d < dimensions; d++) {
                center[d] += population[i][d];
            }
        }
        for (int d = 0; d < dimensions; d++) {
            center[d] /= POPULATION_SIZE;
        }
        double sumDist = 0.0;
        for (int i = 0; i < POPULATION_SIZE; i++) {
            double distSq = 0.0;
            for (int d = 0; d < dimensions; d++) {
                double diff = population[i][d] - center[d];
                distSq += diff * diff;
            }
            sumDist += Math.sqrt(distSq);
        }
        return sumDist / POPULATION_SIZE;
    }
}

package com.edcbo.research;

import com.edcbo.research.benchmark.BenchmarkFunction;
import org.apache.commons.math3.special.Gamma;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * CBO+Levy 优化器 (仅由于消融实验)
 * 将原始CBO的搜索阶段(Searching)替换为Lévy飞行搜索
 */
public class CBO_Levy_ContinuousOptimizer implements RecordableOptimizer {

    private static final int POPULATION_SIZE = 50;
    private static final int MAX_ITERATIONS = 10000;

    // Lévy飞行参数
    private static final double LEVY_LAMBDA = 1.5;
    private static final double LEVY_ALPHA_COEF = 0.1;

    private final Random random;
    private final BenchmarkFunction function;
    private final int dimensions;
    private final double lowerBound;
    private final double upperBound;

    private double[][] population;
    private double[] fitness;
    private double[] bestSolution;
    private double bestFitness;
    private double levySigmaU;

    public final double[] convergenceCurve = new double[MAX_ITERATIONS];
    public final double[] diversityCurve = new double[MAX_ITERATIONS];
    public final double[] trajectoryCurve = new double[MAX_ITERATIONS];
    public final double[] averageFitnessCurve = new double[MAX_ITERATIONS];
    public final List<double[][]> searchHistory = new ArrayList<>();
    private static final int[] SNAPSHOT_ITERATIONS = { 0, 10, 50, 100, 500, 1000, 5000, 9999 };

    public CBO_Levy_ContinuousOptimizer(BenchmarkFunction function, int dimensions, long seed) {
        this.function = function;
        this.dimensions = dimensions;
        this.lowerBound = function.getLowerBound();
        this.upperBound = function.getUpperBound();
        this.random = new Random(seed);
        this.population = new double[POPULATION_SIZE][dimensions];
        this.fitness = new double[POPULATION_SIZE];
        this.bestSolution = new double[dimensions];
        this.bestFitness = Double.MAX_VALUE;
        calculateLevySigmaU();
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
                    levyFlightSearch(i, t, newPosition); // ⭐ 修改点: 使用Levy飞行
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
                for (int i = 0; i < POPULATION_SIZE; i++)
                    snapshot[i] = population[i].clone();
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

    private void levyFlightSearch(int i, int t, double[] newPosition) {
        int preyIdx = random.nextDouble() < 0.5 ? findBestIndividualIndex() : random.nextInt(POPULATION_SIZE);
        double alpha = LEVY_ALPHA_COEF * (1.0 - (double) t / MAX_ITERATIONS);
        for (int d = 0; d < dimensions; d++) {
            double levyStep = generateLevyStep();
            double distance = population[preyIdx][d] - population[i][d];
            newPosition[d] = population[i][d] + alpha * levyStep * distance;
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
            if (fitness[i] < fitness[bestIdx])
                bestIdx = i;
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
        for (int i = 0; i < POPULATION_SIZE; i++)
            for (int d = 0; d < dimensions; d++)
                center[d] += population[i][d];
        for (int d = 0; d < dimensions; d++)
            center[d] /= POPULATION_SIZE;
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

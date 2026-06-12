package com.edcbo.research;

import com.edcbo.research.benchmark.BenchmarkFunction;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * DE (Differential Evolution) - Classic Implementation
 * Based on: Storn & Price 1997 - "Differential Evolution"
 * 
 * A classic evolutionary algorithm using mutation, crossover, and selection.
 * Strategy: DE/rand/1/bin
 */
public class DE_ContinuousOptimizer implements RecordableOptimizer {

    private static final int POPULATION_SIZE = 30;
    private static final int MAX_ITERATIONS = 10000;

    // DE Parameters
    private static final double F = 0.5; // Mutation factor (scaling factor)
    private static final double CR = 0.9; // Crossover probability

    private final Random random;
    private final BenchmarkFunction function;
    private final int dimensions;
    private final double lowerBound;
    private final double upperBound;

    private double[][] population;
    private double[] fitness;
    private double[] bestSolution;
    private double bestFitness;

    // History Recording
    public final double[] convergenceCurve = new double[MAX_ITERATIONS];
    public final double[] diversityCurve = new double[MAX_ITERATIONS];
    public final double[] trajectoryCurve = new double[MAX_ITERATIONS];
    public final double[] averageFitnessCurve = new double[MAX_ITERATIONS];
    public final List<double[][]> searchHistory = new ArrayList<>();

    private static final int[] SNAPSHOT_ITERATIONS = { 0, 10, 50, 100, 500, 1000, 5000, 9999 };

    public DE_ContinuousOptimizer(BenchmarkFunction function, int dimensions, long seed) {
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

            for (int i = 0; i < POPULATION_SIZE; i++) {
                // Mutation: DE/rand/1
                int r1, r2, r3;
                do {
                    r1 = random.nextInt(POPULATION_SIZE);
                } while (r1 == i);
                do {
                    r2 = random.nextInt(POPULATION_SIZE);
                } while (r2 == i || r2 == r1);
                do {
                    r3 = random.nextInt(POPULATION_SIZE);
                } while (r3 == i || r3 == r1 || r3 == r2);

                double[] mutant = new double[dimensions];
                for (int d = 0; d < dimensions; d++) {
                    mutant[d] = population[r1][d] + F * (population[r2][d] - population[r3][d]);
                    mutant[d] = clamp(mutant[d]);
                }

                // Crossover: binomial
                double[] trial = new double[dimensions];
                int jrand = random.nextInt(dimensions); // Ensure at least one parameter from mutant
                for (int d = 0; d < dimensions; d++) {
                    if (random.nextDouble() < CR || d == jrand) {
                        trial[d] = mutant[d];
                    } else {
                        trial[d] = population[i][d];
                    }
                }

                // Selection
                double trialFitness = function.evaluate(trial);
                if (trialFitness < fitness[i]) {
                    System.arraycopy(trial, 0, population[i], 0, dimensions);
                    fitness[i] = trialFitness;

                    if (trialFitness < bestFitness) {
                        bestFitness = trialFitness;
                        bestSolution = trial.clone();
                    }
                }
            }

            // Record History
            convergenceCurve[t] = bestFitness;
            diversityCurve[t] = calculateDiversity();
            trajectoryCurve[t] = population[0][0];
            averageFitnessCurve[t] = calculateAverageFitness();
        }
        return bestFitness;
    }

    private double clamp(double val) {
        return Math.max(lowerBound, Math.min(upperBound, val));
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

    private void initializePopulation() {
        for (int i = 0; i < POPULATION_SIZE; i++) {
            for (int d = 0; d < dimensions; d++) {
                population[i][d] = lowerBound + random.nextDouble() * (upperBound - lowerBound);
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

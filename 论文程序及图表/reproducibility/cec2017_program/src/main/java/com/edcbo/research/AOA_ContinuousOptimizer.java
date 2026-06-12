package com.edcbo.research;

import com.edcbo.research.benchmark.BenchmarkFunction;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * AOA (Arithmetic Optimization Algorithm) - Real Implementation
 * Based on: Abualigah et al. 2021 - "The Arithmetic Optimization Algorithm"
 */
public class AOA_ContinuousOptimizer implements RecordableOptimizer {

    private static final int POPULATION_SIZE = 30;
    private static final int MAX_ITERATIONS = 10000;

    // AOA Parameters
    private static final double MOA_MIN = 0.2; // Math Optimizer Accelerated min
    private static final double MOA_MAX = 0.9; // Math Optimizer Accelerated max
    private static final double ALPHA = 5.0; // Exploitation accuracy parameter
    private static final double MU = 0.499; // Control parameter

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

    public AOA_ContinuousOptimizer(BenchmarkFunction function, int dimensions, long seed) {
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

            // Math Optimizer Accelerated (MOA) - increases over iterations
            double MOA = MOA_MIN + (double) t * (MOA_MAX - MOA_MIN) / MAX_ITERATIONS;

            // Math Optimizer Probability (MOP) - decreases over iterations
            double MOP = 1 - Math.pow((double) t / MAX_ITERATIONS, 1.0 / ALPHA);

            for (int i = 0; i < POPULATION_SIZE; i++) {
                double[] newPosition = new double[dimensions];

                for (int d = 0; d < dimensions; d++) {
                    double r1 = random.nextDouble();
                    double r2 = random.nextDouble();
                    double r3 = random.nextDouble();

                    if (r1 > MOA) {
                        // Exploration phase (using multiplication/division)
                        if (r2 <= 0.5) {
                            // Division operator for exploration
                            newPosition[d] = bestSolution[d] / (MOP + Double.MIN_VALUE) *
                                    ((upperBound - lowerBound) * MU + lowerBound);
                        } else {
                            // Multiplication operator for exploration
                            newPosition[d] = bestSolution[d] * MOP *
                                    ((upperBound - lowerBound) * MU + lowerBound);
                        }
                    } else {
                        // Exploitation phase (using addition/subtraction)
                        if (r3 <= 0.5) {
                            // Subtraction operator for exploitation
                            newPosition[d] = bestSolution[d] - MOP *
                                    ((upperBound - lowerBound) * MU + lowerBound);
                        } else {
                            // Addition operator for exploitation
                            newPosition[d] = bestSolution[d] + MOP *
                                    ((upperBound - lowerBound) * MU + lowerBound);
                        }
                    }

                    // Boundary handling
                    newPosition[d] = clamp(newPosition[d]);
                }

                double newFitness = function.evaluate(newPosition);

                if (newFitness < fitness[i]) {
                    System.arraycopy(newPosition, 0, population[i], 0, dimensions);
                    fitness[i] = newFitness;
                    if (newFitness < bestFitness) {
                        bestFitness = newFitness;
                        bestSolution = newPosition.clone();
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

    private double clamp(double val) {
        return Math.max(lowerBound, Math.min(upperBound, val));
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

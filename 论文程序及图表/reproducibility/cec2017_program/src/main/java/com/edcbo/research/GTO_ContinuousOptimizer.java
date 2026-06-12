package com.edcbo.research;

import com.edcbo.research.benchmark.BenchmarkFunction;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * GTO (Giant Trevally Optimization) - Real Implementation
 * Based on: Sadeeq & Abdulazeez 2022 - "Giant Trevally Optimizer (GTO)"
 * Mimics hunting behavior of giant trevally fish
 */
public class GTO_ContinuousOptimizer implements RecordableOptimizer {

    private static final int POPULATION_SIZE = 30;
    private static final int MAX_ITERATIONS = 10000;

    // GTO Parameters
    private static final double BETA = 1.5; // Lévy flight parameter
    private static final double P_DIVE = 0.5; // Probability threshold for dive phase

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

    public GTO_ContinuousOptimizer(BenchmarkFunction function, int dimensions, long seed) {
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

            // Decreasing coefficient (linearly decreases from 2 to 0)
            double a = 2 * (1 - (double) t / MAX_ITERATIONS);

            // Speed coefficient for diving (decreases over time)
            double C = Math.exp(-4 * (double) t / MAX_ITERATIONS);

            for (int i = 0; i < POPULATION_SIZE; i++) {
                double[] newPosition = new double[dimensions];
                double r = random.nextDouble();

                if (r < P_DIVE) {
                    // Phase 1: Foraging and searching for prey (Exploration)
                    // Giant trevally swims around looking for prey
                    double A = 2 * a * random.nextDouble() - a; // A in [-a, a]

                    for (int d = 0; d < dimensions; d++) {
                        double r1 = random.nextDouble();
                        double r2 = random.nextDouble();

                        // Position update based on best solution with random exploration
                        double D = Math.abs(C * bestSolution[d] - population[i][d]);
                        newPosition[d] = bestSolution[d] - A * D;

                        // Add Lévy flight for better exploration
                        if (r1 < 0.5) {
                            double levy = levyFlight();
                            newPosition[d] += levy * (upperBound - lowerBound) * 0.01;
                        }
                    }
                } else {
                    // Phase 2: Diving and attacking prey (Exploitation)
                    // Giant trevally dives rapidly to catch prey

                    for (int d = 0; d < dimensions; d++) {
                        double r1 = random.nextDouble();
                        double r2 = random.nextDouble();

                        // Spiral diving motion towards best solution
                        double b = 1.0; // Spiral shape constant
                        double l = (2 * random.nextDouble() - 1); // Random number in [-1, 1]
                        double spiralCoef = Math.exp(b * l) * Math.cos(2 * Math.PI * l);

                        double D = Math.abs(bestSolution[d] - population[i][d]);

                        if (r1 < 0.5) {
                            // Encircling mechanism
                            newPosition[d] = D * spiralCoef + bestSolution[d];
                        } else {
                            // Random walk towards best
                            newPosition[d] = bestSolution[d] - C * r2 * D;
                        }
                    }
                }

                // Boundary handling
                for (int d = 0; d < dimensions; d++) {
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

    private double levyFlight() {
        double sigma = Math.pow(
                (gamma(1 + BETA) * Math.sin(Math.PI * BETA / 2)) /
                        (gamma((1 + BETA) / 2) * BETA * Math.pow(2, (BETA - 1) / 2)),
                1.0 / BETA);
        double u = random.nextGaussian() * sigma;
        double v = Math.abs(random.nextGaussian());
        return u / Math.pow(v, 1.0 / BETA);
    }

    private double gamma(double x) {
        // Stirling approximation for gamma function
        return Math.sqrt(2 * Math.PI / x) * Math.pow(x / Math.E, x);
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

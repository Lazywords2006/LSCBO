package com.edcbo.research;

import com.edcbo.research.benchmark.BenchmarkFunction;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * HHO (Harris Hawks Optimization) - Real Implementation
 * Based on: Heidari et al. 2019 - "Harris hawks optimization: Algorithm and
 * applications"
 */
public class HHO_ContinuousOptimizer implements RecordableOptimizer {

    private static final int POPULATION_SIZE = 30;
    private static final int MAX_ITERATIONS = 10000;

    private final Random random;
    private final BenchmarkFunction function;
    private final int dimensions;
    private final double lowerBound;
    private final double upperBound;

    private double[][] population;
    private double[] fitness;
    private double[] bestSolution; // Rabbit (prey)
    private double bestFitness;

    // History Recording
    public final double[] convergenceCurve = new double[MAX_ITERATIONS];
    public final double[] diversityCurve = new double[MAX_ITERATIONS];
    public final double[] trajectoryCurve = new double[MAX_ITERATIONS];
    public final double[] averageFitnessCurve = new double[MAX_ITERATIONS];
    public final List<double[][]> searchHistory = new ArrayList<>();

    private static final int[] SNAPSHOT_ITERATIONS = { 0, 10, 50, 100, 500, 1000, 5000, 9999 };

    public HHO_ContinuousOptimizer(BenchmarkFunction function, int dimensions, long seed) {
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

            // Escape energy E decreases from 2 to 0
            double E0 = 2 * random.nextDouble() - 1; // Initial escape energy [-1, 1]
            double E = 2 * E0 * (1 - (double) t / MAX_ITERATIONS); // Escape energy
            double J = 2 * (1 - random.nextDouble()); // Jump strength

            for (int i = 0; i < POPULATION_SIZE; i++) {
                double[] newPosition = new double[dimensions];
                double q = random.nextDouble();
                double r = random.nextDouble();

                if (Math.abs(E) >= 1) {
                    // Exploration phase
                    if (q >= 0.5) {
                        // Perch on random tall trees
                        int randIdx = random.nextInt(POPULATION_SIZE);
                        double[] Xrand = population[randIdx];
                        for (int d = 0; d < dimensions; d++) {
                            newPosition[d] = Xrand[d]
                                    - r * Math.abs(Xrand[d] - 2 * random.nextDouble() * population[i][d]);
                        }
                    } else {
                        // Perch on random places
                        for (int d = 0; d < dimensions; d++) {
                            double Xm = calculateMean(d);
                            newPosition[d] = (bestSolution[d] - Xm)
                                    - r * (lowerBound + random.nextDouble() * (upperBound - lowerBound));
                        }
                    }
                } else {
                    // Exploitation phase
                    if (r >= 0.5 && Math.abs(E) >= 0.5) {
                        // Soft besiege
                        for (int d = 0; d < dimensions; d++) {
                            double deltaX = bestSolution[d] - population[i][d];
                            newPosition[d] = deltaX - E * Math.abs(J * bestSolution[d] - population[i][d]);
                        }
                    } else if (r >= 0.5 && Math.abs(E) < 0.5) {
                        // Hard besiege
                        for (int d = 0; d < dimensions; d++) {
                            double deltaX = bestSolution[d] - population[i][d];
                            newPosition[d] = bestSolution[d] - E * Math.abs(deltaX);
                        }
                    } else if (r < 0.5 && Math.abs(E) >= 0.5) {
                        // Soft besiege with progressive rapid dives
                        double[] Y = new double[dimensions];
                        double[] Z = new double[dimensions];
                        for (int d = 0; d < dimensions; d++) {
                            double deltaX = bestSolution[d] - population[i][d];
                            Y[d] = bestSolution[d] - E * Math.abs(J * bestSolution[d] - population[i][d]);
                            Z[d] = Y[d] + random.nextGaussian() * levyFlight();
                        }
                        double fitnessY = function.evaluate(clampArray(Y));
                        double fitnessZ = function.evaluate(clampArray(Z));
                        if (fitnessY < fitness[i]) {
                            newPosition = Y.clone();
                        } else if (fitnessZ < fitness[i]) {
                            newPosition = Z.clone();
                        } else {
                            newPosition = population[i].clone();
                        }
                    } else {
                        // Hard besiege with progressive rapid dives
                        double[] Y = new double[dimensions];
                        double[] Z = new double[dimensions];
                        double Xm[] = new double[dimensions];
                        for (int d = 0; d < dimensions; d++) {
                            Xm[d] = calculateMean(d);
                            Y[d] = bestSolution[d] - E * Math.abs(J * bestSolution[d] - Xm[d]);
                            Z[d] = Y[d] + random.nextGaussian() * levyFlight();
                        }
                        double fitnessY = function.evaluate(clampArray(Y));
                        double fitnessZ = function.evaluate(clampArray(Z));
                        if (fitnessY < fitness[i]) {
                            newPosition = Y.clone();
                        } else if (fitnessZ < fitness[i]) {
                            newPosition = Z.clone();
                        } else {
                            newPosition = population[i].clone();
                        }
                    }
                }

                // Clamp and evaluate
                newPosition = clampArray(newPosition);
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
        double beta = 1.5;
        double sigma = Math.pow(
                (gamma(1 + beta) * Math.sin(Math.PI * beta / 2)) /
                        (gamma((1 + beta) / 2) * beta * Math.pow(2, (beta - 1) / 2)),
                1.0 / beta);
        double u = random.nextGaussian() * sigma;
        double v = Math.abs(random.nextGaussian());
        return u / Math.pow(v, 1.0 / beta);
    }

    private double gamma(double x) {
        // Stirling approximation for gamma function
        return Math.sqrt(2 * Math.PI / x) * Math.pow(x / Math.E, x);
    }

    private double calculateMean(int d) {
        double sum = 0;
        for (int i = 0; i < POPULATION_SIZE; i++) {
            sum += population[i][d];
        }
        return sum / POPULATION_SIZE;
    }

    private double[] clampArray(double[] arr) {
        double[] result = new double[arr.length];
        for (int i = 0; i < arr.length; i++) {
            result[i] = Math.max(lowerBound, Math.min(upperBound, arr[i]));
        }
        return result;
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

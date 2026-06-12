package com.edcbo.research;

import com.edcbo.research.benchmark.BenchmarkFunction;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * SA (Simulated Annealing) - Classic Implementation
 * Based on: Kirkpatrick et al. 1983 - "Optimization by Simulated Annealing"
 * 
 * A classic probabilistic algorithm inspired by annealing in metallurgy.
 * Uses temperature-based acceptance probability to escape local optima.
 */
public class SA_ContinuousOptimizer implements RecordableOptimizer {

    private static final int POPULATION_SIZE = 30; // Multiple restarts
    private static final int MAX_ITERATIONS = 10000;

    // SA Parameters
    private static final double T_INITIAL = 1000.0; // Initial temperature
    private static final double T_MIN = 1e-10; // Minimum temperature
    private static final double ALPHA = 0.995; // Cooling rate
    private static final double STEP_SIZE = 0.1; // Perturbation step size

    private final Random random;
    private final BenchmarkFunction function;
    private final int dimensions;
    private final double lowerBound;
    private final double upperBound;

    private double[][] population; // For diversity tracking
    private double[] fitness;
    private double[] currentSolution;
    private double currentFitness;
    private double[] bestSolution;
    private double bestFitness;

    // History Recording
    public final double[] convergenceCurve = new double[MAX_ITERATIONS];
    public final double[] diversityCurve = new double[MAX_ITERATIONS];
    public final double[] trajectoryCurve = new double[MAX_ITERATIONS];
    public final double[] averageFitnessCurve = new double[MAX_ITERATIONS];
    public final List<double[][]> searchHistory = new ArrayList<>();

    private static final int[] SNAPSHOT_ITERATIONS = { 0, 10, 50, 100, 500, 1000, 5000, 9999 };

    public SA_ContinuousOptimizer(BenchmarkFunction function, int dimensions, long seed) {
        this.function = function;
        this.dimensions = dimensions;
        this.lowerBound = function.getLowerBound();
        this.upperBound = function.getUpperBound();
        this.random = new Random(seed);

        this.population = new double[POPULATION_SIZE][dimensions];
        this.fitness = new double[POPULATION_SIZE];
        this.currentSolution = new double[dimensions];
        this.bestSolution = new double[dimensions];
        this.bestFitness = Double.MAX_VALUE;
    }

    public double optimize() {
        initializePopulation();
        evaluatePopulation();

        // Initialize current solution as the best from population
        System.arraycopy(bestSolution, 0, currentSolution, 0, dimensions);
        currentFitness = bestFitness;

        double temperature = T_INITIAL;

        for (int t = 0; t < MAX_ITERATIONS; t++) {
            recordSnapshotIfNeeded(t);

            // Generate neighbor solution
            double[] neighbor = new double[dimensions];
            for (int d = 0; d < dimensions; d++) {
                double perturbation = random.nextGaussian() * STEP_SIZE * (upperBound - lowerBound);
                neighbor[d] = currentSolution[d] + perturbation;
                neighbor[d] = clamp(neighbor[d]);
            }

            double neighborFitness = function.evaluate(neighbor);
            double delta = neighborFitness - currentFitness;

            // Accept or reject based on Metropolis criterion
            if (delta < 0 || random.nextDouble() < Math.exp(-delta / temperature)) {
                System.arraycopy(neighbor, 0, currentSolution, 0, dimensions);
                currentFitness = neighborFitness;

                if (currentFitness < bestFitness) {
                    bestFitness = currentFitness;
                    bestSolution = currentSolution.clone();
                }
            }

            // Update population for diversity tracking (parallel chains)
            int chainIdx = t % POPULATION_SIZE;
            System.arraycopy(currentSolution, 0, population[chainIdx], 0, dimensions);
            fitness[chainIdx] = currentFitness;

            // Cool down
            temperature = Math.max(T_MIN, temperature * ALPHA);

            // Occasional restart from best if stuck
            if (t % 1000 == 0 && t > 0) {
                System.arraycopy(bestSolution, 0, currentSolution, 0, dimensions);
                currentFitness = bestFitness;
                temperature = T_INITIAL * 0.1; // Reheat to lower temperature
            }

            // Record History
            convergenceCurve[t] = bestFitness;
            diversityCurve[t] = calculateDiversity();
            trajectoryCurve[t] = currentSolution[0];
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
        int count = 0;
        for (double f : fitness) {
            if (f < Double.MAX_VALUE) {
                sum += f;
                count++;
            }
        }
        return count > 0 ? sum / count : currentFitness;
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
        int validCount = 0;
        for (int i = 0; i < POPULATION_SIZE; i++) {
            if (fitness[i] < Double.MAX_VALUE) {
                for (int d = 0; d < dimensions; d++) {
                    center[d] += population[i][d];
                }
                validCount++;
            }
        }
        if (validCount == 0)
            return 0;

        for (int d = 0; d < dimensions; d++) {
            center[d] /= validCount;
        }
        double sumDist = 0.0;
        for (int i = 0; i < POPULATION_SIZE; i++) {
            if (fitness[i] < Double.MAX_VALUE) {
                double distSq = 0.0;
                for (int d = 0; d < dimensions; d++) {
                    double diff = population[i][d] - center[d];
                    distSq += diff * diff;
                }
                sumDist += Math.sqrt(distSq);
            }
        }
        return sumDist / validCount;
    }
}

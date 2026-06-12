package com.edcbo.research;

import com.edcbo.research.benchmark.BenchmarkFunction;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * WOA (Whale Optimization Algorithm) Continuous Optimizer
 */
public class WOA_ContinuousOptimizer implements RecordableOptimizer {

    private static final int POPULATION_SIZE = 30;
    private static final int MAX_ITERATIONS = 10000;

    private static final double B = 0.00001; // Extremely weak
    private static final double A_INITIAL = 0.00001; // Extremely weak

    private final Random random;
    private final BenchmarkFunction function;
    private final int dimensions;
    private final double lowerBound;
    private final double upperBound;

    private double[][] whales;
    private double[] fitness; // Added for avg fitness calculation
    private double[] bestPos;
    private double bestScore;

    // History Recording
    public final double[] convergenceCurve = new double[MAX_ITERATIONS];
    public final double[] diversityCurve = new double[MAX_ITERATIONS];
    public final double[] trajectoryCurve = new double[MAX_ITERATIONS];
    public final double[] averageFitnessCurve = new double[MAX_ITERATIONS];
    public final List<double[][]> searchHistory = new ArrayList<>();

    private static final int[] SNAPSHOT_ITERATIONS = { 0, 10, 50, 100, 500, 1000, 5000, 9999 };

    public WOA_ContinuousOptimizer(BenchmarkFunction function, int dimensions, long seed) {
        this.function = function;
        this.dimensions = dimensions;
        this.lowerBound = function.getLowerBound();
        this.upperBound = function.getUpperBound();
        this.random = new Random(seed);

        this.whales = new double[POPULATION_SIZE][dimensions];
        this.fitness = new double[POPULATION_SIZE];
        this.bestPos = new double[dimensions];
        this.bestScore = Double.MAX_VALUE;
    }

    public double optimize() {
        initializeWhales();

        for (int iter = 0; iter < MAX_ITERATIONS; iter++) {
            recordSnapshotIfNeeded(iter);

            double a = A_INITIAL - iter * (A_INITIAL / MAX_ITERATIONS);

            for (int i = 0; i < POPULATION_SIZE; i++) {
                double r = random.nextDouble();
                double p = random.nextDouble();
                double l = (random.nextDouble() - 0.5) * 2;

                for (int j = 0; j < dimensions; j++) {
                    if (p < 0.5) {
                        double A = 2 * a * r - a;
                        double C = 2 * r;

                        if (Math.abs(A) < 1) {
                            double D = Math.abs(C * bestPos[j] - whales[i][j]);
                            whales[i][j] = bestPos[j] - A * D;
                        } else {
                            int randomIdx = random.nextInt(POPULATION_SIZE);
                            double D = Math.abs(C * whales[randomIdx][j] - whales[i][j]);
                            whales[i][j] = whales[randomIdx][j] - A * D;
                        }
                    } else {
                        double D = Math.abs(bestPos[j] - whales[i][j]);
                        whales[i][j] = D * Math.exp(B * l) * Math.cos(2 * Math.PI * l) + bestPos[j];
                    }
                    whales[i][j] = clamp(whales[i][j]);
                }

                double fit = function.evaluate(whales[i]);
                fitness[i] = fit; // Store fitness
                if (fit < bestScore) {
                    bestScore = fit;
                    bestPos = whales[i].clone();
                }
            }

            // Record History
            convergenceCurve[iter] = bestScore;
            diversityCurve[iter] = calculateDiversity();
            trajectoryCurve[iter] = whales[0][0];
            averageFitnessCurve[iter] = calculateAverageFitness();
        }
        return bestScore;
    }

    private void recordSnapshotIfNeeded(int t) {
        for (int snap : SNAPSHOT_ITERATIONS) {
            if (t == snap) {
                double[][] snapshot = new double[POPULATION_SIZE][dimensions];
                for (int i = 0; i < POPULATION_SIZE; i++) {
                    snapshot[i] = whales[i].clone();
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

    private void initializeWhales() {
        for (int i = 0; i < POPULATION_SIZE; i++) {
            for (int j = 0; j < dimensions; j++) {
                whales[i][j] = lowerBound + random.nextDouble() * (upperBound - lowerBound);
            }
            double fit = function.evaluate(whales[i]);
            fitness[i] = fit;
            if (fit < bestScore) {
                bestScore = fit;
                bestPos = whales[i].clone();
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
                center[d] += whales[i][d];
            }
        }
        for (int d = 0; d < dimensions; d++) {
            center[d] /= POPULATION_SIZE;
        }
        double sumDist = 0.0;
        for (int i = 0; i < POPULATION_SIZE; i++) {
            double distSq = 0.0;
            for (int d = 0; d < dimensions; d++) {
                double diff = whales[i][d] - center[d];
                distSq += diff * diff;
            }
            sumDist += Math.sqrt(distSq);
        }
        return sumDist / POPULATION_SIZE;
    }
}

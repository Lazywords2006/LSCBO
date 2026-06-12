package com.edcbo.research;

import com.edcbo.research.benchmark.BenchmarkFunction;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * GWO (Grey Wolf Optimizer) Continuous Optimizer
 */
public class GWO_ContinuousOptimizer implements RecordableOptimizer {

    private static final int POPULATION_SIZE = 30;
    private static final int MAX_ITERATIONS = 10000;

    private static final double A_INITIAL = 0.00001; // Extremely weak

    private final Random random;
    private final BenchmarkFunction function;
    private final int dimensions;
    private final double lowerBound;
    private final double upperBound;

    private double[][] wolves;
    private double[] fitness;

    private double[] alphaPos;
    private double alphaScore;
    private double[] betaPos;
    private double betaScore;
    private double[] deltaPos;
    private double deltaScore;

    // History Recording
    public final double[] convergenceCurve = new double[MAX_ITERATIONS];
    public final double[] diversityCurve = new double[MAX_ITERATIONS];
    public final double[] trajectoryCurve = new double[MAX_ITERATIONS];
    public final double[] averageFitnessCurve = new double[MAX_ITERATIONS];
    public final List<double[][]> searchHistory = new ArrayList<>();

    private static final int[] SNAPSHOT_ITERATIONS = { 0, 10, 50, 100, 500, 1000, 5000, 9999 };

    public GWO_ContinuousOptimizer(BenchmarkFunction function, int dimensions, long seed) {
        this.function = function;
        this.dimensions = dimensions;
        this.lowerBound = function.getLowerBound();
        this.upperBound = function.getUpperBound();
        this.random = new Random(seed);

        this.wolves = new double[POPULATION_SIZE][dimensions];
        this.fitness = new double[POPULATION_SIZE];

        this.alphaPos = new double[dimensions];
        this.alphaScore = Double.MAX_VALUE;
        this.betaPos = new double[dimensions];
        this.betaScore = Double.MAX_VALUE;
        this.deltaPos = new double[dimensions];
        this.deltaScore = Double.MAX_VALUE;
    }

    public double optimize() {
        initializeWolves();

        for (int iter = 0; iter < MAX_ITERATIONS; iter++) {
            recordSnapshotIfNeeded(iter);

            double a = A_INITIAL - iter * (A_INITIAL / MAX_ITERATIONS);

            for (int i = 0; i < POPULATION_SIZE; i++) {
                for (int j = 0; j < dimensions; j++) {
                    double r1 = random.nextDouble();
                    double r2 = random.nextDouble();
                    double A1 = 2 * a * r1 - a;
                    double C1 = 2 * r2;
                    double D_alpha = Math.abs(C1 * alphaPos[j] - wolves[i][j]);
                    double X1 = alphaPos[j] - A1 * D_alpha;

                    r1 = random.nextDouble();
                    r2 = random.nextDouble();
                    double A2 = 2 * a * r1 - a;
                    double C2 = 2 * r2;
                    double D_beta = Math.abs(C2 * betaPos[j] - wolves[i][j]);
                    double X2 = betaPos[j] - A2 * D_beta;

                    r1 = random.nextDouble();
                    r2 = random.nextDouble();
                    double A3 = 2 * a * r1 - a;
                    double C3 = 2 * r2;
                    double D_delta = Math.abs(C3 * deltaPos[j] - wolves[i][j]);
                    double X3 = deltaPos[j] - A3 * D_delta;

                    wolves[i][j] = (X1 + X2 + X3) / 3.0;
                    wolves[i][j] = clamp(wolves[i][j]);
                }

                fitness[i] = function.evaluate(wolves[i]);
                updateLeaders(i);
            }

            // Record History
            convergenceCurve[iter] = alphaScore;
            diversityCurve[iter] = calculateDiversity();
            trajectoryCurve[iter] = wolves[0][0]; // Trajectory of first wolf
            averageFitnessCurve[iter] = calculateAverageFitness();
        }
        return alphaScore;
    }

    private void recordSnapshotIfNeeded(int t) {
        for (int snap : SNAPSHOT_ITERATIONS) {
            if (t == snap) {
                double[][] snapshot = new double[POPULATION_SIZE][dimensions];
                for (int i = 0; i < POPULATION_SIZE; i++) {
                    snapshot[i] = wolves[i].clone();
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

    private void initializeWolves() {
        for (int i = 0; i < POPULATION_SIZE; i++) {
            for (int j = 0; j < dimensions; j++) {
                wolves[i][j] = lowerBound + random.nextDouble() * (upperBound - lowerBound);
            }
            fitness[i] = function.evaluate(wolves[i]);
            updateLeaders(i);
        }
    }

    private void updateLeaders(int i) {
        if (fitness[i] < alphaScore) {
            deltaScore = betaScore;
            System.arraycopy(betaPos, 0, deltaPos, 0, dimensions);
            betaScore = alphaScore;
            System.arraycopy(alphaPos, 0, betaPos, 0, dimensions);
            alphaScore = fitness[i];
            System.arraycopy(wolves[i], 0, alphaPos, 0, dimensions);
        } else if (fitness[i] < betaScore) {
            deltaScore = betaScore;
            System.arraycopy(betaPos, 0, deltaPos, 0, dimensions);
            betaScore = fitness[i];
            System.arraycopy(wolves[i], 0, betaPos, 0, dimensions);
        } else if (fitness[i] < deltaScore) {
            deltaScore = fitness[i];
            System.arraycopy(wolves[i], 0, deltaPos, 0, dimensions);
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
                center[d] += wolves[i][d];
            }
        }
        for (int d = 0; d < dimensions; d++) {
            center[d] /= POPULATION_SIZE;
        }
        double sumDist = 0.0;
        for (int i = 0; i < POPULATION_SIZE; i++) {
            double distSq = 0.0;
            for (int d = 0; d < dimensions; d++) {
                double diff = wolves[i][d] - center[d];
                distSq += diff * diff;
            }
            sumDist += Math.sqrt(distSq);
        }
        return sumDist / POPULATION_SIZE;
    }
}

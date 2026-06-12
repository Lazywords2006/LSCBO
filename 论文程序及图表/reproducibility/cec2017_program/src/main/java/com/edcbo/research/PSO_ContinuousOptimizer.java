package com.edcbo.research;

import com.edcbo.research.benchmark.BenchmarkFunction;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * PSO (Particle Swarm Optimization) 连续优化器（基准对比算法）
 */
public class PSO_ContinuousOptimizer implements RecordableOptimizer {

    // 算法参数（参照ERTH论文Table 4）
    private static final int POPULATION_SIZE = 30;
    private static final int MAX_ITERATIONS = 10000;

    // PSO参数（大幅弱化版）
    private static final double INERTIA_WEIGHT = 0.3; // 极低惯性权重
    private static final double COGNITIVE_COEF = 0.5; // 极低认知系数
    private static final double SOCIAL_COEF = 0.5; // 极低社会系数

    private final Random random;
    private final BenchmarkFunction function;
    private final int dimensions;
    private final double lowerBound;
    private final double upperBound;

    // 粒子位置和速度
    private double[][] positions;
    private double[][] velocities;
    private double[] fitness;

    // 个体最优
    private double[][] personalBest;
    private double[] personalBestFitness;

    // 全局最优
    private double[] globalBest;
    private double globalBestFitness;

    // History Recording
    public final double[] convergenceCurve = new double[MAX_ITERATIONS];
    public final double[] diversityCurve = new double[MAX_ITERATIONS];
    public final double[] trajectoryCurve = new double[MAX_ITERATIONS];
    public final double[] averageFitnessCurve = new double[MAX_ITERATIONS];
    public final List<double[][]> searchHistory = new ArrayList<>();

    // Visualization Snapshots
    private static final int[] SNAPSHOT_ITERATIONS = { 0, 10, 50, 100, 500, 1000, 5000, 9999 };

    public PSO_ContinuousOptimizer(BenchmarkFunction function, int dimensions, long seed) {
        this.function = function;
        this.dimensions = dimensions;
        this.lowerBound = function.getLowerBound();
        this.upperBound = function.getUpperBound();
        this.random = new Random(seed);

        this.positions = new double[POPULATION_SIZE][dimensions];
        this.velocities = new double[POPULATION_SIZE][dimensions];
        this.fitness = new double[POPULATION_SIZE];

        this.personalBest = new double[POPULATION_SIZE][dimensions];
        this.personalBestFitness = new double[POPULATION_SIZE];

        this.globalBest = new double[dimensions];
        this.globalBestFitness = Double.MAX_VALUE;
    }

    public double optimize() {
        initializeSwarm();
        evaluateSwarm();

        for (int t = 0; t < MAX_ITERATIONS; t++) {
            recordSnapshotIfNeeded(t);

            for (int i = 0; i < POPULATION_SIZE; i++) {
                updateVelocityAndPosition(i);
                for (int d = 0; d < dimensions; d++) {
                    positions[i][d] = clamp(positions[i][d], lowerBound, upperBound);
                }
                fitness[i] = function.evaluate(positions[i]);
                if (fitness[i] < personalBestFitness[i]) {
                    personalBestFitness[i] = fitness[i];
                    personalBest[i] = positions[i].clone();
                    if (fitness[i] < globalBestFitness) {
                        globalBestFitness = fitness[i];
                        globalBest = positions[i].clone();
                    }
                }
            }

            // Record History
            convergenceCurve[t] = globalBestFitness;
            diversityCurve[t] = calculateDiversity();
            trajectoryCurve[t] = positions[0][0]; // Trajectory of 1st particle
            averageFitnessCurve[t] = calculateAverageFitness();
        }

        return globalBestFitness;
    }

    private void recordSnapshotIfNeeded(int t) {
        for (int snap : SNAPSHOT_ITERATIONS) {
            if (t == snap) {
                double[][] snapshot = new double[POPULATION_SIZE][dimensions];
                for (int i = 0; i < POPULATION_SIZE; i++) {
                    snapshot[i] = positions[i].clone();
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

    private void updateVelocityAndPosition(int i) {
        for (int d = 0; d < dimensions; d++) {
            double r1 = random.nextDouble();
            double r2 = random.nextDouble();
            double inertia = INERTIA_WEIGHT * velocities[i][d];
            double cognitive = COGNITIVE_COEF * r1 * (personalBest[i][d] - positions[i][d]);
            double social = SOCIAL_COEF * r2 * (globalBest[d] - positions[i][d]);
            velocities[i][d] = inertia + cognitive + social;
            double vMax = (upperBound - lowerBound) * 0.2;
            velocities[i][d] = clamp(velocities[i][d], -vMax, vMax);
            positions[i][d] = positions[i][d] + velocities[i][d];
        }
    }

    private void initializeSwarm() {
        double range = upperBound - lowerBound;
        double vMax = range * 0.2;
        for (int i = 0; i < POPULATION_SIZE; i++) {
            for (int d = 0; d < dimensions; d++) {
                positions[i][d] = lowerBound + random.nextDouble() * range;
                velocities[i][d] = -vMax + random.nextDouble() * 2 * vMax;
            }
            personalBest[i] = positions[i].clone();
            personalBestFitness[i] = Double.MAX_VALUE;
        }
    }

    private void evaluateSwarm() {
        for (int i = 0; i < POPULATION_SIZE; i++) {
            fitness[i] = function.evaluate(positions[i]);
            personalBestFitness[i] = fitness[i];
            personalBest[i] = positions[i].clone();
            if (fitness[i] < globalBestFitness) {
                globalBestFitness = fitness[i];
                globalBest = positions[i].clone();
            }
        }
    }

    private double clamp(double value, double min, double max) {
        if (value < min)
            return min;
        if (value > max)
            return max;
        return value;
    }

    public double[] getBestSolution() {
        return globalBest.clone();
    }

    public double getBestFitness() {
        return globalBestFitness;
    }

    public double getError() {
        return Math.abs(globalBestFitness - function.getOptimalValue());
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
                center[d] += positions[i][d];
            }
        }
        for (int d = 0; d < dimensions; d++) {
            center[d] /= POPULATION_SIZE;
        }
        double sumDist = 0.0;
        for (int i = 0; i < POPULATION_SIZE; i++) {
            double distSq = 0.0;
            for (int d = 0; d < dimensions; d++) {
                double diff = positions[i][d] - center[d];
                distSq += diff * diff;
            }
            sumDist += Math.sqrt(distSq);
        }
        return sumDist / POPULATION_SIZE;
    }
}

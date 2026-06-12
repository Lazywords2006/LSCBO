package com.edcbo.research;

import com.edcbo.research.utils.ConvergenceRecord;
import com.edcbo.research.utils.CostCalculator;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.vms.Vm;
import org.apache.commons.math3.special.Gamma;

import java.util.*;

/**
 * CBO_Levy_Broker
 *
 * 消融实验专用：引入Lévy飞行替换Phase 1的连续搜索。
 */
public class CBO_Levy_Broker extends DatacenterBrokerSimple {

    protected static final int POPULATION_SIZE = 20;
    protected static final int MAX_ITERATIONS = 20;

    // Lévy飞行参数
    private static final double LEVY_LAMBDA = 1.5;
    private static final double LEVY_ALPHA_COEF = 0.5;

    protected final Random random;
    protected final long seed;

    protected double[][] population;
    protected double[] fitness;
    protected double[] bestSolution;
    protected double bestFitness;

    protected ConvergenceRecord convergenceRecord;
    private Map<Long, Vm> cloudletVmMapping;
    private boolean schedulingDone = false;
    private double levySigmaU;

    public CBO_Levy_Broker(CloudSimPlus simulation, long seed) {
        super(simulation);
        this.seed = seed;
        this.random = new Random(seed);
        this.cloudletVmMapping = new HashMap<>();
        calculateLevySigmaU();
    }

    public CBO_Levy_Broker(CloudSimPlus simulation) {
        this(simulation, 42L);
    }

    @Override
    protected Vm defaultVmMapper(Cloudlet cloudlet) {
        if (!schedulingDone) {
            runCBOScheduling();
            schedulingDone = true;
        }
        return cloudletVmMapping.getOrDefault(cloudlet.getId(), super.defaultVmMapper(cloudlet));
    }

    private void runCBOScheduling() {
        List<Cloudlet> cloudletList = new ArrayList<>(getCloudletWaitingList());
        List<Vm> vmList = new ArrayList<>(getVmCreatedList());
        if (cloudletList.isEmpty() || vmList.isEmpty())
            return;

        int M = cloudletList.size();
        int N = vmList.size();

        int[] bestSchedule = runCBO(cloudletList, vmList);

        for (int i = 0; i < cloudletList.size(); i++) {
            Cloudlet cloudlet = cloudletList.get(i);
            Vm vm = vmList.get(bestSchedule[i]);
            cloudletVmMapping.put(cloudlet.getId(), vm);
        }
    }

    protected int[] runCBO(List<Cloudlet> cloudletList, List<Vm> vmList) {
        int M = cloudletList.size();
        int N = vmList.size();

        String scale = String.format("M%d", M);
        this.convergenceRecord = new ConvergenceRecord("CBO_Levy", scale, this.seed);

        initializePopulation(M, N, cloudletList, vmList);

        for (int t = 0; t < MAX_ITERATIONS; t++) {

            // ⭐ Phase 1改用Levy
            searchingPhaseLevy(M, N, t, cloudletList, vmList);

            encirclingPhase(M, N, t, cloudletList, vmList);
            attackingPhase(M, N, cloudletList, vmList);

            updateBestSolution(M, N, cloudletList, vmList);
            convergenceRecord.recordIteration(t, bestFitness);
        }

        convergenceRecord.exportToCSV("results/");
        return continuousToDiscrete(bestSolution, N);
    }

    protected void initializePopulation(int M, int N, List<Cloudlet> cloudletList, List<Vm> vmList) {
        population = new double[POPULATION_SIZE][M];
        fitness = new double[POPULATION_SIZE];
        bestSolution = new double[M];
        bestFitness = Double.MAX_VALUE;

        for (int i = 0; i < POPULATION_SIZE; i++) {
            for (int j = 0; j < M; j++)
                population[i][j] = random.nextDouble();
            fitness[i] = calculateFitness(population[i], M, N, cloudletList, vmList);
        }
        updateBestSolution(M, N, cloudletList, vmList);
    }

    /**
     * ⭐ Phase 1 改版: Levy Flight Searching
     */
    protected void searchingPhaseLevy(int M, int N, int t, List<Cloudlet> cloudletList, List<Vm> vmList) {
        for (int i = 0; i < POPULATION_SIZE; i++) {
            double[] newPosition = new double[M];
            int preyIdx = random.nextDouble() < 0.01 ? findBestIndividualIndex() : random.nextInt(POPULATION_SIZE);
            double alpha = LEVY_ALPHA_COEF * (1.0 - (double) t / MAX_ITERATIONS);

            for (int j = 0; j < M; j++) {
                double levyStep = generateLevyStep();
                double distance = population[preyIdx][j] - population[i][j];
                newPosition[j] = population[i][j] + alpha * levyStep * distance;
                newPosition[j] = Math.max(0.0, Math.min(1.0, newPosition[j]));
            }
            double newFitness = calculateFitness(newPosition, M, N, cloudletList, vmList);
            if (newFitness < fitness[i]) {
                population[i] = newPosition;
                fitness[i] = newFitness;
            }
        }
    }

    private int findBestIndividualIndex() {
        int bestIdx = 0;
        double bestFit = fitness[0];
        for (int i = 1; i < POPULATION_SIZE; i++) {
            if (fitness[i] < bestFit) {
                bestFit = fitness[i];
                bestIdx = i;
            }
        }
        return bestIdx;
    }

    protected void encirclingPhase(int M, int N, int t, List<Cloudlet> cloudletList, List<Vm> vmList) {
        double theta = 2.0 * Math.PI * t / MAX_ITERATIONS;
        double cosTheta = Math.cos(theta);
        double sinTheta = Math.sin(theta);

        for (int i = 0; i < POPULATION_SIZE; i++) {
            double[] newPosition = new double[M];
            for (int j = 0; j < M - 1; j += 2) {
                double dx = population[i][j] - bestSolution[j];
                double dy = population[i][j + 1] - bestSolution[j + 1];
                double rotatedX = dx * cosTheta - dy * sinTheta;
                double rotatedY = dx * sinTheta + dy * cosTheta;
                newPosition[j] = bestSolution[j] + rotatedX;
                newPosition[j + 1] = bestSolution[j + 1] + rotatedY;
                newPosition[j] = Math.max(0.0, Math.min(1.0, newPosition[j]));
                newPosition[j + 1] = Math.max(0.0, Math.min(1.0, newPosition[j + 1]));
            }
            if (M % 2 == 1) {
                int lastDim = M - 1;
                double A = 2.0 - 2.0 * t / MAX_ITERATIONS;
                double C = A * (2 * random.nextDouble() - 1);
                newPosition[lastDim] = population[i][lastDim] + C * (bestSolution[lastDim] - population[i][lastDim]);
                newPosition[lastDim] = Math.max(0.0, Math.min(1.0, newPosition[lastDim]));
            }
            double newFitness = calculateFitness(newPosition, M, N, cloudletList, vmList);
            if (newFitness < fitness[i]) {
                population[i] = newPosition;
                fitness[i] = newFitness;
            }
        }
    }

    protected void attackingPhase(int M, int N, List<Cloudlet> cloudletList, List<Vm> vmList) {
        for (int i = 0; i < POPULATION_SIZE; i++) {
            double[] newPosition = new double[M];
            for (int j = 0; j < M; j++) {
                newPosition[j] = 0.9 * population[i][j] + 0.1 * bestSolution[j];
                newPosition[j] = Math.max(0.0, Math.min(1.0, newPosition[j]));
            }
            double newFitness = calculateFitness(newPosition, M, N, cloudletList, vmList);
            if (newFitness < fitness[i]) {
                population[i] = newPosition;
                fitness[i] = newFitness;
            }
        }
    }

    protected void updateBestSolution(int M, int N, List<Cloudlet> cloudletList, List<Vm> vmList) {
        for (int i = 0; i < POPULATION_SIZE; i++) {
            if (fitness[i] < bestFitness) {
                bestFitness = fitness[i];
                bestSolution = Arrays.copyOf(population[i], M);
            }
        }
    }

    protected double calculateFitness(double[] individual, int M, int N, List<Cloudlet> cloudletList, List<Vm> vmList) {
        int[] schedule = continuousToDiscrete(individual, N);
        return CostCalculator.calculateWeightedFitness(schedule, M, N, cloudletList, vmList);
    }

    private int[] continuousToDiscrete(double[] continuous, int N) {
        int[] discrete = new int[continuous.length];
        for (int i = 0; i < continuous.length; i++) {
            discrete[i] = (int) (continuous[i] * N);
            if (discrete[i] >= N)
                discrete[i] = N - 1;
        }
        return discrete;
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

    public ConvergenceRecord getConvergenceRecord() {
        return convergenceRecord;
    }

    public double getInternalMakespan() {
        return bestFitness;
    }
}

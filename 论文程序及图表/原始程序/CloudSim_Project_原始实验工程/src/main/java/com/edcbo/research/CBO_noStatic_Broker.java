package com.edcbo.research;

import com.edcbo.research.utils.ConvergenceRecord;
import com.edcbo.research.utils.CostCalculator;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.vms.Vm;

import java.util.*;

/**
 * CBO_noStatic_Broker
 *
 * 消融实验专用：移除静态权限 (Static Weight 0.9/0.1)，替换为动态自适应权重。
 */
public class CBO_noStatic_Broker extends DatacenterBrokerSimple {

    protected static final int POPULATION_SIZE = 20;
    protected static final int MAX_ITERATIONS = 20;

    // 动态权重参数
    private static final double W_MAX = 0.80;
    private static final double W_MIN = 0.10;

    protected final Random random;
    protected final long seed;

    protected double[][] population;
    protected double[] fitness;
    protected double[] bestSolution;
    protected double bestFitness;

    protected ConvergenceRecord convergenceRecord;
    private Map<Long, Vm> cloudletVmMapping;
    private boolean schedulingDone = false;

    public CBO_noStatic_Broker(CloudSimPlus simulation, long seed) {
        super(simulation);
        this.seed = seed;
        this.random = new Random(seed);
        this.cloudletVmMapping = new HashMap<>();
    }

    public CBO_noStatic_Broker(CloudSimPlus simulation) {
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
        this.convergenceRecord = new ConvergenceRecord("CBO_noStatic", scale, this.seed);

        initializePopulation(M, N, cloudletList, vmList);

        for (int t = 0; t < MAX_ITERATIONS; t++) {
            System.out.println("      -> Iteration " + t);
            searchingPhase(M, N, cloudletList, vmList);
            encirclingPhase(M, N, t, cloudletList, vmList);

            // ⭐ 使用带有 t 变量的动态攻击
            attackingPhaseDynamic(M, N, t, cloudletList, vmList);

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

    protected void searchingPhase(int M, int N, List<Cloudlet> cloudletList, List<Vm> vmList) {
        for (int i = 0; i < POPULATION_SIZE; i++) {
            double[] newPosition = new double[M];
            int preyIdx = random.nextDouble() < 0.01 ? findBestIndividualIndex() : random.nextInt(POPULATION_SIZE);
            for (int j = 0; j < M; j++) {
                double distance = Math.abs(population[preyIdx][j] - population[i][j]);
                double r = random.nextDouble();
                newPosition[j] = population[i][j]
                        + r * Math.tanh(distance) * (population[preyIdx][j] - population[i][j]);
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

    /**
     * ⭐ Phase 3 改版: 动态权重
     */
    protected void attackingPhaseDynamic(int M, int N, int t, List<Cloudlet> cloudletList, List<Vm> vmList) {
        double t_ratio = (double) t / MAX_ITERATIONS;
        double w = W_MIN + (W_MAX - W_MIN) * Math.pow(1.0 - t_ratio, 3);

        for (int i = 0; i < POPULATION_SIZE; i++) {
            double[] newPosition = new double[M];
            for (int j = 0; j < M; j++) {
                newPosition[j] = w * population[i][j] + (1.0 - w) * bestSolution[j];
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

    public ConvergenceRecord getConvergenceRecord() {
        return convergenceRecord;
    }

    public double getInternalMakespan() {
        return bestFitness;
    }
}

package com.edcbo.research;

import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.vms.Vm;
import com.edcbo.research.utils.ConvergenceRecord;

import java.util.*;

/**
 * Dung Beetle Optimizer (DBO) Broker
 * A recent SOTA algorithm (2024 heavily published) inspired by dung beetles.
 */
public class DBO_Broker extends DatacenterBrokerSimple {

    protected static final int POPULATION_SIZE = 30;
    protected static final int MAX_ITERATIONS = 100;

    private double[][] population;
    private double[] bestSolution;
    private double bestFitness;
    private Random random;
    private ConvergenceRecord convergenceRecord;
    private Map<Long, Vm> cloudletVmMapping;
    private boolean schedulingDone = false;

    public DBO_Broker(CloudSimPlus simulation, long seed) {
        super(simulation);
        this.random = new Random(seed);
        this.convergenceRecord = new ConvergenceRecord("DBO", "unknown", seed);
        this.cloudletVmMapping = new HashMap<>();
    }

    @Override
    protected Vm defaultVmMapper(Cloudlet cloudlet) {
        if (!schedulingDone) {
            runDBO();
            schedulingDone = true;
        }
        return cloudletVmMapping.getOrDefault(cloudlet.getId(), super.defaultVmMapper(cloudlet));
    }

    private void runDBO() {
        List<Cloudlet> cloudletList = new ArrayList<>(getCloudletWaitingList());
        List<Vm> vmList = new ArrayList<>(getVmCreatedList());

        int M = cloudletList.size();
        int N = vmList.size();

        if (M == 0 || N == 0)
            return;

        population = new double[POPULATION_SIZE][M];
        bestFitness = Double.MAX_VALUE;
        bestSolution = new double[M];

        // Init
        for (int i = 0; i < POPULATION_SIZE; i++) {
            for (int d = 0; d < M; d++) {
                population[i][d] = random.nextDouble();
            }
            double fit = calculateFitness(population[i], M, N, cloudletList, vmList);
            if (fit < bestFitness) {
                bestFitness = fit;
                System.arraycopy(population[i], 0, bestSolution, 0, M);
            }
        }

        convergenceRecord.recordIteration(0, bestFitness);

        // Parameters for DBO population breakdown
        int pNum = (int) (POPULATION_SIZE * 0.2); // Ball-rolling
        int bNum = (int) (POPULATION_SIZE * 0.4); // Brood-ball (inc pNum)
        int sNum = (int) (POPULATION_SIZE * 0.6); // Small (inc bNum)

        for (int t = 1; t <= MAX_ITERATIONS; t++) {
            double r2 = random.nextDouble();

            // local worst
            double[] worstSolution = new double[M];
            double worstFitness = Double.MIN_VALUE;
            for (int i = 0; i < POPULATION_SIZE; i++) {
                double fit = calculateFitness(population[i], M, N, cloudletList, vmList);
                if (fit > worstFitness) {
                    worstFitness = fit;
                    System.arraycopy(population[i], 0, worstSolution, 0, M);
                }
            }

            for (int i = 0; i < POPULATION_SIZE; i++) {
                double[] newPos = new double[M];

                if (i < pNum) {
                    // Ball rolling
                    double a = random.nextDouble() < 0.5 ? 1 : -1;
                    if (r2 < 0.9) {
                        for (int d = 0; d < M; d++) {
                            double alpha = 0.5 * (random.nextDouble() > 0.5 ? 1 : -1);
                            newPos[d] = population[i][d] + alpha * Math.abs(population[i][d] - worstSolution[d])
                                    + a * 0.1 * (population[i][d] - population[0][d]);
                        }
                    } else {
                        // Dancing
                        double theta = random.nextDouble() * Math.PI;
                        for (int d = 0; d < M; d++) {
                            newPos[d] = population[i][d]
                                    + Math.tan(theta) * Math.abs(population[i][d] - population[i][d]);
                        }
                    }
                } else if (i < bNum) {
                    // Brood ball
                    double R = 1.0 - (double) t / MAX_ITERATIONS;
                    for (int d = 0; d < M; d++) {
                        newPos[d] = bestSolution[d] + (random.nextDouble() - 0.5) * R;
                    }
                } else if (i < sNum) {
                    // Small beetles (Foraging)
                    double R = 1.0 - (double) t / MAX_ITERATIONS;
                    for (int d = 0; d < M; d++) {
                        newPos[d] = population[i][d] + random.nextGaussian() * (population[i][d] - bestSolution[d])
                                + (random.nextDouble() * 2 - 1) * R;
                    }
                } else {
                    // Thieves (Stealing)
                    double s = 0.5;
                    for (int d = 0; d < M; d++) {
                        newPos[d] = bestSolution[d]
                                + random.nextGaussian() * s * Math.abs(population[i][d] - bestSolution[d]);
                    }
                }

                for (int d = 0; d < M; d++) {
                    newPos[d] = Math.max(0, Math.min(1, newPos[d]));
                }

                double nf = calculateFitness(newPos, M, N, cloudletList, vmList);
                double of = calculateFitness(population[i], M, N, cloudletList, vmList);
                if (nf < of) {
                    System.arraycopy(newPos, 0, population[i], 0, M);
                    if (nf < bestFitness) {
                        bestFitness = nf;
                        System.arraycopy(newPos, 0, bestSolution, 0, M);
                    }
                }
            }
            convergenceRecord.recordIteration(t, bestFitness);
        }

        int[] schedule = continuousToDiscrete(bestSolution, N);
        for (int i = 0; i < M; i++) {
            cloudletVmMapping.put(cloudletList.get(i).getId(), vmList.get(schedule[i]));
        }
    }

    private double calculateFitness(double[] individual, int M, int N, List<Cloudlet> cloudletList, List<Vm> vmList) {
        int[] schedule = continuousToDiscrete(individual, N);
        double[] vmLoads = new double[N];
        for (int i = 0; i < M; i++) {
            vmLoads[schedule[i]] += cloudletList.get(i).getLength() / vmList.get(schedule[i]).getMips();
        }
        return Arrays.stream(vmLoads).max().orElse(0);
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

    public double getInternalMakespan() {
        return bestFitness;
    }
}

package com.edcbo.research;

import com.edcbo.research.utils.ConvergenceRecord;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.vms.Vm;
import java.util.*;

/**
 * 可配置解码器的 CBO Broker
 * 包含完整 CBO 实现，支持三种解码器策略
 */
public class DecodableCBOBroker extends DatacenterBrokerSimple {

    // CBO parameters (matched to LSCBO paper)
    private static final int POPULATION_SIZE = 20;
    private static final int MAX_ITERATIONS = 20;

    private final long seed;
    private final DecoderFairComparisonTest.DecodingStrategy strategy;
    private final Random random;
    private final Map<Long, Vm> mapping;
    private ConvergenceRecord convRecord;
    private boolean schedulingDone = false;

    public DecodableCBOBroker(CloudSimPlus simulation, long seed,
                               DecoderFairComparisonTest.DecodingStrategy strategy) {
        super(simulation);
        this.seed = seed;
        this.strategy = strategy;
        this.random = new Random(seed);
        this.mapping = new HashMap<>();
    }

    @Override
    protected Vm defaultVmMapper(Cloudlet cloudlet) {
        if (!schedulingDone) {
            runCBOScheduling();
            schedulingDone = true;
        }
        return mapping.getOrDefault(cloudlet.getId(), super.defaultVmMapper(cloudlet));
    }

    private void runCBOScheduling() {
        List<Cloudlet> cloudlets = new ArrayList<>(getCloudletWaitingList());
        List<Vm> vms = new ArrayList<>(getVmCreatedList());
        if (cloudlets.isEmpty() || vms.isEmpty()) return;
        int M = cloudlets.size(), N = vms.size();
        convRecord = new ConvergenceRecord("CBO-" + strategy, M + "x" + N, seed);

        // Initialize population
        double[][] pop = new double[POPULATION_SIZE][M];
        double[] fitness = new double[POPULATION_SIZE];
        int[] bestSolution = null;
        double bestFitness = Double.MAX_VALUE;

        for (int i = 0; i < POPULATION_SIZE; i++) {
            for (int j = 0; j < M; j++)
                pop[i][j] = random.nextDouble();
            int[] sched = decode(pop[i], N, cloudlets, vms);
            fitness[i] = computeMakespan(sched, cloudlets, vms);
            if (fitness[i] < bestFitness) {
                bestFitness = fitness[i];
                bestSolution = sched;
            }
        }

        // CBO optimization loop (simplified: cooperative hunting)
        for (int iter = 0; iter < MAX_ITERATIONS; iter++) {
            for (int i = 0; i < POPULATION_SIZE; i++) {
                // Pick a partner (badger/coyote)
                int partner = random.nextInt(POPULATION_SIZE);
                while (partner == i) partner = random.nextInt(POPULATION_SIZE);

                double[] newPos = new double[M];
                for (int j = 0; j < M; j++) {
                    // CBO update: move towards partner with perturbation
                    double r1 = random.nextDouble();
                    double r2 = random.nextDouble();
                    double diff = pop[partner][j] - pop[i][j];
                    newPos[j] = pop[i][j] + r1 * diff + r2 * (random.nextGaussian() * 0.1);
                    newPos[j] = Math.max(0.0, Math.min(1.0, newPos[j]));
                }

                int[] sched = decode(newPos, N, cloudlets, vms);
                double newFit = computeMakespan(sched, cloudlets, vms);
                if (newFit < fitness[i]) {
                    pop[i] = newPos;
                    fitness[i] = newFit;
                    if (newFit < bestFitness) {
                        bestFitness = newFit;
                        bestSolution = sched;
                    }
                }
            }
            convRecord.recordIteration(iter, bestFitness);
        }

        // Apply best schedule
        for (int i = 0; i < M; i++) {
            mapping.put(cloudlets.get(i).getId(), vms.get(bestSolution[i]));
        }
    }

    private int[] decode(double[] continuous, int N, List<Cloudlet> cloudlets, List<Vm> vms) {
        int M = continuous.length;
        switch (strategy) {
            case SPV_MFD: {
                Integer[] indices = new Integer[M];
                for (int i = 0; i < M; i++) indices[i] = i;
                Arrays.sort(indices, (a, b) -> Double.compare(continuous[a], continuous[b]));
                int[] disc = new int[M];
                double[] vmAvail = new double[N];
                for (int idx : indices) {
                    double cLen = cloudlets.get(idx).getLength();
                    int bestVm = 0;
                    double earliest = Double.MAX_VALUE;
                    for (int j = 0; j < N; j++) {
                        double finish = vmAvail[j] + cLen / vms.get(j).getMips();
                        if (finish < earliest) { earliest = finish; bestVm = j; }
                    }
                    disc[idx] = bestVm;
                    vmAvail[bestVm] = earliest;
                }
                return disc;
            }
            case GREEDY_REPAIR: {
                int[] disc = new int[M];
                for (int i = 0; i < M; i++) {
                    disc[i] = (int) (continuous[i] * N);
                    if (disc[i] >= N) disc[i] = N - 1;
                }
                double[] loads = new double[N];
                List<List<Integer>> vmTasks = new ArrayList<>();
                for (int j = 0; j < N; j++) vmTasks.add(new ArrayList<>());
                for (int i = 0; i < M; i++) {
                    int vmIdx = disc[i];
                    loads[vmIdx] += cloudlets.get(i).getLength() / vms.get(vmIdx).getMips();
                    vmTasks.get(vmIdx).add(i);
                }
                int maxVm = 0, minVm = 0;
                for (int j = 1; j < N; j++) {
                    if (loads[j] > loads[maxVm]) maxVm = j;
                    if (loads[j] < loads[minVm]) minVm = j;
                }
                if (!vmTasks.get(maxVm).isEmpty() && maxVm != minVm) {
                    int longest = vmTasks.get(maxVm).get(0);
                    double maxLen = cloudlets.get(longest).getLength();
                    for (int t : vmTasks.get(maxVm))
                        if (cloudlets.get(t).getLength() > maxLen)
                            { maxLen = cloudlets.get(t).getLength(); longest = t; }
                    disc[longest] = minVm;
                }
                return disc;
            }
            default: { // RANDOM_KEY
                int[] disc = new int[M];
                for (int i = 0; i < M; i++) {
                    disc[i] = (int) (continuous[i] * N);
                    if (disc[i] >= N) disc[i] = N - 1;
                }
                return disc;
            }
        }
    }

    private double computeMakespan(int[] schedule, List<Cloudlet> cloudlets, List<Vm> vms) {
        double[] loads = new double[vms.size()];
        for (int i = 0; i < schedule.length; i++) {
            loads[schedule[i]] += cloudlets.get(i).getLength() / vms.get(schedule[i]).getMips();
        }
        return Arrays.stream(loads).max().orElse(Double.MAX_VALUE);
    }
}

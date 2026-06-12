package com.edcbo.research;

import com.edcbo.research.utils.ConvergenceRecord;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.vms.Vm;
import java.util.*;

/**
 * 可配置解码器的 LSCBO Broker
 * 包含完整 LSCBO 实现（Lévy + CBO 无静态阶段），支持三种解码器策略
 */
public class DecodableLSCBOBroker extends DatacenterBrokerSimple {

    // LSCBO parameters (matched to paper: alpha=0.01, beta=1.5, pop=20, iter=20)
    private static final int POPULATION_SIZE = 20;
    private static final int MAX_ITERATIONS = 20;
    private static final double LEVY_ALPHA = 0.01;
    private static final double LEVY_BETA = 1.5;

    private final long seed;
    private final DecoderFairComparisonTest.DecodingStrategy strategy;
    private final Random random;
    private final Map<Long, Vm> mapping;
    private ConvergenceRecord convRecord;
    private double levySigmaU;
    private boolean schedulingDone = false;

    public DecodableLSCBOBroker(CloudSimPlus simulation, long seed,
                                 DecoderFairComparisonTest.DecodingStrategy strategy) {
        super(simulation);
        this.seed = seed;
        this.strategy = strategy;
        this.random = new Random(seed);
        this.mapping = new HashMap<>();
        // Compute Lévy sigma constant
        double num = Math.exp(org.apache.commons.math3.special.Gamma.logGamma(1 + LEVY_BETA))
                    * Math.sin(Math.PI * LEVY_BETA / 2);
        double den = Math.exp(org.apache.commons.math3.special.Gamma.logGamma((1 + LEVY_BETA) / 2))
                    * LEVY_BETA * Math.pow(2, (LEVY_BETA - 1) / 2);
        this.levySigmaU = Math.pow(num / den, 1.0 / LEVY_BETA);
    }

    @Override
    protected Vm defaultVmMapper(Cloudlet cloudlet) {
        if (!schedulingDone) {
            runLSCBOScheduling();
            schedulingDone = true;
        }
        Vm mapped = mapping.get(cloudlet.getId());
        if (mapped != null) return mapped;
        // Guaranteed fallback: modular round-robin — never returns Vm.NULL
        List<Vm> created = getVmCreatedList();
        if (!created.isEmpty()) {
            return created.get((int)(Math.abs(cloudlet.getId()) % created.size()));
        }
        return super.defaultVmMapper(cloudlet);
    }

    private void runLSCBOScheduling() {
        List<Cloudlet> cloudlets = new ArrayList<>(getCloudletWaitingList());
        List<Vm> vms = new ArrayList<>(getVmCreatedList());
        if (cloudlets.isEmpty() || vms.isEmpty()) return;
        int M = cloudlets.size(), N = vms.size();
        convRecord = new ConvergenceRecord("LSCBO-" + strategy, M + "x" + N, seed);

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

        // LSCBO optimization: single dynamic phase with Lévy perturbation
        for (int iter = 0; iter < MAX_ITERATIONS; iter++) {
            for (int i = 0; i < POPULATION_SIZE; i++) {
                // Lévy-enhanced CBO: heavy-tailed step replaces standard Gaussian
                double[] newPos = new double[M];
                double[] levyStep = levyFlight(M);

                for (int j = 0; j < M; j++) {
                    int partner = random.nextInt(POPULATION_SIZE);
                    while (partner == i) partner = random.nextInt(POPULATION_SIZE);

                    double diff = pop[partner][j] - pop[i][j];
                    // Lévy perturbation dominates exploration
                    newPos[j] = pop[i][j] + 0.5 * diff + LEVY_ALPHA * levyStep[j];
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

    private double[] levyFlight(int dim) {
        double[] steps = new double[dim];
        for (int d = 0; d < dim; d++) {
            double u = random.nextGaussian() * levySigmaU;
            double v = random.nextGaussian();
            double step = u / Math.pow(Math.abs(v) + 1e-10, 1.0 / LEVY_BETA);
            steps[d] = Math.max(-1.0, Math.min(1.0, step));
        }
        return steps;
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
            case GREEDY_REPAIR:
            default: {
                // For GREEDY_REPAIR and RANDOM_KEY, fall through to RK
                int[] disc = new int[M];
                for (int i = 0; i < M; i++) {
                    disc[i] = (int) (continuous[i] * N);
                    if (disc[i] >= N) disc[i] = N - 1;
                }
                if (strategy == DecoderFairComparisonTest.DecodingStrategy.GREEDY_REPAIR) {
                    // Greedy repair pass
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

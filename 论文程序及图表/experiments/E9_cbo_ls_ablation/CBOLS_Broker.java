package com.edcbo.research;

import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.vms.Vm;
import com.edcbo.research.utils.*;

import java.util.*;

/**
 * CBOLS_Broker: CBO with Task-Migration Local Search.
 * 
 * Extends the standard CBO_Broker (tanh-based Phase 1, rotation Phase 2, 
 * fixed w=0.5 attack Phase 3). Adds greedy task-migration local search 
 * every K=10 iterations after updateBestSolution.
 * 
 * The local search:
 * 1. Decodes bestSolution to discrete schedule
 * 2. Identifies most-loaded and least-loaded VM by completion time
 * 3. Migrates tasks from most-loaded to least-loaded VM
 * 4. Accepts the migration that maximally reduces makespan
 * 5. Repeats up to 10 swaps, then re-encodes the improved schedule
 * 
 * This is the CBO+LS variant used in the evolution-path ablation 
 * (Section 3.6): CBO + LS, without Levy flight, without staging.
 */
public class CBOLS_Broker extends CBO_Broker {

    private static final int LS_INTERVAL = 10;  // Apply LS every K iterations
    private static final int MAX_SWAPS = 10;    // Max task migrations per LS call

    private List<Cloudlet> cloudletList;
    private List<Vm> vmList;
    private int M;  // task count
    private int N;  // VM count

    public CBOLS_Broker(CloudSimPlus simulation, long seed) {
        super(simulation, seed);
    }

    /**
     * Override scheduleTasks to inject local search after each iteration.
     */
    @Override
    protected Map<Long, Vm> scheduleTasks(List<Cloudlet> cloudletList, List<Vm> vmList) {
        this.cloudletList = cloudletList;
        this.vmList = vmList;
        this.M = cloudletList.size();
        this.N = vmList.size();

        // Initialize (same as CBO)
        String scale = String.format("M%d", M);
        this.convergenceRecord = new ConvergenceRecord("CBO-LS", scale, this.seed);
        initializePopulation(M, N, cloudletList, vmList);

        // CBO iteration loop with LS injection
        for (int t = 0; t < MAX_ITERATIONS; t++) {
            searchingPhase(M, N, cloudletList, vmList);
            encirclingPhase(M, N, t, cloudletList, vmList);
            attackingPhase(M, N, cloudletList, vmList);
            updateBestSolution(M, N, cloudletList, vmList);

            // === Task-migration local search every LS_INTERVAL iterations ===
            if ((t + 1) % LS_INTERVAL == 0) {
                applyLocalSearch(M, N, cloudletList, vmList);
            }

            convergenceRecord.recordIteration(t, bestFitness);

            if ((t + 1) % 10 == 0 || t == 0) {
                System.out.println(String.format("[CBO+LS Iter %3d/%d] Best: %.4f",
                        t + 1, MAX_ITERATIONS, bestFitness));
            }
        }

        convergenceRecord.exportToCSV("results/");
        return continuousToDiscrete(bestSolution, N);
    }

    /**
     * Greedy task-migration local search.
     * Migrates tasks from the most-loaded VM to the least-loaded VM,
     * accepting only migrations that reduce makespan.
     */
    private void applyLocalSearch(int M, int N, List<Cloudlet> cloudletList, List<Vm> vmList) {
        // Decode current best solution to discrete schedule
        int[] schedule = decodeBestToSchedule(M, N, cloudletList, vmList);

        // Compute per-VM completion times
        double[] vmTimes = new double[N];
        List<Integer>[] vmTasks = new ArrayList[N];
        for (int v = 0; v < N; v++) {
            vmTasks[v] = new ArrayList<>();
        }
        for (int i = 0; i < M; i++) {
            int vmIdx = schedule[i];
            double etc = (double) cloudletList.get(i).getLength() / vmList.get(vmIdx).getMips();
            vmTimes[vmIdx] += etc;
            vmTasks[vmIdx].add(i);
        }

        int swaps = 0;
        for (int s = 0; s < MAX_SWAPS; s++) {
            // Find most-loaded and least-loaded VM
            int worstVm = 0, bestVm = 0;
            double maxTime = -1, minTime = Double.MAX_VALUE;
            for (int v = 0; v < N; v++) {
                if (vmTimes[v] > maxTime) { maxTime = vmTimes[v]; worstVm = v; }
                if (vmTimes[v] < minTime) { minTime = vmTimes[v]; bestVm = v; }
            }

            if (worstVm == bestVm || vmTasks[worstVm].isEmpty()) break;

            // Try migrating each task from worstVm to bestVm
            int bestTask = -1;
            double bestImprovement = 0;
            double bestNewWorstTime = maxTime;
            double bestNewBestTime = minTime;

            for (int taskIdx : vmTasks[worstVm]) {
                double taskEtc = (double) cloudletList.get(taskIdx).getLength() / vmList.get(bestVm).getMips();
                double newWorstTime = Math.max(maxTime - (double) cloudletList.get(taskIdx).getLength() / vmList.get(worstVm).getMips(), vmTimes[bestVm] + taskEtc);
                // Also check other VMs to find new makespan
                double newMakespan = 0;
                for (int v = 0; v < N; v++) {
                    double vt = vmTimes[v];
                    if (v == worstVm) vt -= (double) cloudletList.get(taskIdx).getLength() / vmList.get(worstVm).getMips();
                    if (v == bestVm) vt += taskEtc;
                    newMakespan = Math.max(newMakespan, vt);
                }
                double improvement = maxTime - newMakespan;  // positive = better
                if (improvement > bestImprovement) {
                    bestImprovement = improvement;
                    bestTask = taskIdx;
                }
            }

            if (bestTask < 0 || bestImprovement <= 0) break;

            // Apply the migration
            double taskEtc = (double) cloudletList.get(bestTask).getLength() / vmList.get(bestVm).getMips();
            double worstEtc = (double) cloudletList.get(bestTask).getLength() / vmList.get(worstVm).getMips();
            vmTimes[worstVm] -= worstEtc;
            vmTimes[bestVm] += taskEtc;

            vmTasks[worstVm].remove(Integer.valueOf(bestTask));
            vmTasks[bestVm].add(bestTask);
            schedule[bestTask] = bestVm;
            swaps++;
        }

        if (swaps > 0) {
            // Re-encode the improved schedule back to continuous space
            // and update bestSolution/fitness if improved
            double newFitness = calculateFitnessFromSchedule(schedule, M, N, cloudletList, vmList);
            if (newFitness < bestFitness) {
                // Re-encode: set SPV positions consistent with improved schedule
                encodeScheduleToBest(schedule, M);
                bestFitness = newFitness;
            }
        }
    }

    /**
     * Decode bestSolution to discrete schedule via SPV+MFD.
     */
    private int[] decodeBestToSchedule(int M, int N, List<Cloudlet> cloudletList, List<Vm> vmList) {
        // Use SPV sorting to get task order, then MFD for VM assignment
        Integer[] indices = new Integer[M];
        for (int i = 0; i < M; i++) indices[i] = i;
        Arrays.sort(indices, Comparator.comparingDouble(i -> bestSolution[i]));
        
        int[] schedule = new int[M];
        for (int i = 0; i < M; i++) {
            schedule[indices[i]] = i % N;  // MFD: modulo assignment
        }
        return schedule;
    }

    /**
     * Re-encode an improved schedule back to bestSolution.
     * Sets SPV values to preserve the task ordering of the improved schedule.
     */
    private void encodeScheduleToBest(int[] schedule, int M) {
        // Simple approach: assign evenly-spaced values in [0,1] preserving VM ordering
        // Tasks on the same VM get close values
        for (int i = 0; i < M; i++) {
            bestSolution[i] = (schedule[i] + 0.5) / ((double) N);
        }
    }

    /**
     * Calculate scalarized fitness from a discrete schedule.
     */
    private double calculateFitnessFromSchedule(int[] schedule, int M, int N,
                                                  List<Cloudlet> cloudletList, List<Vm> vmList) {
        CostResult cr = CostCalculator.calculateWeightedCostDetails(schedule, M, N, cloudletList, vmList);
        return cr.weightedCost;  // scalarized objective
    }
}

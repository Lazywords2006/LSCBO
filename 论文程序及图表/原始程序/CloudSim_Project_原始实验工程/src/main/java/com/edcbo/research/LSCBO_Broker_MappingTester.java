package com.edcbo.research;

import com.edcbo.research.utils.ConvergenceRecord;
import com.edcbo.research.utils.CostCalculator;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.vms.Vm;

import java.util.*;

/**
 * 支持不同解码策略的LSCBO Broker
 * 用于消融实验：对比三种连续->离散映射策略:
 * 1. Random-Key (Standard continuous[i] * N)
 * 2. SPV+MFD
 * 3. Greedy-Repair
 */
public class LSCBO_Broker_MappingTester extends LSCBO_Broker_Fixed {

    public enum DecodingStrategy {
        RANDOM_KEY,
        SPV_MFD,
        GREEDY_REPAIR
    }

    private final DecodingStrategy strategy;
    private List<Cloudlet> cachedCloudletList;
    private List<Vm> cachedVmList;

    public LSCBO_Broker_MappingTester(CloudSimPlus simulation, long seed, DecodingStrategy strategy) {
        super(simulation, seed);
        this.strategy = strategy;
    }

    @Override
    protected double calculateFitness(double[] individual, int M, int N,
            List<Cloudlet> cloudletList, List<Vm> vmList) {
        this.cachedCloudletList = cloudletList;
        this.cachedVmList = vmList;

        int[] schedule;
        if (strategy == DecodingStrategy.SPV_MFD) {
            schedule = decodeSpvMfd(individual, M, N, cloudletList, vmList);
        } else if (strategy == DecodingStrategy.GREEDY_REPAIR) {
            schedule = decodeGreedyRepair(individual, M, N, cloudletList, vmList);
        } else {
            schedule = decodeRandomKey(individual, N);
        }

        return CostCalculator.calculateWeightedFitness(schedule, M, N, cloudletList, vmList);
    }

    // 在 LSCBO_Broker_Fixed 中没有覆盖 runLSCBO 内部的连续转离散调用，
    // 但是我们可以通过覆盖 continuousToDiscrete 使得它在最后应用结果或者其他地方行为一致：
    // （如果基类中有 private 方法 continuousToDiscrete，将无法重写，
    // 但 LSCBO_Broker_Fixed 的 continuousToDiscrete 是 private 吗？
    // 查阅之前代码，它是 private。这意味着我们需要稍微改变基类或者在这里直接提供映射，
    // 但是由于 calculateFitness 被重写，fitness的评估会用我们的映射，这就足够了。
    // 可是基类的 applySchedule 可能依然调用自己的 private continuousToDiscrete！
    // 所以这里最好重写整个 runCBO / runLSCBO，为了避免干扰不如重写部分方法。
    // 没关系，基类 LSCBO_Broker_Fixed 中并没有 runCBO。）

    // 独立实现的映射
    private int[] decodeRandomKey(double[] continuous, int N) {
        int[] discrete = new int[continuous.length];
        for (int i = 0; i < continuous.length; i++) {
            double value = Math.max(0.0, Math.min(1.0, continuous[i]));
            discrete[i] = (int) (value * N);
            if (discrete[i] >= N)
                discrete[i] = N - 1;
        }
        return discrete;
    }

    private int[] decodeSpvMfd(double[] continuous, int M, int N, List<Cloudlet> cloudlets, List<Vm> vms) {
        // 1. SPV (Smallest Position Value)
        Integer[] indices = new Integer[M];
        for (int i = 0; i < M; i++)
            indices[i] = i;

        Arrays.sort(indices, (a, b) -> Double.compare(continuous[a], continuous[b]));

        // 2. MFD (Minimum Finish Time for execution)
        int[] discrete = new int[M];
        double[] vmAvailableTime = new double[N];

        for (int i = 0; i < M; i++) {
            int taskIdx = indices[i];
            Cloudlet c = cloudlets.get(taskIdx);

            int bestVm = 0;
            double earliestFinish = Double.MAX_VALUE;
            for (int j = 0; j < N; j++) {
                Vm vm = vms.get(j);
                double finishTime = vmAvailableTime[j] + (c.getLength() / vm.getMips());
                if (finishTime < earliestFinish) {
                    earliestFinish = finishTime;
                    bestVm = j;
                }
            }
            discrete[taskIdx] = bestVm;
            vmAvailableTime[bestVm] = earliestFinish;
        }
        return discrete;
    }

    private int[] decodeGreedyRepair(double[] continuous, int M, int N, List<Cloudlet> cloudlets, List<Vm> vms) {
        // 1. Initial Mapping (Random-Key)
        int[] discrete = decodeRandomKey(continuous, N);

        // 2. Greedy Repair: Find max loaded VM and min loaded VM
        double[] vmLoads = new double[N];
        List<List<Integer>> vmTasks = new ArrayList<>();
        for (int i = 0; i < N; i++)
            vmTasks.add(new ArrayList<>());

        for (int i = 0; i < M; i++) {
            int vmIdx = discrete[i];
            double exTime = cloudlets.get(i).getLength() / vms.get(vmIdx).getMips();
            vmLoads[vmIdx] += exTime;
            vmTasks.get(vmIdx).add(i);
        }

        int maxVm = 0;
        int minVm = 0;
        for (int j = 1; j < N; j++) {
            if (vmLoads[j] > vmLoads[maxVm])
                maxVm = j;
            if (vmLoads[j] < vmLoads[minVm])
                minVm = j;
        }

        // Move the longest task from maxVm to minVm
        if (!vmTasks.get(maxVm).isEmpty()) {
            int longestTaskIdx = -1;
            double maxLen = -1;
            for (int taskIdx : vmTasks.get(maxVm)) {
                if (cloudlets.get(taskIdx).getLength() > maxLen) {
                    maxLen = cloudlets.get(taskIdx).getLength();
                    longestTaskIdx = taskIdx;
                }
            }
            // reassign
            if (longestTaskIdx != -1) {
                discrete[longestTaskIdx] = minVm;
            }
        }

        return discrete;
    }

    @Override
    public String getAlgorithmName() {
        return "LSCBO-" + strategy.name();
    }
}

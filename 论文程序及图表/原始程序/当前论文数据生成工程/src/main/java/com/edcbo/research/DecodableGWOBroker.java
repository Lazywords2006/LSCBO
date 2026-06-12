package com.edcbo.research;

import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.vms.Vm;
import java.util.*;

/**
 * 可配置解码器的 GWO Broker
 * 扩展 GWO_Broker，支持三种解码策略: SPV+MFD, Random-Key, Greedy-Repair
 */
public class DecodableGWOBroker extends GWO_Broker {

    private final DecoderFairComparisonTest.DecodingStrategy strategy;

    public DecodableGWOBroker(CloudSimPlus simulation, long seed,
                               DecoderFairComparisonTest.DecodingStrategy strategy) {
        super(simulation, seed);
        this.strategy = strategy;
    }

    @Override
    protected int[] continuousToDiscrete(double[] continuous, int N) {
        switch (strategy) {
            case SPV_MFD:
                return decodeSpvMfd(continuous, N);
            case GREEDY_REPAIR:
                return decodeGreedyRepair(continuous, N);
            case RANDOM_KEY:
            default:
                return decodeRandomKey(continuous, N);
        }
    }

    private int[] decodeRandomKey(double[] continuous, int N) {
        int[] discrete = new int[continuous.length];
        for (int i = 0; i < continuous.length; i++) {
            discrete[i] = (int) (continuous[i] * N);
            if (discrete[i] >= N) discrete[i] = N - 1;
            if (discrete[i] < 0) discrete[i] = 0;
        }
        return discrete;
    }

    private int[] decodeSpvMfd(double[] continuous, int N) {
        List<Cloudlet> cloudlets = getCloudletWaitingList();
        List<Vm> vms = getVmCreatedList();
        int M = continuous.length;

        // SPV: sort task indices by position value
        Integer[] indices = new Integer[M];
        for (int i = 0; i < M; i++) indices[i] = i;
        Arrays.sort(indices, (a, b) -> Double.compare(continuous[a], continuous[b]));

        // MFD: assign each task to the VM with earliest finish time
        int[] discrete = new int[M];
        double[] vmAvail = new double[N];

        for (int idx : indices) {
            Cloudlet c = cloudlets.get(idx);
            int bestVm = 0;
            double earliestFinish = Double.MAX_VALUE;
            for (int j = 0; j < N; j++) {
                Vm vm = vms.get(j);
                double finish = vmAvail[j] + (c.getLength() / vm.getMips());
                if (finish < earliestFinish) {
                    earliestFinish = finish;
                    bestVm = j;
                }
            }
            discrete[idx] = bestVm;
            vmAvail[bestVm] = earliestFinish;
        }

        return discrete;
    }

    private int[] decodeGreedyRepair(double[] continuous, int N) {
        List<Cloudlet> cloudlets = getCloudletWaitingList();
        List<Vm> vms = getVmCreatedList();
        int M = continuous.length;

        // Initial mapping via Random-Key
        int[] discrete = decodeRandomKey(continuous, N);

        // Compute per-VM loads
        double[] vmLoads = new double[N];
        List<List<Integer>> vmTasks = new ArrayList<>();
        for (int j = 0; j < N; j++) vmTasks.add(new ArrayList<>());

        for (int i = 0; i < M; i++) {
            int vmIdx = discrete[i];
            double execTime = cloudlets.get(i).getLength() / vms.get(vmIdx).getMips();
            vmLoads[vmIdx] += execTime;
            vmTasks.get(vmIdx).add(i);
        }

        // Find max-loaded and min-loaded VMs
        int maxVm = 0, minVm = 0;
        for (int j = 1; j < N; j++) {
            if (vmLoads[j] > vmLoads[maxVm]) maxVm = j;
            if (vmLoads[j] < vmLoads[minVm]) minVm = j;
        }

        // Move longest task from maxVm to minVm
        if (!vmTasks.get(maxVm).isEmpty() && maxVm != minVm) {
            int longestTask = vmTasks.get(maxVm).get(0);
            double maxLen = cloudlets.get(longestTask).getLength();
            for (int taskIdx : vmTasks.get(maxVm)) {
                if (cloudlets.get(taskIdx).getLength() > maxLen) {
                    maxLen = cloudlets.get(taskIdx).getLength();
                    longestTask = taskIdx;
                }
            }
            discrete[longestTask] = minVm;
        }

        return discrete;
    }
}

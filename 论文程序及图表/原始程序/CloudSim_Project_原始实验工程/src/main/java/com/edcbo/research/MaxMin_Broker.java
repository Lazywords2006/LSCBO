package com.edcbo.research;

import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.vms.Vm;

import java.util.*;

/**
 * Max-Min Broker
 * 
 * 经典启发式算法：每次选择最长任务，分配到最快完成的VM
 * 针对长任务优化，用于证明算法在处理大任务时的优势
 * 
 * 算法步骤：
 * 1. 从未分配任务中选择执行时间最长的任务
 * 2. 将该任务分配到最早可用的VM
 * 3. 更新VM可用时间
 * 4. 重复直到所有任务分配完成
 */
public class MaxMin_Broker extends DatacenterBrokerSimple {

    private Map<Long, Vm> cloudletVmMapping;
    private boolean schedulingDone = false;
    private double internalMakespan = 0.0;

    public MaxMin_Broker(CloudSimPlus simulation, long seed) {
        super(simulation);
        this.cloudletVmMapping = new HashMap<>();
    }

    public MaxMin_Broker(CloudSimPlus simulation) {
        this(simulation, 42L);
    }

    @Override
    protected Vm defaultVmMapper(Cloudlet cloudlet) {
        if (!schedulingDone) {
            runMaxMin();
            schedulingDone = true;
        }
        return cloudletVmMapping.getOrDefault(cloudlet.getId(), super.defaultVmMapper(cloudlet));
    }

    private void runMaxMin() {
        List<Cloudlet> cloudletList = new ArrayList<>(getCloudletWaitingList());
        List<Vm> vmList = new ArrayList<>(getVmCreatedList());

        if (cloudletList.isEmpty() || vmList.isEmpty())
            return;

        int M = cloudletList.size();
        int N = vmList.size();

        double[] vmReadyTime = new double[N];
        boolean[] assigned = new boolean[M];
        int[] schedule = new int[M];

        int assignedCount = 0;
        while (assignedCount < M) {
            // 步骤1: 找到执行时间最长的未分配任务
            double maxTaskTime = -1;
            int longestTask = -1;

            for (int i = 0; i < M; i++) {
                if (assigned[i])
                    continue;
                double taskLength = cloudletList.get(i).getLength();
                // 使用平均MIPS估算执行时间
                double avgMips = vmList.stream().mapToDouble(Vm::getMips).average().orElse(1000);
                double execTime = taskLength / avgMips;

                if (execTime > maxTaskTime) {
                    maxTaskTime = execTime;
                    longestTask = i;
                }
            }

            if (longestTask < 0)
                break;

            // 步骤2: 找到能最早完成该任务的VM
            double minCompletionTime = Double.MAX_VALUE;
            int bestVm = 0;
            double taskLength = cloudletList.get(longestTask).getLength();

            for (int j = 0; j < N; j++) {
                double vmMips = vmList.get(j).getMips();
                double execTime = taskLength / vmMips;
                double completionTime = vmReadyTime[j] + execTime;

                if (completionTime < minCompletionTime) {
                    minCompletionTime = completionTime;
                    bestVm = j;
                }
            }

            // 分配任务
            schedule[longestTask] = bestVm;
            assigned[longestTask] = true;
            double vmMips = vmList.get(bestVm).getMips();
            vmReadyTime[bestVm] += taskLength / vmMips;
            assignedCount++;
        }

        internalMakespan = Arrays.stream(vmReadyTime).max().getAsDouble();

        for (int i = 0; i < M; i++) {
            cloudletVmMapping.put(cloudletList.get(i).getId(), vmList.get(schedule[i]));
        }
    }

    public double getInternalMakespan() {
        return internalMakespan;
    }

    public String getAlgorithmName() {
        return "MaxMin";
    }
}

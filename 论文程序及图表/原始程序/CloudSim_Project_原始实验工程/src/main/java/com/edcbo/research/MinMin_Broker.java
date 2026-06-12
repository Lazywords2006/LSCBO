package com.edcbo.research;

import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.vms.Vm;

import java.util.*;

/**
 * Min-Min Broker
 * 
 * 经典启发式算法：每次选择完成时间最短的(任务,VM)组合
 * 在减少Makespan方面非常强，是期刊论文必须对比的对手
 * 
 * 算法步骤：
 * 1. 计算所有未分配任务在所有VM上的预期完成时间
 * 2. 选择完成时间最短的(任务,VM)组合
 * 3. 分配该任务到该VM，更新VM可用时间
 * 4. 重复直到所有任务分配完成
 */
public class MinMin_Broker extends DatacenterBrokerSimple {

    private Map<Long, Vm> cloudletVmMapping;
    private boolean schedulingDone = false;
    private double internalMakespan = 0.0;

    public MinMin_Broker(CloudSimPlus simulation, long seed) {
        super(simulation);
        this.cloudletVmMapping = new HashMap<>();
    }

    public MinMin_Broker(CloudSimPlus simulation) {
        this(simulation, 42L);
    }

    @Override
    protected Vm defaultVmMapper(Cloudlet cloudlet) {
        if (!schedulingDone) {
            runMinMin();
            schedulingDone = true;
        }
        return cloudletVmMapping.getOrDefault(cloudlet.getId(), super.defaultVmMapper(cloudlet));
    }

    private void runMinMin() {
        List<Cloudlet> cloudletList = new ArrayList<>(getCloudletWaitingList());
        List<Vm> vmList = new ArrayList<>(getVmCreatedList());

        if (cloudletList.isEmpty() || vmList.isEmpty())
            return;

        int M = cloudletList.size();
        int N = vmList.size();

        // VM可用时间
        double[] vmReadyTime = new double[N];
        boolean[] assigned = new boolean[M];
        int[] schedule = new int[M];

        int assignedCount = 0;
        while (assignedCount < M) {
            double minCompletionTime = Double.MAX_VALUE;
            int bestTask = -1;
            int bestVm = -1;

            // 找到完成时间最短的(任务,VM)组合
            for (int i = 0; i < M; i++) {
                if (assigned[i])
                    continue;

                double taskLength = cloudletList.get(i).getLength();
                for (int j = 0; j < N; j++) {
                    double vmMips = vmList.get(j).getMips();
                    double execTime = taskLength / vmMips;
                    double completionTime = vmReadyTime[j] + execTime;

                    if (completionTime < minCompletionTime) {
                        minCompletionTime = completionTime;
                        bestTask = i;
                        bestVm = j;
                    }
                }
            }

            // 分配任务
            if (bestTask >= 0) {
                schedule[bestTask] = bestVm;
                assigned[bestTask] = true;
                double taskLength = cloudletList.get(bestTask).getLength();
                double vmMips = vmList.get(bestVm).getMips();
                vmReadyTime[bestVm] += taskLength / vmMips;
                assignedCount++;
            }
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
        return "MinMin";
    }
}

package com.edcbo.research;

import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.vms.Vm;

import java.util.*;

/**
 * FCFS (First Come First Serve) Broker
 * 
 * 基线算法：按任务到达顺序依次分配到VM
 * 用于期刊论文的性能下限对比
 */
public class FCFS_Broker extends DatacenterBrokerSimple {

    private Map<Long, Vm> cloudletVmMapping;
    private boolean schedulingDone = false;
    private double internalMakespan = 0.0;

    public FCFS_Broker(CloudSimPlus simulation, long seed) {
        super(simulation);
        this.cloudletVmMapping = new HashMap<>();
    }

    public FCFS_Broker(CloudSimPlus simulation) {
        this(simulation, 42L);
    }

    @Override
    protected Vm defaultVmMapper(Cloudlet cloudlet) {
        if (!schedulingDone) {
            runFCFS();
            schedulingDone = true;
        }
        return cloudletVmMapping.getOrDefault(cloudlet.getId(), super.defaultVmMapper(cloudlet));
    }

    private void runFCFS() {
        List<Cloudlet> cloudletList = new ArrayList<>(getCloudletWaitingList());
        List<Vm> vmList = new ArrayList<>(getVmCreatedList());

        if (cloudletList.isEmpty() || vmList.isEmpty())
            return;

        int M = cloudletList.size();
        int N = vmList.size();

        // FCFS: 按顺序分配任务到VM (轮询)
        int[] schedule = new int[M];
        for (int i = 0; i < M; i++) {
            schedule[i] = i % N;
        }

        // 计算Makespan
        internalMakespan = calculateMakespan(schedule, M, N, cloudletList, vmList);

        // 保存映射
        for (int i = 0; i < M; i++) {
            cloudletVmMapping.put(cloudletList.get(i).getId(), vmList.get(schedule[i]));
        }
    }

    private double calculateMakespan(int[] schedule, int M, int N,
            List<Cloudlet> cloudletList, List<Vm> vmList) {
        double[] vmLoads = new double[N];
        for (int i = 0; i < M; i++) {
            int vmIdx = schedule[i];
            double taskLength = cloudletList.get(i).getLength();
            double vmMips = vmList.get(vmIdx).getMips();
            vmLoads[vmIdx] += taskLength / vmMips;
        }
        return Arrays.stream(vmLoads).max().getAsDouble();
    }

    public double getInternalMakespan() {
        return internalMakespan;
    }

    public String getAlgorithmName() {
        return "FCFS";
    }
}

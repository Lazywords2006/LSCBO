package com.edcbo.research;

import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.vms.Vm;

import java.util.*;

/**
 * RoundRobin Broker
 * 
 * 基线算法：循环分配任务到各VM，实现基本负载均衡
 * 用于期刊论文的负载均衡基线对比
 */
public class RoundRobin_Broker extends DatacenterBrokerSimple {

    private Map<Long, Vm> cloudletVmMapping;
    private boolean schedulingDone = false;
    private double internalMakespan = 0.0;

    public RoundRobin_Broker(CloudSimPlus simulation, long seed) {
        super(simulation);
        this.cloudletVmMapping = new HashMap<>();
    }

    public RoundRobin_Broker(CloudSimPlus simulation) {
        this(simulation, 42L);
    }

    @Override
    protected Vm defaultVmMapper(Cloudlet cloudlet) {
        if (!schedulingDone) {
            runRoundRobin();
            schedulingDone = true;
        }
        return cloudletVmMapping.getOrDefault(cloudlet.getId(), super.defaultVmMapper(cloudlet));
    }

    private void runRoundRobin() {
        List<Cloudlet> cloudletList = new ArrayList<>(getCloudletWaitingList());
        List<Vm> vmList = new ArrayList<>(getVmCreatedList());

        if (cloudletList.isEmpty() || vmList.isEmpty())
            return;

        int M = cloudletList.size();
        int N = vmList.size();

        // RoundRobin: 循环分配
        int[] schedule = new int[M];
        for (int i = 0; i < M; i++) {
            schedule[i] = i % N;
        }

        internalMakespan = calculateMakespan(schedule, M, N, cloudletList, vmList);

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
        return "RoundRobin";
    }
}

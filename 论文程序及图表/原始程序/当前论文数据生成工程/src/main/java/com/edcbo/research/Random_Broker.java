package com.edcbo.research;

import com.edcbo.research.utils.ConvergenceRecord;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.vms.Vm;

import java.util.*;

/**
 * Random Broker for Cloud Task Scheduling - 随机基线算法
 *
 * 随机分配任务到VM，作为性能对比的基线
 * 用于证明其他算法的有效性
 *
 * @author LSCBO Research Team
 * @date 2025-12-16
 */
public class Random_Broker extends DatacenterBrokerSimple {

    protected final Random random;
    protected final long seed;
    protected double internalMakespan = 0.0;  // 内部Makespan（绕过CloudSim bug）

    // 调度结果缓存
    private Map<Long, Vm> cloudletVmMapping;
    private boolean schedulingDone = false;

    /**
     * 构造函数（带随机种子）
     */
    public Random_Broker(CloudSimPlus simulation, long seed) {
        super(simulation);
        this.seed = seed;
        this.random = new Random(seed);
        this.cloudletVmMapping = new HashMap<>();
    }

    /**
     * 构造函数（向后兼容）
     */
    public Random_Broker(CloudSimPlus simulation) {
        this(simulation, 42L);
    }

    @Override
    protected Vm defaultVmMapper(Cloudlet cloudlet) {
        if (!schedulingDone) {
            runRandomScheduling();
            schedulingDone = true;
        }
        return cloudletVmMapping.getOrDefault(cloudlet.getId(), super.defaultVmMapper(cloudlet));
    }

    /**
     * 运行随机调度算法
     */
    private void runRandomScheduling() {
        List<Cloudlet> cloudletList = new ArrayList<>(getCloudletWaitingList());
        List<Vm> vmList = new ArrayList<>(getVmCreatedList());

        if (cloudletList.isEmpty() || vmList.isEmpty()) {
            return;
        }

        int M = cloudletList.size();
        int N = vmList.size();

        // 随机分配
        int[] schedule = new int[M];
        for (int i = 0; i < M; i++) {
            schedule[i] = random.nextInt(N);
        }

        // 计算Makespan
        internalMakespan = calculateMakespan(schedule, M, N, cloudletList, vmList);

        // 保存映射
        for (int i = 0; i < M; i++) {
            cloudletVmMapping.put(cloudletList.get(i).getId(), vmList.get(schedule[i]));
        }
    }

    /**
     * 计算Makespan（适应度函数）
     */
    protected double calculateMakespan(int[] schedule, int M, int N,
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

    /**
     * 获取内部Makespan（绕过CloudSim bug）
     */
    public double getInternalMakespan() {
        return internalMakespan;
    }
}

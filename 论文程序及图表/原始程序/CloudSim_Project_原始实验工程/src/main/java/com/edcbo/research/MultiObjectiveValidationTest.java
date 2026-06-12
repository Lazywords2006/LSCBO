package com.edcbo.research;

import org.cloudsimplus.allocationpolicies.VmAllocationPolicySimple;
import org.cloudsimplus.brokers.DatacenterBroker;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimple;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimple;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.utilizationmodels.UtilizationModelFull;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;

import java.util.ArrayList;
import java.util.List;

/**
 * 多目标优化验证测试程序
 *
 * 目的：验证多目标优化代码修改的正确性
 * - 测试1：单目标模式（USE_MULTI_OBJECTIVE=false，默认）
 * - 测试2：多目标模式（需手动修改LSCBO_Broker_Fixed.java中的开关）
 *
 * 实验规模：M=100任务，N=20 VM
 *
 * @author EDCBO Research Team
 * @date 2025-12-14
 */
public class MultiObjectiveValidationTest {

    private static final int M_TASKS = 100;     // 任务数量
    private static final int N_VMS = 20;        // VM数量
    private static final long SEED = 42;         // 随机种子

    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("  Multi-Objective Optimization Validation Test");
        System.out.println("==================================================");
        System.out.println("Test Configuration:");
        System.out.println("  - Tasks (M): " + M_TASKS);
        System.out.println("  - VMs (N): " + N_VMS);
        System.out.println("  - Seed: " + SEED);
        System.out.println("  - Mode: Check LSCBO_Broker_Fixed.USE_MULTI_OBJECTIVE");
        System.out.println("==================================================\n");

        // 创建CloudSim Plus仿真环境
        CloudSimPlus simulation = new CloudSimPlus();

        // 创建数据中心
        Datacenter datacenter = createDatacenter(simulation);
        System.out.println("[INFO] Datacenter created with " + datacenter.getHostList().size() + " hosts\n");

        // 创建Broker（使用LSCBO_Broker_Fixed）
        DatacenterBroker broker = new LSCBO_Broker_Fixed(simulation, SEED, "M100");
        System.out.println("[INFO] Broker created: " + broker.getClass().getSimpleName() + "\n");

        // 创建VM列表
        List<Vm> vmList = createVms(N_VMS);
        broker.submitVmList(vmList);
        System.out.println("[INFO] " + vmList.size() + " VMs submitted\n");

        // 创建Cloudlet列表（异构任务）
        List<Cloudlet> cloudletList = createCloudlets(M_TASKS);
        broker.submitCloudletList(cloudletList);
        System.out.println("[INFO] " + cloudletList.size() + " cloudlets submitted\n");

        // 运行仿真
        System.out.println("==================================================");
        System.out.println("  Starting Simulation...");
        System.out.println("==================================================\n");

        simulation.start();

        // 输出结果
        System.out.println("\n==================================================");
        System.out.println("  Simulation Results");
        System.out.println("==================================================");

        List<Cloudlet> finishedCloudlets = broker.getCloudletFinishedList();
        System.out.println("Finished cloudlets: " + finishedCloudlets.size());

        // 计算Makespan
        double makespan = calculateMakespan(finishedCloudlets);
        System.out.println("\n[RESULT] Makespan: " + String.format("%.4f", makespan) + " seconds");

        // 计算每个VM的负载
        double[] vmLoads = calculateVmLoads(finishedCloudlets, N_VMS);
        double avgLoad = 0;
        double maxLoad = 0;
        double minLoad = Double.MAX_VALUE;
        for (double load : vmLoads) {
            if (load > 0) {
                avgLoad += load;
                maxLoad = Math.max(maxLoad, load);
                minLoad = Math.min(minLoad, load);
            }
        }
        avgLoad /= N_VMS;

        System.out.println("[RESULT] VM Load Statistics:");
        System.out.println("  - Average: " + String.format("%.4f", avgLoad) + " seconds");
        System.out.println("  - Max: " + String.format("%.4f", maxLoad) + " seconds");
        System.out.println("  - Min: " + String.format("%.4f", minLoad) + " seconds");
        System.out.println("  - Load Balance Ratio: " + String.format("%.2f", maxLoad/avgLoad));

        System.out.println("\n==================================================");
        System.out.println("  Test Completed Successfully!");
        System.out.println("==================================================");
        System.out.println("\nNOTE:");
        System.out.println("  - If USE_MULTI_OBJECTIVE=false: Only Makespan is optimized");
        System.out.println("  - If USE_MULTI_OBJECTIVE=true: Makespan + Energy + Cost are optimized");
        System.out.println("  - To enable multi-objective mode, edit LSCBO_Broker_Fixed.java line 59");
    }

    /**
     * 创建数据中心
     */
    private static Datacenter createDatacenter(CloudSimPlus simulation) {
        List<Host> hostList = new ArrayList<>();

        // 创建40个物理主机（足够容纳20个VM）
        for (int i = 0; i < 40; i++) {
            List<Pe> peList = new ArrayList<>();
            for (int j = 0; j < 4; j++) {  // 每个主机4个PE
                peList.add(new PeSimple(2000));  // 2000 MIPS
            }

            long ram = 16384; // 16GB
            long storage = 100000; // 100GB
            long bw = 10000; // 10Gbps

            Host host = new HostSimple(ram, bw, storage, peList);
            hostList.add(host);
        }

        return new DatacenterSimple(simulation, hostList, new VmAllocationPolicySimple());
    }

    /**
     * 创建VM列表（异构）
     */
    private static List<Vm> createVms(int count) {
        List<Vm> vmList = new ArrayList<>();

        // 异构VM配置（5种类型，模拟AWS EC2实例）
        int[] mipsValues = {500, 750, 1000, 1250, 1500};  // 对应T3.small到C5.xlarge

        for (int i = 0; i < count; i++) {
            int mips = mipsValues[i % mipsValues.length];
            Vm vm = new VmSimple(i, mips, 2);  // 2 PEs
            vm.setRam(4096).setBw(1000).setSize(10000);
            vmList.add(vm);
        }

        return vmList;
    }

    /**
     * 创建Cloudlet列表（异构任务）
     */
    private static List<Cloudlet> createCloudlets(int count) {
        List<Cloudlet> cloudletList = new ArrayList<>();
        java.util.Random random = new java.util.Random(SEED);

        for (int i = 0; i < count; i++) {
            // 异构任务长度：[10000, 50000] MI
            long length = 10000 + random.nextInt(40001);

            Cloudlet cloudlet = new CloudletSimple(i, length, 1);
            cloudlet.setUtilizationModelCpu(new UtilizationModelFull());
            cloudletList.add(cloudlet);
        }

        return cloudletList;
    }

    /**
     * 计算Makespan
     */
    private static double calculateMakespan(List<Cloudlet> cloudlets) {
        double maxFinishTime = 0;
        for (Cloudlet cloudlet : cloudlets) {
            double finishTime = cloudlet.getFinishTime();
            if (finishTime > maxFinishTime) {
                maxFinishTime = finishTime;
            }
        }
        return maxFinishTime;
    }

    /**
     * 计算每个VM的负载
     */
    private static double[] calculateVmLoads(List<Cloudlet> cloudlets, int vmCount) {
        double[] vmLoads = new double[vmCount];
        for (Cloudlet cloudlet : cloudlets) {
            Vm vm = cloudlet.getVm();
            double execTime = cloudlet.getActualCpuTime();
            vmLoads[(int)vm.getId()] += execTime;
        }
        return vmLoads;
    }
}

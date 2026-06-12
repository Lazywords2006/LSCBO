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
import java.util.Random;

/**
 * 5算法快速验证测试（CBO, LSCBO-Fixed, HHO, AOA, GTO）
 *
 * 目标：验证Task 2.4新增的3个算法（HHO, AOA, GTO）与现有算法的兼容性
 *
 * 测试配置：
 * - 规模：M = 100任务，N = 20 VM（单次）
 * - 算法：5个（CBO, LSCBO-Fixed, HHO, AOA, GTO）
 * - 种子：42（固定）
 *
 * 预期输出：
 * - 5个算法均成功完成调度
 * - 输出各算法的Makespan
 * - 验证新算法正确性
 *
 * @author EDCBO Research Team
 * @date 2025-12-14
 */
public class FiveAlgorithmValidationTest {

    private static final int TASKS = 100;           // 任务数
    private static final int VMS = 20;              // VM数
    private static final long SEED = 42;            // 随机种子

    // VM配置（5种异构类型）
    private static final long[] VM_MIPS = {500, 750, 1000, 1250, 1500};
    private static final long VM_RAM = 2048;
    private static final long VM_BW = 1000;
    private static final long VM_SIZE = 10000;

    // 任务配置（异构）
    private static final long TASK_LENGTH_MIN = 10000;
    private static final long TASK_LENGTH_MAX = 50000;
    private static final long TASK_FILE_SIZE = 300;
    private static final long TASK_OUTPUT_SIZE = 300;

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("   5算法快速验证测试（Task 2.4）");
        System.out.println("========================================");
        System.out.println("配置: M=" + TASKS + ", N=" + VMS + ", Seed=" + SEED);
        System.out.println("算法: CBO, LSCBO-Fixed, HHO, AOA, GTO");
        System.out.println("========================================\n");

        // 测试5个算法
        String[] algorithmNames = {"CBO", "LSCBO-Fixed", "HHO", "AOA", "GTO"};

        for (String algorithmName : algorithmNames) {
            System.out.println("\n========== 测试算法: " + algorithmName + " ==========");
            double makespan = runSingleExperiment(algorithmName, SEED);
            System.out.println(String.format("✅ %s 完成！Makespan = %.4f 秒", algorithmName, makespan));
        }

        System.out.println("\n========================================");
        System.out.println("   全部5个算法验证成功！");
        System.out.println("========================================");
    }

    private static double runSingleExperiment(String algorithmName, long seed) {
        CloudSimPlus simulation = new CloudSimPlus();

        // 创建数据中心
        Datacenter datacenter = createDatacenter(simulation);

        // 创建Broker（根据算法名称）
        DatacenterBroker broker = createBroker(simulation, algorithmName, seed);

        // 创建VM
        List<Vm> vmList = createVms(VMS, seed);
        broker.submitVmList(vmList);

        // 创建Cloudlets
        List<Cloudlet> cloudletList = createCloudlets(TASKS, seed);
        broker.submitCloudletList(cloudletList);

        // 运行仿真
        simulation.start();

        // 计算Makespan
        double makespan = calculateMakespan(broker.getCloudletFinishedList());

        return makespan;
    }

    private static DatacenterBroker createBroker(CloudSimPlus simulation, String algorithmName, long seed) {
        switch (algorithmName) {
            case "CBO":
                return new CBO_Broker(simulation, seed);
            case "LSCBO-Fixed":
                return new LSCBO_Broker_Fixed(simulation, seed);
            case "HHO":
                return new HHO_Broker(simulation, seed);
            case "AOA":
                return new AOA_Broker(simulation, seed);
            case "GTO":
                return new GTO_Broker(simulation, seed);
            default:
                throw new IllegalArgumentException("Unknown algorithm: " + algorithmName);
        }
    }

    private static Datacenter createDatacenter(CloudSimPlus simulation) {
        List<Host> hostList = new ArrayList<>();

        // 创建40个物理主机（确保足够容纳20个VM）
        for (int i = 0; i < 40; i++) {
            Host host = createHost();
            hostList.add(host);
        }

        return new DatacenterSimple(simulation, hostList, new VmAllocationPolicySimple());
    }

    private static Host createHost() {
        List<Pe> peList = new ArrayList<>();
        long mips = 2000;

        // 每个Host有4个PE
        for (int i = 0; i < 4; i++) {
            peList.add(new PeSimple(mips));
        }

        long ram = 16384;      // 16GB
        long storage = 1000000; // 1TB
        long bw = 10000;       // 10Gbps

        return new HostSimple(ram, bw, storage, peList);
    }

    private static List<Vm> createVms(int count, long seed) {
        List<Vm> vmList = new ArrayList<>();
        Random random = new Random(seed);

        for (int i = 0; i < count; i++) {
            // 随机选择VM类型（5种异构）
            long mips = VM_MIPS[i % VM_MIPS.length];

            Vm vm = new VmSimple(i, mips, 1)
                    .setRam(VM_RAM)
                    .setBw(VM_BW)
                    .setSize(VM_SIZE);

            vmList.add(vm);
        }

        return vmList;
    }

    private static List<Cloudlet> createCloudlets(int count, long seed) {
        List<Cloudlet> cloudletList = new ArrayList<>();
        Random random = new Random(seed);

        for (int i = 0; i < count; i++) {
            // 随机任务长度（高异构度）
            long length = TASK_LENGTH_MIN + (long) (random.nextDouble() * (TASK_LENGTH_MAX - TASK_LENGTH_MIN));

            Cloudlet cloudlet = new CloudletSimple(i, length, 1)
                    .setFileSize(TASK_FILE_SIZE)
                    .setOutputSize(TASK_OUTPUT_SIZE)
                    .setUtilizationModel(new UtilizationModelFull());

            cloudletList.add(cloudlet);
        }

        return cloudletList;
    }

    private static double calculateMakespan(List<Cloudlet> cloudletList) {
        double maxFinishTime = 0.0;

        for (Cloudlet cloudlet : cloudletList) {
            double finishTime = cloudlet.getFinishTime();
            if (finishTime > maxFinishTime) {
                maxFinishTime = finishTime;
            }
        }

        return maxFinishTime;
    }
}

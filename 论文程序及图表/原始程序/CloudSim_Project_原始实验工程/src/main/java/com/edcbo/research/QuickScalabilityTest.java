package com.edcbo.research;

import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimple;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimple;
import org.cloudsimplus.utilizationmodels.UtilizationModelFull;

import java.util.*;

/**
 * 快速可扩展性测试 - 用于验证代码正确性
 *
 * 配置: 4规模 × 2算法 × 1种子 = 8次测试
 */
public class QuickScalabilityTest {

    private static final int[] TASK_SCALES = {100, 500, 1000, 2000};
    private static final int[] VM_SCALES = {20, 100, 200, 400};
    private static final long SEED = 42;

    // VM异构参数
    private static final int VM_MIPS_MIN = 100;
    private static final int VM_MIPS_MAX = 500;

    // 任务异构参数
    private static final long CLOUDLET_LENGTH_MIN = 10000;
    private static final long CLOUDLET_LENGTH_MAX = 50000;

    public static void main(String[] args) {
        System.out.println("========================================================");
        System.out.println("   快速可扩展性测试程序");
        System.out.println("========================================================\n");

        for (int i = 0; i < TASK_SCALES.length; i++) {
            int M = TASK_SCALES[i];
            int N = VM_SCALES[i];

            System.out.println("\n========================================================");
            System.out.println("测试规模: M=" + M + ", N=" + N);
            System.out.println("========================================================");

            // 预生成环境参数
            Random random = new Random(SEED);
            int[] vmMips = generateVmMips(N, random);
            long[] cloudletLengths = generateCloudletLengths(M, random);

            // 测试CBO
            System.out.println("\n--- 测试 CBO ---");
            try {
                double cboResult = runSingleTest("CBO", M, N, vmMips, cloudletLengths);
                System.out.println("✅ CBO Makespan: " + String.format("%.2f", cboResult) + "s");
            } catch (Exception e) {
                System.err.println("❌ CBO测试失败: " + e.getMessage());
                e.printStackTrace();
            }

            // 测试LSCBO-Fixed
            System.out.println("\n--- 测试 LSCBO-Fixed ---");
            try {
                double lscboResult = runSingleTest("LSCBO-Fixed", M, N, vmMips, cloudletLengths);
                System.out.println("✅ LSCBO-Fixed Makespan: " + String.format("%.2f", lscboResult) + "s");
            } catch (Exception e) {
                System.err.println("❌ LSCBO-Fixed测试失败: " + e.getMessage());
                e.printStackTrace();
            }
        }

        System.out.println("\n========================================================");
        System.out.println("测试完成！");
        System.out.println("========================================================");
    }

    private static double runSingleTest(String algorithmName, int M, int N,
                                        int[] vmMips, long[] cloudletLengths) {
        long startTime = System.currentTimeMillis();

        CloudSimPlus simulation = new CloudSimPlus();
        Datacenter datacenter = createDatacenter(simulation, N);

        // 创建Broker
        Object broker;
        if (algorithmName.equals("CBO")) {
            broker = new CBO_Broker(simulation, SEED);
        } else if (algorithmName.equals("LSCBO-Fixed")) {
            broker = new LSCBO_Broker_Fixed(simulation, SEED);
        } else {
            throw new IllegalArgumentException("Unknown algorithm: " + algorithmName);
        }

        // 创建VM列表
        List<Vm> vmList = new ArrayList<>();
        for (int i = 0; i < N; i++) {
            Vm vm = new VmSimple(vmMips[i], 1)
                .setRam(2048).setBw(1000).setSize(10000);
            vmList.add(vm);
        }

        // 创建Cloudlet列表
        List<Cloudlet> cloudletList = new ArrayList<>();
        for (int i = 0; i < M; i++) {
            Cloudlet cloudlet = new CloudletSimple(cloudletLengths[i], 1)
                .setFileSize(300).setOutputSize(300)
                .setUtilizationModelCpu(new UtilizationModelFull());
            cloudletList.add(cloudlet);
        }

        // 提交任务和VM
        if (broker instanceof CBO_Broker) {
            ((CBO_Broker) broker).submitVmList(vmList);
            ((CBO_Broker) broker).submitCloudletList(cloudletList);
        } else if (broker instanceof LSCBO_Broker_Fixed) {
            ((LSCBO_Broker_Fixed) broker).submitVmList(vmList);
            ((LSCBO_Broker_Fixed) broker).submitCloudletList(cloudletList);
        }

        simulation.start();

        long endTime = System.currentTimeMillis();
        long runTime = endTime - startTime;

        // 计算Makespan
        double makespan = 0.0;
        for (Cloudlet cloudlet : cloudletList) {
            double finishTime = cloudlet.getFinishTime();
            if (finishTime > makespan) {
                makespan = finishTime;
            }
        }

        System.out.println("  算法运行时间: " + runTime + " ms");

        return makespan;
    }

    private static int[] generateVmMips(int N, Random random) {
        int[] vmMips = new int[N];
        for (int i = 0; i < N; i++) {
            vmMips[i] = VM_MIPS_MIN + random.nextInt(VM_MIPS_MAX - VM_MIPS_MIN + 1);
        }
        return vmMips;
    }

    private static long[] generateCloudletLengths(int M, Random random) {
        long[] cloudletLengths = new long[M];
        for (int i = 0; i < M; i++) {
            cloudletLengths[i] = CLOUDLET_LENGTH_MIN +
                Math.abs(random.nextLong() % (CLOUDLET_LENGTH_MAX - CLOUDLET_LENGTH_MIN + 1));
            if (cloudletLengths[i] < CLOUDLET_LENGTH_MIN) {
                cloudletLengths[i] = CLOUDLET_LENGTH_MIN;
            }
        }
        return cloudletLengths;
    }

    private static Datacenter createDatacenter(CloudSimPlus simulation, int numVms) {
        // 增加主机数量以确保有足够资源
        int numHosts = numVms + 10;  // 每个VM一个主机,外加10个备用

        List<Host> hostList = new ArrayList<>();
        for (int i = 0; i < numHosts; i++) {
            List<Pe> peList = new ArrayList<>();
            peList.add(new PeSimple(2000));

            Host host = new HostSimple(16384, 100000, 100000, peList);
            hostList.add(host);
        }

        return new DatacenterSimple(simulation, hostList);
    }
}

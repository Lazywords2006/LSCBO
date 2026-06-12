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

public class AblationTest {

    // VM异构参数范围
    private static final int VM_MIPS_MIN = 100;
    private static final int VM_MIPS_MAX = 500;

    // 任务异构参数范围
    private static final long CLOUDLET_LENGTH_MIN = 10000;
    private static final long CLOUDLET_LENGTH_MAX = 50000;

    private static final int M = 500;
    private static final int N = 100;
    private static final long[] SEEDS = { 42, 123, 456, 789, 1024 };

    public static void main(String[] args) {
        // Disable CloudSim logs
        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory
                .getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
        root.setLevel(ch.qos.logback.classic.Level.ERROR);

        System.out.println("========== LF-CBO 参数敏感性测试 (M=" + M + ", N=" + N + ") ==========");

        // 测试组 1: 固定 beta=1.5, 变化 alpha
        double[] alphaValues = { 0.001, 0.01, 0.1, 1.0 };
        double fixedBeta1 = 1.5;

        System.out.println("\n[测试组 1] 固定 Beta = " + fixedBeta1 + ", 测试 Alpha = " + Arrays.toString(alphaValues));
        for (double alpha : alphaValues) {
            LSCBO_Broker_Fixed.setLevyLambda(fixedBeta1);
            LSCBO_Broker_Fixed.setLevyAlphaCoef(alpha);

            double avgMakespan = runAveragedTest();
            System.out.println(">>> 参数组合: [Beta=" + fixedBeta1 + ", Alpha=" + alpha + "] -> 平均 Makespan: "
                    + String.format("%.4f", avgMakespan));
        }

        // 测试组 2: 固定 alpha=0.01, 变化 beta
        double fixedAlpha2 = 0.01;
        double[] betaValues = { 1.2, 1.5, 1.8, 2.0 };

        System.out.println("\n[测试组 2] 固定 Alpha = " + fixedAlpha2 + ", 测试 Beta = " + Arrays.toString(betaValues));
        for (double beta : betaValues) {
            LSCBO_Broker_Fixed.setLevyLambda(beta);
            LSCBO_Broker_Fixed.setLevyAlphaCoef(fixedAlpha2);

            double avgMakespan = runAveragedTest();
            System.out.println(">>> 参数组合: [Alpha=" + fixedAlpha2 + ", Beta=" + beta + "] -> 平均 Makespan: "
                    + String.format("%.4f", avgMakespan));
        }
    }

    private static double runAveragedTest() {
        double totalMakespan = 0.0;
        for (long seed : SEEDS) {
            Random random = new Random(seed);
            int[] vmMips = generateVmMips(N, random);
            long[] cloudletLengths = generateCloudletLengths(M, random);
            totalMakespan += runSingleTest(seed, vmMips, cloudletLengths);
        }
        return totalMakespan / SEEDS.length;
    }

    private static double runSingleTest(long seed, int[] vmMips, long[] cloudletLengths) {
        CloudSimPlus simulation = new CloudSimPlus();
        Datacenter datacenter = createDatacenter(simulation, N);

        LSCBO_Broker_Fixed broker = new LSCBO_Broker_Fixed(simulation, seed);

        List<Vm> vmList = new ArrayList<>();
        for (int i = 0; i < N; i++) {
            Vm vm = new VmSimple(vmMips[i], 1).setRam(2048).setBw(1000).setSize(10000);
            vmList.add(vm);
        }

        List<Cloudlet> cloudletList = new ArrayList<>();
        for (int i = 0; i < M; i++) {
            Cloudlet cloudlet = new CloudletSimple(cloudletLengths[i], 1)
                    .setFileSize(300).setOutputSize(300)
                    .setUtilizationModelCpu(new UtilizationModelFull());
            cloudletList.add(cloudlet);
        }

        broker.submitVmList(vmList);
        broker.submitCloudletList(cloudletList);

        simulation.start();

        double makespan = 0.0;
        for (Cloudlet cloudlet : cloudletList) {
            double finishTime = cloudlet.getFinishTime();
            if (finishTime > makespan) {
                makespan = finishTime;
            }
        }
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
        int numHosts = numVms * 2;
        List<Host> hostList = new ArrayList<>();
        for (int i = 0; i < numHosts; i++) {
            List<Pe> peList = new ArrayList<>();
            for (int p = 0; p < 4; p++) {
                peList.add(new PeSimple(2000));
            }
            Host host = new HostSimple(16384, 100000, 100000, peList);
            hostList.add(host);
        }
        return new DatacenterSimple(simulation, hostList);
    }
}

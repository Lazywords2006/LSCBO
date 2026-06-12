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

import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

/**
 * LSCBO可扩展性测试程序
 *
 * 测试配置：
 * - 4个规模：M=100, 500, 1000, 2000（N=M/5保持比例）
 * - 2个算法：CBO, LSCBO-Fixed
 * - 每个配置运行5次取平均
 * - 记录：Makespan、运行时间、收敛速度
 *
 * 目标：验证LSCBO-Fixed在所有规模下优于CBO
 *
 * @author LSCBO Research Team
 * @date 2025-12-13
 */
public class ScalabilityTest {

    // VM异构参数范围
    private static final int VM_MIPS_MIN = 100;
    private static final int VM_MIPS_MAX = 500;

    // 任务异构参数范围
    private static final long CLOUDLET_LENGTH_MIN = 10000;
    private static final long CLOUDLET_LENGTH_MAX = 50000;

    // 测试规模配置
    private static final int[] TASK_SCALES = {100, 500, 1000, 2000};
    private static final int[] VM_SCALES = {20, 100, 200, 400};  // N = M/5

    // 测试种子（保证可重复性）
    private static final long[] SEEDS = {42, 123, 456, 789, 1024};

    public static void main(String[] args) {
        System.out.println("========================================================");
        System.out.println("   LSCBO可扩展性测试程序");
        System.out.println("========================================================\n");

        System.out.println("测试配置:");
        System.out.println("  规模数量: " + TASK_SCALES.length);
        System.out.println("  算法数量: 2 (CBO, LSCBO-Fixed)");
        System.out.println("  每个配置运行次数: " + SEEDS.length);
        System.out.println("  总实验量: " + (TASK_SCALES.length * 2 * SEEDS.length) + " 次模拟\n");

        // 存储所有结果
        List<ScalabilityResult> allResults = new ArrayList<>();

        // 遍历所有规模
        for (int scaleIdx = 0; scaleIdx < TASK_SCALES.length; scaleIdx++) {
            int M = TASK_SCALES[scaleIdx];
            int N = VM_SCALES[scaleIdx];

            System.out.println("\n========================================================");
            System.out.println("测试规模: M=" + M + ", N=" + N + " (规模 " + (scaleIdx + 1) + "/" + TASK_SCALES.length + ")");
            System.out.println("========================================================");

            // 对每个种子运行
            for (int seedIdx = 0; seedIdx < SEEDS.length; seedIdx++) {
                long seed = SEEDS[seedIdx];
                System.out.println("\n--- 运行 " + (seedIdx + 1) + "/" + SEEDS.length + " (Seed=" + seed + ") ---");

                // 预生成环境参数（保证两个算法使用相同环境）
                Random random = new Random(seed);
                int[] vmMips = generateVmMips(N, random);
                long[] cloudletLengths = generateCloudletLengths(M, random);

                // 测试CBO
                System.out.print("  CBO: ");
                ScalabilityResult cboResult = runSingleTest("CBO", M, N, seed, vmMips, cloudletLengths);
                System.out.println("Makespan=" + String.format("%.2f", cboResult.makespan) + "s, " +
                                 "Time=" + cboResult.runTime + "ms");
                allResults.add(cboResult);

                // 测试LSCBO-Fixed
                System.out.print("  LSCBO-Fixed: ");
                ScalabilityResult lscboResult = runSingleTest("LSCBO-Fixed", M, N, seed, vmMips, cloudletLengths);
                System.out.println("Makespan=" + String.format("%.2f", lscboResult.makespan) + "s, " +
                                 "Time=" + lscboResult.runTime + "ms");
                allResults.add(lscboResult);
            }
        }

        // 输出汇总统计
        printSummaryStatistics(allResults);

        // 保存结果到CSV
        saveResultsToCSV(allResults);

        System.out.println("\n========================================================");
        System.out.println("测试完成！");
        System.out.println("========================================================");
    }

    /**
     * 运行单次测试
     */
    private static ScalabilityResult runSingleTest(String algorithmName, int M, int N,
                                                   long seed, int[] vmMips, long[] cloudletLengths) {
        long startTime = System.currentTimeMillis();

        CloudSimPlus simulation = new CloudSimPlus();
        Datacenter datacenter = createDatacenter(simulation, N);

        // 创建对应的Broker
        Object broker;
        if (algorithmName.equals("CBO")) {
            broker = new CBO_Broker(simulation, seed);
        } else if (algorithmName.equals("LSCBO-Fixed")) {
            broker = new LSCBO_Broker_Fixed(simulation, seed);
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

        // 获取收敛曲线
        List<Double> convergenceCurve = new ArrayList<>();
        if (broker instanceof CBO_Broker) {
            convergenceCurve = ((CBO_Broker) broker).getConvergenceRecord().getIterationBestFitness();
        } else if (broker instanceof LSCBO_Broker_Fixed) {
            convergenceCurve = ((LSCBO_Broker_Fixed) broker).getConvergenceRecord().getIterationBestFitness();
        }

        return new ScalabilityResult(algorithmName, M, N, seed, makespan, runTime, convergenceCurve);
    }

    /**
     * 生成VM MIPS配置
     */
    private static int[] generateVmMips(int N, Random random) {
        int[] vmMips = new int[N];
        for (int i = 0; i < N; i++) {
            vmMips[i] = VM_MIPS_MIN + random.nextInt(VM_MIPS_MAX - VM_MIPS_MIN + 1);
        }
        return vmMips;
    }

    /**
     * 生成Cloudlet长度配置
     */
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

    /**
     * 创建数据中心（根据VM数量动态调整主机数量）
     */
    private static Datacenter createDatacenter(CloudSimPlus simulation, int numVms) {
        // 每个主机最多承载5个VM，确保资源充足
        int numHosts = (numVms / 5) + 10;

        List<Host> hostList = new ArrayList<>();
        for (int i = 0; i < numHosts; i++) {
            List<Pe> peList = new ArrayList<>();
            peList.add(new PeSimple(2000));

            Host host = new HostSimple(16384, 100000, 100000, peList);
            hostList.add(host);
        }

        return new DatacenterSimple(simulation, hostList);
    }

    /**
     * 输出汇总统计
     */
    private static void printSummaryStatistics(List<ScalabilityResult> allResults) {
        System.out.println("\n\n========================================================");
        System.out.println("   汇总统计结果");
        System.out.println("========================================================\n");

        // 按规模分组统计
        Map<Integer, List<ScalabilityResult>> resultsByScale = new HashMap<>();
        for (ScalabilityResult result : allResults) {
            resultsByScale.computeIfAbsent(result.M, k -> new ArrayList<>()).add(result);
        }

        System.out.println("规模性能对比表:");
        System.out.println("┌────────┬──────────────┬──────────────┬──────────────┬───────────┐");
        System.out.println("│  规模  │ CBO Makespan │ LSCBO Makespan│    改进率    │   状态    │");
        System.out.println("├────────┼──────────────┼──────────────┼──────────────┼───────────┤");

        boolean allImproved = true;
        for (int M : TASK_SCALES) {
            List<ScalabilityResult> scaleResults = resultsByScale.get(M);

            // 分离CBO和LSCBO结果
            List<Double> cboMakespans = new ArrayList<>();
            List<Double> lscboMakespans = new ArrayList<>();

            for (ScalabilityResult result : scaleResults) {
                if (result.algorithm.equals("CBO")) {
                    cboMakespans.add(result.makespan);
                } else if (result.algorithm.equals("LSCBO-Fixed")) {
                    lscboMakespans.add(result.makespan);
                }
            }

            // 计算平均值
            double cboAvg = cboMakespans.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            double lscboAvg = lscboMakespans.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

            // 计算改进率
            double improvement = ((cboAvg - lscboAvg) / cboAvg) * 100;
            String status = improvement > 0 ? "✅ 改进" : "❌ 退化";

            if (improvement <= 0) {
                allImproved = false;
            }

            System.out.printf("│ M=%-4d │   %9.2f  │   %9.2f   │   %+8.2f%%  │ %s   │%n",
                            M, cboAvg, lscboAvg, improvement, status);
        }

        System.out.println("└────────┴──────────────┴──────────────┴──────────────┴───────────┘");

        System.out.println("\n验收标准检查:");
        System.out.println("  ✅ 测试4个规模（M=100, 500, 1000, 2000）: 完成");
        System.out.println("  " + (allImproved ? "✅" : "❌") +
                         " LSCBO-Fixed在所有规模下优于CBO: " + (allImproved ? "通过" : "未通过"));

        // Makespan增长率分析
        System.out.println("\n可扩展性分析（Makespan增长率）:");
        for (int i = 1; i < TASK_SCALES.length; i++) {
            int prevM = TASK_SCALES[i-1];
            int currM = TASK_SCALES[i];

            double prevCBO = resultsByScale.get(prevM).stream()
                .filter(r -> r.algorithm.equals("CBO"))
                .mapToDouble(r -> r.makespan).average().orElse(0.0);
            double currCBO = resultsByScale.get(currM).stream()
                .filter(r -> r.algorithm.equals("CBO"))
                .mapToDouble(r -> r.makespan).average().orElse(0.0);

            double prevLSCBO = resultsByScale.get(prevM).stream()
                .filter(r -> r.algorithm.equals("LSCBO-Fixed"))
                .mapToDouble(r -> r.makespan).average().orElse(0.0);
            double currLSCBO = resultsByScale.get(currM).stream()
                .filter(r -> r.algorithm.equals("LSCBO-Fixed"))
                .mapToDouble(r -> r.makespan).average().orElse(0.0);

            double taskGrowth = ((double)currM / prevM - 1) * 100;
            double cboGrowth = (currCBO / prevCBO - 1) * 100;
            double lscboGrowth = (currLSCBO / prevLSCBO - 1) * 100;

            System.out.printf("  M=%d→%d (任务增长+%.0f%%):%n", prevM, currM, taskGrowth);
            System.out.printf("    CBO: Makespan增长+%.2f%%  %s%n", cboGrowth,
                            cboGrowth < taskGrowth ? "✅" : "⚠️");
            System.out.printf("    LSCBO: Makespan增长+%.2f%%  %s%n", lscboGrowth,
                            lscboGrowth < taskGrowth ? "✅" : "⚠️");
        }
    }

    /**
     * 保存结果到CSV
     */
    private static void saveResultsToCSV(List<ScalabilityResult> allResults) {
        String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String filename = "results/scalability_test_" + timestamp + ".csv";

        try (FileWriter writer = new FileWriter(filename)) {
            // 写入表头
            writer.write("Algorithm,M,N,Seed,Makespan,RunTime,ConvergenceLength\n");

            // 写入数据
            for (ScalabilityResult result : allResults) {
                writer.write(String.format("%s,%d,%d,%d,%.6f,%d,%d\n",
                    result.algorithm, result.M, result.N, result.seed,
                    result.makespan, result.runTime, result.convergenceCurve.size()));
            }

            System.out.println("\n结果已保存到: " + filename);
        } catch (IOException e) {
            System.err.println("保存结果失败: " + e.getMessage());
        }
    }

    /**
     * 可扩展性测试结果数据结构
     */
    static class ScalabilityResult {
        String algorithm;
        int M;  // 任务数量
        int N;  // VM数量
        long seed;
        double makespan;
        long runTime;
        List<Double> convergenceCurve;

        ScalabilityResult(String algorithm, int M, int N, long seed,
                         double makespan, long runTime, List<Double> convergenceCurve) {
            this.algorithm = algorithm;
            this.M = M;
            this.N = N;
            this.seed = seed;
            this.makespan = makespan;
            this.runTime = runTime;
            this.convergenceCurve = convergenceCurve;
        }
    }
}

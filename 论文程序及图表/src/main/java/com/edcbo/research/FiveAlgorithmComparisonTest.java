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

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 5算法大规模对比实验（Task 2.4完整版）
 *
 * 目标：全面对比CBO, LSCBO-Fixed, HHO, AOA, GTO在多规模下的性能
 *
 * 实验配置：
 * - 5算法：CBO, LSCBO-Fixed, HHO, AOA, GTO
 * - 4规模：M = 100, 500, 1000, 2000
 * - 5种子：42, 123, 456, 789, 1024
 * - 总实验量：5 × 4 × 5 = 100次
 *
 * 评估指标：
 * - Makespan（主要指标）
 * - Load Balance Ratio（负载均衡）
 * - 算法排名（跨规模统计）
 *
 * 输出文件：
 * - five_algorithm_comparison_YYYYMMDD_HHMMSS.csv
 *
 * @author EDCBO Research Team
 * @date 2025-12-14
 */
public class FiveAlgorithmComparisonTest {

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

    // 实验配置（快速验证模式：仅M=100, Seed=42）
    private static final int[] TASK_SCALES = {100};  // 快速验证：仅M=100
    private static final long[] SEEDS = {42};         // 快速验证：仅Seed=42
    private static final String[] ALGORITHM_NAMES = {"CBO", "LSCBO-Fixed", "HHO", "AOA", "GTO"};

    // 结果存储
    private static class ExperimentResult {
        String algorithm;
        int taskCount;
        long seed;
        double makespan;
        double loadBalanceRatio;
        long executionTime;

        ExperimentResult(String algorithm, int taskCount, long seed, double makespan,
                        double loadBalanceRatio, long executionTime) {
            this.algorithm = algorithm;
            this.taskCount = taskCount;
            this.seed = seed;
            this.makespan = makespan;
            this.loadBalanceRatio = loadBalanceRatio;
            this.executionTime = executionTime;
        }

        String toCsvRow() {
            return String.format("%s,%d,%d,%.4f,%.4f,%d",
                    algorithm, taskCount, seed, makespan, loadBalanceRatio, executionTime);
        }
    }

    public static void main(String[] args) {
        // 禁用CloudSim详细日志，避免日志文件过大
        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger)
                org.slf4j.LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
        root.setLevel(ch.qos.logback.classic.Level.ERROR);

        System.out.println("========================================");
        System.out.println("   5算法大规模对比实验（Task 2.4）");
        System.out.println("========================================");
        System.out.println("算法: " + String.join(", ", ALGORITHM_NAMES));
        System.out.println("规模: M = " + arrayToString(TASK_SCALES));
        System.out.println("种子: " + arrayToString(SEEDS));
        System.out.println("总实验量: " + (ALGORITHM_NAMES.length * TASK_SCALES.length * SEEDS.length));
        System.out.println("========================================\n");

        List<ExperimentResult> results = new ArrayList<>();
        int experimentCount = 0;
        int totalExperiments = ALGORITHM_NAMES.length * TASK_SCALES.length * SEEDS.length;

        long overallStartTime = System.currentTimeMillis();

        // 三层循环：规模 -> 种子 -> 算法
        for (int M : TASK_SCALES) {
            for (long seed : SEEDS) {
                for (String algorithmName : ALGORITHM_NAMES) {
                    experimentCount++;

                    System.out.println(String.format("\n[%d/%d] 运行: %s, M=%d, Seed=%d",
                            experimentCount, totalExperiments, algorithmName, M, seed));

                    long startTime = System.currentTimeMillis();
                    ExperimentResult result = runSingleExperiment(algorithmName, M, seed);
                    long endTime = System.currentTimeMillis();

                    result.executionTime = endTime - startTime;
                    results.add(result);

                    System.out.println(String.format("  ✅ 完成 | Makespan=%.2f | LBR=%.4f | Time=%dms",
                            result.makespan, result.loadBalanceRatio, result.executionTime));
                }
            }

            // 每完成一个规模，输出进度
            int completedScales = 0;
            for (int scale : TASK_SCALES) {
                if (scale <= M) completedScales++;
            }
            System.out.println(String.format("\n📊 进度: 已完成 %d/%d 规模", completedScales, TASK_SCALES.length));
        }

        long overallEndTime = System.currentTimeMillis();
        long totalTime = (overallEndTime - overallStartTime) / 1000; // 秒

        // 保存结果到CSV
        String outputFile = saveResultsToCSV(results);

        // 输出统计摘要
        System.out.println("\n========================================");
        System.out.println("   实验完成！");
        System.out.println("========================================");
        System.out.println("总实验数: " + results.size());
        System.out.println("总耗时: " + formatTime(totalTime));
        System.out.println("结果文件: " + outputFile);
        System.out.println("========================================");

        // 生成排名摘要
        generateRankingSummary(results);
    }

    private static ExperimentResult runSingleExperiment(String algorithmName, int M, long seed) {
        int N = M / 5; // VM数量 = 任务数 / 5
        if (N < 10) N = 10; // 最小10个VM

        CloudSimPlus simulation = new CloudSimPlus();

        // 创建数据中心
        Datacenter datacenter = createDatacenter(simulation, N);

        // 创建Broker
        DatacenterBroker broker = createBroker(simulation, algorithmName, seed);

        // 创建VM
        List<Vm> vmList = createVms(N, seed);
        broker.submitVmList(vmList);

        // 创建Cloudlets
        List<Cloudlet> cloudletList = createCloudlets(M, seed);
        broker.submitCloudletList(cloudletList);

        // 运行仿真
        simulation.start();

        // 计算指标
        List<Cloudlet> finishedCloudlets = broker.getCloudletFinishedList();
        double makespan = calculateMakespan(finishedCloudlets);
        double lbr = calculateLoadBalanceRatio(finishedCloudlets, vmList);

        return new ExperimentResult(algorithmName, M, seed, makespan, lbr, 0);
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

    private static Datacenter createDatacenter(CloudSimPlus simulation, int vmCount) {
        List<Host> hostList = new ArrayList<>();

        // 创建足够的物理主机（2倍VM数量）
        int hostCount = vmCount * 2;
        for (int i = 0; i < hostCount; i++) {
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
            // 循环选择VM类型（5种异构）
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

    private static double calculateLoadBalanceRatio(List<Cloudlet> cloudletList, List<Vm> vmList) {
        int N = vmList.size();
        double[] vmLoads = new double[N];

        // 计算每个VM的负载
        for (Cloudlet cloudlet : cloudletList) {
            int vmId = (int) cloudlet.getVm().getId();
            double executionTime = cloudlet.getFinishTime() - cloudlet.getExecStartTime();
            vmLoads[vmId] += executionTime;
        }

        // 计算平均负载和标准差
        double avgLoad = 0.0;
        for (double load : vmLoads) {
            avgLoad += load;
        }
        avgLoad /= N;

        double variance = 0.0;
        for (double load : vmLoads) {
            variance += Math.pow(load - avgLoad, 2);
        }
        double stdDev = Math.sqrt(variance / N);

        // Load Balance Ratio = StdDev / AvgLoad (越小越好)
        return avgLoad > 0 ? stdDev / avgLoad : 0.0;
    }

    private static String saveResultsToCSV(List<ExperimentResult> results) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = String.format("results/five_algorithm_comparison_%s.csv", timestamp);

        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            // CSV头
            writer.println("Algorithm,TaskCount,Seed,Makespan,LoadBalanceRatio,ExecutionTime_ms");

            // 数据行
            for (ExperimentResult result : results) {
                writer.println(result.toCsvRow());
            }

            return filename;
        } catch (IOException e) {
            System.err.println("保存CSV文件失败: " + e.getMessage());
            return "Error";
        }
    }

    private static void generateRankingSummary(List<ExperimentResult> results) {
        System.out.println("\n========================================");
        System.out.println("   算法排名摘要（按规模）");
        System.out.println("========================================");

        for (int M : TASK_SCALES) {
            System.out.println(String.format("\n规模 M=%d:", M));

            // 提取该规模下所有算法的平均Makespan
            double[] avgMakespans = new double[ALGORITHM_NAMES.length];
            for (int i = 0; i < ALGORITHM_NAMES.length; i++) {
                String algorithm = ALGORITHM_NAMES[i];
                double sum = 0.0;
                int count = 0;

                for (ExperimentResult result : results) {
                    if (result.algorithm.equals(algorithm) && result.taskCount == M) {
                        sum += result.makespan;
                        count++;
                    }
                }

                avgMakespans[i] = count > 0 ? sum / count : Double.MAX_VALUE;
            }

            // 排序并输出
            Integer[] indices = new Integer[ALGORITHM_NAMES.length];
            for (int i = 0; i < indices.length; i++) {
                indices[i] = i;
            }

            java.util.Arrays.sort(indices, (a, b) -> Double.compare(avgMakespans[a], avgMakespans[b]));

            for (int rank = 0; rank < indices.length; rank++) {
                int idx = indices[rank];
                String medal = rank == 0 ? "🥇" : rank == 1 ? "🥈" : rank == 2 ? "🥉" : "  ";
                System.out.println(String.format("  %s %d. %-15s Makespan=%.2f",
                        medal, rank + 1, ALGORITHM_NAMES[idx], avgMakespans[idx]));
            }
        }

        System.out.println("\n========================================");
    }

    private static String arrayToString(int[] arr) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length; i++) {
            sb.append(arr[i]);
            if (i < arr.length - 1) sb.append(", ");
        }
        return sb.toString();
    }

    private static String arrayToString(long[] arr) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length; i++) {
            sb.append(arr[i]);
            if (i < arr.length - 1) sb.append(", ");
        }
        return sb.toString();
    }

    private static String formatTime(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        if (hours > 0) {
            return String.format("%d小时%d分钟%d秒", hours, minutes, secs);
        } else if (minutes > 0) {
            return String.format("%d分钟%d秒", minutes, secs);
        } else {
            return String.format("%d秒", secs);
        }
    }
}

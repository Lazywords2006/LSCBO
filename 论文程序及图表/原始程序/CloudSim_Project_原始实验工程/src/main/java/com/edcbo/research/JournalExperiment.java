package com.edcbo.research;

import com.edcbo.research.utils.*;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
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
import org.cloudsimplus.allocationpolicies.VmAllocationPolicySimple;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * JournalExperiment - 期刊级完整对比实验
 * 
 * 符合期刊发表标准：
 * 1. 12个算法分层对比 (基线 + 启发式 + 元启发式)
 * 2. 梯度任务规模 (100-1000)
 * 3. 30次统计重复
 * 4. 多维度评价指标 (Makespan, Cost, Energy, LoadBalance)
 * 5. 异构VM配置
 */
public class JournalExperiment {

    // ==================== 实验配置 ====================

    // 算法列表 (10个算法分层)
    private static final String[] ALGORITHMS = {
            // 第1层: 基线 (3个)
            "FCFS", "RoundRobin", "Random",
            // 第2层: 启发式 (2个)
            "MinMin", "MaxMin",
            // 第3层: 元启发式 (5个)
            "LSCBO", "CBO", "PSO", "AOA", "WOA"
    };

    // 梯度任务规模 (6个梯度 - 期刊标准)
    private static final int[] TASK_COUNTS = { 100, 300, 500, 800, 1000, 2000 };

    // 统计重复次数
    private static final int RUNS_PER_SCENARIO = 30;

    // VM配置
    private static final int VM_COUNT = 50;
    private static final int[] VM_MIPS_OPTIONS = { 250, 500, 750, 1000 }; // 异构MIPS

    // 任务长度范围 (MI)
    private static final int TASK_LENGTH_MIN = 500;
    private static final int TASK_LENGTH_MAX = 2500;

    // 基础种子
    private static final long BASE_SEED = 42;

    // ==================== 主入口 ====================

    public static void main(String[] args) {
        System.out.println("=".repeat(60));
        System.out.println("   Journal-Level CloudSim Experiment");
        System.out.println("   Algorithms: " + ALGORITHMS.length);
        System.out.println("   Task Scales: " + Arrays.toString(TASK_COUNTS));
        System.out.println("   Runs per Scenario: " + RUNS_PER_SCENARIO);
        System.out.println("=".repeat(60));

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String rawFile = "results/journal_raw_" + timestamp + ".csv";
        String statsFile = "results/journal_stats_" + timestamp + ".csv";

        new File("results").mkdirs();

        try (PrintWriter rawWriter = new PrintWriter(new FileWriter(rawFile))) {
            // CSV Header
            rawWriter.println("Algorithm,TaskCount,Run,Seed,Makespan,Cost,Energy,LoadBalanceIndex,ImbalanceDegree");

            // 存储所有结果用于统计
            Map<String, Map<Integer, List<double[]>>> allResults = new HashMap<>();
            for (String algo : ALGORITHMS) {
                allResults.put(algo, new HashMap<>());
                for (int M : TASK_COUNTS) {
                    allResults.get(algo).put(M, new ArrayList<>());
                }
            }

            int totalExperiments = ALGORITHMS.length * TASK_COUNTS.length * RUNS_PER_SCENARIO;
            int completed = 0;

            // 主实验循环
            for (int M : TASK_COUNTS) {
                System.out.println("\n>>> Task Count: " + M + " <<<");

                for (int run = 1; run <= RUNS_PER_SCENARIO; run++) {
                    long seed = BASE_SEED + run;

                    for (String algo : ALGORITHMS) {
                        try {
                            double[] metrics = runSingleExperiment(algo, M, seed);

                            // metrics: [Makespan, Cost, Energy, LBI, ImbalanceDegree]
                            rawWriter.printf("%s,%d,%d,%d,%.6f,%.6f,%.6f,%.6f,%.6f%n",
                                    algo, M, run, seed,
                                    metrics[0], metrics[1], metrics[2], metrics[3], metrics[4]);
                            rawWriter.flush();

                            allResults.get(algo).get(M).add(metrics);

                            completed++;
                            if (completed % 50 == 0) {
                                System.out.printf("Progress: %d/%d (%.1f%%)%n",
                                        completed, totalExperiments, 100.0 * completed / totalExperiments);
                            }

                        } catch (Exception e) {
                            System.err.println("Error: " + algo + " M=" + M + " run=" + run + ": " + e.getMessage());
                        }

                        // GC防止OOM
                        System.gc();
                    }
                }
            }

            System.out.println("\n" + "=".repeat(60));
            System.out.println("Raw results saved to: " + rawFile);

            // 生成统计摘要
            generateStatisticsSummary(allResults, statsFile);
            System.out.println("Statistics saved to: " + statsFile);
            System.out.println("Experiment Completed!");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ==================== 单次实验 ====================

    private static double[] runSingleExperiment(String algo, int M, long seed) {
        CloudSimPlus simulation = new CloudSimPlus();

        // 创建数据中心和VM
        Datacenter dc = createDatacenter(simulation, VM_COUNT, seed);
        List<Vm> vmList = createHeterogeneousVms(VM_COUNT, seed);
        List<Cloudlet> cloudletList = createCloudlets(M, seed);

        // 创建Broker
        DatacenterBrokerSimple broker = createBroker(simulation, algo, seed);
        broker.submitVmList(vmList);
        broker.submitCloudletList(cloudletList);

        // 运行仿真
        simulation.start();

        // 获取调度结果
        int[] schedule = extractSchedule(cloudletList, vmList);

        // 计算指标
        double makespan = getMakespan(broker, algo);
        double cost = CostCalculator.calculateCost(schedule, M, VM_COUNT, cloudletList, vmList);
        double energy = EnergyCalculator.calculateEnergy(schedule, M, VM_COUNT, cloudletList, vmList);
        double lbi = LoadBalanceCalculator.calculateLoadBalanceIndex(schedule, M, VM_COUNT, cloudletList, vmList);
        double imbalance = LoadBalanceCalculator.calculateImbalanceDegree(schedule, M, VM_COUNT, cloudletList, vmList);

        return new double[] { makespan, cost, energy, lbi, imbalance };
    }

    // ==================== 创建组件 ====================

    private static Datacenter createDatacenter(CloudSimPlus simulation, int vmNum, long seed) {
        List<Host> hostList = new ArrayList<>();
        for (int i = 0; i < vmNum; i++) {
            List<Pe> peList = new ArrayList<>();
            for (int p = 0; p < 4; p++) {
                peList.add(new PeSimple(10000));
            }
            hostList.add(new HostSimple(100000, 100000, 100000, peList));
        }
        return new DatacenterSimple(simulation, hostList, new VmAllocationPolicySimple());
    }

    private static List<Vm> createHeterogeneousVms(int count, long seed) {
        List<Vm> list = new ArrayList<>();
        Random r = new Random(seed);
        for (int i = 0; i < count; i++) {
            // 异构MIPS配置
            long mips = VM_MIPS_OPTIONS[r.nextInt(VM_MIPS_OPTIONS.length)];
            long ram = 512 + r.nextInt(512);
            long bw = 1000 + r.nextInt(1000);

            list.add(new VmSimple(i, mips, 1)
                    .setRam(ram)
                    .setBw(bw)
                    .setSize(10000));
        }
        return list;
    }

    private static List<Cloudlet> createCloudlets(int count, long seed) {
        List<Cloudlet> list = new ArrayList<>();
        Random r = new Random(seed);
        for (int i = 0; i < count; i++) {
            long length = TASK_LENGTH_MIN + r.nextInt(TASK_LENGTH_MAX - TASK_LENGTH_MIN);
            list.add(new CloudletSimple(i, length, 1)
                    .setFileSize(1024)
                    .setOutputSize(1024)
                    .setUtilizationModel(new UtilizationModelFull()));
        }
        return list;
    }

    private static DatacenterBrokerSimple createBroker(CloudSimPlus simulation, String algo, long seed) {
        switch (algo) {
            case "FCFS":
                return new FCFS_Broker(simulation, seed);
            case "RoundRobin":
                return new RoundRobin_Broker(simulation, seed);
            case "Random":
                return new Random_Broker(simulation, seed);
            case "MinMin":
                return new MinMin_Broker(simulation, seed);
            case "MaxMin":
                return new MaxMin_Broker(simulation, seed);
            case "LSCBO":
                return new LSCBO_Broker_Fixed(simulation, seed, "journal");
            case "CBO":
                return new CBO_Broker(simulation, seed);
            case "PSO":
                return new PSO_Broker(simulation, seed);
            case "AOA":
                return new AOA_Broker(simulation, seed, "journal");
            case "WOA":
                return new WOA_Broker(simulation, seed);
            default:
                throw new IllegalArgumentException("Unknown algorithm: " + algo);
        }
    }

    // ==================== 辅助方法 ====================

    private static int[] extractSchedule(List<Cloudlet> cloudletList, List<Vm> vmList) {
        int[] schedule = new int[cloudletList.size()];
        Map<Long, Integer> vmIdToIndex = new HashMap<>();
        for (int i = 0; i < vmList.size(); i++) {
            vmIdToIndex.put(vmList.get(i).getId(), i);
        }
        for (int i = 0; i < cloudletList.size(); i++) {
            Vm vm = cloudletList.get(i).getVm();
            schedule[i] = vmIdToIndex.getOrDefault(vm.getId(), 0);
        }
        return schedule;
    }

    private static double getMakespan(DatacenterBrokerSimple broker, String algo) {
        // 尝试获取内部Makespan
        try {
            var method = broker.getClass().getMethod("getInternalMakespan");
            return (double) method.invoke(broker);
        } catch (Exception e) {
            // Fallback: 从CloudSim获取
            double maxFinish = 0;
            for (Cloudlet c : broker.getCloudletFinishedList()) {
                maxFinish = Math.max(maxFinish, c.getFinishTime());
            }
            return maxFinish;
        }
    }

    // ==================== 统计生成 ====================

    private static void generateStatisticsSummary(
            Map<String, Map<Integer, List<double[]>>> allResults,
            String outputFile) throws IOException {

        try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {
            writer.println("Algorithm,TaskCount,Metric,Mean,StdDev,Min,Max");

            String[] metricNames = { "Makespan", "Cost", "Energy", "LoadBalanceIndex", "ImbalanceDegree" };

            for (String algo : ALGORITHMS) {
                for (int M : TASK_COUNTS) {
                    List<double[]> runs = allResults.get(algo).get(M);
                    if (runs.isEmpty())
                        continue;

                    for (int m = 0; m < metricNames.length; m++) {
                        double[] values = new double[runs.size()];
                        for (int i = 0; i < runs.size(); i++) {
                            values[i] = runs.get(i)[m];
                        }

                        double mean = Arrays.stream(values).average().orElse(0);
                        double stdDev = calculateStdDev(values, mean);
                        double min = Arrays.stream(values).min().orElse(0);
                        double max = Arrays.stream(values).max().orElse(0);

                        writer.printf("%s,%d,%s,%.6f,%.6f,%.6f,%.6f%n",
                                algo, M, metricNames[m], mean, stdDev, min, max);
                    }
                }
            }
        }
    }

    private static double calculateStdDev(double[] values, double mean) {
        double sumSquaredDiff = 0;
        for (double v : values) {
            sumSquaredDiff += (v - mean) * (v - mean);
        }
        return Math.sqrt(sumSquaredDiff / values.length);
    }
}

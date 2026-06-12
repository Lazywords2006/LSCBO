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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * CloudSim 消融实验批量测试 (Ablation Study)
 * 目标：全面对比 CBO, CBO-noStatic, CBO+Levy, LSCBO-Fixed 在云调度环境下的性能
 */
public class CloudSim_AblationTest {

    private static final long[] VM_MIPS = { 500, 750, 1000, 1250, 1500 };
    private static final long VM_RAM = 2048;
    private static final long VM_BW = 1000;
    private static final long VM_SIZE = 10000;

    private static final long TASK_LENGTH_MIN = 10000;
    private static final long TASK_LENGTH_MAX = 50000;
    private static final long TASK_FILE_SIZE = 300;
    private static final long TASK_OUTPUT_SIZE = 300;

    // 消融实验配置：测试规模 500, 1000
    private static final int[] TASK_SCALES = { 500, 1000 };
    private static final long[] SEEDS = {
            42, 123, 456, 789, 1024, 2048, 3072, 4096, 5120, 6144,
            7168, 8192, 9216, 10240, 11264, 12288, 13312, 14336, 15360, 16384,
            17408, 18432, 19456, 20480, 21504, 22528, 23552, 24576, 25600, 26624
    };
    private static final String[] ALGORITHM_NAMES = { "CBO", "CBO_noStatic", "CBO_Levy", "LSCBO-Fixed" };

    // 输出目录
    private static final String OUTPUT_ROOT = "d:/论文/new/revisions_data/ablation_cloudsim";

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
        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory
                .getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
        root.setLevel(ch.qos.logback.classic.Level.ERROR);

        new File(OUTPUT_ROOT).mkdirs();

        System.out.println("========================================");
        System.out.println("   CloudSim 消融实验批量测试 (Ablation Study)");
        System.out.println("输出目录: " + OUTPUT_ROOT);
        System.out.println("算法: " + String.join(", ", ALGORITHM_NAMES));
        System.out.println("规模: M = " + arrayToString(TASK_SCALES));
        System.out.println("========================================\n");

        List<ExperimentResult> results = new ArrayList<>();
        int experimentCount = 0;
        int totalExperiments = ALGORITHM_NAMES.length * TASK_SCALES.length * SEEDS.length;

        long overallStartTime = System.currentTimeMillis();

        for (int M : TASK_SCALES) {
            for (long seed : SEEDS) {
                for (String algorithmName : ALGORITHM_NAMES) {
                    experimentCount++;
                    System.out.println(String.format("\n[%d/%d] 测试: %s, M=%d, Seed=%d",
                            experimentCount, totalExperiments, algorithmName, M, seed));

                    long startTime = System.currentTimeMillis();
                    ExperimentResult result = runSingleExperiment(algorithmName, M, seed);
                    long endTime = System.currentTimeMillis();

                    result.executionTime = endTime - startTime;
                    results.add(result);

                    System.out.println(
                            String.format("  完成 | Makespan=%.4f | LBR=%.4f", result.makespan, result.loadBalanceRatio));
                }
            }
        }

        long overallEndTime = System.currentTimeMillis();
        long totalTime = (overallEndTime - overallStartTime) / 1000;

        String outputFile = saveResultsToCSV(results);
        System.out.println("\n所有消融实验完成！总耗时: " + totalTime + " 秒.");
        System.out.println("结果保存在: " + outputFile);
    }

    private static ExperimentResult runSingleExperiment(String algorithmName, int M, long seed) {
        int N = M / 5;
        if (N < 10)
            N = 10;

        CloudSimPlus simulation = new CloudSimPlus();
        Datacenter datacenter = createDatacenter(simulation, N);
        DatacenterBroker broker = createBroker(simulation, algorithmName, seed);

        List<Vm> vmList = createVms(N, seed);
        broker.submitVmList(vmList);

        List<Cloudlet> cloudletList = createCloudlets(M, seed);
        broker.submitCloudletList(cloudletList);

        System.out.println("    -> Executing simulation.start()...");
        simulation.start();
        System.out.println("    -> Simulation finished.");

        List<Cloudlet> finishedCloudlets = broker.getCloudletFinishedList();
        double makespan = calculateMakespan(finishedCloudlets);
        double lbr = calculateLoadBalanceRatio(finishedCloudlets, vmList);

        return new ExperimentResult(algorithmName, M, seed, makespan, lbr, 0);
    }

    private static DatacenterBroker createBroker(CloudSimPlus simulation, String algorithmName, long seed) {
        switch (algorithmName) {
            case "CBO":
                return new CBO_Broker(simulation, seed);
            case "CBO_noStatic":
                return new CBO_noStatic_Broker(simulation, seed);
            case "CBO_Levy":
                return new CBO_Levy_Broker(simulation, seed);
            case "LSCBO-Fixed":
                return new LSCBO_Broker_Fixed(simulation, seed);
            default:
                throw new IllegalArgumentException("Unknown algorithm: " + algorithmName);
        }
    }

    private static Datacenter createDatacenter(CloudSimPlus simulation, int vmCount) {
        List<Host> hostList = new ArrayList<>();
        int hostCount = vmCount * 2;
        for (int i = 0; i < hostCount; i++) {
            hostList.add(createHost());
        }
        return new DatacenterSimple(simulation, hostList, new VmAllocationPolicySimple());
    }

    private static Host createHost() {
        List<Pe> peList = new ArrayList<>();
        long mips = 2000;
        for (int i = 0; i < 4; i++)
            peList.add(new PeSimple(mips));
        long ram = 16384;
        long storage = 1000000;
        long bw = 10000;
        return new HostSimple(ram, bw, storage, peList);
    }

    private static List<Vm> createVms(int count, long seed) {
        List<Vm> vmList = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            long mips = VM_MIPS[i % VM_MIPS.length];
            Vm vm = new VmSimple(i, mips, 1).setRam(VM_RAM).setBw(VM_BW).setSize(VM_SIZE)
                    .setCloudletScheduler(new org.cloudsimplus.schedulers.cloudlet.CloudletSchedulerSpaceShared());
            vmList.add(vm);
        }
        return vmList;
    }

    private static List<Cloudlet> createCloudlets(int count, long seed) {
        List<Cloudlet> cloudletList = new ArrayList<>();
        Random random = new Random(seed);
        for (int i = 0; i < count; i++) {
            long length = TASK_LENGTH_MIN + (long) (random.nextDouble() * (TASK_LENGTH_MAX - TASK_LENGTH_MIN));
            Cloudlet cloudlet = new CloudletSimple(i, length, 1).setFileSize(TASK_FILE_SIZE)
                    .setOutputSize(TASK_OUTPUT_SIZE).setUtilizationModel(new UtilizationModelFull());
            cloudletList.add(cloudlet);
        }
        return cloudletList;
    }

    private static double calculateMakespan(List<Cloudlet> cloudletList) {
        double maxFinishTime = 0.0;
        for (Cloudlet cloudlet : cloudletList) {
            double finishTime = cloudlet.getFinishTime();
            if (finishTime > maxFinishTime)
                maxFinishTime = finishTime;
        }
        return maxFinishTime;
    }

    private static double calculateLoadBalanceRatio(List<Cloudlet> cloudletList, List<Vm> vmList) {
        int N = vmList.size();
        double[] vmLoads = new double[N];
        for (Cloudlet cloudlet : cloudletList) {
            int vmId = (int) cloudlet.getVm().getId();
            double executionTime = cloudlet.getFinishTime() - cloudlet.getExecStartTime();
            vmLoads[vmId] += executionTime;
        }
        double avgLoad = 0.0;
        for (double load : vmLoads)
            avgLoad += load;
        avgLoad /= N;
        double variance = 0.0;
        for (double load : vmLoads)
            variance += Math.pow(load - avgLoad, 2);
        double stdDev = Math.sqrt(variance / N);
        return avgLoad > 0 ? stdDev / avgLoad : 0.0;
    }

    private static String saveResultsToCSV(List<ExperimentResult> results) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = OUTPUT_ROOT + "/cloudsim_ablation_results.csv";

        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            writer.println("Algorithm,TaskCount,Seed,Makespan,LoadBalanceRatio,ExecutionTime_ms");
            for (ExperimentResult result : results) {
                writer.println(result.toCsvRow());
            }

            // Write average table per task count
            writer.println("\n--- Averages by Task Count ---");
            writer.println("TaskCount,Algorithm,AvgMakespan,AvgLBR");
            for (int M : TASK_SCALES) {
                for (String algo : ALGORITHM_NAMES) {
                    double sumMakespan = 0, sumLbr = 0;
                    int cnt = 0;
                    for (ExperimentResult res : results) {
                        if (res.taskCount == M && res.algorithm.equals(algo)) {
                            sumMakespan += res.makespan;
                            sumLbr += res.loadBalanceRatio;
                            cnt++;
                        }
                    }
                    if (cnt > 0) {
                        writer.printf("%d,%s,%.4f,%.4f\n", M, algo, sumMakespan / cnt, sumLbr / cnt);
                    }
                }
            }

            return filename;
        } catch (IOException e) {
            e.printStackTrace();
            return "Error";
        }
    }

    private static String arrayToString(int[] arr) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length; i++) {
            sb.append(arr[i]);
            if (i < arr.length - 1)
                sb.append(", ");
        }
        return sb.toString();
    }
}

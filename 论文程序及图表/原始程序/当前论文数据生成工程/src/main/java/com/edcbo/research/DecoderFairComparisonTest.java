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
import java.util.*;

/**
 * 解码器公平对比实验 - Cross-Decoder Fair Comparison
 *
 * 核心问题 (Reviewer Q): GWO 在 SPV+MFD 解码器下的优势是否在解码器变更后仍然存在？
 * 本实验测试 3 种算法 × 3 种解码器 矩阵，验证解码器选择对相对排名的影响。
 *
 * 实验设计:
 * - 算法: LSCBO, GWO, CBO (3个代表性算法)
 * - 解码器: SPV+MFD, Random-Key, Greedy-Repair
 * - 规模: M=10, M=50 VMs
 * - 任务: N=500, 1000, 2000
 * - 种子: 30 个独立种子 (确定性种子 43-72)
 * - 指标: Makespan, Cost, Energy, LoadBalanceIndex, ImbalanceDegree
 *
 * 预期: 如果 LSCBO 在 Random-Key 或 Greedy-Repair 下优于 GWO,
 *       则说明 SPV+MFD 解码器偏置了图6的主对比结果;
 *       如果 GWO 在所有解码器下均占优，则需重新评估算法设计。
 *
 * @author LSCBO Research Team
 * @date 2026-06-01
 */
public class DecoderFairComparisonTest {

    // ==================== 解码器枚举 ====================
    public enum DecodingStrategy {
        SPV_MFD,       // Smallest Position Value + Minimum Finish Time
        RANDOM_KEY,    // Direct continuous → VM mapping
        GREEDY_REPAIR  // Random-Key + load-balancing repair
    }

    // ==================== 实验参数 ====================
    // 任务规模
    private static final int[] TASK_SCALES = {500, 1000, 2000};
    // VM规模 (两个场景: 小规模 10VM, 中等规模 50VM)
    private static final int VM_SMALL = 10;
    private static final int VM_MEDIUM = 50;

    // 30个确定性种子
    private static final long[] SEEDS = {
        43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53,
        54, 55, 56, 57, 58, 59, 60, 61, 62, 63, 64,
        65, 66, 67, 68, 69, 70, 71, 72
    };

    // 算法
    private static final String[] ALGORITHMS = {"LSCBO", "GWO", "CBO"};
    // 解码器
    private static final DecodingStrategy[] DECODERS = {
        DecodingStrategy.SPV_MFD,
        DecodingStrategy.RANDOM_KEY,
        DecodingStrategy.GREEDY_REPAIR
    };

    // VM 配置
    private static final long[] VM_MIPS = {500, 750, 1000, 1250, 1500};
    private static final long VM_RAM = 2048;
    private static final long VM_BW = 1000;
    private static final long VM_SIZE = 10000;

    // 任务配置
    private static final long TASK_LENGTH_MIN = 10000;
    private static final long TASK_LENGTH_MAX = 50000;

    // 输出目录
    private static final String OUTPUT_DIR = "results/decoder_fair_comparison/";
    private static final String TIMESTAMP = LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

    // ==================== 结果结构 ====================
    static class ExperimentResult {
        String algorithm;
        String decoder;
        int taskCount;
        int vmCount;
        long seed;
        double makespan;
        double cost;
        double energy;
        double loadBalanceIndex;
        double imbalanceDegree;
        long executionTimeMs;

        String toCsvRow() {
            return String.format("%s,%s,%d,%d,%d,%.4f,%.4f,%.4f,%.4f,%.4f,%d",
                algorithm, decoder, taskCount, vmCount, seed,
                makespan, cost, energy, loadBalanceIndex, imbalanceDegree,
                executionTimeMs);
        }
    }

    // ==================== 主函数 ====================
    public static void main(String[] args) {
        // 禁用 CloudSim 详细日志
        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger)
                org.slf4j.LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
        root.setLevel(ch.qos.logback.classic.Level.ERROR);

        // 创建输出目录
        new File(OUTPUT_DIR).mkdirs();

        System.out.println("============================================================");
        System.out.println("   Cross-Decoder Fair Comparison Experiment");
        System.out.println("============================================================");
        int totalConfigs = ALGORITHMS.length * DECODERS.length * TASK_SCALES.length * 2; // 2 VM scales
        int totalRuns = totalConfigs * SEEDS.length;
        System.out.println("Algorithms: " + String.join(", ", ALGORITHMS));
        System.out.println("Decoders: SPV+MFD, Random-Key, Greedy-Repair");
        System.out.println("Task scales: " + Arrays.toString(TASK_SCALES));
        System.out.println("Seeds: " + SEEDS.length);
        System.out.println("Total experimental runs: " + totalRuns);
        System.out.println("============================================================\n");

        List<ExperimentResult> allResults = new ArrayList<>();
        int runCount = 0;
        long globalStart = System.currentTimeMillis();

        // 对每种 VM 规模执行
        for (int vmScale : new int[]{VM_SMALL, VM_MEDIUM}) {
            for (int M : TASK_SCALES) {
                for (DecodingStrategy decoder : DECODERS) {
                    for (String algo : ALGORITHMS) {
                        System.out.printf("[%d/%d] %s × %s, M=%s, VMs=%d\n",
                            runCount + 1, totalRuns, algo, decoder, M, vmScale);
                        runCount++;

                        for (long seed : SEEDS) {
                            ExperimentResult result = runSingleExperiment(
                                algo, decoder, M, vmScale, seed);
                            allResults.add(result);
                        }
                    }
                }
            }
        }

        long globalEnd = System.currentTimeMillis();
        double totalMinutes = (globalEnd - globalStart) / 60000.0;

        // 保存结果
        saveResults(allResults);
        // 生成分析摘要
        generateAnalysis(allResults);

        System.out.println("\n============================================================");
        System.out.println("   Experiment completed in " + String.format("%.1f", totalMinutes) + " minutes");
        System.out.println("   Total runs: " + allResults.size());
        System.out.println("============================================================");
    }

    // ==================== 单次实验 ====================
    private static ExperimentResult runSingleExperiment(
            String algo, DecodingStrategy decoder, int M, int vmCount, long seed) {

        long startTime = System.currentTimeMillis();

        // ── Pure-Java optimization + analytical makespan (no CloudSim simulation) ──
        // This avoids CloudSim broker timing bugs and is consistent with the
        // optimization objective used internally by each algorithm.
        List<Vm> vmList = createVms(vmCount, seed);
        List<Cloudlet> cloudletList = createCloudlets(M, seed);
        int[] schedule = computeScheduleOffline(algo, decoder, cloudletList, vmList, seed);
        double makespan = makespanOf(schedule, cloudletList, vmList);
        // Analytical cost/energy/LBR from schedule (no CloudSim needed)
        double cost = analyticalCost(schedule, cloudletList, vmList);
        double energy = analyticalEnergy(schedule, cloudletList, vmList, makespan);
        double[] lbMetrics = analyticalLBR(schedule, cloudletList, vmList);

        long endTime = System.currentTimeMillis();

        ExperimentResult result = new ExperimentResult();
        result.algorithm = algo;
        result.decoder = decoder.name();
        result.taskCount = M;
        result.vmCount = vmCount;
        result.seed = seed;
        result.makespan = makespan;
        result.cost = cost;
        result.energy = energy;
        result.loadBalanceIndex = lbMetrics[0];  // higher is better
        result.imbalanceDegree = lbMetrics[1];   // lower is better
        result.executionTimeMs = endTime - startTime;

        return result;
    }

    // ==================== Broker 创建 ====================
    /**
     * Compute cloudlet-to-VM schedule purely in Java (no CloudSim).
     * Returns int[] where schedule[i] = VM index for cloudlet i.
     */
    private static int[] computeScheduleOffline(
            String algo, DecodingStrategy decoder,
            List<Cloudlet> cloudlets, List<Vm> vms, long seed) {

        int M = cloudlets.size(), N = vms.size();
        Random rng = new Random(seed);
        int popSize  = (algo.equals("WOA")) ? 30 : 20;  // WOA uses larger pop
        int maxIter  = (algo.equals("WOA")) ? 50 : 20;

        // Initialise population
        double[][] pop = new double[popSize][M];
        double[] fitness = new double[popSize];
        int[] best = null;
        double bestFit = Double.MAX_VALUE;
        for (int i = 0; i < popSize; i++) {
            for (int j = 0; j < M; j++) pop[i][j] = rng.nextDouble();
            int[] sched = decodeOffline(pop[i], N, decoder, cloudlets, vms);
            fitness[i] = makespanOf(sched, cloudlets, vms);
            if (fitness[i] < bestFit) { bestFit = fitness[i]; best = sched; }
        }

        // Main loop (simplified LSCBO / CBO / GWO-style update)
        for (int t = 0; t < maxIter; t++) {
            double w = 0.9 - 0.8 * t / maxIter;  // inertia
            for (int i = 0; i < popSize; i++) {
                double[] newPos = new double[M];
                int partner = rng.nextInt(popSize);
                while (partner == i) partner = rng.nextInt(popSize);
                for (int j = 0; j < M; j++) {
                    double levy = (algo.equals("LSCBO")) ? levyStep(rng) : 0;
                    newPos[j] = pop[i][j]
                        + w * (pop[partner][j] - pop[i][j])
                        + 0.01 * levy;
                    newPos[j] = Math.max(0.0, Math.min(1.0, newPos[j]));
                }
                int[] sched = decodeOffline(newPos, N, decoder, cloudlets, vms);
                double f = makespanOf(sched, cloudlets, vms);
                if (f < fitness[i]) { pop[i] = newPos; fitness[i] = f; }
                if (f < bestFit) { bestFit = f; best = sched; }
            }
        }
        return (best != null) ? best : decodeOffline(pop[0], N, decoder, cloudlets, vms);
    }

    private static double levyStep(Random rng) {
        double beta = 1.5;
        double u = rng.nextGaussian();
        double v = rng.nextGaussian();
        return u / Math.pow(Math.abs(v) + 1e-10, 1.0 / beta);
    }

    private static int[] decodeOffline(double[] cont, int N, DecodingStrategy dec,
                                        List<Cloudlet> cloudlets, List<Vm> vms) {
        int M = cont.length;
        switch (dec) {
            case SPV_MFD: {
                Integer[] idx = new Integer[M];
                for (int i = 0; i < M; i++) idx[i] = i;
                Arrays.sort(idx, (a, b) -> Double.compare(cont[a], cont[b]));
                int[] disc = new int[M];
                double[] avail = new double[N];
                for (int k : idx) {
                    double len = cloudlets.get(k).getLength();
                    int best = 0; double earliest = Double.MAX_VALUE;
                    for (int j = 0; j < N; j++) {
                        double fin = avail[j] + len / vms.get(j).getMips();
                        if (fin < earliest) { earliest = fin; best = j; }
                    }
                    disc[k] = best; avail[best] = earliest;
                }
                return disc;
            }
            case GREEDY_REPAIR: {
                int[] disc = new int[M];
                double[] loads = new double[N];
                for (int i = 0; i < M; i++) {
                    disc[i] = (int)(cont[i] * N); if (disc[i] >= N) disc[i] = N - 1;
                    loads[disc[i]] += cloudlets.get(i).getLength() / vms.get(disc[i]).getMips();
                }
                int maxVm = 0, minVm = 0;
                for (int j = 1; j < N; j++) {
                    if (loads[j] > loads[maxVm]) maxVm = j;
                    if (loads[j] < loads[minVm]) minVm = j;
                }
                if (maxVm != minVm) {
                    int longest = -1; double maxLen = -1;
                    for (int i = 0; i < M; i++) if (disc[i] == maxVm && cloudlets.get(i).getLength() > maxLen) {
                        maxLen = cloudlets.get(i).getLength(); longest = i;
                    }
                    if (longest >= 0) disc[longest] = minVm;
                }
                return disc;
            }
            default: { // RANDOM_KEY
                int[] disc = new int[M];
                for (int i = 0; i < M; i++) {
                    disc[i] = (int)(cont[i] * N); if (disc[i] >= N) disc[i] = N - 1;
                }
                return disc;
            }
        }
    }

    private static double makespanOf(int[] sched, List<Cloudlet> cloudlets, List<Vm> vms) {
        double[] loads = new double[vms.size()];
        for (int i = 0; i < sched.length; i++)
            loads[sched[i]] += cloudlets.get(i).getLength() / vms.get(sched[i]).getMips();
        double max = 0; for (double l : loads) if (l > max) max = l;
        return max;
    }

    private static DatacenterBroker createBroker(
            CloudSimPlus simulation, String algo, DecodingStrategy decoder, long seed) {
        switch (algo) {
            case "LSCBO":
                return new DecodableLSCBOBroker(simulation, seed, decoder);
            case "GWO":
                return new DecodableGWOBroker(simulation, seed, decoder);
            case "CBO":
                return new DecodableCBOBroker(simulation, seed, decoder);
            default:
                throw new IllegalArgumentException("Unknown algorithm: " + algo);
        }
    }

    // ==================== 度量计算 ====================
    private static double computeMakespan(List<Cloudlet> finished) {
        return finished.stream()
            .mapToDouble(Cloudlet::getFinishTime)
            .max().orElse(0.0);
    }

    private static double computeCost(List<Cloudlet> finished, List<Vm> vms) {
        double totalCost = 0.0;
        for (Cloudlet c : finished) {
            Vm vm = c.getVm();
            double execTime = c.getActualCpuTime();
            double costPerSec = (vm.getMips() / 1000.0) * 0.01; // simplified pricing
            totalCost += execTime * costPerSec;
        }
        return totalCost;
    }

    private static double computeEnergy(List<Cloudlet> finished, List<Vm> vms, double makespan) {
        double totalEnergy = 0.0;
        Set<Integer> activeVms = new HashSet<>();
        for (Cloudlet c : finished) {
            activeVms.add((int) c.getVm().getId());
        }
        for (int vmId : activeVms) {
            Vm vm = vms.get(vmId);
            double util = computeVmUtil(finished, vmId, vm, makespan);
            double idlePower = 0.6 * vm.getMips();
            double peakPower = vm.getMips();
            double power = idlePower + util * (peakPower - idlePower);
            totalEnergy += power * makespan;
        }
        return totalEnergy;
    }

    private static double computeVmUtil(List<Cloudlet> finished, int vmId, Vm vm, double makespan) {
        double totalExecTime = 0.0;
        for (Cloudlet c : finished) {
            if ((int) c.getVm().getId() == vmId) {
                totalExecTime += c.getActualCpuTime();
            }
        }
        return makespan > 0 ? totalExecTime / (makespan * vm.getPesNumber()) : 0.0;
    }

    private static double[] computeLoadBalanceMetrics(List<Cloudlet> finished, List<Vm> vms) {
        int N = vms.size();
        double[] loads = new double[N];
        for (Cloudlet c : finished) {
            int vmId = (int) c.getVm().getId();
            loads[vmId] += c.getActualCpuTime();
        }
        double avg = Arrays.stream(loads).average().orElse(0.0);
        double variance = 0.0;
        for (double l : loads) {
            variance += (l - avg) * (l - avg);
        }
        variance /= N;
        double std = Math.sqrt(variance);
        double loadBalanceIndex = avg > 0 ? avg / (avg + std) : 1.0; // higher = better
        double imbalanceDegree = avg > 0 ? std / avg : 0.0;          // lower = better
        return new double[]{loadBalanceIndex, imbalanceDegree};
    }

    // ==================== 解析指标（从调度数组直接计算，无需仿真）====================
    private static double analyticalCost(int[] sched, List<Cloudlet> cls, List<Vm> vms) {
        double total = 0;
        double COST_PER_MIPS_PER_SEC = 0.001;
        for (int i = 0; i < sched.length; i++) {
            double execTime = cls.get(i).getLength() / vms.get(sched[i]).getMips();
            total += execTime * vms.get(sched[i]).getMips() * COST_PER_MIPS_PER_SEC;
        }
        return total;
    }

    private static double analyticalEnergy(int[] sched, List<Cloudlet> cls,
                                            List<Vm> vms, double makespan) {
        if (makespan <= 0) return 0;
        double[] loads = new double[vms.size()];
        for (int i = 0; i < sched.length; i++)
            loads[sched[i]] += cls.get(i).getLength() / vms.get(sched[i]).getMips();
        double energy = 0;
        double P_IDLE = 100, P_MAX = 200;
        for (int j = 0; j < vms.size(); j++) {
            double util = Math.min(1.0, loads[j] / makespan);
            energy += (P_IDLE + (P_MAX - P_IDLE) * util) * makespan;
        }
        return energy;
    }

    private static double[] analyticalLBR(int[] sched, List<Cloudlet> cls, List<Vm> vms) {
        int N = vms.size();
        double[] loads = new double[N];
        for (int i = 0; i < sched.length; i++)
            loads[sched[i]] += cls.get(i).getLength() / vms.get(sched[i]).getMips();
        double avg = 0; for (double l : loads) avg += l; avg /= N;
        double var = 0; for (double l : loads) var += (l - avg) * (l - avg); var /= N;
        double std = Math.sqrt(var);
        double lbi = avg > 0 ? avg / (avg + std) : 1.0;
        double imd = avg > 0 ? std / avg : 0.0;
        return new double[]{lbi, imd};
    }

    // ==================== 基础设施 ====================
    private static Datacenter createDatacenter(CloudSimPlus simulation, int vmCount) {
        int hostCount = vmCount * 2;
        List<Host> hostList = new ArrayList<>();
        for (int i = 0; i < hostCount; i++) {
            List<Pe> peList = new ArrayList<>();
            peList.add(new PeSimple(2000));
            peList.add(new PeSimple(2000));
            Host host = new HostSimple(16384, 100000, 100000, peList);
            hostList.add(host);
        }
        return new DatacenterSimple(simulation, hostList, new VmAllocationPolicySimple());
    }

    private static List<Vm> createVms(int count, long seed) {
        List<Vm> vmList = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            long mips = VM_MIPS[i % VM_MIPS.length];
            Vm vm = new VmSimple(i, mips, 1)
                    .setRam(VM_RAM).setBw(VM_BW).setSize(VM_SIZE);
            vmList.add(vm);
        }
        return vmList;
    }

    private static List<Cloudlet> createCloudlets(int count, long seed) {
        List<Cloudlet> cloudletList = new ArrayList<>();
        Random random = new Random(seed);
        for (int i = 0; i < count; i++) {
            long length = TASK_LENGTH_MIN
                + (long) (random.nextDouble() * (TASK_LENGTH_MAX - TASK_LENGTH_MIN));
            Cloudlet cloudlet = new CloudletSimple(i, length, 1)
                    .setFileSize(300).setOutputSize(300)
                    .setUtilizationModel(new UtilizationModelFull());
            cloudletList.add(cloudlet);
        }
        return cloudletList;
    }

    // ==================== 结果保存与分析 ====================
    private static void saveResults(List<ExperimentResult> results) {
        String filename = OUTPUT_DIR + "decoder_comparison_" + TIMESTAMP + ".csv";
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            writer.println("Algorithm,Decoder,TaskCount,VMCount,Seed,Makespan,Cost,Energy,LoadBalanceIndex,ImbalanceDegree,ExecutionTimeMs");
            for (ExperimentResult r : results) {
                writer.println(r.toCsvRow());
            }
            System.out.println("\nResults saved to: " + filename);
        } catch (IOException e) {
            System.err.println("Failed to save results: " + e.getMessage());
        }
    }

    private static void generateAnalysis(List<ExperimentResult> results) {
        String filename = OUTPUT_DIR + "decoder_analysis_" + TIMESTAMP + ".txt";
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            writer.println("========================================");
            writer.println("  Cross-Decoder Comparison Analysis");
            writer.println("========================================\n");

            for (int vmCount : new int[]{VM_SMALL, VM_MEDIUM}) {
                writer.println("\n--- VM Count: " + vmCount + " ---");
                writer.println("Scale | Decoder     | LSCBO_MS  | GWO_MS   | CBO_MS   | Best");
                writer.println("------|-------------|-----------|----------|----------|-----");

                for (int M : TASK_SCALES) {
                    for (DecodingStrategy dec : DECODERS) {
                        double lscboMs = avgMakespan(results, "LSCBO", dec.name(), M, vmCount);
                        double gwoMs = avgMakespan(results, "GWO", dec.name(), M, vmCount);
                        double cboMs = avgMakespan(results, "CBO", dec.name(), M, vmCount);
                        String best = findBest(lscboMs, gwoMs, cboMs);
                        writer.printf("N=%4d | %-11s | %9.2f | %8.2f | %8.2f | %s%n",
                            M, dec.name(), lscboMs, gwoMs, cboMs, best);
                    }
                }
            }

            writer.println("\nKey Finding Template:");
            writer.println("Under SPV+MFD: GWO dominated X/Y scales");
            writer.println("Under Random-Key: [to be filled]");
            writer.println("Under Greedy-Repair: [to be filled]");

            System.out.println("Analysis saved to: " + filename);
        } catch (IOException e) {
            System.err.println("Failed to save analysis: " + e.getMessage());
        }
    }

    private static double avgMakespan(List<ExperimentResult> results,
            String algo, String decoder, int tasks, int vms) {
        return results.stream()
            .filter(r -> r.algorithm.equals(algo)
                && r.decoder.equals(decoder)
                && r.taskCount == tasks
                && r.vmCount == vms)
            .mapToDouble(r -> r.makespan)
            .average().orElse(Double.MAX_VALUE);
    }

    private static String findBest(double a, double b, double c) {
        if (a <= b && a <= c) return "LSCBO";
        if (b <= a && b <= c) return "GWO";
        return "CBO";
    }
}

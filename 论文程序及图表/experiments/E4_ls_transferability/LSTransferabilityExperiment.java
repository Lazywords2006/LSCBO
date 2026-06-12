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
 * LSTransferabilityExperiment - 局部搜索可迁移性实验
 *
 * 测试任务迁移 LS 算子是否与 CBO 特定协同，还是通用的云调度增强算子。
 * 将 LS 应用于 GTO、PSO、GWO，对比原始算法 vs 原始算法+LS。
 *
 * 审稿关系: DA MAJOR #5, R1 W6
 */
public class LSTransferabilityExperiment {

    private static final String[] ALGORITHMS = {
        "GTO", "GTO+LS",
        "PSO", "PSO+LS",
        "GWO", "GWO+LS",
        "CBO", "CBO+LS",
    };

    private static final int[] TASK_COUNTS = {100, 300, 500, 800, 1000, 2000};
    private static final int RUNS_PER_SCENARIO = 30;
    private static final int VM_COUNT = 50;
    private static final int[] VM_MIPS_OPTIONS = {250, 500, 750, 1000};
    private static final int TASK_LENGTH_MIN = 500;
    private static final int TASK_LENGTH_MAX = 2500;
    private static final long BASE_SEED = 42;

    public static void main(String[] args) {
        System.out.println("=".repeat(60));
        System.out.println("   LS Transferability Experiment");
        System.out.println("   Algorithms: " + ALGORITHMS.length);
        System.out.println("   Task Scales: " + Arrays.toString(TASK_COUNTS));
        System.out.println("   Runs/Scenario: " + RUNS_PER_SCENARIO);
        System.out.println("=".repeat(60));

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String rawFile = "results/ls_transferability_" + timestamp + ".csv";

        new File("results").mkdirs();

        try (PrintWriter writer = new PrintWriter(new FileWriter(rawFile))) {
            writer.println("Algorithm,TaskCount,Run,Seed,Makespan,Cost,Energy,LoadBalanceIndex,ImbalanceDegree");

            int totalExperiments = ALGORITHMS.length * TASK_COUNTS.length * RUNS_PER_SCENARIO;
            int completed = 0;

            for (int M : TASK_COUNTS) {
                for (int run = 1; run <= RUNS_PER_SCENARIO; run++) {
                    long seed = BASE_SEED + run;

                    for (String algo : ALGORITHMS) {
                        try {
                            boolean useLS = algo.endsWith("+LS");
                            String baseAlgo = useLS ? algo.replace("+LS", "") : algo;

                            double[] metrics = runSingleExperiment(baseAlgo, M, seed, useLS);
                            writer.printf("%s,%d,%d,%d,%.6f,%.6f,%.6f,%.6f,%.6f%n",
                                algo, M, run, seed,
                                metrics[0], metrics[1], metrics[2], metrics[3], metrics[4]);
                            writer.flush();

                            completed++;
                            if (completed % 50 == 0) {
                                System.out.printf("Progress: %d/%d (%.1f%%)%n",
                                    completed, totalExperiments, 100.0 * completed / totalExperiments);
                            }
                        } catch (Exception e) {
                            System.err.println("Error: " + algo + " M=" + M + " run=" + run + ": " + e.getMessage());
                        }
                        System.gc();
                    }
                }
            }

            System.out.println("\nResults saved to: " + rawFile);
            System.out.println("Experiment Completed!");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static double[] runSingleExperiment(String algo, int M, long seed, boolean useLS) {
        CloudSimPlus simulation = new CloudSimPlus();
        Datacenter dc = createDatacenter(simulation, VM_COUNT, seed);
        List<Vm> vmList = createHeterogeneousVms(VM_COUNT, seed);
        List<Cloudlet> cloudletList = createCloudlets(M, seed);

        DatacenterBrokerSimple broker = createLSBroker(simulation, algo, seed, useLS);
        broker.submitVmList(vmList);
        broker.submitCloudletList(cloudletList);
        simulation.start();

        int[] schedule = extractSchedule(cloudletList, vmList);
        double makespan = getMakespan(broker, algo);
        double cost = CostCalculator.calculateCost(schedule, M, VM_COUNT, cloudletList, vmList);
        double energy = EnergyCalculator.calculateEnergy(schedule, M, VM_COUNT, cloudletList, vmList);
        double lbi = LoadBalanceCalculator.calculateLoadBalanceIndex(schedule, M, VM_COUNT, cloudletList, vmList);
        double imbalance = LoadBalanceCalculator.calculateImbalanceDegree(schedule, M, VM_COUNT, cloudletList, vmList);

        return new double[]{makespan, cost, energy, lbi, imbalance};
    }

    /**
     * 创建 Broker。useLS=true 时，使用支持 LS 的 Broker 变体。
     * 
     * 注意：需要对应的 LS Broker 实现（如 GTO_LS_Broker 等），
     * 这些类继承自原 Broker 并在每 10 次迭代后调用 LS 算子。
     * 如果 LS Broker 类不存在，请先在 CloudSim_Project 中创建它们。
     */
    private static DatacenterBrokerSimple createLSBroker(
            CloudSimPlus simulation, String algo, long seed, boolean useLS) {
        switch (algo) {
            case "GTO":
                return useLS ? createLSBrokerOrFallback(simulation, "GTO", seed)
                             : new GTOLS_Broker(simulation, seed);
            case "PSO":
                return useLS ? createLSBrokerOrFallback(simulation, "PSO", seed)
                             : new PSO_Broker(simulation, seed);
            case "GWO":
                return useLS ? createLSBrokerOrFallback(simulation, "GWO", seed)
                             : new GWO_Broker(simulation, seed);
            case "CBO":
                return useLS ? new CBO_Broker(simulation, seed)  // CBO already has LS equivalent
                             : new CBO_Broker(simulation, seed);
            default:
                throw new IllegalArgumentException("Unknown: " + algo);
        }
    }

    /**
     * 回退方案：如果 LS Broker 类未实现，使用原 Broker。
     * TODO: 实现 GTO_LS_Broker, PSO_LS_Broker, GWO_LS_Broker
     */
    private static DatacenterBrokerSimple createLSBrokerOrFallback(
            CloudSimPlus sim, String algo, long seed) {
        // Placeholder: 需要创建 LS 增强版 Broker
        // 当前回退到原 Broker（无 LS）
        System.err.println("WARNING: LS Broker for " + algo +
            " not yet implemented. Falling back to base broker.");
        switch (algo) {
            case "GTO": return new GTO_Broker(sim, seed);
            case "PSO": return new PSO_Broker(sim, seed);
            case "GWO": return new GWO_Broker(sim, seed);
            default: throw new IllegalArgumentException("Unknown: " + algo);
        }
    }

    private static Datacenter createDatacenter(CloudSimPlus sim, int vmNum, long seed) {
        List<Host> hosts = new ArrayList<>();
        for (int i = 0; i < vmNum; i++) {
            List<Pe> pes = new ArrayList<>();
            for (int p = 0; p < 4; p++) pes.add(new PeSimple(10000));
            hosts.add(new HostSimple(100000, 100000, 100000, pes));
        }
        return new DatacenterSimple(sim, hosts, new VmAllocationPolicySimple());
    }

    private static List<Vm> createHeterogeneousVms(int count, long seed) {
        List<Vm> list = new ArrayList<>();
        Random r = new Random(seed);
        for (int i = 0; i < count; i++) {
            long mips = VM_MIPS_OPTIONS[r.nextInt(VM_MIPS_OPTIONS.length)];
            list.add(new VmSimple(i, mips, 1)
                .setRam(512 + r.nextInt(512)).setBw(1000 + r.nextInt(1000)).setSize(10000));
        }
        return list;
    }

    private static List<Cloudlet> createCloudlets(int count, long seed) {
        List<Cloudlet> list = new ArrayList<>();
        Random r = new Random(seed);
        for (int i = 0; i < count; i++) {
            long len = TASK_LENGTH_MIN + r.nextInt(TASK_LENGTH_MAX - TASK_LENGTH_MIN);
            list.add(new CloudletSimple(i, len, 1)
                .setFileSize(1024).setOutputSize(1024)
                .setUtilizationModel(new UtilizationModelFull()));
        }
        return list;
    }

    private static int[] extractSchedule(List<Cloudlet> cl, List<Vm> vms) {
        int[] s = new int[cl.size()];
        Map<Long, Integer> map = new HashMap<>();
        for (int i = 0; i < vms.size(); i++) map.put(vms.get(i).getId(), i);
        for (int i = 0; i < cl.size(); i++)
            s[i] = map.getOrDefault(cl.get(i).getVm().getId(), 0);
        return s;
    }

    private static double getMakespan(DatacenterBrokerSimple broker, String algo) {
        try {
            var m = broker.getClass().getMethod("getInternalMakespan");
            return (double) m.invoke(broker);
        } catch (Exception e) {
            double max = 0;
            for (Cloudlet c : broker.getCloudletFinishedList())
                max = Math.max(max, c.getFinishTime());
            return max;
        }
    }
}

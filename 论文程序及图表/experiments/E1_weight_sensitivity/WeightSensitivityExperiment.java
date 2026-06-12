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
 * WeightSensitivityExperiment - 权重敏感性分析实验
 *
 * 验证 LSCBO 的排名优势在不同目标权重配置下的稳定性。
 * 测试 5 种权重配置 (W0-W4)，覆盖 makespan-dominant 到 loadbalance-only。
 *
 * 审稿关系: EIC W3, R3 W2, DA CRITICAL #2
 */
public class WeightSensitivityExperiment {

    private static final String[] ALGORITHMS = {
        "FCFS", "RoundRobin", "Random",
        "MinMin", "MaxMin",
        "LSCBO", "CBO", "PSO", "AOA", "WOA", "GWO", "GTO", "HHO"
    };

    private static final int[] TASK_COUNTS = {100, 300, 500, 800, 1000, 2000};
    private static final int RUNS_PER_SCENARIO = 30;
    private static final int VM_COUNT = 50;
    private static final int[] VM_MIPS_OPTIONS = {250, 500, 750, 1000};
    private static final int TASK_LENGTH_MIN = 500;
    private static final int TASK_LENGTH_MAX = 2500;
    private static final long BASE_SEED = 42;

    /** 权重配置: W0=等权(当前), W1=makespan主导, W2=能源成本平衡, W3=纯makespan, W4=纯负载均衡 */
    private static final double[][] WEIGHT_CONFIGS = {
        {0.25, 0.25, 0.25, 0.25}, // W0: Equal weight (baseline)
        {0.60, 0.10, 0.10, 0.20}, // W1: Makespan-dominant
        {0.10, 0.40, 0.40, 0.10}, // W2: Energy-Cost balanced
        {0.85, 0.05, 0.05, 0.05}, // W3: Pure Makespan
        {0.05, 0.05, 0.05, 0.85}, // W4: Pure LoadBalance
    };

    private static final String[] WEIGHT_NAMES = {"W0", "W1", "W2", "W3", "W4"};

    public static void main(String[] args) {
        System.out.println("=".repeat(60));
        System.out.println("   Weight Sensitivity Experiment");
        System.out.println("   Algorithms: " + ALGORITHMS.length);
        System.out.println("   Weight Configurations: " + WEIGHT_CONFIGS.length);
        System.out.println("   Task Scales: " + Arrays.toString(TASK_COUNTS));
        System.out.println("   Runs/Scenario: " + RUNS_PER_SCENARIO);
        System.out.println("=".repeat(60));

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String rawFile = "results/weight_sensitivity_raw_" + timestamp + ".csv";

        new File("results").mkdirs();

        try (PrintWriter writer = new PrintWriter(new FileWriter(rawFile))) {
            writer.println("WeightConfig,Algorithm,TaskCount,Run,Seed,Makespan,Cost,Energy,LoadBalanceIndex,ImbalanceDegree");

            int totalExperiments = WEIGHT_CONFIGS.length * ALGORITHMS.length * TASK_COUNTS.length * RUNS_PER_SCENARIO;
            int completed = 0;

            for (int wi = 0; wi < WEIGHT_CONFIGS.length; wi++) {
                double[] weights = WEIGHT_CONFIGS[wi];
                String wName = WEIGHT_NAMES[wi];
                System.out.println("\n>>> Weight Config " + wName +
                    ": w_ms=" + weights[0] + " w_en=" + weights[1] +
                    " w_cost=" + weights[2] + " w_lb=" + weights[3] + " <<<");

                for (int M : TASK_COUNTS) {
                    for (int run = 1; run <= RUNS_PER_SCENARIO; run++) {
                        long seed = BASE_SEED + run + wi * 1000;

                        for (String algo : ALGORITHMS) {
                            try {
                                double[] metrics = runSingleExperiment(algo, M, seed, weights);
                                writer.printf("%s,%s,%d,%d,%d,%.6f,%.6f,%.6f,%.6f,%.6f%n",
                                    wName, algo, M, run, seed,
                                    metrics[0], metrics[1], metrics[2], metrics[3], metrics[4]);
                                writer.flush();

                                completed++;
                                if (completed % 100 == 0) {
                                    System.out.printf("Progress: %d/%d (%.1f%%)%n",
                                        completed, totalExperiments, 100.0 * completed / totalExperiments);
                                }
                            } catch (Exception e) {
                                System.err.println("Error: " + wName + " " + algo +
                                    " M=" + M + " run=" + run + ": " + e.getMessage());
                            }
                            System.gc();
                        }
                    }
                }
            }

            System.out.println("\n" + "=".repeat(60));
            System.out.println("Results saved to: " + rawFile);
            System.out.println("Experiment Completed!");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static double[] runSingleExperiment(String algo, int M, long seed, double[] weights) {
        CloudSimPlus simulation = new CloudSimPlus();
        Datacenter dc = createDatacenter(simulation, VM_COUNT, seed);
        List<Vm> vmList = createHeterogeneousVms(VM_COUNT, seed);
        List<Cloudlet> cloudletList = createCloudlets(M, seed);

        DatacenterBrokerSimple broker = createWeightedBroker(simulation, algo, seed, weights);
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

    /** 创建带权重配置的 Broker */
    private static DatacenterBrokerSimple createWeightedBroker(
            CloudSimPlus simulation, String algo, long seed, double[] weights) {
        switch (algo) {
            case "FCFS": return new FCFS_Broker(simulation, seed);
            case "RoundRobin": return new RoundRobin_Broker(simulation, seed);
            case "Random": return new Random_Broker(simulation, seed);
            case "MinMin": return new MinMin_Broker(simulation, seed);
            case "MaxMin": return new MaxMin_Broker(simulation, seed);
            case "LSCBO": return new LSCBO_Broker_Weighted(simulation, seed, weights);
            case "CBO": return new CBO_Broker_Weighted(simulation, seed, weights);
            case "PSO": return new PSO_Broker_Weighted(simulation, seed, weights);
            case "AOA": return new AOA_Broker_Weighted(simulation, seed, weights);
            case "WOA": return new WOA_Broker_Weighted(simulation, seed, weights);
            case "GWO": return new GWO_Broker_Weighted(simulation, seed, weights);
            case "GTO": return new GTO_Broker_Weighted(simulation, seed, weights);
            case "HHO": return new HHO_Broker_Weighted(simulation, seed, weights);
            default: throw new IllegalArgumentException("Unknown: " + algo);
        }
    }

    // Delegate to JournalExperiment pattern
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

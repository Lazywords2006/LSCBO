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
 * VMHeterogeneityExperiment - VM 异质性鲁棒性实验
 *
 * 验证 LSCBO 在不同 VM 异构配置下的排名稳定性。
 * 测试 3 种 VM 配置: 当前 (4:1), 高异质 (20:1), 低异质 (1.5:1)。
 *
 * 审稿关系: R1 W4
 */
public class VMHeterogeneityExperiment {

    private static final String[] ALGORITHMS = {
        "FCFS", "RoundRobin", "Random",
        "MinMin", "MaxMin",
        "LSCBO", "CBO", "PSO", "AOA", "WOA", "GWO", "GTO", "HHO"
    };

    private static final int[] TASK_COUNTS = {100, 300, 500, 800, 1000, 2000};
    private static final int RUNS_PER_SCENARIO = 30;
    private static final int VM_COUNT = 50;
    private static final int TASK_LENGTH_MIN = 500;
    private static final int TASK_LENGTH_MAX = 2500;
    private static final long BASE_SEED = 42;

    /** VM 配置: {minMIPS, maxMIPS, ratio_description} */
    private static final int[][] VM_CONFIGS = {
        {250, 1000},   // Config A: 当前 (4:1)
        {100, 2000},   // Config B: 高异质 (20:1)
        {400, 600},    // Config C: 低异质 (1.5:1)
    };
    private static final String[] VM_CONFIG_NAMES = {"A_4to1", "B_20to1", "C_1.5to1"};

    public static void main(String[] args) {
        System.out.println("=".repeat(60));
        System.out.println("   VM Heterogeneity Robustness Experiment");
        System.out.println("   Algorithms: " + ALGORITHMS.length);
        System.out.println("   VM Configs: " + VM_CONFIGS.length);
        System.out.println("   Task Scales: " + Arrays.toString(TASK_COUNTS));
        System.out.println("   Runs/Scenario: " + RUNS_PER_SCENARIO);
        System.out.println("=".repeat(60));

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String rawFile = "results/vm_heterogeneity_" + timestamp + ".csv";

        new File("results").mkdirs();

        try (PrintWriter writer = new PrintWriter(new FileWriter(rawFile))) {
            writer.println("VMConfig,Algorithm,TaskCount,Run,Seed,Makespan,Cost,Energy,LoadBalanceIndex,ImbalanceDegree");

            int totalExperiments = VM_CONFIGS.length * ALGORITHMS.length *
                                   TASK_COUNTS.length * RUNS_PER_SCENARIO;
            int completed = 0;

            for (int vi = 0; vi < VM_CONFIGS.length; vi++) {
                int[] vmc = VM_CONFIGS[vi];
                String vName = VM_CONFIG_NAMES[vi];
                System.out.println("\n>>> VM Config " + vName +
                    ": MIPS ~ U(" + vmc[0] + ", " + vmc[1] + ") <<<");

                for (int M : TASK_COUNTS) {
                    for (int run = 1; run <= RUNS_PER_SCENARIO; run++) {
                        long seed = BASE_SEED + run + vi * 1000;

                        for (String algo : ALGORITHMS) {
                            try {
                                double[] metrics = runSingleExperiment(algo, M, seed, vmc);
                                writer.printf("%s,%s,%d,%d,%d,%.6f,%.6f,%.6f,%.6f,%.6f%n",
                                    vName, algo, M, run, seed,
                                    metrics[0], metrics[1], metrics[2], metrics[3], metrics[4]);
                                writer.flush();

                                completed++;
                                if (completed % 100 == 0) {
                                    System.out.printf("Progress: %d/%d (%.1f%%)%n",
                                        completed, totalExperiments, 100.0 * completed / totalExperiments);
                                }
                            } catch (Exception e) {
                                System.err.println("Error: " + vName + " " + algo +
                                    " M=" + M + " run=" + run + ": " + e.getMessage());
                            }
                            System.gc();
                        }
                    }
                }
            }

            System.out.println("\nResults saved to: " + rawFile);
            System.out.println("Experiment Completed!");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static double[] runSingleExperiment(String algo, int M, long seed, int[] vmConfig) {
        CloudSimPlus simulation = new CloudSimPlus();
        Datacenter dc = createDatacenter(simulation, VM_COUNT, seed);
        List<Vm> vmList = createVmsWithConfig(VM_COUNT, seed, vmConfig);
        List<Cloudlet> cloudletList = createCloudlets(M, seed);

        DatacenterBrokerSimple broker = createBroker(simulation, algo, seed);
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

    /** 使用指定 MIPS 范围创建 VM */
    private static List<Vm> createVmsWithConfig(int count, long seed, int[] vmConfig) {
        List<Vm> list = new ArrayList<>();
        Random r = new Random(seed);
        int minMips = vmConfig[0];
        int maxMips = vmConfig[1];
        for (int i = 0; i < count; i++) {
            long mips = minMips + r.nextInt(maxMips - minMips + 1);
            list.add(new VmSimple(i, mips, 1)
                .setRam(512 + r.nextInt(512)).setBw(1000 + r.nextInt(1000)).setSize(10000));
        }
        return list;
    }

    private static DatacenterBrokerSimple createBroker(CloudSimPlus sim, String algo, long seed) {
        switch (algo) {
            case "FCFS": return new FCFS_Broker(sim, seed);
            case "RoundRobin": return new RoundRobin_Broker(sim, seed);
            case "Random": return new Random_Broker(sim, seed);
            case "MinMin": return new MinMin_Broker(sim, seed);
            case "MaxMin": return new MaxMin_Broker(sim, seed);
            case "LSCBO": return new LSCBO_Broker_Fixed(sim, seed, "journal");
            case "CBO": return new CBO_Broker(sim, seed);
            case "PSO": return new PSO_Broker(sim, seed);
            case "AOA": return new AOA_Broker(sim, seed, "journal");
            case "WOA": return new WOA_Broker(sim, seed);
            case "GWO": return new GWO_Broker(sim, seed);
            case "GTO": return new GTO_Broker(sim, seed);
            case "HHO": return new HHO_Broker(sim, seed);
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

package com.edcbo.research;

import com.edcbo.research.utils.CostCalculator;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.TreeMap;

/**
 * 快速参数调优验证测试
 * M=100, 快速迭代验证 LSCBO 参数
 */
public class QuickTuningTest {

    private static final int VM_NUM = 50;
    private static final int TASK_NUM = 100; // 快速验证
    private static final long SEED = 42;

    private static final int VM_MIPS_MIN = 1000;
    private static final int VM_MIPS_MAX = 5000;
    private static final int TASK_LENGTH_MIN = 10000;
    private static final int TASK_LENGTH_MAX = 50000;

    // 对比算法列表
    private static final String[] ALGORITHMS = { "LSCBO", "WOA", "AOA", "PSO", "GWO", "CBO" };

    public static void main(String[] args) {
        System.out.println("==========================================");
        System.out.println("   Quick Tuning Test (M=100)");
        System.out.println("==========================================\n");

        TreeMap<Double, String> results = new TreeMap<>();

        for (String algo : ALGORITHMS) {
            double fitness = runExperiment(algo);
            results.put(fitness, algo);
            System.out.println(String.format("%-15s : %.4f", algo, fitness));
        }

        System.out.println("\n========== 排名 (Fitness 越低越好) ==========");
        int rank = 1;
        for (var entry : results.entrySet()) {
            String medal = rank == 1 ? "🥇" : (rank == 2 ? "🥈" : (rank == 3 ? "🥉" : "  "));
            System.out.println(String.format("%s %d. %-15s : %.4f", medal, rank++, entry.getValue(), entry.getKey()));
        }

        System.out.println("\n==========================================");
        System.out.println("   Test Complete");
        System.out.println("==========================================");
    }

    private static double runExperiment(String algo) {
        CloudSimPlus simulation = new CloudSimPlus();
        Datacenter datacenter = createDatacenter(simulation);
        DatacenterBroker broker = createBroker(simulation, algo);

        List<Vm> vmList = createVms();
        broker.submitVmList(vmList);

        List<Cloudlet> cloudletList = createCloudlets();
        broker.submitCloudletList(cloudletList);

        simulation.start();

        // 计算最终成本
        List<Cloudlet> finished = broker.getCloudletFinishedList();
        return calculateFitness(finished, TASK_NUM, VM_NUM, vmList);
    }

    private static DatacenterBroker createBroker(CloudSimPlus simulation, String algo) {
        switch (algo) {
            case "LSCBO":
                return new LSCBO_Broker_Fixed(simulation, SEED, "M" + TASK_NUM);
            case "WOA":
                return new WOA_Broker(simulation, SEED);
            case "AOA":
                return new AOA_Broker(simulation, SEED, "M" + TASK_NUM);
            case "PSO":
                return new PSO_Broker(simulation, SEED);
            case "GWO":
                return new GWO_Broker(simulation, SEED);
            case "CBO":
                return new CBO_Broker(simulation, SEED);
            default:
                throw new IllegalArgumentException("Unknown: " + algo);
        }
    }

    private static double calculateFitness(List<Cloudlet> finished, int M, int N, List<Vm> vmList) {
        double[] vmRuntimes = new double[N];
        double totalRuntime = 0;

        for (Cloudlet c : finished) {
            int vmId = (int) c.getVm().getId();
            double time = c.getActualCpuTime();
            if (vmId < N) {
                vmRuntimes[vmId] += time;
                totalRuntime += time;
            }
        }

        // Makespan
        double makespan = 0;
        for (double t : vmRuntimes)
            makespan = Math.max(makespan, t);

        // Load (StdDev)
        double avg = totalRuntime / N;
        double var = 0;
        for (double t : vmRuntimes)
            var += Math.pow(t - avg, 2);
        double stdDev = Math.sqrt(var / N);

        // Price
        double price = 8.0 * totalRuntime;

        // Total Cost = 0.5*Time + 0.25*Load + 0.25*Price
        return 0.5 * makespan + 0.25 * stdDev + 0.25 * price;
    }

    private static Datacenter createDatacenter(CloudSimPlus simulation) {
        List<Host> hostList = new ArrayList<>();
        for (int i = 0; i < VM_NUM; i++) {
            List<Pe> peList = new ArrayList<>();
            for (int p = 0; p < 4; p++)
                peList.add(new PeSimple(10000));
            hostList.add(new HostSimple(100000, 100000, 100000, peList));
        }
        return new DatacenterSimple(simulation, hostList, new VmAllocationPolicySimple());
    }

    private static List<Vm> createVms() {
        List<Vm> list = new ArrayList<>();
        Random r = new Random(SEED);
        for (int i = 0; i < VM_NUM; i++) {
            long mips = VM_MIPS_MIN + r.nextInt(VM_MIPS_MAX - VM_MIPS_MIN);
            long ram = 100 + r.nextInt(401);
            long bw = 100 + r.nextInt(151);
            list.add(new VmSimple(i, mips, 1).setRam(ram).setBw(bw).setSize(10000));
        }
        return list;
    }

    private static List<Cloudlet> createCloudlets() {
        List<Cloudlet> list = new ArrayList<>();
        Random r = new Random(SEED + 1);
        for (int i = 0; i < TASK_NUM; i++) {
            long len = TASK_LENGTH_MIN + r.nextInt(TASK_LENGTH_MAX - TASK_LENGTH_MIN);
            list.add(new CloudletSimple(i, len, 1)
                    .setFileSize(300)
                    .setOutputSize(300)
                    .setUtilizationModel(new UtilizationModelFull()));
        }
        return list;
    }
}

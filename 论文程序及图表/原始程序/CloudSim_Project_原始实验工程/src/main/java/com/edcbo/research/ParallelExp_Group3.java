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
import org.cloudsimplus.schedulers.cloudlet.CloudletSchedulerSpaceShared;
import org.cloudsimplus.util.Log;
import ch.qos.logback.classic.Level;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 并行实验组3：其他元启发式算法
 * AOA, GWO
 */
public class ParallelExp_Group3 {

    private static final String[] ALGORITHMS = {
            "AOA", "GWO"
    };

    private static final int[] TASK_COUNTS = { 100, 300, 500, 800, 1000 };
    private static final int RUNS_PER_SCENARIO = 30;
    private static final int VM_COUNT = 50;
    private static final int[] VM_MIPS_OPTIONS = { 250, 500, 750, 1000 };
    private static final int TASK_LENGTH_MIN = 500;
    private static final int TASK_LENGTH_MAX = 2500;
    private static final long BASE_SEED = 42;

    public static void main(String[] args) {
        Log.setLevel(Level.OFF); // 禁用日志提升性能
        System.out.println("=== 并行实验组3: AOA/GWO ===");
        System.out.println("算法: " + Arrays.toString(ALGORITHMS));

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String rawFile = "results/parallel_group3_" + timestamp + ".csv";
        new File("results").mkdirs();

        try (PrintWriter writer = new PrintWriter(new FileWriter(rawFile))) {
            writer.println("Algorithm,TaskCount,Run,Seed,Makespan,Cost,Energy,LoadBalanceIndex,ImbalanceDegree");

            int total = ALGORITHMS.length * TASK_COUNTS.length * RUNS_PER_SCENARIO;
            int done = 0;

            for (int M : TASK_COUNTS) {
                for (int run = 1; run <= RUNS_PER_SCENARIO; run++) {
                    long seed = BASE_SEED + run;
                    for (String algo : ALGORITHMS) {
                        try {
                            double[] metrics = runExperiment(algo, M, seed);
                            writer.printf("%s,%d,%d,%d,%.6f,%.6f,%.6f,%.6f,%.6f%n",
                                    algo, M, run, seed, metrics[0], metrics[1], metrics[2], metrics[3], metrics[4]);
                            writer.flush();
                            done++;
                            if (done % 30 == 0)
                                System.out.printf("组3进度: %d/%d (%.1f%%)%n", done, total, 100.0 * done / total);
                        } catch (Exception e) {
                            System.err.println("Error: " + algo + " M=" + M + " run=" + run);
                        }
                        System.gc();
                    }
                }
            }
            System.out.println("组3完成! 结果: " + rawFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static double[] runExperiment(String algo, int M, long seed) {
        CloudSimPlus sim = new CloudSimPlus();
        Datacenter dc = createDatacenter(sim, VM_COUNT, seed);
        List<Vm> vms = createVms(VM_COUNT, seed);
        List<Cloudlet> cls = createCloudlets(M, seed);
        DatacenterBrokerSimple broker = createBroker(sim, algo, seed);
        broker.submitVmList(vms);
        broker.submitCloudletList(cls);
        sim.start();
        int[] schedule = extractSchedule(cls, vms);
        double makespan = getMakespan(broker, algo);
        double cost = CostCalculator.calculateCost(schedule, M, VM_COUNT, cls, vms);
        double energy = EnergyCalculator.calculateEnergy(schedule, M, VM_COUNT, cls, vms);
        double lbi = LoadBalanceCalculator.calculateLoadBalanceIndex(schedule, M, VM_COUNT, cls, vms);
        double imb = LoadBalanceCalculator.calculateImbalanceDegree(schedule, M, VM_COUNT, cls, vms);
        return new double[] { makespan, cost, energy, lbi, imb };
    }

    private static Datacenter createDatacenter(CloudSimPlus sim, int vmNum, long seed) {
        List<Host> hosts = new ArrayList<>();
        for (int i = 0; i < vmNum; i++) {
            List<Pe> pes = new ArrayList<>();
            for (int p = 0; p < 4; p++)
                pes.add(new PeSimple(10000));
            hosts.add(new HostSimple(100000, 100000, 100000, pes));
        }
        return new DatacenterSimple(sim, hosts, new VmAllocationPolicySimple());
    }

    private static List<Vm> createVms(int count, long seed) {
        List<Vm> list = new ArrayList<>();
        Random r = new Random(seed);
        for (int i = 0; i < count; i++) {
            long mips = VM_MIPS_OPTIONS[r.nextInt(VM_MIPS_OPTIONS.length)];
            list.add(
                    new VmSimple(i, mips, 1)
                            .setRam(512 + r.nextInt(512))
                            .setBw(1000 + r.nextInt(1000))
                            .setSize(10000)
                            .setCloudletScheduler(new CloudletSchedulerSpaceShared()));
        }
        return list;
    }

    private static List<Cloudlet> createCloudlets(int count, long seed) {
        List<Cloudlet> list = new ArrayList<>();
        Random r = new Random(seed);
        for (int i = 0; i < count; i++) {
            long len = TASK_LENGTH_MIN + r.nextInt(TASK_LENGTH_MAX - TASK_LENGTH_MIN);
            list.add(new CloudletSimple(i, len, 1).setFileSize(1024).setOutputSize(1024)
                    .setUtilizationModel(new UtilizationModelFull()));
        }
        return list;
    }

    private static DatacenterBrokerSimple createBroker(CloudSimPlus sim, String algo, long seed) {
        switch (algo) {
            case "AOA":
                return new AOA_Broker(sim, seed, "journal");
            case "GWO":
                return new GWO_Broker(sim, seed);
            default:
                throw new IllegalArgumentException("Unknown: " + algo);
        }
    }

    private static int[] extractSchedule(List<Cloudlet> cls, List<Vm> vms) {
        int[] s = new int[cls.size()];
        Map<Long, Integer> vmMap = new HashMap<>();
        for (int i = 0; i < vms.size(); i++)
            vmMap.put(vms.get(i).getId(), i);
        for (int i = 0; i < cls.size(); i++)
            s[i] = vmMap.getOrDefault(cls.get(i).getVm().getId(), 0);
        return s;
    }

    private static double getMakespan(DatacenterBrokerSimple broker, String algo) {
        // 始终使用CloudSim仿真的实际完成时间
        double max = 0;
        for (Cloudlet c : broker.getCloudletFinishedList()) {
            max = Math.max(max, c.getFinishTime());
        }
        return max;
    }
}

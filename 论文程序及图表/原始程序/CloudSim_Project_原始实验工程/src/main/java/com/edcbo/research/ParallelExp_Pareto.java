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

public class ParallelExp_Pareto {
    private static final String[] ALGORITHMS = {"LSCBO", "CBO", "PSO", "AOA", "GWO"};
    private static final int[] TASK_COUNTS = {100, 300, 500, 800, 1000, 1500, 2000};
    private static final int RUNS_PER_SCENARIO = 30;
    private static final int VM_COUNT = 50;
    private static final int[] VM_MIPS_OPTIONS = {250, 500, 750, 1000};
    private static final int TASK_LENGTH_MIN = 500;
    private static final int TASK_LENGTH_MAX = 2500;
    private static final long BASE_SEED = 42;

    public static void main(String[] args) {
        Log.setLevel(Level.OFF);
        System.out.println("=== Pareto Experiment ===");
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String rawFile = "results/pareto_experiment_" + timestamp + ".csv";
        new File("results").mkdirs();
        try (PrintWriter w = new PrintWriter(new FileWriter(rawFile))) {
            w.println("Algorithm,TaskCount,Run,Seed,Makespan,Cost,Energy,LoadBalanceIndex,ImbalanceDegree");
            int total = ALGORITHMS.length * TASK_COUNTS.length * RUNS_PER_SCENARIO;
            int done = 0;
            for (int M : TASK_COUNTS) {
                for (int run = 1; run <= RUNS_PER_SCENARIO; run++) {
                    long seed = BASE_SEED + run;
                    for (String algo : ALGORITHMS) {
                        try {
                            double[] m = runExp(algo, M, seed);
                            w.printf("%s,%d,%d,%d,%.6f,%.6f,%.6f,%.6f,%.6f%n", algo, M, run, seed, m[0], m[1], m[2], m[3], m[4]);
                            w.flush();
                            done++;
                            if (done % 20 == 0) System.out.printf("Progress: %d/%d (%.1f%%)%n", done, total, 100.0*done/total);
                        } catch (Exception e) { System.err.println("Error: " + e.getMessage()); }
                        System.gc();
                    }
                }
            }
            System.out.println("Done: " + rawFile);
        } catch (IOException e) { e.printStackTrace(); }
    }

    private static double[] runExp(String algo, int M, long seed) {
        CloudSimPlus sim = new CloudSimPlus();
        createDC(sim, VM_COUNT, seed);
        List<Vm> vms = createVms(VM_COUNT, seed);
        List<Cloudlet> cls = createCls(M, seed);
        DatacenterBrokerSimple b = createBroker(sim, algo, seed);
        b.submitVmList(vms);
        b.submitCloudletList(cls);
        sim.start();
        int[] sch = extractSch(cls, vms);
        double mk = 0;
        for (Cloudlet c : b.getCloudletFinishedList()) mk = Math.max(mk, c.getFinishTime());
        return new double[]{mk, CostCalculator.calculateCost(sch, M, VM_COUNT, cls, vms),
            EnergyCalculator.calculateEnergy(sch, M, VM_COUNT, cls, vms),
            LoadBalanceCalculator.calculateLoadBalanceIndex(sch, M, VM_COUNT, cls, vms),
            LoadBalanceCalculator.calculateImbalanceDegree(sch, M, VM_COUNT, cls, vms)};
    }

    private static void createDC(CloudSimPlus sim, int n, long seed) {
        List<Host> h = new ArrayList<>();
        for (int i = 0; i < n; i++) { List<Pe> p = new ArrayList<>(); for (int j = 0; j < 4; j++) p.add(new PeSimple(10000)); h.add(new HostSimple(100000,100000,100000,p)); }
        new DatacenterSimple(sim, h, new VmAllocationPolicySimple());
    }

    private static List<Vm> createVms(int n, long seed) {
        List<Vm> l = new ArrayList<>(); Random r = new Random(seed);
        for (int i = 0; i < n; i++) { long m = VM_MIPS_OPTIONS[r.nextInt(VM_MIPS_OPTIONS.length)]; l.add(new VmSimple(i,m,1).setRam(512+r.nextInt(512)).setBw(1000+r.nextInt(1000)).setSize(10000).setCloudletScheduler(new CloudletSchedulerSpaceShared())); }
        return l;
    }

    private static List<Cloudlet> createCls(int n, long seed) {
        List<Cloudlet> l = new ArrayList<>(); Random r = new Random(seed);
        for (int i = 0; i < n; i++) { long len = TASK_LENGTH_MIN + r.nextInt(TASK_LENGTH_MAX-TASK_LENGTH_MIN); l.add(new CloudletSimple(i,len,1).setFileSize(1024).setOutputSize(1024).setUtilizationModel(new UtilizationModelFull())); }
        return l;
    }

    private static DatacenterBrokerSimple createBroker(CloudSimPlus sim, String algo, long seed) {
        switch (algo) {
            case "LSCBO": return new LSCBO_Broker_Pareto(sim, seed);
            case "CBO": return new CBO_Broker(sim, seed);
            case "PSO": return new PSO_Broker(sim, seed);
            case "AOA": return new AOA_Broker(sim, seed);
            case "GWO": return new GWO_Broker(sim, seed);
            default: throw new IllegalArgumentException("Unknown: " + algo);
        }
    }

    private static int[] extractSch(List<Cloudlet> cls, List<Vm> vms) {
        int[] s = new int[cls.size()]; Map<Long,Integer> m = new HashMap<>();
        for (int i = 0; i < vms.size(); i++) m.put(vms.get(i).getId(), i);
        for (int i = 0; i < cls.size(); i++) s[i] = m.getOrDefault(cls.get(i).getVm().getId(), 0);
        return s;
    }
}
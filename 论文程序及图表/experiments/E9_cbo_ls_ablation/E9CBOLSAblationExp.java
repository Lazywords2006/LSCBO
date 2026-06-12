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
import java.util.*;

/**
 * E9: CBO+LS Ablation Experiment.
 * 
 * Tests CBO + task-migration local search (without Levy flight, without staging, 
 * fixed w=0.5 attack weight). Compares against pure CBO to quantify LS-only contribution.
 * 
 * Args: [scale] [runLo] [runHi]
 *   scale: task count (200/500/1000/2000/5000, -1 = all)
 *   runLo/runHi: run range (1-30)
 * 
 * Output: results/E9_cbo_ls/E9_<tag>.csv
 */
public class E9CBOLSAblationExp {

    private static final String[] ALGORITHMS = {"CBO", "CBO_LS"};
    private static final int[] TASK_COUNTS = {200, 500, 1000, 2000, 5000};
    private static final int RUNS = 30;
    private static final int VM_COUNT = 50;
    // MIPS ~ U(500, 2000) - 4:1 ratio consistent with paper
    private static final int[] VM_MIPS = {500, 750, 1000, 1250, 1500, 1750, 2000};
    private static final int LEN_MIN = 1000, LEN_MAX = 20000;
    private static final long BASE_SEED = 42;

    public static void main(String[] args) {
        int scaleFilter = -1, runLo = 1, runHi = RUNS;
        if (args.length >= 1) scaleFilter = Integer.parseInt(args[0]);
        if (args.length >= 3) { runLo = Integer.parseInt(args[1]); runHi = Integer.parseInt(args[2]); }
        String tag = (scaleFilter < 0 ? "all" : "M" + scaleFilter) + "_r" + runLo + "-" + runHi;

        new File("results/E9_cbo_ls").mkdirs();
        String rawFile = "results/E9_cbo_ls/E9_" + tag + ".csv";
        try (PrintWriter w = new PrintWriter(new FileWriter(rawFile))) {
            w.println("Algorithm,TaskCount,Run,Seed,Makespan,Cost,Energy,LoadBalanceIndex,ImbalanceDegree");
            int done = 0;
            for (int M : TASK_COUNTS) {
                if (scaleFilter > 0 && M != scaleFilter) continue;
                System.out.println("[E9] Starting scale M=" + M);
                for (int run = runLo; run <= runHi; run++) {
                    long seed = BASE_SEED + run;
                    for (String algo : ALGORITHMS) {
                        try {
                            double[] m = runOne(algo, M, seed);
                            w.printf("%s,%d,%d,%d,%.6f,%.6f,%.6f,%.6f,%.6f%n",
                                algo, M, run, seed, m[0], m[1], m[2], m[3], m[4]);
                            w.flush();
                            done++;
                        } catch (Exception e) {
                            System.err.println("Error " + algo + " M=" + M + " run=" + run + ": " + e.getMessage());
                            e.printStackTrace();
                        }
                        System.gc();
                    }
                }
            }
            System.out.println("[E9 " + tag + "] done, " + done + " trials -> " + rawFile);
        } catch (IOException e) { e.printStackTrace(); }
    }

    private static double[] runOne(String algo, int M, long seed) {
        CloudSimPlus sim = new CloudSimPlus();
        createDatacenter(sim, VM_COUNT);
        List<Vm> vms = createVms(VM_COUNT, seed);
        List<Cloudlet> cls = createCloudlets(M, seed);

        // CBO: original tanh-based CBO (Phase 1: tanh searching, Phase 2: rotation, Phase 3: fixed w=0.5 attack)
        // CBO_LS: same CBO + task-migration local search every 10 iterations
        CBO_Broker broker = algo.equals("CBO_LS")
            ? new CBOLS_Broker(sim, seed)
            : new CBO_Broker(sim, seed);

        broker.submitVmList(vms);
        broker.submitCloudletList(cls);
        sim.addOnClockTickListener(info -> { if (info.getTime() > 0.0) sim.terminate(); });
        sim.start();

        int[] sch = extractSchedule(cls, vms);
        double mk = CostCalculator.calculateWeightedCostDetails(sch, M, VM_COUNT, cls, vms).time;
        double cost = CostCalculator.calculateCost(sch, M, VM_COUNT, cls, vms);
        double en = EnergyCalculator.calculateEnergy(sch, M, VM_COUNT, cls, vms);
        double lbi = LoadBalanceCalculator.calculateLoadBalanceIndex(sch, M, VM_COUNT, cls, vms);
        double imb = LoadBalanceCalculator.calculateImbalanceDegree(sch, M, VM_COUNT, cls, vms);
        return new double[]{mk, cost, en, lbi, imb};
    }

    // ===== Infrastructure (same as E8MinMinExp) =====

    private static Datacenter createDatacenter(CloudSimPlus sim, int vmNum) {
        List<Host> hosts = new ArrayList<>();
        for (int i = 0; i < vmNum; i++) {
            List<Pe> pes = new ArrayList<>();
            for (int p = 0; p < 4; p++) pes.add(new PeSimple(10000));
            hosts.add(new HostSimple(100000, 100000, 100000, pes));
        }
        return new DatacenterSimple(sim, hosts, new VmAllocationPolicySimple());
    }

    private static List<Vm> createVms(int count, long seed) {
        List<Vm> list = new ArrayList<>();
        Random r = new Random(seed);
        for (int i = 0; i < count; i++) {
            long mips = VM_MIPS[r.nextInt(VM_MIPS.length)];
            r.nextInt(512); r.nextInt(1000);
            list.add(new VmSimple(i, mips, 1)
                .setRam(65536).setBw(100000).setSize(10000));
        }
        return list;
    }

    private static List<Cloudlet> createCloudlets(int count, long seed) {
        List<Cloudlet> list = new ArrayList<>();
        Random r = new Random(seed);
        for (int i = 0; i < count; i++) {
            long len = LEN_MIN + r.nextInt(LEN_MAX - LEN_MIN);
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
}

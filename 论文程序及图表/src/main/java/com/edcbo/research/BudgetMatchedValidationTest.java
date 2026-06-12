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

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * E2: Budget-Matched Validation Test
 *
 * Purpose  : Fair comparison — all algorithms use EQUAL evaluation budget
 *            LSCBO: pop=20, iter=20 (400 evaluations)
 *            CBO:   pop=20, iter=20 (400 evaluations)
 *            WOA:   pop=20, iter=20 (400 evaluations)  <- matched to LSCBO/CBO
 * Scales   : N=500, N=1000, N=2000 (M=50 VMs each)
 * Seeds    : 30 independent seeds (43–72)
 */
public class BudgetMatchedValidationTest {

    private static final int    VM_COUNT         = 50;
    private static final int    VM_MIPS_MIN      = 500;
    private static final int    VM_MIPS_MAX      = 2000;
    private static final int    CLOUDLET_LEN_MIN = 1000;
    private static final int    CLOUDLET_LEN_MAX = 20000;
    private static final int[]  TASK_COUNTS      = {500, 1000, 2000};
    private static final long[] SEEDS            = {
        43, 44, 45, 46, 47, 48, 49, 50, 51, 52,
        53, 54, 55, 56, 57, 58, 59, 60, 61, 62,
        63, 64, 65, 66, 67, 68, 69, 70, 71, 72
    };

    public static void main(String[] args) throws Exception {
        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger)
            org.slf4j.LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
        root.setLevel(ch.qos.logback.classic.Level.ERROR);

        String ts      = java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String outDir  = "results";
        String outFile = outDir + "/BudgetMatched_" + ts + ".csv";
        new java.io.File(outDir).mkdirs();

        int total = TASK_COUNTS.length * SEEDS.length * 3;
        int done  = 0;
        System.out.println("=== BudgetMatchedValidationTest (E2) ===");
        System.out.printf("Scales=%s  seeds=%d  algos=3  total=%d%n",
            Arrays.toString(TASK_COUNTS), SEEDS.length, total);
        System.out.println("Budget: all algorithms use pop=20, iter=20");
        System.out.println("Output: " + outFile);

        try (PrintWriter pw = new PrintWriter(new FileWriter(outFile))) {
            pw.println("Algorithm,TaskCount,Seed,Makespan,LoadBalanceRatio");

            for (int n : TASK_COUNTS) {
                for (long seed : SEEDS) {
                    for (String algo : new String[]{"LSCBO", "CBO", "WOA"}) {
                        double[] res = runSingle(algo, n, seed);
                        pw.printf("%s,%d,%d,%.4f,%.4f%n",
                            algo, n, seed, res[0], res[1]);
                        pw.flush();
                        done++;
                        System.out.printf("[%3d/%d] N=%-5d %s seed=%d  makespan=%.2f%n",
                            done, total, n, algo, seed, res[0]);
                        System.gc();
                    }
                }
            }
        }
        System.out.println("Done. Results saved to " + outFile);
    }

    private static double[] runSingle(String algo, int N, long seed) {
        CloudSimPlus sim = new CloudSimPlus();

        List<Host> hosts = new ArrayList<>();
        for (int i = 0; i < VM_COUNT; i++) {
            List<Pe> pes = new ArrayList<>();
            for (int p = 0; p < 8; p++) pes.add(new PeSimple(10000));
            hosts.add(new HostSimple(200_000L, 200_000L, 200_000L, pes));
        }
        new DatacenterSimple(sim, hosts, new VmAllocationPolicySimple());

        Random rngVm = new Random(seed);
        List<Vm> vms = new ArrayList<>();
        for (int i = 0; i < VM_COUNT; i++) {
            long mips = VM_MIPS_MIN + (long)(rngVm.nextDouble() * (VM_MIPS_MAX - VM_MIPS_MIN));
            vms.add(new VmSimple(i, mips, 2).setRam(2048).setBw(1000).setSize(10_000));
        }

        Random rngCl = new Random(seed + 1_000_000L);
        List<Cloudlet> cloudlets = new ArrayList<>();
        for (int i = 0; i < N; i++) {
            long len = CLOUDLET_LEN_MIN + (long)(rngCl.nextDouble() * (CLOUDLET_LEN_MAX - CLOUDLET_LEN_MIN));
            cloudlets.add(new CloudletSimple(i, len, 1)
                .setFileSize(300).setOutputSize(300)
                .setUtilizationModel(new UtilizationModelFull()));
        }

        DatacenterBroker broker;
        switch (algo) {
            case "LSCBO": broker = new LSCBO_Broker_Fixed(sim, seed, "BudgetMatch"); break;
            case "CBO":   broker = new CBO_Broker(sim, seed); break;
            case "WOA":   broker = new WOA_Broker(sim, seed); break;
            default: throw new IllegalArgumentException("Unknown: " + algo);
        }

        broker.submitVmList(vms);
        broker.submitCloudletList(cloudlets);
        sim.start();

        // Makespan via broker's internal optimised value
        double makespan;
        if      (broker instanceof LSCBO_Broker_Fixed) makespan = ((LSCBO_Broker_Fixed) broker).getInternalMakespan();
        else if (broker instanceof CBO_Broker)         makespan = ((CBO_Broker) broker).getInternalMakespan();
        else if (broker instanceof WOA_Broker)         makespan = ((WOA_Broker) broker).getInternalMakespan();
        else makespan = broker.getCloudletFinishedList().stream()
                .mapToDouble(Cloudlet::getFinishTime).max().orElse(0.0);

        // LBR
        double[] vmLoad = new double[VM_COUNT];
        for (Cloudlet c : broker.getCloudletFinishedList()) {
            int vmId = (int) c.getVm().getId();
            if (vmId >= 0 && vmId < VM_COUNT) {
                double t = c.getFinishTime() - c.getExecStartTime();
                if (t > 0) vmLoad[vmId] += t;
            }
        }
        double avg = Arrays.stream(vmLoad).average().orElse(0.0);
        double var = Arrays.stream(vmLoad).map(t -> (t - avg) * (t - avg)).average().orElse(0.0);
        double lbr = (avg > 0) ? Math.sqrt(var) / avg : 0.0;

        return new double[]{makespan, lbr};
    }
}

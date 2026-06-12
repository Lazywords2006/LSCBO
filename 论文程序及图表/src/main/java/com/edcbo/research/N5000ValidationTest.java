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
 * N=5000 Focused Validation with 30 seeds (seeds 43-72).
 *
 * Purpose  : Replace single-seed N=5000 result with proper multi-seed evaluation
 * Config   : LSCBO (pop=20, iter=20), CBO (pop=20, iter=20), WOA (pop=30, iter=50)
 * Decoder  : SPV+MFD (matches main paper experiments)
 * Metrics  : Makespan (seconds), Load Balance Ratio
 *
 * Output   : results/N5000_validation_<timestamp>.csv
 */
public class N5000ValidationTest {

    private static final int    TASK_COUNT        = 5000;
    private static final int    VM_COUNT          = 50;
    private static final int    VM_MIPS_MIN       = 500;
    private static final int    VM_MIPS_MAX       = 2000;
    private static final int    CLOUDLET_LEN_MIN  = 1000;
    private static final int    CLOUDLET_LEN_MAX  = 20000;

    // 30 independent seeds (43–72)
    private static final long[] SEEDS = {
        43, 44, 45, 46, 47, 48, 49, 50, 51, 52,
        53, 54, 55, 56, 57, 58, 59, 60, 61, 62,
        63, 64, 65, 66, 67, 68, 69, 70, 71, 72
    };

    public static void main(String[] args) throws Exception {
        // Suppress CloudSim verbose logs
        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger)
            org.slf4j.LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
        root.setLevel(ch.qos.logback.classic.Level.ERROR);

        String timestamp = java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String outDir  = "results";
        String outFile = outDir + "/N5000_validation_" + timestamp + ".csv";
        new java.io.File(outDir).mkdirs();

        System.out.println("=== N5000ValidationTest ===");
        System.out.println("Tasks=" + TASK_COUNT + ", VMs=" + VM_COUNT
            + ", seeds=" + SEEDS.length + " x 3 algorithms");
        System.out.println("Output: " + outFile);

        int total = SEEDS.length * 3;
        int done  = 0;

        try (PrintWriter pw = new PrintWriter(new FileWriter(outFile))) {
            pw.println("Algorithm,TaskCount,Seed,Makespan,LoadBalanceRatio");

            for (long seed : SEEDS) {
                for (String algo : new String[]{"LSCBO", "CBO", "WOA"}) {
                    double[] result = runSingle(algo, seed);
                    pw.printf("%s,%d,%d,%.4f,%.4f%n",
                        algo, TASK_COUNT, seed, result[0], result[1]);
                    pw.flush();
                    done++;
                    System.out.printf("[%3d/%d] %s seed=%d  makespan=%.2f  lbr=%.4f%n",
                        done, total, algo, seed, result[0], result[1]);
                    System.gc();
                }
            }
        }
        System.out.println("Done. Results saved to " + outFile);
    }

    /** Run one experiment and return [makespan, loadBalanceRatio]. */
    private static double[] runSingle(String algo, long seed) {
        CloudSimPlus sim = new CloudSimPlus();

        // ── Datacenter: 50 hosts, each with 8 PE @ 10000 MIPS ──
        List<Host> hosts = new ArrayList<>();
        for (int i = 0; i < VM_COUNT; i++) {
            List<Pe> pes = new ArrayList<>();
            for (int p = 0; p < 8; p++) pes.add(new PeSimple(10000));
            hosts.add(new HostSimple(200_000L, 200_000L, 200_000L, pes));
        }
        Datacenter dc = new DatacenterSimple(sim, hosts, new VmAllocationPolicySimple());

        // ── VMs (heterogeneous MIPS: 500–2000) ──
        Random rngVm = new Random(seed);
        List<Vm> vms = new ArrayList<>();
        for (int i = 0; i < VM_COUNT; i++) {
            long mips = VM_MIPS_MIN + (long)(rngVm.nextDouble() * (VM_MIPS_MAX - VM_MIPS_MIN));
            vms.add(new VmSimple(i, mips, 2)
                .setRam(2048).setBw(1000).setSize(10_000));
        }

        // ── Cloudlets (heterogeneous MI: 1000–20000) ──
        Random rngCl = new Random(seed + 1_000_000L);
        List<Cloudlet> cloudlets = new ArrayList<>();
        for (int i = 0; i < TASK_COUNT; i++) {
            long len = CLOUDLET_LEN_MIN + (long)(rngCl.nextDouble() * (CLOUDLET_LEN_MAX - CLOUDLET_LEN_MIN));
            cloudlets.add(new CloudletSimple(i, len, 1)
                .setFileSize(300).setOutputSize(300)
                .setUtilizationModel(new UtilizationModelFull()));
        }

        // ── Broker ──
        DatacenterBroker broker;
        switch (algo) {
            case "LSCBO":
                broker = new LSCBO_Broker_Fixed(sim, seed, "N5000");
                break;
            case "CBO":
                broker = new CBO_Broker(sim, seed);
                break;
            case "WOA":
                broker = new WOA_Broker(sim, seed);
                break;
            default:
                throw new IllegalArgumentException("Unknown algorithm: " + algo);
        }

        broker.submitVmList(vms);
        broker.submitCloudletList(cloudlets);
        sim.start();

        // ── Collect metrics via broker's internal result ──
        // Makespan: use broker's optimised value (same metric used during scheduling)
        double makespan;
        if (broker instanceof LSCBO_Broker_Fixed) {
            makespan = ((LSCBO_Broker_Fixed) broker).getInternalMakespan();
        } else if (broker instanceof CBO_Broker) {
            makespan = ((CBO_Broker) broker).getInternalMakespan();
        } else if (broker instanceof WOA_Broker) {
            makespan = ((WOA_Broker) broker).getInternalMakespan();
        } else {
            // Fallback: max finish time across cloudlets
            makespan = broker.getCloudletFinishedList().stream()
                .mapToDouble(Cloudlet::getFinishTime).max().orElse(0.0);
        }

        // LBR: coefficient of variation of per-VM execution load
        List<Cloudlet> finished = broker.getCloudletFinishedList();
        double[] vmLoad = new double[VM_COUNT];
        for (Cloudlet c : finished) {
            int vmId = (int) c.getVm().getId();
            if (vmId >= 0 && vmId < VM_COUNT) {
                double execTime = c.getFinishTime() - c.getExecStartTime();
                if (execTime > 0) vmLoad[vmId] += execTime;
            }
        }
        double avg      = Arrays.stream(vmLoad).average().orElse(0.0);
        double variance = Arrays.stream(vmLoad).map(t -> (t - avg) * (t - avg)).average().orElse(0.0);
        double lbr      = (avg > 0) ? Math.sqrt(variance) / avg : 0.0;

        return new double[]{makespan, lbr};
    }
}

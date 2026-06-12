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
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * 多规模负载均衡验证：与 N5000ValidationTest 完全相同的公平设定
 * （50 VM，MIPS 500-2000，任务 1000-20000 MI，pop30/iter100，相同 fitness/解码），
 * 跨多个任务规模评估 LSCBO/CBO/WOA 的 makespan 与 LBR。
 *
 * 配合已有的 N=5000 数据，构成 1000–5000 的多规模 LBR 证据链。
 * 输出格式与 N5000 一致，便于统一统计分析。
 *
 * 用法：mvn exec:java -Dexec.mainClass="com.edcbo.research.LoadBalanceValidation"
 */
public class LoadBalanceValidation {

    private static final int VM_COUNT = 50;
    private static final int VM_MIPS_MIN = 500;
    private static final int VM_MIPS_MAX = 2000;
    private static final int CLOUDLET_LEN_MIN = 1000;
    private static final int CLOUDLET_LEN_MAX = 20000;

    private static final int[] SCALES = {1000, 2000, 3000};
    private static final long[] SEEDS = {
        43, 44, 45, 46, 47, 48, 49, 50, 51, 52,
        53, 54, 55, 56, 57, 58, 59, 60, 61, 62,
        63, 64, 65, 66, 67, 68, 69, 70, 71, 72
    };
    private static final String[] ALGOS = {"LSCBO", "CBO", "WOA"};

    public static void main(String[] args) throws Exception {
        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger)
                org.slf4j.LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
        root.setLevel(ch.qos.logback.classic.Level.ERROR);

        String ts = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        new java.io.File("results").mkdirs();
        String outFile = "results/loadbalance_multiscale_" + ts + ".csv";

        System.out.println("=== LoadBalanceValidation ===");
        System.out.println("Scales=" + Arrays.toString(SCALES) + ", seeds=" + SEEDS.length
                + ", algos=" + String.join("/", ALGOS));
        System.out.println("Output: " + outFile);

        int total = SCALES.length * SEEDS.length * ALGOS.length;
        int done = 0;

        try (PrintWriter pw = new PrintWriter(new FileWriter(outFile))) {
            pw.println("Algorithm,TaskCount,Seed,Makespan,LoadBalanceRatio");
            for (int M : SCALES) {
                for (long seed : SEEDS) {
                    for (String algo : ALGOS) {
                        double[] r = runSingle(algo, seed, M);
                        pw.printf("%s,%d,%d,%.4f,%.4f%n", algo, M, seed, r[0], r[1]);
                        pw.flush();
                        done++;
                        if (done % 10 == 0 || done == total)
                            System.out.printf("[%4d/%d] M=%d %s seed=%d mk=%.1f lbr=%.4f%n",
                                    done, total, M, algo, seed, r[0], r[1]);
                        System.gc();
                    }
                }
            }
        }
        System.out.println("Done. " + outFile);
    }

    /** @return [makespan, loadBalanceRatio] */
    private static double[] runSingle(String algo, long seed, int taskCount) {
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
            long mips = VM_MIPS_MIN + (long) (rngVm.nextDouble() * (VM_MIPS_MAX - VM_MIPS_MIN));
            vms.add(new VmSimple(i, mips, 2).setRam(2048).setBw(1000).setSize(10_000));
        }

        Random rngCl = new Random(seed + 1_000_000L);
        List<Cloudlet> cloudlets = new ArrayList<>();
        for (int i = 0; i < taskCount; i++) {
            long len = CLOUDLET_LEN_MIN + (long) (rngCl.nextDouble() * (CLOUDLET_LEN_MAX - CLOUDLET_LEN_MIN));
            cloudlets.add(new CloudletSimple(i, len, 1)
                    .setFileSize(300).setOutputSize(300)
                    .setUtilizationModel(new UtilizationModelFull()));
        }

        DatacenterBroker broker;
        switch (algo) {
            case "LSCBO": broker = new LSCBO_Broker_Fixed(sim, seed, "MS" + taskCount); break;
            case "CBO":   broker = new CBO_Broker(sim, seed); break;
            case "WOA":   broker = new WOA_Broker(sim, seed); break;
            default: throw new IllegalArgumentException(algo);
        }

        broker.submitVmList(vms);
        broker.submitCloudletList(cloudlets);
        sim.start();

        double makespan;
        if (broker instanceof LSCBO_Broker_Fixed) makespan = ((LSCBO_Broker_Fixed) broker).getInternalMakespan();
        else if (broker instanceof CBO_Broker) makespan = ((CBO_Broker) broker).getInternalMakespan();
        else if (broker instanceof WOA_Broker) makespan = ((WOA_Broker) broker).getInternalMakespan();
        else makespan = broker.getCloudletFinishedList().stream().mapToDouble(Cloudlet::getFinishTime).max().orElse(0);

        List<Cloudlet> finished = broker.getCloudletFinishedList();
        double[] vmLoad = new double[VM_COUNT];
        for (Cloudlet c : finished) {
            int vmId = (int) c.getVm().getId();
            if (vmId >= 0 && vmId < VM_COUNT) {
                double execTime = c.getFinishTime() - c.getExecStartTime();
                if (execTime > 0) vmLoad[vmId] += execTime;
            }
        }
        double avg = Arrays.stream(vmLoad).average().orElse(0);
        double var = Arrays.stream(vmLoad).map(t -> (t - avg) * (t - avg)).average().orElse(0);
        double lbr = (avg > 0) ? Math.sqrt(var) / avg : 0;

        return new double[]{makespan, lbr};
    }
}

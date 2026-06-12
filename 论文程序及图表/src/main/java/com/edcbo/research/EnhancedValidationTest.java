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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Enhanced 验证测试：对比原版 LSCBO / 增强版（含消融）/ WOA / CBO。
 *
 * 环境与 N5000ValidationTest 完全一致（50 VM，MIPS 500-2000，任务 1000-20000 MI），
 * 仅任务数可调，便于先在小规模快速看趋势，再上 N=5000。
 *
 * 算法：
 *   CBO         : 基线
 *   WOA         : 当前 makespan 最强对手
 *   LSCBO       : 原版（每代三算子串联，早熟）
 *   LSCBO-E     : 增强版，仅算子改进 + LPT 播种（无局部搜索）—— 消融
 *   LSCBO-E+LS  : 增强版全套（+ 局部搜索）
 *
 * 用法：mvn exec:java -Dexec.mainClass="com.edcbo.research.EnhancedValidationTest" -Dexec.args="1000"
 *   args[0] = 任务数（默认 1000）
 */
public class EnhancedValidationTest {

    private static final int VM_COUNT = 50;
    private static final int VM_MIPS_MIN = 500;
    private static final int VM_MIPS_MAX = 2000;
    private static final int CLOUDLET_LEN_MIN = 1000;
    private static final int CLOUDLET_LEN_MAX = 20000;

    private static final long[] SEEDS = {43, 44, 45, 46, 47};
    private static final String[] ALGOS = {"CBO", "WOA", "LSCBO", "LSCBO-E", "LSCBO-E+LS"};

    public static void main(String[] args) {
        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger)
                org.slf4j.LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
        root.setLevel(ch.qos.logback.classic.Level.ERROR);

        int taskCount = args.length > 0 ? Integer.parseInt(args[0]) : 1000;

        System.out.println("=== EnhancedValidationTest ===");
        System.out.printf("Tasks=%d, VMs=%d, seeds=%d%n", taskCount, VM_COUNT, SEEDS.length);
        System.out.println("Algorithms: " + String.join(", ", ALGOS));
        System.out.println();

        // 累计：makespan 和 lbr 之和、makespan 胜场
        double[] sumMk = new double[ALGOS.length];
        double[] sumLbr = new double[ALGOS.length];
        int[] mkWins = new int[ALGOS.length];

        System.out.printf("%-6s", "seed");
        for (String a : ALGOS) System.out.printf(" | %-13s", a);
        System.out.println();

        for (long seed : SEEDS) {
            double[] mk = new double[ALGOS.length];
            double[] lbr = new double[ALGOS.length];
            for (int a = 0; a < ALGOS.length; a++) {
                double[] r = runSingle(ALGOS[a], seed, taskCount);
                mk[a] = r[0];
                lbr[a] = r[1];
                sumMk[a] += r[0];
                sumLbr[a] += r[1];
            }
            int win = 0;
            for (int a = 1; a < ALGOS.length; a++) if (mk[a] < mk[win]) win = a;
            mkWins[win]++;

            System.out.printf("%-6d", seed);
            for (int a = 0; a < ALGOS.length; a++) {
                String mark = (a == win) ? "*" : " ";
                System.out.printf(" | %8.1f%s%3.2f", mk[a], mark, lbr[a]);
            }
            System.out.println();
        }

        System.out.println("\n--- 均值（makespan / lbr）与 makespan 胜场 ---");
        System.out.printf("%-12s %12s %10s %8s%n", "Algorithm", "MeanMakespan", "MeanLBR", "Wins");
        for (int a = 0; a < ALGOS.length; a++) {
            System.out.printf("%-12s %12.2f %10.3f %8d%n",
                    ALGOS[a], sumMk[a] / SEEDS.length, sumLbr[a] / SEEDS.length, mkWins[a]);
        }

        // 关键判定：增强版相对 WOA 的 makespan 改进
        int woa = indexOf("WOA"), e = indexOf("LSCBO-E"), els = indexOf("LSCBO-E+LS");
        double woaMean = sumMk[woa] / SEEDS.length;
        System.out.printf("%n增强版(纯算子) vs WOA makespan: %.2f%%  | 增强版+LS vs WOA: %.2f%%%n",
                (woaMean - sumMk[e] / SEEDS.length) / woaMean * 100,
                (woaMean - sumMk[els] / SEEDS.length) / woaMean * 100);
    }

    private static int indexOf(String a) {
        for (int i = 0; i < ALGOS.length; i++) if (ALGOS[i].equals(a)) return i;
        return -1;
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
        Datacenter dc = new DatacenterSimple(sim, hosts, new VmAllocationPolicySimple());

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
            case "CBO":
                broker = new CBO_Broker(sim, seed);
                break;
            case "WOA":
                broker = new WOA_Broker(sim, seed);
                break;
            case "LSCBO":
                broker = new LSCBO_Broker_Fixed(sim, seed, "valid");
                break;
            case "LSCBO-E": {
                LSCBO_Broker_Enhanced b = new LSCBO_Broker_Enhanced(sim, seed, "valid");
                b.setUseLocalSearch(false);
                broker = b;
                break;
            }
            case "LSCBO-E+LS": {
                LSCBO_Broker_Enhanced b = new LSCBO_Broker_Enhanced(sim, seed, "valid");
                b.setUseLocalSearch(true);
                broker = b;
                break;
            }
            default:
                throw new IllegalArgumentException("Unknown algorithm: " + algo);
        }

        broker.submitVmList(vms);
        broker.submitCloudletList(cloudlets);
        sim.start();

        double makespan;
        if (broker instanceof LSCBO_Broker_Enhanced) {
            makespan = ((LSCBO_Broker_Enhanced) broker).getInternalMakespan();
        } else if (broker instanceof LSCBO_Broker_Fixed) {
            makespan = ((LSCBO_Broker_Fixed) broker).getInternalMakespan();
        } else if (broker instanceof CBO_Broker) {
            makespan = ((CBO_Broker) broker).getInternalMakespan();
        } else if (broker instanceof WOA_Broker) {
            makespan = ((WOA_Broker) broker).getInternalMakespan();
        } else {
            makespan = broker.getCloudletFinishedList().stream()
                    .mapToDouble(Cloudlet::getFinishTime).max().orElse(0.0);
        }

        List<Cloudlet> finished = broker.getCloudletFinishedList();
        double[] vmLoad = new double[VM_COUNT];
        for (Cloudlet c : finished) {
            int vmId = (int) c.getVm().getId();
            if (vmId >= 0 && vmId < VM_COUNT) {
                double execTime = c.getFinishTime() - c.getExecStartTime();
                if (execTime > 0) vmLoad[vmId] += execTime;
            }
        }
        double avg = Arrays.stream(vmLoad).average().orElse(0.0);
        double var = Arrays.stream(vmLoad).map(t -> (t - avg) * (t - avg)).average().orElse(0.0);
        double lbr = (avg > 0) ? Math.sqrt(var) / avg : 0.0;

        return new double[]{makespan, lbr};
    }
}

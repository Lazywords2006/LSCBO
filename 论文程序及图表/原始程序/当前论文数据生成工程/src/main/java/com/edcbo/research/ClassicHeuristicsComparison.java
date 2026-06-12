package com.edcbo.research;

import org.cloudsimplus.allocationpolicies.VmAllocationPolicySimple;
import org.cloudsimplus.brokers.DatacenterBroker;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimple;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.hosts.HostSimple;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.utilizationmodels.UtilizationModelFull;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.*;

/**
 * E0.2A — 经典调度启发式基线对比（P0 阻塞性实验）
 *
 * 审稿意见：CloudSim 对比缺少经典调度启发式（Min-Min, Max-Min, Sufferage 等）。
 *
 * 算法：
 *   Min-Min    : 将任务分配给使其 EFT 最小的 VM
 *   Max-Min    : 将任务中 MCT 最大者优先分配给最优 VM
 *   Sufferage  : 按 sufferage = MCT2 - MCT1 降序分配
 *   Round-Robin: 循环分配
 *   MCT        : Minimum Completion Time（每个任务独立贪心）
 *   LSCBO      : 使用现有 LSCBO_Broker_Fixed（对比用）
 *   CBO        : 使用现有 CBO_Broker（对比用）
 *
 * 输出：E0_cloudsim_heuristics_TIMESTAMP.csv
 *
 * 用法：mvn exec:java -Dexec.mainClass="experiments.e0.ClassicHeuristicsComparison"
 */
public class ClassicHeuristicsComparison {

    static final int VM_COUNT = 20;
    static final int VM_MIPS_MIN = 500, VM_MIPS_MAX = 2000;
    static final int CL_LEN_MIN = 1000, CL_LEN_MAX = 20000;
    static final int[] SCALES = {500, 1000, 2000};
    static final long[] SEEDS = {
        43,44,45,46,47,48,49,50,51,52,
        53,54,55,56,57,58,59,60,61,62,
        63,64,65,66,67,68,69,70,71,72
    };
    static final String[] ALGOS = {"MinMin","MaxMin","Sufferage","RoundRobin","MCT"};

    public static void main(String[] args) throws Exception {
        silenceLogs();
        String ts = java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String out = "results/E0_cloudsim_heuristics_" + ts + ".csv";
        new java.io.File("results").mkdirs();

        int total = SCALES.length * SEEDS.length * ALGOS.length, done = 0;
        System.out.printf("E0.2A ClassicHeuristics: %d runs total%n", total);

        try (PrintWriter pw = new PrintWriter(new FileWriter(out))) {
            pw.println("Algorithm,TaskCount,Seed,Makespan,LoadBalanceRatio");
            for (int M : SCALES) {
                for (long seed : SEEDS) {
                    double[] vm = genVmMips(seed);
                    double[] cl = genClLen(seed, M);
                    for (String algo : ALGOS) {
                        int[] assign = schedule(algo, vm, cl, M);
                        double mk  = makespan(assign, vm, cl, M);
                        double lbr = lbr(assign, vm, cl, M);
                        pw.printf("%s,%d,%d,%.4f,%.4f%n", algo, M, seed, mk, lbr);
                        pw.flush();
                        done++;
                        if (done % 50 == 0 || done == total)
                            System.out.printf("[%4d/%d] %s M=%d seed=%d mk=%.1f%n",
                                done, total, algo, M, seed, mk);
                    }
                }
            }
        }
        System.out.println("Done: " + out);
    }

    // ── Scheduling algorithms ──────────────────────────────────────────────

    static int[] schedule(String algo, double[] vm, double[] cl, int M) {
        int N = vm.length;
        switch (algo) {
            case "MinMin":    return minMin(vm, cl, M, N);
            case "MaxMin":    return maxMin(vm, cl, M, N);
            case "Sufferage": return sufferage(vm, cl, M, N);
            case "RoundRobin":return roundRobin(M, N);
            case "MCT":       return mct(vm, cl, M, N);
            default: throw new IllegalArgumentException(algo);
        }
    }

    /** Min-Min: 反复找 MCT 最小的任务，分配给使其 EFT 最小的 VM。 */
    static int[] minMin(double[] vm, double[] cl, int M, int N) {
        boolean[] assigned = new boolean[M];
        int[] res = new int[M];
        double[] load = new double[N];
        for (int k = 0; k < M; k++) {
            int bestTask = -1, bestVm = 0;
            double bestEft = Double.MAX_VALUE;
            for (int i = 0; i < M; i++) {
                if (assigned[i]) continue;
                for (int v = 0; v < N; v++) {
                    double eft = load[v] + cl[i] / vm[v];
                    if (eft < bestEft) { bestEft = eft; bestTask = i; bestVm = v; }
                }
            }
            assigned[bestTask] = true;
            res[bestTask] = bestVm;
            load[bestVm] += cl[bestTask] / vm[bestVm];
        }
        return res;
    }

    /** Max-Min: 反复找 MCT 最大的任务，分配给使其 EFT 最小的 VM。 */
    static int[] maxMin(double[] vm, double[] cl, int M, int N) {
        boolean[] assigned = new boolean[M];
        int[] res = new int[M];
        double[] load = new double[N];
        for (int k = 0; k < M; k++) {
            int bestTask = -1, bestVm = 0;
            double bestEft = Double.MAX_VALUE, worstMinEft = -1;
            for (int i = 0; i < M; i++) {
                if (assigned[i]) continue;
                double minEft = Double.MAX_VALUE; int minV = 0;
                for (int v = 0; v < N; v++) {
                    double eft = load[v] + cl[i] / vm[v];
                    if (eft < minEft) { minEft = eft; minV = v; }
                }
                if (minEft > worstMinEft) { worstMinEft = minEft; bestTask = i; bestVm = minV; bestEft = minEft; }
            }
            assigned[bestTask] = true;
            res[bestTask] = bestVm;
            load[bestVm] += cl[bestTask] / vm[bestVm];
        }
        return res;
    }

    /** Sufferage: sufferage_i = MCT2_i - MCT1_i (损失最大的先分配)。 */
    static int[] sufferage(double[] vm, double[] cl, int M, int N) {
        boolean[] assigned = new boolean[M];
        int[] res = new int[M];
        double[] load = new double[N];
        for (int k = 0; k < M; k++) {
            int bestTask = -1, bestVm = 0;
            double bestSuf = -1;
            for (int i = 0; i < M; i++) {
                if (assigned[i]) continue;
                double min1 = Double.MAX_VALUE, min2 = Double.MAX_VALUE; int v1 = 0;
                for (int v = 0; v < N; v++) {
                    double eft = load[v] + cl[i] / vm[v];
                    if (eft < min1) { min2 = min1; min1 = eft; v1 = v; }
                    else if (eft < min2) min2 = eft;
                }
                double suf = min2 - min1;
                if (suf > bestSuf) { bestSuf = suf; bestTask = i; bestVm = v1; }
            }
            assigned[bestTask] = true;
            res[bestTask] = bestVm;
            load[bestVm] += cl[bestTask] / vm[bestVm];
        }
        return res;
    }

    /** MCT: 每个任务独立贪心，分配给当前 completion time 最小的 VM。 */
    static int[] mct(double[] vm, double[] cl, int M, int N) {
        int[] res = new int[M];
        double[] load = new double[N];
        for (int i = 0; i < M; i++) {
            int best = 0; double bestEft = Double.MAX_VALUE;
            for (int v = 0; v < N; v++) {
                double eft = load[v] + cl[i] / vm[v];
                if (eft < bestEft) { bestEft = eft; best = v; }
            }
            res[i] = best; load[best] += cl[i] / vm[best];
        }
        return res;
    }

    /** Round-Robin: 循环分配。 */
    static int[] roundRobin(int M, int N) {
        int[] res = new int[M];
        for (int i = 0; i < M; i++) res[i] = i % N;
        return res;
    }

    // ── Metrics ───────────────────────────────────────────────────────────

    static double makespan(int[] s, double[] vm, double[] cl, int M) {
        double[] load = new double[vm.length];
        for (int i = 0; i < M; i++) load[s[i]] += cl[i] / vm[s[i]];
        double mk = 0; for (double x : load) mk = Math.max(mk, x); return mk;
    }

    static double lbr(int[] s, double[] vm, double[] cl, int M) {
        int N = vm.length;
        double[] load = new double[N];
        for (int i = 0; i < M; i++) load[s[i]] += cl[i] / vm[s[i]];
        double avg = Arrays.stream(load).average().orElse(0);
        double var = Arrays.stream(load).map(x -> (x-avg)*(x-avg)).average().orElse(0);
        return avg > 0 ? Math.sqrt(var) / avg : 0;
    }

    // ── Data generation (same as N5000ValidationTest) ─────────────────────

    static double[] genVmMips(long seed) {
        Random r = new Random(seed);
        double[] v = new double[VM_COUNT];
        for (int i = 0; i < VM_COUNT; i++) v[i] = VM_MIPS_MIN + r.nextDouble()*(VM_MIPS_MAX-VM_MIPS_MIN);
        return v;
    }

    static double[] genClLen(long seed, int M) {
        Random r = new Random(seed + 1_000_000L);
        double[] c = new double[M];
        for (int i = 0; i < M; i++) c[i] = CL_LEN_MIN + r.nextDouble()*(CL_LEN_MAX-CL_LEN_MIN);
        return c;
    }

    static void silenceLogs() {
        try {
            ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger)
                org.slf4j.LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
            root.setLevel(ch.qos.logback.classic.Level.ERROR);
        } catch (Exception ignored) {}
    }
}

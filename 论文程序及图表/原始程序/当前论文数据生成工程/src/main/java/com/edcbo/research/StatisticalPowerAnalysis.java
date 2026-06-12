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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 统计功效分析实验 - Statistical Power Analysis
 *
 * 目的: 回答审稿人问题 "LSCBO vs GWO at N=2000 的 p=0.363 是否因为样本量不足？"
 *
 * 方法: 在 N=2000, M=50 VMs 配置下运行 100 次独立种子，
 *       计算 LSCBO vs GWO 的:
 *       - 效应量 (Cohen's d) - 观测到的真实效应大小
 *       - 统计功效 (1 - β) - 以 n=30/50/100 检测该效应的概率
 *       - 所需样本量 - 达到 80% 功效所需的最小 seed 数
 *
 * 实验设计:
 * - 算法: LSCBO, GWO (under SPV+MFD decoder)
 * - 规模: N=2000 tasks, M=50 VMs
 * - 种子: 100 个独立种子
 * - 指标: Makespan (主要), 辅助记录 Cost, Energy, LoadBalance
 *
 * @author LSCBO Research Team
 * @date 2026-06-01
 */
public class StatisticalPowerAnalysis {

    // 实验参数
    private static final int TASK_COUNT = 2000;
    private static final int VM_COUNT = 50;
    private static final int NUM_SEEDS = 100;  // 100 个独立种子

    // VM 配置
    private static final long[] VM_MIPS = {500, 750, 1000, 1250, 1500};
    private static final long VM_RAM = 2048;
    private static final long VM_BW = 1000;
    private static final long VM_SIZE = 10000;

    // 任务配置
    private static final long TASK_LENGTH_MIN = 10000;
    private static final long TASK_LENGTH_MAX = 50000;

    // 输出目录
    private static final String OUTPUT_DIR = "results/statistical_power/";

    static class RunResult {
        long seed;
        double lscboMakespan;
        double gwoMakespan;
        double lscboCost;
        double gwoCost;

        String toCsvRow() {
            return String.format("%d,%.4f,%.4f,%.4f,%.4f",
                seed, lscboMakespan, gwoMakespan, lscboCost, gwoCost);
        }
    }

    public static void main(String[] args) {
        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger)
                org.slf4j.LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
        root.setLevel(ch.qos.logback.classic.Level.ERROR);

        new File(OUTPUT_DIR).mkdirs();

        System.out.println("============================================================");
        System.out.println("   Statistical Power Analysis: LSCBO vs GWO at N=2000, M=50");
        System.out.println("============================================================");
        System.out.println("Total seeds: " + NUM_SEEDS);
        System.out.println("============================================================\n");

        List<RunResult> results = new ArrayList<>();
        long startTime = System.currentTimeMillis();

        for (int seedIdx = 0; seedIdx < NUM_SEEDS; seedIdx++) {
            long seed = 1000 + seedIdx * 7;  // systematically spaced seeds
            System.out.printf("[%d/%d] Seed=%d ... ", seedIdx + 1, NUM_SEEDS, seed);

            RunResult result = new RunResult();
            result.seed = seed;

            // Run LSCBO
            result.lscboMakespan = runSingle("LSCBO", seed);
            // Run GWO
            result.gwoMakespan = runSingle("GWO", seed);

            results.add(result);
            double diff = result.gwoMakespan - result.lscboMakespan;
            System.out.printf("LSCBO=%.2f, GWO=%.2f, Δ=%.2f%n",
                result.lscboMakespan, result.gwoMakespan, diff);
        }

        long endTime = System.currentTimeMillis();
        double minutes = (endTime - startTime) / 60000.0;

        // 统计分析
        saveResults(results);
        generateStatsReport(results, minutes);
    }

    private static double runSingle(String algo, long seed) {
        CloudSimPlus simulation = new CloudSimPlus();
        Datacenter dc = createDatacenter(simulation);
        List<Vm> vms = createVms(seed);
        List<Cloudlet> cloudlets = createCloudlets(seed);

        DatacenterBroker broker = null;
        if ("LSCBO".equals(algo)) {
            broker = new DecodableLSCBOBroker(simulation, seed,
                DecoderFairComparisonTest.DecodingStrategy.SPV_MFD);
        } else if ("GWO".equals(algo)) {
            broker = new DecodableGWOBroker(simulation, seed,
                DecoderFairComparisonTest.DecodingStrategy.SPV_MFD);
        }

        broker.submitVmList(vms);
        broker.submitCloudletList(cloudlets);
        simulation.terminateAt(300_000);
        simulation.start();

        List<Cloudlet> finished = broker.getCloudletFinishedList();
        return finished.stream()
            .mapToDouble(Cloudlet::getFinishTime)
            .max().orElse(0.0);
    }

    // ==================== 统计分析 ====================
    private static void generateStatsReport(List<RunResult> results, double durationMin) {
        String timestamp = LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = OUTPUT_DIR + "power_analysis_" + timestamp + ".txt";

        int N = results.size();
        double[] lscbo = results.stream().mapToDouble(r -> r.lscboMakespan).toArray();
        double[] gwo  = results.stream().mapToDouble(r -> r.gwoMakespan).toArray();
        double[] diffs = new double[N];
        for (int i = 0; i < N; i++) diffs[i] = gwo[i] - lscbo[i];

        // 描述性统计
        double lscboMean = mean(lscbo);
        double gwoMean = mean(gwo);
        double lscboStd = std(lscbo, lscboMean);
        double gwoStd = std(gwo, gwoMean);
        double diffMean = mean(diffs);
        double diffStd = std(diffs, diffMean);

        // Cohen's d
        double pooledStd = Math.sqrt((lscboStd * lscboStd + gwoStd * gwoStd) / 2.0);
        double cohensD = Math.abs(diffMean) / pooledStd;

        // Welch t-test
        double tStat = Math.abs(diffMean) / Math.sqrt(lscboStd * lscboStd / N + gwoStd * gwoStd / N);
        double dfWelch = Math.pow(lscboStd * lscboStd / N + gwoStd * gwoStd / N, 2)
            / (Math.pow(lscboStd * lscboStd / N, 2) / (N - 1)
             + Math.pow(gwoStd * gwoStd / N, 2) / (N - 1));
        double pValue = 2.0 * tDistSurvival(tStat, dfWelch);  // two-tailed

        // Paired t-test (更强)
        double pairedT = Math.abs(diffMean) / (diffStd / Math.sqrt(N));
        double pairedP = 2.0 * tDistSurvival(pairedT, N - 1);

        // 统计功效分析: 给定效应量 cohensD, 计算不同 n 下的功效
        double[] sampleSizes = {30, 50, 80, 100, 150, 200};

        // Bootstrap 估计功效
        int BOOTSTRAP = 10000;

        try (PrintWriter pw = new PrintWriter(new FileWriter(filename))) {
            pw.println("========================================");
            pw.println("  Statistical Power Analysis Report");
            pw.println("  N=2000 tasks, M=50 VMs");
            pw.println("========================================\n");

            pw.println("Configuration:");
            pw.printf("  Algorithm pair: LSCBO vs GWO%n");
            pw.printf("  Decoder: SPV+MFD%n");
            pw.printf("  Task count: %d, VM count: %d%n", TASK_COUNT, VM_COUNT);
            pw.printf("  Seeds: %d%n", N);
            pw.printf("  Duration: %.1f minutes%n%n", durationMin);

            pw.println("--- Descriptive Statistics ---");
            pw.printf("  LSCBO: mean=%.2f, std=%.2f%n", lscboMean, lscboStd);
            pw.printf("  GWO:   mean=%.2f, std=%.2f%n", gwoMean, gwoStd);
            pw.printf("  LSCBO better in %d/%d runs (%.1f%%)%n%n",
                countPositive(diffs), N, 100.0 * countPositive(diffs) / N);

            pw.println("--- Effect Size ---");
            pw.printf("  Mean difference (GWO - LSCBO): %.2f%n", diffMean);
            pw.printf("  Pooled SD: %.2f%n", pooledStd);
            pw.printf("  Cohen's d: %.4f (%s)%n%n",
                cohensD, interpretCohen(cohensD));

            pw.println("--- Inferential Tests (full 100 seeds) ---");
            pw.printf("  Welch t-test: t=%.4f, df=%.1f, p=%.4f%n", tStat, dfWelch, pValue);
            pw.printf("  Paired t-test: t=%.4f, df=%d, p=%.6f%n%n", pairedT, N-1, pairedP);

            pw.println("--- Required Sample Size (80% power target) ---");
            pw.printf("  Using Cohen's d = %.4f%n", cohensD);
            pw.printf("  Required N for 80%% power: %.0f%n%n",
                requiredSampleSize(cohensD, 0.80, 0.05));

            pw.println("--- Power at Different Sample Sizes ---");
            pw.println("  n_seeds | Power   | Detectable?");
            pw.println("  --------|---------|------------");
            for (double n : sampleSizes) {
                double power = computePower(cohensD, n, 0.05);
                pw.printf("  %-7.0f | %.1f%%   | %s%n",
                    n, power * 100, power >= 0.80 ? "YES ✓" : "NO");
            }
            pw.println();

            pw.println("--- Interpretation Guide ---");
            pw.println("  Effect size interpretation follows Cohen (1988):");
            pw.println("    d < 0.2: negligible");
            pw.println("    d ≈ 0.5: medium");
            pw.println("    d ≥ 0.8: large");
            pw.println();
            pw.println("  Power ≥ 80% is the conventional threshold for adequate power.");
            pw.println("  If LSCBO's advantage is statistically detectable only with");
            pw.println("  n > 100 seeds, the practical effect may be too small for");
            pw.println("  operational significance in cloud scheduling.");
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("\nPower analysis report saved to: " + filename);
    }

    // ==================== 统计辅助方法 ====================
    private static double mean(double[] arr) {
        return Arrays.stream(arr).average().orElse(0);
    }

    private static double std(double[] arr, double mean) {
        return Math.sqrt(Arrays.stream(arr)
            .map(x -> (x - mean) * (x - mean)).sum() / (arr.length - 1));
    }

    private static int countPositive(double[] arr) {
        int c = 0;
        for (double v : arr) if (v > 0) c++;
        return c;
    }

    private static double computePower(double d, double n, double alpha) {
        double ncp = Math.abs(d) * Math.sqrt(n / 2.0);
        double tCrit = tInverseCdf(1 - alpha / 2, 2 * n - 2);
        double beta = tCdf(tCrit, 2 * n - 2, ncp)
                    - tCdf(-tCrit, 2 * n - 2, ncp);
        return 1.0 - beta;
    }

    private static double requiredSampleSize(double d, double targetPower, double alpha) {
        for (int n = 2; n <= 1000; n++) {
            if (computePower(d, n, alpha) >= targetPower)
                return n;
        }
        return 1000;
    }

    // Simplified t-distribution functions (valid for df > 30)
    private static double tDistSurvival(double t, double df) {
        // Use Normal approximation for df > 30
        double x = t * Math.pow(1 - 1/(4*df), -0.5) / Math.sqrt(1 + t*t/(2*df));
        return 2 * (1 - normalCdf(x));
    }

    private static double normalCdf(double x) {
        return 0.5 * (1 + erf(x / Math.sqrt(2)));
    }

    private static double erf(double x) {
        // Approximation from Abramowitz & Stegun
        double t = 1.0 / (1.0 + 0.3275911 * Math.abs(x));
        double poly = t * (0.254829592 + t * (-0.284496736 + t * (1.421413741
            + t * (-1.453152027 + t * 1.061405429))));
        double result = 1 - poly * Math.exp(-x * x);
        return x >= 0 ? result : -result;
    }

    private static double tCdf(double t, double df, double ncp) {
        // Non-central t CDF approximation
        double z = (t * Math.sqrt(1 + ncp*ncp/(2*df)) - ncp)
                 / Math.sqrt(1 + t*t/(2*df));
        double base = normalCdf(z);
        // Johnson & Welch correction terms (2nd order)
        double corr = 0;
        if (df > 2)
            corr = (t*t - 1) * ncp / (4 * df * Math.pow(1 + t*t/(2*df), 1.5));
        return base - corr * normalPdf(z);
    }

    private static double normalPdf(double x) {
        return Math.exp(-x*x/2) / Math.sqrt(2 * Math.PI);
    }

    private static double tInverseCdf(double p, double df) {
        // Use Normal approx for df > 30
        double z = normalInvCdf(p);
        return z * Math.sqrt(1 + z*z/(2*df));
    }

    private static double normalInvCdf(double p) {
        // Rational approximation (Wichura, 1988)
        double q = p - 0.5;
        if (Math.abs(q) <= 0.425) {
            double r = 0.180625 - q*q;
            return q * (((((((2.50908092873012 * r + 3.34305755835881)
                * r + 12.6191959147142) * r + 15.6191959147142)
                * r + 6.465195135669) * r + 0.850456) * r + 0.337475)
                / (((((((2.50908092873013 * r + 5.36426095559583)
                * r + 10.3853294402613) * r + 12.6191959147142)
                * r + 9.6191959147142) * r + 3.465195135669)
                * r + 0.850456) * r + 1.0));
        } else {
            double r = Math.sqrt(-Math.log(Math.min(p, 1-p)));
            double sign = q > 0 ? 1 : -1;
            double val = (((((2.32121276858 * r + 4.85014127135)
                * r - 2.29796479134) * r - 2.78718931138)
                * r - 2.29796479134) * r + 2.78718931138) * r;
            return sign * val;
        }
    }

    private static String interpretCohen(double d) {
        d = Math.abs(d);
        if (d < 0.2) return "negligible";
        if (d < 0.5) return "small";
        if (d < 0.8) return "medium";
        return "large";
    }

    // ==================== 结果保存 ====================
    private static void saveResults(List<RunResult> results) {
        String timestamp = LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = OUTPUT_DIR + "power_raw_data_" + timestamp + ".csv";
        try (PrintWriter pw = new PrintWriter(new FileWriter(filename))) {
            pw.println("Seed,LSCBO_Makespan,GWO_Makespan,LSCBO_Cost,GWO_Cost");
            for (RunResult r : results) {
                pw.println(r.toCsvRow());
            }
            System.out.println("Raw data saved to: " + filename);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ==================== 基础设施 ====================
    private static Datacenter createDatacenter(CloudSimPlus simulation) {
        List<Host> hosts = new ArrayList<>();
        for (int i = 0; i < VM_COUNT * 2; i++) {
            List<Pe> pes = new ArrayList<>();
            pes.add(new PeSimple(2000));
            pes.add(new PeSimple(2000));
            hosts.add(new HostSimple(16384, 100000, 100000, pes));
        }
        return new DatacenterSimple(simulation, hosts, new VmAllocationPolicySimple());
    }

    private static List<Vm> createVms(long seed) {
        List<Vm> vms = new ArrayList<>();
        for (int i = 0; i < VM_COUNT; i++) {
            vms.add(new VmSimple(i, VM_MIPS[i % VM_MIPS.length], 1)
                .setRam(VM_RAM).setBw(VM_BW).setSize(VM_SIZE));
        }
        return vms;
    }

    private static List<Cloudlet> createCloudlets(long seed) {
        List<Cloudlet> list = new ArrayList<>();
        Random rng = new Random(seed);
        for (int i = 0; i < TASK_COUNT; i++) {
            long len = TASK_LENGTH_MIN
                + (long) (rng.nextDouble() * (TASK_LENGTH_MAX - TASK_LENGTH_MIN));
            list.add(new CloudletSimple(i, len, 1)
                .setFileSize(300).setOutputSize(300)
                .setUtilizationModel(new UtilizationModelFull()));
        }
        return list;
    }
}

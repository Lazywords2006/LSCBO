package com.edcbo.research;

import com.edcbo.research.utils.StatisticalTest;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimple;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimple;
import org.cloudsimplus.utilizationmodels.UtilizationModelFull;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

/**
 * 统计显著性可扩展性测试 - 多种子版本
 *
 * 配置: 4规模 × 2算法 × 5种子 = 40次测试
 *
 * 目的: 验证LSCBO-Fixed相对CBO的改进具有统计显著性
 * - Wilcoxon秩和检验: p-value < 0.05
 * - Cohen's d效应量: > 0.8 (large effect)
 */
public class StatisticalScalabilityTest {

    private static final int[] TASK_SCALES = {100, 500, 1000, 2000};
    private static final int[] VM_SCALES = {20, 100, 200, 400};
    private static final long[] SEEDS = {42, 123, 456, 789, 1024};

    // VM异构参数
    private static final int VM_MIPS_MIN = 100;
    private static final int VM_MIPS_MAX = 500;

    // 任务异构参数
    private static final long CLOUDLET_LENGTH_MIN = 10000;
    private static final long CLOUDLET_LENGTH_MAX = 50000;

    // 存储所有规模的结果
    private static Map<Integer, List<Double>> cboResults = new HashMap<>();
    private static Map<Integer, List<Double>> lscboResults = new HashMap<>();

    public static void main(String[] args) {
        System.out.println("========================================================");
        System.out.println("   统计显著性可扩展性测试程序");
        System.out.println("   配置: 4规模 × 2算法 × 5种子 = 40次测试");
        System.out.println("========================================================\n");

        long overallStartTime = System.currentTimeMillis();

        // 初始化结果存储
        for (int M : TASK_SCALES) {
            cboResults.put(M, new ArrayList<>());
            lscboResults.put(M, new ArrayList<>());
        }

        // 主实验循环：规模 × 种子
        int totalTests = TASK_SCALES.length * SEEDS.length * 2;
        int completedTests = 0;

        for (int i = 0; i < TASK_SCALES.length; i++) {
            int M = TASK_SCALES[i];
            int N = VM_SCALES[i];

            System.out.println("\n========================================================");
            System.out.println("测试规模: M=" + M + ", N=" + N);
            System.out.println("========================================================");

            for (long seed : SEEDS) {
                System.out.println("\n--- 种子: " + seed + " ---");

                // 预生成环境参数（确保CBO和LSCBO使用相同环境）
                Random random = new Random(seed);
                int[] vmMips = generateVmMips(N, random);
                long[] cloudletLengths = generateCloudletLengths(M, random);

                // 测试CBO
                System.out.print("  [CBO] 运行中...");
                try {
                    double cboMakespan = runSingleTest("CBO", M, N, vmMips, cloudletLengths, seed);
                    cboResults.get(M).add(cboMakespan);
                    System.out.println(" ✅ Makespan: " + String.format("%.2f", cboMakespan) + "s");
                    completedTests++;
                    printProgress(completedTests, totalTests);
                } catch (Exception e) {
                    System.err.println(" ❌ 失败: " + e.getMessage());
                }

                // 测试LSCBO-Fixed
                System.out.print("  [LSCBO-Fixed] 运行中...");
                try {
                    double lscboMakespan = runSingleTest("LSCBO-Fixed", M, N, vmMips, cloudletLengths, seed);
                    lscboResults.get(M).add(lscboMakespan);
                    System.out.println(" ✅ Makespan: " + String.format("%.2f", lscboMakespan) + "s");
                    completedTests++;
                    printProgress(completedTests, totalTests);
                } catch (Exception e) {
                    System.err.println(" ❌ 失败: " + e.getMessage());
                }
            }
        }

        long overallEndTime = System.currentTimeMillis();
        long totalTime = (overallEndTime - overallStartTime) / 1000;

        System.out.println("\n========================================================");
        System.out.println("所有测试完成！总耗时: " + totalTime + " 秒");
        System.out.println("========================================================\n");

        // 统计分析
        performStatisticalAnalysis();

        // 导出结果到CSV
        exportResultsToCSV();
    }

    private static double runSingleTest(String algorithmName, int M, int N,
                                        int[] vmMips, long[] cloudletLengths, long seed) {
        CloudSimPlus simulation = new CloudSimPlus();
        Datacenter datacenter = createDatacenter(simulation, N);

        // 创建Broker
        Object broker;
        if (algorithmName.equals("CBO")) {
            broker = new CBO_Broker(simulation, seed);
        } else if (algorithmName.equals("LSCBO-Fixed")) {
            broker = new LSCBO_Broker_Fixed(simulation, seed);
        } else {
            throw new IllegalArgumentException("Unknown algorithm: " + algorithmName);
        }

        // 创建VM列表
        List<Vm> vmList = new ArrayList<>();
        for (int i = 0; i < N; i++) {
            Vm vm = new VmSimple(vmMips[i], 1)
                .setRam(2048).setBw(1000).setSize(10000);
            vmList.add(vm);
        }

        // 创建Cloudlet列表
        List<Cloudlet> cloudletList = new ArrayList<>();
        for (int i = 0; i < M; i++) {
            Cloudlet cloudlet = new CloudletSimple(cloudletLengths[i], 1)
                .setFileSize(300).setOutputSize(300)
                .setUtilizationModelCpu(new UtilizationModelFull());
            cloudletList.add(cloudlet);
        }

        // 提交任务和VM
        if (broker instanceof CBO_Broker) {
            ((CBO_Broker) broker).submitVmList(vmList);
            ((CBO_Broker) broker).submitCloudletList(cloudletList);
        } else if (broker instanceof LSCBO_Broker_Fixed) {
            ((LSCBO_Broker_Fixed) broker).submitVmList(vmList);
            ((LSCBO_Broker_Fixed) broker).submitCloudletList(cloudletList);
        }

        simulation.start();

        // 计算Makespan
        double makespan = 0.0;
        for (Cloudlet cloudlet : cloudletList) {
            double finishTime = cloudlet.getFinishTime();
            if (finishTime > makespan) {
                makespan = finishTime;
            }
        }

        return makespan;
    }

    private static void performStatisticalAnalysis() {
        System.out.println("╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║              统计显著性分析结果                                ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝\n");

        for (int M : TASK_SCALES) {
            List<Double> cboList = cboResults.get(M);
            List<Double> lscboList = lscboResults.get(M);

            if (cboList.size() < 5 || lscboList.size() < 5) {
                System.out.println("⚠️ M=" + M + " 数据不足，跳过统计分析");
                continue;
            }

            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            System.out.println("规模: M=" + M);
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

            // 描述性统计
            double cboMean = cboList.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double lscboMean = lscboList.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double cboStd = calculateStd(cboList, cboMean);
            double lscboStd = calculateStd(lscboList, lscboMean);

            double improvementRate = ((cboMean - lscboMean) / cboMean) * 100.0;

            System.out.println("\n📊 描述性统计:");
            System.out.println("  CBO平均值:          " + String.format("%.2f", cboMean) + " ± " + String.format("%.2f", cboStd));
            System.out.println("  LSCBO-Fixed平均值:  " + String.format("%.2f", lscboMean) + " ± " + String.format("%.2f", lscboStd));
            System.out.println("  改进率:             " + String.format("%.2f%%", improvementRate));

            // Wilcoxon秩和检验
            double pValue = StatisticalTest.wilcoxonTest(cboList, lscboList);
            String significance = StatisticalTest.interpretPValue(pValue);

            System.out.println("\n🔬 Wilcoxon秩和检验:");
            System.out.println("  p-value:            " + String.format("%.4e", pValue) + " " + significance);
            System.out.println("  显著性:             " + (pValue < 0.05 ? "✅ 显著 (p < 0.05)" : "❌ 不显著 (p >= 0.05)"));

            // Cohen's d效应量
            double cohensD = StatisticalTest.cohensD(cboList, lscboList);
            String effect = StatisticalTest.interpretCohensD(cohensD);

            System.out.println("\n💪 Cohen's d效应量:");
            System.out.println("  Cohen's d:          " + String.format("%.4f", cohensD));
            System.out.println("  效应大小:           " + effect);
            System.out.println("  解释:               " + (Math.abs(cohensD) > 0.8 ? "✅ Large effect (|d| > 0.8)" :
                                                        Math.abs(cohensD) > 0.5 ? "⚠️ Medium effect (|d| > 0.5)" :
                                                        "❌ Small effect (|d| ≤ 0.5)"));

            // 95%置信区间
            double[] cboCI = calculate95CI(cboList);
            double[] lscboCI = calculate95CI(lscboList);

            System.out.println("\n📈 95%置信区间:");
            System.out.println("  CBO:                [" + String.format("%.2f", cboCI[0]) + ", " + String.format("%.2f", cboCI[1]) + "]");
            System.out.println("  LSCBO-Fixed:        [" + String.format("%.2f", lscboCI[0]) + ", " + String.format("%.2f", lscboCI[1]) + "]");
            System.out.println("  区间重叠:           " + (lscboCI[1] < cboCI[0] ? "✅ 无重叠（LSCBO显著更优）" : "⚠️ 有重叠"));

            System.out.println();
        }

        // 总结
        System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║              验收标准检查                                      ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝\n");

        boolean allSignificant = true;
        boolean allLargeEffect = true;

        for (int M : TASK_SCALES) {
            List<Double> cboList = cboResults.get(M);
            List<Double> lscboList = lscboResults.get(M);

            if (cboList.size() < 5 || lscboList.size() < 5) continue;

            double pValue = StatisticalTest.wilcoxonTest(cboList, lscboList);
            double cohensD = StatisticalTest.cohensD(cboList, lscboList);

            boolean significant = pValue < 0.05;
            boolean largeEffect = Math.abs(cohensD) > 0.8;

            allSignificant &= significant;
            allLargeEffect &= largeEffect;

            System.out.println("M=" + M + ": p=" + String.format("%.4e", pValue) + " " + (significant ? "✅" : "❌") +
                             ", d=" + String.format("%.4f", cohensD) + " " + (largeEffect ? "✅" : "⚠️"));
        }

        System.out.println("\n最终验收:");
        System.out.println("  " + (allSignificant ? "✅" : "❌") + " 所有规模p-value < 0.05");
        System.out.println("  " + (allLargeEffect ? "✅" : "⚠️") + " 所有规模Cohen's d > 0.8");
        System.out.println("  " + (allSignificant && allLargeEffect ? "✅ 通过验收" : "⚠️ 部分通过"));
    }

    private static void exportResultsToCSV() {
        String filename = "results/statistical_scalability_results.csv";
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
            writer.write("Scale,Seed,CBO_Makespan,LSCBO_Makespan,Improvement_Rate\n");

            for (int i = 0; i < TASK_SCALES.length; i++) {
                int M = TASK_SCALES[i];
                List<Double> cboList = cboResults.get(M);
                List<Double> lscboList = lscboResults.get(M);

                for (int j = 0; j < SEEDS.length && j < cboList.size() && j < lscboList.size(); j++) {
                    double cbo = cboList.get(j);
                    double lscbo = lscboList.get(j);
                    double improvement = ((cbo - lscbo) / cbo) * 100.0;

                    writer.write(String.format("%d,%d,%.2f,%.2f,%.2f\n",
                        M, SEEDS[j], cbo, lscbo, improvement));
                }
            }

            System.out.println("\n✅ 结果已导出到: " + filename);
        } catch (IOException e) {
            System.err.println("❌ 导出结果失败: " + e.getMessage());
        }
    }

    private static double calculateStd(List<Double> values, double mean) {
        double variance = values.stream()
            .mapToDouble(v -> Math.pow(v - mean, 2))
            .average().orElse(0);
        return Math.sqrt(variance);
    }

    private static double[] calculate95CI(List<Double> values) {
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double std = calculateStd(values, mean);
        double margin = 1.96 * std / Math.sqrt(values.size());  // 95% CI
        return new double[] {mean - margin, mean + margin};
    }

    private static void printProgress(int completed, int total) {
        int percentage = (completed * 100) / total;
        System.out.println("  进度: " + completed + "/" + total + " (" + percentage + "%)");
    }

    private static int[] generateVmMips(int N, Random random) {
        int[] vmMips = new int[N];
        for (int i = 0; i < N; i++) {
            vmMips[i] = VM_MIPS_MIN + random.nextInt(VM_MIPS_MAX - VM_MIPS_MIN + 1);
        }
        return vmMips;
    }

    private static long[] generateCloudletLengths(int M, Random random) {
        long[] cloudletLengths = new long[M];
        for (int i = 0; i < M; i++) {
            cloudletLengths[i] = CLOUDLET_LENGTH_MIN +
                Math.abs(random.nextLong() % (CLOUDLET_LENGTH_MAX - CLOUDLET_LENGTH_MIN + 1));
            if (cloudletLengths[i] < CLOUDLET_LENGTH_MIN) {
                cloudletLengths[i] = CLOUDLET_LENGTH_MIN;
            }
        }
        return cloudletLengths;
    }

    private static Datacenter createDatacenter(CloudSimPlus simulation, int numVms) {
        int numHosts = numVms + 10;  // 确保有足够资源

        List<Host> hostList = new ArrayList<>();
        for (int i = 0; i < numHosts; i++) {
            List<Pe> peList = new ArrayList<>();
            peList.add(new PeSimple(2000));

            Host host = new HostSimple(16384, 100000, 100000, peList);
            hostList.add(host);
        }

        return new DatacenterSimple(simulation, hostList);
    }
}

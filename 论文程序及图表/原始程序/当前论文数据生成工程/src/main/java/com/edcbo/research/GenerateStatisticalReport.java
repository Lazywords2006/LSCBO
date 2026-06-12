package com.edcbo.research;

import com.edcbo.research.utils.StatisticalTest;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 生成Q1/Q2实验的统计检验报告
 *
 * 功能：
 * 1. Friedman检验（9算法×7规模）
 * 2. Wilcoxon成对检验（所有算法两两对比）
 * 3. Cohen's d效应量计算
 * 4. Nemenyi后续检验
 *
 * @author EDCBO Research Team
 * @date 2025-12-16
 */
public class GenerateStatisticalReport {

    private static final String[] ALGORITHMS = {
        "PSO", "LSCBO-Fixed", "GTO", "WOA", "HHO", "GWO", "CBO", "AOA", "Random"
    };

    private static final int[] SCALES = {50, 100, 200, 300, 500, 1000, 2000};

    public static void main(String[] args) {
        System.out.println("============================================================");
        System.out.println("   Q1/Q2 Statistical Test Report");
        System.out.println("============================================================\n");

        // 读取合并后的CSV数据
        String csvFile = "results/q1q2_full_3150_merged.csv";
        Map<String, Map<Integer, List<Double>>> data = readCSV(csvFile);

        if (data.isEmpty()) {
            System.err.println("Error: No data loaded from " + csvFile);
            return;
        }

        System.out.println("Data loaded successfully:");
        System.out.println("  Algorithms: " + data.size());
        System.out.println("  Scales: " + SCALES.length);
        System.out.println();

        // 1. Friedman检验
        performFriedmanTest(data);

        // 2. Wilcoxon成对检验
        performPairwiseWilcoxonTests(data);

        // 3. 重点对比：LSCBO-Fixed vs CBO
        performKeyComparison(data, "LSCBO-Fixed", "CBO");

        System.out.println("\n============================================================");
        System.out.println("   Report Generation Complete!");
        System.out.println("============================================================");
    }

    /**
     * 读取CSV文件
     */
    private static Map<String, Map<Integer, List<Double>>> readCSV(String filename) {
        Map<String, Map<Integer, List<Double>>> data = new HashMap<>();

        try (BufferedReader br = Files.newBufferedReader(Paths.get(filename))) {
            String line = br.readLine(); // 跳过表头

            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length < 5) continue;

                String algorithm = parts[0].trim();
                int taskCount = Integer.parseInt(parts[1].trim());
                double internalMakespan = Double.parseDouble(parts[4].trim());

                data.putIfAbsent(algorithm, new HashMap<>());
                data.get(algorithm).putIfAbsent(taskCount, new ArrayList<>());
                data.get(algorithm).get(taskCount).add(internalMakespan);
            }
        } catch (IOException e) {
            System.err.println("Error reading CSV: " + e.getMessage());
        }

        return data;
    }

    /**
     * Friedman检验
     */
    private static void performFriedmanTest(Map<String, Map<Integer, List<Double>>> data) {
        System.out.println("============================================================");
        System.out.println("   1. Friedman Test (9 Algorithms x 7 Scales)");
        System.out.println("============================================================\n");

        // 构建数据矩阵：data[i][j] = 算法i在规模j上的平均性能
        int k = ALGORITHMS.length;
        int N = SCALES.length;
        double[][] matrix = new double[k][N];

        for (int i = 0; i < k; i++) {
            String algo = ALGORITHMS[i];
            for (int j = 0; j < N; j++) {
                int scale = SCALES[j];
                List<Double> values = data.get(algo).get(scale);
                double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                matrix[i][j] = mean;
            }
        }

        // 执行Friedman检验
        StatisticalTest.FriedmanTestResult result = StatisticalTest.friedmanTestFull(matrix, 0.05);

        System.out.println("Friedman Test Results:");
        System.out.println("  Chi-Square Statistic: " + String.format("%.4f", result.chiSquare));
        System.out.println("  p-value: " + String.format("%.4e", result.pValue));
        System.out.println("  Significance: " + StatisticalTest.interpretPValue(result.pValue));
        System.out.println("  Critical Difference (CD): " + String.format("%.4f", result.criticalDifference));
        System.out.println();

        // 显示平均排名
        System.out.println("Average Ranks (lower is better):");
        for (int i = 0; i < k; i++) {
            String medal = i == 0 ? "[1]" : i == 1 ? "[2]" : i == 2 ? "[3]" : "   ";
            System.out.println(String.format("  %s %d. %-15s Rank: %.4f",
                    medal, i + 1, ALGORITHMS[i], result.averageRanks[i]));
        }
        System.out.println();

        // Nemenyi后续检验
        System.out.println("Nemenyi Post-hoc Test:");
        System.out.println("  If |Rank_i - Rank_j| > CD, then algorithms i and j are significantly different.");
        System.out.println();

        // 显示关键对比
        System.out.println("Key Comparisons:");
        for (int i = 0; i < k - 1; i++) {
            for (int j = i + 1; j < k; j++) {
                double rankDiff = Math.abs(result.averageRanks[i] - result.averageRanks[j]);
                boolean significant = rankDiff > result.criticalDifference;
                if (significant || ALGORITHMS[i].contains("LSCBO") || ALGORITHMS[j].contains("LSCBO")) {
                    System.out.println(String.format("  %s vs %s: |%.4f - %.4f| = %.4f %s",
                            ALGORITHMS[i], ALGORITHMS[j],
                            result.averageRanks[i], result.averageRanks[j],
                            rankDiff,
                            significant ? "(Significant)" : "(Not Significant)"));
                }
            }
        }
        System.out.println();
    }

    /**
     * Wilcoxon成对检验（所有算法两两对比）
     */
    private static void performPairwiseWilcoxonTests(Map<String, Map<Integer, List<Double>>> data) {
        System.out.println("============================================================");
        System.out.println("   2. Pairwise Wilcoxon Tests (All Algorithms)");
        System.out.println("============================================================\n");

        // 合并所有规模的数据
        Map<String, List<Double>> allData = new HashMap<>();
        for (String algo : ALGORITHMS) {
            List<Double> combined = new ArrayList<>();
            for (int scale : SCALES) {
                combined.addAll(data.get(algo).get(scale));
            }
            allData.put(algo, combined);
        }

        System.out.println("Pairwise Comparisons (p-value < 0.05 indicates significant difference):");
        System.out.println();

        // 只显示涉及LSCBO-Fixed和CBO的对比
        String[] keyAlgos = {"LSCBO-Fixed", "CBO", "PSO", "GTO", "WOA"};

        for (int i = 0; i < keyAlgos.length; i++) {
            for (int j = i + 1; j < keyAlgos.length; j++) {
                String algo1 = keyAlgos[i];
                String algo2 = keyAlgos[j];

                List<Double> data1 = allData.get(algo1);
                List<Double> data2 = allData.get(algo2);

                double pValue = StatisticalTest.wilcoxonTest(data1, data2);
                double cohensD = StatisticalTest.cohensD(data1, data2);
                String effectSize = StatisticalTest.interpretCohensD(cohensD);
                String significance = StatisticalTest.interpretPValue(pValue);

                System.out.println(String.format("  %s vs %s:",
                        algo1, algo2));
                System.out.println(String.format("    p-value: %.4e %s", pValue, significance));
                System.out.println(String.format("    Cohen's d: %.4f (%s)", cohensD, effectSize));
                System.out.println();
            }
        }
    }

    /**
     * 重点对比：LSCBO-Fixed vs CBO
     */
    private static void performKeyComparison(Map<String, Map<Integer, List<Double>>> data,
                                            String algo1, String algo2) {
        System.out.println("============================================================");
        System.out.println("   3. Key Comparison: " + algo1 + " vs " + algo2);
        System.out.println("============================================================\n");

        // 按规模分别对比
        System.out.println("Scale-by-Scale Comparison:");
        System.out.println();

        for (int scale : SCALES) {
            List<Double> data1 = data.get(algo1).get(scale);
            List<Double> data2 = data.get(algo2).get(scale);

            double mean1 = data1.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            double mean2 = data2.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            double improvement = ((mean2 - mean1) / mean2) * 100.0;

            double pValue = StatisticalTest.wilcoxonTest(data1, data2);
            double cohensD = StatisticalTest.cohensD(data2, data1); // baseline=CBO
            String effectSize = StatisticalTest.interpretCohensD(cohensD);
            String significance = StatisticalTest.interpretPValue(pValue);

            System.out.println(String.format("M=%d:", scale));
            System.out.println(String.format("  %s: %.2f", algo1, mean1));
            System.out.println(String.format("  %s: %.2f", algo2, mean2));
            System.out.println(String.format("  Improvement: %.2f%%", improvement));
            System.out.println(String.format("  p-value: %.4e %s", pValue, significance));
            System.out.println(String.format("  Cohen's d: %.4f (%s)", cohensD, effectSize));
            System.out.println();
        }

        // 总体对比
        List<Double> allData1 = new ArrayList<>();
        List<Double> allData2 = new ArrayList<>();
        for (int scale : SCALES) {
            allData1.addAll(data.get(algo1).get(scale));
            allData2.addAll(data.get(algo2).get(scale));
        }

        double overallMean1 = allData1.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double overallMean2 = allData2.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double overallImprovement = ((overallMean2 - overallMean1) / overallMean2) * 100.0;

        double overallPValue = StatisticalTest.wilcoxonTest(allData1, allData2);
        double overallCohensD = StatisticalTest.cohensD(allData2, allData1);
        String overallEffectSize = StatisticalTest.interpretCohensD(overallCohensD);
        String overallSignificance = StatisticalTest.interpretPValue(overallPValue);

        System.out.println("Overall Comparison (All Scales Combined):");
        System.out.println(String.format("  %s: %.2f", algo1, overallMean1));
        System.out.println(String.format("  %s: %.2f", algo2, overallMean2));
        System.out.println(String.format("  Overall Improvement: %.2f%%", overallImprovement));
        System.out.println(String.format("  p-value: %.4e %s", overallPValue, overallSignificance));
        System.out.println(String.format("  Cohen's d: %.4f (%s)", overallCohensD, overallEffectSize));
        System.out.println();
    }
}

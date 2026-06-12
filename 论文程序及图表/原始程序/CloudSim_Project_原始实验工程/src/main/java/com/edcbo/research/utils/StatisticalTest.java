package com.edcbo.research.utils;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.inference.MannWhitneyUTest;

import java.util.List;

/**
 * StatisticalTest - 统计检验工具类
 *
 * 提供算法性能对比的统计显著性检验
 *
 * 功能：
 * 1. Wilcoxon秩和检验（Mann-Whitney U Test）- 非参数检验
 * 2. Cohen's d效应量 - 衡量差异大小
 *
 * 使用场景：
 * - 验证LSCBO-Fixed相对CBO的改进是否统计显著
 * - 计算效应量以评估改进的实际意义
 *
 * 参考标准：
 * - p-value < 0.05: 显著差异
 * - p-value < 0.01: 高度显著差异
 * - Cohen's d > 0.5: 中等效应
 * - Cohen's d > 0.8: 大效应
 */
public class StatisticalTest {

    /**
     * Wilcoxon秩和检验（Mann-Whitney U Test）
     *
     * 非参数检验，不假设数据分布，适合云调度实验数据
     *
     * @param baseline 基准算法结果（如CBO）
     * @param improved 改进算法结果（如LSCBO-Fixed）
     * @return p-value（<0.05表示显著差异）
     */
    public static double wilcoxonTest(List<Double> baseline, List<Double> improved) {
        if (baseline.isEmpty() || improved.isEmpty()) {
            return 1.0; // 无数据时返回1.0（不显著）
        }

        // 转换为double数组
        double[] baselineArray = baseline.stream().mapToDouble(Double::doubleValue).toArray();
        double[] improvedArray = improved.stream().mapToDouble(Double::doubleValue).toArray();

        // 执行Mann-Whitney U检验
        MannWhitneyUTest test = new MannWhitneyUTest();
        return test.mannWhitneyUTest(baselineArray, improvedArray);
    }

    /**
     * Cohen's d效应量
     *
     * 衡量两组数据的标准化差异大小
     *
     * 效应量解释：
     * - d < 0.2: 可忽略效应
     * - 0.2 <= d < 0.5: 小效应
     * - 0.5 <= d < 0.8: 中等效应
     * - d >= 0.8: 大效应
     *
     * @param baseline 基准算法结果（如CBO）
     * @param improved 改进算法结果（如LSCBO-Fixed）
     * @return Cohen's d值（>0表示改进算法更好）
     */
    public static double cohensD(List<Double> baseline, List<Double> improved) {
        if (baseline.isEmpty() || improved.isEmpty()) {
            return 0.0;
        }

        // 计算均值
        DescriptiveStatistics baselineStats = new DescriptiveStatistics();
        DescriptiveStatistics improvedStats = new DescriptiveStatistics();

        baseline.forEach(baselineStats::addValue);
        improved.forEach(improvedStats::addValue);

        double baselineMean = baselineStats.getMean();
        double improvedMean = improvedStats.getMean();

        // 计算合并标准差（pooled standard deviation）
        double baselineVar = baselineStats.getVariance();
        double improvedVar = improvedStats.getVariance();
        int n1 = baseline.size();
        int n2 = improved.size();

        double pooledStd = Math.sqrt(((n1 - 1) * baselineVar + (n2 - 1) * improvedVar) / (n1 + n2 - 2));

        // Cohen's d = (M1 - M2) / pooled_std
        // 注意：对于最小化问题（如Makespan），improved < baseline时d为正
        return (baselineMean - improvedMean) / pooledStd;
    }

    /**
     * 效应量解释
     *
     * @param cohensD Cohen's d值
     * @return 效应量描述
     */
    public static String interpretCohensD(double cohensD) {
        double absD = Math.abs(cohensD);
        if (absD < 0.2) {
            return "negligible";  // 可忽略
        } else if (absD < 0.5) {
            return "small";       // 小效应
        } else if (absD < 0.8) {
            return "medium";      // 中等效应
        } else {
            return "large";       // 大效应
        }
    }

    /**
     * p-value解释
     *
     * @param pValue p-value
     * @return 显著性描述
     */
    public static String interpretPValue(double pValue) {
        if (pValue < 0.001) {
            return "***";  // 高度显著
        } else if (pValue < 0.01) {
            return "**";   // 很显著
        } else if (pValue < 0.05) {
            return "*";    // 显著
        } else {
            return "ns";   // 不显著
        }
    }

    /**
     * 判断是否显著
     *
     * @param pValue p-value
     * @return true if significant (p < 0.05)
     */
    public static boolean isSignificant(double pValue) {
        return pValue < 0.05;
    }

    /**
     * Friedman检验 - 多算法非参数检验
     *
     * Friedman检验用于比较多个相关样本（算法）在多个数据集（规模）上的性能
     * 是Kruskal-Wallis检验的配对版本
     *
     * 原假设H0：所有算法的性能相同
     * 备择假设H1：至少有一个算法的性能不同
     *
     * @param data 实验数据矩阵：data[i][j] = 算法i在规模j上的性能
     *             - 行：算法（k个算法）
     *             - 列：数据集/规模（N个问题）
     * @return p-value（<0.05表示算法间存在显著差异）
     */
    public static double friedmanTest(double[][] data) {
        int k = data.length;      // 算法数
        int N = data[0].length;   // 问题数（规模数）

        // 验证数据完整性
        for (int i = 0; i < k; i++) {
            if (data[i].length != N) {
                throw new IllegalArgumentException("数据矩阵不规则：每个算法必须在所有数据集上有结果");
            }
        }

        // Step 1: 对每个数据集（列），对所有算法（行）进行排名（从小到大，1是最好）
        double[][] ranks = new double[k][N];
        for (int j = 0; j < N; j++) {
            // 提取第j列（第j个数据集上所有算法的结果）
            double[] column = new double[k];
            for (int i = 0; i < k; i++) {
                column[i] = data[i][j];
            }

            // 对这一列进行排名（最小值排名1）
            double[] columnRanks = rankArray(column);

            // 将排名填回ranks矩阵
            for (int i = 0; i < k; i++) {
                ranks[i][j] = columnRanks[i];
            }
        }

        // Step 2: 计算每个算法的排名总和
        double[] rankSums = new double[k];
        for (int i = 0; i < k; i++) {
            for (int j = 0; j < N; j++) {
                rankSums[i] += ranks[i][j];
            }
        }

        // Step 3: 计算Friedman统计量χ²
        // 公式: χ² = (12 * N) / (k * (k+1)) * Σ(R_i²) - 3 * N * (k+1)
        double sumSquaredRanks = 0.0;
        for (int i = 0; i < k; i++) {
            sumSquaredRanks += rankSums[i] * rankSums[i];
        }

        double chiSquare = (12.0 * N) / (k * (k + 1.0)) * sumSquaredRanks - 3.0 * N * (k + 1.0);

        // Step 4: 计算p-value
        // Friedman统计量近似服从自由度为(k-1)的χ²分布
        int degreesOfFreedom = k - 1;
        org.apache.commons.math3.distribution.ChiSquaredDistribution chiSquaredDist =
                new org.apache.commons.math3.distribution.ChiSquaredDistribution(degreesOfFreedom);

        // p-value = P(χ² > 观测值)
        double pValue = 1.0 - chiSquaredDist.cumulativeProbability(chiSquare);

        return pValue;
    }

    /**
     * 对数组进行排名（从小到大，最小值排名1）
     *
     * 处理并列情况：使用平均排名
     * 例如：[10, 20, 20, 30] → [1, 2.5, 2.5, 4]
     *
     * @param array 输入数组
     * @return 排名数组
     */
    private static double[] rankArray(double[] array) {
        int n = array.length;
        double[] ranks = new double[n];

        // 创建索引数组并排序
        Integer[] indices = new Integer[n];
        for (int i = 0; i < n; i++) {
            indices[i] = i;
        }

        // 按值排序索引
        java.util.Arrays.sort(indices, (a, b) -> Double.compare(array[a], array[b]));

        // 分配排名（处理并列）
        int i = 0;
        while (i < n) {
            int j = i;
            // 找到所有相同值的元素
            while (j < n && array[indices[j]] == array[indices[i]]) {
                j++;
            }

            // 计算平均排名（排名从1开始）
            double averageRank = (i + 1 + j) / 2.0;

            // 分配平均排名给所有并列元素
            for (int k = i; k < j; k++) {
                ranks[indices[k]] = averageRank;
            }

            i = j;
        }

        return ranks;
    }

    /**
     * 计算Friedman检验后的平均排名
     *
     * @param data 实验数据矩阵：data[i][j] = 算法i在规模j上的性能
     * @return 每个算法的平均排名（值越小越好）
     */
    public static double[] calculateAverageRanks(double[][] data) {
        int k = data.length;      // 算法数
        int N = data[0].length;   // 问题数

        // 对每个数据集进行排名
        double[][] ranks = new double[k][N];
        for (int j = 0; j < N; j++) {
            double[] column = new double[k];
            for (int i = 0; i < k; i++) {
                column[i] = data[i][j];
            }
            double[] columnRanks = rankArray(column);
            for (int i = 0; i < k; i++) {
                ranks[i][j] = columnRanks[i];
            }
        }

        // 计算平均排名
        double[] averageRanks = new double[k];
        for (int i = 0; i < k; i++) {
            double sum = 0.0;
            for (int j = 0; j < N; j++) {
                sum += ranks[i][j];
            }
            averageRanks[i] = sum / N;
        }

        return averageRanks;
    }

    /**
     * Nemenyi后续检验 - 多算法事后多重比较
     *
     * 在Friedman检验显著后，使用Nemenyi检验进行算法间两两比较
     * 计算临界差值（Critical Difference, CD）
     *
     * @param k 算法数
     * @param N 数据集数（规模数）
     * @param alpha 显著性水平（通常为0.05或0.10）
     * @return 临界差值CD（如果两个算法的平均排名差>CD，则显著不同）
     */
    public static double nemenyiCriticalDifference(int k, int N, double alpha) {
        // Nemenyi临界值表（alpha=0.05）
        // 来源：Demšar, J. (2006). Statistical comparisons of classifiers over multiple data sets.
        //       Journal of Machine learning research, 7(1), 1-30.
        double[][] qAlphaTable = {
                // k:   2      3      4      5      6      7      8      9     10
                /* 0.05 */ {0.0, 1.960, 2.343, 2.569, 2.728, 2.850, 2.949, 3.031, 3.102},
                /* 0.10 */ {0.0, 1.645, 2.052, 2.291, 2.459, 2.589, 2.693, 2.780, 2.855}
        };

        // 选择临界值
        double qAlpha;
        if (alpha == 0.05 && k >= 2 && k <= 10) {
            qAlpha = qAlphaTable[0][k - 1];
        } else if (alpha == 0.10 && k >= 2 && k <= 10) {
            qAlpha = qAlphaTable[1][k - 1];
        } else {
            // 对于k>10或其他alpha值，使用近似公式
            // qAlpha ≈ z_(alpha/2) * sqrt(2)
            if (alpha == 0.05) {
                qAlpha = 1.960 * Math.sqrt(2);
            } else if (alpha == 0.10) {
                qAlpha = 1.645 * Math.sqrt(2);
            } else {
                throw new IllegalArgumentException("Unsupported alpha: " + alpha);
            }
        }

        // 计算临界差值
        // CD = q_alpha * sqrt(k*(k+1) / (6*N))
        double CD = qAlpha * Math.sqrt(k * (k + 1.0) / (6.0 * N));

        return CD;
    }

    /**
     * Friedman检验结果对象
     */
    public static class FriedmanTestResult {
        public final double pValue;
        public final double chiSquare;
        public final double[] averageRanks;
        public final double criticalDifference;
        public final boolean isSignificant;

        public FriedmanTestResult(double pValue, double chiSquare, double[] averageRanks,
                                   double criticalDifference, boolean isSignificant) {
            this.pValue = pValue;
            this.chiSquare = chiSquare;
            this.averageRanks = averageRanks;
            this.criticalDifference = criticalDifference;
            this.isSignificant = isSignificant;
        }

        @Override
        public String toString() {
            return String.format("Friedman Test: χ²=%.4f, p=%.4e (%s), CD=%.4f",
                    chiSquare, pValue, interpretPValue(pValue), criticalDifference);
        }
    }

    /**
     * 完整的Friedman检验分析（包括后续检验）
     *
     * @param data 实验数据矩阵
     * @param alpha 显著性水平（默认0.05）
     * @return Friedman检验完整结果
     */
    public static FriedmanTestResult friedmanTestFull(double[][] data, double alpha) {
        int k = data.length;
        int N = data[0].length;

        double pValue = friedmanTest(data);
        double[] averageRanks = calculateAverageRanks(data);

        // 计算χ²统计量（用于报告）
        double[] rankSums = new double[k];
        for (int i = 0; i < k; i++) {
            for (int j = 0; j < N; j++) {
                // 重新计算排名（效率不高但代码清晰）
                double[] column = new double[k];
                for (int ii = 0; ii < k; ii++) {
                    column[ii] = data[ii][j];
                }
                double[] columnRanks = rankArray(column);
                rankSums[i] += columnRanks[i];
            }
        }
        double sumSquaredRanks = 0.0;
        for (int i = 0; i < k; i++) {
            sumSquaredRanks += rankSums[i] * rankSums[i];
        }
        double chiSquare = (12.0 * N) / (k * (k + 1.0)) * sumSquaredRanks - 3.0 * N * (k + 1.0);

        // 计算临界差值
        double CD = nemenyiCriticalDifference(k, N, alpha);

        // 判断显著性
        boolean isSignificant = pValue < alpha;

        return new FriedmanTestResult(pValue, chiSquare, averageRanks, CD, isSignificant);
    }

    /**
     * 完整的Friedman检验分析（使用默认alpha=0.05）
     */
    public static FriedmanTestResult friedmanTestFull(double[][] data) {
        return friedmanTestFull(data, 0.05);
    }

    // ==================== 多重比较校正 ====================

    /**
     * Bonferroni多重比较校正
     *
     * 用于校正多次成对比较导致的Type I错误累积。
     * Bonferroni校正是最保守的方法，适合少量比较（<10次）。
     *
     * 公式：p_adjusted = min(p_original × m, 1.0)
     * 其中 m 是比较次数
     *
     * 使用场景：
     * - LSCBO-Fixed与其他7个算法进行成对Wilcoxon检验（7次比较）
     * - 校正后仍然p < 0.05，则证明改进非常稳健
     *
     * 参考文献：
     * Bonferroni, C. E. (1936). Teoria statistica delle classi e calcolo delle probabilita.
     *
     * @param pValue 原始p值
     * @param numComparisons 比较次数（必须>0）
     * @return 校正后的p值（范围[0, 1]）
     * @throws IllegalArgumentException 如果numComparisons <= 0
     */
    public static double bonferroniCorrection(double pValue, int numComparisons) {
        if (numComparisons <= 0) {
            throw new IllegalArgumentException("numComparisons must be positive, got: " + numComparisons);
        }
        return Math.min(pValue * numComparisons, 1.0);
    }

    /**
     * Holm多重比较校正（逐步拒绝方法）
     *
     * Holm校正比Bonferroni更强大，在保持相同Type I错误率的同时
     * 提供更高的统计功效（power）。
     *
     * 算法：
     * 1. 将p值从小到大排序：p(1) <= p(2) <= ... <= p(m)
     * 2. 对于第i个p值，校正因子为 (m - i + 1)
     * 3. p_adjusted(i) = min(p(i) × (m - i + 1), 1.0)
     *
     * 注意：输入的pValues数组必须已经排序（从小到大）
     *
     * 使用场景：
     * - 多个算法与LSCBO-Fixed的成对比较
     * - 比Bonferroni更推荐使用
     *
     * 参考文献：
     * Holm, S. (1979). A simple sequentially rejective multiple test procedure.
     * Scandinavian Journal of Statistics, 6(2), 65-70.
     *
     * @param pValues 原始p值数组（必须已排序，从小到大）
     * @return 校正后的p值数组
     * @throws IllegalArgumentException 如果pValues为null或长度为0
     */
    public static double[] holmCorrection(double[] pValues) {
        if (pValues == null || pValues.length == 0) {
            throw new IllegalArgumentException("pValues array cannot be null or empty");
        }

        int m = pValues.length;
        double[] corrected = new double[m];

        for (int i = 0; i < m; i++) {
            // Holm校正：p_adj = p × (m - i)
            double adjustedP = pValues[i] * (m - i);
            corrected[i] = Math.min(adjustedP, 1.0);
        }

        return corrected;
    }

    /**
     * 批量Bonferroni校正
     *
     * 对多个p值进行Bonferroni校正的便捷方法
     *
     * @param pValues 原始p值数组
     * @return 校正后的p值数组
     */
    public static double[] bonferroniCorrectionBatch(double[] pValues) {
        if (pValues == null || pValues.length == 0) {
            throw new IllegalArgumentException("pValues array cannot be null or empty");
        }

        int m = pValues.length;
        double[] corrected = new double[m];

        for (int i = 0; i < m; i++) {
            corrected[i] = bonferroniCorrection(pValues[i], m);
        }

        return corrected;
    }
}

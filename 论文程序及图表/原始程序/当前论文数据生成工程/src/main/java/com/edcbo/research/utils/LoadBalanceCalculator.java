package com.edcbo.research.utils;

import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.vms.Vm;

import java.util.Arrays;
import java.util.List;

/**
 * 负载均衡计算器 - 用于云任务调度的负载均衡评估
 *
 * 负载均衡指标：
 * - Load Balance Ratio (LBR) = MaxLoad / AvgLoad
 * - Load Standard Deviation (LSD) = StdDev(VM_Loads)
 * - Imbalance Degree (ID) = (MaxLoad - MinLoad) / AvgLoad
 *
 * 参考文献：
 * - Xu, M., Tian, W., & Buyya, R. (2017). A survey on load balancing algorithms
 *   for virtual machines placement in cloud computing.
 *   Concurrency and Computation: Practice and Experience, 29(12), e4123.
 *
 * - Wang, T., Liu, Z., Chen, Y., Xu, Y., & Dai, X. (2014). Load balancing task
 *   scheduling based on genetic algorithm in cloud computing.
 *   In 2014 IEEE 12th International Conference on Dependable, Autonomic and
 *   Secure Computing (pp. 146-152). IEEE.
 *
 * @author LSCBO Research Team
 * @date 2025-12-16
 */
public class LoadBalanceCalculator {

    // ==================== 负载均衡指标计算 ====================

    /**
     * 计算负载均衡比（Load Balance Ratio, LBR）
     * LBR = MaxLoad / AvgLoad
     *
     * 解释：
     * - LBR = 1.0: 完美均衡（所有VM负载相同）
     * - LBR > 1.0: 负载不均衡，值越大表示不均衡程度越高
     * - 例如：LBR = 1.5 表示最大负载是平均负载的1.5倍
     *
     * @param schedule 任务到VM的映射关系（schedule[i]表示任务i分配到哪个VM）
     * @param M 任务数量
     * @param N VM数量
     * @param cloudletList 任务列表
     * @param vmList VM列表
     * @return 负载均衡比（越接近1.0越好）
     */
    public static double calculateLoadBalanceRatio(int[] schedule, int M, int N,
                                                  List<Cloudlet> cloudletList, List<Vm> vmList) {
        double[] vmLoads = calculateVmLoads(schedule, M, N, cloudletList, vmList);

        // 计算平均负载和最大负载
        double sumLoad = 0.0;
        double maxLoad = 0.0;
        int usedVmCount = 0;

        for (int j = 0; j < N; j++) {
            if (vmLoads[j] > 0) {
                sumLoad += vmLoads[j];
                maxLoad = Math.max(maxLoad, vmLoads[j]);
                usedVmCount++;
            }
        }

        if (usedVmCount == 0) return 1.0;  // 无负载时返回完美均衡

        double avgLoad = sumLoad / usedVmCount;

        // 计算负载均衡比
        return avgLoad > 0 ? maxLoad / avgLoad : 1.0;
    }

    /**
     * 计算负载标准差（Load Standard Deviation, LSD）
     * 标准差越小，表示负载分布越均匀
     *
     * @param schedule 任务到VM的映射关系
     * @param M 任务数量
     * @param N VM数量
     * @param cloudletList 任务列表
     * @param vmList VM列表
     * @return 负载标准差（秒）
     */
    public static double calculateLoadStdDev(int[] schedule, int M, int N,
                                            List<Cloudlet> cloudletList, List<Vm> vmList) {
        double[] vmLoads = calculateVmLoads(schedule, M, N, cloudletList, vmList);

        // 计算平均负载
        double sumLoad = 0.0;
        int usedVmCount = 0;

        for (int j = 0; j < N; j++) {
            if (vmLoads[j] > 0) {
                sumLoad += vmLoads[j];
                usedVmCount++;
            }
        }

        if (usedVmCount == 0) return 0.0;

        double avgLoad = sumLoad / usedVmCount;

        // 计算标准差
        double sumSquaredDiff = 0.0;
        for (int j = 0; j < N; j++) {
            if (vmLoads[j] > 0) {
                double diff = vmLoads[j] - avgLoad;
                sumSquaredDiff += diff * diff;
            }
        }

        return Math.sqrt(sumSquaredDiff / usedVmCount);
    }

    /**
     * 计算不平衡度（Imbalance Degree, ID）
     * ID = (MaxLoad - MinLoad) / AvgLoad
     *
     * 解释：
     * - ID = 0.0: 完美均衡
     * - ID > 0.0: 负载不均衡，值越大表示不均衡程度越高
     *
     * @param schedule 任务到VM的映射关系
     * @param M 任务数量
     * @param N VM数量
     * @param cloudletList 任务列表
     * @param vmList VM列表
     * @return 不平衡度（越接近0越好）
     */
    public static double calculateImbalanceDegree(int[] schedule, int M, int N,
                                                 List<Cloudlet> cloudletList, List<Vm> vmList) {
        double[] vmLoads = calculateVmLoads(schedule, M, N, cloudletList, vmList);

        // 计算平均负载、最大负载和最小负载
        double sumLoad = 0.0;
        double maxLoad = 0.0;
        double minLoad = Double.MAX_VALUE;
        int usedVmCount = 0;

        for (int j = 0; j < N; j++) {
            if (vmLoads[j] > 0) {
                sumLoad += vmLoads[j];
                maxLoad = Math.max(maxLoad, vmLoads[j]);
                minLoad = Math.min(minLoad, vmLoads[j]);
                usedVmCount++;
            }
        }

        if (usedVmCount == 0) return 0.0;

        double avgLoad = sumLoad / usedVmCount;

        // 计算不平衡度
        return avgLoad > 0 ? (maxLoad - minLoad) / avgLoad : 0.0;
    }

    /**
     * 计算负载方差（Load Variance）
     * 方差越小，表示负载分布越均匀
     *
     * @param schedule 任务到VM的映射关系
     * @param M 任务数量
     * @param N VM数量
     * @param cloudletList 任务列表
     * @param vmList VM列表
     * @return 负载方差
     */
    public static double calculateLoadVariance(int[] schedule, int M, int N,
                                              List<Cloudlet> cloudletList, List<Vm> vmList) {
        double[] vmLoads = calculateVmLoads(schedule, M, N, cloudletList, vmList);

        // 计算平均负载
        double sumLoad = 0.0;
        int usedVmCount = 0;

        for (int j = 0; j < N; j++) {
            if (vmLoads[j] > 0) {
                sumLoad += vmLoads[j];
                usedVmCount++;
            }
        }

        if (usedVmCount == 0) return 0.0;

        double avgLoad = sumLoad / usedVmCount;

        // 计算方差
        double sumSquaredDiff = 0.0;
        for (int j = 0; j < N; j++) {
            if (vmLoads[j] > 0) {
                double diff = vmLoads[j] - avgLoad;
                sumSquaredDiff += diff * diff;
            }
        }

        return sumSquaredDiff / usedVmCount;
    }

    /**
     * 计算负载均衡指数（Load Balance Index, LBI）
     * LBI = 1 - (StdDev / AvgLoad)
     *
     * 解释：
     * - LBI = 1.0: 完美均衡（标准差为0）
     * - LBI接近1.0: 负载均衡较好
     * - LBI接近0或负数: 负载严重不均衡
     *
     * @param schedule 任务到VM的映射关系
     * @param M 任务数量
     * @param N VM数量
     * @param cloudletList 任务列表
     * @param vmList VM列表
     * @return 负载均衡指数（0-1之间，越接近1越好）
     */
    public static double calculateLoadBalanceIndex(int[] schedule, int M, int N,
                                                  List<Cloudlet> cloudletList, List<Vm> vmList) {
        double[] vmLoads = calculateVmLoads(schedule, M, N, cloudletList, vmList);

        // 计算平均负载
        double sumLoad = 0.0;
        int usedVmCount = 0;

        for (int j = 0; j < N; j++) {
            if (vmLoads[j] > 0) {
                sumLoad += vmLoads[j];
                usedVmCount++;
            }
        }

        if (usedVmCount == 0) return 1.0;

        double avgLoad = sumLoad / usedVmCount;

        // 计算标准差
        double sumSquaredDiff = 0.0;
        for (int j = 0; j < N; j++) {
            if (vmLoads[j] > 0) {
                double diff = vmLoads[j] - avgLoad;
                sumSquaredDiff += diff * diff;
            }
        }

        double stdDev = Math.sqrt(sumSquaredDiff / usedVmCount);

        // 计算负载均衡指数
        return avgLoad > 0 ? 1.0 - (stdDev / avgLoad) : 1.0;
    }

    // ==================== 辅助方法 ====================

    /**
     * 计算每个VM的负载（运行时间）
     *
     * @param schedule 任务到VM的映射关系
     * @param M 任务数量
     * @param N VM数量
     * @param cloudletList 任务列表
     * @param vmList VM列表
     * @return VM负载数组（秒）
     */
    private static double[] calculateVmLoads(int[] schedule, int M, int N,
                                            List<Cloudlet> cloudletList, List<Vm> vmList) {
        double[] vmLoads = new double[N];

        for (int i = 0; i < M; i++) {
            int vmIdx = schedule[i];
            double taskLength = cloudletList.get(i).getLength();  // MI
            double vmMips = vmList.get(vmIdx).getMips();          // MIPS
            double taskRuntime = taskLength / vmMips;             // 秒

            vmLoads[vmIdx] += taskRuntime;
        }

        return vmLoads;
    }

    /**
     * 获取VM负载统计信息
     *
     * @param schedule 任务到VM的映射关系
     * @param M 任务数量
     * @param N VM数量
     * @param cloudletList 任务列表
     * @param vmList VM列表
     * @return LoadStatistics对象
     */
    public static LoadStatistics getLoadStatistics(int[] schedule, int M, int N,
                                                  List<Cloudlet> cloudletList, List<Vm> vmList) {
        double[] vmLoads = calculateVmLoads(schedule, M, N, cloudletList, vmList);

        double sumLoad = 0.0;
        double maxLoad = 0.0;
        double minLoad = Double.MAX_VALUE;
        int usedVmCount = 0;

        for (int j = 0; j < N; j++) {
            if (vmLoads[j] > 0) {
                sumLoad += vmLoads[j];
                maxLoad = Math.max(maxLoad, vmLoads[j]);
                minLoad = Math.min(minLoad, vmLoads[j]);
                usedVmCount++;
            }
        }

        double avgLoad = usedVmCount > 0 ? sumLoad / usedVmCount : 0.0;

        return new LoadStatistics(avgLoad, maxLoad, minLoad, usedVmCount, vmLoads);
    }

    /**
     * 负载统计信息类
     */
    public static class LoadStatistics {
        public final double avgLoad;
        public final double maxLoad;
        public final double minLoad;
        public final int usedVmCount;
        public final double[] vmLoads;

        public LoadStatistics(double avgLoad, double maxLoad, double minLoad,
                            int usedVmCount, double[] vmLoads) {
            this.avgLoad = avgLoad;
            this.maxLoad = maxLoad;
            this.minLoad = minLoad;
            this.usedVmCount = usedVmCount;
            this.vmLoads = vmLoads;
        }

        @Override
        public String toString() {
            return String.format(
                "LoadStatistics{avgLoad=%.2f, maxLoad=%.2f, minLoad=%.2f, usedVmCount=%d}",
                avgLoad, maxLoad, minLoad, usedVmCount
            );
        }
    }

    // ==================== 实用方法 ====================

    /**
     * 计算所有负载均衡指标并返回字符串
     *
     * @param schedule 任务到VM的映射关系
     * @param M 任务数量
     * @param N VM数量
     * @param cloudletList 任务列表
     * @param vmList VM列表
     * @return 所有负载均衡指标的字符串表示
     */
    public static String getAllMetrics(int[] schedule, int M, int N,
                                      List<Cloudlet> cloudletList, List<Vm> vmList) {
        LoadStatistics stats = getLoadStatistics(schedule, M, N, cloudletList, vmList);
        double lbr = calculateLoadBalanceRatio(schedule, M, N, cloudletList, vmList);
        double lsd = calculateLoadStdDev(schedule, M, N, cloudletList, vmList);
        double id = calculateImbalanceDegree(schedule, M, N, cloudletList, vmList);
        double lbi = calculateLoadBalanceIndex(schedule, M, N, cloudletList, vmList);

        return String.format(
            "负载均衡指标:\n" +
            "- 平均负载: %.2f 秒\n" +
            "- 最大负载: %.2f 秒\n" +
            "- 最小负载: %.2f 秒\n" +
            "- 使用的VM数量: %d/%d\n" +
            "- 负载均衡比 (LBR): %.4f (越接近1.0越好)\n" +
            "- 负载标准差 (LSD): %.4f 秒\n" +
            "- 不平衡度 (ID): %.4f (越接近0越好)\n" +
            "- 负载均衡指数 (LBI): %.4f (越接近1.0越好)",
            stats.avgLoad, stats.maxLoad, stats.minLoad, stats.usedVmCount, N,
            lbr, lsd, id, lbi
        );
    }

    /**
     * 判断负载均衡是否良好
     *
     * @param schedule 任务到VM的映射关系
     * @param M 任务数量
     * @param N VM数量
     * @param cloudletList 任务列表
     * @param vmList VM列表
     * @param lbrThreshold 负载均衡比阈值（默认1.2，即最大负载不超过平均负载的1.2倍）
     * @return 如果负载均衡良好返回true
     */
    public static boolean isLoadBalanced(int[] schedule, int M, int N,
                                        List<Cloudlet> cloudletList, List<Vm> vmList,
                                        double lbrThreshold) {
        double lbr = calculateLoadBalanceRatio(schedule, M, N, cloudletList, vmList);
        return lbr <= lbrThreshold;
    }

    /**
     * 判断负载均衡是否良好（使用默认阈值1.2）
     */
    public static boolean isLoadBalanced(int[] schedule, int M, int N,
                                        List<Cloudlet> cloudletList, List<Vm> vmList) {
        return isLoadBalanced(schedule, M, N, cloudletList, vmList, 1.2);
    }

    /**
     * 计算VM利用率（Utilization）
     * Utilization = VM实际运行时间 / Makespan
     *
     * @param schedule 任务到VM的映射关系
     * @param M 任务数量
     * @param N VM数量
     * @param cloudletList 任务列表
     * @param vmList VM列表
     * @param makespan Makespan（秒）
     * @return 平均VM利用率（0-1之间）
     */
    public static double calculateAverageUtilization(int[] schedule, int M, int N,
                                                    List<Cloudlet> cloudletList, List<Vm> vmList,
                                                    double makespan) {
        double[] vmLoads = calculateVmLoads(schedule, M, N, cloudletList, vmList);

        double sumUtilization = 0.0;
        int usedVmCount = 0;

        for (int j = 0; j < N; j++) {
            if (vmLoads[j] > 0) {
                double utilization = makespan > 0 ? vmLoads[j] / makespan : 0.0;
                sumUtilization += utilization;
                usedVmCount++;
            }
        }

        return usedVmCount > 0 ? sumUtilization / usedVmCount : 0.0;
    }
}

package com.edcbo.research.utils;

import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.vms.Vm;

import java.util.List;

/**
 * 成本计算器 - 用于云任务调度的成本评估
 *
 * 成本模型：
 * Cost = Σ(VM_Hourly_Rate × VM_Runtime)
 *
 * 参考文献：
 * - Buyya, R., Yeo, C. S., Venugopal, S., Broberg, J., & Brandic, I. (2009).
 *   Cloud computing and emerging IT platforms: Vision, hype, and reality for
 *   delivering computing as the 5th utility.
 *   Future Generation Computer Systems, 25(6), 599-616.
 *
 * - AWS EC2定价：https://aws.amazon.com/ec2/pricing/
 * - Azure VM定价：https://azure.microsoft.com/en-us/pricing/details/virtual-machines/
 *
 * @author LSCBO Research Team
 * @date 2025-12-14
 */
public class CostCalculator {

    // ==================== 成本模型参数 ====================

    /**
     * VM类型枚举 - 基于AWS EC2实例类型
     */
    public enum VmType {
        /**
         * T3.small: 2 vCPU, 2GB RAM, $0.0208/hour
         * 适合：低负载任务
         */
        T3_SMALL(500, 0.0208),

        /**
         * T3.medium: 2 vCPU, 4GB RAM, $0.0416/hour
         * 适合：中等负载任务
         */
        T3_MEDIUM(750, 0.0416),

        /**
         * M5.large: 2 vCPU, 8GB RAM, $0.096/hour
         * 适合：通用计算任务
         */
        M5_LARGE(1000, 0.096),

        /**
         * M5.xlarge: 4 vCPU, 16GB RAM, $0.192/hour
         * 适合：高性能计算任务
         */
        M5_XLARGE(1250, 0.192),

        /**
         * C5.xlarge: 4 vCPU, 8GB RAM, $0.17/hour
         * 适合：计算密集型任务
         */
        C5_XLARGE(1500, 0.17);

        private final int mips;          // VM处理能力（MIPS）
        private final double hourlyRate; // 小时费率（USD/hour）

        VmType(int mips, double hourlyRate) {
            this.mips = mips;
            this.hourlyRate = hourlyRate;
        }

        public int getMips() {
            return mips;
        }

        public double getHourlyRate() {
            return hourlyRate;
        }

        /**
         * 根据VM的MIPS值推断VM类型
         * @param vmMips VM的MIPS值
         * @return 对应的VmType
         */
        public static VmType inferFromMips(double vmMips) {
            if (vmMips < 625) return T3_SMALL;
            else if (vmMips < 875) return T3_MEDIUM;
            else if (vmMips < 1125) return M5_LARGE;
            else if (vmMips < 1375) return M5_XLARGE;
            else return C5_XLARGE;
        }
    }

    /**
     * 默认VM小时费率（USD/hour）
     * 基于AWS t3.medium实例类型（通用型VM）
     */
    private static final double DEFAULT_HOURLY_RATE = 0.05;  // USD/hour

    /**
     * 数据传输成本（USD/GB）
     * 基于AWS S3数据传输费率
     */
    private static final double DATA_TRANSFER_COST = 0.09;  // USD/GB

    /**
     * 存储成本（USD/GB-month）
     * 基于AWS EBS通用SSD存储费率
     */
    private static final double STORAGE_COST = 0.10;  // USD/GB-month

    // ==================== 成本计算方法 ====================

    /**
     * 计算给定调度方案的总成本（美元）
     * 包括：VM运行成本
     *
     * @param schedule 任务到VM的映射关系（schedule[i]表示任务i分配到哪个VM）
     * @param M 任务数量
     * @param N VM数量
     * @param cloudletList 任务列表
     * @param vmList VM列表
     * @return 总成本（USD）
     */
    public static double calculateCost(int[] schedule, int M, int N,
                                      List<Cloudlet> cloudletList, List<Vm> vmList) {
        // 步骤1：计算每个VM的运行时间
        double[] vmRuntimes = new double[N];  // 每个VM的总运行时间（秒）

        for (int i = 0; i < M; i++) {
            int vmIdx = schedule[i];
            double taskLength = cloudletList.get(i).getLength();  // MI
            double vmMips = vmList.get(vmIdx).getMips();          // MIPS
            double taskRuntime = taskLength / vmMips;             // 秒

            vmRuntimes[vmIdx] += taskRuntime;
        }

        // 步骤2：计算每个VM的成本
        double totalCost = 0.0;  // 总成本（USD）

        for (int j = 0; j < N; j++) {
            if (vmRuntimes[j] > 0) {  // 只计算有任务分配的VM
                // 根据VM的MIPS推断VM类型和小时费率
                double vmMips = vmList.get(j).getMips();
                VmType vmType = VmType.inferFromMips(vmMips);
                double hourlyRate = vmType.getHourlyRate();

                // 计算该VM的成本（USD）
                // Cost = Hourly_Rate × Runtime (hours)
                double vmCost = hourlyRate * vmRuntimes[j] / 3600.0;

                totalCost += vmCost;
            }
        }

        return totalCost;
    }

    /**
     * 计算给定调度方案的总成本（使用统一小时费率）
     * 简化版本，适用于所有VM类型相同的场景
     *
     * @param schedule 任务到VM的映射关系
     * @param M 任务数量
     * @param N VM数量
     * @param cloudletList 任务列表
     * @param vmList VM列表
     * @param hourlyRate 统一小时费率（USD/hour）
     * @return 总成本（USD）
     */
    public static double calculateCostUniform(int[] schedule, int M, int N,
                                             List<Cloudlet> cloudletList, List<Vm> vmList,
                                             double hourlyRate) {
        double[] vmRuntimes = new double[N];

        for (int i = 0; i < M; i++) {
            int vmIdx = schedule[i];
            double taskLength = cloudletList.get(i).getLength();
            double vmMips = vmList.get(vmIdx).getMips();
            vmRuntimes[vmIdx] += taskLength / vmMips;
        }

        double totalCost = 0.0;
        for (int j = 0; j < N; j++) {
            if (vmRuntimes[j] > 0) {
                totalCost += hourlyRate * vmRuntimes[j] / 3600.0;
            }
        }

        return totalCost;
    }

    /**
     * 计算平均VM成本（USD）
     * 用于评估成本分布
     *
     * @param schedule 任务到VM的映射关系
     * @param M 任务数量
     * @param N VM数量
     * @param cloudletList 任务列表
     * @param vmList VM列表
     * @return 平均VM成本（USD）
     */
    public static double calculateAverageVmCost(int[] schedule, int M, int N,
                                               List<Cloudlet> cloudletList, List<Vm> vmList) {
        double[] vmRuntimes = new double[N];
        int usedVmCount = 0;

        for (int i = 0; i < M; i++) {
            int vmIdx = schedule[i];
            double taskLength = cloudletList.get(i).getLength();
            double vmMips = vmList.get(vmIdx).getMips();
            vmRuntimes[vmIdx] += taskLength / vmMips;
        }

        double totalCost = 0.0;
        for (int j = 0; j < N; j++) {
            if (vmRuntimes[j] > 0) {
                double vmMips = vmList.get(j).getMips();
                VmType vmType = VmType.inferFromMips(vmMips);
                double vmCost = vmType.getHourlyRate() * vmRuntimes[j] / 3600.0;
                totalCost += vmCost;
                usedVmCount++;
            }
        }

        return usedVmCount > 0 ? totalCost / usedVmCount : 0.0;
    }

    /**
     * 计算成本标准差（用于评估成本分布均衡性）
     * 标准差越小，表示成本分布越均匀
     *
     * @param schedule 任务到VM的映射关系
     * @param M 任务数量
     * @param N VM数量
     * @param cloudletList 任务列表
     * @param vmList VM列表
     * @return 成本标准差（USD）
     */
    public static double calculateCostStdDev(int[] schedule, int M, int N,
                                            List<Cloudlet> cloudletList, List<Vm> vmList) {
        double[] vmRuntimes = new double[N];
        double[] vmCosts = new double[N];
        int usedVmCount = 0;

        // 计算每个VM的成本
        for (int i = 0; i < M; i++) {
            int vmIdx = schedule[i];
            double taskLength = cloudletList.get(i).getLength();
            double vmMips = vmList.get(vmIdx).getMips();
            vmRuntimes[vmIdx] += taskLength / vmMips;
        }

        double totalCost = 0.0;
        for (int j = 0; j < N; j++) {
            if (vmRuntimes[j] > 0) {
                double vmMips = vmList.get(j).getMips();
                VmType vmType = VmType.inferFromMips(vmMips);
                vmCosts[j] = vmType.getHourlyRate() * vmRuntimes[j] / 3600.0;
                totalCost += vmCosts[j];
                usedVmCount++;
            }
        }

        if (usedVmCount == 0) return 0.0;

        // 计算平均成本
        double avgCost = totalCost / usedVmCount;

        // 计算标准差
        double sumSquaredDiff = 0.0;
        for (int j = 0; j < N; j++) {
            if (vmRuntimes[j] > 0) {
                double diff = vmCosts[j] - avgCost;
                sumSquaredDiff += diff * diff;
            }
        }

        return Math.sqrt(sumSquaredDiff / usedVmCount);
    }

    /**
     * 计算数据传输成本（简化模型）
     * 假设任务输入数据大小与任务长度成正比
     *
     * @param schedule 任务到VM的映射关系
     * @param M 任务数量
     * @param cloudletList 任务列表
     * @param dataSizePerMI 每MI任务对应的数据大小（GB/MI），默认0.0001
     * @return 数据传输成本（USD）
     */
    public static double calculateDataTransferCost(int[] schedule, int M,
                                                  List<Cloudlet> cloudletList,
                                                  double dataSizePerMI) {
        double totalDataSize = 0.0;  // GB

        for (int i = 0; i < M; i++) {
            double taskLength = cloudletList.get(i).getLength();  // MI
            totalDataSize += taskLength * dataSizePerMI;          // GB
        }

        return totalDataSize * DATA_TRANSFER_COST;
    }

    // ==================== 实用方法 ====================

    /**
     * 获取成本模型参数（用于文档记录）
     *
     * @return 参数说明字符串
     */
    public static String getModelParameters() {
        StringBuilder sb = new StringBuilder("成本模型参数:\n");
        sb.append("VM类型费率 (AWS EC2):\n");
        for (VmType type : VmType.values()) {
            sb.append(String.format("  - %s: %d MIPS, $%.4f/hour\n",
                type.name(), type.getMips(), type.getHourlyRate()));
        }
        sb.append(String.format("- 数据传输成本: $%.3f/GB\n", DATA_TRANSFER_COST));
        sb.append(String.format("- 存储成本: $%.2f/GB-month\n", STORAGE_COST));
        return sb.toString();
    }

    /**
     * 估算任务的成本效益比（Cost-Performance Ratio）
     * 值越低表示性价比越高
     *
     * @param schedule 任务到VM的映射关系
     * @param makespan Makespan（秒）
     * @param M 任务数量
     * @param N VM数量
     * @param cloudletList 任务列表
     * @param vmList VM列表
     * @return 成本效益比（USD/s）
     */
    public static double calculateCostPerformanceRatio(int[] schedule, double makespan,
                                                      int M, int N,
                                                      List<Cloudlet> cloudletList,
                                                      List<Vm> vmList) {
        double totalCost = calculateCost(schedule, M, N, cloudletList, vmList);
        return makespan > 0 ? totalCost / makespan : Double.MAX_VALUE;
    }

    /**
     * 计算单位时间成本（USD/second）
     * 用于快速成本估算
     *
     * @param schedule 任务到VM的映射关系
     * @param M 任务数量
     * @param N VM数量
     * @param cloudletList 任务列表
     * @param vmList VM列表
     * @return 单位时间成本（USD/s）
     */
    public static double calculateCostPerSecond(int[] schedule, int M, int N,
                                               List<Cloudlet> cloudletList, List<Vm> vmList) {
        double totalCost = calculateCost(schedule, M, N, cloudletList, vmList);

        // 计算总运行时间
        double[] vmRuntimes = new double[N];
        for (int i = 0; i < M; i++) {
            int vmIdx = schedule[i];
            double taskLength = cloudletList.get(i).getLength();
            double vmMips = vmList.get(vmIdx).getMips();
            vmRuntimes[vmIdx] += taskLength / vmMips;
        }

        double totalRuntime = 0.0;
        for (double runtime : vmRuntimes) {
            totalRuntime += runtime;
        }

        return totalRuntime > 0 ? totalCost / totalRuntime : 0.0;
    }
}

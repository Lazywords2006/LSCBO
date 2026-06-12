package com.edcbo.research.utils;

import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.vms.Vm;

import java.util.List;

/**
 * 能耗计算器 - 用于云任务调度的能耗评估
 *
 * 能耗模型：
 * Energy = Σ(VM_Power × VM_Runtime)
 * VM_Power = Base_Power + Dynamic_Power × CPU_Utilization
 *
 * 参考文献：
 * - Beloglazov, A., & Buyya, R. (2012). Optimal online deterministic
 *   algorithms and adaptive heuristics for energy and performance efficient
 *   dynamic consolidation of virtual machines in Cloud data centers.
 *   Concurrency and Computation: Practice and Experience, 24(13), 1397-1420.
 *
 * @author LSCBO Research Team
 * @date 2025-12-14
 */
public class EnergyCalculator {

    // ==================== 能耗模型参数 ====================

    /**
     * VM基础功率（瓦特）- 空闲状态下的功率消耗
     * 典型值：Intel Xeon服务器空闲功率约100-150W
     */
    private static final double BASE_POWER = 150.0;  // 瓦特

    /**
     * VM动态功率（瓦特）- CPU满载时的额外功率消耗
     * 典型值：Intel Xeon服务器满载时额外功率约150-200W
     */
    private static final double DYNAMIC_POWER = 200.0;  // 瓦特

    /**
     * 功率使用效率（Power Usage Effectiveness, PUE）
     * 典型值：现代数据中心PUE为1.2-1.5，Google数据中心可达1.12
     */
    private static final double PUE = 1.3;  // 数据中心级别效率

    // ==================== 能耗计算方法 ====================

    /**
     * 计算给定调度方案的总能耗（千瓦时，kWh）
     *
     * @param schedule 任务到VM的映射关系（schedule[i]表示任务i分配到哪个VM）
     * @param M 任务数量
     * @param N VM数量
     * @param cloudletList 任务列表
     * @param vmList VM列表
     * @return 总能耗（kWh）
     */
    public static double calculateEnergy(int[] schedule, int M, int N,
                                        List<Cloudlet> cloudletList, List<Vm> vmList) {
        // 步骤1：计算每个VM的运行时间和CPU利用率
        double[] vmRuntimes = new double[N];  // 每个VM的总运行时间（秒）
        int[] vmTaskCounts = new int[N];      // 每个VM分配的任务数量

        for (int i = 0; i < M; i++) {
            int vmIdx = schedule[i];
            double taskLength = cloudletList.get(i).getLength();  // MI（Million Instructions）
            double vmMips = vmList.get(vmIdx).getMips();          // MIPS
            double taskRuntime = taskLength / vmMips;             // 秒

            vmRuntimes[vmIdx] += taskRuntime;
            vmTaskCounts[vmIdx]++;
        }

        // 步骤2：计算每个VM的能耗
        double totalEnergy = 0.0;  // 总能耗（kWh）

        for (int j = 0; j < N; j++) {
            if (vmRuntimes[j] > 0) {  // 只计算有任务分配的VM
                // 计算CPU利用率（简化模型：假设任务执行期间CPU利用率为100%）
                double cpuUtilization = 1.0;

                // 计算VM功率（瓦特）
                // VM_Power = Base_Power + Dynamic_Power × CPU_Utilization
                double vmPower = BASE_POWER + DYNAMIC_POWER * cpuUtilization;

                // 考虑PUE（数据中心效率）
                double effectivePower = vmPower * PUE;

                // 计算该VM的能耗（kWh）
                // Energy = Power (W) × Time (hours) / 1000
                double vmEnergy = effectivePower * vmRuntimes[j] / 3600.0 / 1000.0;

                totalEnergy += vmEnergy;
            }
        }

        return totalEnergy;
    }

    /**
     * 计算给定调度方案的平均VM能耗（kWh）
     * 用于评估负载均衡对能耗的影响
     *
     * @param schedule 任务到VM的映射关系
     * @param M 任务数量
     * @param N VM数量
     * @param cloudletList 任务列表
     * @param vmList VM列表
     * @return 平均VM能耗（kWh）
     */
    public static double calculateAverageVmEnergy(int[] schedule, int M, int N,
                                                 List<Cloudlet> cloudletList, List<Vm> vmList) {
        double[] vmRuntimes = new double[N];
        int usedVmCount = 0;

        for (int i = 0; i < M; i++) {
            int vmIdx = schedule[i];
            double taskLength = cloudletList.get(i).getLength();
            double vmMips = vmList.get(vmIdx).getMips();
            vmRuntimes[vmIdx] += taskLength / vmMips;
        }

        double totalEnergy = 0.0;
        for (int j = 0; j < N; j++) {
            if (vmRuntimes[j] > 0) {
                double vmPower = (BASE_POWER + DYNAMIC_POWER) * PUE;
                double vmEnergy = vmPower * vmRuntimes[j] / 3600.0 / 1000.0;
                totalEnergy += vmEnergy;
                usedVmCount++;
            }
        }

        return usedVmCount > 0 ? totalEnergy / usedVmCount : 0.0;
    }

    /**
     * 计算能耗标准差（用于评估负载均衡）
     * 标准差越小，表示负载越均衡，能耗分布越均匀
     *
     * @param schedule 任务到VM的映射关系
     * @param M 任务数量
     * @param N VM数量
     * @param cloudletList 任务列表
     * @param vmList VM列表
     * @return 能耗标准差（kWh）
     */
    public static double calculateEnergyStdDev(int[] schedule, int M, int N,
                                              List<Cloudlet> cloudletList, List<Vm> vmList) {
        double[] vmRuntimes = new double[N];
        double[] vmEnergies = new double[N];
        int usedVmCount = 0;

        // 计算每个VM的能耗
        for (int i = 0; i < M; i++) {
            int vmIdx = schedule[i];
            double taskLength = cloudletList.get(i).getLength();
            double vmMips = vmList.get(vmIdx).getMips();
            vmRuntimes[vmIdx] += taskLength / vmMips;
        }

        double totalEnergy = 0.0;
        for (int j = 0; j < N; j++) {
            if (vmRuntimes[j] > 0) {
                double vmPower = (BASE_POWER + DYNAMIC_POWER) * PUE;
                vmEnergies[j] = vmPower * vmRuntimes[j] / 3600.0 / 1000.0;
                totalEnergy += vmEnergies[j];
                usedVmCount++;
            }
        }

        if (usedVmCount == 0) return 0.0;

        // 计算平均能耗
        double avgEnergy = totalEnergy / usedVmCount;

        // 计算标准差
        double sumSquaredDiff = 0.0;
        for (int j = 0; j < N; j++) {
            if (vmRuntimes[j] > 0) {
                double diff = vmEnergies[j] - avgEnergy;
                sumSquaredDiff += diff * diff;
            }
        }

        return Math.sqrt(sumSquaredDiff / usedVmCount);
    }

    // ==================== 实用方法 ====================

    /**
     * 获取能耗模型参数（用于文档记录）
     *
     * @return 参数说明字符串
     */
    public static String getModelParameters() {
        return String.format(
            "能耗模型参数:\n" +
            "- 基础功率: %.1f W\n" +
            "- 动态功率: %.1f W\n" +
            "- PUE系数: %.2f\n" +
            "- 典型VM功率: %.1f W (空闲) - %.1f W (满载)",
            BASE_POWER,
            DYNAMIC_POWER,
            PUE,
            BASE_POWER * PUE,
            (BASE_POWER + DYNAMIC_POWER) * PUE
        );
    }

    /**
     * 将能耗转换为CO2排放量（千克）
     * 假设电网碳强度：0.5 kg CO2/kWh（中国电网典型值）
     *
     * @param energyKwh 能耗（kWh）
     * @return CO2排放量（kg）
     */
    public static double energyToCo2(double energyKwh) {
        final double CARBON_INTENSITY = 0.5;  // kg CO2/kWh
        return energyKwh * CARBON_INTENSITY;
    }

    /**
     * 将能耗转换为成本（美元）
     * 假设电价：0.10 USD/kWh（美国商业电价典型值）
     *
     * @param energyKwh 能耗（kWh）
     * @return 电费成本（USD）
     */
    public static double energyToElectricityCost(double energyKwh) {
        final double ELECTRICITY_PRICE = 0.10;  // USD/kWh
        return energyKwh * ELECTRICITY_PRICE;
    }
}

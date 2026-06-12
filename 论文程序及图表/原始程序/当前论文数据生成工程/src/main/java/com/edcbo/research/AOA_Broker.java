package com.edcbo.research;

import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.vms.Vm;
import com.edcbo.research.utils.ConvergenceRecord;

import java.util.*;

/**
 * AOA (Arithmetic Optimization Algorithm) Broker
 *
 * 基于Arithmetic Optimization Algorithm的云任务调度器
 * 参考文献: Abualigah et al. (2021) "The arithmetic optimization algorithm"
 *          Computer Methods in Applied Mechanics and Engineering, 376, 113609.
 *          引用量: 1000+ (截至2024)
 *
 * 算法灵感: 数学中的算术运算符（加减乘除）
 *
 * 核心机制:
 * 1. Math Optimizer Accelerated (MOA): 控制探索-开发切换
 *    MOA(t) = MOA_Min + t × ((MOA_Max - MOA_Min) / T_max)
 *
 * 2. Math Optimizer Probability (MOP): 控制运算符选择
 *    MOP(t) = 1 - (t^(1/α) / T_max^(1/α))
 *
 * 3. 四种算术运算符:
 *    - Division (÷): 探索阶段，大步长搜索
 *    - Multiplication (×): 探索阶段，中等步长搜索
 *    - Subtraction (−): 开发阶段，向最优解靠近
 *    - Addition (+): 开发阶段，向最优解收敛
 *
 * 关键参数:
 * - MOA_Min = 0.2: 最小数学优化加速度
 * - MOA_Max = 1.0: 最大数学优化加速度
 * - α = 5.0: 敏感参数，控制开发阶段的强度
 * - μ = 0.5: 控制系数
 *
 * @author EDCBO Research Team
 * @date 2025-12-14
 * @version 1.0
 */
public class AOA_Broker extends DatacenterBrokerSimple {

    // ==================== 算法参数 ====================
    protected static final int POPULATION_SIZE = 30;      // 种群大小
    protected static final int MAX_ITERATIONS = 100;      // 最大迭代次数

    // AOA特有参数
    private static final double MOA_MIN = 0.2;            // Math Optimizer Accelerated最小值
    private static final double MOA_MAX = 1.0;            // Math Optimizer Accelerated最大值
    private static final double ALPHA = 5.0;              // 敏感参数
    private static final double MU = 0.5;                 // 控制系数

    // ==================== 内部状态 ====================
    private double[][] population;                        // 种群（连续空间[0,1]）
    private double[] bestSolution;                        // 全局最优解
    private double bestFitness;                           // 全局最优适应度
    private Random random;                                // 随机数生成器
    private ConvergenceRecord convergenceRecord;          // 收敛记录器
    private Map<Long, Vm> cloudletVmMapping;              // Cloudlet到VM的映射
    private boolean schedulingDone = false;               // 调度是否完成

    // ==================== 构造函数 ====================

    public AOA_Broker(CloudSimPlus simulation) {
        super(simulation);
        this.random = new Random();
        this.convergenceRecord = new ConvergenceRecord("AOA", "unknown", System.currentTimeMillis());
        this.cloudletVmMapping = new HashMap<>();
    }

    public AOA_Broker(CloudSimPlus simulation, long seed) {
        super(simulation);
        this.random = new Random(seed);
        this.convergenceRecord = new ConvergenceRecord("AOA", "unknown", seed);
        this.cloudletVmMapping = new HashMap<>();
    }

    public AOA_Broker(CloudSimPlus simulation, long seed, String scale) {
        super(simulation);
        this.random = new Random(seed);
        this.convergenceRecord = new ConvergenceRecord("AOA", scale, seed);
        this.cloudletVmMapping = new HashMap<>();
    }

    public AOA_Broker(CloudSimPlus simulation, long seed, ConvergenceRecord record) {
        super(simulation);
        this.random = new Random(seed);
        this.convergenceRecord = record;
        this.cloudletVmMapping = new HashMap<>();
    }

    // ==================== 主算法流程 ====================

    @Override
    protected Vm defaultVmMapper(Cloudlet cloudlet) {
        if (!schedulingDone) {
            runAOA();
            schedulingDone = true;
        }
        return cloudletVmMapping.getOrDefault(cloudlet.getId(), super.defaultVmMapper(cloudlet));
    }

    private void runAOA() {
        List<Cloudlet> cloudletList = new ArrayList<>(getCloudletWaitingList());
        List<Vm> vmList = new ArrayList<>(getVmCreatedList());

        int M = cloudletList.size();
        int N = vmList.size();

        System.out.println("\n========== AOA调度算法启动 ==========");
        System.out.println("等待任务数: " + M);
        System.out.println("已创建VM数: " + N);
        System.out.println("种群大小: " + POPULATION_SIZE);
        System.out.println("最大迭代: " + MAX_ITERATIONS);
        System.out.println("=====================================\n");

        if (M == 0 || N == 0) {
            System.out.println("⚠️ 任务或VM数量为0，算法退出");
            return;
        }

        // 初始化种群（随机）
        population = new double[POPULATION_SIZE][M];
        for (int i = 0; i < POPULATION_SIZE; i++) {
            for (int d = 0; d < M; d++) {
                population[i][d] = random.nextDouble();
            }
        }

        // 评估初始种群
        bestFitness = Double.MAX_VALUE;
        bestSolution = new double[M];
        for (int i = 0; i < POPULATION_SIZE; i++) {
            double fitness = calculateFitness(population[i], M, N, cloudletList, vmList);
            if (fitness < bestFitness) {
                bestFitness = fitness;
                System.arraycopy(population[i], 0, bestSolution, 0, M);
            }
        }

        convergenceRecord.recordIteration(0, bestFitness);

        // 主循环
        for (int t = 1; t <= MAX_ITERATIONS; t++) {
            // 计算MOA (Math Optimizer Accelerated) - 线性增长
            double MOA = MOA_MIN + (double) t * ((MOA_MAX - MOA_MIN) / MAX_ITERATIONS);

            // 计算MOP (Math Optimizer Probability) - 非线性衰减
            double MOP = 1.0 - Math.pow((double) t / MAX_ITERATIONS, 1.0 / ALPHA);

            for (int i = 0; i < POPULATION_SIZE; i++) {
                double[] newPosition = new double[M];

                for (int d = 0; d < M; d++) {
                    double r1 = random.nextDouble();

                    if (r1 > MOA) {
                        // ===== 探索阶段 (r1 > MOA) =====
                        double r2 = random.nextDouble();

                        if (r2 > 0.5) {
                            // ===== Division Operator (÷): 大步长探索 =====
                            int r3_idx = random.nextInt(POPULATION_SIZE);
                            double r4 = random.nextDouble();

                            // 避免除以零
                            double epsilon = 1e-6;
                            double divisor = MOP * population[r3_idx][d] + epsilon;

                            newPosition[d] = bestSolution[d] / (divisor + epsilon);
                            newPosition[d] = clamp(newPosition[d], 0, 1);

                        } else {
                            // ===== Multiplication Operator (×): 中等步长探索 =====
                            int r3_idx = random.nextInt(POPULATION_SIZE);

                            newPosition[d] = bestSolution[d] * MOP * population[r3_idx][d];
                            newPosition[d] = clamp(newPosition[d], 0, 1);
                        }

                    } else {
                        // ===== 开发阶段 (r1 ≤ MOA) =====
                        double r2 = random.nextDouble();

                        if (r2 > 0.5) {
                            // ===== Subtraction Operator (−): 向最优解靠近 =====
                            int r3_idx = random.nextInt(POPULATION_SIZE);

                            newPosition[d] = bestSolution[d] - MOP * population[r3_idx][d];
                            newPosition[d] = clamp(newPosition[d], 0, 1);

                        } else {
                            // ===== Addition Operator (+): 向最优解收敛 =====
                            int r3_idx = random.nextInt(POPULATION_SIZE);

                            newPosition[d] = bestSolution[d] + MOP * population[r3_idx][d];
                            newPosition[d] = clamp(newPosition[d], 0, 1);
                        }
                    }
                }

                // 评估新解
                double newFitness = calculateFitness(newPosition, M, N, cloudletList, vmList);
                double oldFitness = calculateFitness(population[i], M, N, cloudletList, vmList);

                if (newFitness < oldFitness) {
                    System.arraycopy(newPosition, 0, population[i], 0, M);

                    if (newFitness < bestFitness) {
                        bestFitness = newFitness;
                        System.arraycopy(newPosition, 0, bestSolution, 0, M);
                    }
                }
            }

            convergenceRecord.recordIteration(t, bestFitness);
        }

        // 应用最优解
        applySchedule(bestSolution, M, N, cloudletList, vmList);

        System.out.println("\n========== AOA调度算法完成 ==========");
        System.out.println("最优Makespan: " + String.format("%.4f", bestFitness));
        System.out.println("映射条目数: " + cloudletVmMapping.size());
        System.out.println("=====================================\n");
    }

    // ==================== 辅助方法 ====================

    /**
     * 边界约束
     */
    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * 计算适应度（Makespan）
     */
    private double calculateFitness(double[] individual, int M, int N,
                                   List<Cloudlet> cloudletList, List<Vm> vmList) {
        int[] schedule = continuousToDiscrete(individual, N);

        double[] vmLoads = new double[N];
        for (int i = 0; i < M; i++) {
            int vmIdx = schedule[i];
            double taskLength = cloudletList.get(i).getLength();
            double vmMips = vmList.get(vmIdx).getMips();
            vmLoads[vmIdx] += taskLength / vmMips;
        }

        return Arrays.stream(vmLoads).max().getAsDouble();
    }

    /**
     * 连续空间到离散空间的映射
     */
    private int[] continuousToDiscrete(double[] continuous, int N) {
        int[] discrete = new int[continuous.length];
        for (int i = 0; i < continuous.length; i++) {
            discrete[i] = (int) (continuous[i] * N);
            if (discrete[i] >= N) {
                discrete[i] = N - 1;
            }
        }
        return discrete;
    }

    /**
     * 应用调度方案
     */
    private void applySchedule(double[] solution, int M, int N,
                              List<Cloudlet> cloudletList, List<Vm> vmList) {
        int[] schedule = continuousToDiscrete(solution, N);

        for (int i = 0; i < M; i++) {
            Cloudlet cloudlet = cloudletList.get(i);
            Vm vm = vmList.get(schedule[i]);
            cloudletVmMapping.put(cloudlet.getId(), vm);
        }
    }

    public ConvergenceRecord getConvergenceRecord() {
        return convergenceRecord;
    }

    public String getAlgorithmName() {
        return "AOA";
    }

    /**
     * 获取算法内部计算的最优Makespan
     * 此值绕过CloudSim Plus 8.0.0的buggy getFinishTime()方法
     *
     * @return 内部最优适应度（Makespan，单位：秒）
     */
    public double getInternalMakespan() {
        return bestFitness;
    }
}

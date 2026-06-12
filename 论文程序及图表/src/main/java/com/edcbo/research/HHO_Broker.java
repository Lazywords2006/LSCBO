package com.edcbo.research;

import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.vms.Vm;
import com.edcbo.research.utils.ConvergenceRecord;
import org.apache.commons.math3.special.Gamma;

import java.util.*;

/**
 * HHO (Harris Hawks Optimization) Broker
 *
 * 基于Harris Hawks Optimization算法的云任务调度器
 * 参考文献: Heidari et al. (2019) "Harris hawks optimization: Algorithm and applications"
 *          Future Generation Computer Systems, 97, 849-872.
 *          引用量: 4000+ (截至2024)
 *
 * 算法灵感: Harris鹰的协作狩猎行为
 *
 * 核心机制:
 * 1. 探索阶段 (|E| ≥ 1):
 *    - 策略1: 基于族群其他成员位置
 *    - 策略2: 基于随机高树位置
 *
 * 2. 开发阶段 (|E| < 1): 四种围捕策略
 *    - 软包围 (|E| ≥ 0.5, r < 0.5): 渐进靠近
 *    - 硬包围 (|E| < 0.5, r < 0.5): 快速攻击
 *    - 软包围+快速俯冲 (|E| ≥ 0.5, r ≥ 0.5): Lévy飞行增强
 *    - 硬包围+快速俯冲 (|E| < 0.5, r ≥ 0.5): Lévy飞行增强
 *
 * 关键参数:
 * - E: 逃逸能量 (Escaping Energy), 随迭代线性衰减
 * - J: 猎物跳跃强度 (Jump Strength)
 * - r: 围捕策略选择概率
 *
 * @author EDCBO Research Team
 * @date 2025-12-14
 * @version 1.0
 */
public class HHO_Broker extends DatacenterBrokerSimple {

    // ==================== 算法参数 ====================
    protected static final int POPULATION_SIZE = 30;      // 种群大小（Harris鹰数量）
    protected static final int MAX_ITERATIONS = 100;      // 最大迭代次数

    // HHO特有参数
    private static final double LEVY_BETA = 1.5;          // Lévy飞行参数

    // ==================== 内部状态 ====================
    private double[][] population;                        // 种群（连续空间[0,1]）
    private double[] bestSolution;                        // 全局最优解（兔子位置）
    private double bestFitness;                           // 全局最优适应度
    private Random random;                                // 随机数生成器
    private ConvergenceRecord convergenceRecord;          // 收敛记录器
    private Map<Long, Vm> cloudletVmMapping;              // Cloudlet到VM的映射
    private boolean schedulingDone = false;               // 调度是否完成

    // Lévy飞行相关
    private double levySigmaU;                            // σ_u 预计算值

    // ==================== 构造函数 ====================

    public HHO_Broker(CloudSimPlus simulation) {
        super(simulation);
        this.random = new Random();
        this.convergenceRecord = new ConvergenceRecord("HHO", "unknown", System.currentTimeMillis());
        this.cloudletVmMapping = new HashMap<>();
        calculateLevySigmaU();
    }

    public HHO_Broker(CloudSimPlus simulation, long seed) {
        super(simulation);
        this.random = new Random(seed);
        this.convergenceRecord = new ConvergenceRecord("HHO", "unknown", seed);
        this.cloudletVmMapping = new HashMap<>();
        calculateLevySigmaU();
    }

    public HHO_Broker(CloudSimPlus simulation, long seed, String scale) {
        super(simulation);
        this.random = new Random(seed);
        this.convergenceRecord = new ConvergenceRecord("HHO", scale, seed);
        this.cloudletVmMapping = new HashMap<>();
        calculateLevySigmaU();
    }

    public HHO_Broker(CloudSimPlus simulation, long seed, ConvergenceRecord record) {
        super(simulation);
        this.random = new Random(seed);
        this.convergenceRecord = record;
        this.cloudletVmMapping = new HashMap<>();
        calculateLevySigmaU();
    }

    // ==================== 主算法流程 ====================

    @Override
    protected Vm defaultVmMapper(Cloudlet cloudlet) {
        if (!schedulingDone) {
            runHHO();
            schedulingDone = true;
        }
        return cloudletVmMapping.getOrDefault(cloudlet.getId(), super.defaultVmMapper(cloudlet));
    }

    private void runHHO() {
        List<Cloudlet> cloudletList = new ArrayList<>(getCloudletWaitingList());
        List<Vm> vmList = new ArrayList<>(getVmCreatedList());

        int M = cloudletList.size();
        int N = vmList.size();

        System.out.println("\n========== HHO调度算法启动 ==========");
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

        // 评估初始种群，找到兔子位置
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
            // 计算逃逸能量 E (线性衰减从2到0)
            double E0 = 2.0 * random.nextDouble() - 1.0;  // [-1, 1]
            double E = 2.0 * E0 * (1.0 - (double) t / MAX_ITERATIONS);

            for (int i = 0; i < POPULATION_SIZE; i++) {
                double[] newPosition = new double[M];

                if (Math.abs(E) >= 1.0) {
                    // ===== 探索阶段 (|E| ≥ 1) =====
                    double q = random.nextDouble();

                    if (q >= 0.5) {
                        // 策略1: 基于族群其他随机成员位置
                        int r1 = random.nextInt(POPULATION_SIZE);
                        int r2 = random.nextInt(POPULATION_SIZE);
                        for (int d = 0; d < M; d++) {
                            double r3 = random.nextDouble();
                            double r4 = random.nextDouble();
                            newPosition[d] = population[r1][d] - r3 * Math.abs(population[r1][d] - 2.0 * r4 * population[i][d]);
                            newPosition[d] = clamp(newPosition[d], 0, 1);
                        }
                    } else {
                        // 策略2: 基于兔子位置和族群平均位置（高树位置）
                        for (int d = 0; d < M; d++) {
                            double r1 = random.nextDouble();
                            double r2 = random.nextDouble();
                            // 计算族群平均位置（高树）
                            double avgPos = 0.0;
                            for (int j = 0; j < POPULATION_SIZE; j++) {
                                avgPos += population[j][d];
                            }
                            avgPos /= POPULATION_SIZE;

                            newPosition[d] = (bestSolution[d] - avgPos) - r1 * (0.1 + 2.0 * r2) * avgPos;
                            newPosition[d] = clamp(newPosition[d], 0, 1);
                        }
                    }

                } else {
                    // ===== 开发阶段 (|E| < 1) =====
                    double r = random.nextDouble();  // 围捕策略选择概率

                    if (r >= 0.5 && Math.abs(E) >= 0.5) {
                        // ===== 软包围 (Soft besiege) =====
                        for (int d = 0; d < M; d++) {
                            double deltaE = bestSolution[d] - population[i][d];
                            double J = 2.0 * (1.0 - random.nextDouble());  // 跳跃强度
                            newPosition[d] = deltaE - E * Math.abs(J * bestSolution[d] - population[i][d]);
                            newPosition[d] = clamp(newPosition[d], 0, 1);
                        }

                    } else if (r >= 0.5 && Math.abs(E) < 0.5) {
                        // ===== 硬包围 (Hard besiege) =====
                        for (int d = 0; d < M; d++) {
                            newPosition[d] = bestSolution[d] - E * Math.abs(bestSolution[d] - population[i][d]);
                            newPosition[d] = clamp(newPosition[d], 0, 1);
                        }

                    } else if (r < 0.5 && Math.abs(E) >= 0.5) {
                        // ===== 软包围 + 快速俯冲 (Soft besiege with progressive rapid dives) =====
                        // 第一次尝试：软包围
                        double[] Y = new double[M];
                        for (int d = 0; d < M; d++) {
                            double deltaE = bestSolution[d] - population[i][d];
                            double J = 2.0 * (1.0 - random.nextDouble());
                            Y[d] = bestSolution[d] - E * Math.abs(J * bestSolution[d] - population[i][d]);
                            Y[d] = clamp(Y[d], 0, 1);
                        }

                        // 评估Y
                        double fitnessY = calculateFitness(Y, M, N, cloudletList, vmList);

                        if (fitnessY < calculateFitness(population[i], M, N, cloudletList, vmList)) {
                            // Y更优，采用Y
                            System.arraycopy(Y, 0, newPosition, 0, M);
                        } else {
                            // Y不优，尝试Lévy飞行增强的快速俯冲
                            double[] Z = new double[M];
                            double[] levySteps = generateLevyFlight(M);
                            for (int d = 0; d < M; d++) {
                                double S = random.nextDouble();
                                Z[d] = Y[d] + S * levySteps[d];
                                Z[d] = clamp(Z[d], 0, 1);
                            }

                            // 评估Z
                            double fitnessZ = calculateFitness(Z, M, N, cloudletList, vmList);

                            if (fitnessZ < calculateFitness(population[i], M, N, cloudletList, vmList)) {
                                System.arraycopy(Z, 0, newPosition, 0, M);
                            } else {
                                System.arraycopy(population[i], 0, newPosition, 0, M);
                            }
                        }

                    } else {
                        // ===== 硬包围 + 快速俯冲 (Hard besiege with progressive rapid dives) =====
                        // 第一次尝试：硬包围
                        double[] Y = new double[M];
                        for (int d = 0; d < M; d++) {
                            double J = 2.0 * (1.0 - random.nextDouble());
                            Y[d] = bestSolution[d] - E * Math.abs(J * bestSolution[d] - calculateAvgPosition(d));
                            Y[d] = clamp(Y[d], 0, 1);
                        }

                        // 评估Y
                        double fitnessY = calculateFitness(Y, M, N, cloudletList, vmList);

                        if (fitnessY < calculateFitness(population[i], M, N, cloudletList, vmList)) {
                            System.arraycopy(Y, 0, newPosition, 0, M);
                        } else {
                            // 尝试Lévy飞行增强的快速俯冲
                            double[] Z = new double[M];
                            double[] levySteps = generateLevyFlight(M);
                            for (int d = 0; d < M; d++) {
                                double S = random.nextDouble();
                                Z[d] = Y[d] + S * levySteps[d];
                                Z[d] = clamp(Z[d], 0, 1);
                            }

                            double fitnessZ = calculateFitness(Z, M, N, cloudletList, vmList);

                            if (fitnessZ < calculateFitness(population[i], M, N, cloudletList, vmList)) {
                                System.arraycopy(Z, 0, newPosition, 0, M);
                            } else {
                                System.arraycopy(population[i], 0, newPosition, 0, M);
                            }
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

        System.out.println("\n========== HHO调度算法完成 ==========");
        System.out.println("最优Makespan: " + String.format("%.4f", bestFitness));
        System.out.println("映射条目数: " + cloudletVmMapping.size());
        System.out.println("=====================================\n");
    }

    // ==================== 辅助方法 ====================

    /**
     * 计算族群在维度d的平均位置
     */
    private double calculateAvgPosition(int d) {
        double sum = 0.0;
        for (int i = 0; i < POPULATION_SIZE; i++) {
            sum += population[i][d];
        }
        return sum / POPULATION_SIZE;
    }

    /**
     * 计算Lévy飞行分布的σ_u参数（Mantegna方法）
     *
     * 理论基础：
     * - Mantegna, R. N. (1994). Fast, accurate algorithm for numerical
     *   simulation of Lévy stable stochastic processes.
     *   Physical Review E, 49(5), 4677-4683.
     *
     * 公式：σ_u = [Γ(1+λ)sin(πλ/2) / (Γ((1+λ)/2) × λ × 2^((λ-1)/2))]^(1/λ)
     *
     * 使用Apache Commons Math 3.6.1的Gamma函数替代Stirling近似，
     * 提供更高的数值精度。
     */
    private void calculateLevySigmaU() {
        double lambda = LEVY_BETA;
        double numerator = Gamma.gamma(1 + lambda) * Math.sin(Math.PI * lambda / 2.0);
        double denominator = Gamma.gamma((1 + lambda) / 2.0) * lambda * Math.pow(2, (lambda - 1) / 2.0);
        this.levySigmaU = Math.pow(numerator / denominator, 1.0 / lambda);
    }

    /**
     * 生成Lévy飞行步长向量（Mantegna算法）
     */
    private double[] generateLevyFlight(int dim) {
        double[] levy = new double[dim];

        for (int d = 0; d < dim; d++) {
            double u = random.nextGaussian() * levySigmaU;
            double v = random.nextGaussian();
            levy[d] = u / Math.pow(Math.abs(v) + 1e-10, 1.0 / LEVY_BETA);
        }

        return levy;
    }

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
        return "HHO";
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

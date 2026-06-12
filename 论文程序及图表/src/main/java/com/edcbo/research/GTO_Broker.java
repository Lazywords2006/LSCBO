package com.edcbo.research;

import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.vms.Vm;
import com.edcbo.research.utils.ConvergenceRecord;

import java.util.*;

/**
 * GTO (Gorilla Troops Optimizer) Broker
 *
 * 基于Gorilla Troops Optimizer的云任务调度器
 * 参考文献: Abdollahzadeh et al. (2021) "Artificial gorilla troops optimizer: A new nature‐inspired metaheuristic algorithm for global optimization problems"
 *          International Journal of Intelligent Systems, 36(10), 5887-5958.
 *          引用量: 500+ (截至2024)
 *
 * 算法灵感: 大猩猩群体的社会智能和群居行为
 *
 * 核心机制:
 * 1. 探索阶段 (p < p_threshold):
 *    - GX运算符: 模拟大猩猩四处移动和迁移行为
 *    - 基于银背大猩猩（Silverback）的领导
 *
 * 2. 开发阶段 (p ≥ p_threshold): 三种策略
 *    - 跟随银背 (C < W): 跟随领导者
 *    - 跟随成年雌性 (C ≥ W): 竞争机制
 *    - 成年雄性竞争: 争夺领导权
 *
 * 关键参数:
 * - p: 控制参数，p = (cos(2r1) + 1) / 2，r1 ~ [-π, π]
 * - β: 自适应参数，β = β0 * (1 - t / T_max)
 * - β0 = 3.0: 初始值
 * - L: [-1, 1]的随机数向量
 * - W: 常数，通常设为0.8
 *
 * 群体结构:
 * - Silverback (银背): 群体领导者（最优解）
 * - Adult females (成年雌性): 高质量解
 * - Adult males (成年雄性): 其他解
 *
 * @author EDCBO Research Team
 * @date 2025-12-14
 * @version 1.0
 */
public class GTO_Broker extends DatacenterBrokerSimple {

    // ==================== 算法参数 ====================
    protected static final int POPULATION_SIZE = 30;      // 种群大小（大猩猩数量）
    protected static final int MAX_ITERATIONS = 100;      // 最大迭代次数

    // GTO特有参数
    private static final double BETA_INITIAL = 3.0;       // β初始值
    private static final double W = 0.8;                  // 权重常数
    private static final double P_THRESHOLD = 0.5;        // 探索-开发切换阈值

    // ==================== 内部状态 ====================
    private double[][] population;                        // 种群（连续空间[0,1]）
    private double[] bestSolution;                        // 全局最优解（Silverback位置）
    private double bestFitness;                           // 全局最优适应度
    private Random random;                                // 随机数生成器
    private ConvergenceRecord convergenceRecord;          // 收敛记录器
    private Map<Long, Vm> cloudletVmMapping;              // Cloudlet到VM的映射
    private boolean schedulingDone = false;               // 调度是否完成

    // ==================== 构造函数 ====================

    public GTO_Broker(CloudSimPlus simulation) {
        super(simulation);
        this.random = new Random();
        this.convergenceRecord = new ConvergenceRecord("GTO", "unknown", System.currentTimeMillis());
        this.cloudletVmMapping = new HashMap<>();
    }

    public GTO_Broker(CloudSimPlus simulation, long seed) {
        super(simulation);
        this.random = new Random(seed);
        this.convergenceRecord = new ConvergenceRecord("GTO", "unknown", seed);
        this.cloudletVmMapping = new HashMap<>();
    }

    public GTO_Broker(CloudSimPlus simulation, long seed, String scale) {
        super(simulation);
        this.random = new Random(seed);
        this.convergenceRecord = new ConvergenceRecord("GTO", scale, seed);
        this.cloudletVmMapping = new HashMap<>();
    }

    public GTO_Broker(CloudSimPlus simulation, long seed, ConvergenceRecord record) {
        super(simulation);
        this.random = new Random(seed);
        this.convergenceRecord = record;
        this.cloudletVmMapping = new HashMap<>();
    }

    // ==================== 主算法流程 ====================

    @Override
    protected Vm defaultVmMapper(Cloudlet cloudlet) {
        if (!schedulingDone) {
            runGTO();
            schedulingDone = true;
        }
        return cloudletVmMapping.getOrDefault(cloudlet.getId(), super.defaultVmMapper(cloudlet));
    }

    private void runGTO() {
        List<Cloudlet> cloudletList = new ArrayList<>(getCloudletWaitingList());
        List<Vm> vmList = new ArrayList<>(getVmCreatedList());

        int M = cloudletList.size();
        int N = vmList.size();

        System.out.println("\n========== GTO调度算法启动 ==========");
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

        // 评估初始种群，找到Silverback（最优解）
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
            // 计算自适应参数β (线性衰减)
            double beta = BETA_INITIAL * (1.0 - (double) t / MAX_ITERATIONS);

            // 计算控制参数p
            double r1 = (random.nextDouble() * 2.0 - 1.0) * Math.PI;  // [-π, π]
            double p = (Math.cos(2.0 * r1) + 1.0) / 2.0;

            for (int i = 0; i < POPULATION_SIZE; i++) {
                double[] newPosition = new double[M];

                if (p < P_THRESHOLD) {
                    // ===== 探索阶段: GX运算符 (大猩猩迁移) =====
                    for (int d = 0; d < M; d++) {
                        double r2 = random.nextDouble();
                        double r3 = random.nextDouble();
                        double r4 = random.nextDouble();

                        // 计算下界和上界 (对于[0,1]空间)
                        double UB = 1.0;
                        double LB = 0.0;

                        if (r2 < 0.5) {
                            // GX1: 基于随机位置和银背
                            int r_idx = random.nextInt(POPULATION_SIZE);
                            newPosition[d] = (UB - LB) * r3 + LB;
                            newPosition[d] = clamp(newPosition[d], 0, 1);

                        } else {
                            // GX2: 基于其他大猩猩位置
                            int r_idx = random.nextInt(POPULATION_SIZE);
                            double L = -1.0 + 2.0 * random.nextDouble();  // [-1, 1]

                            newPosition[d] = bestSolution[d] - L * (2.0 * r4 - 1.0) *
                                           Math.abs(bestSolution[d] - population[r_idx][d]);
                            newPosition[d] = clamp(newPosition[d], 0, 1);
                        }
                    }

                } else {
                    // ===== 开发阶段: 跟随银背或竞争 =====
                    // 计算C值
                    double C = calculateC(beta);

                    if (Math.abs(C) < W) {
                        // ===== 跟随银背 (Follow Silverback) =====
                        for (int d = 0; d < M; d++) {
                            double L = -1.0 + 2.0 * random.nextDouble();  // [-1, 1]
                            double r5 = random.nextDouble();

                            newPosition[d] = L * C * (bestSolution[d] - population[i][d]) + population[i][d];
                            newPosition[d] = clamp(newPosition[d], 0, 1);
                        }

                    } else {
                        // ===== 竞争机制 (Adult males competition or follow adult females) =====
                        // 选择一个随机的高质量解（模拟成年雌性）
                        int Q_idx = random.nextInt(POPULATION_SIZE);

                        for (int d = 0; d < M; d++) {
                            double r6 = random.nextDouble();
                            double r7 = random.nextDouble();
                            double A = beta * Math.exp(-r6 / (r7 + 1e-6));

                            double Z = -1.0 + 2.0 * random.nextDouble();  // [-1, 1]

                            if (r6 < 0.5) {
                                // 基于成年雌性位置
                                newPosition[d] = A * (bestSolution[d] - population[i][d]) + population[Q_idx][d];
                            } else {
                                // 基于银背和成年雌性的关系
                                newPosition[d] = bestSolution[d] - A * Z *
                                               Math.abs((C * bestSolution[d] - population[Q_idx][d]));
                            }

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

        System.out.println("\n========== GTO调度算法完成 ==========");
        System.out.println("最优Makespan: " + String.format("%.4f", bestFitness));
        System.out.println("映射条目数: " + cloudletVmMapping.size());
        System.out.println("=====================================\n");
    }

    // ==================== 辅助方法 ====================

    /**
     * 计算C值（参数）
     * C = F × (2r - 1)
     * F = cos(2 × r) + 1
     */
    private double calculateC(double beta) {
        double r = random.nextDouble();
        double F = Math.cos(2.0 * r) + 1.0;
        return F * (2.0 * r - 1.0);
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
        return "GTO";
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

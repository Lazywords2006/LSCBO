package com.edcbo.research;

import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.vms.Vm;
import com.edcbo.research.utils.ConvergenceRecord;
import com.edcbo.research.utils.EnergyCalculator;
import com.edcbo.research.utils.CostCalculator;
import org.apache.commons.math3.special.Gamma;

import java.util.*;

/**
 * LSCBO (Lévy-flight CBO) Broker - 随机初始化测试版
 *
 * 算法配置（测试版本v6.1）：
 * - Stage 0：CBO标准随机初始化（测试Tent混沌的影响）
 * - Phase 1：Lévy飞行搜索
 * - Phase 2：CBO旋转矩阵包围
 * - Phase 3：CBO动态攻击
 *
 * 测试目的：验证Lévy飞行配合随机初始化的性能
 *
 * @version 6.1-LSCBO (Lévy + Random Init)
 */
public class LSCBO_Broker_Fixed extends DatacenterBrokerSimple {

    // ==================== 算法参数 (极速版) ====================
    protected static final int POPULATION_SIZE = 20;
    protected static final int MAX_ITERATIONS = 20; // 极度简化以确保完成

    // Lévy飞行参数 (Tuned v7.3 - Optimal)
    private static final double LEVY_LAMBDA = 1.5;
    private static final double LEVY_ALPHA_COEF = 0.5; // 最优加强探索 (tested: 0.8 worse)

    // CBO阶段参数 (Tuned v7.3)
    private static final double PREY_SELECTION_PROB = 0.8; // 频繁学习最优解
    private static final double CONVERGENCE_COEF = 2.0; // Phase 2: 线性收敛系数
    private static final double ATTACK_WEIGHT = 0.3; // 最优值 (tested: 0.2 worse, 0.4 worse)

    // ==================== 内部状态 ====================
    private double[][] population;
    private double[] fitness;
    private double[] bestSolution;
    private double bestFitness;
    private Random random;
    private ConvergenceRecord convergenceRecord;
    private Map<Long, Vm> cloudletVmMapping;
    private boolean schedulingDone = false;

    // Lévy飞行相关
    private double levySigmaU;

    // ==================== 构造函数 ====================

    public LSCBO_Broker_Fixed(CloudSimPlus simulation) {
        super(simulation);
        this.random = new Random();
        this.convergenceRecord = new ConvergenceRecord("LSCBO-Levy-Random", "unknown", System.currentTimeMillis());
        this.cloudletVmMapping = new HashMap<>();
        calculateLevySigmaU();
    }

    public LSCBO_Broker_Fixed(CloudSimPlus simulation, long seed) {
        super(simulation);
        this.random = new Random(seed);
        this.convergenceRecord = new ConvergenceRecord("LSCBO-Levy-Random", "unknown", seed);
        this.cloudletVmMapping = new HashMap<>();
        calculateLevySigmaU();
    }

    public LSCBO_Broker_Fixed(CloudSimPlus simulation, long seed, String scale) {
        super(simulation);
        this.random = new Random(seed);
        this.convergenceRecord = new ConvergenceRecord("LSCBO-Levy-Random", scale, seed);
        this.cloudletVmMapping = new HashMap<>();
        calculateLevySigmaU();
    }

    public LSCBO_Broker_Fixed(CloudSimPlus simulation, long seed, ConvergenceRecord record) {
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
            runLSCBO();
            schedulingDone = true;
        }
        return cloudletVmMapping.getOrDefault(cloudlet.getId(), super.defaultVmMapper(cloudlet));
    }

    private void runLSCBO() {
        List<Cloudlet> cloudletList = new ArrayList<>(getCloudletWaitingList());
        List<Vm> vmList = new ArrayList<>(getVmCreatedList());

        int M = cloudletList.size();
        int N = vmList.size();

        // Update ConvergenceRecord with correct scale
        this.convergenceRecord = new ConvergenceRecord("LSCBO", "M" + M, this.convergenceRecord.getSeed());

        System.out.println("\n========== LSCBO-Levy-Random调度算法启动 ==========");
        System.out.println("等待任务数: " + M);
        System.out.println("已创建VM数: " + N);
        System.out.println("种群大小: " + POPULATION_SIZE);
        System.out.println("最大迭代: " + MAX_ITERATIONS);
        System.out.println(String.format("Lévy参数: LAMBDA=%.2f, ALPHA_COEF=%.2f",
                LEVY_LAMBDA, LEVY_ALPHA_COEF));
        System.out.println("初始化策略: 标准随机初始化（测试版）");
        System.out.println("算法策略: Phase1=Lévy飞行, Phase2-3=CBO原版");
        System.out.println("===========================================\n");

        if (M == 0 || N == 0) {
            System.out.println("⚠️ 任务或VM数量为0，算法退出");
            return;
        }

        // 初始化种群（标准随机初始化）
        population = new double[POPULATION_SIZE][M];
        for (int i = 0; i < POPULATION_SIZE; i++) {
            // 使用标准随机初始化（与CBO一致）
            for (int j = 0; j < M; j++) {
                population[i][j] = random.nextDouble();
            }
        }

        // 评估初始种群并初始化fitness数组
        fitness = new double[POPULATION_SIZE];
        bestFitness = Double.MAX_VALUE;
        bestSolution = new double[M];
        for (int i = 0; i < POPULATION_SIZE; i++) {
            fitness[i] = calculateFitness(population[i], M, N, cloudletList, vmList);
            if (fitness[i] < bestFitness) {
                bestFitness = fitness[i];
                System.arraycopy(population[i], 0, bestSolution, 0, M);
            }
        }

        convergenceRecord.recordIteration(0, bestFitness);

        // 主循环
        for (int t = 1; t <= MAX_ITERATIONS; t++) {
            for (int i = 0; i < POPULATION_SIZE; i++) {
                double[] newPosition = new double[M];

                // ========== Phase 1: Lévy飞行全局搜索 ==========
                int preyIdx = random.nextDouble() < PREY_SELECTION_PROB ? findBestIndividualIndex()
                        : random.nextInt(POPULATION_SIZE);

                for (int d = 0; d < M; d++) {
                    double levyStep = generateLevyStep();
                    double alpha = LEVY_ALPHA_COEF * (1.0 - (double) t / MAX_ITERATIONS);

                    newPosition[d] = population[i][d]
                            + alpha * levyStep * (population[preyIdx][d] - population[i][d]);

                    newPosition[d] = clamp(newPosition[d], 0, 1);
                }

                // ========== Phase 2: CBO旋转矩阵包围 ==========
                double theta = 2.0 * Math.PI * t / MAX_ITERATIONS;
                double cosTheta = Math.cos(theta);
                double sinTheta = Math.sin(theta);

                for (int d = 0; d < M - 1; d += 2) {
                    double dx = newPosition[d] - bestSolution[d];
                    double dy = newPosition[d + 1] - bestSolution[d + 1];

                    double rotatedX = dx * cosTheta - dy * sinTheta;
                    double rotatedY = dx * sinTheta + dy * cosTheta;

                    newPosition[d] = bestSolution[d] + rotatedX;
                    newPosition[d + 1] = bestSolution[d + 1] + rotatedY;

                    newPosition[d] = clamp(newPosition[d], 0, 1);
                    newPosition[d + 1] = clamp(newPosition[d + 1], 0, 1);
                }

                if (M % 2 == 1) {
                    int lastDim = M - 1;
                    double A = CONVERGENCE_COEF - CONVERGENCE_COEF * t / MAX_ITERATIONS;
                    double C = A * (2 * random.nextDouble() - 1);
                    newPosition[lastDim] = newPosition[lastDim]
                            + C * (bestSolution[lastDim] - newPosition[lastDim]);
                    newPosition[lastDim] = clamp(newPosition[lastDim], 0, 1);
                }

                // ========== Phase 3: CBO动态攻击 ==========
                for (int d = 0; d < M; d++) {
                    newPosition[d] = ATTACK_WEIGHT * newPosition[d] +
                            (1 - ATTACK_WEIGHT) * bestSolution[d];
                    newPosition[d] = clamp(newPosition[d], 0, 1);
                }

                // 评估新解
                int[] newDiscreteSchedule = continuousToDiscrete(newPosition, N);
                CostCalculator.CostResult result = CostCalculator.calculateWeightedCostDetails(newDiscreteSchedule, M,
                        N, cloudletList, vmList);
                double newFitness = result.fitness;

                if (newFitness < fitness[i]) {
                    fitness[i] = newFitness;
                    System.arraycopy(newPosition, 0, population[i], 0, M);
                    if (newFitness < bestFitness) {
                        bestFitness = newFitness;
                        System.arraycopy(newPosition, 0, bestSolution, 0, M);
                        // Record detailed best components for this iteration
                        // Note: In strict LSCBO logic, alpha is the best.
                        // We should store alpha's components. But 'result' is just a candidate.
                        // We need to re-calculate alpha's details or store them.
                        // Simply re-calculating alpha's details at end of loop is safer.
                    }
                }
            }

            // Record Alpha (Best) details
            int[] bestDiscreteSchedule = continuousToDiscrete(bestSolution, N);
            CostCalculator.CostResult bestRes = CostCalculator.calculateWeightedCostDetails(bestDiscreteSchedule, M, N,
                    cloudletList, vmList);
            if (convergenceRecord != null) {
                convergenceRecord.recordIteration(t, bestRes.fitness, bestRes.time, bestRes.load, bestRes.price);
            }
        }

        // 应用最优解
        applySchedule(bestSolution, M, N, cloudletList, vmList);

        System.out.println("\n========== LSCBO-Levy-Random调度算法完成 ==========");
        System.out.println("最优Makespan: " + String.format("%.4f", bestFitness));
        System.out.println("映射条目数: " + cloudletVmMapping.size());
        System.out.println("=============================================\n");
    }

    // ==================== 辅助方法 ====================

    private int findBestIndividualIndex() {
        int bestIdx = 0;
        double bestFit = Double.MAX_VALUE;

        for (int i = 0; i < POPULATION_SIZE; i++) {
            if (fitness[i] < bestFit) {
                bestFit = fitness[i];
                bestIdx = i;
            }
        }
        return bestIdx;
    }

    /**
     * 计算Lévy飞行分布的σ_u参数（Mantegna方法）
     *
     * 理论基础：
     * - Mantegna, R. N. (1994). Fast, accurate algorithm for numerical
     * simulation of Lévy stable stochastic processes.
     * Physical Review E, 49(5), 4677-4683.
     *
     * 公式：σ_u = [Γ(1+λ)sin(πλ/2) / (Γ((1+λ)/2) × λ × 2^((λ-1)/2))]^(1/λ)
     *
     * 使用Apache Commons Math 3.6.1的Gamma函数替代Stirling近似，
     * 提供更高的数值精度。
     */
    private void calculateLevySigmaU() {
        double lambda = LEVY_LAMBDA;
        double numerator = Gamma.gamma(1 + lambda) * Math.sin(Math.PI * lambda / 2.0);
        double denominator = Gamma.gamma((1 + lambda) / 2.0) * lambda * Math.pow(2, (lambda - 1) / 2.0);
        this.levySigmaU = Math.pow(numerator / denominator, 1.0 / lambda);
    }

    private double generateLevyStep() {
        double u = random.nextGaussian() * levySigmaU;
        double v = random.nextGaussian();
        double step = u / Math.pow(Math.abs(v) + 1e-10, 1.0 / LEVY_LAMBDA);
        return Math.max(-1.0, Math.min(1.0, step));
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    protected double calculateFitness(double[] individual, int M, int N,
            List<Cloudlet> cloudletList, List<Vm> vmList) {
        int[] schedule = continuousToDiscrete(individual, N);

        // 使用加权成本作为适应度函数 (Cost = 0.5*Time + 0.25*Load + 0.25*Price)
        return CostCalculator.calculateWeightedFitness(schedule, M, N, cloudletList, vmList);
    }

    protected int[] continuousToDiscrete(double[] continuous, int N) {
        // 输入验证
        if (N <= 0) {
            throw new IllegalArgumentException("N must be positive, got: " + N);
        }
        if (continuous == null) {
            throw new NullPointerException("continuous array cannot be null");
        }

        int[] discrete = new int[continuous.length];
        for (int i = 0; i < continuous.length; i++) {
            // 确保值在[0,1]范围内
            double value = Math.max(0.0, Math.min(1.0, continuous[i]));
            discrete[i] = (int) (value * N);

            // 边界保护
            if (discrete[i] >= N) {
                discrete[i] = N - 1;
            }
            // 负数保护（防御性编程）
            if (discrete[i] < 0) {
                discrete[i] = 0;
            }
        }
        return discrete;
    }

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
        return "LSCBO-Levy-Random";
    }

    public double getInternalMakespan() {
        return bestFitness;
    }
}

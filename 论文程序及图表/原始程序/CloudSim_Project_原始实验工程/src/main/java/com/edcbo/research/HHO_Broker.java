package com.edcbo.research;

import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.vms.Vm;
import com.edcbo.research.utils.ConvergenceRecord;
import com.edcbo.research.utils.CostCalculator;
import org.apache.commons.math3.special.Gamma;

import java.util.*;

/**
 * HHO (Harris Hawks Optimization) Broker for Cloud Task Scheduling
 * 
 * 哈里斯鹰优化算法 - 模拟哈里斯鹰的合作狩猎行为
 * - 探索阶段: 随机栖息或随机高位
 * - 转换阶段: 基于逃逸能量E的策略切换
 * - 开发阶段: 软围攻/硬围攻 + Lévy飞行
 * 
 * @author LSCBO Research Team
 */
public class HHO_Broker extends DatacenterBrokerSimple {

    // HHO算法参数 (Reduced for CloudSim Performance)
    protected static final int POPULATION_SIZE = 30;
    protected static final int MAX_ITERATIONS = 50;

    // HHO特有参数
    private static final double LEVY_BETA = 1.5;

    private double[][] population;
    private double[] fitness;
    private double[] bestSolution;
    private double bestFitness;
    private Random random;
    private long seed;
    private ConvergenceRecord convergenceRecord;
    private Map<Long, Vm> cloudletVmMapping;
    private boolean schedulingDone = false;
    private double levySigmaU;

    public HHO_Broker(CloudSimPlus simulation) {
        super(simulation);
        this.random = new Random();
        this.seed = System.currentTimeMillis();
        this.cloudletVmMapping = new HashMap<>();
        calculateLevySigmaU();
    }

    public HHO_Broker(CloudSimPlus simulation, long seed) {
        super(simulation);
        this.seed = seed;
        this.random = new Random(seed);
        this.cloudletVmMapping = new HashMap<>();
        calculateLevySigmaU();
    }

    public HHO_Broker(CloudSimPlus simulation, long seed, String scale) {
        super(simulation);
        this.seed = seed;
        this.random = new Random(seed);
        this.cloudletVmMapping = new HashMap<>();
        calculateLevySigmaU();
    }

    public HHO_Broker(CloudSimPlus simulation, long seed, ConvergenceRecord record) {
        super(simulation);
        this.seed = seed;
        this.random = new Random(seed);
        this.convergenceRecord = record;
        this.cloudletVmMapping = new HashMap<>();
        calculateLevySigmaU();
    }

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

        // 创建收敛记录器
        String scale = String.format("M%d", M);
        this.convergenceRecord = new ConvergenceRecord("HHO", scale, this.seed);

        System.out.println("\n========== HHO调度算法启动 ==========");
        System.out.println("任务数: " + M);
        System.out.println("VM数: " + N);
        System.out.println("种群大小: " + POPULATION_SIZE);
        System.out.println("最大迭代: " + MAX_ITERATIONS);
        System.out.println("======================================\n");

        if (M == 0 || N == 0) {
            return;
        }

        // 初始化种群
        initializePopulation(M, N, cloudletList, vmList);

        // HHO迭代
        for (int t = 0; t < MAX_ITERATIONS; t++) {
            // 逃逸能量 E0 ∈ [-1, 1], E从2线性衰减到0
            double E0 = 2.0 * random.nextDouble() - 1.0;
            double E = 2.0 * E0 * (1.0 - (double) t / MAX_ITERATIONS);

            for (int i = 0; i < POPULATION_SIZE; i++) {
                double[] newPosition = new double[M];
                double q = random.nextDouble();
                double r = random.nextDouble();

                if (Math.abs(E) >= 1) {
                    // 探索阶段 (Exploration)
                    if (q >= 0.5) {
                        // 随机栖息
                        int randomIdx = random.nextInt(POPULATION_SIZE);
                        for (int d = 0; d < M; d++) {
                            double r1 = random.nextDouble();
                            double r2 = random.nextDouble();
                            newPosition[d] = population[randomIdx][d]
                                    - r1 * Math.abs(population[randomIdx][d] - 2.0 * r2 * population[i][d]);
                            newPosition[d] = clamp(newPosition[d], 0, 1);
                        }
                    } else {
                        // 随机高位 (基于种群平均位置)
                        double[] avgPos = calculateAvgPosition(M);
                        for (int d = 0; d < M; d++) {
                            double r3 = random.nextDouble();
                            double r4 = random.nextDouble();
                            newPosition[d] = (bestSolution[d] - avgPos[d])
                                    - r3 * (0.0 + r4 * (1.0 - 0.0)); // LB=0, UB=1
                            newPosition[d] = clamp(newPosition[d], 0, 1);
                        }
                    }
                } else {
                    // 开发阶段 (Exploitation)
                    double J = 2.0 * (1.0 - random.nextDouble()); // 跳跃强度

                    if (r >= 0.5) {
                        // 软围攻 (|E| >= 0.5) 或 硬围攻的软部分
                        if (Math.abs(E) >= 0.5) {
                            // 软围攻
                            for (int d = 0; d < M; d++) {
                                double deltaX = bestSolution[d] - population[i][d];
                                newPosition[d] = deltaX - E * Math.abs(J * bestSolution[d] - population[i][d]);
                                newPosition[d] = clamp(newPosition[d], 0, 1);
                            }
                        } else {
                            // 硬围攻
                            for (int d = 0; d < M; d++) {
                                newPosition[d] = bestSolution[d] - E * Math.abs(bestSolution[d] - population[i][d]);
                                newPosition[d] = clamp(newPosition[d], 0, 1);
                            }
                        }
                    } else {
                        // 渐进式快速俯冲 with Lévy飞行
                        double[] levy = generateLevyFlight(M);
                        if (Math.abs(E) >= 0.5) {
                            // 软围攻 + Lévy
                            double[] Y = new double[M];
                            for (int d = 0; d < M; d++) {
                                Y[d] = bestSolution[d] - E * Math.abs(J * bestSolution[d] - population[i][d]);
                            }
                            // 评估Y
                            int[] scheduleY = continuousToDiscrete(Y, N);
                            double fitnessY = CostCalculator.calculateWeightedFitness(scheduleY, M, N, cloudletList,
                                    vmList);

                            double[] Z = new double[M];
                            for (int d = 0; d < M; d++) {
                                Z[d] = Y[d] + 0.01 * levy[d]; // 小步长Lévy
                                Z[d] = clamp(Z[d], 0, 1);
                            }
                            int[] scheduleZ = continuousToDiscrete(Z, N);
                            double fitnessZ = CostCalculator.calculateWeightedFitness(scheduleZ, M, N, cloudletList,
                                    vmList);

                            if (fitnessY < fitness[i]) {
                                System.arraycopy(Y, 0, newPosition, 0, M);
                            } else if (fitnessZ < fitness[i]) {
                                System.arraycopy(Z, 0, newPosition, 0, M);
                            } else {
                                System.arraycopy(population[i], 0, newPosition, 0, M);
                            }
                        } else {
                            // 硬围攻 + Lévy
                            double[] Y = new double[M];
                            for (int d = 0; d < M; d++) {
                                Y[d] = bestSolution[d] - E * Math.abs(J * bestSolution[d] - population[i][d]);
                            }
                            int[] scheduleY = continuousToDiscrete(Y, N);
                            double fitnessY = CostCalculator.calculateWeightedFitness(scheduleY, M, N, cloudletList,
                                    vmList);

                            double[] Z = new double[M];
                            for (int d = 0; d < M; d++) {
                                Z[d] = Y[d] + 0.01 * levy[d];
                                Z[d] = clamp(Z[d], 0, 1);
                            }
                            int[] scheduleZ = continuousToDiscrete(Z, N);
                            double fitnessZ = CostCalculator.calculateWeightedFitness(scheduleZ, M, N, cloudletList,
                                    vmList);

                            if (fitnessY < fitness[i]) {
                                System.arraycopy(Y, 0, newPosition, 0, M);
                            } else if (fitnessZ < fitness[i]) {
                                System.arraycopy(Z, 0, newPosition, 0, M);
                            } else {
                                System.arraycopy(population[i], 0, newPosition, 0, M);
                            }
                        }
                    }
                }

                // 边界处理
                for (int d = 0; d < M; d++) {
                    newPosition[d] = clamp(newPosition[d], 0, 1);
                }

                // 计算适应度并更新
                int[] schedule = continuousToDiscrete(newPosition, N);
                CostCalculator.CostResult result = CostCalculator.calculateWeightedCostDetails(
                        schedule, M, N, cloudletList, vmList);
                double newFitness = result.fitness;

                if (newFitness < fitness[i]) {
                    System.arraycopy(newPosition, 0, population[i], 0, M);
                    fitness[i] = newFitness;

                    if (newFitness < bestFitness) {
                        bestFitness = newFitness;
                        System.arraycopy(newPosition, 0, bestSolution, 0, M);
                    }
                }
            }

            // 记录收敛曲线
            int[] bestSchedule = continuousToDiscrete(bestSolution, N);
            CostCalculator.CostResult bestRes = CostCalculator.calculateWeightedCostDetails(
                    bestSchedule, M, N, cloudletList, vmList);
            if (convergenceRecord != null) {
                convergenceRecord.recordIteration(t, bestRes.fitness, bestRes.time, bestRes.load, bestRes.price);
            }

            if ((t + 1) % 20 == 0) {
                System.out.println(String.format("[HHO Iter %3d/%d] Best Fitness: %.4f",
                        t + 1, MAX_ITERATIONS, bestFitness));
            }
        }

        // 导出收敛曲线
        if (convergenceRecord != null) {
            convergenceRecord.exportToCSV("results/");
        }

        // 应用最优调度
        int[] bestSchedule = continuousToDiscrete(bestSolution, N);
        for (int i = 0; i < M; i++) {
            cloudletVmMapping.put(cloudletList.get(i).getId(), vmList.get(bestSchedule[i]));
        }

        System.out.println("\n========== HHO调度算法完成 ==========");
        System.out.println("最优Fitness: " + String.format("%.4f", bestFitness));
        System.out.println("======================================\n");
    }

    private void initializePopulation(int M, int N, List<Cloudlet> cloudletList, List<Vm> vmList) {
        population = new double[POPULATION_SIZE][M];
        fitness = new double[POPULATION_SIZE];
        bestSolution = new double[M];
        bestFitness = Double.MAX_VALUE;

        for (int i = 0; i < POPULATION_SIZE; i++) {
            for (int d = 0; d < M; d++) {
                population[i][d] = random.nextDouble();
            }
            int[] schedule = continuousToDiscrete(population[i], N);
            fitness[i] = CostCalculator.calculateWeightedFitness(schedule, M, N, cloudletList, vmList);

            if (fitness[i] < bestFitness) {
                bestFitness = fitness[i];
                System.arraycopy(population[i], 0, bestSolution, 0, M);
            }
        }
    }

    private double[] calculateAvgPosition(int M) {
        double[] avg = new double[M];
        for (int d = 0; d < M; d++) {
            double sum = 0;
            for (int i = 0; i < POPULATION_SIZE; i++) {
                sum += population[i][d];
            }
            avg[d] = sum / POPULATION_SIZE;
        }
        return avg;
    }

    private void calculateLevySigmaU() {
        double num = Gamma.gamma(1 + LEVY_BETA) * Math.sin(Math.PI * LEVY_BETA / 2.0);
        double den = Gamma.gamma((1 + LEVY_BETA) / 2.0) * LEVY_BETA * Math.pow(2, (LEVY_BETA - 1) / 2.0);
        this.levySigmaU = Math.pow(num / den, 1.0 / LEVY_BETA);
    }

    private double[] generateLevyFlight(int M) {
        double[] levy = new double[M];
        for (int d = 0; d < M; d++) {
            double u = random.nextGaussian() * levySigmaU;
            double v = random.nextGaussian();
            levy[d] = u / Math.pow(Math.abs(v) + 1e-10, 1.0 / LEVY_BETA);
            // 限制步长
            levy[d] = Math.max(-1.0, Math.min(1.0, levy[d]));
        }
        return levy;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private int[] continuousToDiscrete(double[] continuous, int N) {
        int[] discrete = new int[continuous.length];
        for (int i = 0; i < continuous.length; i++) {
            discrete[i] = (int) (continuous[i] * N);
            if (discrete[i] >= N)
                discrete[i] = N - 1;
            if (discrete[i] < 0)
                discrete[i] = 0;
        }
        return discrete;
    }

    public ConvergenceRecord getConvergenceRecord() {
        return convergenceRecord;
    }

    public String getAlgorithmName() {
        return "HHO";
    }

    public double getInternalMakespan() {
        return bestFitness;
    }
}

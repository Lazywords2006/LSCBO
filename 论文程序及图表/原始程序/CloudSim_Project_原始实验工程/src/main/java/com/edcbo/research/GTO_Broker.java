package com.edcbo.research;

import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.vms.Vm;
import com.edcbo.research.utils.ConvergenceRecord;
import com.edcbo.research.utils.CostCalculator;

import java.util.*;

/**
 * GTO (Gorilla Troops Optimizer) Broker for Cloud Task Scheduling
 * 
 * 大猩猩部队优化算法 - 模拟大猩猩群体的社会行为
 * - 探索阶段: 向随机猩猩或群体中心移动
 * - 开发阶段: 银背领导者跟随 + 竞争行为
 * - 成年雄性行为: Lévy飞行 + 贪心选择
 * 
 * @author LSCBO Research Team
 */
public class GTO_Broker extends DatacenterBrokerSimple {

    // GTO算法参数 (Reduced for CloudSim Performance)
    protected static final int POPULATION_SIZE = 30;
    protected static final int MAX_ITERATIONS = 50;

    // GTO特有参数
    private static final double BETA_INITIAL = 3.0; // 初始β参数
    private static final double W = 0.8; // 探索/开发权重
    private static final double P_THRESHOLD = 0.5; // 策略选择阈值

    private double[][] population;
    private double[] fitness;
    private double[] bestSolution;
    private double bestFitness;
    private Random random;
    private long seed;
    private ConvergenceRecord convergenceRecord;
    private Map<Long, Vm> cloudletVmMapping;
    private boolean schedulingDone = false;

    public GTO_Broker(CloudSimPlus simulation) {
        super(simulation);
        this.random = new Random();
        this.seed = System.currentTimeMillis();
        this.cloudletVmMapping = new HashMap<>();
    }

    public GTO_Broker(CloudSimPlus simulation, long seed) {
        super(simulation);
        this.seed = seed;
        this.random = new Random(seed);
        this.cloudletVmMapping = new HashMap<>();
    }

    public GTO_Broker(CloudSimPlus simulation, long seed, String scale) {
        super(simulation);
        this.seed = seed;
        this.random = new Random(seed);
        this.cloudletVmMapping = new HashMap<>();
    }

    public GTO_Broker(CloudSimPlus simulation, long seed, ConvergenceRecord record) {
        super(simulation);
        this.seed = seed;
        this.random = new Random(seed);
        this.convergenceRecord = record;
        this.cloudletVmMapping = new HashMap<>();
    }

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

        // 创建收敛记录器
        String scale = String.format("M%d", M);
        this.convergenceRecord = new ConvergenceRecord("GTO", scale, this.seed);

        System.out.println("\n========== GTO调度算法启动 ==========");
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

        // GTO迭代
        for (int t = 0; t < MAX_ITERATIONS; t++) {
            // 计算β参数 (随迭代衰减)
            double beta = BETA_INITIAL * (1.0 - (double) t / MAX_ITERATIONS);

            // 计算种群中心
            double[] groupCenter = calculateGroupCenter(M);

            for (int i = 0; i < POPULATION_SIZE; i++) {
                double[] newPosition = new double[M];
                double p = random.nextDouble();

                if (p < P_THRESHOLD) {
                    // 探索阶段 (Exploration)
                    if (random.nextDouble() < W) {
                        // 向群体中心移动
                        for (int d = 0; d < M; d++) {
                            double C = calculateC(beta);
                            newPosition[d] = (1 - C) * population[i][d] + C * groupCenter[d];
                            newPosition[d] = clamp(newPosition[d], 0, 1);
                        }
                    } else {
                        // 向随机个体移动
                        int randomIdx = random.nextInt(POPULATION_SIZE);
                        for (int d = 0; d < M; d++) {
                            double r = random.nextDouble();
                            newPosition[d] = population[i][d] + r * (population[randomIdx][d] - population[i][d]);
                            newPosition[d] = clamp(newPosition[d], 0, 1);
                        }
                    }
                } else {
                    // 开发阶段 (Exploitation) - 银背领导者跟随
                    for (int d = 0; d < M; d++) {
                        double L = calculateC(beta); // 使用C作为步长因子
                        double H = random.nextGaussian() * 0.1; // 小扰动
                        newPosition[d] = bestSolution[d] - L * (bestSolution[d] - population[i][d]) + H;
                        newPosition[d] = clamp(newPosition[d], 0, 1);
                    }
                }

                // 计算适应度
                int[] schedule = continuousToDiscrete(newPosition, N);
                CostCalculator.CostResult result = CostCalculator.calculateWeightedCostDetails(
                        schedule, M, N, cloudletList, vmList);
                double newFitness = result.fitness;

                // 贪心选择
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
                System.out.println(String.format("[GTO Iter %3d/%d] Best Fitness: %.4f",
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

        System.out.println("\n========== GTO调度算法完成 ==========");
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

    private double[] calculateGroupCenter(int M) {
        double[] center = new double[M];
        for (int d = 0; d < M; d++) {
            double sum = 0;
            for (int i = 0; i < POPULATION_SIZE; i++) {
                sum += population[i][d];
            }
            center[d] = sum / POPULATION_SIZE;
        }
        return center;
    }

    private double calculateC(double beta) {
        double r = random.nextDouble();
        double F = Math.cos(2.0 * r) + 1.0;
        return F * (2.0 * r - 1.0) * beta / BETA_INITIAL;
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
        return "GTO";
    }

    public double getInternalMakespan() {
        return bestFitness;
    }
}

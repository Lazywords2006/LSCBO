package com.edcbo.research;

import com.edcbo.research.utils.ConvergenceRecord;
import com.edcbo.research.utils.CostCalculator;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.vms.Vm;

import java.util.*;

/**
 * WOA (Whale Optimization Algorithm) Broker for Cloud Task Scheduling
 * 
 * 鲸鱼优化算法 - 模拟座头鲸的气泡网捕食行为
 * - 包围猎物 (Encircling Prey)
 * - 气泡网攻击 (Bubble-net Attacking): 螺旋更新 + 收缩包围
 * - 搜索猎物 (Search for Prey): 全局探索
 * 
 * @author LSCBO Research Team
 */
public class WOA_Broker extends DatacenterBrokerSimple {

    // WOA算法参数 (Reduced to prevent simulation stall)
    protected static final int POPULATION_SIZE = 30;
    protected static final int MAX_ITERATIONS = 50;
    protected static final double B = 1.0; // 螺旋形常数

    protected final Random random;
    protected final long seed;

    protected double[][] whales;
    protected double[] fitness;
    protected double[] bestWhale;
    protected double bestFitness;

    protected ConvergenceRecord convergenceRecord;
    private Map<Long, Vm> cloudletVmMapping;
    private boolean schedulingDone = false;

    public WOA_Broker(CloudSimPlus simulation, long seed) {
        super(simulation);
        this.seed = seed;
        this.random = new Random(seed);
        this.cloudletVmMapping = new HashMap<>();
    }

    public WOA_Broker(CloudSimPlus simulation) {
        this(simulation, 42L);
    }

    public void setConvergenceRecord(ConvergenceRecord record) {
        this.convergenceRecord = record;
    }

    @Override
    protected Vm defaultVmMapper(Cloudlet cloudlet) {
        if (!schedulingDone) {
            runWOAScheduling();
            schedulingDone = true;
        }
        return cloudletVmMapping.getOrDefault(cloudlet.getId(), super.defaultVmMapper(cloudlet));
    }

    private void runWOAScheduling() {
        List<Cloudlet> cloudletList = new ArrayList<>(getCloudletWaitingList());
        List<Vm> vmList = new ArrayList<>(getVmCreatedList());

        if (cloudletList.isEmpty() || vmList.isEmpty()) {
            return;
        }

        int M = cloudletList.size();
        int N = vmList.size();

        // 创建收敛记录器
        String scale = String.format("M%d", M);
        this.convergenceRecord = new ConvergenceRecord("WOA", scale, this.seed);

        System.out.println("\n========== WOA调度算法启动 ==========");
        System.out.println("任务数: " + M);
        System.out.println("VM数: " + N);
        System.out.println("种群大小: " + POPULATION_SIZE);
        System.out.println("最大迭代: " + MAX_ITERATIONS);
        System.out.println("======================================\n");

        // 初始化鲸鱼种群
        initializeWhales(M, N, cloudletList, vmList);

        // WOA迭代
        for (int t = 0; t < MAX_ITERATIONS; t++) {
            // 收敛因子a从2线性衰减到0
            double a = 2.0 - 2.0 * t / MAX_ITERATIONS;
            double a2 = -1.0 - t / MAX_ITERATIONS; // 螺旋参数

            for (int i = 0; i < POPULATION_SIZE; i++) {
                double[] newPosition = new double[M];

                double r = random.nextDouble();
                double p = random.nextDouble(); // 选择包围或螺旋
                double l = (random.nextDouble() - 0.5) * 2; // l ∈ [-1, 1]

                for (int j = 0; j < M; j++) {
                    double A = 2 * a * r - a; // A ∈ [-a, a]
                    double C = 2 * random.nextDouble(); // C ∈ [0, 2]

                    if (p < 0.5) {
                        // 包围或搜索阶段
                        if (Math.abs(A) < 1) {
                            // 包围猎物 (向最优解收缩)
                            double D = Math.abs(C * bestWhale[j] - whales[i][j]);
                            newPosition[j] = bestWhale[j] - A * D;
                        } else {
                            // 搜索猎物 (全局探索)
                            int randomIdx = random.nextInt(POPULATION_SIZE);
                            double D = Math.abs(C * whales[randomIdx][j] - whales[i][j]);
                            newPosition[j] = whales[randomIdx][j] - A * D;
                        }
                    } else {
                        // 气泡网攻击 (螺旋更新) - 修复数值溢出
                        double D = Math.abs(bestWhale[j] - whales[i][j]);
                        double expTerm = Math.min(Math.exp(B * l), 10.0); // 防止指数爆炸
                        newPosition[j] = D * expTerm * Math.cos(2 * Math.PI * l) + bestWhale[j];
                    }

                    newPosition[j] = clamp(newPosition[j], 0.0, 1.0);
                }

                // 计算适应度
                int[] schedule = continuousToDiscrete(newPosition, N);
                CostCalculator.CostResult result = CostCalculator.calculateWeightedCostDetails(
                        schedule, M, N, cloudletList, vmList);
                double newFitness = result.fitness;

                // 贪心选择
                if (newFitness < fitness[i]) {
                    System.arraycopy(newPosition, 0, whales[i], 0, M);
                    fitness[i] = newFitness;

                    // 更新全局最优
                    if (newFitness < bestFitness) {
                        bestFitness = newFitness;
                        System.arraycopy(newPosition, 0, bestWhale, 0, M);
                    }
                }
            }

            // 记录收敛曲线
            int[] bestSchedule = continuousToDiscrete(bestWhale, N);
            CostCalculator.CostResult bestRes = CostCalculator.calculateWeightedCostDetails(
                    bestSchedule, M, N, cloudletList, vmList);
            if (convergenceRecord != null) {
                convergenceRecord.recordIteration(t, bestRes.fitness, bestRes.time, bestRes.load, bestRes.price);
            }

            if ((t + 1) % 20 == 0) {
                System.out.println(String.format("[WOA Iter %3d/%d] Best Fitness: %.4f",
                        t + 1, MAX_ITERATIONS, bestFitness));
            }
        }

        // 导出收敛曲线
        if (convergenceRecord != null) {
            convergenceRecord.exportToCSV("results/");
        }

        // 应用最优调度
        int[] bestSchedule = continuousToDiscrete(bestWhale, N);
        for (int i = 0; i < M; i++) {
            cloudletVmMapping.put(cloudletList.get(i).getId(), vmList.get(bestSchedule[i]));
        }

        System.out.println("\n========== WOA调度算法完成 ==========");
        System.out.println("最优Fitness: " + String.format("%.4f", bestFitness));
        System.out.println("======================================\n");
    }

    private void initializeWhales(int M, int N, List<Cloudlet> cloudletList, List<Vm> vmList) {
        whales = new double[POPULATION_SIZE][M];
        fitness = new double[POPULATION_SIZE];
        bestWhale = new double[M];
        bestFitness = Double.MAX_VALUE;

        for (int i = 0; i < POPULATION_SIZE; i++) {
            for (int j = 0; j < M; j++) {
                whales[i][j] = random.nextDouble();
            }
            int[] schedule = continuousToDiscrete(whales[i], N);
            fitness[i] = CostCalculator.calculateWeightedFitness(schedule, M, N, cloudletList, vmList);

            if (fitness[i] < bestFitness) {
                bestFitness = fitness[i];
                System.arraycopy(whales[i], 0, bestWhale, 0, M);
            }
        }
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    protected int[] continuousToDiscrete(double[] continuous, int N) {
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

    public double getInternalMakespan() {
        return bestFitness;
    }
}

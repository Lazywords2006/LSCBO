package com.edcbo.research;

import com.edcbo.research.utils.ConvergenceRecord;
import com.edcbo.research.utils.CostCalculator;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.vms.Vm;

import java.util.*;

/**
 * GWO (Grey Wolf Optimizer) Broker for Cloud Task Scheduling
 * 
 * 灰狼优化算法 - 模拟灰狼社会等级和狩猎行为
 * - Alpha, Beta, Delta 三级狼群领导结构
 * - 包围、追踪、攻击三阶段狩猎策略
 * - 收敛因子a从2线性衰减到0
 * 
 * @author LSCBO Research Team
 */
public class GWO_Broker extends DatacenterBrokerSimple {

    // GWO算法参数 (Reduced to prevent simulation stall)
    protected static final int POPULATION_SIZE = 20;
    protected static final int MAX_ITERATIONS = 20;

    protected final Random random;
    protected final long seed;

    // 狼群数据
    protected double[][] wolves;
    protected double[] fitness;

    // 三级领导者
    protected double[] alphaPos;
    protected double alphaScore;
    protected double[] betaPos;
    protected double betaScore;
    protected double[] deltaPos;
    protected double deltaScore;

    protected ConvergenceRecord convergenceRecord;
    private Map<Long, Vm> cloudletVmMapping;
    private boolean schedulingDone = false;

    public GWO_Broker(CloudSimPlus simulation, long seed) {
        super(simulation);
        this.seed = seed;
        this.random = new Random(seed);
        this.cloudletVmMapping = new HashMap<>();
    }

    public GWO_Broker(CloudSimPlus simulation) {
        this(simulation, 42L);
    }

    public void setConvergenceRecord(ConvergenceRecord record) {
        this.convergenceRecord = record;
    }

    @Override
    protected Vm defaultVmMapper(Cloudlet cloudlet) {
        if (!schedulingDone) {
            runGWOScheduling();
            schedulingDone = true;
        }
        return cloudletVmMapping.getOrDefault(cloudlet.getId(), super.defaultVmMapper(cloudlet));
    }

    private void runGWOScheduling() {
        List<Cloudlet> cloudletList = new ArrayList<>(getCloudletWaitingList());
        List<Vm> vmList = new ArrayList<>(getVmCreatedList());

        if (cloudletList.isEmpty() || vmList.isEmpty()) {
            return;
        }

        int M = cloudletList.size();
        int N = vmList.size();

        // 创建收敛记录器
        String scale = String.format("M%d", M);
        this.convergenceRecord = new ConvergenceRecord("GWO", scale, this.seed);

        System.out.println("\n========== GWO调度算法启动 ==========");
        System.out.println("任务数: " + M);
        System.out.println("VM数: " + N);
        System.out.println("种群大小: " + POPULATION_SIZE);
        System.out.println("最大迭代: " + MAX_ITERATIONS);
        System.out.println("======================================\n");

        // 初始化狼群
        initializeWolves(M, N, cloudletList, vmList);

        // GWO迭代
        for (int t = 0; t < MAX_ITERATIONS; t++) {
            // 收敛因子a从2线性衰减到0
            double a = 2.0 - 2.0 * t / MAX_ITERATIONS;

            for (int i = 0; i < POPULATION_SIZE; i++) {
                double[] newPosition = new double[M];

                for (int j = 0; j < M; j++) {
                    // 包围Alpha
                    double r1 = random.nextDouble();
                    double r2 = random.nextDouble();
                    double A1 = 2 * a * r1 - a;
                    double C1 = 2 * r2;
                    double D_alpha = Math.abs(C1 * alphaPos[j] - wolves[i][j]);
                    double X1 = alphaPos[j] - A1 * D_alpha;

                    // 包围Beta
                    r1 = random.nextDouble();
                    r2 = random.nextDouble();
                    double A2 = 2 * a * r1 - a;
                    double C2 = 2 * r2;
                    double D_beta = Math.abs(C2 * betaPos[j] - wolves[i][j]);
                    double X2 = betaPos[j] - A2 * D_beta;

                    // 包围Delta
                    r1 = random.nextDouble();
                    r2 = random.nextDouble();
                    double A3 = 2 * a * r1 - a;
                    double C3 = 2 * r2;
                    double D_delta = Math.abs(C3 * deltaPos[j] - wolves[i][j]);
                    double X3 = deltaPos[j] - A3 * D_delta;

                    // 更新位置 (三个领导者的平均)
                    newPosition[j] = (X1 + X2 + X3) / 3.0;
                    newPosition[j] = clamp(newPosition[j], 0.0, 1.0);
                }

                // 计算适应度
                int[] schedule = continuousToDiscrete(newPosition, N);
                CostCalculator.CostResult result = CostCalculator.calculateWeightedCostDetails(
                        schedule, M, N, cloudletList, vmList);
                double newFitness = result.fitness;

                // 贪心选择
                if (newFitness < fitness[i]) {
                    System.arraycopy(newPosition, 0, wolves[i], 0, M);
                    fitness[i] = newFitness;
                }

                // 更新领导层级
                updateHierarchy(i, M);
            }

            // 记录收敛曲线
            int[] bestSchedule = continuousToDiscrete(alphaPos, N);
            CostCalculator.CostResult bestRes = CostCalculator.calculateWeightedCostDetails(
                    bestSchedule, M, N, cloudletList, vmList);
            if (convergenceRecord != null) {
                convergenceRecord.recordIteration(t, bestRes.fitness, bestRes.time, bestRes.load, bestRes.price);
            }

            if ((t + 1) % 20 == 0) {
                System.out.println(String.format("[GWO Iter %3d/%d] Alpha Fitness: %.4f",
                        t + 1, MAX_ITERATIONS, alphaScore));
            }
        }

        // 导出收敛曲线
        if (convergenceRecord != null) {
            convergenceRecord.exportToCSV("results/");
        }

        // 应用最优调度
        int[] bestSchedule = continuousToDiscrete(alphaPos, N);
        for (int i = 0; i < M; i++) {
            cloudletVmMapping.put(cloudletList.get(i).getId(), vmList.get(bestSchedule[i]));
        }

        System.out.println("\n========== GWO调度算法完成 ==========");
        System.out.println("最优Fitness: " + String.format("%.4f", alphaScore));
        System.out.println("======================================\n");
    }

    private void initializeWolves(int M, int N, List<Cloudlet> cloudletList, List<Vm> vmList) {
        wolves = new double[POPULATION_SIZE][M];
        fitness = new double[POPULATION_SIZE];

        alphaPos = new double[M];
        alphaScore = Double.MAX_VALUE;
        betaPos = new double[M];
        betaScore = Double.MAX_VALUE;
        deltaPos = new double[M];
        deltaScore = Double.MAX_VALUE;

        for (int i = 0; i < POPULATION_SIZE; i++) {
            for (int j = 0; j < M; j++) {
                wolves[i][j] = random.nextDouble();
            }
            int[] schedule = continuousToDiscrete(wolves[i], N);
            fitness[i] = CostCalculator.calculateWeightedFitness(schedule, M, N, cloudletList, vmList);
            updateHierarchy(i, M);
        }
    }

    private void updateHierarchy(int i, int M) {
        if (fitness[i] < alphaScore) {
            // 新Alpha，旧Alpha变Beta，旧Beta变Delta
            deltaScore = betaScore;
            System.arraycopy(betaPos, 0, deltaPos, 0, M);
            betaScore = alphaScore;
            System.arraycopy(alphaPos, 0, betaPos, 0, M);
            alphaScore = fitness[i];
            System.arraycopy(wolves[i], 0, alphaPos, 0, M);
        } else if (fitness[i] < betaScore) {
            deltaScore = betaScore;
            System.arraycopy(betaPos, 0, deltaPos, 0, M);
            betaScore = fitness[i];
            System.arraycopy(wolves[i], 0, betaPos, 0, M);
        } else if (fitness[i] < deltaScore) {
            deltaScore = fitness[i];
            System.arraycopy(wolves[i], 0, deltaPos, 0, M);
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
        return alphaScore;
    }
}

package com.edcbo.research;

import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.vms.Vm;

import com.edcbo.research.utils.*;

import java.util.*;

/**
 * LSCBO Pareto多目标优化版本
 * 
 * 使用Pareto支配替代加权求和，实现真正的多目标优化
 * 
 * 优化目标:
 * 1. Makespan - 任务完成时间 (最小化)
 * 2. Cost - 运行成本 (最小化)
 * 3. Energy - 能源消耗 (最小化)
 * 4. LoadBalance - 负载均衡度 (最小化)
 * 
 * @author LSCBO Research Team
 * @date 2025-12-23
 */
public class LSCBO_Broker_Pareto extends DatacenterBrokerSimple {

    // ==================== 算法参数 ====================
    protected static final int POPULATION_SIZE = 30;
    protected static final int MAX_ITERATIONS = 30;

    // Lévy飞行参数
    private static final double LEVY_LAMBDA = 1.5;
    private static final double LEVY_ALPHA_COEF = 0.01;
    private static final double PREY_SELECTION_PROB = 0.8;
    private double sigmaU;

    // ==================== 内部状态 ====================
    private double[][] population;
    private List<MultiObjectiveResult> paretoFront;
    private MultiObjectiveResult bestSolution;
    private Random random;
    private Map<Long, Vm> cloudletVmMapping;
    private boolean schedulingDone = false;

    // 用于外部获取
    // 权重配置: Makespan优先（云调度场景时间最重要）
    private double[] topsisWeights = { 0.50, 0.20, 0.20, 0.10 }; // [makespan, cost, energy, loadBalance]
    private boolean useKneePoint = false; // 是否使用膝点法替代TOPSIS

    // ==================== 构造函数 ====================

    public LSCBO_Broker_Pareto(CloudSimPlus simulation) {
        super(simulation);
        this.random = new Random();
        this.cloudletVmMapping = new HashMap<>();
        this.paretoFront = new ArrayList<>();
        calculateLevySigmaU();
    }

    public LSCBO_Broker_Pareto(CloudSimPlus simulation, long seed) {
        super(simulation);
        this.random = new Random(seed);
        this.cloudletVmMapping = new HashMap<>();
        this.paretoFront = new ArrayList<>();
        calculateLevySigmaU();
    }

    public LSCBO_Broker_Pareto(CloudSimPlus simulation, long seed, double[] weights) {
        this(simulation, seed);
        if (weights != null && weights.length == 4) {
            this.topsisWeights = weights.clone();
        }
    }

    private void calculateLevySigmaU() {
        double numerator = gamma(1 + LEVY_LAMBDA) * Math.sin(Math.PI * LEVY_LAMBDA / 2);
        double denominator = gamma((1 + LEVY_LAMBDA) / 2) * LEVY_LAMBDA * Math.pow(2, (LEVY_LAMBDA - 1) / 2);
        this.sigmaU = Math.pow(numerator / denominator, 1.0 / LEVY_LAMBDA);
    }

    private double gamma(double x) {
        return Math.exp(logGamma(x));
    }

    private double logGamma(double x) {
        double[] c = { 76.18009172947146, -86.50532032941677, 24.01409824083091,
                -1.231739572450155, 0.1208650973866179e-2, -0.5395239384953e-5 };
        double y = x, tmp = x + 5.5;
        tmp -= (x + 0.5) * Math.log(tmp);
        double sum = 1.000000000190015;
        for (int j = 0; j < 6; j++) {
            sum += c[j] / ++y;
        }
        return -tmp + Math.log(2.5066282746310005 * sum / x);
    }

    // ==================== 主算法流程 ====================

    @Override
    protected Vm defaultVmMapper(Cloudlet cloudlet) {
        if (!schedulingDone) {
            runParetoLSCBO();
            schedulingDone = true;
        }
        return cloudletVmMapping.getOrDefault(cloudlet.getId(), super.defaultVmMapper(cloudlet));
    }

    private void runParetoLSCBO() {
        List<Cloudlet> cloudletList = new ArrayList<>(getCloudletWaitingList());
        List<Vm> vmList = new ArrayList<>(getVmCreatedList());

        int M = cloudletList.size();
        int N = vmList.size();

        if (M == 0 || N == 0)
            return;

        // 初始化种群
        population = new double[POPULATION_SIZE][M];
        for (int i = 0; i < POPULATION_SIZE; i++) {
            for (int d = 0; d < M; d++) {
                population[i][d] = random.nextDouble();
            }
        }

        // 评估初始种群并建立Pareto前沿
        for (int i = 0; i < POPULATION_SIZE; i++) {
            MultiObjectiveResult result = evaluateMultiObjective(population[i], M, N, cloudletList, vmList);
            paretoFront = ParetoUtils.updateFront(paretoFront, result);
        }

        // 主循环
        for (int t = 1; t <= MAX_ITERATIONS; t++) {
            for (int i = 0; i < POPULATION_SIZE; i++) {
                double[] newPosition = new double[M];

                // 从Pareto前沿随机选择一个解作为引导
                MultiObjectiveResult guide = selectGuideFromFront();
                double[] guidePos = guide.continuousSolution;

                // Lévy飞行探索
                for (int d = 0; d < M; d++) {
                    double levyStep = generateLevyStep();
                    double alpha = LEVY_ALPHA_COEF * (1.0 - (double) t / MAX_ITERATIONS);

                    newPosition[d] = population[i][d]
                            + alpha * levyStep * (guidePos[d] - population[i][d]);
                    newPosition[d] = clamp(newPosition[d], 0, 1);
                }

                // CBO开发阶段
                if (random.nextDouble() < 0.5) {
                    MultiObjectiveResult anotherGuide = selectGuideFromFront();
                    double[] attackTarget = anotherGuide.continuousSolution;

                    for (int d = 0; d < M; d++) {
                        double r = random.nextDouble();
                        newPosition[d] = attackTarget[d] + r * (attackTarget[d] - newPosition[d]);
                        newPosition[d] = clamp(newPosition[d], 0, 1);
                    }
                }

                // 评估新解并更新Pareto前沿
                MultiObjectiveResult newResult = evaluateMultiObjective(newPosition, M, N, cloudletList, vmList);

                // 基于Pareto支配决定是否更新个体
                MultiObjectiveResult currentResult = evaluateMultiObjective(population[i], M, N, cloudletList, vmList);

                if (newResult.dominates(currentResult) ||
                        (!currentResult.dominates(newResult) && random.nextDouble() < 0.3)) {
                    population[i] = newPosition.clone();
                }

                paretoFront = ParetoUtils.updateFront(paretoFront, newResult);

                // 限制Pareto前沿大小
                if (paretoFront.size() > POPULATION_SIZE * 2) {
                    paretoFront = ParetoUtils.selectByDiversity(paretoFront, POPULATION_SIZE);
                }
            }
        }

        // 使用TOPSIS从Pareto前沿选择最终解
        bestSolution = TopsisDecision.select(paretoFront, topsisWeights);

        // 应用调度方案
        if (bestSolution != null && bestSolution.schedule != null) {
            for (int i = 0; i < M; i++) {
                cloudletVmMapping.put(cloudletList.get(i).getId(), vmList.get(bestSolution.schedule[i]));
            }
        }
    }

    private MultiObjectiveResult selectGuideFromFront() {
        if (paretoFront.isEmpty()) {
            return null;
        }
        // 80%概率选择Makespan最优的解，20%概率随机选择以保持多样性
        if (random.nextDouble() < 0.8) {
            // 选择Makespan最小的解
            return paretoFront.stream()
                    .min((a, b) -> Double.compare(a.makespan, b.makespan))
                    .orElse(paretoFront.get(0));
        } else {
            return paretoFront.get(random.nextInt(paretoFront.size()));
        }
    }

    private double generateLevyStep() {
        double u = random.nextGaussian() * sigmaU;
        double v = Math.abs(random.nextGaussian());
        return u / Math.pow(v, 1.0 / LEVY_LAMBDA);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    // ==================== 多目标评估 ====================

    private MultiObjectiveResult evaluateMultiObjective(double[] individual, int M, int N,
            List<Cloudlet> cloudletList, List<Vm> vmList) {
        int[] schedule = continuousToDiscrete(individual, N);

        // 计算4个目标
        double makespan = calculateMakespan(schedule, M, N, cloudletList, vmList);
        double cost = CostCalculator.calculateCost(schedule, M, N, cloudletList, vmList);
        double energy = EnergyCalculator.calculateEnergy(schedule, M, N, cloudletList, vmList);
        double loadBalance = LoadBalanceCalculator.calculateLoadBalanceIndex(schedule, M, N, cloudletList, vmList);

        return new MultiObjectiveResult(makespan, cost, energy, loadBalance, schedule, individual);
    }

    private double calculateMakespan(int[] schedule, int M, int N,
            List<Cloudlet> cloudletList, List<Vm> vmList) {
        double[] vmRuntimes = new double[N];

        for (int i = 0; i < M; i++) {
            int vmIdx = schedule[i];
            double taskLength = cloudletList.get(i).getLength();
            double vmMips = vmList.get(vmIdx).getMips();
            vmRuntimes[vmIdx] += taskLength / vmMips;
        }

        double maxTime = 0;
        for (double t : vmRuntimes) {
            if (t > maxTime)
                maxTime = t;
        }
        return maxTime;
    }

    private int[] continuousToDiscrete(double[] continuous, int N) {
        int[] discrete = new int[continuous.length];
        for (int i = 0; i < continuous.length; i++) {
            discrete[i] = (int) (continuous[i] * N);
            if (discrete[i] >= N)
                discrete[i] = N - 1;
        }
        return discrete;
    }

    // ==================== 公共接口 ====================

    public List<MultiObjectiveResult> getParetoFront() {
        return new ArrayList<>(paretoFront);
    }

    public MultiObjectiveResult getBestSolution() {
        return bestSolution;
    }

    public double getInternalMakespan() {
        return bestSolution != null ? bestSolution.makespan : 0;
    }

    public String getAlgorithmName() {
        return "LSCBO-Pareto";
    }

    public void setTopsisWeights(double[] weights) {
        if (weights != null && weights.length == 4) {
            this.topsisWeights = weights.clone();
        }
    }
}

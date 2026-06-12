package com.edcbo.research;

import com.edcbo.research.utils.ConvergenceRecord;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.vms.Vm;

import java.util.*;

/**
 * CBO (Coyote and Badger Optimization) Broker for Cloud Task Scheduling
 *
 * 基于郊狼和獾协作狩猎行为的云任务调度算法
 * 标准CBO动态方法实现 (Dynamic Approach - Stages 1, 3, 5)
 *
 * 算法三阶段：
 * - Phase 1: Searching（搜索阶段） - Stage 1: Dynamic Searching
 *   使用tanh函数进行非线性连续移动
 *   公式: x^{i+1} = x^i + r * tanh(d) * (x_prey - x^i)
 *
 * - Phase 2: Encircling（包围阶段） - Stage 3: Dynamic Encircling
 *   使用旋转矩阵M收紧包围圈
 *   旋转角度: θ = 2π * t / T_max
 *   旋转矩阵: M = [cos(θ) -sin(θ); sin(θ) cos(θ)]
 *
 * - Phase 3: Attacking（攻击阶段） - Stage 5: Dynamic Attacking
 *   向领导者位置收敛（Leader Following）
 *   公式: x^{i+1} = (x^i + x_leader) / 2
 */
public class CBO_Broker extends DatacenterBrokerSimple {

    // CBO算法参数
    protected static final int POPULATION_SIZE = 30;      // 种群大小
    protected static final int MAX_ITERATIONS = 100;      // 最大迭代次数
    protected final Random random;
    protected final long seed;  // 随机种子

    // CBO种群：每个个体是一个调度方案（连续空间 [0,1]）
    protected double[][] population;      // population[i][j]: 第i个个体，第j个任务的位置
    protected double[] fitness;           // 每个个体的适应度（Makespan）
    protected double[] bestSolution;      // 全局最优解（连续空间）
    protected double bestFitness;         // 全局最优适应度

    // ✅ Phase 3新增：收敛曲线记录
    protected ConvergenceRecord convergenceRecord;

    // 调度结果缓存
    private Map<Long, Vm> cloudletVmMapping;  // cloudletId -> Vm
    private boolean schedulingDone = false;

    /**
     * 构造函数（带随机种子）
     * @param simulation CloudSim仿真实例
     * @param seed 随机种子
     */
    public CBO_Broker(CloudSimPlus simulation, long seed) {
        super(simulation);
        this.seed = seed;
        this.random = new Random(seed);
        this.cloudletVmMapping = new HashMap<>();
    }

    /**
     * 构造函数（向后兼容，使用默认种子42）
     * @param simulation CloudSim仿真实例
     */
    public CBO_Broker(CloudSimPlus simulation) {
        this(simulation, 42L);
    }

    /**
     * 重写VM映射方法，实现CBO调度算法
     * 该方法在每个Cloudlet需要分配VM时被调用
     */
    @Override
    protected Vm defaultVmMapper(Cloudlet cloudlet) {
        // 如果还没有运行CBO算法，先运行一次
        if (!schedulingDone) {
            runCBOScheduling();
            schedulingDone = true;
        }

        // 返回预先计算好的VM映射（guaranteed fallback 防止 Vm.NULL 导致无限循环）
        Vm mapped = cloudletVmMapping.get(cloudlet.getId());
        if (mapped != null) return mapped;
        List<Vm> created = getVmCreatedList();
        if (!created.isEmpty()) return created.get((int)(Math.abs(cloudlet.getId()) % created.size()));
        return super.defaultVmMapper(cloudlet);
    }

    /**
     * 运行CBO算法进行任务调度
     */
    private void runCBOScheduling() {
        List<Cloudlet> cloudletList = new ArrayList<>(getCloudletWaitingList());
        List<Vm> vmList = new ArrayList<>(getVmCreatedList());

        // 如果没有任务或VM，直接返回
        if (cloudletList.isEmpty() || vmList.isEmpty()) {
            return;
        }

        int M = cloudletList.size();  // 任务数
        int N = vmList.size();        // VM数

        System.out.println("\n==================== CBO算法开始 ====================");
        System.out.println("任务数: " + M);
        System.out.println("VM数: " + N);
        System.out.println("种群大小: " + POPULATION_SIZE);
        System.out.println("最大迭代次数: " + MAX_ITERATIONS);
        System.out.println("====================================================\n");

        // 运行CBO算法获取最优调度方案
        long startTime = System.currentTimeMillis();
        int[] bestSchedule = runCBO(cloudletList, vmList);
        long endTime = System.currentTimeMillis();

        System.out.println("\n==================== CBO算法完成 ====================");
        System.out.println("最优Makespan: " + String.format("%.4f", bestFitness));
        System.out.println("算法运行时间: " + (endTime - startTime) + " ms");
        System.out.println("====================================================\n");

        // 构建Cloudlet到VM的映射
        for (int i = 0; i < cloudletList.size(); i++) {
            Cloudlet cloudlet = cloudletList.get(i);
            Vm vm = vmList.get(bestSchedule[i]);
            cloudletVmMapping.put(cloudlet.getId(), vm);
        }
    }

    /**
     * CBO算法核心实现
     * @param cloudletList 任务列表
     * @param vmList VM列表
     * @return 最优调度方案（离散空间）
     */
    protected int[] runCBO(List<Cloudlet> cloudletList, List<Vm> vmList) {
        int M = cloudletList.size();  // 任务数
        int N = vmList.size();        // VM数

        // ✅ Phase 3：创建收敛记录器
        String scale = String.format("M%d", M);
        this.convergenceRecord = new ConvergenceRecord("CBO", scale, this.seed);

        // 初始化种群
        initializePopulation(M, N, cloudletList, vmList);

        // CBO迭代
        for (int t = 0; t < MAX_ITERATIONS; t++) {
            // Phase 1: Searching（搜索阶段）
            searchingPhase(M, N, cloudletList, vmList);

            // Phase 2: Encircling（包围阶段）
            encirclingPhase(M, N, t, cloudletList, vmList);

            // Phase 3: Attacking（攻击阶段）
            attackingPhase(M, N, cloudletList, vmList);

            // 更新全局最优解
            updateBestSolution(M, N, cloudletList, vmList);

            // ✅ Phase 3：记录收敛曲线
            convergenceRecord.recordIteration(t, bestFitness);

            // 每10次迭代打印进度
            if ((t + 1) % 10 == 0 || t == 0) {
                System.out.println(String.format("[Iteration %3d/%d] Best Makespan: %.4f",
                    t + 1, MAX_ITERATIONS, bestFitness));
            }
        }

        // ✅ Day 3.1新增：导出收敛曲线到CSV
        convergenceRecord.exportToCSV("results/");

        // 将最优解从连续空间转换为离散空间
        return continuousToDiscrete(bestSolution, N);
    }

    /**
     * 初始化种群
     */
    protected void initializePopulation(int M, int N, List<Cloudlet> cloudletList, List<Vm> vmList) {
        population = new double[POPULATION_SIZE][M];
        fitness = new double[POPULATION_SIZE];
        bestSolution = new double[M];
        bestFitness = Double.MAX_VALUE;

        // 随机初始化种群位置
        for (int i = 0; i < POPULATION_SIZE; i++) {
            for (int j = 0; j < M; j++) {
                population[i][j] = random.nextDouble();  // [0, 1]
            }
            fitness[i] = calculateFitness(population[i], M, N, cloudletList, vmList);
        }

        // 找到初始最优解
        updateBestSolution(M, N, cloudletList, vmList);
    }

    /**
     * Phase 1: Searching Phase（搜索阶段） - Stage 1: Dynamic Searching
     * 全局探索，使用tanh函数进行非线性连续移动
     *
     * 标准CBO动态搜索公式:
     * x^{i+1} = x^i + r * tanh(d) * (x_prey - x^i)
     *
     * 其中:
     * - d = |x_prey - x^i| (与猎物的距离)
     * - r ~ U(0,1) (随机因子)
     * - tanh(d) 提供非线性衰减效应
     */
    protected void searchingPhase(int M, int N, List<Cloudlet> cloudletList, List<Vm> vmList) {
        for (int i = 0; i < POPULATION_SIZE; i++) {
            double[] newPosition = new double[M];

            // 随机选择猎物（best solution或随机个体）
            int preyIdx = random.nextDouble() < 0.5 ?
                          findBestIndividualIndex() :
                          random.nextInt(POPULATION_SIZE);

            for (int j = 0; j < M; j++) {
                // 计算与猎物的距离
                double distance = Math.abs(population[preyIdx][j] - population[i][j]);

                // 标准CBO动态搜索公式
                // x_new = x_old + r * tanh(d) * (x_prey - x_old)
                double r = random.nextDouble();

                newPosition[j] = population[i][j]
                    + r * Math.tanh(distance) * (population[preyIdx][j] - population[i][j]);

                // 边界处理：保持在[0, 1]范围内
                newPosition[j] = Math.max(0.0, Math.min(1.0, newPosition[j]));
            }

            // 贪心选择：如果新位置更好，则更新
            double newFitness = calculateFitness(newPosition, M, N, cloudletList, vmList);
            if (newFitness < fitness[i]) {
                population[i] = newPosition;
                fitness[i] = newFitness;
            }
        }
    }

    /**
     * 找到当前种群中最优个体的索引
     */
    private int findBestIndividualIndex() {
        int bestIdx = 0;
        double bestFit = fitness[0];
        for (int i = 1; i < POPULATION_SIZE; i++) {
            if (fitness[i] < bestFit) {
                bestFit = fitness[i];
                bestIdx = i;
            }
        }
        return bestIdx;
    }

    /**
     * Phase 2: Encircling Phase（包围阶段） - Stage 3: Dynamic Encircling
     * 使用旋转矩阵收紧包围圈
     *
     * 标准CBO动态包围：
     * 1. 计算旋转角度: θ = 2π * t / T_max
     * 2. 应用旋转矩阵M到位置向量
     * 3. 旋转矩阵 M = [cos(θ) -sin(θ)]
     *                  [sin(θ)  cos(θ)]
     *
     * 对于多维问题，在每对维度之间应用旋转
     */
    protected void encirclingPhase(int M, int N, int t, List<Cloudlet> cloudletList, List<Vm> vmList) {
        // 计算旋转角度：随迭代增加而增加
        double theta = 2.0 * Math.PI * t / MAX_ITERATIONS;
        double cosTheta = Math.cos(theta);
        double sinTheta = Math.sin(theta);

        for (int i = 0; i < POPULATION_SIZE; i++) {
            double[] newPosition = new double[M];

            // 对每对相邻维度应用旋转矩阵
            for (int j = 0; j < M - 1; j += 2) {
                // 获取当前维度对相对于最优解的位置
                double dx = population[i][j] - bestSolution[j];
                double dy = population[i][j + 1] - bestSolution[j + 1];

                // 应用旋转矩阵
                // [x_new]   [cos(θ) -sin(θ)] [dx]   [x_best]
                // [y_new] = [sin(θ)  cos(θ)] [dy] + [y_best]
                double rotatedX = dx * cosTheta - dy * sinTheta;
                double rotatedY = dx * sinTheta + dy * cosTheta;

                newPosition[j] = bestSolution[j] + rotatedX;
                newPosition[j + 1] = bestSolution[j + 1] + rotatedY;

                // 边界处理
                newPosition[j] = Math.max(0.0, Math.min(1.0, newPosition[j]));
                newPosition[j + 1] = Math.max(0.0, Math.min(1.0, newPosition[j + 1]));
            }

            // 如果维度数是奇数，最后一个维度保持不变或简单线性收敛
            if (M % 2 == 1) {
                int lastDim = M - 1;
                double A = 2.0 - 2.0 * t / MAX_ITERATIONS;  // 收敛因子
                double C = A * (2 * random.nextDouble() - 1);
                newPosition[lastDim] = population[i][lastDim]
                                     + C * (bestSolution[lastDim] - population[i][lastDim]);
                newPosition[lastDim] = Math.max(0.0, Math.min(1.0, newPosition[lastDim]));
            }

            // 贪心选择
            double newFitness = calculateFitness(newPosition, M, N, cloudletList, vmList);
            if (newFitness < fitness[i]) {
                population[i] = newPosition;
                fitness[i] = newFitness;
            }
        }
    }

    /**
     * Phase 3: Attacking Phase（攻击阶段） - Stage 5: Dynamic Attacking
     * 向领导者位置收敛（Leader Following）
     *
     * 标准CBO动态攻击公式:
     * x^{i+1} = (x^i + x_leader) / 2
     *
     * 实现方式：使用静态权重0.5计算当前位置和领导者位置的算术平均
     * 这里领导者就是全局最优解(bestSolution)
     *
     * 注意：LSCBO会重写此方法，使用动态惯性权重替代静态0.5权重
     */
    protected void attackingPhase(int M, int N, List<Cloudlet> cloudletList, List<Vm> vmList) {
        for (int i = 0; i < POPULATION_SIZE; i++) {
            double[] newPosition = new double[M];

            for (int j = 0; j < M; j++) {
                // CBO标准公式: x_new = 0.5*x_old + 0.5*x_best
                newPosition[j] = 0.5 * population[i][j] + 0.5 * bestSolution[j];

                // 边界处理
                newPosition[j] = Math.max(0.0, Math.min(1.0, newPosition[j]));
            }

            // 贪心选择
            double newFitness = calculateFitness(newPosition, M, N, cloudletList, vmList);
            if (newFitness < fitness[i]) {
                population[i] = newPosition;
                fitness[i] = newFitness;
            }
        }
    }

    /**
     * 更新全局最优解
     */
    protected void updateBestSolution(int M, int N, List<Cloudlet> cloudletList, List<Vm> vmList) {
        for (int i = 0; i < POPULATION_SIZE; i++) {
            if (fitness[i] < bestFitness) {
                bestFitness = fitness[i];
                bestSolution = Arrays.copyOf(population[i], M);
            }
        }
    }

    /**
     * 计算适应度（Makespan）
     * @param individual 个体（连续空间位置）
     * @return Makespan（最大完成时间）
     */
    protected double calculateFitness(double[] individual, int M, int N,
                                     List<Cloudlet> cloudletList, List<Vm> vmList) {
        // 将连续位置映射到离散VM索引
        int[] schedule = continuousToDiscrete(individual, N);

        // 计算每个VM的负载
        double[] vmLoads = new double[N];
        for (int i = 0; i < M; i++) {
            int vmIdx = schedule[i];
            double taskLength = cloudletList.get(i).getLength();
            double vmMips = vmList.get(vmIdx).getMips();
            vmLoads[vmIdx] += taskLength / vmMips;
        }

        // Makespan = 最大VM负载
        double makespan = 0;
        for (double load : vmLoads) {
            makespan = Math.max(makespan, load);
        }

        return makespan;
    }

    /**
     * 将连续空间位置转换为离散VM索引
     * @param continuous 连续空间位置 [0, 1]
     * @param N VM数量
     * @return 离散VM索引 [0, N-1]
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
     * ✅ Phase 3：获取收敛记录
     * @return 收敛记录对象，如果尚未运行优化则返回null
     */
    public ConvergenceRecord getConvergenceRecord() {
        return convergenceRecord;
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

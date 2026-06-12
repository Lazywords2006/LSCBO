package com.edcbo.research;

import com.edcbo.research.utils.ConvergenceRecord;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.vms.Vm;

import java.util.*;

/**
 * PSO (Particle Swarm Optimization) Broker for Cloud Task Scheduling
 *
 * 粒子群优化算法 - 模拟鸟群觅食行为
 * - 速度-位置更新机制
 * - 个体最优（pBest）+ 全局最优（gBest）双向学习
 * - 惯性权重线性衰减（0.9 → 0.4）
 *
 * @author LSCBO Research Team
 * @date 2025-12-16
 */
public class PSO_Broker extends DatacenterBrokerSimple {

    // PSO算法参数
    protected static final int POPULATION_SIZE = 30;
    protected static final int MAX_ITERATIONS = 100;
    protected static final double W_MAX = 0.9;        // 最大惯性权重
    protected static final double W_MIN = 0.4;        // 最小惯性权重
    protected static final double C1 = 1.5;           // 认知学习率
    protected static final double C2 = 1.5;           // 社会学习率
    protected static final double V_MAX = 0.2;        // 最大速度

    protected final Random random;
    protected final long seed;

    // PSO种群数据
    protected double[][] particles;      // 粒子位置 [0,1]
    protected double[][] velocities;     // 粒子速度
    protected double[][] pBest;          // 个体最优位置
    protected double[] pBestFitness;     // 个体最优适应度
    protected double[] gBest;            // 全局最优位置
    protected double gBestFitness;       // 全局最优适应度

    protected ConvergenceRecord convergenceRecord;

    // 调度结果
    private Map<Long, Vm> cloudletVmMapping;
    private boolean schedulingDone = false;

    /**
     * 构造函数（带随机种子）
     */
    public PSO_Broker(CloudSimPlus simulation, long seed) {
        super(simulation);
        this.seed = seed;
        this.random = new Random(seed);
        this.cloudletVmMapping = new HashMap<>();
    }

    /**
     * 构造函数（向后兼容）
     */
    public PSO_Broker(CloudSimPlus simulation) {
        this(simulation, 42L);
    }

    /**
     * 设置收敛记录器
     */
    public void setConvergenceRecord(ConvergenceRecord record) {
        this.convergenceRecord = record;
    }

    @Override
    protected Vm defaultVmMapper(Cloudlet cloudlet) {
        if (!schedulingDone) {
            runPSOScheduling();
            schedulingDone = true;
        }
        return cloudletVmMapping.getOrDefault(cloudlet.getId(), super.defaultVmMapper(cloudlet));
    }

    /**
     * 运行PSO调度算法
     */
    private void runPSOScheduling() {
        List<Cloudlet> cloudletList = new ArrayList<>(getCloudletWaitingList());
        List<Vm> vmList = new ArrayList<>(getVmCreatedList());

        if (cloudletList.isEmpty() || vmList.isEmpty()) {
            return;
        }

        int M = cloudletList.size();
        int N = vmList.size();

        // 初始化粒子群
        initializeSwarm(M, N, cloudletList, vmList);

        // PSO迭代
        for (int iter = 0; iter < MAX_ITERATIONS; iter++) {
            // 计算惯性权重（线性衰减）
            double w = W_MAX - (W_MAX - W_MIN) * iter / MAX_ITERATIONS;

            // 更新每个粒子
            for (int i = 0; i < POPULATION_SIZE; i++) {
                for (int j = 0; j < M; j++) {
                    double r1 = random.nextDouble();
                    double r2 = random.nextDouble();

                    // PSO速度更新公式
                    velocities[i][j] = w * velocities[i][j]
                            + C1 * r1 * (pBest[i][j] - particles[i][j])
                            + C2 * r2 * (gBest[j] - particles[i][j]);

                    // 速度限制
                    if (velocities[i][j] > V_MAX) velocities[i][j] = V_MAX;
                    if (velocities[i][j] < -V_MAX) velocities[i][j] = -V_MAX;

                    // 位置更新
                    particles[i][j] = particles[i][j] + velocities[i][j];

                    // 边界处理
                    if (particles[i][j] > 1.0) particles[i][j] = 1.0;
                    if (particles[i][j] < 0.0) particles[i][j] = 0.0;
                }

                // 计算适应度
                int[] schedule = continuousToDiscrete(particles[i], N);
                double fitness = calculateFitness(schedule, M, N, cloudletList, vmList);

                // 更新个体最优
                if (fitness < pBestFitness[i]) {
                    pBestFitness[i] = fitness;
                    System.arraycopy(particles[i], 0, pBest[i], 0, M);
                }

                // 更新全局最优
                if (fitness < gBestFitness) {
                    gBestFitness = fitness;
                    System.arraycopy(particles[i], 0, gBest, 0, M);
                }
            }

            // 记录收敛曲线
            if (convergenceRecord != null) {
                convergenceRecord.recordIteration(iter, gBestFitness);
            }
        }

        // 保存最优调度方案
        int[] bestSchedule = continuousToDiscrete(gBest, N);
        for (int i = 0; i < M; i++) {
            cloudletVmMapping.put(cloudletList.get(i).getId(), vmList.get(bestSchedule[i]));
        }
    }

    /**
     * 初始化粒子群
     */
    private void initializeSwarm(int M, int N, List<Cloudlet> cloudletList, List<Vm> vmList) {
        particles = new double[POPULATION_SIZE][M];
        velocities = new double[POPULATION_SIZE][M];
        pBest = new double[POPULATION_SIZE][M];
        pBestFitness = new double[POPULATION_SIZE];
        gBest = new double[M];
        gBestFitness = Double.MAX_VALUE;

        for (int i = 0; i < POPULATION_SIZE; i++) {
            // 随机初始化位置和速度
            for (int j = 0; j < M; j++) {
                particles[i][j] = random.nextDouble();
                velocities[i][j] = (random.nextDouble() - 0.5) * 2 * V_MAX;
                pBest[i][j] = particles[i][j];
            }

            // 计算初始适应度
            int[] schedule = continuousToDiscrete(particles[i], N);
            double fitness = calculateFitness(schedule, M, N, cloudletList, vmList);
            pBestFitness[i] = fitness;

            // 更新全局最优
            if (fitness < gBestFitness) {
                gBestFitness = fitness;
                System.arraycopy(particles[i], 0, gBest, 0, M);
            }
        }
    }

    /**
     * 连续空间到离散空间的映射
     */
    protected int[] continuousToDiscrete(double[] continuous, int N) {
        int[] discrete = new int[continuous.length];
        for (int i = 0; i < continuous.length; i++) {
            discrete[i] = (int) (continuous[i] * N);
            if (discrete[i] >= N) discrete[i] = N - 1;
        }
        return discrete;
    }

    /**
     * 计算适应度（Makespan）
     */
    protected double calculateFitness(int[] schedule, int M, int N,
                                     List<Cloudlet> cloudletList, List<Vm> vmList) {
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
     * 获取内部Makespan
     */
    public double getInternalMakespan() {
        return gBestFitness;
    }
}

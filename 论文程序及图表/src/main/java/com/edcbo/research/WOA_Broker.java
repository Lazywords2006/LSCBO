package com.edcbo.research;

import com.edcbo.research.utils.ConvergenceRecord;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.vms.Vm;

import java.util.*;

/**
 * WOA (Whale Optimization Algorithm) Broker for Cloud Task Scheduling
 *
 * 鲸鱼优化算法 - 模拟座头鲸的狩猎行为
 * - 包围猎物（Encircling Prey）：收敛系数控制
 * - 气泡网攻击（Bubble-net Attacking）：螺旋更新机制
 * - 探索猎物（Search for Prey）：随机搜索
 *
 * @author LSCBO Research Team
 * @date 2025-12-16
 */
public class WOA_Broker extends DatacenterBrokerSimple {

    // WOA算法参数
    protected static final int POPULATION_SIZE = 30;
    protected static final int MAX_ITERATIONS = 100;
    protected static final double B = 1.0;   // 螺旋形状常数

    protected final Random random;
    protected final long seed;

    // 种群数据
    protected double[][] whales;         // 鲸鱼位置 [0,1]
    protected double[] fitness;          // 适应度
    protected double[] bestWhale;        // 最优鲸鱼位置
    protected double bestFitness;        // 最优适应度

    protected ConvergenceRecord convergenceRecord;

    // 调度结果
    private Map<Long, Vm> cloudletVmMapping;
    private boolean schedulingDone = false;

    /**
     * 构造函数（带随机种子）
     */
    public WOA_Broker(CloudSimPlus simulation, long seed) {
        super(simulation);
        this.seed = seed;
        this.random = new Random(seed);
        this.cloudletVmMapping = new HashMap<>();
    }

    /**
     * 构造函数（向后兼容）
     */
    public WOA_Broker(CloudSimPlus simulation) {
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
            runWOAScheduling();
            schedulingDone = true;
        }
        return cloudletVmMapping.getOrDefault(cloudlet.getId(), super.defaultVmMapper(cloudlet));
    }

    /**
     * 运行WOA调度算法
     */
    private void runWOAScheduling() {
        List<Cloudlet> cloudletList = new ArrayList<>(getCloudletWaitingList());
        List<Vm> vmList = new ArrayList<>(getVmCreatedList());

        if (cloudletList.isEmpty() || vmList.isEmpty()) {
            return;
        }

        int M = cloudletList.size();
        int N = vmList.size();

        // 初始化种群
        initializePopulation(M, N, cloudletList, vmList);

        // WOA迭代
        for (int iter = 0; iter < MAX_ITERATIONS; iter++) {
            // 计算收敛系数a（从2线性减少到0）
            double a = 2.0 - iter * (2.0 / MAX_ITERATIONS);

            // 更新每条鲸鱼的位置
            for (int i = 0; i < POPULATION_SIZE; i++) {
                double r = random.nextDouble();
                double A = 2 * a * r - a;
                double C = 2 * r;

                double p = random.nextDouble();  // [0,1]
                double l = random.nextDouble() * 2 - 1;  // [-1,1]

                for (int j = 0; j < M; j++) {
                    if (p < 0.5) {
                        if (Math.abs(A) < 1) {
                            // 包围猎物（Encircling Prey）
                            double D = Math.abs(C * bestWhale[j] - whales[i][j]);
                            whales[i][j] = bestWhale[j] - A * D;
                        } else {
                            // 探索猎物（Search for Prey）- 随机选择另一条鲸鱼
                            int randomWhale = random.nextInt(POPULATION_SIZE);
                            double D = Math.abs(C * whales[randomWhale][j] - whales[i][j]);
                            whales[i][j] = whales[randomWhale][j] - A * D;
                        }
                    } else {
                        // 气泡网攻击（Bubble-net Attacking）- 螺旋更新
                        double D = Math.abs(bestWhale[j] - whales[i][j]);
                        whales[i][j] = D * Math.exp(B * l) * Math.cos(2 * Math.PI * l) + bestWhale[j];
                    }

                    // 边界处理
                    if (whales[i][j] > 1.0) whales[i][j] = 1.0;
                    if (whales[i][j] < 0.0) whales[i][j] = 0.0;
                }

                // 计算适应度
                int[] schedule = continuousToDiscrete(whales[i], N);
                fitness[i] = calculateFitness(schedule, M, N, cloudletList, vmList);

                // 更新最优解
                if (fitness[i] < bestFitness) {
                    bestFitness = fitness[i];
                    System.arraycopy(whales[i], 0, bestWhale, 0, M);
                }
            }

            // 记录收敛曲线
            if (convergenceRecord != null) {
                convergenceRecord.recordIteration(iter, bestFitness);
            }
        }

        // 保存最优调度方案
        int[] bestSchedule = continuousToDiscrete(bestWhale, N);
        for (int i = 0; i < M; i++) {
            cloudletVmMapping.put(cloudletList.get(i).getId(), vmList.get(bestSchedule[i]));
        }
    }

    /**
     * 初始化种群
     */
    private void initializePopulation(int M, int N, List<Cloudlet> cloudletList, List<Vm> vmList) {
        whales = new double[POPULATION_SIZE][M];
        fitness = new double[POPULATION_SIZE];
        bestWhale = new double[M];
        bestFitness = Double.MAX_VALUE;

        for (int i = 0; i < POPULATION_SIZE; i++) {
            // 随机初始化位置
            for (int j = 0; j < M; j++) {
                whales[i][j] = random.nextDouble();
            }

            // 计算适应度
            int[] schedule = continuousToDiscrete(whales[i], N);
            fitness[i] = calculateFitness(schedule, M, N, cloudletList, vmList);

            // 更新最优解
            if (fitness[i] < bestFitness) {
                bestFitness = fitness[i];
                System.arraycopy(whales[i], 0, bestWhale, 0, M);
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
        return bestFitness;
    }
}

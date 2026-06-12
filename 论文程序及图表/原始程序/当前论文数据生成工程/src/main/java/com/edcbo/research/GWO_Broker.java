package com.edcbo.research;

import com.edcbo.research.utils.ConvergenceRecord;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.vms.Vm;

import java.util.*;

/**
 * GWO (Grey Wolf Optimizer) Broker for Cloud Task Scheduling
 *
 * 灰狼优化算法 - 模拟灰狼捕猎行为
 * - 社会等级：Alpha（领导者）、Beta（副手）、Delta（第三位）、Omega（跟随者）
 * - 包围机制：Alpha、Beta、Delta引导狼群位置更新
 * - 收敛系数a：从2线性衰减到0
 *
 * @author LSCBO Research Team
 * @date 2025-12-16
 */
public class GWO_Broker extends DatacenterBrokerSimple {

    // GWO算法参数
    protected static final int POPULATION_SIZE = 30;
    protected static final int MAX_ITERATIONS = 100;

    protected final Random random;
    protected final long seed;

    // 狼群数据
    protected double[][] wolves;         // 狼群位置 [0,1]
    protected double[] fitness;          // 适应度

    // 社会等级（前三位领导者）
    protected double[] alphaPos;         // Alpha狼位置
    protected double alphaScore;         // Alpha狼适应度
    protected double[] betaPos;          // Beta狼位置
    protected double betaScore;          // Beta狼适应度
    protected double[] deltaPos;         // Delta狼位置
    protected double deltaScore;         // Delta狼适应度

    protected ConvergenceRecord convergenceRecord;

    // 调度结果
    private Map<Long, Vm> cloudletVmMapping;
    private boolean schedulingDone = false;

    /**
     * 构造函数（带随机种子）
     */
    public GWO_Broker(CloudSimPlus simulation, long seed) {
        super(simulation);
        this.seed = seed;
        this.random = new Random(seed);
        this.cloudletVmMapping = new HashMap<>();
    }

    /**
     * 构造函数（向后兼容）
     */
    public GWO_Broker(CloudSimPlus simulation) {
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
            runGWOScheduling();
            schedulingDone = true;
        }
        Vm mapped = cloudletVmMapping.get(cloudlet.getId());
        if (mapped != null) return mapped;
        List<Vm> created = getVmCreatedList();
        if (!created.isEmpty()) return created.get((int)(Math.abs(cloudlet.getId()) % created.size()));
        return super.defaultVmMapper(cloudlet);
    }

    /**
     * 运行GWO调度算法
     */
    private void runGWOScheduling() {
        List<Cloudlet> cloudletList = new ArrayList<>(getCloudletWaitingList());
        List<Vm> vmList = new ArrayList<>(getVmCreatedList());

        if (cloudletList.isEmpty() || vmList.isEmpty()) {
            return;
        }

        int M = cloudletList.size();
        int N = vmList.size();

        // 初始化狼群
        initializeWolves(M, N, cloudletList, vmList);

        // GWO迭代
        for (int iter = 0; iter < MAX_ITERATIONS; iter++) {
            // 计算包围系数a（从2线性减少到0）
            double a = 2.0 - iter * (2.0 / MAX_ITERATIONS);

            // 更新每只狼的位置
            for (int i = 0; i < POPULATION_SIZE; i++) {
                for (int j = 0; j < M; j++) {
                    // 随机向量
                    double r1 = random.nextDouble();
                    double r2 = random.nextDouble();

                    // Alpha引导
                    double A1 = 2 * a * r1 - a;
                    double C1 = 2 * r2;
                    double D_alpha = Math.abs(C1 * alphaPos[j] - wolves[i][j]);
                    double X1 = alphaPos[j] - A1 * D_alpha;

                    // Beta引导
                    r1 = random.nextDouble();
                    r2 = random.nextDouble();
                    double A2 = 2 * a * r1 - a;
                    double C2 = 2 * r2;
                    double D_beta = Math.abs(C2 * betaPos[j] - wolves[i][j]);
                    double X2 = betaPos[j] - A2 * D_beta;

                    // Delta引导
                    r1 = random.nextDouble();
                    r2 = random.nextDouble();
                    double A3 = 2 * a * r1 - a;
                    double C3 = 2 * r2;
                    double D_delta = Math.abs(C3 * deltaPos[j] - wolves[i][j]);
                    double X3 = deltaPos[j] - A3 * D_delta;

                    // 更新位置（三个领导者的平均）
                    wolves[i][j] = (X1 + X2 + X3) / 3.0;

                    // 边界处理
                    if (wolves[i][j] > 1.0) wolves[i][j] = 1.0;
                    if (wolves[i][j] < 0.0) wolves[i][j] = 0.0;
                }

                // 计算适应度
                int[] schedule = continuousToDiscrete(wolves[i], N);
                fitness[i] = calculateFitness(schedule, M, N, cloudletList, vmList);

                // 更新社会等级
                updateHierarchy(i, M);
            }

            // 记录收敛曲线
            if (convergenceRecord != null) {
                convergenceRecord.recordIteration(iter, alphaScore);
            }
        }

        // 保存最优调度方案
        int[] bestSchedule = continuousToDiscrete(alphaPos, N);
        for (int i = 0; i < M; i++) {
            cloudletVmMapping.put(cloudletList.get(i).getId(), vmList.get(bestSchedule[i]));
        }
    }

    /**
     * 初始化狼群
     */
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
            // 随机初始化位置
            for (int j = 0; j < M; j++) {
                wolves[i][j] = random.nextDouble();
            }

            // 计算适应度
            int[] schedule = continuousToDiscrete(wolves[i], N);
            fitness[i] = calculateFitness(schedule, M, N, cloudletList, vmList);

            // 更新社会等级
            updateHierarchy(i, M);
        }
    }

    /**
     * 更新社会等级（Alpha, Beta, Delta）
     */
    private void updateHierarchy(int i, int M) {
        if (fitness[i] < alphaScore) {
            // 新的Alpha
            deltaScore = betaScore;
            System.arraycopy(betaPos, 0, deltaPos, 0, M);

            betaScore = alphaScore;
            System.arraycopy(alphaPos, 0, betaPos, 0, M);

            alphaScore = fitness[i];
            System.arraycopy(wolves[i], 0, alphaPos, 0, M);

        } else if (fitness[i] < betaScore) {
            // 新的Beta
            deltaScore = betaScore;
            System.arraycopy(betaPos, 0, deltaPos, 0, M);

            betaScore = fitness[i];
            System.arraycopy(wolves[i], 0, betaPos, 0, M);

        } else if (fitness[i] < deltaScore) {
            // 新的Delta
            deltaScore = fitness[i];
            System.arraycopy(wolves[i], 0, deltaPos, 0, M);
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
        return alphaScore;
    }
}

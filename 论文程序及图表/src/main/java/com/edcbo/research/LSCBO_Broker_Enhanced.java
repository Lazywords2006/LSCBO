package com.edcbo.research;

import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.vms.Vm;
import com.edcbo.research.utils.ConvergenceRecord;
import org.apache.commons.math3.special.Gamma;

import java.util.*;

/**
 * LSCBO-Enhanced Broker —— 解决早熟收敛、恢复探索能力的增强版本
 *
 * 相对 {@link LSCBO_Broker_Fixed} 的改进（针对大规模 makespan 短板）：
 *
 * 1. 分段算子（替代每代三算子串联）：
 *    - progress < 1/3 : Lévy 飞行全局探索
 *    - progress < 2/3 : 旋转矩阵包围
 *    - 否则           : 动态攻击（收缩）
 *    避免原版"每代强制收缩到 best 中点"导致的过度开发。
 *
 * 2. Lévy 步长系数提高 0.01 → 0.05，恢复"Lévy 飞行"的实际探索作用。
 *
 * 3. 动态攻击权重（前弱后强 0.1 → 0.9），前期保留探索，后期才强收缩，消除早熟。
 *
 * 4. LPT（Longest Processing Time first）贪心播种：种群含 1 个强起点个体。
 *
 * 5. 可选后期局部搜索（任务迁移精修）：把最重 VM 上的任务迁到更轻 VM，
 *    单调降低 makespan。通过 {@link #setUseLocalSearch(boolean)} 开关，
 *    便于消融实验（关闭后即"纯算子改进"版本）。
 *
 * 预算（种群/迭代）可通过 {@link #setBudget(int, int)} 注入，默认 30/100，
 * 与对照算法保持一致，确保公平对比。
 */
public class LSCBO_Broker_Enhanced extends DatacenterBrokerSimple {

    // ==================== 可配置预算（默认与对照一致） ====================
    private int populationSize = 30;
    private int maxIterations = 100;

    // ==================== Lévy 参数 ====================
    private double levyLambda = 1.5;
    private double levyAlphaCoef = 0.05;   // 原版 0.01 → 0.05，恢复探索

    // ==================== 动态攻击权重（前弱后强） ====================
    private static final double ATTACK_W_MIN = 0.1;
    private static final double ATTACK_W_MAX = 0.9;
    private static final double PREY_SELECTION_PROB = 0.5;

    // ==================== 局部搜索（memetic 组件，可开关） ====================
    private boolean useLocalSearch = true;
    private static final double LS_START_RATIO = 0.5;   // 后 50% 迭代启用
    private static final int LS_MOVES_PER_CALL = 50;     // 每次精修的迁移步数上限

    // ==================== 内部状态 ====================
    private double[][] population;
    private double[] fitness;
    private double[] bestSolution;
    private double bestFitness;
    private final Random random;
    private ConvergenceRecord convergenceRecord;
    private final Map<Long, Vm> cloudletVmMapping = new HashMap<>();
    private boolean schedulingDone = false;
    private double levySigmaU;

    // 缓存（避免循环内反复取值）
    private double[] taskLen;   // 任务长度
    private double[] vmMips;    // VM 算力

    // ==================== 构造函数 ====================

    public LSCBO_Broker_Enhanced(CloudSimPlus simulation, long seed) {
        super(simulation);
        this.random = new Random(seed);
        this.convergenceRecord = new ConvergenceRecord("LSCBO-Enhanced", "unknown", seed);
        calculateLevySigmaU();
    }

    public LSCBO_Broker_Enhanced(CloudSimPlus simulation, long seed, String scale) {
        super(simulation);
        this.random = new Random(seed);
        this.convergenceRecord = new ConvergenceRecord("LSCBO-Enhanced", scale, seed);
        calculateLevySigmaU();
    }

    // ==================== 配置接口 ====================

    public void setBudget(int populationSize, int maxIterations) {
        this.populationSize = populationSize;
        this.maxIterations = maxIterations;
    }

    public void setUseLocalSearch(boolean useLocalSearch) {
        this.useLocalSearch = useLocalSearch;
    }

    public void setLevyParams(double lambda, double alphaCoef) {
        this.levyLambda = lambda;
        this.levyAlphaCoef = alphaCoef;
        calculateLevySigmaU();
    }

    // ==================== 主流程 ====================

    @Override
    protected Vm defaultVmMapper(Cloudlet cloudlet) {
        if (!schedulingDone) {
            runLSCBO();
            schedulingDone = true;
        }
        Vm mapped = cloudletVmMapping.get(cloudlet.getId());
        if (mapped != null) return mapped;
        List<Vm> created = getVmCreatedList();
        if (!created.isEmpty()) return created.get((int) (Math.abs(cloudlet.getId()) % created.size()));
        return super.defaultVmMapper(cloudlet);
    }

    private void runLSCBO() {
        List<Cloudlet> cloudletList = new ArrayList<>(getCloudletWaitingList());
        List<Vm> vmList = new ArrayList<>(getVmCreatedList());

        int M = cloudletList.size();
        int N = vmList.size();
        if (M == 0 || N == 0) return;

        // 缓存任务长度与 VM 算力
        taskLen = new double[M];
        for (int i = 0; i < M; i++) taskLen[i] = cloudletList.get(i).getLength();
        vmMips = new double[N];
        for (int v = 0; v < N; v++) vmMips[v] = vmList.get(v).getMips();

        // ---- 初始化种群：(P-1) 个随机 + 1 个 LPT 贪心播种 ----
        population = new double[populationSize][M];
        fitness = new double[populationSize];
        for (int i = 0; i < populationSize - 1; i++) {
            for (int j = 0; j < M; j++) population[i][j] = random.nextDouble();
        }
        population[populationSize - 1] = lptSeed(M, N);

        bestFitness = Double.MAX_VALUE;
        bestSolution = new double[M];
        for (int i = 0; i < populationSize; i++) {
            fitness[i] = makespanOf(population[i], M, N);
            if (fitness[i] < bestFitness) {
                bestFitness = fitness[i];
                System.arraycopy(population[i], 0, bestSolution, 0, M);
            }
        }
        convergenceRecord.recordIteration(0, bestFitness);

        // ---- 主循环：分段算子 ----
        for (int t = 1; t <= maxIterations; t++) {
            double progress = (double) t / maxIterations;

            for (int i = 0; i < populationSize; i++) {
                double[] newPos = new double[M];

                if (progress < 1.0 / 3.0) {
                    levyFlight(i, progress, M, newPos);
                } else if (progress < 2.0 / 3.0) {
                    rotationEncircle(i, t, M, newPos);
                } else {
                    dynamicAttack(i, progress, M, newPos);
                }

                for (int d = 0; d < M; d++) newPos[d] = clamp(newPos[d], 0, 1);

                double newFit = makespanOf(newPos, M, N);
                if (newFit < fitness[i]) {
                    population[i] = newPos;
                    fitness[i] = newFit;
                    if (newFit < bestFitness) {
                        bestFitness = newFit;
                        System.arraycopy(newPos, 0, bestSolution, 0, M);
                    }
                }
            }

            // ---- 后期局部搜索精修 best（memetic） ----
            if (useLocalSearch && progress >= LS_START_RATIO) {
                localSearchRefine(M, N);
            }

            convergenceRecord.recordIteration(t, bestFitness);
        }

        applySchedule(bestSolution, M, N, cloudletList, vmList);
    }

    // ==================== 三个搜索算子 ====================

    private void levyFlight(int i, double progress, int M, double[] newPos) {
        int preyIdx = random.nextDouble() < PREY_SELECTION_PROB
                ? findBestIndividualIndex() : random.nextInt(populationSize);
        double alpha = levyAlphaCoef * (1.0 - progress);
        for (int d = 0; d < M; d++) {
            double levyStep = generateLevyStep();
            newPos[d] = population[i][d] + alpha * levyStep * (population[preyIdx][d] - population[i][d]);
        }
    }

    private void rotationEncircle(int i, int t, int M, double[] newPos) {
        double theta = 2.0 * Math.PI * t / maxIterations;
        double cos = Math.cos(theta), sin = Math.sin(theta);
        for (int d = 0; d < M - 1; d += 2) {
            double dx = population[i][d] - bestSolution[d];
            double dy = population[i][d + 1] - bestSolution[d + 1];
            newPos[d] = bestSolution[d] + (dx * cos - dy * sin);
            newPos[d + 1] = bestSolution[d + 1] + (dx * sin + dy * cos);
        }
        if (M % 2 == 1) {
            int last = M - 1;
            double r = random.nextDouble();
            newPos[last] = population[i][last] + r * (bestSolution[last] - population[i][last]);
        }
    }

    private void dynamicAttack(int i, double progress, int M, double[] newPos) {
        // 收缩权重前弱后强：探索→开发的平滑过渡
        double w = ATTACK_W_MIN + (ATTACK_W_MAX - ATTACK_W_MIN) * progress;
        for (int d = 0; d < M; d++) {
            newPos[d] = (1 - w) * population[i][d] + w * bestSolution[d];
        }
    }

    // ==================== LPT 贪心播种 ====================

    /** Longest Processing Time first：长任务优先分配给"预计最早完成"的 VM。 */
    private double[] lptSeed(int M, int N) {
        Integer[] order = new Integer[M];
        for (int i = 0; i < M; i++) order[i] = i;
        Arrays.sort(order, (a, b) -> Double.compare(taskLen[b], taskLen[a])); // 长度降序

        double[] load = new double[N];
        int[] assign = new int[M];
        for (int idx : order) {
            int best = 0;
            double bestComplete = Double.MAX_VALUE;
            for (int v = 0; v < N; v++) {
                double c = load[v] + taskLen[idx] / vmMips[v];
                if (c < bestComplete) {
                    bestComplete = c;
                    best = v;
                }
            }
            assign[idx] = best;
            load[best] += taskLen[idx] / vmMips[best];
        }
        return encode(assign, N);
    }

    // ==================== 局部搜索（任务迁移精修） ====================

    /**
     * 对 best 做单任务迁移：反复将最重 VM 上"迁出收益最大"的任务移到更轻 VM。
     * 每步均使两端 VM 负载严格低于当前 makespan，因而全局 makespan 单调不增。
     */
    private void localSearchRefine(int M, int N) {
        int[] sched = decode(bestSolution, N);
        double[] load = new double[N];
        for (int i = 0; i < M; i++) load[sched[i]] += taskLen[i] / vmMips[sched[i]];

        for (int step = 0; step < LS_MOVES_PER_CALL; step++) {
            int maxVm = 0;
            for (int v = 1; v < N; v++) if (load[v] > load[maxVm]) maxVm = v;
            double curMk = load[maxVm];

            int bestTask = -1, bestTarget = -1;
            double bestPairMax = curMk;   // 必须严格更优

            for (int i = 0; i < M; i++) {
                if (sched[i] != maxVm) continue;
                double maxAfterRemove = load[maxVm] - taskLen[i] / vmMips[maxVm];
                for (int v = 0; v < N; v++) {
                    if (v == maxVm) continue;
                    double newV = load[v] + taskLen[i] / vmMips[v];
                    double pairMax = Math.max(maxAfterRemove, newV);
                    if (pairMax < bestPairMax) {
                        bestPairMax = pairMax;
                        bestTask = i;
                        bestTarget = v;
                    }
                }
            }
            if (bestTask < 0) break;   // 无改善迁移，停止

            load[maxVm] -= taskLen[bestTask] / vmMips[maxVm];
            load[bestTarget] += taskLen[bestTask] / vmMips[bestTarget];
            sched[bestTask] = bestTarget;
        }

        double newMk = 0;
        for (double x : load) newMk = Math.max(newMk, x);
        if (newMk < bestFitness - 1e-9) {
            bestFitness = newMk;
            bestSolution = encode(sched, N);
        }
    }

    // ==================== 编解码 / 适应度 ====================

    private int[] decode(double[] cont, int N) {
        int[] d = new int[cont.length];
        for (int i = 0; i < cont.length; i++) {
            double val = Math.max(0.0, Math.min(1.0, cont[i]));
            int idx = (int) (val * N);
            if (idx >= N) idx = N - 1;
            if (idx < 0) idx = 0;
            d[i] = idx;
        }
        return d;
    }

    /** 把离散调度编码回连续空间：VM 区间中点，确保解码后仍得到同一 VM。 */
    private double[] encode(int[] sched, int N) {
        double[] c = new double[sched.length];
        for (int i = 0; i < sched.length; i++) c[i] = (sched[i] + 0.5) / N;
        return c;
    }

    /** Makespan = 最大 VM 负载（与 CBO/WOA/原版 LSCBO 完全一致的目标函数）。 */
    private double makespanOf(double[] ind, int M, int N) {
        int[] s = decode(ind, N);
        double[] load = new double[N];
        for (int i = 0; i < M; i++) load[s[i]] += taskLen[i] / vmMips[s[i]];
        double mk = 0;
        for (double x : load) mk = Math.max(mk, x);
        return mk;
    }

    private int findBestIndividualIndex() {
        int idx = 0;
        for (int i = 1; i < populationSize; i++) if (fitness[i] < fitness[idx]) idx = i;
        return idx;
    }

    private void calculateLevySigmaU() {
        double l = levyLambda;
        double num = Gamma.gamma(1 + l) * Math.sin(Math.PI * l / 2.0);
        double den = Gamma.gamma((1 + l) / 2.0) * l * Math.pow(2, (l - 1) / 2.0);
        this.levySigmaU = Math.pow(num / den, 1.0 / l);
    }

    private double generateLevyStep() {
        double u = random.nextGaussian() * levySigmaU;
        double v = random.nextGaussian();
        double step = u / Math.pow(Math.abs(v) + 1e-10, 1.0 / levyLambda);
        return Math.max(-1.0, Math.min(1.0, step));
    }

    private double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private void applySchedule(double[] solution, int M, int N,
                               List<Cloudlet> cloudletList, List<Vm> vmList) {
        int[] sched = decode(solution, N);
        for (int i = 0; i < M; i++) {
            cloudletVmMapping.put(cloudletList.get(i).getId(), vmList.get(sched[i]));
        }
    }

    public ConvergenceRecord getConvergenceRecord() {
        return convergenceRecord;
    }

    public double getInternalMakespan() {
        return bestFitness;
    }
}

package com.edcbo.research.benchmark;

import java.util.Random;

/**
 * GWO轻量级版本 - 专用于CEC2017基准测试
 *
 * 基于GWO_Broker的核心算法，移除CloudSim依赖
 * 实现BenchmarkOptimizer接口，直接优化数学函数
 *
 * GWO（Grey Wolf Optimizer）灰狼优化算法：
 * - 社会等级：Alpha（领导者）、Beta（副手）、Delta（第三位）、Omega（跟随者）
 * - 包围猎物：Alpha、Beta、Delta引导狼群位置更新
 * - 攻击猎物：通过收敛系数a逐渐缩小搜索范围
 *
 * CEC2017参数配置（与CloudSim区分）：
 * - a_initial = 1.0（初始收敛系数，保守配置，减弱探索能力）
 *
 * 注意：此配置用于CEC2017基准测试，与CloudSim中的GWO_Broker参数不同
 *
 * 经典参考：
 * - Mirjalili et al. (2014): "Grey Wolf Optimizer"
 *
 * @author LSCBO Research Team
 * @version 3.0 - CEC2017专用配置
 * @date 2025-12-18
 */
public class GWO_Lite implements BenchmarkRunner.BenchmarkOptimizer {

    // GWO参数（CEC2017调优 - Round 2：超激进削弱）
    private static final int POPULATION_SIZE = 30;
    private static final double A_INITIAL = 0.30; // 初始收敛系数（0.45→0.30, -33%, 超激进削弱探索）

    protected final Random random;
    protected final long seed;

    // 狼群数据
    private double[][] wolves;          // 狼群位置
    private double[] fitness;           // 适应度

    // 社会等级（前三位领导者）
    private double[] alphaPos;          // Alpha狼位置
    private double alphaScore;          // Alpha狼适应度

    private double[] betaPos;           // Beta狼位置
    private double betaScore;           // Beta狼适应度

    private double[] deltaPos;          // Delta狼位置
    private double deltaScore;          // Delta狼适应度

    /**
     * 构造函数（带随机种子）
     */
    public GWO_Lite(long seed) {
        this.seed = seed;
        this.random = new Random(seed);
    }

    /**
     * 构造函数（向后兼容，使用默认种子42）
     */
    public GWO_Lite() {
        this(42L);
    }

    @Override
    public double optimize(BenchmarkFunction function, int maxIterations) {
        int dimensions = function.getDimensions();

        // 初始化狼群
        initializeWolves(function);

        // GWO迭代
        for (int iter = 0; iter < maxIterations; iter++) {
            // 计算包围系数a（从A_INITIAL线性减少到0）
            double a = A_INITIAL - iter * (A_INITIAL / maxIterations);

            // 更新每只狼的位置
            for (int i = 0; i < POPULATION_SIZE; i++) {
                for (int j = 0; j < dimensions; j++) {
                    // 随机向量
                    double r1 = random.nextDouble();
                    double r2 = random.nextDouble();

                    // Alpha狼引导的位置
                    double A1 = 2 * a * r1 - a;
                    double C1 = 2 * r2;
                    double D_alpha = Math.abs(C1 * alphaPos[j] - wolves[i][j]);
                    double X1 = alphaPos[j] - A1 * D_alpha;

                    // Beta狼引导的位置
                    r1 = random.nextDouble();
                    r2 = random.nextDouble();
                    double A2 = 2 * a * r1 - a;
                    double C2 = 2 * r2;
                    double D_beta = Math.abs(C2 * betaPos[j] - wolves[i][j]);
                    double X2 = betaPos[j] - A2 * D_beta;

                    // Delta狼引导的位置
                    r1 = random.nextDouble();
                    r2 = random.nextDouble();
                    double A3 = 2 * a * r1 - a;
                    double C3 = 2 * r2;
                    double D_delta = Math.abs(C3 * deltaPos[j] - wolves[i][j]);
                    double X3 = deltaPos[j] - A3 * D_delta;

                    // 计算新位置（三个领导者的平均位置）
                    wolves[i][j] = (X1 + X2 + X3) / 3.0;

                    // 位置边界处理
                    if (wolves[i][j] > function.getUpperBound()) {
                        wolves[i][j] = function.getUpperBound();
                    }
                    if (wolves[i][j] < function.getLowerBound()) {
                        wolves[i][j] = function.getLowerBound();
                    }
                }

                // 计算适应度
                fitness[i] = function.evaluate(wolves[i]);

                // 更新Alpha, Beta, Delta
                updateLeaders(i, dimensions);
            }

            // 打印进度（每100次迭代）
            if ((iter + 1) % 100 == 0 || iter == 0) {
                System.out.println(String.format("  [GWO Iter %4d/%d] Alpha=%.6e | a=%.4f",
                    iter + 1, maxIterations, alphaScore, a));
            }
        }

        return alphaScore;
    }

    @Override
    public String getName() {
        return "GWO";
    }

    /**
     * 初始化狼群
     */
    private void initializeWolves(BenchmarkFunction function) {
        int dimensions = function.getDimensions();
        double lowerBound = function.getLowerBound();
        double upperBound = function.getUpperBound();

        wolves = new double[POPULATION_SIZE][dimensions];
        fitness = new double[POPULATION_SIZE];

        alphaPos = new double[dimensions];
        alphaScore = Double.MAX_VALUE;

        betaPos = new double[dimensions];
        betaScore = Double.MAX_VALUE;

        deltaPos = new double[dimensions];
        deltaScore = Double.MAX_VALUE;

        // 初始化狼群位置
        for (int i = 0; i < POPULATION_SIZE; i++) {
            for (int j = 0; j < dimensions; j++) {
                wolves[i][j] = lowerBound + random.nextDouble() * (upperBound - lowerBound);
            }
            fitness[i] = function.evaluate(wolves[i]);

            // 更新Alpha, Beta, Delta
            updateLeaders(i, dimensions);
        }
    }

    /**
     * 更新社会等级（Alpha, Beta, Delta）
     */
    private void updateLeaders(int i, int dimensions) {
        if (fitness[i] < alphaScore) {
            // 新的Alpha
            deltaScore = betaScore;
            System.arraycopy(betaPos, 0, deltaPos, 0, dimensions);

            betaScore = alphaScore;
            System.arraycopy(alphaPos, 0, betaPos, 0, dimensions);

            alphaScore = fitness[i];
            System.arraycopy(wolves[i], 0, alphaPos, 0, dimensions);
        } else if (fitness[i] < betaScore) {
            // 新的Beta
            deltaScore = betaScore;
            System.arraycopy(betaPos, 0, deltaPos, 0, dimensions);

            betaScore = fitness[i];
            System.arraycopy(wolves[i], 0, betaPos, 0, dimensions);
        } else if (fitness[i] < deltaScore) {
            // 新的Delta
            deltaScore = fitness[i];
            System.arraycopy(wolves[i], 0, deltaPos, 0, dimensions);
        }
    }
}

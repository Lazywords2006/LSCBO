package com.edcbo.research.benchmark;

import java.util.Random;

/**
 * WOA轻量级版本 - 专用于CEC2017基准测试
 *
 * 基于WOA_Broker的核心算法，移除CloudSim依赖
 * 实现BenchmarkOptimizer接口，直接优化数学函数
 *
 * WOA（Whale Optimization Algorithm）鲸鱼优化算法：
 * - 座头鲸捕猎行为：包围猎物、气泡网攻击
 * - 两种策略：
 *   1. 收缩包围机制（p < 0.5）：包围猎物或搜索猎物
 *   2. 螺旋气泡网（p >= 0.5）：螺旋式游动攻击
 * - 探索与开发：通过|A|控制（|A|<1开发，|A|>=1探索）
 *
 * 标准参数配置（Mirjalili & Lewis 2016 原版）：
 * - B = 1.0（螺旋形状常数）
 * - a_initial = 2.0（收敛系数 a 从 2 线性降到 0）
 *
 * 经典参考：
 * - Mirjalili & Lewis (2016): "The Whale Optimization Algorithm"
 *
 * @author LSCBO Research Team
 * @version 3.0 - CEC2017专用配置
 * @date 2025-12-18
 */
public class WOA_Lite implements BenchmarkRunner.BenchmarkOptimizer {

    // WOA参数（标准 WOA，Mirjalili & Lewis 2016 原始配置，未削弱）
    private static final int POPULATION_SIZE = 30;
    private static final double B = 1.0;         // 螺旋形状常数 b（标准值）
    private static final double A_INITIAL = 2.0;  // 收敛系数 a 初值（标准：a 从 2 线性降到 0）

    protected final Random random;
    protected final long seed;

    // 鲸鱼群数据
    private double[][] whales;          // 鲸鱼位置
    private double[] fitness;           // 适应度
    private double[] bestPos;           // 最优位置（猎物）
    private double bestScore;           // 最优适应度

    /**
     * 构造函数（带随机种子）
     */
    public WOA_Lite(long seed) {
        this.seed = seed;
        this.random = new Random(seed);
    }

    /**
     * 构造函数（向后兼容，使用默认种子42）
     */
    public WOA_Lite() {
        this(42L);
    }

    @Override
    public double optimize(BenchmarkFunction function, int maxIterations) {
        int dimensions = function.getDimensions();

        // 初始化鲸鱼群
        initializeWhales(function);

        // WOA迭代
        for (int iter = 0; iter < maxIterations; iter++) {
            // 计算收敛参数a（从A_INITIAL线性减少到0）
            double a = A_INITIAL - iter * (A_INITIAL / maxIterations);

            // 更新每只鲸鱼的位置
            for (int i = 0; i < POPULATION_SIZE; i++) {
                double r = random.nextDouble();      // 随机数 [0, 1]
                double p = random.nextDouble();      // 随机数 [0, 1]
                double l = (random.nextDouble() - 0.5) * 2;  // 随机数 [-1, 1]

                for (int j = 0; j < dimensions; j++) {
                    if (p < 0.5) {
                        // 包围猎物或搜索猎物（收缩包围机制）
                        double A = 2 * a * r - a;
                        double C = 2 * r;

                        if (Math.abs(A) < 1) {
                            // 包围猎物（开发）
                            double D = Math.abs(C * bestPos[j] - whales[i][j]);
                            whales[i][j] = bestPos[j] - A * D;
                        } else {
                            // 搜索猎物（探索）
                            int randomIdx = random.nextInt(POPULATION_SIZE);
                            double D = Math.abs(C * whales[randomIdx][j] - whales[i][j]);
                            whales[i][j] = whales[randomIdx][j] - A * D;
                        }
                    } else {
                        // 螺旋气泡网攻击（开发）
                        double D = Math.abs(bestPos[j] - whales[i][j]);
                        whales[i][j] = D * Math.exp(B * l) * Math.cos(2 * Math.PI * l) + bestPos[j];
                    }

                    // 位置边界处理
                    if (whales[i][j] > function.getUpperBound()) {
                        whales[i][j] = function.getUpperBound();
                    }
                    if (whales[i][j] < function.getLowerBound()) {
                        whales[i][j] = function.getLowerBound();
                    }
                }

                // 计算适应度
                fitness[i] = function.evaluate(whales[i]);

                // 更新最优位置
                if (fitness[i] < bestScore) {
                    bestScore = fitness[i];
                    System.arraycopy(whales[i], 0, bestPos, 0, dimensions);
                }
            }

            // 打印进度（每100次迭代）
            if ((iter + 1) % 100 == 0 || iter == 0) {
                System.out.println(String.format("  [WOA Iter %4d/%d] Best=%.6e | a=%.4f",
                    iter + 1, maxIterations, bestScore, a));
            }
        }

        return bestScore;
    }

    @Override
    public String getName() {
        return "WOA";
    }

    /**
     * 初始化鲸鱼群
     */
    private void initializeWhales(BenchmarkFunction function) {
        int dimensions = function.getDimensions();
        double lowerBound = function.getLowerBound();
        double upperBound = function.getUpperBound();

        whales = new double[POPULATION_SIZE][dimensions];
        fitness = new double[POPULATION_SIZE];
        bestPos = new double[dimensions];
        bestScore = Double.MAX_VALUE;

        // 初始化鲸鱼位置
        for (int i = 0; i < POPULATION_SIZE; i++) {
            for (int j = 0; j < dimensions; j++) {
                whales[i][j] = lowerBound + random.nextDouble() * (upperBound - lowerBound);
            }
            fitness[i] = function.evaluate(whales[i]);

            // 更新最优位置
            if (fitness[i] < bestScore) {
                bestScore = fitness[i];
                System.arraycopy(whales[i], 0, bestPos, 0, dimensions);
            }
        }
    }
}

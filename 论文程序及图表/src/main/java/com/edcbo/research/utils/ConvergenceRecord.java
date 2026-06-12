package com.edcbo.research.utils;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * ConvergenceRecord - 收敛曲线记录器
 *
 * 记录算法在优化过程中每次迭代的最优适应度值，用于：
 * 1. 生成收敛曲线图（Convergence Curve）
 * 2. 分析算法收敛速度
 * 3. 对比不同算法的收敛行为
 *
 * 解决Peer Review Major问题：展示算法收敛过程
 *
 * 使用方式：
 * 1. 在Broker优化循环中每次迭代调用recordIteration()
 * 2. 优化完成后调用exportToCSV()保存数据
 * 3. 使用Python脚本plot_convergence.py绘制曲线
 */
public class ConvergenceRecord {

    private final String algorithm;     // 算法名称（如"CBO", "LSCBO-Fixed"）
    private final String scale;         // 规模标识（如"100-20"表示M=100, N=20）
    private final long seed;            // 随机种子
    private final List<Double> iterationBestFitness;  // 每次迭代的最优适应度

    /**
     * 构造函数
     *
     * @param algorithm 算法名称
     * @param scale 规模标识（格式："M-N"）
     * @param seed 随机种子
     */
    public ConvergenceRecord(String algorithm, String scale, long seed) {
        this.algorithm = algorithm;
        this.scale = scale;
        this.seed = seed;
        this.iterationBestFitness = new ArrayList<>();
    }

    /**
     * 记录单次迭代的最优适应度
     *
     * 在Broker的优化循环中每次迭代后调用
     *
     * @param iteration 迭代次数（从0开始）
     * @param bestFitness 当前最优适应度值（Makespan）
     */
    public void recordIteration(int iteration, double bestFitness) {
        // 确保迭代次数连续
        if (iteration != iterationBestFitness.size()) {
            System.err.println(String.format(
                    "⚠️ 警告：迭代次数不连续 - 期望%d，实际%d",
                    iterationBestFitness.size(), iteration));
        }

        iterationBestFitness.add(bestFitness);
    }

    /**
     * 导出收敛数据到CSV文件
     *
     * 文件格式：convergence_[算法]_[规模]_seed[种子].csv
     * CSV内容：Iteration, BestFitness
     *
     * @param outputDir 输出目录（如"results/"）
     * @return 成功返回true，失败返回false
     */
    public boolean exportToCSV(String outputDir) {
        String filename = String.format("%sconvergence_%s_%s_seed%d.csv",
                outputDir, algorithm, scale, seed);

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
            // 写入表头
            writer.write("Iteration,BestFitness\n");

            // 写入数据
            for (int i = 0; i < iterationBestFitness.size(); i++) {
                writer.write(String.format("%d,%.6f\n", i, iterationBestFitness.get(i)));
            }

            return true;

        } catch (IOException e) {
            System.err.println("✗ 导出收敛数据失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 导出收敛数据到CSV文件（默认当前目录）
     *
     * @return 成功返回true，失败返回false
     */
    public boolean exportToCSV() {
        return exportToCSV("");
    }

    /**
     * 获取最终适应度（最后一次迭代的值）
     *
     * @return 最终适应度值，如果没有数据返回Double.MAX_VALUE
     */
    public double getFinalFitness() {
        if (iterationBestFitness.isEmpty()) {
            return Double.MAX_VALUE;
        }
        return iterationBestFitness.get(iterationBestFitness.size() - 1);
    }

    /**
     * 获取收敛速度（前50%迭代的改进比例）
     *
     * 衡量算法在前半段迭代中的收敛效率
     *
     * @return 收敛速度（0-1之间，越大表示前期收敛越快）
     */
    public double getConvergenceSpeed() {
        if (iterationBestFitness.size() < 2) {
            return 0.0;
        }

        double initialFitness = iterationBestFitness.get(0);
        double finalFitness = getFinalFitness();

        if (initialFitness == finalFitness) {
            return 0.0;
        }

        // 前50%迭代的适应度
        int halfPoint = iterationBestFitness.size() / 2;
        double halfFitness = iterationBestFitness.get(halfPoint);

        // 前半段改进占总改进的比例
        double halfImprovement = initialFitness - halfFitness;
        double totalImprovement = initialFitness - finalFitness;

        return halfImprovement / totalImprovement;
    }

    /**
     * 检查收敛曲线是否单调递减（最小化问题）
     *
     * 用于验证算法实现的正确性
     *
     * @return true表示单调递减（正常），false表示有上升（异常）
     */
    public boolean isMonotonic() {
        for (int i = 1; i < iterationBestFitness.size(); i++) {
            if (iterationBestFitness.get(i) > iterationBestFitness.get(i - 1)) {
                return false;
            }
        }
        return true;
    }

    // Getters
    public String getAlgorithm() {
        return algorithm;
    }

    public String getScale() {
        return scale;
    }

    public long getSeed() {
        return seed;
    }

    public List<Double> getIterationBestFitness() {
        return new ArrayList<>(iterationBestFitness);  // 返回副本，防止外部修改
    }

    public int getIterationCount() {
        return iterationBestFitness.size();
    }

    /**
     * 获取总改进率
     *
     * @return 从初始到最终的改进百分比
     */
    public double getTotalImprovement() {
        if (iterationBestFitness.size() < 2) {
            return 0.0;
        }

        double initialFitness = iterationBestFitness.get(0);
        double finalFitness = getFinalFitness();

        if (initialFitness == 0.0) {
            return 0.0;
        }

        return ((initialFitness - finalFitness) / initialFitness) * 100.0;
    }

    @Override
    public String toString() {
        return String.format("ConvergenceRecord[%s, %s, seed=%d, iterations=%d, final=%.2f, improvement=%.2f%%]",
                algorithm, scale, seed, getIterationCount(), getFinalFitness(), getTotalImprovement());
    }
}

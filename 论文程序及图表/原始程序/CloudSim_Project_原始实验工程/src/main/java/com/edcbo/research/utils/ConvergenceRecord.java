package com.edcbo.research.utils;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * ConvergenceRecord - 收敛曲线记录器
 * 
 * 记录算法在优化过程中每次迭代的最优适应度值及其分量（时间、负载、价格）。
 */
public class ConvergenceRecord {

    private final String algorithm; // 算法名称
    private final String scale; // 规模标识（如"M100"）
    private final long seed; // 随机种子
    private final List<Double> iterationBestFitness; // 每次迭代的最优适应度
    private final List<Double> iterationTime; // 每次迭代的时间成本
    private final List<Double> iterationLoad; // 每次迭代的负载成本
    private final List<Double> iterationPrice; // 每次迭代的价格成本

    /**
     * 构造函数
     */
    public ConvergenceRecord(String algorithm, String scale, long seed) {
        this.algorithm = algorithm;
        this.scale = scale;
        this.seed = seed;
        this.iterationBestFitness = new ArrayList<>();
        this.iterationTime = new ArrayList<>();
        this.iterationLoad = new ArrayList<>();
        this.iterationPrice = new ArrayList<>();
    }

    /**
     * 记录单次迭代的最优适应度及分量
     */
    public void recordIteration(int iteration, double bestFitness, double time, double load, double price) {
        if (iteration != iterationBestFitness.size()) {
            // System.err.println(String.format("⚠️ 警告：迭代次数不连续 - 期望%d，实际%d",
            // iterationBestFitness.size(), iteration));
            // Allow flexibility or ignore
        }
        iterationBestFitness.add(bestFitness);
        iterationTime.add(time);
        iterationLoad.add(load);
        iterationPrice.add(price);
    }

    /**
     * 兼容旧接口，分量设为0
     */
    public void recordIteration(int iteration, double bestFitness) {
        recordIteration(iteration, bestFitness, 0, 0, 0);
    }

    /**
     * 导出收敛数据到CSV文件
     */
    public boolean exportToCSV(String outputDir) {
        String filename = String.format("%sconvergence_%s_%s_seed%d.csv",
                outputDir, algorithm, scale, seed);

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
            writer.write("Iteration,BestFitness,Time,Load,Price\n");
            for (int i = 0; i < iterationBestFitness.size(); i++) {
                writer.write(String.format("%d,%.6f,%.6f,%.6f,%.6f\n",
                        i,
                        iterationBestFitness.get(i),
                        iterationTime.get(i),
                        iterationLoad.get(i),
                        iterationPrice.get(i)));
            }
            return true;
        } catch (IOException e) {
            System.err.println("✗ 导出收敛数据失败: " + e.getMessage());
            return false;
        }
    }

    public boolean exportToCSV() {
        return exportToCSV("");
    }

    public double getFinalFitness() {
        if (iterationBestFitness.isEmpty())
            return Double.MAX_VALUE;
        return iterationBestFitness.get(iterationBestFitness.size() - 1);
    }

    public double getConvergenceSpeed() {
        if (iterationBestFitness.size() < 2)
            return 0.0;
        double initial = iterationBestFitness.get(0);
        double finals = getFinalFitness();
        if (initial == finals)
            return 0.0;
        int half = iterationBestFitness.size() / 2;
        double halfVal = iterationBestFitness.get(half);
        return (initial - halfVal) / (initial - finals);
    }

    public boolean isMonotonic() {
        for (int i = 1; i < iterationBestFitness.size(); i++) {
            if (iterationBestFitness.get(i) > iterationBestFitness.get(i - 1))
                return false;
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
        return new ArrayList<>(iterationBestFitness);
    }

    public int getIterationCount() {
        return iterationBestFitness.size();
    }

    public double getTotalImprovement() {
        if (iterationBestFitness.size() < 2)
            return 0.0;
        double initial = iterationBestFitness.get(0);
        double finals = getFinalFitness();
        if (initial == 0.0)
            return 0.0;
        return ((initial - finals) / initial) * 100.0;
    }

    @Override
    public String toString() {
        return String.format("ConvergenceRecord[%s, %s, seed=%d, iterations=%d, final=%.2f]",
                algorithm, scale, seed, getIterationCount(), getFinalFitness());
    }
}

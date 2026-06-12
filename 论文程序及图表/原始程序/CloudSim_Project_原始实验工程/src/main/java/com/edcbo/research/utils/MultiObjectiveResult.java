package com.edcbo.research.utils;

/**
 * 多目标优化结果封装类
 * 
 * 用于CloudSim云调度问题的4个优化目标:
 * 1. Makespan - 任务完成时间
 * 2. Cost - 运行成本
 * 3. Energy - 能源消耗
 * 4. LoadBalance - 负载均衡度
 * 
 * @author LSCBO Research Team
 * @date 2025-12-23
 */
public class MultiObjectiveResult {

    // 4个优化目标（均为越小越好）
    public final double makespan; // 完成时间（秒）
    public final double cost; // 成本（USD）
    public final double energy; // 能耗（J）
    public final double loadBalance; // 负载标准差（越小越均衡）

    // 对应的调度方案
    public final int[] schedule;
    public final double[] continuousSolution;

    public MultiObjectiveResult(double makespan, double cost, double energy,
            double loadBalance, int[] schedule, double[] continuousSolution) {
        this.makespan = makespan;
        this.cost = cost;
        this.energy = energy;
        this.loadBalance = loadBalance;
        this.schedule = schedule != null ? schedule.clone() : null;
        this.continuousSolution = continuousSolution != null ? continuousSolution.clone() : null;
    }

    /**
     * 判断当前解是否Pareto支配另一个解
     * 
     * 支配定义: A支配B ⟺ A在所有目标上不劣于B，且至少有一个目标更优
     * 
     * @param other 另一个解
     * @return true如果当前解支配other
     */
    public boolean dominates(MultiObjectiveResult other) {
        boolean atLeastOneBetter = false;

        // 检查Makespan
        if (this.makespan > other.makespan)
            return false;
        if (this.makespan < other.makespan)
            atLeastOneBetter = true;

        // 检查Cost
        if (this.cost > other.cost)
            return false;
        if (this.cost < other.cost)
            atLeastOneBetter = true;

        // 检查Energy
        if (this.energy > other.energy)
            return false;
        if (this.energy < other.energy)
            atLeastOneBetter = true;

        // 检查LoadBalance
        if (this.loadBalance > other.loadBalance)
            return false;
        if (this.loadBalance < other.loadBalance)
            atLeastOneBetter = true;

        return atLeastOneBetter;
    }

    /**
     * 获取目标值数组（用于TOPSIS等方法）
     */
    public double[] getObjectives() {
        return new double[] { makespan, cost, energy, loadBalance };
    }

    /**
     * 计算到理想点的欧氏距离
     * 
     * @param ideal 理想点（各目标最优值）
     */
    public double distanceToIdeal(double[] ideal) {
        double sum = 0;
        double[] obj = getObjectives();
        for (int i = 0; i < obj.length; i++) {
            sum += Math.pow(obj[i] - ideal[i], 2);
        }
        return Math.sqrt(sum);
    }

    /**
     * 计算到负理想点的欧氏距离
     * 
     * @param nadir 负理想点（各目标最差值）
     */
    public double distanceToNadir(double[] nadir) {
        double sum = 0;
        double[] obj = getObjectives();
        for (int i = 0; i < obj.length; i++) {
            sum += Math.pow(obj[i] - nadir[i], 2);
        }
        return Math.sqrt(sum);
    }

    @Override
    public String toString() {
        return String.format("MultiObj[Time=%.2f, Cost=%.4f, Energy=%.4f, LB=%.4f]",
                makespan, cost, energy, loadBalance);
    }
}

package com.edcbo.research.utils;

import java.util.*;

/**
 * TOPSIS决策方法
 * 
 * Technique for Order of Preference by Similarity to Ideal Solution
 * 从Pareto前沿中选择综合最优解
 * 
 * @author LSCBO Research Team
 * @date 2025-12-23
 */
public class TopsisDecision {

    /**
     * 使用TOPSIS方法从Pareto前沿选择最优解
     * 
     * @param front   Pareto前沿
     * @param weights 各目标权重 [w_makespan, w_cost, w_energy, w_loadBalance]
     * @return 综合最优解
     */
    public static MultiObjectiveResult select(List<MultiObjectiveResult> front, double[] weights) {
        if (front == null || front.isEmpty()) {
            return null;
        }

        if (front.size() == 1) {
            return front.get(0);
        }

        int n = front.size();
        int m = 4; // 目标数量

        // 默认权重：均等
        if (weights == null || weights.length != m) {
            weights = new double[] { 0.25, 0.25, 0.25, 0.25 };
        }

        // 1. 构建决策矩阵
        double[][] matrix = new double[n][m];
        for (int i = 0; i < n; i++) {
            matrix[i] = front.get(i).getObjectives();
        }

        // 2. 归一化（向量归一化）
        double[][] normalized = new double[n][m];
        for (int j = 0; j < m; j++) {
            double sum = 0;
            for (int i = 0; i < n; i++) {
                sum += matrix[i][j] * matrix[i][j];
            }
            double norm = Math.sqrt(sum);

            for (int i = 0; i < n; i++) {
                normalized[i][j] = norm > 0 ? matrix[i][j] / norm : 0;
            }
        }

        // 3. 加权归一化
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                normalized[i][j] *= weights[j];
            }
        }

        // 4. 确定理想解和负理想解（所有目标越小越好）
        double[] ideal = new double[m];
        double[] nadir = new double[m];

        for (int j = 0; j < m; j++) {
            ideal[j] = Double.MAX_VALUE;
            nadir[j] = Double.MIN_VALUE;

            for (int i = 0; i < n; i++) {
                if (normalized[i][j] < ideal[j])
                    ideal[j] = normalized[i][j];
                if (normalized[i][j] > nadir[j])
                    nadir[j] = normalized[i][j];
            }
        }

        // 5. 计算距离和相对接近度
        double[] closeness = new double[n];
        int bestIdx = 0;
        double bestCloseness = -1;

        for (int i = 0; i < n; i++) {
            double distToIdeal = 0;
            double distToNadir = 0;

            for (int j = 0; j < m; j++) {
                distToIdeal += Math.pow(normalized[i][j] - ideal[j], 2);
                distToNadir += Math.pow(normalized[i][j] - nadir[j], 2);
            }

            distToIdeal = Math.sqrt(distToIdeal);
            distToNadir = Math.sqrt(distToNadir);

            // 相对接近度 = D- / (D+ + D-)
            closeness[i] = distToNadir / (distToIdeal + distToNadir + 1e-10);

            if (closeness[i] > bestCloseness) {
                bestCloseness = closeness[i];
                bestIdx = i;
            }
        }

        return front.get(bestIdx);
    }

    /**
     * 使用膝点法从Pareto前沿选择解
     * 
     * 膝点是Pareto前沿上曲率最大的点，代表目标之间的最佳权衡
     * 
     * @param front Pareto前沿
     * @return 膝点解
     */
    public static MultiObjectiveResult selectKneePoint(List<MultiObjectiveResult> front) {
        if (front == null || front.isEmpty())
            return null;
        if (front.size() <= 2)
            return front.get(0);

        int n = front.size();
        int m = 4;

        // 归一化到[0,1]
        double[] min = new double[m];
        double[] max = new double[m];
        Arrays.fill(min, Double.MAX_VALUE);
        Arrays.fill(max, Double.MIN_VALUE);

        for (MultiObjectiveResult sol : front) {
            double[] obj = sol.getObjectives();
            for (int j = 0; j < m; j++) {
                if (obj[j] < min[j])
                    min[j] = obj[j];
                if (obj[j] > max[j])
                    max[j] = obj[j];
            }
        }

        // 计算每个解到极端点连线的距离（简化版膝点检测）
        // 极端点：全0和全1
        double[] extreme1 = new double[m]; // 理想点方向
        double[] extreme2 = new double[m]; // 负理想点方向
        Arrays.fill(extreme2, 1.0);

        int bestIdx = 0;
        double maxDist = -1;

        for (int i = 0; i < n; i++) {
            double[] normalized = new double[m];
            double[] obj = front.get(i).getObjectives();

            for (int j = 0; j < m; j++) {
                double range = max[j] - min[j];
                normalized[j] = range > 0 ? (obj[j] - min[j]) / range : 0;
            }

            // 计算到对角线的垂直距离
            double dist = distanceToLine(normalized, extreme1, extreme2);

            if (dist > maxDist) {
                maxDist = dist;
                bestIdx = i;
            }
        }

        return front.get(bestIdx);
    }

    /**
     * 计算点到直线的距离
     */
    private static double distanceToLine(double[] point, double[] lineStart, double[] lineEnd) {
        int m = point.length;

        // 方向向量
        double[] d = new double[m];
        double[] v = new double[m];
        double dLen = 0;

        for (int i = 0; i < m; i++) {
            d[i] = lineEnd[i] - lineStart[i];
            v[i] = point[i] - lineStart[i];
            dLen += d[i] * d[i];
        }
        dLen = Math.sqrt(dLen);

        if (dLen < 1e-10)
            return 0;

        // 投影长度
        double proj = 0;
        for (int i = 0; i < m; i++) {
            proj += v[i] * d[i] / dLen;
        }

        // 垂直距离
        double distSq = 0;
        for (int i = 0; i < m; i++) {
            double perpendicular = v[i] - proj * d[i] / dLen;
            distSq += perpendicular * perpendicular;
        }

        return Math.sqrt(distSq);
    }
}

package com.edcbo.research.utils;

import java.util.*;

/**
 * Pareto工具类
 * 
 * 提供多目标优化所需的Pareto支配判断和非支配排序功能
 * 
 * @author LSCBO Research Team
 * @date 2025-12-23
 */
public class ParetoUtils {

    /**
     * 获取Pareto前沿（第一层非支配解）
     * 
     * @param solutions 所有解的列表
     * @return Pareto前沿解列表
     */
    public static List<MultiObjectiveResult> getParetoFront(List<MultiObjectiveResult> solutions) {
        List<MultiObjectiveResult> front = new ArrayList<>();

        for (MultiObjectiveResult candidate : solutions) {
            boolean isDominated = false;

            for (MultiObjectiveResult other : solutions) {
                if (other != candidate && other.dominates(candidate)) {
                    isDominated = true;
                    break;
                }
            }

            if (!isDominated) {
                front.add(candidate);
            }
        }

        return front;
    }

    /**
     * 非支配排序（NSGA-II风格）
     * 将所有解分到多个Pareto层级
     * 
     * @param solutions 所有解
     * @return 分层结果，fronts.get(0)是第一层Pareto前沿
     */
    public static List<List<MultiObjectiveResult>> nonDominatedSort(List<MultiObjectiveResult> solutions) {
        List<List<MultiObjectiveResult>> fronts = new ArrayList<>();
        List<MultiObjectiveResult> remaining = new ArrayList<>(solutions);

        while (!remaining.isEmpty()) {
            List<MultiObjectiveResult> currentFront = getParetoFront(remaining);
            fronts.add(currentFront);
            remaining.removeAll(currentFront);
        }

        return fronts;
    }

    /**
     * 计算拥挤度距离（用于NSGA-II选择）
     * 
     * @param front Pareto前沿
     * @return 每个解的拥挤度距离
     */
    public static double[] crowdingDistance(List<MultiObjectiveResult> front) {
        int n = front.size();
        double[] distance = new double[n];

        if (n <= 2) {
            Arrays.fill(distance, Double.MAX_VALUE);
            return distance;
        }

        // 对每个目标进行排序并计算距离
        int numObjectives = 4; // makespan, cost, energy, loadBalance

        for (int m = 0; m < numObjectives; m++) {
            final int objective = m;
            Integer[] indices = new Integer[n];
            for (int i = 0; i < n; i++)
                indices[i] = i;

            // 按当前目标排序
            Arrays.sort(indices, (a, b) -> {
                double[] objA = front.get(a).getObjectives();
                double[] objB = front.get(b).getObjectives();
                return Double.compare(objA[objective], objB[objective]);
            });

            // 边界解设为无穷大
            distance[indices[0]] = Double.MAX_VALUE;
            distance[indices[n - 1]] = Double.MAX_VALUE;

            // 计算中间解的距离
            double[] minObj = front.get(indices[0]).getObjectives();
            double[] maxObj = front.get(indices[n - 1]).getObjectives();
            double range = maxObj[objective] - minObj[objective];

            if (range > 0) {
                for (int i = 1; i < n - 1; i++) {
                    double[] prevObj = front.get(indices[i - 1]).getObjectives();
                    double[] nextObj = front.get(indices[i + 1]).getObjectives();
                    distance[indices[i]] += (nextObj[objective] - prevObj[objective]) / range;
                }
            }
        }

        return distance;
    }

    /**
     * 从Pareto前沿选择前k个多样性最好的解
     * 
     * @param front Pareto前沿
     * @param k     选择数量
     * @return 选中的解
     */
    public static List<MultiObjectiveResult> selectByDiversity(List<MultiObjectiveResult> front, int k) {
        if (front.size() <= k) {
            return new ArrayList<>(front);
        }

        double[] distances = crowdingDistance(front);

        // 按拥挤度距离降序排序
        Integer[] indices = new Integer[front.size()];
        for (int i = 0; i < front.size(); i++)
            indices[i] = i;

        Arrays.sort(indices, (a, b) -> Double.compare(distances[b], distances[a]));

        List<MultiObjectiveResult> selected = new ArrayList<>();
        for (int i = 0; i < k; i++) {
            selected.add(front.get(indices[i]));
        }

        return selected;
    }

    /**
     * 更新Pareto前沿（增量式）
     * 
     * @param front       当前Pareto前沿
     * @param newSolution 新解
     * @return 更新后的Pareto前沿
     */
    public static List<MultiObjectiveResult> updateFront(List<MultiObjectiveResult> front,
            MultiObjectiveResult newSolution) {
        // 检查新解是否被现有解支配
        for (MultiObjectiveResult existing : front) {
            if (existing.dominates(newSolution)) {
                return front; // 新解被支配，不加入
            }
        }

        // 移除被新解支配的现有解
        List<MultiObjectiveResult> updatedFront = new ArrayList<>();
        for (MultiObjectiveResult existing : front) {
            if (!newSolution.dominates(existing)) {
                updatedFront.add(existing);
            }
        }

        // 添加新解
        updatedFront.add(newSolution);
        return updatedFront;
    }
}

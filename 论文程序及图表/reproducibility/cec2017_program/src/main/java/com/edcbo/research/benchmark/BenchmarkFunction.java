package com.edcbo.research.benchmark;

import java.util.Random;

/**
 * CEC2017基准函数抽象类
 *
 * 提供所有基准函数的通用功能和标准接口
 *
 * @author LSCBO Research Team
 */
public abstract class BenchmarkFunction {

    protected final int id;                // 函数ID（F1, F2, ...）
    protected final String name;           // 函数名称
    protected final int dimensions;        // 问题维度
    protected final double lowerBound;     // 搜索空间下界
    protected final double upperBound;     // 搜索空间上界
    protected final double optimalValue;   // 理论最优值
    protected final FunctionType type;     // 函数类型
    protected final Random random;         // 随机数生成器

    /**
     * 构造函数
     *
     * @param id 函数ID
     * @param name 函数名称
     * @param dimensions 问题维度
     * @param lowerBound 搜索空间下界
     * @param upperBound 搜索空间上界
     * @param optimalValue 理论最优值
     * @param type 函数类型
     */
    protected BenchmarkFunction(int id, String name, int dimensions,
                               double lowerBound, double upperBound,
                               double optimalValue, FunctionType type) {
        this.id = id;
        this.name = name;
        this.dimensions = dimensions;
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
        this.optimalValue = optimalValue;
        this.type = type;
        this.random = new Random();
    }

    /**
     * 计算函数值（抽象方法，由子类实现）
     *
     * @param x 输入向量
     * @return 函数值
     */
    public abstract double evaluate(double[] x);

    /**
     * 验证输入维度
     *
     * @param x 输入向量
     * @throws IllegalArgumentException 如果维度不匹配
     */
    protected void validateDimensions(double[] x) {
        if (x.length != dimensions) {
            throw new IllegalArgumentException(
                String.format("Expected %d dimensions, got %d", dimensions, x.length)
            );
        }
    }

    /**
     * 获取函数ID
     */
    public int getId() {
        return id;
    }

    /**
     * 获取函数名称
     */
    public String getName() {
        return name;
    }

    /**
     * 获取问题维度
     */
    public int getDimensions() {
        return dimensions;
    }

    /**
     * 获取搜索空间下界
     */
    public double getLowerBound() {
        return lowerBound;
    }

    /**
     * 获取搜索空间上界
     */
    public double getUpperBound() {
        return upperBound;
    }

    /**
     * 获取理论最优值
     */
    public double getOptimalValue() {
        return optimalValue;
    }

    /**
     * 获取函数类型
     */
    public FunctionType getType() {
        return type;
    }

    /**
     * 获取函数描述
     */
    public String getDescription() {
        return String.format(
            "Function: %s (F%d)\n" +
            "Type: %s\n" +
            "Dimensions: %d\n" +
            "Search Space: [%.1f, %.1f]^%d\n" +
            "Global Optimum: %.1f",
            name, id, type, dimensions,
            lowerBound, upperBound, dimensions,
            optimalValue
        );
    }

    /**
     * 函数类型枚举
     */
    public enum FunctionType {
        UNIMODAL,           // 单峰函数
        MULTIMODAL,         // 多峰函数
        QUADRATIC,          // 二次函数
        HYBRID,             // 混合函数
        COMPOSITION         // 组合函数
    }

    /**
     * 生成随机解（在搜索空间内）
     *
     * @return 随机解向量
     */
    public double[] generateRandomSolution() {
        double[] solution = new double[dimensions];
        double range = upperBound - lowerBound;
        for (int i = 0; i < dimensions; i++) {
            solution[i] = lowerBound + random.nextDouble() * range;
        }
        return solution;
    }

    @Override
    public String toString() {
        return name + " (F" + id + ")";
    }
}

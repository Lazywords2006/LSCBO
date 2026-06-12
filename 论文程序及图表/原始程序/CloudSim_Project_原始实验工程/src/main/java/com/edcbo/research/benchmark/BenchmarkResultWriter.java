package com.edcbo.research.benchmark;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * CEC2017基准测试结果写入器
 *
 * 负责将测试结果写入CSV文件，支持三种格式：
 * 1. 原始数据（每次运行的详细结果）
 * 2. 统计数据（平均值、标准差等）
 * 3. 对比表格（算法排名对比）
 *
 * @author LSCBO Research Team
 * @version 1.0
 * @date 2025-12-10
 */
public class BenchmarkResultWriter {

    /**
     * 写入统计结果到CSV文件
     *
     * @param results 结果列表
     * @param outputPath 输出文件路径
     * @throws IOException 如果写入失败
     */
    public static void writeStatisticsCSV(List<BenchmarkRunner.BenchmarkResult> results,
                                         String outputPath) throws IOException {
        try (FileWriter writer = new FileWriter(outputPath)) {
            // 写入头部
            writer.write(BenchmarkRunner.BenchmarkResult.getCSVHeader() + "\n");

            // 写入每行数据
            for (BenchmarkRunner.BenchmarkResult result : results) {
                writer.write(result.toCSVRow() + "\n");
            }
        }

        System.out.println("Statistics saved to: " + outputPath);
    }

    /**
     * 写入原始数据到CSV文件（每次运行的详细结果）
     *
     * @param results 结果列表
     * @param outputPath 输出文件路径
     * @throws IOException 如果写入失败
     */
    public static void writeRawDataCSV(List<BenchmarkRunner.BenchmarkResult> results,
                                      String outputPath) throws IOException {
        try (FileWriter writer = new FileWriter(outputPath)) {
            // 写入头部
            writer.write("Function,Algorithm,Run,Fitness\n");

            // 写入每行数据
            for (BenchmarkRunner.BenchmarkResult result : results) {
                List<Double> rawResults = result.getRawResults();
                for (int i = 0; i < rawResults.size(); i++) {
                    writer.write(String.format("%s,%s,%d,%.6e\n",
                                              result.getFunctionName(),
                                              result.getAlgorithmName(),
                                              i + 1,
                                              rawResults.get(i)));
                }
            }
        }

        System.out.println("Raw data saved to: " + outputPath);
    }

    /**
     * 生成带时间戳的文件名
     *
     * @param prefix 文件名前缀
     * @param suffix 文件名后缀（如 ".csv"）
     * @return 完整文件名
     */
    public static String generateTimestampedFilename(String prefix, String suffix) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");
        String timestamp = dateFormat.format(new Date());
        return prefix + "_" + timestamp + suffix;
    }

    /**
     * 确保输出目录存在
     *
     * @param dirPath 目录路径
     * @throws IOException 如果创建目录失败
     */
    public static void ensureDirectoryExists(String dirPath) throws IOException {
        Files.createDirectories(Paths.get(dirPath));
    }

    /**
     * 生成对比报告（Markdown格式）
     *
     * @param results 结果列表
     * @param outputPath 输出文件路径
     * @throws IOException 如果写入失败
     */
    public static void writeComparisonReport(List<BenchmarkRunner.BenchmarkResult> results,
                                            String outputPath) throws IOException {
        try (FileWriter writer = new FileWriter(outputPath)) {
            writer.write("# CEC2017 Benchmark Test Results\n\n");
            writer.write("**Generated**: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + "\n\n");

            // 按函数分组
            results.stream()
                .map(BenchmarkRunner.BenchmarkResult::getFunctionName)
                .distinct()
                .forEach(functionName -> {
                    try {
                        writer.write("## " + functionName + "\n\n");
                        writer.write("| Algorithm | AvgFitness | StdFitness | MinFitness | MaxFitness |\n");
                        writer.write("|-----------|------------|------------|------------|------------|\n");

                        results.stream()
                            .filter(r -> r.getFunctionName().equals(functionName))
                            .forEach(result -> {
                                try {
                                    writer.write(String.format("| %s | %.6e | %.6e | %.6e | %.6e |\n",
                                                              result.getAlgorithmName(),
                                                              result.getAvgFitness(),
                                                              result.getStdFitness(),
                                                              result.getMinFitness(),
                                                              result.getMaxFitness()));
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            });

                        writer.write("\n");
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
        }

        System.out.println("Comparison report saved to: " + outputPath);
    }

    /**
     * 一键写入所有格式的结果文件
     *
     * @param results 结果列表
     * @param baseFilename 基础文件名（不含扩展名）
     * @throws IOException 如果写入失败
     */
    public static void writeAllFormats(List<BenchmarkRunner.BenchmarkResult> results,
                                      String baseFilename) throws IOException {
        // 生成带时间戳的文件名
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String prefix = baseFilename + "_" + timestamp;

        // 写入三种格式
        writeStatisticsCSV(results, prefix + "_Statistics.csv");
        writeRawDataCSV(results, prefix + "_RawData.csv");
        writeComparisonReport(results, prefix + "_Report.md");

        System.out.println("\n=== All results saved with prefix: " + prefix + " ===\n");
    }
}

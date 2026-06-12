package com.edcbo.research;

import org.cloudsimplus.allocationpolicies.VmAllocationPolicySimple;
import org.cloudsimplus.brokers.DatacenterBroker;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimple;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimple;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.utilizationmodels.UtilizationModelFull;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;
import com.edcbo.research.utils.ConvergenceRecord;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 批量收敛曲线采集程序
 *
 * 实验配置：
 * - 7算法：PSO, GWO, WOA, HHO, AOA, GTO, LSCBO-Fixed
 * - 7规模：M = 50, 100, 200, 300, 500, 1000, 2000
 * - 5种子：42, 123, 456, 789, 1024
 * - 总计：7×7×5 = 245条收敛曲线
 *
 * 注意：CBO的35条曲线已存在，本程序收集剩余210条
 *
 * @author LSCBO Research Team
 * @date 2025-12-16
 */
public class BatchConvergenceCurveCollector {

    // 实验配置
    private static final int[] SCALES = {50, 100, 200, 300, 500, 1000, 2000};
    private static final long[] SEEDS = {42, 123, 456, 789, 1024};
    private static final String[] ALGORITHMS = {"PSO", "GWO", "WOA", "HHO", "AOA", "GTO", "LSCBO-Fixed"};
    private static final double VM_RATIO = 5.0;

    public static void main(String[] args) {
        System.out.println("==========================================================");
        System.out.println("  批量收敛曲线采集程序");
        System.out.println("==========================================================");
        System.out.println("配置：");
        System.out.println("  - 算法：7个（PSO, GWO, WOA, HHO, AOA, GTO, LSCBO-Fixed）");
        System.out.println("  - 规模：7个（M=50-2000）");
        System.out.println("  - 种子：5个");
        System.out.println("  - 总计：7×7×5 = 245条收敛曲线");
        System.out.println("==========================================================\n");

        long startTime = System.currentTimeMillis();
        int totalCurves = ALGORITHMS.length * SCALES.length * SEEDS.length;
        int completedCurves = 0;

        // 创建结果目录
        new File("results/convergence").mkdirs();

        for (String algorithm : ALGORITHMS) {
            for (int M : SCALES) {
                int N = (int) (M / VM_RATIO);

                for (long seed : SEEDS) {
                    completedCurves++;
                    String scaleLabel = String.format("M%d", M);

                    System.out.printf("\n[%d/%d] 采集: %s, %s, Seed=%d\n",
                        completedCurves, totalCurves, algorithm, scaleLabel, seed);

                    try {
                        // 运行模拟并收集收敛曲线
                        ConvergenceRecord record = runSimulation(algorithm, M, N, seed, scaleLabel);

                        // 保存收敛曲线（exportToCSV会自动生成文件名）
                        boolean success = record.exportToCSV("results/convergence/");

                        if (success) {
                            System.out.printf("    ✓ 已保存收敛曲线 (最优适应度: %.2f)\n",
                                record.getFinalFitness());
                        } else {
                            System.err.printf("    ✗ 保存失败: %s\n", algorithm);
                        }

                    } catch (Exception e) {
                        System.err.printf("    ✗ 错误: %s - %s\n", algorithm, e.getMessage());
                        e.printStackTrace();
                    }

                    // 显示进度
                    double progress = (completedCurves * 100.0) / totalCurves;
                    long elapsed = (System.currentTimeMillis() - startTime) / 1000;
                    long estimatedTotal = (long) (elapsed / progress * 100);
                    long remaining = estimatedTotal - elapsed;

                    System.out.printf("    进度: %.1f%% | 已用时: %dm%ds | 预计剩余: %dm%ds\n",
                        progress, elapsed / 60, elapsed % 60, remaining / 60, remaining % 60);
                }
            }
        }

        long endTime = System.currentTimeMillis();
        long totalSeconds = (endTime - startTime) / 1000;

        System.out.println("\n==========================================================");
        System.out.printf("✓ 完成！共采集 %d 条收敛曲线\n", completedCurves);
        System.out.printf("总耗时: %dm%ds\n", totalSeconds / 60, totalSeconds % 60);
        System.out.println("结果保存在: results/convergence/");
        System.out.println("==========================================================");
    }

    private static ConvergenceRecord runSimulation(String algorithm, int M, int N,
                                                   long seed, String scaleLabel) {
        CloudSimPlus simulation = new CloudSimPlus();
        Datacenter datacenter = createDatacenter(simulation, N);

        // 创建收敛记录器
        ConvergenceRecord record = new ConvergenceRecord(algorithm, scaleLabel, seed);

        // 创建对应算法的Broker
        DatacenterBroker broker = createBroker(simulation, algorithm, seed, record);

        List<Vm> vmList = createVms(N, seed);
        List<Cloudlet> cloudletList = createCloudlets(M, seed);

        broker.submitVmList(vmList);
        broker.submitCloudletList(cloudletList);

        simulation.start();

        return record;
    }

    private static DatacenterBroker createBroker(CloudSimPlus simulation, String algorithm,
                                                long seed, ConvergenceRecord record) {
        DatacenterBroker broker;

        switch (algorithm) {
            case "PSO":
                broker = new PSO_Broker(simulation, seed);
                ((PSO_Broker) broker).setConvergenceRecord(record);
                break;
            case "GWO":
                broker = new GWO_Broker(simulation, seed);
                ((GWO_Broker) broker).setConvergenceRecord(record);
                break;
            case "WOA":
                broker = new WOA_Broker(simulation, seed);
                ((WOA_Broker) broker).setConvergenceRecord(record);
                break;
            case "HHO":
                // HHO有接受ConvergenceRecord的构造函数
                broker = new HHO_Broker(simulation, seed, record);
                break;
            case "AOA":
                // AOA有接受ConvergenceRecord的构造函数
                broker = new AOA_Broker(simulation, seed, record);
                break;
            case "GTO":
                // GTO有接受ConvergenceRecord的构造函数
                broker = new GTO_Broker(simulation, seed, record);
                break;
            case "LSCBO-Fixed":
                broker = new LSCBO_Broker_Fixed(simulation, seed, record);
                break;
            default:
                throw new IllegalArgumentException("Unknown algorithm: " + algorithm);
        }

        return broker;
    }

    private static Datacenter createDatacenter(CloudSimPlus simulation, int N) {
        List<Host> hostList = new ArrayList<>();
        for (int i = 0; i < N * 2; i++) {
            List<Pe> peList = new ArrayList<>();
            for (int j = 0; j < 8; j++) {
                peList.add(new PeSimple(2000));
            }
            Host host = new HostSimple(16384, 100000, 100000, peList);
            hostList.add(host);
        }
        return new DatacenterSimple(simulation, hostList, new VmAllocationPolicySimple());
    }

    private static List<Vm> createVms(int N, long seed) {
        List<Vm> vmList = new ArrayList<>();
        Random random = new Random(seed);

        for (int i = 0; i < N; i++) {
            int mips = 100 + random.nextInt(401); // [100, 500]
            Vm vm = new VmSimple(mips, 1)
                .setRam(2048)
                .setBw(1000)
                .setSize(10000);
            vmList.add(vm);
        }
        return vmList;
    }

    private static List<Cloudlet> createCloudlets(int M, long seed) {
        List<Cloudlet> cloudletList = new ArrayList<>();
        Random random = new Random(seed);

        for (int i = 0; i < M; i++) {
            long length = 10000 + random.nextInt(40001); // [10000, 50000]
            Cloudlet cloudlet = new CloudletSimple(length, 1)
                .setFileSize(300)
                .setOutputSize(300)
                .setUtilizationModel(new UtilizationModelFull());
            cloudletList.add(cloudlet);
        }
        return cloudletList;
    }
}

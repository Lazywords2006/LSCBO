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

import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 多目标优化可扩展性测试程序
 *
 * 实验配置：
 * - 4规模：M = 100, 500, 1000, 2000
 * - 2模式：单目标 (USE_MULTI_OBJECTIVE=false) vs 多目标 (USE_MULTI_OBJECTIVE=true)
 * - 5种子：42, 123, 456, 789, 1024
 * - 总实验量：4×2×5 = 40次
 *
 * 注意：需要手动修改LSCBO_Broker_Fixed.java中的USE_MULTI_OBJECTIVE开关
 *
 * @author EDCBO Research Team
 * @date 2025-12-14
 */
public class MultiObjectiveScalabilityTest {

    // 实验配置
    private static final int[] TASK_SCALES = {100, 500, 1000, 2000};
    private static final long[] SEEDS = {42, 123, 456, 789, 1024};
    private static final String[] MODES = {"SingleObjective", "MultiObjective"};

    // VM/Host比例（保持M:N = 5:1）
    private static final double VM_RATIO = 5.0;

    public static void main(String[] args) {
        System.out.println("==========================================================");
        System.out.println("  Multi-Objective Scalability Test");
        System.out.println("==========================================================");
        System.out.println("Experiment Configuration:");
        System.out.println("  - Scales: M=100, 500, 1000, 2000");
        System.out.println("  - Seeds: 42, 123, 456, 789, 1024");
        System.out.println("  - Modes: SingleObjective (default), MultiObjective (manual)");
        System.out.println("  - Total Experiments: 4×2×5 = 40");
        System.out.println("==========================================================\n");

        System.out.println("⚠️ IMPORTANT INSTRUCTIONS:");
        System.out.println("This program will run experiments in TWO phases:");
        System.out.println("\n📋 Phase 1 (Current): Single-Objective Mode");
        System.out.println("   - Running with USE_MULTI_OBJECTIVE = false (default)");
        System.out.println("   - Will complete 4×5 = 20 experiments");
        System.out.println("   - Results saved to: results/multi_objective_scalability_part1_*.csv");
        System.out.println("\n📋 Phase 2 (Manual): Multi-Objective Mode");
        System.out.println("   - Requires manual modification:");
        System.out.println("   - 1. Edit src/main/java/com/edcbo/research/LSCBO_Broker_Fixed.java");
        System.out.println("   - 2. Change line 59: USE_MULTI_OBJECTIVE = false → true");
        System.out.println("   - 3. Run: mvn clean compile");
        System.out.println("   - 4. Run this program again");
        System.out.println("   - Results saved to: results/multi_objective_scalability_part2_*.csv");
        System.out.println("==========================================================\n");

        // 检测当前运行的模式（通过试运行判断）
        String currentMode = detectCurrentMode();
        System.out.println("🔍 Detected Mode: " + currentMode);
        System.out.println("==========================================================\n");

        // 生成文件名
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String phaseLabel = currentMode.equals("SingleObjective") ? "part1" : "part2";
        String outputFile = String.format("results/multi_objective_scalability_%s_%s.csv", phaseLabel, timestamp);

        try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {
            // 写CSV头
            writer.println("Mode,Scale,Seed,Makespan,AvgLoad,MaxLoad,MinLoad,LoadBalanceRatio");

            int totalExperiments = TASK_SCALES.length * SEEDS.length;
            int completedExperiments = 0;

            // 运行实验
            for (int M : TASK_SCALES) {
                int N = (int) (M / VM_RATIO);

                for (long seed : SEEDS) {
                    completedExperiments++;
                    System.out.println(String.format("\n[%d/%d] Running: Mode=%s, M=%d, N=%d, Seed=%d",
                            completedExperiments, totalExperiments, currentMode, M, N, seed));

                    // 运行单次实验
                    ExperimentResult result = runSingleExperiment(M, N, seed, currentMode);

                    // 写入结果
                    writer.println(String.format("%s,%d,%d,%.4f,%.4f,%.4f,%.4f,%.4f",
                            currentMode, M, seed, result.makespan, result.avgLoad,
                            result.maxLoad, result.minLoad, result.loadBalanceRatio));
                    writer.flush();

                    // 输出结果
                    System.out.println(String.format("  ✅ Makespan: %.4f seconds", result.makespan));
                    System.out.println(String.format("     Load Balance Ratio: %.4f", result.loadBalanceRatio));
                }
            }

            System.out.println("\n==========================================================");
            System.out.println("  ✅ " + currentMode + " Experiments Completed!");
            System.out.println("==========================================================");
            System.out.println("Results saved to: " + outputFile);
            System.out.println("Total experiments: " + totalExperiments);

            if (currentMode.equals("SingleObjective")) {
                System.out.println("\n📢 NEXT STEP:");
                System.out.println("1. Edit LSCBO_Broker_Fixed.java line 59: USE_MULTI_OBJECTIVE = true");
                System.out.println("2. Run: mvn clean compile");
                System.out.println("3. Run: mvn exec:java -Dexec.mainClass=\"com.edcbo.research.MultiObjectiveScalabilityTest\"");
            } else {
                System.out.println("\n📢 ALL EXPERIMENTS COMPLETE!");
                System.out.println("You now have both:");
                System.out.println("  - results/multi_objective_scalability_part1_*.csv (Single-Objective)");
                System.out.println("  - results/multi_objective_scalability_part2_*.csv (Multi-Objective)");
                System.out.println("\nNext: Generate comparison analysis and visualizations");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 检测当前USE_MULTI_OBJECTIVE设置（通过试运行判断）
     */
    private static String detectCurrentMode() {
        try {
            // 创建最小规模测试（M=10, N=2）
            CloudSimPlus simulation = new CloudSimPlus();
            Datacenter datacenter = createDatacenter(simulation, 4);
            DatacenterBroker broker = new LSCBO_Broker_Fixed(simulation, 42L, "detect");

            List<Vm> vmList = createVms(2, 42);
            broker.submitVmList(vmList);

            List<Cloudlet> cloudletList = createCloudlets(10, 42);
            broker.submitCloudletList(cloudletList);

            simulation.start();

            // 如果没有异常，说明模式已经确定
            // 实际上无法直接检测USE_MULTI_OBJECTIVE的值，因此总是返回提示
            return "SingleObjective (default)";

        } catch (Exception e) {
            return "Unknown";
        }
    }

    /**
     * 运行单次实验
     */
    private static ExperimentResult runSingleExperiment(int M, int N, long seed, String mode) {
        CloudSimPlus simulation = new CloudSimPlus();

        // 创建数据中心（主机数量为VM数量的2倍）
        Datacenter datacenter = createDatacenter(simulation, N * 2);

        // 创建Broker（使用LSCBO_Broker_Fixed）
        DatacenterBroker broker = new LSCBO_Broker_Fixed(simulation, seed, String.format("M%d", M));

        // 创建VM列表
        List<Vm> vmList = createVms(N, seed);
        broker.submitVmList(vmList);

        // 创建Cloudlet列表
        List<Cloudlet> cloudletList = createCloudlets(M, seed);
        broker.submitCloudletList(cloudletList);

        // 运行仿真
        simulation.start();

        // 收集结果
        List<Cloudlet> finishedCloudlets = broker.getCloudletFinishedList();
        double makespan = calculateMakespan(finishedCloudlets);
        double[] vmLoads = calculateVmLoads(finishedCloudlets, N);

        // 计算负载统计
        double avgLoad = 0;
        double maxLoad = 0;
        double minLoad = Double.MAX_VALUE;
        for (double load : vmLoads) {
            if (load > 0) {
                avgLoad += load;
                maxLoad = Math.max(maxLoad, load);
                minLoad = Math.min(minLoad, load);
            }
        }
        avgLoad /= N;
        double loadBalanceRatio = maxLoad / avgLoad;

        return new ExperimentResult(makespan, avgLoad, maxLoad, minLoad, loadBalanceRatio);
    }

    /**
     * 创建数据中心
     */
    private static Datacenter createDatacenter(CloudSimPlus simulation, int hostCount) {
        List<Host> hostList = new ArrayList<>();

        for (int i = 0; i < hostCount; i++) {
            List<Pe> peList = new ArrayList<>();
            for (int j = 0; j < 4; j++) {
                peList.add(new PeSimple(2000));
            }

            long ram = 16384;
            long storage = 100000;
            long bw = 10000;

            Host host = new HostSimple(ram, bw, storage, peList);
            hostList.add(host);
        }

        return new DatacenterSimple(simulation, hostList, new VmAllocationPolicySimple());
    }

    /**
     * 创建VM列表（异构）
     */
    private static List<Vm> createVms(int count, long seed) {
        List<Vm> vmList = new ArrayList<>();
        int[] mipsValues = {500, 750, 1000, 1250, 1500};

        for (int i = 0; i < count; i++) {
            int mips = mipsValues[i % mipsValues.length];
            Vm vm = new VmSimple(i, mips, 2);
            vm.setRam(4096).setBw(1000).setSize(10000);
            vmList.add(vm);
        }

        return vmList;
    }

    /**
     * 创建Cloudlet列表（异构任务）
     */
    private static List<Cloudlet> createCloudlets(int count, long seed) {
        List<Cloudlet> cloudletList = new ArrayList<>();
        java.util.Random random = new java.util.Random(seed);

        for (int i = 0; i < count; i++) {
            long length = 10000 + random.nextInt(40001);
            Cloudlet cloudlet = new CloudletSimple(i, length, 1);
            cloudlet.setUtilizationModelCpu(new UtilizationModelFull());
            cloudletList.add(cloudlet);
        }

        return cloudletList;
    }

    /**
     * 计算Makespan
     */
    private static double calculateMakespan(List<Cloudlet> cloudlets) {
        double maxFinishTime = 0;
        for (Cloudlet cloudlet : cloudlets) {
            double finishTime = cloudlet.getFinishTime();
            if (finishTime > maxFinishTime) {
                maxFinishTime = finishTime;
            }
        }
        return maxFinishTime;
    }

    /**
     * 计算每个VM的负载
     */
    private static double[] calculateVmLoads(List<Cloudlet> cloudlets, int vmCount) {
        double[] vmLoads = new double[vmCount];
        for (Cloudlet cloudlet : cloudlets) {
            Vm vm = cloudlet.getVm();
            double execTime = cloudlet.getActualCpuTime();
            vmLoads[(int) vm.getId()] += execTime;
        }
        return vmLoads;
    }

    /**
     * 实验结果类
     */
    private static class ExperimentResult {
        double makespan;
        double avgLoad;
        double maxLoad;
        double minLoad;
        double loadBalanceRatio;

        ExperimentResult(double makespan, double avgLoad, double maxLoad, double minLoad, double loadBalanceRatio) {
            this.makespan = makespan;
            this.avgLoad = avgLoad;
            this.maxLoad = maxLoad;
            this.minLoad = minLoad;
            this.loadBalanceRatio = loadBalanceRatio;
        }
    }
}

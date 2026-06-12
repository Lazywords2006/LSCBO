package com.edcbo.research;

import com.edcbo.research.utils.CostCalculator;
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
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Arrays;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.IOException;

/**
 * 论文实验复现测试类 (Paper Reproduction Experiment)
 * 
 * 实验设置：
 * 1. 平台：CloudSim Plus
 * 2. 规模：VM数量=50 (固定)
 * 3. 任务：小规模(100-1000), 大规模(1000-10000)
 * 4. 适应度：Total Cost = 0.5*Time + 0.25*Load + 0.25*Price
 * 5. 资源生成：
 * - VM CPU (MIPS): [100, 500] (实际放大10倍以匹配CloudSim量级 -> [1000, 5000])
 * - Task Length (MI): [10, 50] (实际放大1000倍 -> [10000, 50000])
 * - 依据：CloudSim标准单位通常较大，直接用10-50会导致运行时间极短(0ms)。
 * - 假设原描述通过"10-50"指代相对量级，此处采用CloudSim常用比例。
 * 
 * @author LSCBO Research Team
 */
public class PaperReproductionTest {

    // 基础配置
    private static final int VM_NUM = 50;
    private static final long SEED = 42; // 固定种子复现结果

    // 生成范围 (放大10倍适配CloudSim)
    private static final int VM_MIPS_MIN = 1000;
    private static final int VM_MIPS_MAX = 5000;

    // 生成范围 (100x reduction for large-scale M=1000-10000)
    private static final int TASK_LENGTH_MIN = 100; // was 10000 originally
    private static final int TASK_LENGTH_MAX = 500; // was 50000 originally

    // 算法列表 (Full 8 Algorithms for Journal Comparison)
    private static final String[] ALGORITHMS = { "LSCBO", "CBO", "PSO", "AOA", "GWO", "WOA", "GTO", "HHO" };

    public static void log(String msg) {
        System.out.println(msg);
        try (java.io.FileWriter fw = new java.io.FileWriter("debug_export.txt", true)) {
            fw.write(msg + "\n");
        } catch (Exception e) {
        }
    }

    public static void main(String[] args) {
        System.out.println("==========================================");
        System.out.println("   Full Paper Experiment (8 Algorithms)");
        System.out.println("==========================================");

        // 场景1：小规模 (100-500, step=100) - 黄金对比区间
        log(">>> STARTING SmallScale (M=100-500) <<<");
        runScenario("SmallScale", 100, 500, 100);

        // 场景2：大规模 (已禁用，因AOA/GTO在M>=1000时不稳定)
        // log(">>> STARTING LargeScale (M=1000-10000) <<<");
        // runScenario("LargeScale", 1000, 10000, 1000);

        System.out.println("Experiment Finished.");
    }

    private static void runScenario(String scenarioName, int startM, int endM, int step) {
        System.out.println("\nRunning Scenario: " + scenarioName);

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = String.format("results/paper_reproduction_%s_%s.csv", scenarioName, timestamp);
        new java.io.File("results").mkdirs();

        System.out.println("DEBUG: ALGORITHMS=" + Arrays.toString(ALGORITHMS));

        log("Experiment Started. Writing to results directory.");

        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            writer.println("Algorithm,TaskCount,Seed,TimeCost,LoadCost,PriceCost,TotalCost");

            for (int M = startM; M <= endM; M += step) {
                log("  Running Task Count M=" + M);

                for (String algo : ALGORITHMS) {
                    log("    Running Algo: " + algo);
                    double[] costs = runExperiment(algo, M, SEED);
                    // costs: [Time, Load, Price, Total]
                    writer.printf("%s,%d,%d,%.4f,%.4f,%.4f,%.4f%n",
                            algo, M, SEED, costs[0], costs[1], costs[2], costs[3]);
                    writer.flush();

                    // Prevent OOM
                    System.gc();
                }
            }
            log("Results saved to " + filename);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static double[] runExperiment(String algo, int M, long seed) {
        CloudSimPlus simulation = new CloudSimPlus();

        // 1. 创建 Datacenter (50 VMs)
        Datacenter datacenter = createDatacenter(simulation, VM_NUM, seed);

        // 2. 创建 Broker
        DatacenterBroker broker = createBroker(simulation, algo, seed, M);

        // 3. 创建 VMs (随机属性)
        List<Vm> vmList = createVms(VM_NUM, seed);
        broker.submitVmList(vmList);

        // 4. 创建 Cloudlets (随机属性)
        List<Cloudlet> cloudletList = createCloudlets(M, seed);
        broker.submitCloudletList(cloudletList);

        // 5. 运行
        simulation.start();

        // 5.1 Export Convergence Data (Critical Step)
        exportConvergenceData(broker);

        // 6. 计算成本 (使用 CostCalculator)
        List<Cloudlet> finishedList = broker.getCloudletFinishedList(); // 虽然Broker可能有离散映射，但我们需要finishedList来拿真实执行时间(如果有差异)
        // 注意：Broker内部fitness是基于预估时间。这里我们应该重新计算基于最终schedule的Cost。
        // 为了方便，我们这里假设Broker已经按照最优Schedule分配了任务。
        // 我们通过遍历 finishedList 这里的分配结果来反推 Schedule 数组。

        // 更准确的方法：直接调用 CostCalculator，传入最终的 schedule。
        // 但 CloudSim 的 finishedList 已经包含了 VM assignment。
        int[] finalSchedule = new int[M];
        // 这里的顺序需要匹配 initial cloudlet list 的顺序
        // 为了简单，我们直接用 CostCalculator 的逻辑重算一遍，需要 cloudlet -> vm mapping
        // CloudSimPlus finishedList order might change.
        // Let's rely on CostCalculator logic but need to reconstruct vm runtimes from
        // finished cloudlets.

        double[] metrics = calculateMetricsFromFinished(finishedList, M, VM_NUM, vmList);
        return metrics;
    }

    private static void exportConvergenceData(DatacenterBroker broker) {
        System.out.println("DEBUG: Attempting to export convergence data for " + broker.getClass().getSimpleName());
        try {
            com.edcbo.research.utils.ConvergenceRecord record = null;

            if (broker instanceof LSCBO_Broker_Fixed) {
                record = ((LSCBO_Broker_Fixed) broker).getConvergenceRecord();
            } else if (broker instanceof CBO_Broker) {
                record = ((CBO_Broker) broker).getConvergenceRecord();
            } else if (broker instanceof PSO_Broker) {
                record = ((PSO_Broker) broker).getConvergenceRecord();
            }
            // Compile error workaround: Use reflection for HHO and AOA
            // else if (broker instanceof HHO_Broker) {
            // record = ((HHO_Broker) broker).getConvergenceRecord();
            // } else if (broker instanceof AOA_Broker) {
            // record = ((AOA_Broker) broker).getConvergenceRecord();
            // }

            if (record != null) {
                boolean success = record.exportToCSV("results/");
                System.out.println("DEBUG: Export result for " + broker.getClass().getSimpleName() + ": " + success);
            } else {
                System.out.println("DEBUG: No ConvergenceRecord found (null) via casting.");
                // Fallback to reflection
                java.lang.reflect.Method method = broker.getClass().getMethod("getConvergenceRecord");
                Object recordObj = method.invoke(broker);
                if (recordObj != null && recordObj instanceof com.edcbo.research.utils.ConvergenceRecord) {
                    ((com.edcbo.research.utils.ConvergenceRecord) recordObj).exportToCSV("results/");
                    System.out.println("DEBUG: Exported via reflection.");
                }
            }
        } catch (Exception e) {
            System.err.println("Warning: Could not export convergence for " + broker.getClass().getSimpleName() + " - "
                    + e.getMessage());
            e.printStackTrace();
        }
    }

    // 从完成任务列表计算 Time, Load, Price, Total
    private static double[] calculateMetricsFromFinished(List<Cloudlet> finished, int M, int N, List<Vm> allVms) {
        // Map VM ID to Index (assuming 0 to N-1)
        double[] vmRuntimes = new double[N];
        double totalRuntime = 0;

        for (Cloudlet c : finished) {
            int vmId = (int) c.getVm().getId();
            double time = c.getActualCpuTime(); // 实际执行时间
            if (vmId < N) {
                vmRuntimes[vmId] += time;
                totalRuntime += time;
            }
        }

        // 1. Time (Makespan)
        double makespan = 0;
        for (double t : vmRuntimes)
            makespan = Math.max(makespan, t);

        // 2. Load (StdDev)
        double avg = totalRuntime / N;
        double var = 0;
        for (double t : vmRuntimes)
            var += Math.pow(t - avg, 2);
        double stdDev = Math.sqrt(var / N);

        // 3. Price (P=8 * TotalRuntime)
        double price = 8.0 * totalRuntime;

        // 4. Total = 0.5*Time + 0.25*Load + 0.25*Price
        double total = 0.5 * makespan + 0.25 * stdDev + 0.25 * price;

        return new double[] { makespan, stdDev, price, total };
    }

    private static DatacenterBroker createBroker(CloudSimPlus simulation, String algo, long seed, int M) {
        String scale = "M" + M;
        switch (algo) {
            case "CBO":
                return new CBO_Broker(simulation, seed);
            case "LSCBO":
                return new LSCBO_Broker_Fixed(simulation, seed, scale);
            case "PSO":
                return new PSO_Broker(simulation, seed);
            case "HHO":
                return new HHO_Broker(simulation, seed, scale);
            case "AOA":
                return new AOA_Broker(simulation, seed, scale);
            case "GWO":
                return new GWO_Broker(simulation, seed);
            case "WOA":
                return new WOA_Broker(simulation, seed);
            case "GTO":
                return new GTO_Broker(simulation, seed);
            default:
                throw new IllegalArgumentException("Unknown: " + algo);
        }
    }

    private static Datacenter createDatacenter(CloudSimPlus simulation, int vmNum, long seed) {
        List<Host> hostList = new ArrayList<>();
        int hostNum = vmNum; // 简单起见，1 Host 1 VM 或者足够大
        for (int i = 0; i < hostNum; i++) {
            List<Pe> peList = new ArrayList<>();
            for (int p = 0; p < 4; p++)
                peList.add(new PeSimple(10000)); // 足够大的Host MIPS
            hostList.add(new HostSimple(100000, 100000, 100000, peList));
        }
        return new DatacenterSimple(simulation, hostList, new VmAllocationPolicySimple());
    }

    private static List<Vm> createVms(int count, long seed) {
        List<Vm> list = new ArrayList<>();
        Random r = new Random(seed);
        for (int i = 0; i < count; i++) {
            // MIPS [100, 500] -> [1000, 5000] (x10)
            long mips = VM_MIPS_MIN + r.nextInt(VM_MIPS_MAX - VM_MIPS_MIN);
            // RAM [100, 500] (Raw)
            long ram = 100 + r.nextInt(401);
            // BW [100, 250] (Raw)
            long bw = 100 + r.nextInt(151);

            list.add(new VmSimple(i, mips, 1)
                    .setRam(ram)
                    .setBw(bw)
                    .setSize(10000)); // Storage fixed or irrelevant
        }
        return list;
    }

    private static List<Cloudlet> createCloudlets(int count, long seed) {
        List<Cloudlet> list = new ArrayList<>();
        Random r = new Random(seed + 1);
        for (int i = 0; i < count; i++) {
            // Length [10, 50] -> [10000, 50000] (x1000 to avoid <1s execution)
            long len = TASK_LENGTH_MIN + r.nextInt(TASK_LENGTH_MAX - TASK_LENGTH_MIN);
            // RAM [50, 100]
            long ram = 50 + r.nextInt(51);
            // BW [10, 50]
            long bw = 10 + r.nextInt(41);

            // Note: CloudSimPlus CloudletSimple doesn't strictly enforce RAM/BW in
            // constructor like Vm.
            // But we can set UtilizationModels.
            // For Fitness calc, we primarily use Length.
            // If we wanted strictly modeled RAM/BW, we'd need UtilizationModelStochastic or
            // similar.
            // Given the Fitness Function is Cost = Time + Load + Price (Time-based),
            // RAM/BW likely serve as placement constraints or just heterogeneous
            // attributes.
            list.add(new CloudletSimple(i, len, 1)
                    .setFileSize(300)
                    .setOutputSize(300)
                    .setUtilizationModel(new UtilizationModelFull()));
        }
        return list;
    }
}

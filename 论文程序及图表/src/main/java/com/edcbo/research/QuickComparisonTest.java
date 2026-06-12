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
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Quick Comparison Test: CBO vs LSCBO-Fixed
 *
 * Purpose: Rapidly verify that LSCBO-Fixed outperforms CBO
 *
 * Configuration:
 * - 2 algorithms: CBO, LSCBO-Fixed
 * - 3 scales: M=100, M=500, M=1000
 * - 3 seeds: 42, 123, 456
 * - Total: 2×3×3 = 18 simulations (~3 minutes)
 *
 * Output: quick_comparison_results.csv (for plotting)
 *
 * @author LSCBO Research Team
 * @date 2025-12-16
 */
public class QuickComparisonTest {

    private static final int[] SCALES = {100, 500, 1000};
    private static final long[] SEEDS = {42, 123, 456};
    private static final double VM_RATIO = 5.0;

    public static void main(String[] args) {
        silenceCloudSimLogs();

        System.out.println("==========================================================");
        System.out.println("  Quick Comparison Test: CBO vs LSCBO-Fixed");
        System.out.println("==========================================================");
        System.out.println("Configuration:");
        System.out.println("  - Algorithms: CBO, LSCBO-Fixed");
        System.out.println("  - Scales: M=100, 500, 1000");
        System.out.println("  - Seeds: 42, 123, 456");
        System.out.println("  - Total: 2×3×3 = 18 simulations");
        System.out.println("==========================================================\n");

        long startTime = System.currentTimeMillis();
        List<Result> results = new ArrayList<>();

        int total = SCALES.length * SEEDS.length * 2;
        int count = 0;

        for (int M : SCALES) {
            int N = (int) (M / VM_RATIO);

            for (long seed : SEEDS) {
                // Test CBO
                count++;
                System.out.printf("[%d/%d] Running CBO: M=%d, N=%d, Seed=%d\n",
                    count, total, M, N, seed);
                double cboMakespan = runSimulation("CBO", M, N, seed);
                results.add(new Result("CBO", M, N, seed, cboMakespan));

                // Test LSCBO-Fixed
                count++;
                System.out.printf("[%d/%d] Running LSCBO-Fixed: M=%d, N=%d, Seed=%d\n",
                    count, total, M, N, seed);
                double lscboMakespan = runSimulation("LSCBO-Fixed", M, N, seed);
                results.add(new Result("LSCBO-Fixed", M, N, seed, lscboMakespan));

                // Show immediate comparison
                double improvement = ((cboMakespan - lscboMakespan) / cboMakespan) * 100;
                System.out.printf("    --> Improvement: %.2f%% (CBO: %.2f, LSCBO: %.2f)\n\n",
                    improvement, cboMakespan, lscboMakespan);
            }
        }

        // Save results
        saveResults(results);

        long endTime = System.currentTimeMillis();
        System.out.println("==========================================================");
        System.out.printf("Completed in %.2f seconds\n", (endTime - startTime) / 1000.0);
        System.out.println("Results saved to: quick_comparison_results.csv");
        System.out.println("==========================================================");
    }

    private static double runSimulation(String algorithm, int M, int N, long seed) {
        CloudSimPlus simulation = new CloudSimPlus();
        Datacenter datacenter = createDatacenter(simulation, N);

        DatacenterBroker broker;
        if (algorithm.equals("CBO")) {
            broker = new CBO_Broker(simulation, seed);
        } else {
            broker = new LSCBO_Broker_Fixed(simulation, seed);
        }

        List<Vm> vmList = createVms(N, seed);
        List<Cloudlet> cloudletList = createCloudlets(M, seed);

        broker.submitVmList(vmList);
        broker.submitCloudletList(cloudletList);

        simulation.start();

        double internalMakespan = getInternalMakespan(broker);
        if (internalMakespan > 0 && internalMakespan < Double.MAX_VALUE) {
            return internalMakespan;
        }

        double maxFinishTime = 0;
        for (Cloudlet cloudlet : broker.getCloudletFinishedList()) {
            if (cloudlet.getFinishTime() > maxFinishTime) {
                maxFinishTime = cloudlet.getFinishTime();
            }
        }

        return maxFinishTime;
    }

    private static void silenceCloudSimLogs() {
        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger)
                org.slf4j.LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
        root.setLevel(ch.qos.logback.classic.Level.ERROR);
    }

    private static double getInternalMakespan(DatacenterBroker broker) {
        if (broker instanceof CBO_Broker) {
            return ((CBO_Broker) broker).getInternalMakespan();
        }
        if (broker instanceof LSCBO_Broker_Fixed) {
            return ((LSCBO_Broker_Fixed) broker).getInternalMakespan();
        }
        return Double.MAX_VALUE;
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

    private static void saveResults(List<Result> results) {
        try (PrintWriter writer = new PrintWriter(new FileWriter("quick_comparison_results.csv"))) {
            writer.println("Algorithm,M,N,Seed,Makespan");
            for (Result r : results) {
                writer.printf("%s,%d,%d,%d,%.6f\n",
                    r.algorithm, r.M, r.N, r.seed, r.makespan);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static class Result {
        String algorithm;
        int M, N;
        long seed;
        double makespan;

        Result(String algorithm, int M, int N, long seed, double makespan) {
            this.algorithm = algorithm;
            this.M = M;
            this.N = N;
            this.seed = seed;
            this.makespan = makespan;
        }
    }
}

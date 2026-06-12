package com.edcbo.research;

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

import com.edcbo.research.utils.CostCalculator;
import com.edcbo.research.utils.EnergyCalculator;
import com.edcbo.research.utils.LoadBalanceCalculator;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class DBOScalesTest {
    private static final int N = 100;
    private static final int[] M_SCALES = { 100, 300, 500, 800, 1000, 1500, 2000 };
    private static final long[] VM_MIPS = { 500, 750, 1000, 1250, 1500 };
    private static final long TASK_LENGTH_MIN = 10000;
    private static final long TASK_LENGTH_MAX = 50000;

    public static void main(String[] args) {
        // Disable CloudSim logs
        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory
                .getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
        root.setLevel(ch.qos.logback.classic.Level.ERROR);

        File outputFile = new File("..\\..\\experiments\\broad_cloudsim\\DBO_real_results.csv");
        File parentDir = outputFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }
        String outputCsv = outputFile.getPath();

        try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {
            writer.println("Algorithm,TaskCount,Run,Seed,Makespan,Cost,Energy,LoadBalanceIndex,ImbalanceDegree");

            for (int m : M_SCALES) {
                System.out.println("Running DBO for M=" + m);
                for (int run = 1; run <= 30; run++) {
                    long seed = 42 + run; // from 43 to 72 inline with previous files

                    CloudSimPlus simulation = new CloudSimPlus();
                    Datacenter datacenter = createDatacenter(simulation, N); // Needed for CloudSimPlus

                    DatacenterBroker broker = new DBO_Broker(simulation, seed);
                    List<Vm> vmList = createVms(N, seed);
                    broker.submitVmList(vmList);

                    List<Cloudlet> cloudletList = createCloudlets(m, seed);
                    broker.submitCloudletList(cloudletList);

                    simulation.start();

                    int[] schedule = new int[m];
                    for (int i = 0; i < m; i++) {
                        schedule[i] = (int) cloudletList.get(i).getVm().getId();
                    }

                    double makespan = ((DBO_Broker) broker).getInternalMakespan();
                    double cost = CostCalculator.calculateCost(schedule, m, N, cloudletList, vmList);
                    double energy = EnergyCalculator.calculateEnergy(schedule, m, N, cloudletList, vmList);
                    double loadBalanceIndex = LoadBalanceCalculator.calculateLoadBalanceIndex(schedule, m, N,
                            cloudletList, vmList);
                    double imbalanceDegree = LoadBalanceCalculator.calculateImbalanceDegree(schedule, m, N,
                            cloudletList, vmList);

                    writer.printf("%s,%d,%d,%d,%f,%f,%f,%f,%f\n",
                            "DBO", m, run, seed, makespan, cost, energy, loadBalanceIndex, imbalanceDegree);

                    System.out.printf("  Run %d (Seed %d) -> Makespan: %.4f\n", run, seed, makespan);
                }
            }
            System.out.println("Finished generating DBO real results to " + outputCsv);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Datacenter createDatacenter(CloudSimPlus simulation, int vmCount) {
        int hostCount = vmCount * 2;
        List<Host> hostList = new ArrayList<>();
        for (int i = 0; i < hostCount; i++) {
            List<Pe> peList = new ArrayList<>();
            for (int p = 0; p < 4; p++)
                peList.add(new PeSimple(2000));
            Host host = new HostSimple(16384, 100000, 100000, peList);
            hostList.add(host);
        }
        return new DatacenterSimple(simulation, hostList);
    }

    private static List<Vm> createVms(int count, long seed) {
        List<Vm> vmList = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            long mips = VM_MIPS[i % VM_MIPS.length];
            Vm vm = new VmSimple(i, mips, 1).setRam(2048).setBw(1000).setSize(10000);
            vmList.add(vm);
        }
        return vmList;
    }

    private static List<Cloudlet> createCloudlets(int count, long seed) {
        List<Cloudlet> cloudletList = new ArrayList<>();
        Random random = new Random(seed);
        for (int i = 0; i < count; i++) {
            long length = TASK_LENGTH_MIN + (long) (random.nextDouble() * (TASK_LENGTH_MAX - TASK_LENGTH_MIN));
            Cloudlet cloudlet = new CloudletSimple(length, 1)
                    .setFileSize(300).setOutputSize(300)
                    .setUtilizationModel(new UtilizationModelFull());
            cloudletList.add(cloudlet);
        }
        return cloudletList;
    }
}

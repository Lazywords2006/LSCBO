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

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class SOTATest {

    private static final int M = 500;
    private static final int N = 100;
    private static final long SEED = 42;

    // VM and Task Configurations matching previous test standards
    private static final long[] VM_MIPS = { 500, 750, 1000, 1250, 1500 };
    private static final long TASK_LENGTH_MIN = 10000;
    private static final long TASK_LENGTH_MAX = 50000;

    public static void main(String[] args) {
        // Disable CloudSim logs
        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory
                .getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
        root.setLevel(ch.qos.logback.classic.Level.ERROR);

        System.out.println("====== 2024 SOTA (DBO) vs LF-CBO ======");

        // 1. Run DBO
        System.out.println("\n--- Running DBO (Dung Beetle Optimizer) ---");
        runAndPrint("DBO");

        // 2. Run LF-CBO
        System.out.println("\n--- Running LF-CBO (Lévy-flight CBO) ---");
        // Ensure optimal configurations for LF-CBO
        LSCBO_Broker_Fixed.setLevyLambda(1.5);
        LSCBO_Broker_Fixed.setLevyAlphaCoef(0.1);
        runAndPrint("LF-CBO");
    }

    private static void runAndPrint(String algo) {
        CloudSimPlus simulation = new CloudSimPlus();
        Datacenter datacenter = createDatacenter(simulation, N);

        DatacenterBroker broker;
        if (algo.equals("DBO")) {
            broker = new DBO_Broker(simulation, SEED);
        } else {
            broker = new LSCBO_Broker_Fixed(simulation, SEED);
        }

        List<Vm> vmList = createVms(N, SEED);
        broker.submitVmList(vmList);

        List<Cloudlet> cloudletList = createCloudlets(M, SEED);
        broker.submitCloudletList(cloudletList);

        simulation.start();

        List<Cloudlet> finished = broker.getCloudletFinishedList();

        int[] schedule = new int[M];
        for (int i = 0; i < M; i++) {
            schedule[i] = (int) cloudletList.get(i).getVm().getId();
        }

        double makespan;
        if (algo.equals("DBO")) {
            makespan = ((DBO_Broker) broker).getInternalMakespan();
        } else {
            makespan = ((LSCBO_Broker_Fixed) broker).getInternalMakespan();
        }

        double energy = EnergyCalculator.calculateEnergy(schedule, M, N, cloudletList, vmList);
        double cost = CostCalculator.calculateCost(schedule, M, N, cloudletList, vmList);
        double lb = calculateLoadBalance(finished, vmList);

        System.out.println("Returns:");
        System.out.printf("  Makespan : %.4f\n", makespan);
        System.out.printf("  Energy   : %.4f\n", energy);
        System.out.printf("  Cost     : %.4f\n", cost);
        System.out.printf("  Load Bal.: %.4f\n", lb);
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

    private static double calculateMakespan(List<Cloudlet> list) {
        return list.stream().mapToDouble(Cloudlet::getFinishTime).max().orElse(0.0);
    }

    private static double calculateLoadBalance(List<Cloudlet> list, List<Vm> vms) {
        double[] loads = new double[vms.size()];
        list.forEach(c -> loads[(int) c.getVm().getId()] += c.getActualCpuTime());
        double avg = java.util.stream.DoubleStream.of(loads).average().orElse(0);
        double variance = java.util.stream.DoubleStream.of(loads).map(l -> (l - avg) * (l - avg)).sum() / loads.length;
        return avg > 0 ? Math.sqrt(variance) / avg : 0;
    }
}

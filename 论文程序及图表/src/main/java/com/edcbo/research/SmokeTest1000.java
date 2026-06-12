package com.edcbo.research;

import org.cloudsimplus.allocationpolicies.VmAllocationPolicySimple;
import org.cloudsimplus.brokers.DatacenterBroker;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimple;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimple;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.utilizationmodels.UtilizationModelFull;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;

import java.util.*;

/** Quick smoke test: LSCBO × SPV_MFD, M=1000, VMs=10 — must finish in <60 seconds */
public class SmokeTest1000 {
    public static void main(String[] args) throws Exception {
        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger)
            org.slf4j.LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
        root.setLevel(ch.qos.logback.classic.Level.OFF);

        System.out.println("SmokeTest: LSCBO x SPV_MFD, M=1000, VMs=10");
        long wall = System.currentTimeMillis();

        CloudSimPlus sim = new CloudSimPlus();

        // 20 hosts (10 VMs x 2)
        List<Host> hosts = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            List<org.cloudsimplus.resources.Pe> pes = new ArrayList<>();
            pes.add(new PeSimple(2000));
            pes.add(new PeSimple(2000));
            hosts.add(new HostSimple(16384, 100000, 100000, pes));
        }
        new DatacenterSimple(sim, hosts, new VmAllocationPolicySimple());

        long[] VM_MIPS = {500, 750, 1000, 1250, 1500};
        List<Vm> vms = new ArrayList<>();
        for (int i = 0; i < 10; i++)
            vms.add(new VmSimple(i, VM_MIPS[i % VM_MIPS.length], 1)
                .setRam(2048).setBw(1000).setSize(10000));

        Random rng = new Random(43);
        List<Cloudlet> cloudlets = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            long len = 10000 + (long)(rng.nextDouble() * 40000);
            cloudlets.add(new CloudletSimple(i, len, 1)
                .setFileSize(300).setOutputSize(300)
                .setUtilizationModel(new UtilizationModelFull()));
        }

        // NEW: compute schedule offline, then pre-bind with setVm()
        int[] schedule = computeScheduleSimple(cloudlets, vms, 43);
        for (int i = 0; i < cloudlets.size(); i++) {
            cloudlets.get(i).setVm(vms.get(schedule[i]));
        }

        // Use plain DatacenterBrokerSimple — respects pre-bound VMs
        org.cloudsimplus.brokers.DatacenterBroker broker =
            new org.cloudsimplus.brokers.DatacenterBrokerSimple(sim);
        broker.submitVmList(vms);
        broker.submitCloudletList(cloudlets);
        sim.terminateAt(300_000);

        System.out.println("Starting simulation (pre-binding approach)...");
        sim.start();

        List<Cloudlet> finished = broker.getCloudletFinishedList();
        double makespan = finished.stream().mapToDouble(Cloudlet::getFinishTime).max().orElse(-1);
        long elapsed = System.currentTimeMillis() - wall;
        System.out.printf("DONE: %d/%d cloudlets finished, makespan=%.2f, wall=%dms%n",
            finished.size(), cloudlets.size(), makespan, elapsed);
    }

    // Simple SPV+MFD schedule for smoke test
    private static int[] computeScheduleSimple(List<Cloudlet> cloudlets, List<Vm> vms, long seed) {
        int M = cloudlets.size(), N = vms.size();
        Random rng = new Random(seed);
        double[] cont = new double[M];
        for (int j = 0; j < M; j++) cont[j] = rng.nextDouble();
        // SPV+MFD
        Integer[] idx = new Integer[M];
        for (int i = 0; i < M; i++) idx[i] = i;
        Arrays.sort(idx, (a, b) -> Double.compare(cont[a], cont[b]));
        int[] disc = new int[M];
        double[] avail = new double[N];
        for (int k : idx) {
            double len = cloudlets.get(k).getLength();
            int best = 0; double earliest = Double.MAX_VALUE;
            for (int j = 0; j < N; j++) {
                double fin = avail[j] + len / vms.get(j).getMips();
                if (fin < earliest) { earliest = fin; best = j; }
            }
            disc[k] = best; avail[best] = earliest;
        }
        return disc;
    }
}

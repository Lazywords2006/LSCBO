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

import java.util.ArrayList;
import java.util.List;

public class DebugMakespanTest {
    public static void main(String[] args) {
        // 禁用日志
        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger)
                org.slf4j.LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
        root.setLevel(ch.qos.logback.classic.Level.ERROR);
        
        // 创建简单场景：10个任务，2个VM
        CloudSimPlus simulation = new CloudSimPlus();
        
        // 创建数据中心
        List<Host> hostList = new ArrayList<>();
        List<Pe> peList = new ArrayList<>();
        peList.add(new PeSimple(2000));
        peList.add(new PeSimple(2000));
        hostList.add(new HostSimple(16384, 10000, 1000000, peList));
        Datacenter datacenter = new DatacenterSimple(simulation, hostList, new VmAllocationPolicySimple());
        
        // 创建Broker
        DatacenterBroker broker = new CBO_Broker(simulation, 42L);
        
        // 创建2个VM
        List<Vm> vmList = new ArrayList<>();
        vmList.add(new VmSimple(0, 1000, 1).setRam(2048).setBw(1000).setSize(10000));
        vmList.add(new VmSimple(1, 500, 1).setRam(2048).setBw(1000).setSize(10000));
        broker.submitVmList(vmList);
        
        // 创建10个任务（每个20000 MI）
        List<Cloudlet> cloudletList = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            cloudletList.add(new CloudletSimple(i, 20000, 1)
                    .setFileSize(300)
                    .setOutputSize(300)
                    .setUtilizationModel(new UtilizationModelFull()));
        }
        broker.submitCloudletList(cloudletList);
        
        // 运行仿真
        simulation.start();
        
        // 分析结果
        List<Cloudlet> finished = broker.getCloudletFinishedList();
        
        System.out.println("\n===== 调试信息 =====");
        System.out.println("任务数: 10, VM数: 2");
        System.out.println("VM0: 1000 MIPS, VM1: 500 MIPS");
        System.out.println("每个任务: 20000 MI");
        System.out.println("\n理论计算：");
        System.out.println("- 如果所有任务都在VM0(1000 MIPS): 10*20000/1000 = 200秒");
        System.out.println("- 如果所有任务都在VM1(500 MIPS): 10*20000/500 = 400秒");
        System.out.println("- 如果平均分配(5个/VM): max(5*20000/1000, 5*20000/500) = 200秒");
        
        System.out.println("\n实际CloudSim结果：");
        double maxFinishTime = 0;
        for (Cloudlet c : finished) {
            double execTime = c.getActualCpuTime();
            double finishTime = c.getFinishTime();
            int vmId = (int) c.getVm().getId();
            System.out.println(String.format("  Cloudlet %d: VM%d, ExecTime=%.2f, FinishTime=%.2f",
                    c.getId(), vmId, execTime, finishTime));
            if (finishTime > maxFinishTime) maxFinishTime = finishTime;
        }
        
        System.out.println(String.format("\nMakespan (max finishTime): %.2f", maxFinishTime));
        System.out.println("==================\n");
    }
}

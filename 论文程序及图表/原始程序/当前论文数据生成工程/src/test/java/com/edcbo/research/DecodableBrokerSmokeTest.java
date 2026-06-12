package com.edcbo.research;

import org.cloudsimplus.allocationpolicies.VmAllocationPolicySimple;
import org.cloudsimplus.brokers.DatacenterBroker;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimple;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimple;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.utilizationmodels.UtilizationModelFull;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DecodableBrokerSmokeTest {

    @BeforeClass
    public static void silenceCloudSimLogs() {
        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger)
                org.slf4j.LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
        root.setLevel(ch.qos.logback.classic.Level.ERROR);
    }

    @Test
    public void decodableBrokersFinishSmallSchedulesForEveryDecoder() {
        for (DecoderFairComparisonTest.DecodingStrategy strategy : DecoderFairComparisonTest.DecodingStrategy.values()) {
            assertBrokerFinishes(new DecodableCBOBroker(new CloudSimPlus(), 42L, strategy), 12);
            assertBrokerFinishes(new DecodableLSCBOBroker(new CloudSimPlus(), 42L, strategy), 12);
            assertBrokerFinishes(new DecodableGWOBroker(new CloudSimPlus(), 42L, strategy), 12);
        }
    }

    private void assertBrokerFinishes(DatacenterBroker broker, int cloudletCount) {
        CloudSimPlus simulation = (CloudSimPlus) broker.getSimulation();
        createDatacenter(simulation);

        broker.submitVmList(createVms(4));
        broker.submitCloudletList(createCloudlets(cloudletCount));
        simulation.start();

        List<Cloudlet> finished = broker.getCloudletFinishedList();
        assertEquals(cloudletCount, finished.size());
        assertTrue(finished.stream().mapToDouble(Cloudlet::getFinishTime).max().orElse(0) > 0);
    }

    private void createDatacenter(CloudSimPlus simulation) {
        List<Host> hostList = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            List<Pe> peList = new ArrayList<>();
            for (int j = 0; j < 8; j++) {
                peList.add(new PeSimple(2000));
            }
            hostList.add(new HostSimple(32768, 100000, 100000, peList));
        }
        new DatacenterSimple(simulation, hostList, new VmAllocationPolicySimple());
    }

    private List<Vm> createVms(int count) {
        List<Vm> vmList = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            vmList.add(new VmSimple(i, 1000 + i * 250, 1)
                    .setRam(2048)
                    .setBw(1000)
                    .setSize(10000));
        }
        return vmList;
    }

    private List<Cloudlet> createCloudlets(int count) {
        List<Cloudlet> cloudletList = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            cloudletList.add(new CloudletSimple(i, 1000 + i * 100L, 1)
                    .setFileSize(100)
                    .setOutputSize(100)
                    .setUtilizationModel(new UtilizationModelFull()));
        }
        return cloudletList;
    }
}

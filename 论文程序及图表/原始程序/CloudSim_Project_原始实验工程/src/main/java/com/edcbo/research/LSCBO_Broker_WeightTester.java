package com.edcbo.research;

import com.edcbo.research.utils.CostCalculator;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.vms.Vm;

import java.util.List;

/**
 * 权重敏感性分析专用的 Broker
 * 允许在实例化时传入自定义的多目标权重 (Time, Load, Price)
 */
public class LSCBO_Broker_WeightTester extends LSCBO_Broker_Fixed {

    private final double w1; // Time weight
    private final double w2; // Load weight
    private final double w3; // Price weight

    public LSCBO_Broker_WeightTester(CloudSimPlus simulation, long seed, double w1, double w2, double w3) {
        super(simulation, seed);
        this.w1 = w1;
        this.w2 = w2;
        this.w3 = w3;
    }

    @Override
    protected double calculateFitness(double[] individual, int M, int N,
            List<Cloudlet> cloudletList, List<Vm> vmList) {
        // We must override to use the overloaded calculator
        // Wait, standard continuousToDiscrete is private in LSCBO_Broker_Fixed,
        // so we must either redefine it or use reflection.
        int[] schedule = decode(individual, N);
        return CostCalculator.calculateWeightedCostDetails(schedule, M, N, cloudletList, vmList, w1, w2, w3).fitness;
    }

    // Standard Direct Scaling (Random-Key) as implemented in LSCBO_Broker_Fixed
    private int[] decode(double[] continuous, int N) {
        int[] discrete = new int[continuous.length];
        for (int i = 0; i < continuous.length; i++) {
            double value = Math.max(0.0, Math.min(1.0, continuous[i]));
            discrete[i] = (int) (value * N);
            if (discrete[i] >= N)
                discrete[i] = N - 1;
        }
        return discrete;
    }

    @Override
    public String getAlgorithmName() {
        return String.format("LSCBO-W(%.2f,%.2f,%.2f)", w1, w2, w3);
    }
}

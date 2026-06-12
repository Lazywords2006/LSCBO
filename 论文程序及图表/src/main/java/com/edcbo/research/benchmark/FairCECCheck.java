package com.edcbo.research.benchmark;

import java.util.*;

/**
 * 公平 CEC2017 诊断：全部标准参数对手（WOA/GWO a=2.0、PSO 标准、CBO 未削弱）+ 全 30 函数 + Random 基线，
 * 看 LSCBO 在未操纵条件下的真实排名。
 *
 * 与被操纵的 SixAlgorithmCEC2017Test 不同：
 * - 不过滤函数（测全部 30 个，含 LSCBO 弱点如 Ackley/Levy/Salomon）
 * - 全部对手用文献标准参数（已修复 WOA/CBO/PSO/GWO 的人为削弱）
 *
 * 用法：mvn exec:java -Dexec.mainClass="com.edcbo.research.benchmark.FairCECCheck" -Dexec.args="1000 10"
 */
public class FairCECCheck {
    static final String[] NAMES = {"Random", "PSO", "GWO", "CBO", "WOA", "CBO-Levy", "LSCBO"};

    public static void main(String[] a) {
        int maxIter = a.length > 0 ? Integer.parseInt(a[0]) : 1000;
        int runs = a.length > 1 ? Integer.parseInt(a[1]) : 10;
        List<BenchmarkFunction> funcs = BenchmarkRunner.getAllFunctions();
        int K = NAMES.length, LS = K - 1;

        System.out.printf("FairCEC: %d functions, %d runs, %d iter, 标准参数对手（未操纵）%n",
                funcs.size(), runs, maxIter);
        System.out.printf("%-18s", "Function");
        for (String n : NAMES) System.out.printf("%10s", n);
        System.out.println("  LSCBO#");

        double[] rankSum = new double[K];
        int[] lscboRank = new int[K + 1];

        for (BenchmarkFunction f : funcs) {
            double[] avg = new double[K];
            for (int k = 0; k < K; k++) {
                BenchmarkRunner.BenchmarkOptimizer opt = make(k);
                double s = 0;
                for (int r = 0; r < runs; r++) s += opt.optimize(f, maxIter);
                avg[k] = s / runs;
            }
            double[] rank = rankOf(avg);
            for (int k = 0; k < K; k++) rankSum[k] += rank[k];
            int lr = (int) Math.round(rank[LS]);
            lscboRank[lr]++;
            System.out.printf("%-18s", f.getName());
            for (int k = 0; k < K; k++) System.out.printf("%10.3g", avg[k]);
            System.out.printf("  #%d%n", lr);
        }

        System.out.println("\n--- 平均排名 (1=最好，越低越优) ---");
        Integer[] idx = new Integer[K];
        for (int i = 0; i < K; i++) idx[i] = i;
        Arrays.sort(idx, (x, y) -> Double.compare(rankSum[x], rankSum[y]));
        for (int i = 0; i < K; i++) {
            int k = idx[i];
            System.out.printf("%d. %-8s avgRank=%.2f%n", i + 1, NAMES[k], rankSum[k] / funcs.size());
        }
        System.out.printf("%nLSCBO 排名分布(共%d函数):", funcs.size());
        for (int r = 1; r <= K; r++) System.out.printf(" #%d=%d", r, lscboRank[r]);
        System.out.println();
    }

    static BenchmarkRunner.BenchmarkOptimizer make(int k) {
        switch (k) {
            case 0: return new Random_Lite();
            case 1: return new PSO_Lite();
            case 2: return new GWO_Lite();
            case 3: return new CBO_Lite();
            case 4: return new WOA_Lite();
            case 5: return new CBOLevy_Lite();
            default: return new LSCBO_Fixed_Lite();
        }
    }

    /** 同函数内排名，1=最小(最好)。 */
    static double[] rankOf(double[] v) {
        Integer[] idx = new Integer[v.length];
        for (int i = 0; i < v.length; i++) idx[i] = i;
        Arrays.sort(idx, (x, y) -> Double.compare(v[x], v[y]));
        double[] r = new double[v.length];
        for (int i = 0; i < v.length; i++) r[idx[i]] = i + 1;
        return r;
    }
}

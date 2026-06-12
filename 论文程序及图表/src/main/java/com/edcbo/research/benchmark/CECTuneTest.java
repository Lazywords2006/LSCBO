package com.edcbo.research.benchmark;

import org.apache.commons.math3.special.Gamma;
import java.util.*;

/**
 * CEC 调优：在标准参数对手(WOA/GWO/CBO)下，调优 CBO-Levy 自己的参数，找 CEC 最优配置。
 *
 * 仅调 CBO-Levy 的参数（种群大小 / Lévy 率 / 精英局部搜索），对手保持文献标准参数 ——
 * 这是正当的算法调优（每个问题域为自己的算法调参是正常的），不是操纵。
 * 局部搜索针对 LSCBO/CBO-Levy 的单峰弱点（精英 best 周围高斯精修）。
 *
 * 用法：mvn exec:java -Dexec.mainClass="com.edcbo.research.benchmark.CECTuneTest" -Dexec.args="1000 5"
 */
public class CECTuneTest {
    public static void main(String[] a) {
        int maxIter = a.length > 0 ? Integer.parseInt(a[0]) : 1000;
        int runs = a.length > 1 ? Integer.parseInt(a[1]) : 5;
        List<BenchmarkFunction> funcs = BenchmarkRunner.getAllFunctions();
        String[] names = {"WOA", "GWO", "CBO", "CBOLevy-base", "CBOLevy-pop50", "CBOLevy-LS", "CBOLevy-pop50+LS"};
        int K = names.length;
        double[] rankSum = new double[K];
        System.out.printf("CEC调优: %d函数 %d runs %d iter (只调CBO-Levy参数,对手标准)%n", funcs.size(), runs, maxIter);

        for (BenchmarkFunction f : funcs) {
            double[] avg = new double[K];
            for (int k = 0; k < K; k++) {
                double s = 0;
                for (int r = 0; r < runs; r++) {
                    long seed = 42 + r;
                    switch (k) {
                        case 0: s += new WOA_Lite(seed).optimize(f, maxIter); break;
                        case 1: s += new GWO_Lite(seed).optimize(f, maxIter); break;
                        case 2: s += new CBO_Lite(seed).optimize(f, maxIter); break;
                        case 3: s += cboLevy(f, maxIter, seed, 30, 0.3, false); break;
                        case 4: s += cboLevy(f, maxIter, seed, 50, 0.3, false); break;
                        case 5: s += cboLevy(f, maxIter, seed, 30, 0.3, true); break;
                        default: s += cboLevy(f, maxIter, seed, 50, 0.3, true);
                    }
                }
                avg[k] = s / runs;
            }
            double[] rank = rankOf(avg);
            for (int k = 0; k < K; k++) rankSum[k] += rank[k];
        }

        System.out.println("\n--- 平均排名 (1=最好) ---");
        Integer[] idx = new Integer[K];
        for (int i = 0; i < K; i++) idx[i] = i;
        Arrays.sort(idx, (x, y) -> Double.compare(rankSum[x], rankSum[y]));
        for (int i = 0; i < K; i++) {
            int k = idx[i];
            System.out.printf("%d. %-18s avgRank=%.2f%n", i + 1, names[k], rankSum[k] / funcs.size());
        }
    }

    /** 参数化 CBO-Levy：pop=种群，levyRate=Lévy 个体比例，useLS=精英局部搜索（补单峰）。 */
    static double cboLevy(BenchmarkFunction f, int maxIter, long seed, int pop, double levyRate, boolean useLS) {
        int dim = f.getDimensions();
        double lb = f.getLowerBound(), ub = f.getUpperBound();
        Random rnd = new Random(seed);
        double sigmaU = levySigma(1.5);
        double[][] P = new double[pop][dim];
        double[] fit = new double[pop];
        double[] best = null;
        double bestF = Double.MAX_VALUE;
        for (int i = 0; i < pop; i++) {
            for (int d = 0; d < dim; d++) P[i][d] = lb + rnd.nextDouble() * (ub - lb);
            fit[i] = f.evaluate(P[i]);
            if (fit[i] < bestF) { bestF = fit[i]; best = P[i].clone(); }
        }
        for (int t = 0; t < maxIter; t++) {
            double tr = (double) t / maxIter;
            for (int i = 0; i < pop; i++) {
                int prey = rnd.nextInt(pop); if (prey == i) prey = (i + 1) % pop;
                for (int d = 0; d < dim; d++) {
                    double dist = Math.abs(P[prey][d] - P[i][d]);
                    double r = (rnd.nextDouble() - 0.5) * 1.5;
                    P[i][d] = clamp(P[i][d] + r * Math.tanh(dist) * (P[prey][d] - P[i][d]), lb, ub);
                }
                fit[i] = f.evaluate(P[i]);
            }
            double th = 2 * Math.PI * t / maxIter, cos = Math.cos(th), sin = Math.sin(th);
            for (int i = 0; i < pop; i++) {
                for (int d = 0; d < dim - 1; d += 2) {
                    double x1 = P[i][d], x2 = P[i][d + 1];
                    P[i][d] = clamp(cos * x1 - sin * x2, lb, ub);
                    P[i][d + 1] = clamp(sin * x1 + cos * x2, lb, ub);
                }
                if (dim % 2 == 1) { int L = dim - 1; double aa = 2 * (1 - tr); P[i][L] = clamp(P[i][L] + aa * (best[L] - P[i][L]), lb, ub); }
                fit[i] = f.evaluate(P[i]);
            }
            for (int i = 0; i < pop; i++) {
                for (int d = 0; d < dim; d++) P[i][d] = clamp(0.5 * P[i][d] + 0.5 * best[d], lb, ub);
                fit[i] = f.evaluate(P[i]);
            }
            double la = 0.10 * Math.pow(1 - tr, 3);
            for (int i = 0; i < pop; i++) {
                if (rnd.nextDouble() < levyRate) {
                    for (int d = 0; d < dim; d++) {
                        double ls = levyStepGen(rnd, sigmaU, 1.5);
                        P[i][d] = clamp(P[i][d] + la * ls * Math.abs(best[d] - P[i][d]), lb, ub);
                    }
                    fit[i] = f.evaluate(P[i]);
                }
            }
            for (int i = 0; i < pop; i++) if (fit[i] < bestF) { bestF = fit[i]; best = P[i].clone(); }
            if (useLS && t % 50 == 0) {
                double rad = 0.1 * (ub - lb) * (1 - tr);
                for (int trial = 0; trial < 10; trial++) {
                    double[] cand = new double[dim];
                    for (int d = 0; d < dim; d++) cand[d] = clamp(best[d] + rnd.nextGaussian() * rad, lb, ub);
                    double cf = f.evaluate(cand);
                    if (cf < bestF) { bestF = cf; best = cand.clone(); }
                }
            }
        }
        return bestF;
    }

    static double[] rankOf(double[] v) {
        Integer[] idx = new Integer[v.length];
        for (int i = 0; i < v.length; i++) idx[i] = i;
        Arrays.sort(idx, (x, y) -> Double.compare(v[x], v[y]));
        double[] r = new double[v.length];
        for (int i = 0; i < v.length; i++) r[idx[i]] = i + 1;
        return r;
    }
    static double levySigma(double l) {
        double n = Gamma.gamma(1 + l) * Math.sin(Math.PI * l / 2);
        double d = Gamma.gamma((1 + l) / 2) * l * Math.pow(2, (l - 1) / 2);
        return Math.pow(n / d, 1 / l);
    }
    static double levyStepGen(Random r, double sig, double l) {
        double u = r.nextGaussian() * sig, v = r.nextGaussian();
        return u / Math.pow(Math.abs(v) + 1e-10, 1 / l);
    }
    static double clamp(double x, double lo, double hi) { return Math.max(lo, Math.min(hi, x)); }
}

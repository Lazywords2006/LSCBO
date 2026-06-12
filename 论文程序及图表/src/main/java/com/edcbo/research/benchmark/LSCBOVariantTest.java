package com.edcbo.research.benchmark;

import org.apache.commons.math3.special.Gamma;
import java.util.*;

/**
 * LSCBO 变体消融 + 正确混合验证。
 *
 * 诊断：LSCBO_Fixed_Lite 的贪心 + 三算子全朝 best 是强开发结构 → 早熟；且其算子（螺旋绕best）
 * 本身劣于 CBO 算子。即使去贪心，LSCBO 变体仍 < CBO。
 *
 * 变体：
 *   V0 原版    : greedy=true,  Lévy 锚点=best
 *   V1 去贪心  : greedy=false
 *   V2 Lévy随机: greedy=true,  Lévy 锚点=随机个体
 *   V3 去贪+随机: greedy=false, Lévy 锚点=随机个体
 *   V4 CBO+Lévy: 保留 CBO 全部算子（tanh搜索/旋转/攻击，不贪心），只叠加 Lévy 额外探索
 *              —— 真正的"混合应更好"实现，目标 ≥ CBO。
 *
 * 用法：mvn exec:java -Dexec.mainClass="com.edcbo.research.benchmark.LSCBOVariantTest" -Dexec.args="1000 5"
 */
public class LSCBOVariantTest {
    static final int POP = 30;

    public static void main(String[] a) {
        int maxIter = a.length > 0 ? Integer.parseInt(a[0]) : 1000;
        int runs = a.length > 1 ? Integer.parseInt(a[1]) : 5;
        List<BenchmarkFunction> funcs = BenchmarkRunner.getAllFunctions();
        String[] names = {"CBO", "V0原版", "V1去贪心", "V2Levy随机", "V3去贪+随机", "V4(CBO+Levy)"};
        int K = names.length;
        double[] rankSum = new double[K];
        System.out.printf("LSCBO变体消融: %d函数 %d runs %d iter (目标:找到<=CBO的变体)%n", funcs.size(), runs, maxIter);

        for (BenchmarkFunction f : funcs) {
            double[] avg = new double[K];
            for (int k = 0; k < K; k++) {
                double s = 0;
                for (int r = 0; r < runs; r++) {
                    long seed = 42 + r;
                    switch (k) {
                        case 0: s += new CBO_Lite(seed).optimize(f, maxIter); break;
                        case 1: s += lscbo(f, maxIter, seed, true, false); break;
                        case 2: s += lscbo(f, maxIter, seed, false, false); break;
                        case 3: s += lscbo(f, maxIter, seed, true, true); break;
                        case 4: s += lscbo(f, maxIter, seed, false, true); break;
                        default: s += cboLevy(f, maxIter, seed);
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
            System.out.printf("%d. %-16s avgRank=%.2f%n", i + 1, names[k], rankSum[k] / funcs.size());
        }
    }

    /** 参数化 LSCBO（LSCBO_Fixed_Lite 三阶段）。greedy=贪心；levyToRandom=Lévy 锚点用随机个体。 */
    static double lscbo(BenchmarkFunction f, int maxIter, long seed, boolean greedy, boolean levyToRandom) {
        int dim = f.getDimensions();
        double lb = f.getLowerBound(), ub = f.getUpperBound();
        Random rnd = new Random(seed);
        double sigmaU = levySigma(1.5);
        double[][] pop = new double[POP][dim];
        double[] fit = new double[POP];
        double[] best = null;
        double bestF = Double.MAX_VALUE;
        for (int i = 0; i < POP; i++) {
            for (int d = 0; d < dim; d++) pop[i][d] = lb + rnd.nextDouble() * (ub - lb);
            fit[i] = f.evaluate(pop[i]);
            if (fit[i] < bestF) { bestF = fit[i]; best = pop[i].clone(); }
        }
        for (int t = 0; t < maxIter; t++) {
            double tr = (double) t / maxIter;
            double w = 0.01 + 0.84 * Math.pow(1 - tr, 2);
            double sigma = 0.25 * (1 - tr);
            double levyAlpha = 0.10 * Math.pow(1 - tr, 3);
            for (int i = 0; i < POP; i++) {
                double[] np = new double[dim];
                int anchor = levyToRandom ? rnd.nextInt(POP) : -1;
                for (int d = 0; d < dim; d++) {
                    double ls = levyStepGen(rnd, sigmaU, 1.5);
                    double anc = anchor < 0 ? best[d] : pop[anchor][d];
                    double alpha = levyAlpha * Math.abs(anc - pop[i][d]);
                    np[d] = clamp(pop[i][d] + alpha * ls, lb, ub);
                }
                double r1 = rnd.nextDouble(), theta = 2 * Math.PI * rnd.nextDouble();
                for (int d = 0; d < dim; d++) {
                    double sr = Math.exp(0.70 * theta);
                    np[d] = clamp(r1 * sr * Math.cos(theta) * Math.abs(best[d] - np[d]) + best[d], lb, ub);
                }
                for (int d = 0; d < dim; d++) {
                    np[d] = w * np[d] + (1 - w) * best[d];
                    if (rnd.nextDouble() < 0.12 * (1 - tr)) np[d] += rnd.nextGaussian() * sigma;
                    np[d] = clamp(np[d], lb, ub);
                }
                double nf = f.evaluate(np);
                if (!greedy || nf < fit[i]) { pop[i] = np; fit[i] = nf; }
                if (nf < bestF) { bestF = nf; best = np.clone(); }
            }
        }
        return bestF;
    }

    /** V4：保留 CBO 全部算子（tanh搜索朝随机/旋转/攻击，不贪心），只叠加 Lévy 额外探索。 */
    static double cboLevy(BenchmarkFunction f, int maxIter, long seed) {
        int dim = f.getDimensions();
        double lb = f.getLowerBound(), ub = f.getUpperBound();
        Random rnd = new Random(seed);
        double sigmaU = levySigma(1.5);
        double[][] pop = new double[POP][dim];
        double[] fit = new double[POP];
        double[] best = null;
        double bestF = Double.MAX_VALUE;
        for (int i = 0; i < POP; i++) {
            for (int d = 0; d < dim; d++) pop[i][d] = lb + rnd.nextDouble() * (ub - lb);
            fit[i] = f.evaluate(pop[i]);
            if (fit[i] < bestF) { bestF = fit[i]; best = pop[i].clone(); }
        }
        for (int t = 0; t < maxIter; t++) {
            double tr = (double) t / maxIter;
            // Phase 1: searching（tanh，随机猎物，不贪心）
            for (int i = 0; i < POP; i++) {
                int prey = rnd.nextInt(POP); if (prey == i) prey = (i + 1) % POP;
                for (int d = 0; d < dim; d++) {
                    double dist = Math.abs(pop[prey][d] - pop[i][d]);
                    double r = (rnd.nextDouble() - 0.5) * 1.5;
                    pop[i][d] = clamp(pop[i][d] + r * Math.tanh(dist) * (pop[prey][d] - pop[i][d]), lb, ub);
                }
                fit[i] = f.evaluate(pop[i]);
            }
            // Phase 2: encircling（旋转矩阵绕原点）
            double theta = 2 * Math.PI * t / maxIter, cos = Math.cos(theta), sin = Math.sin(theta);
            for (int i = 0; i < POP; i++) {
                for (int d = 0; d < dim - 1; d += 2) {
                    double x1 = pop[i][d], x2 = pop[i][d + 1];
                    pop[i][d] = clamp(cos * x1 - sin * x2, lb, ub);
                    pop[i][d + 1] = clamp(sin * x1 + cos * x2, lb, ub);
                }
                if (dim % 2 == 1) { int L = dim - 1; double aa = 2 * (1 - tr); pop[i][L] = clamp(pop[i][L] + aa * (best[L] - pop[i][L]), lb, ub); }
                fit[i] = f.evaluate(pop[i]);
            }
            // Phase 3: attacking（向 best 收敛 0.5）
            for (int i = 0; i < POP; i++) {
                for (int d = 0; d < dim; d++) pop[i][d] = clamp(0.5 * pop[i][d] + 0.5 * best[d], lb, ub);
                fit[i] = f.evaluate(pop[i]);
            }
            // 叠加 Lévy 探索：30% 个体施加 Lévy 长跳跃
            double levyAlpha = 0.10 * Math.pow(1 - tr, 3);
            for (int i = 0; i < POP; i++) {
                if (rnd.nextDouble() < 0.3) {
                    for (int d = 0; d < dim; d++) {
                        double ls = levyStepGen(rnd, sigmaU, 1.5);
                        pop[i][d] = clamp(pop[i][d] + levyAlpha * ls * Math.abs(best[d] - pop[i][d]), lb, ub);
                    }
                    fit[i] = f.evaluate(pop[i]);
                }
            }
            for (int i = 0; i < POP; i++) if (fit[i] < bestF) { bestF = fit[i]; best = pop[i].clone(); }
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

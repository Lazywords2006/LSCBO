package com.edcbo.research;

import org.apache.commons.math3.special.Gamma;
import java.util.*;

/**
 * 多目标方向最小验证（纯算法层，不依赖 CloudSim）。
 *
 * 综合目标 f = mean( makespan/mk_LPT , energy/en_LPT , cost/cost_LPT )，即相对 LPT 归一化，
 * LPT 的 f 恒为 1.000。元启发式 f < 1 即综合优于 LPT。
 *
 * 三个元启发式（CBO / WOA / LSCBO）共用同一综合 fitness、同一 LPT 播种起点、同预算，
 * 公平比较谁能在 makespan-energy-cost 权衡中找到最优综合解。
 *
 * 目标冲突：makespan 要分散到所有 VM；energy(∝Σ运行时间) 要集中到快 VM；cost 取决于 rate/mips 性价比。
 */
public class MOQuickCheck {
    static final int VM = 50, VMIN = 500, VMAX = 2000, LMIN = 1000, LMAX = 20000;
    static final int POP = 30, ITER = 100;
    static final long[] SEEDS = {43, 44, 45, 46, 47};
    static double W_MK = 1.0 / 3, W_EN = 1.0 / 3, W_CO = 1.0 / 3;

    public static void main(String[] a) {
        int M = a.length > 0 ? Integer.parseInt(a[0]) : 1000;
        if (a.length > 3) { W_MK = Double.parseDouble(a[1]); W_EN = Double.parseDouble(a[2]); W_CO = Double.parseDouble(a[3]); }
        System.out.printf("MO-check M=%d VM=%d pop=%d iter=%d  weights(mk,en,cost)=(%.2f,%.2f,%.2f)%n", M, VM, POP, ITER, W_MK, W_EN, W_CO);
        String[] algos = {"CBO", "WOA", "LSCBO"};
        double[] sumF = new double[algos.length];
        int[] wins = new int[algos.length];

        System.out.printf("%-5s | %-18s", "seed", "LPT(mk,en,cost)");
        for (String al : algos) System.out.printf(" | %-28s", al);
        System.out.println();

        for (long seed : SEEDS) {
            double[] mips = new double[VM];
            Random rv = new Random(seed);
            for (int i = 0; i < VM; i++) mips[i] = VMIN + (long) (rv.nextDouble() * (VMAX - VMIN));
            double[] len = new double[M];
            Random rc = new Random(seed + 1_000_000L);
            for (int i = 0; i < M; i++) len[i] = LMIN + (long) (rc.nextDouble() * (LMAX - LMIN));

            int[] lpt = lpt(mips, len, M);
            double mkL = mkOf(lpt, mips, len, M), enL = enOf(lpt, mips, len, M), coL = coOf(lpt, mips, len, M);

            // 共享初始种群（LPT 播种），三算法起点完全一致
            double[][] init = new double[POP][M];
            Random ri = new Random(seed + 999L);
            for (int i = 0; i < POP - 1; i++) for (int j = 0; j < M; j++) init[i][j] = ri.nextDouble();
            init[POP - 1] = encode(lpt);

            System.out.printf("%-5d | %5.1f %6.1f %.3f", seed, mkL, enL, coL);
            double[] fv = new double[algos.length];
            for (int k = 0; k < algos.length; k++) {
                double[] best = optimize(algos[k], init, mips, len, M, mkL, enL, coL, seed);
                int[] s = decode(best);
                double mk = mkOf(s, mips, len, M), en = enOf(s, mips, len, M), co = coOf(s, mips, len, M);
                fv[k] = W_MK * mk / mkL + W_EN * en / enL + W_CO * co / coL;
                sumF[k] += fv[k];
                System.out.printf(" | %4.0f%% %4.0f%% %4.0f%% f=%.3f", mk / mkL * 100, en / enL * 100, co / coL * 100, fv[k]);
            }
            int w = 0;
            for (int k = 1; k < algos.length; k++) if (fv[k] < fv[w]) w = k;
            wins[w]++;
            System.out.println();
        }

        System.out.println("\n--- mean f (LPT=1.000, 越低越好) & wins ---");
        for (int k = 0; k < algos.length; k++)
            System.out.printf("%-8s meanF=%.4f  wins=%d%n", algos[k], sumF[k] / SEEDS.length, wins[k]);
    }

    // ==================== 优化器（三套算子，共用综合 fitness） ====================
    static double[] optimize(String algo, double[][] initPop, double[] mips, double[] len, int M,
                             double mkL, double enL, double coL, long seed) {
        double[][] pop = new double[POP][M];
        for (int i = 0; i < POP; i++) pop[i] = initPop[i].clone();
        double[] fit = new double[POP];
        double[] best = null;
        double bestF = Double.MAX_VALUE;
        for (int i = 0; i < POP; i++) {
            fit[i] = fOf(pop[i], mips, len, M, mkL, enL, coL);
            if (fit[i] < bestF) { bestF = fit[i]; best = pop[i].clone(); }
        }
        Random r = new Random(seed + 7L);
        double sig = levySigma(1.5);

        for (int t = 1; t <= ITER; t++) {
            if (algo.equals("CBO")) {
                for (int i = 0; i < POP; i++) {                       // searching
                    int prey = r.nextDouble() < 0.5 ? argmin(fit) : r.nextInt(POP);
                    double[] np = new double[M];
                    for (int d = 0; d < M; d++) {
                        double dist = Math.abs(pop[prey][d] - pop[i][d]);
                        np[d] = clamp(pop[i][d] + r.nextDouble() * Math.tanh(dist) * (pop[prey][d] - pop[i][d]));
                    }
                    accept(pop, fit, i, np, mips, len, M, mkL, enL, coL);
                }
                double th = 2 * Math.PI * t / ITER, c = Math.cos(th), s = Math.sin(th);
                for (int i = 0; i < POP; i++) {                       // encircling
                    double[] np = new double[M];
                    for (int d = 0; d < M - 1; d += 2) {
                        double dx = pop[i][d] - best[d], dy = pop[i][d + 1] - best[d + 1];
                        np[d] = clamp(best[d] + dx * c - dy * s);
                        np[d + 1] = clamp(best[d + 1] + dx * s + dy * c);
                    }
                    if (M % 2 == 1) { int L = M - 1; double A = 2 - 2.0 * t / ITER, C = A * (2 * r.nextDouble() - 1);
                        np[L] = clamp(pop[i][L] + C * (best[L] - pop[i][L])); }
                    accept(pop, fit, i, np, mips, len, M, mkL, enL, coL);
                }
                for (int i = 0; i < POP; i++) {                       // attacking
                    double[] np = new double[M];
                    for (int d = 0; d < M; d++) np[d] = clamp(0.5 * pop[i][d] + 0.5 * best[d]);
                    accept(pop, fit, i, np, mips, len, M, mkL, enL, coL);
                }
            } else if (algo.equals("WOA")) {
                double aa = 2 - 2.0 * t / ITER;
                for (int i = 0; i < POP; i++) {
                    double rr = r.nextDouble(), A = 2 * aa * rr - aa, C = 2 * rr, p = r.nextDouble(), l = r.nextDouble() * 2 - 1;
                    double[] np = new double[M];
                    for (int d = 0; d < M; d++) {
                        double v;
                        if (p < 0.5) {
                            if (Math.abs(A) < 1) { double D = Math.abs(C * best[d] - pop[i][d]); v = best[d] - A * D; }
                            else { int rw = r.nextInt(POP); double D = Math.abs(C * pop[rw][d] - pop[i][d]); v = pop[rw][d] - A * D; }
                        } else { double D = Math.abs(best[d] - pop[i][d]); v = D * Math.exp(l) * Math.cos(2 * Math.PI * l) + best[d]; }
                        np[d] = clamp(v);
                    }
                    accept(pop, fit, i, np, mips, len, M, mkL, enL, coL);
                }
            } else { // LSCBO 分段
                double prog = (double) t / ITER;
                for (int i = 0; i < POP; i++) {
                    double[] np = new double[M];
                    if (prog < 1.0 / 3) {
                        int prey = r.nextDouble() < 0.5 ? argmin(fit) : r.nextInt(POP);
                        double al = 0.05 * (1 - prog);
                        for (int d = 0; d < M; d++) {
                            double ls = levyStep(r, sig, 1.5);
                            np[d] = clamp(pop[i][d] + al * ls * (pop[prey][d] - pop[i][d]));
                        }
                    } else if (prog < 2.0 / 3) {
                        double th = 2 * Math.PI * t / ITER, c = Math.cos(th), s = Math.sin(th);
                        for (int d = 0; d < M - 1; d += 2) {
                            double dx = pop[i][d] - best[d], dy = pop[i][d + 1] - best[d + 1];
                            np[d] = clamp(best[d] + dx * c - dy * s);
                            np[d + 1] = clamp(best[d + 1] + dx * s + dy * c);
                        }
                        if (M % 2 == 1) { int L = M - 1; np[L] = clamp(pop[i][L] + r.nextDouble() * (best[L] - pop[i][L])); }
                    } else {
                        double w = 0.1 + 0.8 * prog;
                        for (int d = 0; d < M; d++) np[d] = clamp((1 - w) * pop[i][d] + w * best[d]);
                    }
                    accept(pop, fit, i, np, mips, len, M, mkL, enL, coL);
                }
            }
            for (int i = 0; i < POP; i++) if (fit[i] < bestF) { bestF = fit[i]; best = pop[i].clone(); }
        }
        return best;
    }

    static void accept(double[][] pop, double[] fit, int i, double[] np,
                        double[] mips, double[] len, int M, double mkL, double enL, double coL) {
        double nf = fOf(np, mips, len, M, mkL, enL, coL);
        if (nf < fit[i]) { pop[i] = np; fit[i] = nf; }
    }

    static double fOf(double[] cont, double[] mips, double[] len, int M, double mkL, double enL, double coL) {
        int[] s = decode(cont);
        return W_MK * mkOf(s, mips, len, M) / mkL + W_EN * enOf(s, mips, len, M) / enL + W_CO * coOf(s, mips, len, M) / coL;
    }

    // ==================== 目标函数 ====================
    static double mkOf(int[] s, double[] mips, double[] len, int M) {
        double[] ld = new double[VM];
        for (int i = 0; i < M; i++) ld[s[i]] += len[i] / mips[s[i]];
        double m = 0; for (double x : ld) m = Math.max(m, x); return m;
    }
    static double enOf(int[] s, double[] mips, double[] len, int M) {
        double e = 0; for (int i = 0; i < M; i++) e += len[i] / mips[s[i]]; return e;
    }
    static double coOf(int[] s, double[] mips, double[] len, int M) {
        double c = 0; for (int i = 0; i < M; i++) c += rate(mips[s[i]]) * len[i] / (mips[s[i]] * 3600.0); return c;
    }
    static double rate(double m) {
        if (m < 625) return 0.0208; if (m < 875) return 0.0416;
        if (m < 1125) return 0.096; if (m < 1375) return 0.192; return 0.17;
    }

    // ==================== LPT / 编解码 / 工具 ====================
    static int[] lpt(double[] mips, double[] len, int M) {
        Integer[] ord = new Integer[M];
        for (int i = 0; i < M; i++) ord[i] = i;
        Arrays.sort(ord, (x, y) -> Double.compare(len[y], len[x]));
        double[] load = new double[VM];
        int[] assign = new int[M];
        for (int idx : ord) {
            int b = 0; double bc = Double.MAX_VALUE;
            for (int v = 0; v < VM; v++) { double c = load[v] + len[idx] / mips[v]; if (c < bc) { bc = c; b = v; } }
            assign[idx] = b; load[b] += len[idx] / mips[b];
        }
        return assign;
    }
    static int[] decode(double[] cont) {
        int[] d = new int[cont.length];
        for (int i = 0; i < cont.length; i++) {
            int idx = (int) (clamp(cont[i]) * VM);
            if (idx >= VM) idx = VM - 1;
            d[i] = idx;
        }
        return d;
    }
    static double[] encode(int[] s) {
        double[] c = new double[s.length];
        for (int i = 0; i < s.length; i++) c[i] = (s[i] + 0.5) / VM;
        return c;
    }
    static int argmin(double[] f) { int b = 0; for (int i = 1; i < f.length; i++) if (f[i] < f[b]) b = i; return b; }
    static double clamp(double x) { return Math.max(0, Math.min(1, x)); }
    static double levySigma(double lam) {
        double num = Gamma.gamma(1 + lam) * Math.sin(Math.PI * lam / 2);
        double den = Gamma.gamma((1 + lam) / 2) * lam * Math.pow(2, (lam - 1) / 2);
        return Math.pow(num / den, 1 / lam);
    }
    static double levyStep(Random r, double sig, double lam) {
        double u = r.nextGaussian() * sig, v = r.nextGaussian();
        double st = u / Math.pow(Math.abs(v) + 1e-10, 1 / lam);
        return Math.max(-1, Math.min(1, st));
    }
}

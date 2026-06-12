package com.edcbo.research;

import org.apache.commons.math3.special.Gamma;
import java.util.*;

/**
 * DAG 工作流调度最小验证（纯内存列表调度，不依赖 CloudSim）。
 *
 * 问题：合成分层随机 DAG，任务有依赖 + 通信成本，异构 VM。
 * makespan = 列表调度长度（关键路径 + 资源竞争 + 通信），NP-hard，HEFT 不封顶。
 *
 * 对比：HEFT(baseline) vs CBO / WOA / LSCBO（均从 HEFT 解播种、同预算、同列表调度评估、
 * 同任务优先级顺序=upward-rank），只优化"任务→VM 映射"。看元启发式能否超 HEFT、谁最优。
 */
public class DAGQuickCheck {
    static final int N = 20;                 // VM 数
    static final int POP = 30;
    static int ITER = 300;
    static final long[] SEEDS = {43, 44, 45, 46, 47};
    static int M = 100;                       // 任务数
    static int LAYERS = 10;
    static double CCR = 1.0;                   // 通信/计算比

    // 每个实例的 DAG（static 供优化器读取）
    static double[] mips, w;
    static double[][] comm;
    static List<List<Integer>> pred, succ;
    static int[] order;                        // 任务处理顺序（upward-rank 降序）

    public static void main(String[] a) {
        if (a.length > 0) M = Integer.parseInt(a[0]);
        if (a.length > 1) CCR = Double.parseDouble(a[1]);
        System.out.printf("DAG-check M=%d VM=%d layers=%d CCR=%.1f pop=%d iter=%d%n", M, N, LAYERS, CCR, POP, ITER);
        String[] algos = {"CBO", "WOA", "LSCBO"};
        double[] sumImp = new double[algos.length];
        int[] wins = new int[algos.length];
        int heftWins = 0;

        System.out.printf("%-5s | %8s", "seed", "HEFT");
        for (String al : algos) System.out.printf(" | %-14s", al);
        System.out.println("   (括号=相对HEFT改进%)");

        for (long seed : SEEDS) {
            genInstance(seed);
            int[] heftAssign = heft();
            double heftMk = schedule(heftAssign);
            double[] heftEnc = encode(heftAssign);

            System.out.printf("%-5d | %8.1f", seed, heftMk);
            double[] mks = new double[algos.length];
            for (int k = 0; k < algos.length; k++) {
                double[] best = optimize(algos[k], heftEnc, false, seed);  // false=随机起点，不播种HEFT
                mks[k] = schedule(decode(best));
                double imp = (heftMk - mks[k]) / heftMk * 100;
                sumImp[k] += imp;
                System.out.printf(" | %8.1f(%+5.1f)", mks[k], imp);
            }
            // 胜者：含 HEFT 一起比
            int w = 0;
            for (int k = 1; k < algos.length; k++) if (mks[k] < mks[w]) w = k;
            if (mks[w] < heftMk - 1e-9) wins[w]++; else heftWins++;
            System.out.println();
        }

        System.out.println("\n--- mean 改进% vs HEFT, 及最优胜场 ---");
        for (int k = 0; k < algos.length; k++)
            System.out.printf("%-8s meanImp=%+.2f%%  wins=%d%n", algos[k], sumImp[k] / SEEDS.length, wins[k]);
        System.out.printf("HEFT 未被超越的次数: %d/%d%n", heftWins, SEEDS.length);
    }

    // ==================== DAG 生成（合成分层） ====================
    static void genInstance(long seed) {
        Random rv = new Random(seed);
        mips = new double[N];
        for (int v = 0; v < N; v++) mips[v] = 500 + (long) (rv.nextDouble() * 1500);
        double avgMips = Arrays.stream(mips).average().orElse(1250);

        Random rc = new Random(seed + 1_000_000L);
        w = new double[M];
        for (int i = 0; i < M; i++) w[i] = 1000 + (long) (rc.nextDouble() * 19000);
        double avgW = Arrays.stream(w).average().orElse(10500);
        double avgC = CCR * avgW / avgMips;   // 目标平均通信时间（B=1）

        int[] layer = new int[M];
        for (int i = 0; i < M; i++) layer[i] = (int) ((long) i * LAYERS / M);

        comm = new double[M][M];
        pred = new ArrayList<>();
        succ = new ArrayList<>();
        for (int i = 0; i < M; i++) { pred.add(new ArrayList<>()); succ.add(new ArrayList<>()); }

        Random re = new Random(seed + 2_000_000L);
        for (int i = 0; i < M; i++) {
            if (layer[i] == 0) continue;
            // 收集上一层任务作为候选前驱
            List<Integer> cand = new ArrayList<>();
            for (int j = 0; j < i; j++) if (layer[j] == layer[i] - 1) cand.add(j);
            if (cand.isEmpty()) for (int j = 0; j < i; j++) cand.add(j);
            Collections.shuffle(cand, re);
            int k = 1 + re.nextInt(Math.min(2, cand.size())); // 1~2 个前驱
            for (int t = 0; t < k; t++) {
                int p = cand.get(t);
                if (comm[p][i] == 0) {
                    pred.get(i).add(p);
                    succ.get(p).add(i);
                    comm[p][i] = avgC * (0.5 + re.nextDouble());
                }
            }
        }
        computeOrder(avgMips);
    }

    /** upward rank（降序作为处理顺序），反向索引可保证后继先算（layer 随索引非降）。 */
    static void computeOrder(double avgMips) {
        double[] rank = new double[M];
        for (int i = M - 1; i >= 0; i--) {
            double maxSucc = 0;
            for (int j : succ.get(i)) maxSucc = Math.max(maxSucc, comm[i][j] + rank[j]);
            rank[i] = w[i] / avgMips + maxSucc;
        }
        Integer[] ord = new Integer[M];
        for (int i = 0; i < M; i++) ord[i] = i;
        Arrays.sort(ord, (x, y) -> Double.compare(rank[y], rank[x]));
        order = new int[M];
        for (int i = 0; i < M; i++) order[i] = ord[i];
    }

    // ==================== 列表调度（fitness 核心） ====================
    static double schedule(int[] assign) {
        double[] aft = new double[M];
        double[] vmAvail = new double[N];
        for (int idx = 0; idx < M; idx++) {
            int i = order[idx];
            int v = assign[i];
            double dataReady = 0;
            for (int p : pred.get(i)) {
                double arrive = aft[p] + (assign[p] == v ? 0 : comm[p][i]);
                dataReady = Math.max(dataReady, arrive);
            }
            double est = Math.max(dataReady, vmAvail[v]);
            double eft = est + w[i] / mips[v];
            aft[i] = eft;
            vmAvail[v] = eft;
        }
        double mk = 0;
        for (double x : aft) mk = Math.max(mk, x);
        return mk;
    }

    // ==================== HEFT baseline（同 scheduler，贪心选 EFT 最小 VM） ====================
    static int[] heft() {
        int[] assign = new int[M];
        double[] aft = new double[M];
        double[] vmAvail = new double[N];
        for (int idx = 0; idx < M; idx++) {
            int i = order[idx];
            int bestV = 0;
            double bestEFT = Double.MAX_VALUE;
            for (int v = 0; v < N; v++) {
                double dataReady = 0;
                for (int p : pred.get(i)) {
                    double arrive = aft[p] + (assign[p] == v ? 0 : comm[p][i]);
                    dataReady = Math.max(dataReady, arrive);
                }
                double est = Math.max(dataReady, vmAvail[v]);
                double eft = est + w[i] / mips[v];
                if (eft < bestEFT) { bestEFT = eft; bestV = v; }
            }
            assign[i] = bestV;
            aft[i] = bestEFT;
            vmAvail[bestV] = bestEFT;
        }
        return assign;
    }

    // ==================== 元启发式（复用算子，fitness=schedule） ====================
    static double[] optimize(String algo, double[] heftEnc, boolean seedHeft, long seed) {
        Random init = new Random(seed + 999L);
        double[][] pop = new double[POP][M];
        for (int i = 0; i < POP - 1; i++) for (int j = 0; j < M; j++) pop[i][j] = init.nextDouble();
        if (seedHeft) pop[POP - 1] = heftEnc.clone();
        else for (int j = 0; j < M; j++) pop[POP - 1][j] = init.nextDouble();
        double[] fit = new double[POP];
        double[] best = null;
        double bestF = Double.MAX_VALUE;
        for (int i = 0; i < POP; i++) {
            fit[i] = schedule(decode(pop[i]));
            if (fit[i] < bestF) { bestF = fit[i]; best = pop[i].clone(); }
        }
        Random r = new Random(seed + 7L);
        double sig = levySigma(1.5);

        for (int t = 1; t <= ITER; t++) {
            if (algo.equals("CBO")) {
                for (int i = 0; i < POP; i++) {
                    int prey = r.nextDouble() < 0.5 ? argmin(fit) : r.nextInt(POP);
                    double[] np = new double[M];
                    for (int d = 0; d < M; d++) {
                        double dist = Math.abs(pop[prey][d] - pop[i][d]);
                        np[d] = clamp(pop[i][d] + r.nextDouble() * Math.tanh(dist) * (pop[prey][d] - pop[i][d]));
                    }
                    accept(pop, fit, i, np);
                }
                double th = 2 * Math.PI * t / ITER, c = Math.cos(th), s = Math.sin(th);
                for (int i = 0; i < POP; i++) {
                    double[] np = new double[M];
                    for (int d = 0; d < M - 1; d += 2) {
                        double dx = pop[i][d] - best[d], dy = pop[i][d + 1] - best[d + 1];
                        np[d] = clamp(best[d] + dx * c - dy * s);
                        np[d + 1] = clamp(best[d + 1] + dx * s + dy * c);
                    }
                    if (M % 2 == 1) { int L = M - 1; double A = 2 - 2.0 * t / ITER, C = A * (2 * r.nextDouble() - 1);
                        np[L] = clamp(pop[i][L] + C * (best[L] - pop[i][L])); }
                    accept(pop, fit, i, np);
                }
                for (int i = 0; i < POP; i++) {
                    double[] np = new double[M];
                    for (int d = 0; d < M; d++) np[d] = clamp(0.5 * pop[i][d] + 0.5 * best[d]);
                    accept(pop, fit, i, np);
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
                    accept(pop, fit, i, np);
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
                        double wt = 0.1 + 0.8 * prog;
                        for (int d = 0; d < M; d++) np[d] = clamp((1 - wt) * pop[i][d] + wt * best[d]);
                    }
                    accept(pop, fit, i, np);
                }
            }
            for (int i = 0; i < POP; i++) if (fit[i] < bestF) { bestF = fit[i]; best = pop[i].clone(); }
        }
        return best;
    }

    static void accept(double[][] pop, double[] fit, int i, double[] np) {
        double nf = schedule(decode(np));
        if (nf < fit[i]) { pop[i] = np; fit[i] = nf; }
    }

    // ==================== 编解码 / 工具 ====================
    static int[] decode(double[] cont) {
        int[] d = new int[cont.length];
        for (int i = 0; i < cont.length; i++) {
            int idx = (int) (clamp(cont[i]) * N);
            if (idx >= N) idx = N - 1;
            d[i] = idx;
        }
        return d;
    }
    static double[] encode(int[] s) {
        double[] c = new double[s.length];
        for (int i = 0; i < s.length; i++) c[i] = (s[i] + 0.5) / N;
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

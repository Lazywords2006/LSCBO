package com.edcbo.research;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.*;

/**
 * DAG 工作流调度对比实验
 *
 * 真正适合元启发式的场景：任务间有依赖（DAG），makespan 由关键路径决定。
 * HEFT (Heterogeneous Earliest Finish Time) 是 list-scheduling 标准基线，
 * 文献证明它离最优有 5-20% 差距 → 元启发式有真实优化空间。
 *
 * DAG 生成：合成分层 DAG（10 层，每层 N/10 任务），任务间依赖+通信成本。
 * 解码：每个个体编码任务→VM 映射；处理顺序由 upward-rank（HEFT 标准）确定。
 * Fitness：列表调度算法计算 makespan（含数据传输时间）。
 *
 * 对比：HEFT (baseline) vs LSCBO / CBO / WOA (均从 HEFT 解播种, 同预算 P=30 T=100)
 *
 * 输出：results/DAGWorkflow_TIMESTAMP.csv
 */
public class DAGWorkflowComparison {

    static final int N_VMS = 20;
    static final int VM_MIN = 100, VM_MAX = 2000;
    static final int LAYERS = 10;
    static final double CCR = 1.0;  // Communication-to-Computation Ratio
    static final int[] SCALES = {50, 100, 200};
    static final long[] SEEDS = {
        43,44,45,46,47,48,49,50,51,52,
        53,54,55,56,57,58,59,60,61,62,
        63,64,65,66,67,68,69,70,71,72
    };
    static final String[] ALGOS = {"HEFT","LSCBO","CBO","WOA"};
    static final int POP = 30, ITER = 100;

    // 当前实例（每个 seed 生成一次，所有算法共用）
    static double[] mips;
    static double[] taskLen;
    static double[][] commCost;        // commCost[i][j] = i 到 j 的通信量
    static List<List<Integer>> preds;
    static List<List<Integer>> succs;
    static int[] processOrder;          // upward-rank 降序

    public static void main(String[] a) throws Exception {
        String ts = java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String out = "results/DAGWorkflow_" + ts + ".csv";
        new java.io.File("results").mkdirs();

        int total = SCALES.length * SEEDS.length * ALGOS.length;
        int done = 0;
        System.out.printf("DAG 工作流: VM=%d, LAYERS=%d, CCR=%.1f, 总 %d 次运行%n",
            N_VMS, LAYERS, CCR, total);

        try (PrintWriter pw = new PrintWriter(new FileWriter(out))) {
            pw.println("Algorithm,TaskCount,Seed,Makespan,vs_HEFT_pct");
            for (int N : SCALES) {
                for (long seed : SEEDS) {
                    genInstance(N, seed);
                    int[] heftSched = heft(N);
                    double heftMk = scheduleSimulate(heftSched, N);

                    double[] mks = new double[ALGOS.length];
                    for (int k = 0; k < ALGOS.length; k++) {
                        if (k == 0) { mks[k] = heftMk; }
                        else {
                            int[] s = optimize(ALGOS[k], N, seed, heftSched);
                            mks[k] = scheduleSimulate(s, N);
                        }
                        double vs = (mks[k] - heftMk) / heftMk * 100;
                        pw.printf("%s,%d,%d,%.4f,%+.2f%n", ALGOS[k], N, seed, mks[k], vs);
                        done++;
                    }
                    pw.flush();
                    if (done % 20 == 0 || done == total)
                        System.out.printf("[%4d/%d] N=%d seed=%d  HEFT=%.1f LSCBO=%.1f(%.1f%%) CBO=%.1f WOA=%.1f%n",
                            done, total, N, seed, mks[0], mks[1], (mks[1]-heftMk)/heftMk*100, mks[2], mks[3]);
                }
            }
        }
        System.out.println("Done: " + out);
    }

    // ── DAG 生成（合成分层 + 依赖 + 通信成本） ───────────────────────────────
    static void genInstance(int N, long seed) {
        Random rvm = new Random(seed);
        mips = new double[N_VMS];
        for (int v = 0; v < N_VMS; v++) mips[v] = VM_MIN + rvm.nextDouble()*(VM_MAX-VM_MIN);
        double avgMips = Arrays.stream(mips).average().orElse(1000);

        Random rcl = new Random(seed + 1_000_000L);
        taskLen = new double[N];
        for (int i = 0; i < N; i++) taskLen[i] = 1000 + rcl.nextDouble()*19000;
        double avgLen = Arrays.stream(taskLen).average().orElse(10000);
        double avgComm = CCR * avgLen / avgMips;

        // 任务分配到层
        int[] layer = new int[N];
        for (int i = 0; i < N; i++) layer[i] = (int)((long)i * LAYERS / N);

        // 生成依赖（每层任务从上一层选 1-2 个前驱）
        commCost = new double[N][N];
        preds = new ArrayList<>(); succs = new ArrayList<>();
        for (int i = 0; i < N; i++) { preds.add(new ArrayList<>()); succs.add(new ArrayList<>()); }

        Random re = new Random(seed + 2_000_000L);
        for (int i = 0; i < N; i++) {
            if (layer[i] == 0) continue;
            List<Integer> cand = new ArrayList<>();
            for (int j = 0; j < i; j++) if (layer[j] == layer[i] - 1) cand.add(j);
            if (cand.isEmpty()) for (int j = 0; j < i; j++) cand.add(j);
            Collections.shuffle(cand, re);
            int k = 1 + re.nextInt(Math.min(2, cand.size()));
            for (int t = 0; t < k; t++) {
                int p = cand.get(t);
                if (commCost[p][i] == 0) {
                    preds.get(i).add(p); succs.get(p).add(i);
                    commCost[p][i] = avgComm * (0.5 + re.nextDouble());
                }
            }
        }
        // 计算 upward rank（HEFT 标准），降序作为处理顺序
        double[] rank = new double[N];
        for (int i = N-1; i >= 0; i--) {
            double maxSucc = 0;
            for (int j : succs.get(i)) maxSucc = Math.max(maxSucc, commCost[i][j] + rank[j]);
            rank[i] = taskLen[i] / avgMips + maxSucc;
        }
        Integer[] order = new Integer[N];
        for (int i = 0; i < N; i++) order[i] = i;
        Arrays.sort(order, (x, y) -> Double.compare(rank[y], rank[x]));
        processOrder = new int[N];
        for (int i = 0; i < N; i++) processOrder[i] = order[i];
    }

    /** 列表调度（fitness 计算核心，含通信成本）。 */
    static double scheduleSimulate(int[] assign, int N) {
        double[] aft = new double[N];        // actual finish time
        double[] vmAvail = new double[N_VMS]; // VM 何时可用
        for (int k = 0; k < N; k++) {
            int i = processOrder[k];
            int v = assign[i];
            double dataReady = 0;
            for (int p : preds.get(i)) {
                double arrive = aft[p] + (assign[p] == v ? 0 : commCost[p][i]);
                dataReady = Math.max(dataReady, arrive);
            }
            double est = Math.max(dataReady, vmAvail[v]);
            double eft = est + taskLen[i] / mips[v];
            aft[i] = eft;
            vmAvail[v] = eft;
        }
        double mk = 0;
        for (double f : aft) mk = Math.max(mk, f);
        return mk;
    }

    // ── HEFT baseline ───────────────────────────────────────────────────────
    static int[] heft(int N) {
        int[] assign = new int[N];
        double[] aft = new double[N];
        double[] vmAvail = new double[N_VMS];
        for (int k = 0; k < N; k++) {
            int i = processOrder[k];
            int bestV = 0; double bestEft = Double.MAX_VALUE;
            for (int v = 0; v < N_VMS; v++) {
                double dataReady = 0;
                for (int p : preds.get(i)) {
                    double arrive = aft[p] + (assign[p] == v ? 0 : commCost[p][i]);
                    dataReady = Math.max(dataReady, arrive);
                }
                double est = Math.max(dataReady, vmAvail[v]);
                double eft = est + taskLen[i] / mips[v];
                if (eft < bestEft) { bestEft = eft; bestV = v; }
            }
            assign[i] = bestV; aft[i] = bestEft; vmAvail[bestV] = bestEft;
        }
        return assign;
    }

    // ── 元启发式（从 HEFT 播种 + 连续编码） ─────────────────────────────────
    static int[] optimize(String algo, int N, long seed, int[] heftSched) {
        Random rnd = new Random(seed + 7L);
        double[][] pop = new double[POP][N];
        double[] fit = new double[POP];
        // 第一个个体用 HEFT 解播种
        for (int d = 0; d < N; d++) pop[0][d] = (heftSched[d] + 0.5) / N_VMS;
        for (int i = 1; i < POP; i++)
            for (int d = 0; d < N; d++) pop[i][d] = rnd.nextDouble();
        double[] best = null; double bestF = Double.MAX_VALUE;
        for (int i = 0; i < POP; i++) {
            fit[i] = scheduleSimulate(decode(pop[i], N), N);
            if (fit[i] < bestF) { bestF = fit[i]; best = pop[i].clone(); }
        }
        for (int t = 0; t < ITER; t++) {
            double tr = (double)t / ITER;
            for (int i = 0; i < POP; i++) {
                double[] np = new double[N];
                if (algo.equals("LSCBO")) {
                    if (tr < 1.0/3) {
                        int prey = rnd.nextInt(POP); double al = 0.05*(1-tr);
                        for (int d = 0; d < N; d++) np[d] = clamp(pop[i][d]+al*rnd.nextGaussian()*(pop[prey][d]-pop[i][d]));
                    } else if (tr < 2.0/3) {
                        double th=2*Math.PI*t/ITER, cs=Math.cos(th), sn=Math.sin(th);
                        for (int d = 0; d < N-1; d+=2) { double dx=pop[i][d]-best[d], dy=pop[i][d+1]-best[d+1];
                            np[d]=clamp(best[d]+dx*cs-dy*sn); np[d+1]=clamp(best[d+1]+dx*sn+dy*cs);}
                        if (N%2==1) np[N-1]=clamp(pop[i][N-1]+rnd.nextDouble()*(best[N-1]-pop[i][N-1]));
                    } else { double w=0.1+0.8*tr; for (int d=0;d<N;d++) np[d]=clamp((1-w)*pop[i][d]+w*best[d]); }
                } else if (algo.equals("CBO")) {
                    int prey = rnd.nextInt(POP);
                    for (int d = 0; d < N; d++) {
                        double dist = Math.abs(pop[prey][d]-pop[i][d]);
                        np[d] = clamp(pop[i][d]+rnd.nextDouble()*Math.tanh(dist)*(pop[prey][d]-pop[i][d]));
                    }
                } else { // WOA
                    double a = 2.0-2.0*t/ITER;
                    double r=rnd.nextDouble(),A=2*a*r-a,C=2*r,p=rnd.nextDouble(),l=rnd.nextDouble()*2-1;
                    for (int d = 0; d < N; d++) {
                        double v;
                        if (p<0.5) {
                            if (Math.abs(A)<1) { double D=Math.abs(C*best[d]-pop[i][d]); v=best[d]-A*D; }
                            else { int rw=rnd.nextInt(POP); double D=Math.abs(C*pop[rw][d]-pop[i][d]); v=pop[rw][d]-A*D; }
                        } else { double D=Math.abs(best[d]-pop[i][d]); v=D*Math.exp(l)*Math.cos(2*Math.PI*l)+best[d]; }
                        np[d] = clamp(v);
                    }
                }
                double nf = scheduleSimulate(decode(np, N), N);
                if (nf < fit[i]) { pop[i] = np; fit[i] = nf; }
                if (nf < bestF) { bestF = nf; best = np.clone(); }
            }
        }
        return decode(best, N);
    }

    static int[] decode(double[] c, int N) {
        int[] d = new int[N];
        for (int i = 0; i < N; i++) {
            int v = (int)(Math.max(0,Math.min(1,c[i]))*N_VMS);
            if (v >= N_VMS) v = N_VMS-1;
            d[i] = v;
        }
        return d;
    }
    static double clamp(double x){return Math.max(0,Math.min(1,x));}
}

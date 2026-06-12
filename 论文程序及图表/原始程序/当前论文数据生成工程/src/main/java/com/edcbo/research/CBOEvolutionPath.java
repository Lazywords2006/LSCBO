package com.edcbo.research;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.*;

/**
 * CBO → LSCBO-LS 演进路径实验
 *
 * 模拟作者真实的算法开发过程，逐步累加每个组件，量化每一步贡献：
 *
 *   V1. CBO-original    : 原版 CBO (tanh搜索朝随机猎物 + 旋转 + 0.5 权重攻击) - 每代三阶段都跑
 *   V2. +Levy           : 把 V1 的 tanh 搜索换成 Lévy 飞行
 *   V3. +DynamicWeight  : 把 V2 的固定 0.5 攻击权重换成动态 0.1→0.9
 *   V4. +Staged         : 把 V3 的"每代三阶段都跑"改为"按 progress 切分三阶段"
 *   V5. +LocalSearch    : 把 V4 加上任务迁移局部搜索 = LSCBO-LS
 *
 * 这能清晰回答："你从 CBO 出发改进的每一步分别贡献多少"
 */
public class CBOEvolutionPath {
    static final int VM = 50;
    static final int VM_MIN = 100, VM_MAX = 5000;
    static final int CL_MIN = 1000, CL_MAX = 20000;
    static final int[] SCALES = {200, 500, 1000, 2000, 5000};
    static final long[] SEEDS = {
        43,44,45,46,47,48,49,50,51,52,53,54,55,56,57,58,59,60,61,62,
        63,64,65,66,67,68,69,70,71,72
    };
    static final int POP = 30, T = 100;
    static final String[] STAGES = {"V1_CBO_orig","V2_addLevy","V3_addDynW","V4_addStaged","V5_addLS"};

    public static void main(String[] args) throws Exception {
        String ts = java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String out = "results/E3_Evolution_" + ts + ".csv";
        new java.io.File("results").mkdirs();
        int total = SCALES.length * SEEDS.length * STAGES.length;
        int done = 0;
        System.out.printf("E3 CBO→LSCBO 演进路径: %d 阶段 x %d 规模 x %d seeds = %d 次%n",
            STAGES.length, SCALES.length, SEEDS.length, total);
        try (PrintWriter pw = new PrintWriter(new FileWriter(out))) {
            pw.println("Stage,TaskCount,Seed,Makespan,LoadBalanceRatio");
            for (int M : SCALES) {
                for (long seed : SEEDS) {
                    double[] vm = genVm(seed);
                    double[] cl = genCl(seed, M);
                    for (String stage : STAGES) {
                        int[] s = runStage(stage, vm, cl, M, seed);
                        double mk = makespan(s,vm,cl,M);
                        double lb = lbr(s,vm,cl,M);
                        pw.printf("%s,%d,%d,%.4f,%.4f%n", stage, M, seed, mk, lb);
                        done++;
                    }
                    pw.flush();
                    if (done % STAGES.length == 0 && (done/STAGES.length) % 15 == 0)
                        System.out.printf("[%5d/%d] M=%d seed=%d%n", done, total, M, seed);
                }
            }
        }
        System.out.println("Done: " + out);
    }

    static int[] runStage(String stage, double[] vm, double[] cl, int M, long seed) {
        boolean useLevy   = !stage.equals("V1_CBO_orig");
        boolean useDynW   = stage.equals("V3_addDynW") || stage.equals("V4_addStaged") || stage.equals("V5_addLS");
        boolean useStaged = stage.equals("V4_addStaged") || stage.equals("V5_addLS");
        boolean useLS     = stage.equals("V5_addLS");

        Random rnd = new Random(seed + 7L);
        double[][] pop = new double[POP][M];
        double[] fit = new double[POP];
        for (int i=0;i<POP;i++) {
            for (int d=0;d<M;d++) pop[i][d]=rnd.nextDouble();
            fit[i] = makespan(decode(pop[i]),vm,cl,M);
        }
        double[] best = null; double bestF = Double.MAX_VALUE;
        for (int i=0;i<POP;i++) if (fit[i]<bestF) {bestF=fit[i]; best=pop[i].clone();}

        for (int t=0;t<T;t++) {
            double tr = (double)t/T;

            if (!useStaged) {
                // V1-V3: 每代三阶段全跑（CBO 原版做法）
                for (int i=0;i<POP;i++) {
                    double[] np = phase1(pop[i], pop, best, M, useLevy, tr, rnd);
                    double nf = makespan(decode(np),vm,cl,M);
                    if (nf<fit[i]) {pop[i]=np; fit[i]=nf;}
                    if (nf<bestF) {bestF=nf; best=np.clone();}
                }
                for (int i=0;i<POP;i++) {
                    double[] np = phase2(pop[i], best, M, t, rnd);
                    double nf = makespan(decode(np),vm,cl,M);
                    if (nf<fit[i]) {pop[i]=np; fit[i]=nf;}
                    if (nf<bestF) {bestF=nf; best=np.clone();}
                }
                for (int i=0;i<POP;i++) {
                    double[] np = phase3(pop[i], best, M, tr, useDynW);
                    double nf = makespan(decode(np),vm,cl,M);
                    if (nf<fit[i]) {pop[i]=np; fit[i]=nf;}
                    if (nf<bestF) {bestF=nf; best=np.clone();}
                }
            } else {
                // V4-V5: 按 progress 切分阶段
                for (int i=0;i<POP;i++) {
                    double[] np;
                    if (tr<1.0/3)      np = phase1(pop[i], pop, best, M, useLevy, tr, rnd);
                    else if (tr<2.0/3) np = phase2(pop[i], best, M, t, rnd);
                    else               np = phase3(pop[i], best, M, tr, useDynW);
                    double nf = makespan(decode(np),vm,cl,M);
                    if (nf<fit[i]) {pop[i]=np; fit[i]=nf;}
                    if (nf<bestF) {bestF=nf; best=np.clone();}
                }
            }

            if (useLS && t>0 && t%10==0) {
                int[] s = decode(best);
                int[] imp = localSearch(s,vm,cl,M);
                double imk = makespan(imp,vm,cl,M);
                if (imk<bestF) {bestF=imk; for(int d=0;d<M;d++) best[d]=(imp[d]+0.5)/VM;}
            }
        }
        return decode(best);
    }

    /** Phase 1: 搜索阶段（原版用 tanh 朝随机猎物，Lévy 版用重尾分布步长）。 */
    static double[] phase1(double[] cur, double[][] pop, double[] best, int M,
                           boolean useLevy, double tr, Random rnd) {
        double[] np = new double[M];
        int prey = rnd.nextInt(POP);
        if (!useLevy) {  // V1: 原版 CBO tanh 搜索
            for (int d=0;d<M;d++) {
                double dist = Math.abs(pop[prey][d]-cur[d]);
                np[d] = clamp(cur[d]+rnd.nextDouble()*Math.tanh(dist)*(pop[prey][d]-cur[d]));
            }
        } else {  // V2+: Lévy 飞行
            double alpha = 0.05*(1-tr);
            for (int d=0;d<M;d++) {
                np[d] = clamp(cur[d]+alpha*rnd.nextGaussian()*(pop[prey][d]-cur[d]));
            }
        }
        return np;
    }
    /** Phase 2: 旋转矩阵包围（V1-V5 都用，不变）。 */
    static double[] phase2(double[] cur, double[] best, int M, int t, Random rnd) {
        double th=2*Math.PI*t/T, cs=Math.cos(th), sn=Math.sin(th);
        double[] np = new double[M];
        for (int d=0;d<M-1;d+=2) {
            double dx=cur[d]-best[d], dy=cur[d+1]-best[d+1];
            np[d]=clamp(best[d]+dx*cs-dy*sn);
            np[d+1]=clamp(best[d+1]+dx*sn+dy*cs);
        }
        if (M%2==1) np[M-1]=clamp(cur[M-1]+rnd.nextDouble()*(best[M-1]-cur[M-1]));
        return np;
    }
    /** Phase 3: 攻击阶段（V1-V2 用 0.5 固定权重，V3+ 用动态 0.1→0.9）。 */
    static double[] phase3(double[] cur, double[] best, int M, double tr, boolean useDynW) {
        double w = useDynW ? (0.1+0.8*tr) : 0.5;
        double[] np = new double[M];
        for (int d=0;d<M;d++) np[d] = clamp((1-w)*cur[d]+w*best[d]);
        return np;
    }

    static int[] localSearch(int[] s, double[] vm, double[] cl, int M) {
        int[] r = s.clone();
        double[] load = new double[VM];
        for (int i=0;i<M;i++) load[r[i]] += cl[i]/vm[r[i]];
        for (int iter=0; iter<10; iter++) {
            int worst=0, bv=0;
            for (int v=1;v<VM;v++) {if(load[v]>load[worst]) worst=v; if(load[v]<load[bv]) bv=v;}
            if (worst==bv) break;
            int bt=-1; double bg=0;
            for (int i=0;i<M;i++) {
                if (r[i]!=worst) continue;
                double rc=cl[i]/vm[worst], rn=cl[i]/vm[bv];
                double nmk = Math.max(load[worst]-rc, load[bv]+rn);
                double gain = load[worst]-nmk;
                if (gain>bg) {bg=gain; bt=i;}
            }
            if (bt<0) break;
            load[worst]-=cl[bt]/vm[worst]; load[bv]+=cl[bt]/vm[bv]; r[bt]=bv;
        }
        return r;
    }
    static int[] decode(double[] c) {
        int[] d=new int[c.length];
        for(int i=0;i<c.length;i++){int v=(int)(Math.max(0,Math.min(1,c[i]))*VM);if(v>=VM)v=VM-1;d[i]=v;}
        return d;
    }
    static double makespan(int[] s, double[] vm, double[] cl, int M) {
        double[] ld=new double[VM];for(int i=0;i<M;i++) ld[s[i]]+=cl[i]/vm[s[i]];
        double m=0;for(double x:ld) m=Math.max(m,x); return m;
    }
    static double lbr(int[] s, double[] vm, double[] cl, int M) {
        double[] ld=new double[VM];for(int i=0;i<M;i++) ld[s[i]]+=cl[i]/vm[s[i]];
        double avg=Arrays.stream(ld).average().orElse(0);
        double var=Arrays.stream(ld).map(x->(x-avg)*(x-avg)).average().orElse(0);
        return avg>0?Math.sqrt(var)/avg:0;
    }
    static double clamp(double x){return Math.max(0,Math.min(1,x));}
    static double[] genVm(long seed){Random r=new Random(seed);double[] v=new double[VM];
        for(int i=0;i<VM;i++) v[i]=VM_MIN+r.nextDouble()*(VM_MAX-VM_MIN); return v;}
    static double[] genCl(long seed,int M){Random r=new Random(seed+1_000_000L);double[] c=new double[M];
        for(int i=0;i<M;i++) c[i]=CL_MIN+r.nextDouble()*(CL_MAX-CL_MIN); return c;}
}

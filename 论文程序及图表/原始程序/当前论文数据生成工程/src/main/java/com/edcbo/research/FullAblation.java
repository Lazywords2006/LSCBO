package com.edcbo.research;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.*;

/**
 * E2 — 完整消融实验
 *
 * 6 个 LSCBO 变体，验证各组件贡献：
 *   base    : LSCBO 三阶段算子（无 LS，pop=30）
 *   noPhase1: 移除 Lévy 阶段（仅旋转+攻击）
 *   noPhase2: 移除旋转阶段
 *   noPhase3: 移除攻击阶段
 *   pop50   : 仅放大种群
 *   LS-only : 仅局部搜索（不跑 LSCBO 算子）
 *   FULL    : LSCBO + LS（论文最终版）
 */
public class FullAblation {
    static final int VM = 50;
    static final int VM_MIN = 100, VM_MAX = 5000;
    static final int CL_MIN = 1000, CL_MAX = 20000;
    static final int[] SCALES = {200, 500, 1000, 2000, 5000};
    static final long[] SEEDS = {
        43,44,45,46,47,48,49,50,51,52,53,54,55,56,57,58,59,60,61,62,
        63,64,65,66,67,68,69,70,71,72
    };
    static final int POP_BASE = 30, POP_BIG = 50, T = 100;
    static final String[] VARIANTS = {"base","noPhase1","noPhase2","noPhase3","pop50","LS-only","FULL"};

    public static void main(String[] args) throws Exception {
        String ts = java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String out = "results/E2_Ablation_" + ts + ".csv";
        new java.io.File("results").mkdirs();
        int total = SCALES.length * SEEDS.length * VARIANTS.length;
        int done = 0;
        System.out.printf("E2 消融: %d 变体 x %d 规模 x %d seeds = %d 次%n",
            VARIANTS.length, SCALES.length, SEEDS.length, total);
        try (PrintWriter pw = new PrintWriter(new FileWriter(out))) {
            pw.println("Variant,TaskCount,Seed,Makespan,LoadBalanceRatio");
            for (int M : SCALES) {
                for (long seed : SEEDS) {
                    double[] vm = genVm(seed);
                    double[] cl = genCl(seed, M);
                    for (String v : VARIANTS) {
                        int[] s = runVariant(v, vm, cl, M, seed);
                        double mk = makespan(s,vm,cl,M);
                        double lb = lbr(s,vm,cl,M);
                        pw.printf("%s,%d,%d,%.4f,%.4f%n", v, M, seed, mk, lb);
                        done++;
                    }
                    pw.flush();
                    if (done % VARIANTS.length == 0 && (done/VARIANTS.length) % 10 == 0)
                        System.out.printf("[%5d/%d] M=%d seed=%d%n", done, total, M, seed);
                }
            }
        }
        System.out.println("Done: " + out);
    }

    static int[] runVariant(String variant, double[] vm, double[] cl, int M, long seed) {
        int POP = variant.equals("pop50") ? POP_BIG : POP_BASE;
        boolean useLS  = variant.equals("FULL") || variant.equals("LS-only");
        boolean useP1  = !variant.equals("noPhase1") && !variant.equals("LS-only");
        boolean useP2  = !variant.equals("noPhase2") && !variant.equals("LS-only");
        boolean useP3  = !variant.equals("noPhase3") && !variant.equals("LS-only");

        Random rnd = new Random(seed + 7L);
        double[][] pop = new double[POP][M];
        double[] fit = new double[POP];
        for (int i=0;i<POP;i++) {
            for (int d=0;d<M;d++) pop[i][d]=rnd.nextDouble();
            fit[i] = makespan(decode(pop[i]),vm,cl,M);
        }
        double[] best = null; double bestF = Double.MAX_VALUE;
        for (int i=0;i<POP;i++) if(fit[i]<bestF){bestF=fit[i];best=pop[i].clone();}

        for (int t=0;t<T;t++) {
            double tr=(double)t/T;
            for (int i=0;i<POP;i++) {
                double[] np = pop[i].clone();
                if (useP1 && tr<1.0/3) {
                    int prey=rnd.nextInt(POP); double al=0.05*(1-tr);
                    for(int d=0;d<M;d++) np[d]=clamp(pop[i][d]+al*rnd.nextGaussian()*(pop[prey][d]-pop[i][d]));
                } else if (useP2 && tr<2.0/3) {
                    double th=2*Math.PI*t/T,cs=Math.cos(th),sn=Math.sin(th);
                    for(int d=0;d<M-1;d+=2){double dx=pop[i][d]-best[d],dy=pop[i][d+1]-best[d+1];
                        np[d]=clamp(best[d]+dx*cs-dy*sn);np[d+1]=clamp(best[d+1]+dx*sn+dy*cs);}
                    if(M%2==1) np[M-1]=clamp(pop[i][M-1]+rnd.nextDouble()*(best[M-1]-pop[i][M-1]));
                } else if (useP3) {
                    double w=0.1+0.8*tr;
                    for(int d=0;d<M;d++) np[d]=clamp((1-w)*pop[i][d]+w*best[d]);
                }
                double nf = makespan(decode(np),vm,cl,M);
                if (nf<fit[i]) {pop[i]=np; fit[i]=nf;}
                if (nf<bestF) {bestF=nf; best=np.clone();}
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

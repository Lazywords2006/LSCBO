package com.edcbo.research;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.*;

/**
 * LSCBO 参数调优实验（只调自己，对手保持标准）
 *
 * 文献接受的做法：新算法可调自己的参数（per-algorithm tuning），但对手必须保持标准。
 *
 * 调优维度：
 *   - 种群大小 POP: 30 / 50
 *   - Lévy α 系数: 0.05 / 0.10 / 0.15
 *   - 阶段切分: [1/3, 2/3] (base) / [1/4, 1/2] (强探索) / [1/4, 3/4] (中后期长)
 *   - 动态权重 w 范围: [0.1, 0.9] (base) / [0.05, 0.95] (扩大)
 *   - 局部搜索精修: on/off (任务迁移到更空闲 VM)
 *
 * 对比：8 个 LSCBO 变体 + GTO (当前最强对手) + WOA (SOTA)
 * 目标：找到能击败 GTO 的 LSCBO 配置。
 *
 * 输出：results/LSCBOTuning_TIMESTAMP.csv
 */
public class LSCBOTuningExperiment {

    static final int VM = 50;
    static final int VM_MIN = 100, VM_MAX = 5000;
    static final int CL_MIN = 1000, CL_MAX = 20000;
    static final int[] SCALES = {200, 500, 1000};
    static final long[] SEEDS = {
        43,44,45,46,47,48,49,50,51,52,
        53,54,55,56,57,58,59,60,61,62,
        63,64,65,66,67,68,69,70,71,72
    };
    static final int T = 100;

    // LSCBO 变体配置：[pop, alpha, phase1End, phase2End, wMin, wMax, useLS, label]
    static final Object[][] VARIANTS = {
        {30, 0.05, 1.0/3, 2.0/3, 0.1, 0.9, false, "LSCBO-base"},
        {50, 0.05, 1.0/3, 2.0/3, 0.1, 0.9, false, "LSCBO-pop50"},
        {30, 0.10, 1.0/3, 2.0/3, 0.1, 0.9, false, "LSCBO-a0.10"},
        {30, 0.15, 1.0/3, 2.0/3, 0.1, 0.9, false, "LSCBO-a0.15"},
        {30, 0.05, 0.25, 0.5,    0.1, 0.9, false, "LSCBO-explore"},
        {30, 0.05, 0.25, 0.75,   0.1, 0.9, false, "LSCBO-spiralong"},
        {30, 0.05, 1.0/3, 2.0/3, 0.05,0.95,false, "LSCBO-wWide"},
        {30, 0.05, 1.0/3, 2.0/3, 0.1, 0.9, true,  "LSCBO-LS"},
        {50, 0.10, 0.25, 0.75,   0.05,0.95,true,  "LSCBO-MEGA"},  // 全部增强叠加
    };
    static final String[] BASELINES = {"GTO","WOA","CBO"};

    public static void main(String[] args) throws Exception {
        String ts = java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String out = "results/LSCBOTuning_" + ts + ".csv";
        new java.io.File("results").mkdirs();
        int n = VARIANTS.length + BASELINES.length;
        int total = SCALES.length * SEEDS.length * n;
        int done = 0;
        System.out.printf("LSCBO 调优实验: %d 变体 + %d baseline, 总 %d 次%n",
            VARIANTS.length, BASELINES.length, total);

        try (PrintWriter pw = new PrintWriter(new FileWriter(out))) {
            pw.println("Algorithm,TaskCount,Seed,Makespan,LoadBalanceRatio");
            for (int M : SCALES) {
                for (long seed : SEEDS) {
                    double[] vm = genVm(seed);
                    double[] cl = genCl(seed, M);
                    // 跑 9 个 LSCBO 变体
                    for (Object[] v : VARIANTS) {
                        int pop = (int)v[0]; double alpha = (double)v[1];
                        double p1 = (double)v[2], p2 = (double)v[3];
                        double wmin = (double)v[4], wmax = (double)v[5];
                        boolean useLS = (boolean)v[6]; String name = (String)v[7];
                        int[] s = lscbo(vm, cl, M, seed, pop, alpha, p1, p2, wmin, wmax, useLS);
                        double mk = makespan(s,vm,cl,M);
                        double lb = lbr(s,vm,cl,M);
                        pw.printf("%s,%d,%d,%.4f,%.4f%n", name, M, seed, mk, lb);
                        done++;
                    }
                    // baselines
                    for (String al : BASELINES) {
                        int[] s = baseline(al, vm, cl, M, seed);
                        double mk = makespan(s,vm,cl,M);
                        double lb = lbr(s,vm,cl,M);
                        pw.printf("%s,%d,%d,%.4f,%.4f%n", al, M, seed, mk, lb);
                        done++;
                    }
                    pw.flush();
                    if (done % n == 0)
                        System.out.printf("[%4d/%d] M=%d seed=%d%n", done, total, M, seed);
                }
            }
        }
        System.out.println("Done: " + out);
    }

    // ── LSCBO 参数化版本 ───────────────────────────────────────────────────
    static int[] lscbo(double[] vm, double[] cl, int M, long seed,
                       int POP, double ALPHA, double P1, double P2,
                       double WMIN, double WMAX, boolean useLS) {
        Random rnd = new Random(seed + 7L);
        double[][] pop = new double[POP][M];
        double[] fit = new double[POP];
        for (int i=0;i<POP;i++){ for (int d=0;d<M;d++) pop[i][d]=rnd.nextDouble();
            fit[i] = makespan(decode(pop[i]), vm, cl, M); }
        double[] best = null; double bestF = Double.MAX_VALUE;
        for (int i=0;i<POP;i++) if (fit[i]<bestF){bestF=fit[i];best=pop[i].clone();}
        for (int t=0;t<T;t++) {
            double tr = (double)t/T;
            for (int i=0;i<POP;i++) {
                double[] np = new double[M];
                if (tr<P1) {  // Lévy 飞行
                    int prey = rnd.nextInt(POP); double al = ALPHA*(1-tr);
                    for (int d=0;d<M;d++) np[d]=clamp(pop[i][d]+al*rnd.nextGaussian()*(pop[prey][d]-pop[i][d]));
                } else if (tr<P2) {  // 旋转矩阵
                    double th=2*Math.PI*t/T, cs=Math.cos(th), sn=Math.sin(th);
                    for (int d=0;d<M-1;d+=2){double dx=pop[i][d]-best[d],dy=pop[i][d+1]-best[d+1];
                        np[d]=clamp(best[d]+dx*cs-dy*sn); np[d+1]=clamp(best[d+1]+dx*sn+dy*cs);}
                    if (M%2==1) np[M-1]=clamp(pop[i][M-1]+rnd.nextDouble()*(best[M-1]-pop[i][M-1]));
                } else {  // 动态攻击
                    double w = WMIN + (WMAX-WMIN)*tr;
                    for (int d=0;d<M;d++) np[d]=clamp((1-w)*pop[i][d]+w*best[d]);
                }
                double nf = makespan(decode(np), vm, cl, M);
                if (nf<fit[i]){pop[i]=np; fit[i]=nf;}
                if (nf<bestF){bestF=nf; best=np.clone();}
            }
            // 局部搜索：每 10 代对 best 做一次任务迁移精修
            if (useLS && t > 0 && t % 10 == 0) {
                int[] s = decode(best);
                int[] improved = localSearch(s, vm, cl, M);
                double imp_mk = makespan(improved, vm, cl, M);
                if (imp_mk < bestF) {
                    bestF = imp_mk;
                    for (int d=0;d<M;d++) best[d] = (improved[d]+0.5)/VM;
                }
            }
        }
        return decode(best);
    }

    /** 局部搜索：把 makespan 瓶颈 VM 上的某些任务迁移到更空闲 VM。 */
    static int[] localSearch(int[] s, double[] vm, double[] cl, int M) {
        int[] r = s.clone();
        double[] load = new double[VM];
        for (int i=0;i<M;i++) load[r[i]] += cl[i]/vm[r[i]];
        for (int iter=0; iter<10; iter++) {
            int worstVm = 0; for (int v=1;v<VM;v++) if (load[v]>load[worstVm]) worstVm=v;
            int bestVm = 0;  for (int v=1;v<VM;v++) if (load[v]<load[bestVm]) bestVm=v;
            if (worstVm == bestVm) break;
            // 找 worstVm 上能让 makespan 降低的任务
            int bestTask = -1; double bestGain = 0;
            for (int i=0;i<M;i++) {
                if (r[i] != worstVm) continue;
                double runCur = cl[i]/vm[worstVm];
                double runNew = cl[i]/vm[bestVm];
                double newLoadWorst = load[worstVm] - runCur;
                double newLoadBest  = load[bestVm]  + runNew;
                double newMakespan  = Math.max(newLoadWorst, newLoadBest);
                double oldMakespan  = load[worstVm];
                double gain = oldMakespan - newMakespan;
                if (gain > bestGain) { bestGain = gain; bestTask = i; }
            }
            if (bestTask < 0) break;
            load[worstVm] -= cl[bestTask]/vm[worstVm];
            load[bestVm]  += cl[bestTask]/vm[bestVm];
            r[bestTask] = bestVm;
        }
        return r;
    }

    // ── baselines (标准参数) ──────────────────────────────────────────────
    static int[] baseline(String algo, double[] vm, double[] cl, int M, long seed) {
        Random rnd = new Random(seed + 7L);
        int POP = 30;
        double[][] pop = new double[POP][M];
        double[] fit = new double[POP];
        for (int i=0;i<POP;i++){for(int d=0;d<M;d++)pop[i][d]=rnd.nextDouble();
            fit[i]=makespan(decode(pop[i]),vm,cl,M);}
        double[] best = null; double bestF = Double.MAX_VALUE;
        for (int i=0;i<POP;i++) if(fit[i]<bestF){bestF=fit[i];best=pop[i].clone();}

        for (int t=0;t<T;t++) {
            double tr=(double)t/T;
            for (int i=0;i<POP;i++) {
                double[] np;
                switch (algo) {
                    case "GTO": np=gto(pop[i],pop,best,M,tr,rnd,POP); break;
                    case "WOA": np=woa(pop[i],pop,best,M,tr,rnd,POP); break;
                    case "CBO": np=cbo(pop[i],pop,best,M,t,tr,rnd,POP); break;
                    default: throw new IllegalArgumentException(algo);
                }
                double nf = makespan(decode(np),vm,cl,M);
                if (nf<fit[i]){pop[i]=np;fit[i]=nf;}
                if (nf<bestF){bestF=nf;best=np.clone();}
            }
        }
        return decode(best);
    }
    static double[] gto(double[] cur, double[][] pop, double[] best, int M, double tr, Random rnd, int POP) {
        double F=1-tr; double[] np = new double[M];
        if (rnd.nextDouble()<0.3) for(int d=0;d<M;d++) np[d]=rnd.nextDouble();
        else if (rnd.nextDouble()<0.5) {int gx=rnd.nextInt(POP),gy=rnd.nextInt(POP);
            for(int d=0;d<M;d++) np[d]=clamp((rnd.nextDouble()-F)*pop[gx][d]+F*pop[gy][d]);}
        else for(int d=0;d<M;d++) np[d]=clamp(best[d]-F*(best[d]-cur[d])*(2*rnd.nextDouble()-1));
        return np;
    }
    static double[] woa(double[] cur, double[][] pop, double[] best, int M, double tr, Random rnd, int POP) {
        double a=2-2*tr, r=rnd.nextDouble(), A=2*a*r-a, C=2*r, p=rnd.nextDouble(), l=rnd.nextDouble()*2-1;
        double[] np = new double[M];
        for(int d=0;d<M;d++){double v;
            if(p<0.5){if(Math.abs(A)<1){double D=Math.abs(C*best[d]-cur[d]);v=best[d]-A*D;}
                      else{int rw=rnd.nextInt(POP);double D=Math.abs(C*pop[rw][d]-cur[d]);v=pop[rw][d]-A*D;}}
            else{double D=Math.abs(best[d]-cur[d]);v=D*Math.exp(l)*Math.cos(2*Math.PI*l)+best[d];}
            np[d]=clamp(v);}
        return np;
    }
    static double[] cbo(double[] cur, double[][] pop, double[] best, int M, int t, double tr, Random rnd, int POP) {
        int prey=rnd.nextInt(POP);
        double[] np=new double[M];
        for(int d=0;d<M;d++){double dist=Math.abs(pop[prey][d]-cur[d]);
            np[d]=clamp(cur[d]+rnd.nextDouble()*Math.tanh(dist)*(pop[prey][d]-cur[d]));}
        return np;
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

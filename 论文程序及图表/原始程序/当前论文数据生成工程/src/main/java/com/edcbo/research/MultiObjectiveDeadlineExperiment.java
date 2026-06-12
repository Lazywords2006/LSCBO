package com.edcbo.research;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.*;

/**
 * 多目标约束云调度实验：makespan + 能耗 + 截止期约束
 *
 * 关键差异：MaxMin/MinMin/HEFT 只优化 makespan，不考虑能耗与截止期，
 * 加入约束后它们的解会有截止期违反 → 元启发式有真实优化空间。
 *
 * 问题模型：
 *   - 每个任务有截止期 deadline_i = baseline_eft_i × (1 + slack)
 *     baseline_eft = 任务最快 VM 上的执行时间
 *     slack = 0.5 (50% 松弛)
 *   - 目标函数（综合 fitness）：
 *       f = w_mk * makespan/mk_ref + w_en * energy/en_ref + w_pen * violation_rate
 *   - 能耗：Beloglazov & Buyya 2012 标准模型
 *     Energy = Σ (BASE + DYNAMIC * U) * runtime, U=1, PUE=1.3
 *   - 约束违反：任务完成时间 > deadline 的比例
 *
 * 对比：MaxMin / MinMin / MCT (经典，仅 makespan) vs LSCBO / CBO / WOA (优化综合 f)
 *
 * 输出：results/MultiObj_TIMESTAMP.csv
 */
public class MultiObjectiveDeadlineExperiment {

    static final int VM = 50;
    static final int VM_MIN = 100, VM_MAX = 5000;
    static final int CL_MIN = 1000, CL_MAX = 20000;
    static final double DEADLINE_SLACK = 0.5;
    // 权重（约束违反加重惩罚）
    static final double W_MK = 0.3, W_EN = 0.3, W_PEN = 0.4;

    // Beloglazov & Buyya 2012 能耗参数
    static final double BASE_POWER = 150.0;
    static final double DYNAMIC_POWER = 200.0;
    static final double PUE = 1.3;

    static final int[] SCALES = {200, 500, 1000};
    static final long[] SEEDS = {
        43,44,45,46,47,48,49,50,51,52,
        53,54,55,56,57,58,59,60,61,62,
        63,64,65,66,67,68,69,70,71,72
    };
    static final String[] CLASSIC = {"MaxMin","MinMin","MCT"};
    static final String[] META    = {"LSCBO","CBO","WOA"};
    static final int P = 30, T = 100;

    public static void main(String[] args) throws Exception {
        String ts = java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String out = "results/MultiObj_" + ts + ".csv";
        new java.io.File("results").mkdirs();
        int total = SCALES.length * SEEDS.length * (CLASSIC.length + META.length);
        int done = 0;
        System.out.printf("多目标(makespan+能耗+截止期约束): VM=%d, slack=%.0f%%, 总 %d 次%n",
            VM, DEADLINE_SLACK*100, total);

        try (PrintWriter pw = new PrintWriter(new FileWriter(out))) {
            pw.println("Algorithm,Type,TaskCount,Seed,Makespan,EnergyKWh,ViolRate,CompositeF");
            for (int M : SCALES) {
                for (long seed : SEEDS) {
                    double[] vm = genVm(seed);
                    double[] cl = genCl(seed, M);
                    double[] deadline = genDeadline(vm, cl, M);
                    double[] ref = refValues(vm, cl, M);
                    double mkRef = ref[0], enRef = ref[1];

                    for (String al : CLASSIC) {
                        int[] s = scheduleClassic(al, vm, cl, M);
                        double mk = makespan(s, vm, cl, M);
                        double en = energy(s, vm, cl, M);
                        double vr = violationRate(s, vm, cl, deadline, M);
                        double f  = W_MK*mk/mkRef + W_EN*en/enRef + W_PEN*vr;
                        pw.printf("%s,classic,%d,%d,%.4f,%.4f,%.4f,%.4f%n",
                            al, M, seed, mk, en, vr, f);
                        done++;
                    }
                    for (String al : META) {
                        int[] s = optimize(al, vm, cl, deadline, mkRef, enRef, M, seed);
                        double mk = makespan(s, vm, cl, M);
                        double en = energy(s, vm, cl, M);
                        double vr = violationRate(s, vm, cl, deadline, M);
                        double f  = W_MK*mk/mkRef + W_EN*en/enRef + W_PEN*vr;
                        pw.printf("%s,meta,%d,%d,%.4f,%.4f,%.4f,%.4f%n",
                            al, M, seed, mk, en, vr, f);
                        done++;
                    }
                    pw.flush();
                    if (done % 24 == 0 || done == total)
                        System.out.printf("[%4d/%d] M=%d seed=%d%n", done, total, M, seed);
                }
            }
        }
        System.out.println("Done: " + out);
    }

    // ── 经典启发式（同前） ────────────────────────────────────────────────
    static int[] scheduleClassic(String al, double[] vm, double[] cl, int M) {
        switch (al) {
            case "MaxMin": return maxMin(vm, cl, M);
            case "MinMin": return minMin(vm, cl, M);
            case "MCT":    return mct(vm, cl, M);
            default: throw new IllegalArgumentException(al);
        }
    }
    static int[] minMin(double[] vm, double[] cl, int M) {
        boolean[] d=new boolean[M]; int[] r=new int[M]; double[] load=new double[VM];
        for (int k=0;k<M;k++){int bt=-1,bv=0;double be=Double.MAX_VALUE;
            for (int i=0;i<M;i++){if(d[i])continue;for(int v=0;v<VM;v++){double e=load[v]+cl[i]/vm[v];if(e<be){be=e;bt=i;bv=v;}}}
            d[bt]=true;r[bt]=bv;load[bv]+=cl[bt]/vm[bv];} return r;
    }
    static int[] maxMin(double[] vm, double[] cl, int M) {
        boolean[] d=new boolean[M]; int[] r=new int[M]; double[] load=new double[VM];
        for (int k=0;k<M;k++){int bt=-1,bv=0;double wm=-1;
            for (int i=0;i<M;i++){if(d[i])continue;double me=Double.MAX_VALUE;int mv=0;
                for(int v=0;v<VM;v++){double e=load[v]+cl[i]/vm[v];if(e<me){me=e;mv=v;}}
                if(me>wm){wm=me;bt=i;bv=mv;}}
            d[bt]=true;r[bt]=bv;load[bv]+=cl[bt]/vm[bv];} return r;
    }
    static int[] mct(double[] vm, double[] cl, int M) {
        int[] r=new int[M]; double[] load=new double[VM];
        for (int i=0;i<M;i++){int b=0;double be=Double.MAX_VALUE;
            for(int v=0;v<VM;v++){double e=load[v]+cl[i]/vm[v];if(e<be){be=e;b=v;}} r[i]=b;load[b]+=cl[i]/vm[b];}
        return r;
    }

    // ── 目标函数 ──────────────────────────────────────────────────────────
    static double makespan(int[] s, double[] vm, double[] cl, int M) {
        double[] ld=new double[VM]; for(int i=0;i<M;i++) ld[s[i]]+=cl[i]/vm[s[i]];
        double m=0; for(double x:ld) m=Math.max(m,x); return m;
    }
    /** 能耗 (kWh)：Beloglazov 模型。 */
    static double energy(int[] s, double[] vm, double[] cl, int M) {
        double[] runtime=new double[VM];
        for(int i=0;i<M;i++) runtime[s[i]]+=cl[i]/vm[s[i]];
        double total=0;
        for(int v=0;v<VM;v++) if(runtime[v]>0)
            total += (BASE_POWER+DYNAMIC_POWER)*PUE*runtime[v]/3600.0/1000.0;
        return total;
    }
    /** 截止期违反率（违反任务数/总任务数）。 */
    static double violationRate(int[] s, double[] vm, double[] cl, double[] dl, int M) {
        double[] vmFinish = new double[VM];
        double[] taskFin  = new double[M];
        // 简化：按任务顺序在每 VM 上累计
        Integer[] order = new Integer[M]; for(int i=0;i<M;i++) order[i]=i;
        Arrays.sort(order,(a,b)->Double.compare(cl[b],cl[a])); // 长任务优先（与 MaxMin 一致）
        for (int idx : order) {
            int v = s[idx];
            vmFinish[v] += cl[idx]/vm[v];
            taskFin[idx] = vmFinish[v];
        }
        int viol = 0;
        for (int i=0;i<M;i++) if (taskFin[i] > dl[i]) viol++;
        return (double)viol/M;
    }
    /** 截止期：基于 MaxMin 解的 EFT × (1+slack) —— 让 MaxMin 刚好满足约束。 */
    static double[] genDeadline(double[] vm, double[] cl, int M) {
        int[] s = maxMin(vm, cl, M);
        double[] dl = new double[M];
        double[] vmFinish = new double[VM];
        Integer[] order = new Integer[M]; for(int i=0;i<M;i++) order[i]=i;
        Arrays.sort(order,(a,b)->Double.compare(cl[b],cl[a]));
        for (int idx : order) {
            int v = s[idx];
            vmFinish[v] += cl[idx]/vm[v];
            dl[idx] = vmFinish[v] * (1+DEADLINE_SLACK);
        }
        return dl;
    }
    /** 归一化参考值：MaxMin 解的 makespan/能耗。 */
    static double[] refValues(double[] vm, double[] cl, int M) {
        int[] s = maxMin(vm, cl, M);
        return new double[]{makespan(s,vm,cl,M), energy(s,vm,cl,M)};
    }

    // ── 元启发式（优化综合 fitness） ──────────────────────────────────────
    static int[] optimize(String algo, double[] vm, double[] cl, double[] dl,
                          double mkRef, double enRef, int M, long seed) {
        Random rnd = new Random(seed + 7L);
        double[][] pop = new double[P][M];
        double[] fit = new double[P];
        // 纯随机初始化（让算子真正搜索综合 fitness 空间）
        for (int i = 0; i < P; i++)
            for (int d = 0; d < M; d++) pop[i][d] = rnd.nextDouble();
        double[] best=null; double bestF=Double.MAX_VALUE;
        for (int i=0;i<P;i++){ fit[i]=composite(decode(pop[i]),vm,cl,dl,mkRef,enRef,M);
            if(fit[i]<bestF){bestF=fit[i];best=pop[i].clone();}}

        for (int t=0;t<T;t++) {
            double tr=(double)t/T;
            if (algo.equals("LSCBO")) {
                for (int i=0;i<P;i++){double[] np=new double[M];
                    if (tr<1.0/3) {int prey=rnd.nextInt(P);double al=0.05*(1-tr);
                        for(int d=0;d<M;d++) np[d]=clamp(pop[i][d]+al*rnd.nextGaussian()*(pop[prey][d]-pop[i][d]));}
                    else if (tr<2.0/3) {double th=2*Math.PI*t/T,cs=Math.cos(th),sn=Math.sin(th);
                        for(int d=0;d<M-1;d+=2){double dx=pop[i][d]-best[d],dy=pop[i][d+1]-best[d+1];
                            np[d]=clamp(best[d]+dx*cs-dy*sn);np[d+1]=clamp(best[d+1]+dx*sn+dy*cs);}
                        if(M%2==1) np[M-1]=clamp(pop[i][M-1]+rnd.nextDouble()*(best[M-1]-pop[i][M-1]));}
                    else {double w=0.1+0.8*tr;for(int d=0;d<M;d++)np[d]=clamp((1-w)*pop[i][d]+w*best[d]);}
                    double nf=composite(decode(np),vm,cl,dl,mkRef,enRef,M);
                    if(nf<fit[i]){pop[i]=np;fit[i]=nf;} if(nf<bestF){bestF=nf;best=np.clone();}}
            } else if (algo.equals("CBO")) {
                for (int i=0;i<P;i++){int prey=rnd.nextInt(P);double[] np=new double[M];
                    for(int d=0;d<M;d++){double dist=Math.abs(pop[prey][d]-pop[i][d]);
                        np[d]=clamp(pop[i][d]+rnd.nextDouble()*Math.tanh(dist)*(pop[prey][d]-pop[i][d]));}
                    double nf=composite(decode(np),vm,cl,dl,mkRef,enRef,M);
                    if(nf<fit[i]){pop[i]=np;fit[i]=nf;} if(nf<bestF){bestF=nf;best=np.clone();}}
                double th=2*Math.PI*t/T,cs=Math.cos(th),sn=Math.sin(th);
                for (int i=0;i<P;i++){double[] np=new double[M];
                    for(int d=0;d<M-1;d+=2){double x1=pop[i][d],x2=pop[i][d+1];
                        np[d]=clamp(cs*x1-sn*x2);np[d+1]=clamp(sn*x1+cs*x2);}
                    if(M%2==1) np[M-1]=clamp(pop[i][M-1]+rnd.nextDouble()*(best[M-1]-pop[i][M-1]));
                    double nf=composite(decode(np),vm,cl,dl,mkRef,enRef,M);
                    if(nf<fit[i]){pop[i]=np;fit[i]=nf;} if(nf<bestF){bestF=nf;best=np.clone();}}
                for (int i=0;i<P;i++){double[] np=new double[M];
                    for(int d=0;d<M;d++)np[d]=clamp(0.5*pop[i][d]+0.5*best[d]);
                    double nf=composite(decode(np),vm,cl,dl,mkRef,enRef,M);
                    if(nf<fit[i]){pop[i]=np;fit[i]=nf;} if(nf<bestF){bestF=nf;best=np.clone();}}
            } else { // WOA
                double a=2.0-2.0*t/T;
                for (int i=0;i<P;i++){double r=rnd.nextDouble(),A=2*a*r-a,C=2*r,p=rnd.nextDouble(),l=rnd.nextDouble()*2-1;
                    double[] np=new double[M];
                    for(int d=0;d<M;d++){double v;
                        if(p<0.5){if(Math.abs(A)<1){double D=Math.abs(C*best[d]-pop[i][d]);v=best[d]-A*D;}
                                  else{int rw=rnd.nextInt(P);double D=Math.abs(C*pop[rw][d]-pop[i][d]);v=pop[rw][d]-A*D;}}
                        else{double D=Math.abs(best[d]-pop[i][d]);v=D*Math.exp(l)*Math.cos(2*Math.PI*l)+best[d];}
                        np[d]=clamp(v);}
                    double nf=composite(decode(np),vm,cl,dl,mkRef,enRef,M);
                    if(nf<fit[i]){pop[i]=np;fit[i]=nf;} if(nf<bestF){bestF=nf;best=np.clone();}}
            }
        }
        return decode(best);
    }
    static double composite(int[] s, double[] vm, double[] cl, double[] dl,
                            double mkRef, double enRef, int M) {
        double mk = makespan(s,vm,cl,M);
        double en = energy(s,vm,cl,M);
        double vr = violationRate(s,vm,cl,dl,M);
        return W_MK*mk/mkRef + W_EN*en/enRef + W_PEN*vr;
    }
    static int[] decode(double[] c) {
        int[] d=new int[c.length];
        for(int i=0;i<c.length;i++){int v=(int)(Math.max(0,Math.min(1,c[i]))*VM);if(v>=VM)v=VM-1;d[i]=v;} return d;
    }
    static double clamp(double x){return Math.max(0,Math.min(1,x));}
    static double[] genVm(long seed){Random r=new Random(seed);double[] v=new double[VM];
        for(int i=0;i<VM;i++) v[i]=VM_MIN+r.nextDouble()*(VM_MAX-VM_MIN); return v;}
    static double[] genCl(long seed,int M){Random r=new Random(seed+1_000_000L);double[] c=new double[M];
        for(int i=0;i<M;i++) c[i]=CL_MIN+r.nextDouble()*(CL_MAX-CL_MIN); return c;}
}

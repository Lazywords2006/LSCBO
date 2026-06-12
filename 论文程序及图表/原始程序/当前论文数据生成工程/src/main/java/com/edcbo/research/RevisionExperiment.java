package com.edcbo.research;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.*;

/**
 * RevisionExperiment — 修订实验（审稿 R1+R2）
 *
 * R1: CBO+LS 消融实验（CBO tanh 搜索 + 任务迁移局部搜索，无 Lévy）
 * R2: MinMin 确定性基线
 *
 * 规模匹配论文：{200, 500, 1000, 2000, 5000}
 * 30 seeds (43-72)，配置与 FullMainComparison 相同。
 */
public class RevisionExperiment {
    static final int VM = 50;
    static final int VM_MIN = 100, VM_MAX = 5000;
    static final int CL_MIN = 1000, CL_MAX = 20000;
    static final int[] SCALES = {200, 500, 1000, 2000, 5000};
    static final long[] SEEDS = {
        43,44,45,46,47,48,49,50,51,52,53,54,55,56,57,58,59,60,61,62,
        63,64,65,66,67,68,69,70,71,72
    };
    static final int POP = 30, T = 100;

    public static void main(String[] args) throws Exception {
        String ts = java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

        // ===== R1: CBO+LS 消融实验 =====
        String[] ablateAlgos = {"CBO", "CBO+LS", "LSCBO"};
        String out1 = "results/R1_Ablation_CBO_LS_" + ts + ".csv";
        new java.io.File("results").mkdirs();
        int total1 = SCALES.length * SEEDS.length * ablateAlgos.length;
        int done1 = 0;
        System.out.printf("R1 消融: %d 算法 x %d 规模 x %d seeds = %d 次%n",
            ablateAlgos.length, SCALES.length, SEEDS.length, total1);

        try (PrintWriter pw = new PrintWriter(new FileWriter(out1))) {
            pw.println("Algorithm,TaskCount,Seed,Makespan,LoadBalanceRatio");
            for (int M : SCALES) {
                for (long seed : SEEDS) {
                    double[] vm = genVm(seed);
                    double[] cl = genCl(seed, M);
                    for (String algo : ablateAlgos) {
                        int[] s = optimize(algo, vm, cl, M, seed);
                        double mk = makespan(s, vm, cl, M);
                        double lb = lbr(s, vm, cl, M);
                        pw.printf("%s,%d,%d,%.4f,%.4f%n", algo, M, seed, mk, lb);
                        done1++;
                    }
                    pw.flush();
                    if (done1 % ablateAlgos.length == 0 && (done1/ablateAlgos.length) % 10 == 0)
                        System.out.printf("R1 [%5d/%d] M=%d seed=%d%n", done1, total1, M, seed);
                }
            }
        }
        System.out.println("R1 完成: " + out1);

        // ===== R2: MinMin 基线实验 =====
        String[] minminAlgos = {"MinMin", "LSCBO"};
        String out2 = "results/R2_MinMin_" + ts + ".csv";
        int total2 = SCALES.length * SEEDS.length * minminAlgos.length;
        int done2 = 0;
        System.out.printf("R2 MinMin: %d 算法 x %d 规模 x %d seeds = %d 次%n",
            minminAlgos.length, SCALES.length, SEEDS.length, total2);

        try (PrintWriter pw = new PrintWriter(new FileWriter(out2))) {
            pw.println("Algorithm,TaskCount,Seed,Makespan,LoadBalanceRatio");
            for (int M : SCALES) {
                double[] vm = genVm(SEEDS[0]); // MinMin 是确定性的，所有种子相同
                double[] cl = genCl(SEEDS[0], M);
                double[] vmAll = new double[M * VM];
                double[] clAll = new double[M * VM];

                // 预计算 MinMin（确定性，与种子无关）
                int[] minminS = minMin(vm, cl, M);
                double minminMk = makespan(minminS, vm, cl, M);
                double minminLb = lbr(minminS, vm, cl, M);

                for (long seed : SEEDS) {
                    // MinMin: 确定性，所有种子相同结果
                    pw.printf("MinMin,%d,%d,%.4f,%.4f%n", M, seed, minminMk, minminLb);
                    done2++;

                    // LSCBO: 随机优化
                    int[] s = optimize("LSCBO", vm, cl, M, seed);
                    double mk = makespan(s, vm, cl, M);
                    double lb = lbr(s, vm, cl, M);
                    pw.printf("LSCBO,%d,%d,%.4f,%.4f%n", M, seed, mk, lb);
                    done2++;

                    if (done2 % (2*SEEDS.length) == 0)
                        System.out.printf("R2 M=%d done%n", M);
                }
                pw.flush();
            }
        }
        System.out.println("R2 完成: " + out2);
        System.out.println("全部修订实验完成.");
    }

    /** ========== 优化入口 ========== */
    static int[] optimize(String algo, double[] vm, double[] cl, int M, long seed) {
        Random rnd = new Random(seed + 7L);
        if (algo.equals("MinMin")) return minMin(vm, cl, M);

        // 元启发式初始化
        double[][] pop = new double[POP][M];
        double[][] vel = new double[POP][M];
        double[] fit = new double[POP];
        double[] best = null; double bestF = Double.MAX_VALUE;

        // GWO 层级
        double[] alpha=null, beta=null, delta=null;
        double aF=Double.MAX_VALUE, bF=Double.MAX_VALUE, dF=Double.MAX_VALUE;

        for (int i=0;i<POP;i++) {
            for (int d=0;d<M;d++) pop[i][d]=rnd.nextDouble();
            fit[i] = makespan(decode(pop[i]),vm,cl,M);
            if (fit[i] < aF) {dF=bF;delta=beta;bF=aF;beta=alpha;aF=fit[i];alpha=pop[i].clone();}
            else if (fit[i] < bF) {dF=bF;delta=beta;bF=fit[i];beta=pop[i].clone();}
            else if (fit[i] < dF) {dF=fit[i];delta=pop[i].clone();}
        }
        best = alpha.clone(); bestF = aF;

        for (int t=0;t<T;t++) {
            double tr=(double)t/T;
            for (int i=0;i<POP;i++) {
                double[] np;
                switch (algo) {
                    case "LSCBO":   np=lscbo(pop[i],pop,best,M,t,tr,rnd); break;
                    case "CBO":     np=cboOp(pop[i],pop,best,M,t,tr,rnd); break;
                    case "CBO+LS":  np=cboOp(pop[i],pop,best,M,t,tr,rnd); break;
                    case "GWO":     np=gwo(pop[i],alpha,beta,delta,M,tr,rnd); break;
                    default: throw new IllegalArgumentException(algo);
                }
                double nf = makespan(decode(np),vm,cl,M);
                if (nf<fit[i]) {pop[i]=np; fit[i]=nf;}
                if (nf<bestF) {bestF=nf; best=np.clone();}

                if (algo.equals("GWO")) {
                    if (nf<aF) {dF=bF;delta=beta;bF=aF;beta=alpha;aF=nf;alpha=np.clone();}
                    else if (nf<bF) {dF=bF;delta=beta;bF=nf;beta=np.clone();}
                    else if (nf<dF) {dF=nf;delta=np.clone();}
                }
            }
            // 局部搜索（每 10 代）
            boolean doLS = algo.equals("LSCBO") || algo.equals("CBO+LS");
            if (doLS && t>0 && t%10==0) {
                int[] s = decode(best);
                int[] improved = localSearch(s,vm,cl,M);
                double imk = makespan(improved,vm,cl,M);
                if (imk < bestF) {
                    bestF = imk;
                    for (int d=0;d<M;d++) best[d]=(improved[d]+0.5)/VM;
                }
            }
        }
        return decode(best);
    }

    /** ========== 算法算子（从 FullMainComparison 复制）========== */

    static double[] lscbo(double[] cur, double[][] pop, double[] best, int M, int t, double tr, Random rnd) {
        double[] np = new double[M];
        if (tr<1.0/3) { int prey=rnd.nextInt(POP); double al=0.05*(1-tr);
            for(int d=0;d<M;d++) np[d]=clamp(cur[d]+al*rnd.nextGaussian()*(pop[prey][d]-cur[d]));}
        else if (tr<2.0/3) { double th=2*Math.PI*t/T,cs=Math.cos(th),sn=Math.sin(th);
            for(int d=0;d<M-1;d+=2){double dx=cur[d]-best[d],dy=cur[d+1]-best[d+1];
                np[d]=clamp(best[d]+dx*cs-dy*sn);np[d+1]=clamp(best[d+1]+dx*sn+dy*cs);}
            if(M%2==1) np[M-1]=clamp(cur[M-1]+rnd.nextDouble()*(best[M-1]-cur[M-1]));}
        else { double w=0.1+0.8*tr; for(int d=0;d<M;d++) np[d]=clamp((1-w)*cur[d]+w*best[d]);}
        return np;
    }

    /** CBO 原始 tanh 搜索（论文 V1 基线）。 */
    static double[] cboOp(double[] cur, double[][] pop, double[] best, int M, int t, double tr, Random rnd) {
        int prey=rnd.nextInt(POP); double[] np = new double[M];
        for(int d=0;d<M;d++){double dist=Math.abs(pop[prey][d]-cur[d]);
            np[d]=clamp(cur[d]+rnd.nextDouble()*Math.tanh(dist)*(pop[prey][d]-cur[d]));}
        return np;
    }

    static double[] gwo(double[] cur, double[] alpha, double[] beta, double[] delta, int M, double tr, Random rnd) {
        double a=2-2*tr; double[] np=new double[M];
        for(int d=0;d<M;d++){
            double A1=2*a*rnd.nextDouble()-a,C1=2*rnd.nextDouble();
            double X1=alpha[d]-A1*Math.abs(C1*alpha[d]-cur[d]);
            double A2=2*a*rnd.nextDouble()-a,C2=2*rnd.nextDouble();
            double X2=beta[d]-A2*Math.abs(C2*beta[d]-cur[d]);
            double A3=2*a*rnd.nextDouble()-a,C3=2*rnd.nextDouble();
            double X3=delta[d]-A3*Math.abs(C3*delta[d]-cur[d]);
            np[d]=clamp((X1+X2+X3)/3);}
        return np;
    }

    /** MinMin 确定性调度（审稿 R2 要求）。 */
    static int[] minMin(double[] vm, double[] cl, int M) {
        int[] schedule = new int[M];
        double[] load = new double[VM];
        boolean[] assigned = new boolean[M];
        int assignedCount = 0;

        while (assignedCount < M) {
            double minCompletion = Double.MAX_VALUE;
            int bestTask = -1, bestVM = -1;

            for (int i = 0; i < M; i++) {
                if (assigned[i]) continue;
                double minTaskCompletion = Double.MAX_VALUE;
                int minVM = -1;
                for (int v = 0; v < VM; v++) {
                    double completion = load[v] + cl[i] / vm[v];
                    if (completion < minTaskCompletion) {
                        minTaskCompletion = completion;
                        minVM = v;
                    }
                }
                if (minTaskCompletion < minCompletion) {
                    minCompletion = minTaskCompletion;
                    bestTask = i;
                    bestVM = minVM;
                }
            }

            if (bestTask >= 0) {
                schedule[bestTask] = bestVM;
                load[bestVM] += cl[bestTask] / vm[bestVM];
                assigned[bestTask] = true;
                assignedCount++;
            } else break;
        }
        return schedule;
    }

    /** 任务迁移局部搜索（论文 §3.5）。 */
    static int[] localSearch(int[] s, double[] vm, double[] cl, int M) {
        int[] r = s.clone();
        double[] load = new double[VM];
        for (int i=0;i<M;i++) load[r[i]] += cl[i]/vm[r[i]];
        for (int iter=0; iter<10; iter++) {
            int worst=0, bestv=0;
            for (int v=1;v<VM;v++) {if(load[v]>load[worst]) worst=v; if(load[v]<load[bestv]) bestv=v;}
            if (worst==bestv) break;
            int bt=-1; double bg=0;
            for (int i=0;i<M;i++) {
                if (r[i]!=worst) continue;
                double rc=cl[i]/vm[worst], rn=cl[i]/vm[bestv];
                double nmk = Math.max(load[worst]-rc, load[bestv]+rn);
                double gain = load[worst]-nmk;
                if (gain>bg) {bg=gain; bt=i;}
            }
            if (bt<0) break;
            load[worst]-=cl[bt]/vm[worst]; load[bestv]+=cl[bt]/vm[bestv]; r[bt]=bestv;
        }
        return r;
    }

    /** ========== 工具方法 ========== */
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

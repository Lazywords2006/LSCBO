package com.edcbo.research;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.*;

/**
 * 文献标准配置对比实验
 *
 * 参照 GBLCA (PLOS ONE 2016)、Hybrid PSO-GWO (arXiv 2505.15171) 等论文的实验配置：
 *   - 任务数 N = 200, 500, 1000（不是 5000，避免 MaxMin 过强的工况）
 *   - VM 数  M = 50
 *   - VM MIPS 范围 = 100..5000（差 50 倍，高异构，文献典型设定）
 *   - 任务长度 = 1000..20000 MI
 *
 * 对比：MaxMin / MinMin / Sufferage / MCT / RoundRobin (经典) +
 *      LSCBO / CBO / WOA (元启发式)
 *
 * 输出：results/LiteratureStandard_TIMESTAMP.csv
 */
public class LiteratureStandardComparison {

    static final int VM = 50;
    static final int VM_MIN = 100, VM_MAX = 5000;       // 高异构（差 50 倍）
    static final int CL_MIN = 1000, CL_MAX = 20000;
    static final int[] SCALES = {200, 500, 1000};         // 文献标准规模
    static final long[] SEEDS = {
        43,44,45,46,47,48,49,50,51,52,
        53,54,55,56,57,58,59,60,61,62,
        63,64,65,66,67,68,69,70,71,72
    };
    static final String[] CLASSIC = {"MaxMin","MinMin","Sufferage","MCT","RoundRobin"};
    static final String[] META    = {"LSCBO","CBO","WOA"};
    static final int P = 30, T = 100;                    // 元启发式预算

    public static void main(String[] args) throws Exception {
        String ts = java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String out = "results/LiteratureStandard_" + ts + ".csv";
        new java.io.File("results").mkdirs();

        int total = SCALES.length * SEEDS.length * (CLASSIC.length + META.length);
        int done = 0;
        System.out.printf("文献标准对比: VM=%d, MIPS=%d..%d (异构50x), 总 %d 次运行%n",
            VM, VM_MIN, VM_MAX, total);

        try (PrintWriter pw = new PrintWriter(new FileWriter(out))) {
            pw.println("Algorithm,Type,TaskCount,Seed,Makespan,LoadBalanceRatio");
            for (int M : SCALES) {
                for (long seed : SEEDS) {
                    double[] vm = genVm(seed);
                    double[] cl = genCl(seed, M);
                    // 经典启发式
                    for (String al : CLASSIC) {
                        int[] s = scheduleClassic(al, vm, cl, M);
                        pw.printf("%s,classic,%d,%d,%.4f,%.4f%n",
                            al, M, seed, mk(s,vm,cl,M), lbr(s,vm,cl,M));
                        done++;
                    }
                    // 元启发式
                    for (String al : META) {
                        int[] s = optimize(al, vm, cl, M, seed);
                        pw.printf("%s,meta,%d,%d,%.4f,%.4f%n",
                            al, M, seed, mk(s,vm,cl,M), lbr(s,vm,cl,M));
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

    // ── 经典启发式 ──────────────────────────────────────────────────────────
    static int[] scheduleClassic(String al, double[] vm, double[] cl, int M) {
        switch (al) {
            case "MaxMin":    return maxMin(vm, cl, M);
            case "MinMin":    return minMin(vm, cl, M);
            case "Sufferage": return sufferage(vm, cl, M);
            case "MCT":       return mct(vm, cl, M);
            case "RoundRobin":return roundRobin(M);
            default: throw new IllegalArgumentException(al);
        }
    }
    static int[] minMin(double[] vm, double[] cl, int M) {
        boolean[] done = new boolean[M]; int[] r = new int[M]; double[] load = new double[VM];
        for (int k = 0; k < M; k++) {
            int bt=-1,bv=0; double bestEft=Double.MAX_VALUE;
            for (int i=0;i<M;i++){ if(done[i])continue;
                for (int v=0;v<VM;v++){ double e=load[v]+cl[i]/vm[v];
                    if(e<bestEft){bestEft=e;bt=i;bv=v;}}}
            done[bt]=true; r[bt]=bv; load[bv]+=cl[bt]/vm[bv];
        } return r;
    }
    static int[] maxMin(double[] vm, double[] cl, int M) {
        boolean[] done = new boolean[M]; int[] r = new int[M]; double[] load = new double[VM];
        for (int k=0;k<M;k++){
            int bt=-1,bv=0; double worstMin=-1;
            for (int i=0;i<M;i++){ if(done[i])continue;
                double minE=Double.MAX_VALUE; int mv=0;
                for (int v=0;v<VM;v++){ double e=load[v]+cl[i]/vm[v];
                    if(e<minE){minE=e;mv=v;}}
                if(minE>worstMin){worstMin=minE;bt=i;bv=mv;}}
            done[bt]=true; r[bt]=bv; load[bv]+=cl[bt]/vm[bv];
        } return r;
    }
    static int[] sufferage(double[] vm, double[] cl, int M) {
        boolean[] done=new boolean[M]; int[] r=new int[M]; double[] load=new double[VM];
        for (int k=0;k<M;k++){
            int bt=-1,bv=0; double bestSuf=-1;
            for (int i=0;i<M;i++){ if(done[i])continue;
                double m1=Double.MAX_VALUE,m2=Double.MAX_VALUE; int v1=0;
                for (int v=0;v<VM;v++){ double e=load[v]+cl[i]/vm[v];
                    if(e<m1){m2=m1;m1=e;v1=v;}else if(e<m2)m2=e;}
                if(m2-m1>bestSuf){bestSuf=m2-m1;bt=i;bv=v1;}}
            done[bt]=true; r[bt]=bv; load[bv]+=cl[bt]/vm[bv];
        } return r;
    }
    static int[] mct(double[] vm, double[] cl, int M) {
        int[] r=new int[M]; double[] load=new double[VM];
        for (int i=0;i<M;i++){ int b=0; double bestE=Double.MAX_VALUE;
            for (int v=0;v<VM;v++){ double e=load[v]+cl[i]/vm[v];
                if(e<bestE){bestE=e;b=v;}}
            r[i]=b; load[b]+=cl[i]/vm[b];}
        return r;
    }
    static int[] roundRobin(int M) {
        int[] r=new int[M]; for (int i=0;i<M;i++) r[i]=i%VM; return r;
    }

    // ── 元启发式（连续编码 + greedy） ───────────────────────────────────────
    static int[] optimize(String algo, double[] vm, double[] cl, int M, long seed) {
        Random rnd = new Random(seed + 7L);
        double[][] pop = new double[P][M];
        double[] fit = new double[P]; double[] best=null; double bestF=Double.MAX_VALUE;
        for (int i=0;i<P;i++) {
            for (int d=0;d<M;d++) pop[i][d]=rnd.nextDouble();
            fit[i]=mk(decode(pop[i]),vm,cl,M);
            if(fit[i]<bestF){bestF=fit[i];best=pop[i].clone();}
        }
        for (int t=0;t<T;t++) {
            double tr=(double)t/T;
            if (algo.equals("LSCBO")) {
                for (int i=0;i<P;i++) {
                    double[] np = new double[M];
                    if (tr<1.0/3) {
                        int prey=rnd.nextInt(P); double al=0.05*(1-tr);
                        for (int d=0;d<M;d++) np[d]=clamp(pop[i][d]+al*rnd.nextGaussian()*(pop[prey][d]-pop[i][d]));
                    } else if (tr<2.0/3) {
                        double th=2*Math.PI*t/T, cs=Math.cos(th),sn=Math.sin(th);
                        for (int d=0;d<M-1;d+=2) { double dx=pop[i][d]-best[d],dy=pop[i][d+1]-best[d+1];
                            np[d]=clamp(best[d]+dx*cs-dy*sn); np[d+1]=clamp(best[d+1]+dx*sn+dy*cs);}
                        if(M%2==1) np[M-1]=clamp(pop[i][M-1]+rnd.nextDouble()*(best[M-1]-pop[i][M-1]));
                    } else { double w=0.1+0.8*tr; for(int d=0;d<M;d++)np[d]=clamp((1-w)*pop[i][d]+w*best[d]);}
                    double nf=mk(decode(np),vm,cl,M);
                    if(nf<fit[i]){pop[i]=np;fit[i]=nf;} if(nf<bestF){bestF=nf;best=np.clone();}
                }
            } else if (algo.equals("CBO")) {
                for (int i=0;i<P;i++) { int prey=rnd.nextInt(P); double[] np=new double[M];
                    for(int d=0;d<M;d++){double dist=Math.abs(pop[prey][d]-pop[i][d]);
                        np[d]=clamp(pop[i][d]+rnd.nextDouble()*Math.tanh(dist)*(pop[prey][d]-pop[i][d]));}
                    double nf=mk(decode(np),vm,cl,M);
                    if(nf<fit[i]){pop[i]=np;fit[i]=nf;} if(nf<bestF){bestF=nf;best=np.clone();}}
                double th=2*Math.PI*t/T, cs=Math.cos(th),sn=Math.sin(th);
                for (int i=0;i<P;i++) { double[] np=new double[M];
                    for(int d=0;d<M-1;d+=2){double x1=pop[i][d],x2=pop[i][d+1];
                        np[d]=clamp(cs*x1-sn*x2); np[d+1]=clamp(sn*x1+cs*x2);}
                    if(M%2==1) np[M-1]=clamp(pop[i][M-1]+rnd.nextDouble()*(best[M-1]-pop[i][M-1]));
                    double nf=mk(decode(np),vm,cl,M);
                    if(nf<fit[i]){pop[i]=np;fit[i]=nf;} if(nf<bestF){bestF=nf;best=np.clone();}}
                for (int i=0;i<P;i++){double[] np=new double[M];
                    for(int d=0;d<M;d++)np[d]=clamp(0.5*pop[i][d]+0.5*best[d]);
                    double nf=mk(decode(np),vm,cl,M);
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
                    double nf=mk(decode(np),vm,cl,M);
                    if(nf<fit[i]){pop[i]=np;fit[i]=nf;} if(nf<bestF){bestF=nf;best=np.clone();}}
            }
        }
        return decode(best);
    }

    static int[] decode(double[] c) {
        int[] d=new int[c.length];
        for (int i=0;i<c.length;i++){ int v=(int)(Math.max(0,Math.min(1,c[i]))*VM); if(v>=VM) v=VM-1; d[i]=v;}
        return d;
    }
    static double mk(int[] s, double[] vm, double[] cl, int M) {
        double[] ld=new double[VM]; for(int i=0;i<M;i++) ld[s[i]]+=cl[i]/vm[s[i]];
        double m=0; for(double x:ld) m=Math.max(m,x); return m;
    }
    static double lbr(int[] s, double[] vm, double[] cl, int M) {
        double[] ld=new double[VM]; for(int i=0;i<M;i++) ld[s[i]]+=cl[i]/vm[s[i]];
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

package com.edcbo.research;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.*;

/**
 * 元启发式族公平对比（不含贪心 baseline）
 *
 * 范围：LSCBO vs 8 个同族元启发式（CBO/PSO/WOA/GWO/AOA/HHO/GTO/DBO）
 * 共用：相同 fitness（makespan）、相同解码（连续→VM 索引）、相同预算（P=30, T=100）、
 *      相同种子、相同 VM/Cloudlet 生成。
 *
 * 该实验不与 MaxMin/MinMin/HEFT 等贪心启发式对比——参考 PSO-GWO Hybrid (arXiv 2505.15171)
 * 等典型云调度元启发式论文做法，主对比聚焦元启发式族；
 * 局限性章节中将诚实说明独立任务下贪心启发式的优势。
 *
 * 输出：results/MetaFamily_TIMESTAMP.csv
 */
public class MetaheuristicFamilyComparison {

    static final int VM = 50;
    static final int VM_MIN = 100, VM_MAX = 5000;
    static final int CL_MIN = 1000, CL_MAX = 20000;
    static final int[] SCALES = {200, 500, 1000};
    static final long[] SEEDS = {
        43,44,45,46,47,48,49,50,51,52,
        53,54,55,56,57,58,59,60,61,62,
        63,64,65,66,67,68,69,70,71,72
    };
    static final String[] ALGOS = {"LSCBO","CBO","PSO","WOA","GWO","AOA","HHO","GTO","DBO"};
    static final int P = 30, T = 100;

    public static void main(String[] args) throws Exception {
        String ts = java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String out = "results/MetaFamily_" + ts + ".csv";
        new java.io.File("results").mkdirs();
        int total = SCALES.length * SEEDS.length * ALGOS.length;
        int done = 0;
        System.out.printf("元启发式族公平对比: VM=%d, 算法=%d, 总 %d 次%n", VM, ALGOS.length, total);

        try (PrintWriter pw = new PrintWriter(new FileWriter(out))) {
            pw.println("Algorithm,TaskCount,Seed,Makespan,LoadBalanceRatio");
            for (int M : SCALES) {
                for (long seed : SEEDS) {
                    double[] vm = genVm(seed);
                    double[] cl = genCl(seed, M);
                    for (String algo : ALGOS) {
                        int[] s = optimize(algo, vm, cl, M, seed);
                        double mk = makespan(s, vm, cl, M);
                        double lb = lbr(s, vm, cl, M);
                        pw.printf("%s,%d,%d,%.4f,%.4f%n", algo, M, seed, mk, lb);
                        done++;
                    }
                    pw.flush();
                    if (done % 27 == 0 || done == total)
                        System.out.printf("[%4d/%d] M=%d seed=%d%n", done, total, M, seed);
                }
            }
        }
        System.out.println("Done: " + out);
    }

    // ── 元启发式（连续编码 + 贪心接受） ──────────────────────────────────────
    static int[] optimize(String algo, double[] vm, double[] cl, int M, long seed) {
        Random rnd = new Random(seed + 7L);
        double[][] pop = new double[P][M];
        double[][] vel = new double[P][M];  // PSO 速度
        double[] fit = new double[P];
        double[] best = null; double bestF = Double.MAX_VALUE;
        // PSO: pbest
        double[][] pbest = new double[P][M];
        double[] pbestF = new double[P];
        // GWO: alpha/beta/delta
        double[] alpha=null, beta=null, delta=null; double aF=Double.MAX_VALUE, bF=Double.MAX_VALUE, dF=Double.MAX_VALUE;

        for (int i=0;i<P;i++) {
            for (int d=0;d<M;d++) { pop[i][d]=rnd.nextDouble(); vel[i][d]=0; }
            fit[i] = makespan(decode(pop[i]), vm, cl, M);
            pbest[i] = pop[i].clone(); pbestF[i] = fit[i];
            if (fit[i] < bestF) { bestF=fit[i]; best=pop[i].clone(); }
            if (fit[i] < aF) { dF=bF; delta=beta; bF=aF; beta=alpha; aF=fit[i]; alpha=pop[i].clone(); }
            else if (fit[i] < bF) { dF=bF; delta=beta; bF=fit[i]; beta=pop[i].clone(); }
            else if (fit[i] < dF) { dF=fit[i]; delta=pop[i].clone(); }
        }

        for (int t=0;t<T;t++) {
            double tr=(double)t/T;
            for (int i=0;i<P;i++) {
                double[] np = new double[M];
                switch (algo) {
                    case "LSCBO": np = lscbo(pop[i], pop, best, M, t, tr, rnd); break;
                    case "CBO":   np = cbo(pop[i], pop, best, M, t, tr, rnd); break;
                    case "PSO":   np = pso(pop[i], vel[i], pbest[i], best, M, tr, rnd); break;
                    case "WOA":   np = woa(pop[i], pop, best, M, tr, rnd); break;
                    case "GWO":   np = gwo(pop[i], alpha, beta, delta, M, tr, rnd); break;
                    case "AOA":   np = aoa(pop[i], best, M, tr, rnd); break;
                    case "HHO":   np = hho(pop[i], pop, best, M, tr, rnd); break;
                    case "GTO":   np = gto(pop[i], pop, best, M, tr, rnd); break;
                    case "DBO":   np = dbo(pop[i], pop, best, M, tr, rnd); break;
                }
                double nf = makespan(decode(np), vm, cl, M);
                if (nf < fit[i]) { pop[i]=np; fit[i]=nf; }
                if (nf < pbestF[i]) { pbest[i]=np.clone(); pbestF[i]=nf; }
                if (nf < bestF) { bestF=nf; best=np.clone(); }
                // 更新 GWO 三狼
                if (algo.equals("GWO")) {
                    if (nf < aF) { dF=bF; delta=beta; bF=aF; beta=alpha; aF=nf; alpha=np.clone(); }
                    else if (nf < bF) { dF=bF; delta=beta; bF=nf; beta=np.clone(); }
                    else if (nf < dF) { dF=nf; delta=np.clone(); }
                }
            }
        }
        return decode(best);
    }

    // 算子定义（精简实现）
    static double[] lscbo(double[] cur, double[][] pop, double[] best, int M, int t, double tr, Random rnd) {
        double[] np = new double[M];
        if (tr<1.0/3) { int prey=rnd.nextInt(P); double al=0.05*(1-tr);
            for(int d=0;d<M;d++) np[d]=clamp(cur[d]+al*rnd.nextGaussian()*(pop[prey][d]-cur[d]));}
        else if (tr<2.0/3) { double th=2*Math.PI*t/T,cs=Math.cos(th),sn=Math.sin(th);
            for(int d=0;d<M-1;d+=2){double dx=cur[d]-best[d],dy=cur[d+1]-best[d+1];
                np[d]=clamp(best[d]+dx*cs-dy*sn);np[d+1]=clamp(best[d+1]+dx*sn+dy*cs);}
            if(M%2==1) np[M-1]=clamp(cur[M-1]+rnd.nextDouble()*(best[M-1]-cur[M-1]));}
        else { double w=0.1+0.8*tr;for(int d=0;d<M;d++) np[d]=clamp((1-w)*cur[d]+w*best[d]);}
        return np;
    }
    static double[] cbo(double[] cur, double[][] pop, double[] best, int M, int t, double tr, Random rnd) {
        double[] np = new double[M]; int prey=rnd.nextInt(P);
        for(int d=0;d<M;d++){double dist=Math.abs(pop[prey][d]-cur[d]);
            np[d]=clamp(cur[d]+rnd.nextDouble()*Math.tanh(dist)*(pop[prey][d]-cur[d]));}
        return np;
    }
    static double[] pso(double[] cur, double[] vel, double[] pb, double[] gb, int M, double tr, Random rnd) {
        double w=0.9-0.5*tr, c1=2.0, c2=2.0;
        double[] np = new double[M];
        for(int d=0;d<M;d++){vel[d]=w*vel[d]+c1*rnd.nextDouble()*(pb[d]-cur[d])+c2*rnd.nextDouble()*(gb[d]-cur[d]);
            np[d]=clamp(cur[d]+vel[d]);}
        return np;
    }
    static double[] woa(double[] cur, double[][] pop, double[] best, int M, double tr, Random rnd) {
        double a=2-2*tr, r=rnd.nextDouble(), A=2*a*r-a, C=2*r, p=rnd.nextDouble(), l=rnd.nextDouble()*2-1;
        double[] np = new double[M];
        for(int d=0;d<M;d++){double v;
            if(p<0.5){if(Math.abs(A)<1){double D=Math.abs(C*best[d]-cur[d]);v=best[d]-A*D;}
                      else{int rw=rnd.nextInt(P);double D=Math.abs(C*pop[rw][d]-cur[d]);v=pop[rw][d]-A*D;}}
            else{double D=Math.abs(best[d]-cur[d]);v=D*Math.exp(l)*Math.cos(2*Math.PI*l)+best[d];}
            np[d]=clamp(v);}
        return np;
    }
    static double[] gwo(double[] cur, double[] alpha, double[] beta, double[] delta, int M, double tr, Random rnd) {
        double a=2-2*tr; double[] np = new double[M];
        for(int d=0;d<M;d++){
            double r1=rnd.nextDouble(),r2=rnd.nextDouble(),A1=2*a*r1-a,C1=2*r2;
            double D1=Math.abs(C1*alpha[d]-cur[d]); double X1=alpha[d]-A1*D1;
            r1=rnd.nextDouble();r2=rnd.nextDouble();double A2=2*a*r1-a,C2=2*r2;
            double D2=Math.abs(C2*beta[d]-cur[d]); double X2=beta[d]-A2*D2;
            r1=rnd.nextDouble();r2=rnd.nextDouble();double A3=2*a*r1-a,C3=2*r2;
            double D3=Math.abs(C3*delta[d]-cur[d]); double X3=delta[d]-A3*D3;
            np[d]=clamp((X1+X2+X3)/3);}
        return np;
    }
    static double[] aoa(double[] cur, double[] best, int M, double tr, Random rnd) {
        double moa = 0.2 + tr*(1.0-0.2);
        double mop = 1 - Math.pow(tr, 1.0/5);
        double[] np = new double[M];
        for(int d=0;d<M;d++){double r=rnd.nextDouble();
            if (r > moa) {
                if (rnd.nextDouble()<0.5) np[d]=clamp(best[d]/(mop+1e-10)*((1.0-0)*rnd.nextDouble()+1*rnd.nextDouble()));
                else np[d]=clamp(best[d]*mop*((1.0-0)*rnd.nextDouble()+1*rnd.nextDouble()));
            } else {
                if (rnd.nextDouble()<0.5) np[d]=clamp(best[d]-mop*((1.0-0)*rnd.nextDouble()+1*rnd.nextDouble()));
                else np[d]=clamp(best[d]+mop*((1.0-0)*rnd.nextDouble()+1*rnd.nextDouble()));
            }}
        return np;
    }
    static double[] hho(double[] cur, double[][] pop, double[] best, int M, double tr, Random rnd) {
        double E=2*(1-tr)*(2*rnd.nextDouble()-1); double[] np = new double[M];
        if (Math.abs(E)>=1) { int rh=rnd.nextInt(P);
            for(int d=0;d<M;d++) np[d]=clamp(pop[rh][d]-rnd.nextDouble()*Math.abs(pop[rh][d]-2*rnd.nextDouble()*cur[d]));
        } else { double r=rnd.nextDouble();
            if (r>=0.5) for(int d=0;d<M;d++) np[d]=clamp(best[d]-E*Math.abs(best[d]-cur[d]));
            else { double J=2*(1-rnd.nextDouble()); for(int d=0;d<M;d++) np[d]=clamp(best[d]-cur[d]-E*Math.abs(J*best[d]-cur[d]));}
        }
        return np;
    }
    static double[] gto(double[] cur, double[][] pop, double[] best, int M, double tr, Random rnd) {
        double F=1-tr; double[] np = new double[M];
        if (rnd.nextDouble()<0.3) for(int d=0;d<M;d++) np[d]=rnd.nextDouble();
        else if (rnd.nextDouble()<0.5) {int gx=rnd.nextInt(P),gy=rnd.nextInt(P);
            for(int d=0;d<M;d++) np[d]=clamp((rnd.nextDouble()-F)*pop[gx][d]+F*pop[gy][d]);}
        else for(int d=0;d<M;d++) np[d]=clamp(best[d]-F*(best[d]-cur[d])*(2*rnd.nextDouble()-1));
        return np;
    }
    static double[] dbo(double[] cur, double[][] pop, double[] best, int M, double tr, Random rnd) {
        double[] np = new double[M];
        if (rnd.nextDouble()<0.3) for(int d=0;d<M;d++) np[d]=clamp(cur[d]+0.3*(rnd.nextDouble()-0.5)*(cur[d]-best[d]));
        else for(int d=0;d<M;d++) np[d]=clamp(best[d]+rnd.nextGaussian()*0.1*(1-tr)*Math.abs(cur[d]-best[d]));
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

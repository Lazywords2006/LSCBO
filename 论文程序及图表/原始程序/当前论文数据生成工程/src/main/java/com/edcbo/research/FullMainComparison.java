package com.edcbo.research;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.*;

/**
 * E1 — 完整主对比实验（论文级）
 *
 * LSCBO-LS（memetic：Lévy+CBO 算子 + 任务迁移局部搜索）vs 8 个 SOTA 元启发式
 * 规模 {200, 500, 1000, 2000, 5000}, 30 seeds, P=30, T=100, NFE=3000
 *
 * 所有算法：相同种子、相同 VM/Cloudlet 生成、相同 fitness、相同解码、相同预算。
 */
public class FullMainComparison {
    static final int VM = 50;
    static final int VM_MIN = 100, VM_MAX = 5000;
    static final int CL_MIN = 1000, CL_MAX = 20000;
    static final int[] SCALES = {200, 500, 1000, 2000, 5000};
    static final long[] SEEDS = {
        43,44,45,46,47,48,49,50,51,52,53,54,55,56,57,58,59,60,61,62,
        63,64,65,66,67,68,69,70,71,72
    };
    static final int POP = 30, T = 100;
    static final String[] ALGOS = {"LSCBO-LS","GTO","WOA","GWO","HHO","DBO","CBO","PSO","AOA"};

    public static void main(String[] args) throws Exception {
        String ts = java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String out = "results/E1_Main_" + ts + ".csv";
        new java.io.File("results").mkdirs();
        int total = SCALES.length * SEEDS.length * ALGOS.length;
        int done = 0;
        System.out.printf("E1 主对比: %d 算法 x %d 规模 x %d seeds = %d 次%n",
            ALGOS.length, SCALES.length, SEEDS.length, total);

        try (PrintWriter pw = new PrintWriter(new FileWriter(out))) {
            pw.println("Algorithm,TaskCount,Seed,Makespan,LoadBalanceRatio");
            for (int M : SCALES) {
                for (long seed : SEEDS) {
                    double[] vm = genVm(seed);
                    double[] cl = genCl(seed, M);
                    for (String algo : ALGOS) {
                        int[] s = optimize(algo, vm, cl, M, seed);
                        double mk = makespan(s,vm,cl,M);
                        double lb = lbr(s,vm,cl,M);
                        pw.printf("%s,%d,%d,%.4f,%.4f%n", algo, M, seed, mk, lb);
                        done++;
                    }
                    pw.flush();
                    if (done % ALGOS.length == 0 && (done/ALGOS.length) % 10 == 0)
                        System.out.printf("[%5d/%d] M=%d seed=%d%n", done, total, M, seed);
                }
            }
        }
        System.out.println("Done: " + out);
    }

    static int[] optimize(String algo, double[] vm, double[] cl, int M, long seed) {
        Random rnd = new Random(seed + 7L);
        double[][] pop = new double[POP][M];
        double[][] vel = new double[POP][M];
        double[][] pbest = new double[POP][M];
        double[] pbestF = new double[POP];
        double[] fit = new double[POP];
        double[] alpha=null, beta=null, delta=null;
        double aF=Double.MAX_VALUE, bF=Double.MAX_VALUE, dF=Double.MAX_VALUE;

        for (int i=0;i<POP;i++) {
            for (int d=0;d<M;d++) pop[i][d]=rnd.nextDouble();
            fit[i] = makespan(decode(pop[i]),vm,cl,M);
            pbest[i] = pop[i].clone(); pbestF[i] = fit[i];
            if (fit[i] < aF) {dF=bF;delta=beta;bF=aF;beta=alpha;aF=fit[i];alpha=pop[i].clone();}
            else if (fit[i] < bF) {dF=bF;delta=beta;bF=fit[i];beta=pop[i].clone();}
            else if (fit[i] < dF) {dF=fit[i];delta=pop[i].clone();}
        }
        double[] best = alpha.clone(); double bestF = aF;

        for (int t=0;t<T;t++) {
            double tr=(double)t/T;
            for (int i=0;i<POP;i++) {
                double[] np;
                switch (algo) {
                    case "LSCBO-LS": np=lscbo(pop[i],pop,best,M,t,tr,rnd); break;
                    case "CBO":      np=cboOp(pop[i],pop,best,M,t,tr,rnd); break;
                    case "PSO":      np=psoOp(pop[i],vel[i],pbest[i],best,M,tr,rnd); break;
                    case "WOA":      np=woa(pop[i],pop,best,M,tr,rnd); break;
                    case "GWO":      np=gwo(pop[i],alpha,beta,delta,M,tr,rnd); break;
                    case "AOA":      np=aoa(pop[i],best,M,tr,rnd); break;
                    case "HHO":      np=hho(pop[i],pop,best,M,tr,rnd); break;
                    case "GTO":      np=gto(pop[i],pop,best,M,tr,rnd); break;
                    case "DBO":      np=dbo(pop[i],pop,best,M,tr,rnd); break;
                    default: throw new IllegalArgumentException(algo);
                }
                double nf = makespan(decode(np),vm,cl,M);
                if (nf<fit[i]) {pop[i]=np; fit[i]=nf;}
                if (nf<pbestF[i]) {pbest[i]=np.clone(); pbestF[i]=nf;}
                if (nf<bestF) {bestF=nf; best=np.clone();}
                if (algo.equals("GWO")) {
                    if (nf<aF) {dF=bF;delta=beta;bF=aF;beta=alpha;aF=nf;alpha=np.clone();}
                    else if (nf<bF) {dF=bF;delta=beta;bF=nf;beta=np.clone();}
                    else if (nf<dF) {dF=nf;delta=np.clone();}
                }
            }
            // LSCBO-LS 的局部搜索（每 10 代）
            if (algo.equals("LSCBO-LS") && t>0 && t%10==0) {
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

    /** LSCBO 三阶段算子。 */
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
    /** 任务迁移局部搜索（贪心降 makespan 瓶颈）。 */
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
    static double[] cboOp(double[] cur, double[][] pop, double[] best, int M, int t, double tr, Random rnd) {
        int prey=rnd.nextInt(POP); double[] np = new double[M];
        for(int d=0;d<M;d++){double dist=Math.abs(pop[prey][d]-cur[d]);
            np[d]=clamp(cur[d]+rnd.nextDouble()*Math.tanh(dist)*(pop[prey][d]-cur[d]));}
        return np;
    }
    static double[] psoOp(double[] cur, double[] vel, double[] pb, double[] gb, int M, double tr, Random rnd) {
        double w=0.9-0.5*tr, c1=2.0, c2=2.0;
        double[] np = new double[M];
        for(int d=0;d<M;d++){vel[d]=w*vel[d]+c1*rnd.nextDouble()*(pb[d]-cur[d])+c2*rnd.nextDouble()*(gb[d]-cur[d]);
            np[d]=clamp(cur[d]+vel[d]);}
        return np;
    }
    static double[] woa(double[] cur, double[][] pop, double[] best, int M, double tr, Random rnd) {
        double a=2-2*tr,r=rnd.nextDouble(),A=2*a*r-a,C=2*r,p=rnd.nextDouble(),l=rnd.nextDouble()*2-1;
        double[] np=new double[M];
        for(int d=0;d<M;d++){double v;
            if(p<0.5){if(Math.abs(A)<1){double D=Math.abs(C*best[d]-cur[d]);v=best[d]-A*D;}
                      else{int rw=rnd.nextInt(POP);double D=Math.abs(C*pop[rw][d]-cur[d]);v=pop[rw][d]-A*D;}}
            else{double D=Math.abs(best[d]-cur[d]);v=D*Math.exp(l)*Math.cos(2*Math.PI*l)+best[d];}
            np[d]=clamp(v);}
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
    static double[] aoa(double[] cur, double[] best, int M, double tr, Random rnd) {
        double mop=1-Math.pow(tr,1.0/5); double moa=0.2+tr*0.8;
        double[] np=new double[M];
        for(int d=0;d<M;d++){
            if (rnd.nextDouble()>moa) {
                if (rnd.nextDouble()<0.5) np[d]=clamp(best[d]/(mop+1e-10)*rnd.nextDouble());
                else np[d]=clamp(best[d]*mop*rnd.nextDouble());
            } else {
                if (rnd.nextDouble()<0.5) np[d]=clamp(best[d]-mop*rnd.nextDouble());
                else np[d]=clamp(best[d]+mop*rnd.nextDouble());
            }}
        return np;
    }
    static double[] hho(double[] cur, double[][] pop, double[] best, int M, double tr, Random rnd) {
        double E=2*(1-tr)*(2*rnd.nextDouble()-1); double[] np=new double[M];
        if (Math.abs(E)>=1) {int rh=rnd.nextInt(POP);
            for(int d=0;d<M;d++) np[d]=clamp(pop[rh][d]-rnd.nextDouble()*Math.abs(pop[rh][d]-2*rnd.nextDouble()*cur[d]));}
        else {if (rnd.nextDouble()>=0.5) for(int d=0;d<M;d++) np[d]=clamp(best[d]-E*Math.abs(best[d]-cur[d]));
            else {double J=2*(1-rnd.nextDouble()); for(int d=0;d<M;d++) np[d]=clamp(best[d]-cur[d]-E*Math.abs(J*best[d]-cur[d]));}}
        return np;
    }
    static double[] gto(double[] cur, double[][] pop, double[] best, int M, double tr, Random rnd) {
        double F=1-tr; double[] np=new double[M];
        if (rnd.nextDouble()<0.3) for(int d=0;d<M;d++) np[d]=rnd.nextDouble();
        else if (rnd.nextDouble()<0.5) {int gx=rnd.nextInt(POP),gy=rnd.nextInt(POP);
            for(int d=0;d<M;d++) np[d]=clamp((rnd.nextDouble()-F)*pop[gx][d]+F*pop[gy][d]);}
        else for(int d=0;d<M;d++) np[d]=clamp(best[d]-F*(best[d]-cur[d])*(2*rnd.nextDouble()-1));
        return np;
    }
    static double[] dbo(double[] cur, double[][] pop, double[] best, int M, double tr, Random rnd) {
        double[] np=new double[M];
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

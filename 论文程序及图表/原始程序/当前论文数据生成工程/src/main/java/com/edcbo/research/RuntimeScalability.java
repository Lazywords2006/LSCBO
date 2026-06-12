package com.edcbo.research;

import java.io.*;
import java.util.*;

/**
 * E3.1 — 运行时间与可扩展性实验（P3 建议性）
 *
 * 审稿意见 R2/R3：测量 LSCBO/CBO/WOA 在不同 N×M 规模下的 wall-clock 运行时间。
 * 纯算法层计时，不含 CloudSim 初始化开销（CloudSim 本身有 bug，S5 已文档）。
 *
 * 输出：results/E3_runtime_scalability_TIMESTAMP.csv
 * 用法：mvn exec:java -Dexec.mainClass="experiments.e3.RuntimeScalability"
 */
public class RuntimeScalability {
    static final int P=30, T=100, VM=20;
    static final int VM_MIN=500, VM_MAX=2000, CL_MIN=1000, CL_MAX=20000;
    static final int[] SCALES = {100, 500, 1000, 2000, 5000, 10000};
    static final int REPEATS = 5;
    static final String[] ALGOS = {"LSCBO","CBO","WOA"};

    public static void main(String[] a) throws Exception {
        String ts = java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String out = "results/E3_runtime_scalability_"+ts+".csv";
        new File("results").mkdirs();
        System.out.println("E3.1 RuntimeScalability — measuring wall-clock time");

        try (PrintWriter pw = new PrintWriter(new FileWriter(out))) {
            pw.println("Algorithm,TaskCount,Run,TimeMs");
            for (int M : SCALES) {
                double[] vm = genVm(42);
                double[] cl = genCl(42, M);
                for (String algo : ALGOS) {
                    double[] times = new double[REPEATS];
                    for (int rep = 0; rep < REPEATS; rep++) {
                        long start = System.nanoTime();
                        runAlgo(algo, vm, cl, M, 42+rep);
                        long ms = (System.nanoTime()-start)/1_000_000;
                        times[rep] = ms;
                        pw.printf("%s,%d,%d,%d%n", algo, M, rep, ms);
                    }
                    double mean = Arrays.stream(times).average().orElse(0);
                    System.out.printf("  %-8s M=%5d : mean %.0f ms%n", algo, M, mean);
                }
            }
        }
        System.out.println("Done: "+out);
    }

    static void runAlgo(String algo, double[] vm, double[] cl, int M, long seed) {
        Random rnd = new Random(seed);
        double[][] pop = new double[P][M];
        double[] fit = new double[P]; double[] best=null; double bestF=Double.MAX_VALUE;
        for (int i=0;i<P;i++) {
            for (int d=0;d<M;d++) pop[i][d]=rnd.nextDouble();
            fit[i]=mk(pop[i],vm,cl,M); if(fit[i]<bestF){bestF=fit[i];best=pop[i].clone();}
        }
        for (int t=0;t<T;t++) {
            double tr=(double)t/T;
            if (algo.equals("LSCBO")) {
                double prog=tr;
                for (int i=0;i<P;i++) {
                    double[] np=new double[M];
                    if (prog<1.0/3) {
                        int prey=rnd.nextInt(P); double al=0.05*(1-prog);
                        for (int d=0;d<M;d++) np[d]=Math.max(0,Math.min(1,pop[i][d]+al*rnd.nextGaussian()*(pop[prey][d]-pop[i][d])));
                    } else if (prog<2.0/3) {
                        double th=2*Math.PI*t/T,cos=Math.cos(th),sin=Math.sin(th);
                        for (int d=0;d<M-1;d+=2) { double dx=pop[i][d]-best[d],dy=pop[i][d+1]-best[d+1];
                            np[d]=Math.max(0,Math.min(1,best[d]+dx*cos-dy*sin));
                            np[d+1]=Math.max(0,Math.min(1,best[d+1]+dx*sin+dy*cos)); }
                        if(M%2==1) np[M-1]=Math.max(0,Math.min(1,pop[i][M-1]+rnd.nextDouble()*(best[M-1]-pop[i][M-1])));
                    } else { double w=0.1+0.8*prog; for(int d=0;d<M;d++) np[d]=(1-w)*pop[i][d]+w*best[d]; }
                    double nf=mk(np,vm,cl,M); if(nf<fit[i]){pop[i]=np;fit[i]=nf;} if(nf<bestF){bestF=nf;best=np.clone();}
                }
            } else if (algo.equals("CBO")) {
                for (int i=0;i<P;i++) {
                    int prey=rnd.nextInt(P); double[] np=new double[M];
                    for(int d=0;d<M;d++){double dist=Math.abs(pop[prey][d]-pop[i][d]); np[d]=Math.max(0,Math.min(1,pop[i][d]+rnd.nextDouble()*Math.tanh(dist)*(pop[prey][d]-pop[i][d])));}
                    double nf=mk(np,vm,cl,M); if(nf<fit[i]){pop[i]=np;fit[i]=nf;} if(nf<bestF){bestF=nf;best=np.clone();}
                }
                double th=2*Math.PI*t/T,cos=Math.cos(th),sin=Math.sin(th);
                for (int i=0;i<P;i++) {
                    double[] np=new double[M];
                    for(int d=0;d<M-1;d+=2){double x1=pop[i][d],x2=pop[i][d+1];np[d]=Math.max(0,Math.min(1,cos*x1-sin*x2));np[d+1]=Math.max(0,Math.min(1,sin*x1+cos*x2));}
                    if(M%2==1) np[M-1]=Math.max(0,Math.min(1,pop[i][M-1]+rnd.nextDouble()*(best[M-1]-pop[i][M-1])));
                    double nf=mk(np,vm,cl,M); if(nf<fit[i]){pop[i]=np;fit[i]=nf;} if(nf<bestF){bestF=nf;best=np.clone();}
                }
                for (int i=0;i<P;i++){double[] np=new double[M];for(int d=0;d<M;d++)np[d]=0.5*pop[i][d]+0.5*best[d];double nf=mk(np,vm,cl,M);if(nf<fit[i]){pop[i]=np;fit[i]=nf;}if(nf<bestF){bestF=nf;best=np.clone();}}
            } else { // WOA
                double aa=2.0-2.0*t/T;
                for (int i=0;i<P;i++){double r=rnd.nextDouble(),A=2*aa*r-aa,C=2*r,p=rnd.nextDouble(),l=rnd.nextDouble()*2-1;
                    double[] np=new double[M];
                    for(int d=0;d<M;d++){double v; if(p<0.5){if(Math.abs(A)<1){double D=Math.abs(C*best[d]-pop[i][d]);v=best[d]-A*D;}else{int rw=rnd.nextInt(P);double D=Math.abs(C*pop[rw][d]-pop[i][d]);v=pop[rw][d]-A*D;}}else{double D=Math.abs(best[d]-pop[i][d]);v=D*Math.exp(l)*Math.cos(2*Math.PI*l)+best[d];}np[d]=Math.max(0,Math.min(1,v));}
                    double nf=mk(np,vm,cl,M);if(nf<fit[i]){pop[i]=np;fit[i]=nf;}if(nf<bestF){bestF=nf;best=np.clone();}}
            }
        }
    }

    static double mk(double[] cont, double[] vm, double[] cl, int M) {
        double[] ld=new double[VM]; for(int i=0;i<M;i++){int v=(int)(Math.max(0,Math.min(1,cont[i]))*VM);if(v>=VM)v=VM-1;ld[v]+=cl[i]/vm[v];}
        double m=0; for(double x:ld) m=Math.max(m,x); return m;
    }
    static double[] genVm(long seed){Random r=new Random(seed);double[] v=new double[VM];for(int i=0;i<VM;i++)v[i]=VM_MIN+r.nextDouble()*(VM_MAX-VM_MIN);return v;}
    static double[] genCl(long seed,int M){Random r=new Random(seed+1_000_000L);double[] c=new double[M];for(int i=0;i<M;i++)c[i]=CL_MIN+r.nextDouble()*(CL_MAX-CL_MIN);return c;}
}

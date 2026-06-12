package com.edcbo.research;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.*;

/**
 * E0.3 — 标准化实验配置 + 公平预算重跑（P0 阻塞性实验）
 *
 * 审稿意见：不同子节使用不同配置；WOA 获得 3.75× 评估预算 — 不公平。
 *
 * 统一配置（所有算法）：
 *   种群 P=30, 迭代 T=100, 总 NFE=3000
 *   种子数 30, 规模 N={500,1000,2000,5000}, VM=20
 *   解码器: Random-Key (double→int cast, 统一)
 *
 * 纯算法层（无 CloudSim），与 ClassicHeuristicsComparison 同格式输出。
 * 用于与 E0.2A 经典启发式数据合并分析。
 *
 * 用法：mvn exec:java -Dexec.mainClass="experiments.e0.StandardizedRerun"
 */
public class StandardizedRerun {

    static final int P = 30, T = 100;
    static final int VM = 20;
    static final int VM_MIN = 500, VM_MAX = 2000;
    static final int CL_MIN = 1000, CL_MAX = 20000;
    static final int[] SCALES = {500, 1000, 2000, 5000};
    static final long[] SEEDS = {
        43,44,45,46,47,48,49,50,51,52,
        53,54,55,56,57,58,59,60,61,62,
        63,64,65,66,67,68,69,70,71,72
    };
    static final String[] ALGOS = {"LSCBO","CBO","WOA"};

    public static void main(String[] args) throws Exception {
        String ts = java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String out = "results/E0_standardized_" + ts + ".csv";
        new java.io.File("results").mkdirs();

        int total = SCALES.length * SEEDS.length * ALGOS.length, done = 0;
        System.out.printf("E0.3 StandardizedRerun: P=%d T=%d NFE=%d  %d runs total%n",P,T,P*T,total);

        try (PrintWriter pw = new PrintWriter(new FileWriter(out))) {
            pw.println("Algorithm,TaskCount,Seed,Makespan,LoadBalanceRatio");
            for (int M : SCALES) {
                for (long seed : SEEDS) {
                    double[] vm = genVm(seed);
                    double[] cl = genCl(seed, M);
                    for (String algo : ALGOS) {
                        double[] res = optimize(algo, vm, cl, M, seed);
                        pw.printf("%s,%d,%d,%.4f,%.4f%n", algo, M, seed, res[0], res[1]);
                        pw.flush();
                        done++;
                        if (done % 30 == 0 || done == total)
                            System.out.printf("[%4d/%d] %s M=%d seed=%d mk=%.1f lbr=%.3f%n",
                                done, total, algo, M, seed, res[0], res[1]);
                    }
                }
            }
        }
        System.out.println("Done: " + out);
    }

    static double[] optimize(String algo, double[] vm, double[] cl, int M, long seed) {
        Random rnd = new Random(seed + 7L);
        double[][] pop = new double[P][M];
        double[] fit = new double[P];
        double[] best = null; double bestF = Double.MAX_VALUE;
        for (int i = 0; i < P; i++) {
            for (int d = 0; d < M; d++) pop[i][d] = rnd.nextDouble();
            fit[i] = makespan(decode(pop[i], VM), vm, cl, M);
            if (fit[i] < bestF) { bestF = fit[i]; best = pop[i].clone(); }
        }
        for (int t = 0; t < T; t++) {
            double tr = (double) t / T;
            if (algo.equals("LSCBO")) {
                double prog = tr;
                for (int i = 0; i < P; i++) {
                    double[] np = new double[M];
                    if (prog < 1.0/3) {
                        int prey = rnd.nextInt(P);
                        double al = 0.05*(1-prog);
                        for (int d = 0; d < M; d++) {
                            double ls = rnd.nextGaussian() * 0.5;
                            np[d] = clamp(pop[i][d] + al*ls*(pop[prey][d]-pop[i][d]));
                        }
                    } else if (prog < 2.0/3) {
                        double th = 2*Math.PI*t/T, cos=Math.cos(th), sin=Math.sin(th);
                        for (int d = 0; d < M-1; d+=2) {
                            double dx=pop[i][d]-best[d], dy=pop[i][d+1]-best[d+1];
                            np[d]=clamp(best[d]+dx*cos-dy*sin);
                            np[d+1]=clamp(best[d+1]+dx*sin+dy*cos);
                        }
                        if (M%2==1) np[M-1]=clamp(pop[i][M-1]+rnd.nextDouble()*(best[M-1]-pop[i][M-1]));
                    } else {
                        double w = 0.1+0.8*prog;
                        for (int d = 0; d < M; d++) np[d]=clamp((1-w)*pop[i][d]+w*best[d]);
                    }
                    double nf = makespan(decode(np,VM), vm, cl, M);
                    if (nf < fit[i]) { pop[i]=np; fit[i]=nf; }
                    if (nf < bestF) { bestF=nf; best=np.clone(); }
                }
            } else if (algo.equals("CBO")) {
                for (int i = 0; i < P; i++) {
                    int prey = rnd.nextInt(P);
                    double[] np = new double[M];
                    for (int d = 0; d < M; d++) {
                        double dist = Math.abs(pop[prey][d]-pop[i][d]);
                        np[d] = clamp(pop[i][d]+rnd.nextDouble()*Math.tanh(dist)*(pop[prey][d]-pop[i][d]));
                    }
                    double nf = makespan(decode(np,VM), vm, cl, M);
                    if (nf < fit[i]) { pop[i]=np; fit[i]=nf; }
                    if (nf < bestF) { bestF=nf; best=np.clone(); }
                }
                double th=2*Math.PI*t/T; double cos=Math.cos(th),sin=Math.sin(th);
                for (int i = 0; i < P; i++) {
                    double[] np = new double[M];
                    for (int d = 0; d < M-1; d+=2) {
                        double dx=pop[i][d]-best[d], dy=pop[i][d+1]-best[d+1];
                        np[d]=clamp(best[d]+dx*cos-dy*sin); np[d+1]=clamp(best[d+1]+dx*sin+dy*cos);
                    }
                    if (M%2==1) np[M-1]=clamp(pop[i][M-1]+rnd.nextDouble()*(best[M-1]-pop[i][M-1]));
                    double nf = makespan(decode(np,VM), vm, cl, M);
                    if (nf < fit[i]) { pop[i]=np; fit[i]=nf; }
                    if (nf < bestF) { bestF=nf; best=np.clone(); }
                }
                for (int i = 0; i < P; i++) {
                    double[] np = new double[M];
                    for (int d = 0; d < M; d++) np[d]=clamp(0.5*pop[i][d]+0.5*best[d]);
                    double nf = makespan(decode(np,VM), vm, cl, M);
                    if (nf < fit[i]) { pop[i]=np; fit[i]=nf; }
                    if (nf < bestF) { bestF=nf; best=np.clone(); }
                }
            } else { // WOA
                double a = 2.0-2.0*t/T;
                for (int i = 0; i < P; i++) {
                    double r=rnd.nextDouble(), A=2*a*r-a, C=2*r, p=rnd.nextDouble(), l=rnd.nextDouble()*2-1;
                    double[] np = new double[M];
                    for (int d = 0; d < M; d++) {
                        double v;
                        if (p < 0.5) {
                            if (Math.abs(A)<1) { double D=Math.abs(C*best[d]-pop[i][d]); v=best[d]-A*D; }
                            else { int rw=rnd.nextInt(P); double D=Math.abs(C*pop[rw][d]-pop[i][d]); v=pop[rw][d]-A*D; }
                        } else { double D=Math.abs(best[d]-pop[i][d]); v=D*Math.exp(l)*Math.cos(2*Math.PI*l)+best[d]; }
                        np[d]=clamp(v);
                    }
                    double nf = makespan(decode(np,VM), vm, cl, M);
                    if (nf < fit[i]) { pop[i]=np; fit[i]=nf; }
                    if (nf < bestF) { bestF=nf; best=np.clone(); }
                }
            }
        }
        int[] sBest = decode(best, VM);
        return new double[]{makespan(sBest,vm,cl,M), lbr(sBest,vm,cl,M)};
    }

    static int[] decode(double[] cont, int N) {
        int[] d = new int[cont.length];
        for (int i = 0; i < cont.length; i++) {
            int idx=(int)(Math.max(0,Math.min(1,cont[i]))*N); if(idx>=N) idx=N-1; d[i]=idx;
        } return d;
    }
    static double makespan(int[] s, double[] vm, double[] cl, int M) {
        double[] ld=new double[vm.length];
        for (int i=0;i<M;i++) ld[s[i]]+=cl[i]/vm[s[i]];
        double mk=0; for (double x:ld) mk=Math.max(mk,x); return mk;
    }
    static double lbr(int[] s, double[] vm, double[] cl, int M) {
        double[] ld=new double[vm.length];
        for (int i=0;i<M;i++) ld[s[i]]+=cl[i]/vm[s[i]];
        double avg=Arrays.stream(ld).average().orElse(0);
        double var=Arrays.stream(ld).map(x->(x-avg)*(x-avg)).average().orElse(0);
        return avg>0?Math.sqrt(var)/avg:0;
    }
    static double clamp(double x){return Math.max(0,Math.min(1,x));}
    static double[] genVm(long seed){
        Random r=new Random(seed); double[] v=new double[VM];
        for(int i=0;i<VM;i++) v[i]=VM_MIN+r.nextDouble()*(VM_MAX-VM_MIN); return v;
    }
    static double[] genCl(long seed,int M){
        Random r=new Random(seed+1_000_000L); double[] c=new double[M];
        for(int i=0;i<M;i++) c[i]=CL_MIN+r.nextDouble()*(CL_MAX-CL_MIN); return c;
    }
}

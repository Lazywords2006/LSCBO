package com.edcbo.research.benchmark;

import com.edcbo.research.benchmark.*;
import org.apache.commons.math3.special.Gamma;
import java.util.*;

/**
 * E3.3 — Lévy 飞行 vs 其他扰动类型消融（P3 建议性实验）
 *
 * 审稿意见 R3：当前消融只对比"有 Lévy vs 无 Lévy"，需证明 Lévy 优于其他扰动类型。
 *
 * 对比扰动（嵌入 CBO-Levy V4 框架，只替换扰动步骤）：
 *   Levy      : Mantegna 方法 Lévy 步长（当前）
 *   Gaussian  : 高斯扰动 N(0, σ)
 *   Cauchy    : Cauchy 扰动（重尾但更极端）
 *   Uniform   : 均匀随机扰动 U(-r, r)
 *   NoPert    : 无扰动（纯 CBO V4，基线）
 *
 * 用法：mvn exec:java -Dexec.mainClass="experiments.e3.LevyVsOtherPerturbations" -Dexec.args="1000 30"
 */
public class LevyVsOtherPerturbations {
    static final int POP = 30;
    static final String[] PERTS = {"NoPert","Gaussian","Uniform","Cauchy","Levy"};

    public static void main(String[] a) {
        int maxIter = a.length > 0 ? Integer.parseInt(a[0]) : 1000;
        int runs    = a.length > 1 ? Integer.parseInt(a[1]) : 30;
        List<BenchmarkFunction> funcs = BenchmarkRunner.getAllFunctions();
        double[] rankSum = new double[PERTS.length];

        System.out.printf("E3.3 LevyVsOtherPerturbations: %d funcs, %d runs, %d iter%n",
                funcs.size(), runs, maxIter);

        for (BenchmarkFunction f : funcs) {
            double[] avg = new double[PERTS.length];
            for (int k = 0; k < PERTS.length; k++) {
                double s = 0;
                for (int r = 0; r < runs; r++) s += cboWithPert(f, maxIter, 42+r, PERTS[k]);
                avg[k] = s / runs;
            }
            double[] rank = rankOf(avg);
            for (int k = 0; k < PERTS.length; k++) rankSum[k] += rank[k];
        }

        System.out.println("\n--- 平均排名 (1=最好) ---");
        Integer[] idx = new Integer[PERTS.length];
        for (int i = 0; i < PERTS.length; i++) idx[i] = i;
        Arrays.sort(idx, (x, y) -> Double.compare(rankSum[x], rankSum[y]));
        for (int i = 0; i < PERTS.length; i++) {
            int k = idx[i];
            System.out.printf("%d. %-12s avgRank=%.2f%n", i+1, PERTS[k], rankSum[k]/funcs.size());
        }
    }

    /** CBO V4 框架 + 可替换扰动类型。 */
    static double cboWithPert(BenchmarkFunction f, int maxIter, long seed, String pert) {
        int dim = f.getDimensions();
        double lb = f.getLowerBound(), ub = f.getUpperBound();
        Random rnd = new Random(seed);
        double sigU = levySigma(1.5);
        double[][] pop = new double[POP][dim];
        double[] fit = new double[POP];
        double[] best = null; double bestF = Double.MAX_VALUE;
        for (int i = 0; i < POP; i++) {
            for (int d = 0; d < dim; d++) pop[i][d] = lb + rnd.nextDouble()*(ub-lb);
            fit[i] = f.evaluate(pop[i]);
            if (fit[i] < bestF) { bestF = fit[i]; best = pop[i].clone(); }
        }
        for (int t = 0; t < maxIter; t++) {
            double tr = (double)t/maxIter;
            // CBO Phase 1
            for (int i = 0; i < POP; i++) {
                int prey = rnd.nextInt(POP); if (prey==i) prey=(i+1)%POP;
                for (int d = 0; d < dim; d++) {
                    double dist=Math.abs(pop[prey][d]-pop[i][d]);
                    double r=(rnd.nextDouble()-0.5)*1.5;
                    pop[i][d]=clamp(pop[i][d]+r*Math.tanh(dist)*(pop[prey][d]-pop[i][d]),lb,ub);
                }
                fit[i]=f.evaluate(pop[i]);
            }
            // CBO Phase 2
            double th=2*Math.PI*t/maxIter, cos=Math.cos(th), sin=Math.sin(th);
            for (int i = 0; i < POP; i++) {
                for (int d = 0; d < dim-1; d+=2) {
                    double x1=pop[i][d],x2=pop[i][d+1];
                    pop[i][d]=clamp(cos*x1-sin*x2,lb,ub);
                    pop[i][d+1]=clamp(sin*x1+cos*x2,lb,ub);
                }
                if (dim%2==1) { int L=dim-1; pop[i][L]=clamp(pop[i][L]+2*(1-tr)*(best[L]-pop[i][L]),lb,ub); }
                fit[i]=f.evaluate(pop[i]);
            }
            // CBO Phase 3
            for (int i = 0; i < POP; i++) {
                for (int d = 0; d < dim; d++) pop[i][d]=clamp(0.5*pop[i][d]+0.5*best[d],lb,ub);
                fit[i]=f.evaluate(pop[i]);
            }
            // 可替换扰动层（30% 概率）
            if (!pert.equals("NoPert")) {
                double la = 0.10*Math.pow(1-tr,3);
                for (int i = 0; i < POP; i++) {
                    if (rnd.nextDouble() < 0.3) {
                        for (int d = 0; d < dim; d++) {
                            double step;
                            switch (pert) {
                                case "Levy":
                                    step = levyStep(rnd, sigU, 1.5);
                                    pop[i][d] = clamp(pop[i][d]+la*step*Math.abs(best[d]-pop[i][d]),lb,ub);
                                    break;
                                case "Gaussian":
                                    step = rnd.nextGaussian();
                                    pop[i][d] = clamp(pop[i][d]+la*step*Math.abs(best[d]-pop[i][d]),lb,ub);
                                    break;
                                case "Cauchy":
                                    // Cauchy: u1/u2 (u1,u2 standard normal)
                                    step = rnd.nextGaussian()/(rnd.nextGaussian()+1e-10);
                                    step = Math.max(-5,Math.min(5,step));
                                    pop[i][d] = clamp(pop[i][d]+la*step*Math.abs(best[d]-pop[i][d]),lb,ub);
                                    break;
                                case "Uniform":
                                    step = (rnd.nextDouble()*2-1);
                                    pop[i][d] = clamp(pop[i][d]+la*step*(ub-lb)*0.1,lb,ub);
                                    break;
                            }
                        }
                        fit[i]=f.evaluate(pop[i]);
                    }
                }
            }
            for (int i = 0; i < POP; i++) if (fit[i]<bestF) { bestF=fit[i]; best=pop[i].clone(); }
        }
        return bestF;
    }

    static double[] rankOf(double[] v) {
        Integer[] idx = new Integer[v.length];
        for (int i = 0; i < v.length; i++) idx[i]=i;
        Arrays.sort(idx,(x,y)->Double.compare(v[x],v[y]));
        double[] r = new double[v.length];
        for (int i = 0; i < v.length; i++) r[idx[i]]=i+1;
        return r;
    }
    static double levySigma(double l) {
        double n=Gamma.gamma(1+l)*Math.sin(Math.PI*l/2);
        double d=Gamma.gamma((1+l)/2)*l*Math.pow(2,(l-1)/2);
        return Math.pow(n/d,1/l);
    }
    static double levyStep(Random r,double sig,double l) {
        double u=r.nextGaussian()*sig,v=r.nextGaussian();
        return u/Math.pow(Math.abs(v)+1e-10,1/l);
    }
    static double clamp(double x,double lo,double hi){return Math.max(lo,Math.min(hi,x));}
}

package com.edcbo.research.benchmark;

import org.apache.commons.math3.special.Gamma;
import java.util.Random;

/**
 * CBO-Lévy：真正的"CBO + Lévy 增强"。
 *
 * 保留 CBO 全部算子（tanh 搜索朝随机猎物 / 旋转矩阵 / 向 best 攻击，均不贪心），
 * 只叠加 Lévy 长跳跃作为额外探索 —— 是"叠加增强"而非"替换重构"。
 *
 * 变体消融（LSCBOVariantTest）证明它在公平 CEC 对比中超过基础算法 CBO，
 * 是 LSCBO_Fixed_Lite（用更差算子替换 + 贪心 → 反而 < CBO）的正确替代实现。
 */
public class CBOLevy_Lite implements BenchmarkRunner.BenchmarkOptimizer {
    private static final int POP = 50;  // 调优最优（CECTuneTest 验证 pop50 > pop30；局部搜索无效已舍弃）
    private final Random random;
    private final double sigmaU;

    public CBOLevy_Lite(long seed) { this.random = new Random(seed); this.sigmaU = levySigma(1.5); }
    public CBOLevy_Lite() { this(42L); }

    @Override public String getName() { return "CBO-Levy"; }

    @Override
    public double optimize(BenchmarkFunction f, int maxIter) {
        int dim = f.getDimensions();
        double lb = f.getLowerBound(), ub = f.getUpperBound();
        Random rnd = this.random;
        double[][] pop = new double[POP][dim];
        double[] fit = new double[POP];
        double[] best = null;
        double bestF = Double.MAX_VALUE;
        for (int i = 0; i < POP; i++) {
            for (int d = 0; d < dim; d++) pop[i][d] = lb + rnd.nextDouble() * (ub - lb);
            fit[i] = f.evaluate(pop[i]);
            if (fit[i] < bestF) { bestF = fit[i]; best = pop[i].clone(); }
        }
        for (int t = 0; t < maxIter; t++) {
            double tr = (double) t / maxIter;
            // Phase 1: searching（tanh，随机猎物，不贪心）
            for (int i = 0; i < POP; i++) {
                int prey = rnd.nextInt(POP); if (prey == i) prey = (i + 1) % POP;
                for (int d = 0; d < dim; d++) {
                    double dist = Math.abs(pop[prey][d] - pop[i][d]);
                    double r = (rnd.nextDouble() - 0.5) * 1.5;
                    pop[i][d] = clamp(pop[i][d] + r * Math.tanh(dist) * (pop[prey][d] - pop[i][d]), lb, ub);
                }
                fit[i] = f.evaluate(pop[i]);
            }
            // Phase 2: encircling（旋转矩阵绕原点）
            double theta = 2 * Math.PI * t / maxIter, cos = Math.cos(theta), sin = Math.sin(theta);
            for (int i = 0; i < POP; i++) {
                for (int d = 0; d < dim - 1; d += 2) {
                    double x1 = pop[i][d], x2 = pop[i][d + 1];
                    pop[i][d] = clamp(cos * x1 - sin * x2, lb, ub);
                    pop[i][d + 1] = clamp(sin * x1 + cos * x2, lb, ub);
                }
                if (dim % 2 == 1) { int L = dim - 1; double aa = 2 * (1 - tr); pop[i][L] = clamp(pop[i][L] + aa * (best[L] - pop[i][L]), lb, ub); }
                fit[i] = f.evaluate(pop[i]);
            }
            // Phase 3: attacking（向 best 收敛 0.5）
            for (int i = 0; i < POP; i++) {
                for (int d = 0; d < dim; d++) pop[i][d] = clamp(0.5 * pop[i][d] + 0.5 * best[d], lb, ub);
                fit[i] = f.evaluate(pop[i]);
            }
            // 叠加 Lévy 探索：30% 个体施加 Lévy 长跳跃
            double levyAlpha = 0.10 * Math.pow(1 - tr, 3);
            for (int i = 0; i < POP; i++) {
                if (rnd.nextDouble() < 0.3) {
                    for (int d = 0; d < dim; d++) {
                        double ls = levyStepGen(rnd, sigmaU, 1.5);
                        pop[i][d] = clamp(pop[i][d] + levyAlpha * ls * Math.abs(best[d] - pop[i][d]), lb, ub);
                    }
                    fit[i] = f.evaluate(pop[i]);
                }
            }
            for (int i = 0; i < POP; i++) if (fit[i] < bestF) { bestF = fit[i]; best = pop[i].clone(); }
        }
        return bestF;
    }

    private static double levySigma(double l) {
        double n = Gamma.gamma(1 + l) * Math.sin(Math.PI * l / 2);
        double d = Gamma.gamma((1 + l) / 2) * l * Math.pow(2, (l - 1) / 2);
        return Math.pow(n / d, 1 / l);
    }
    private static double levyStepGen(Random r, double sig, double l) {
        double u = r.nextGaussian() * sig, v = r.nextGaussian();
        return u / Math.pow(Math.abs(v) + 1e-10, 1 / l);
    }
    private static double clamp(double x, double lo, double hi) { return Math.max(lo, Math.min(hi, x)); }
}

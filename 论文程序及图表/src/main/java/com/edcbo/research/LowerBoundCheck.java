package com.edcbo.research;

import java.util.*;

/**
 * 独立下界检查：复现 EnhancedValidationTest 的 VM/任务生成，
 * 计算 makespan 理论下界 sum(len)/sum(mips)、LPT 贪心解、纯随机分配解。
 * 用于验证增强版结果是否物理可行（>= 下界），并定位提升来源。
 */
public class LowerBoundCheck {
    public static void main(String[] args) {
        int VM = 50, M = args.length > 0 ? Integer.parseInt(args[0]) : 1000;
        int VMIN = 500, VMAX = 2000, LMIN = 1000, LMAX = 20000;
        long[] seeds = {43, 44, 45, 46, 47};

        System.out.printf("M=%d, VM=%d%n", M, VM);
        System.out.printf("%-6s %10s %10s %12s%n", "seed", "下界", "LPT", "随机分配");
        for (long seed : seeds) {
            Random rv = new Random(seed);
            double[] mips = new double[VM];
            double sumMips = 0;
            for (int i = 0; i < VM; i++) {
                mips[i] = VMIN + (long) (rv.nextDouble() * (VMAX - VMIN));
                sumMips += mips[i];
            }
            Random rc = new Random(seed + 1_000_000L);
            double[] len = new double[M];
            double sumLen = 0;
            for (int i = 0; i < M; i++) {
                len[i] = LMIN + (long) (rc.nextDouble() * (LMAX - LMIN));
                sumLen += len[i];
            }

            double lb = sumLen / sumMips;

            // LPT 贪心
            Integer[] ord = new Integer[M];
            for (int i = 0; i < M; i++) ord[i] = i;
            Arrays.sort(ord, (x, y) -> Double.compare(len[y], len[x]));
            double[] load = new double[VM];
            for (int idx : ord) {
                int b = 0;
                double bc = Double.MAX_VALUE;
                for (int v = 0; v < VM; v++) {
                    double c = load[v] + len[idx] / mips[v];
                    if (c < bc) { bc = c; b = v; }
                }
                load[b] += len[idx] / mips[b];
            }
            double lpt = 0;
            for (double x : load) lpt = Math.max(lpt, x);

            // 纯随机分配（模拟随机初始化 decode）
            Random rr = new Random(seed + 7L);
            double[] rl = new double[VM];
            for (int i = 0; i < M; i++) {
                int v = (int) (rr.nextDouble() * VM);
                if (v >= VM) v = VM - 1;
                rl[v] += len[i] / mips[v];
            }
            double rnd = 0;
            for (double x : rl) rnd = Math.max(rnd, x);

            System.out.printf("%-6d %10.1f %10.1f %12.1f%n", seed, lb, lpt, rnd);
        }
    }
}

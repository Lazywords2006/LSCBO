package com.edcbo.research;

import com.edcbo.research.utils.StatisticalTest;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import java.io.File;
import java.nio.file.Files;
import java.util.*;

/**
 * 诚实的负载均衡(LBR)统计分析：读取最新的公平 N5000 结果，
 * 计算 LSCBO/CBO/WOA 的 makespan 与 LBR 描述统计 + Wilcoxon p + Cohen's d。
 *
 * 目的：用未操纵的公平数据，量化 LSCBO 在负载均衡上的真实优势，
 * 同时诚实呈现 makespan 上的真实地位（持平/略优，不夸大）。
 */
public class LBRQuickStats {
    public static void main(String[] args) throws Exception {
        File dir = new File("results");
        File[] files = dir.listFiles((d, n) -> n.startsWith("N5000_validation_") && n.endsWith(".csv"));
        if (files == null || files.length == 0) { System.out.println("无 N5000 结果文件"); return; }
        Arrays.sort(files, Comparator.comparing(File::getName));
        File csv = files[files.length - 1];
        System.out.println("数据文件: " + csv.getName());

        Map<String, List<Double>> mk = new LinkedHashMap<>();
        Map<String, List<Double>> lbr = new LinkedHashMap<>();
        for (String a : new String[]{"LSCBO", "CBO", "WOA"}) { mk.put(a, new ArrayList<>()); lbr.put(a, new ArrayList<>()); }

        List<String> lines = Files.readAllLines(csv.toPath());
        for (int i = 1; i < lines.size(); i++) {
            String[] p = lines.get(i).split(",");
            if (p.length < 5) continue;
            String algo = p[0];
            if (!mk.containsKey(algo)) continue;
            mk.get(algo).add(Double.parseDouble(p[3]));
            lbr.get(algo).add(Double.parseDouble(p[4]));
        }

        System.out.printf("%n样本数: 每算法 %d seeds%n", mk.get("LSCBO").size());
        System.out.printf("%n%-8s | %-22s | %-22s%n", "算法", "Makespan(mean±std,中位)", "LBR(mean±std,中位)");
        System.out.println("---------|------------------------|------------------------");
        for (String a : mk.keySet()) {
            System.out.printf("%-8s | %s | %s%n", a, desc(mk.get(a)), desc(lbr.get(a)));
        }

        System.out.println("\n=== 成对检验（基准 vs LSCBO；d>0 表示 LSCBO 更优）===");
        for (String base : new String[]{"CBO", "WOA"}) {
            System.out.printf("%nLSCBO vs %s:%n", base);
            report("  Makespan", mk.get(base), mk.get("LSCBO"));
            report("  LBR     ", lbr.get(base), lbr.get("LSCBO"));
        }

        // LBR 胜场（每 seed 谁的 LBR 最低）
        int[] win = new int[3];
        String[] names = {"LSCBO", "CBO", "WOA"};
        int n = lbr.get("LSCBO").size();
        for (int i = 0; i < n; i++) {
            int b = 0;
            for (int k = 1; k < 3; k++) if (lbr.get(names[k]).get(i) < lbr.get(names[b]).get(i)) b = k;
            win[b]++;
        }
        System.out.printf("%nLBR 逐 seed 最优胜场: LSCBO=%d, CBO=%d, WOA=%d (共%d)%n", win[0], win[1], win[2], n);
    }

    static String desc(List<Double> v) {
        DescriptiveStatistics s = new DescriptiveStatistics();
        v.forEach(s::addValue);
        return String.format("%.1f±%.1f, %.2f", s.getMean(), s.getStandardDeviation(), s.getPercentile(50));
    }

    static void report(String label, List<Double> base, List<Double> improved) {
        double p = StatisticalTest.wilcoxonTest(base, improved);
        double d = StatisticalTest.cohensD(base, improved);
        System.out.printf("%s : p=%.4e (%s), Cohen's d=%.3f (%s)%n",
                label, p, StatisticalTest.interpretPValue(p), d, StatisticalTest.interpretCohensD(d));
    }
}

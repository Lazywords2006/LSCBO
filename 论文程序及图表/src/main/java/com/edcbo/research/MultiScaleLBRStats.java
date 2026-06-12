package com.edcbo.research;

import com.edcbo.research.utils.StatisticalTest;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import java.io.File;
import java.nio.file.Files;
import java.util.*;

/**
 * 多规模 LBR 统计：读取 loadbalance_multiscale_*.csv，按任务规模分组，
 * 输出 LSCBO/CBO/WOA 的 makespan 与 LBR 描述统计 + Wilcoxon p + Cohen's d。
 * 验证 LSCBO 的负载均衡优势是否跨规模稳定。
 */
public class MultiScaleLBRStats {
    public static void main(String[] args) throws Exception {
        File dir = new File("results");
        File[] fs = dir.listFiles((d, n) -> n.startsWith("loadbalance_multiscale_") && n.endsWith(".csv"));
        if (fs == null || fs.length == 0) { System.out.println("无多规模结果文件"); return; }
        Arrays.sort(fs, Comparator.comparing(File::getName));
        File csv = fs[fs.length - 1];
        System.out.println("数据: " + csv.getName());

        Map<Integer, Map<String, List<Double>>> mk = new TreeMap<>(), lbr = new TreeMap<>();
        for (String line : Files.readAllLines(csv.toPath())) {
            if (line.startsWith("Algorithm")) continue;
            String[] p = line.split(",");
            if (p.length < 5) continue;
            int M = Integer.parseInt(p[1]);
            String algo = p[0];
            mk.computeIfAbsent(M, k -> new LinkedHashMap<>()).computeIfAbsent(algo, k -> new ArrayList<>()).add(Double.parseDouble(p[3]));
            lbr.computeIfAbsent(M, k -> new LinkedHashMap<>()).computeIfAbsent(algo, k -> new ArrayList<>()).add(Double.parseDouble(p[4]));
        }

        for (int M : mk.keySet()) {
            System.out.printf("%n===== M=%d  (n=%d seeds) =====%n", M, lbr.get(M).get("LSCBO").size());
            System.out.printf("%-8s | %-20s | %-20s%n", "算法", "Makespan(mean±std)", "LBR(mean±std,中位)");
            for (String al : new String[]{"LSCBO", "CBO", "WOA"})
                System.out.printf("%-8s | %-20s | %-20s%n", al, desc(mk.get(M).get(al)), desc(lbr.get(M).get(al)));
            for (String base : new String[]{"CBO", "WOA"}) {
                double pl = StatisticalTest.wilcoxonTest(lbr.get(M).get(base), lbr.get(M).get("LSCBO"));
                double dl = StatisticalTest.cohensD(lbr.get(M).get(base), lbr.get(M).get("LSCBO"));
                double pm = StatisticalTest.wilcoxonTest(mk.get(M).get(base), mk.get(M).get("LSCBO"));
                double dm = StatisticalTest.cohensD(mk.get(M).get(base), mk.get(M).get("LSCBO"));
                System.out.printf("  LSCBO vs %-4s: LBR p=%.3e(%s) d=%.2f | Makespan p=%.3e(%s) d=%.2f%n",
                        base, pl, StatisticalTest.interpretPValue(pl), dl,
                        pm, StatisticalTest.interpretPValue(pm), dm);
            }
        }
    }

    static String desc(List<Double> v) {
        DescriptiveStatistics s = new DescriptiveStatistics();
        v.forEach(s::addValue);
        return String.format("%.1f±%.1f, %.2f", s.getMean(), s.getStandardDeviation(), s.getPercentile(50));
    }
}

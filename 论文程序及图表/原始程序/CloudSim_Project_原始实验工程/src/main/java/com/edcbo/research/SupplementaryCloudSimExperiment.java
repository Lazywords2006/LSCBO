package com.edcbo.research;

import com.edcbo.research.utils.CostCalculator;
import com.edcbo.research.utils.LoadBalanceCalculator;
import com.edcbo.research.utils.StatisticalTest;
import org.cloudsimplus.allocationpolicies.VmAllocationPolicySimple;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimple;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimple;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.utilizationmodels.UtilizationModelFull;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

public class SupplementaryCloudSimExperiment {

    private static final String LF_CBO = "LF-CBO";
    private static final String CBO = "CBO";
    private static final String WOA = "WOA";
    private static final String FCFS = "FCFS";
    private static final String MIN_MIN = "Min-Min";
    private static final String MAX_MIN = "Max-Min";
    private static final List<String> ALGORITHMS = Arrays.asList(LF_CBO, CBO, WOA, FCFS, MIN_MIN, MAX_MIN);

    private static final int[] DEFAULT_TASKS = {500, 1000, 2000, 5000};
    private static final int[] EXTENDED_TASKS = {500, 1000, 2000, 5000, 10000};
    private static final long[] DEFAULT_SEEDS = {42L, 123L, 456L, 789L, 1024L};
    private static final int[] DEFAULT_VM_SCENARIOS = {30, 50, 100};

    private static final int VM_MIPS_MIN = 1000;
    private static final int VM_MIPS_MAX = 5000;
    private static final int HOST_MIPS = 10000;
    private static final long VM_RAM = 2048;
    private static final long VM_BW = 1000;
    private static final long VM_SIZE = 10000;
    private static final long HOST_RAM = 100000;
    private static final long HOST_BW = 100000;
    private static final long HOST_STORAGE = 100000;
    private static final long TASK_LENGTH_MIN = 10000;
    private static final long TASK_LENGTH_MAX = 50000;

    private static final DecimalFormat DF3 = new DecimalFormat("0.000");
    private static final DecimalFormat DF4 = new DecimalFormat("0.0000");

    private static final class Config {
        int[] tasks = DEFAULT_TASKS;
        long[] seeds = DEFAULT_SEEDS;
        int vmCount = 50;
        List<String> algorithms = ALGORITHMS;
        boolean runVmScenario = true;
        int[] vmScenarios = DEFAULT_VM_SCENARIOS;
        int vmScenarioTaskCount = 2000;
        String outputDir = "supplementary_cloudsim_" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
    }

    private static final class Record {
        String scenario;
        int taskCount;
        int vmCount;
        long seed;
        String algorithm;
        double makespan;
        double lbr;
        double cost;
        double fitness;
        long runtimeMs;
    }

    private static final class Summary {
        String scenario;
        int taskCount;
        int vmCount;
        String algorithm;
        int runs;
        double makespanMean;
        double makespanStd;
        double lbrMean;
        double lbrStd;
        double runtimeMean;
        double runtimeStd;
    }

    public static void main(String[] args) throws Exception {
        Config cfg = parseArgs(args);
        Path out = Paths.get(cfg.outputDir).toAbsolutePath();
        Files.createDirectories(out);

        System.out.println("Supplementary CloudSim Experiment");
        System.out.println("Output: " + out);
        System.out.println("Tasks: " + Arrays.toString(cfg.tasks));
        System.out.println("VM count: " + cfg.vmCount);
        System.out.println("Seeds: " + Arrays.toString(cfg.seeds));
        System.out.println("Algorithms: " + cfg.algorithms);

        List<Record> main = runMain(cfg);
        writeRaw(main, out.resolve("raw_main_results.csv"));
        List<Summary> mainSummary = summarize(main);
        writeSummary(mainSummary, out.resolve("main_summary.csv"));
        writeMainTables(mainSummary, out);
        writeComparison(main, out.resolve("lfcbo_vs_baselines_stats.csv"));
        writeRanks(mainSummary, out.resolve("friedman_ranking.csv"));

        if (cfg.runVmScenario) {
            List<Record> vmScenario = runVmScenario(cfg);
            writeRaw(vmScenario, out.resolve("raw_vm_scenario_results.csv"));
            List<Summary> vmSummary = summarize(vmScenario);
            writeSummary(vmSummary, out.resolve("vm_scenario_summary.csv"));
            writeVmScenarioTable(vmSummary, out.resolve("table_vm_scenario_makespan.tex"));
        }
    }

    private static Config parseArgs(String[] args) {
        Config cfg = new Config();
        for (String arg : args) {
            if (!arg.startsWith("--") || !arg.contains("=")) {
                continue;
            }
            String[] parts = arg.substring(2).split("=", 2);
            switch (parts[0]) {
                case "taskCounts":
                    cfg.tasks = parseInts(parts[1]);
                    break;
                case "include10000":
                    if (Boolean.parseBoolean(parts[1])) {
                        cfg.tasks = EXTENDED_TASKS;
                    }
                    break;
                case "seeds":
                    cfg.seeds = parseLongs(parts[1]);
                    break;
                case "vmCount":
                    cfg.vmCount = Integer.parseInt(parts[1]);
                    break;
                case "algorithms":
                    cfg.algorithms = Arrays.stream(parts[1].split(","))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .collect(Collectors.toList());
                    break;
                case "runVmScenario":
                    cfg.runVmScenario = Boolean.parseBoolean(parts[1]);
                    break;
                case "vmScenarioCounts":
                    cfg.vmScenarios = parseInts(parts[1]);
                    break;
                case "vmScenarioTaskCount":
                    cfg.vmScenarioTaskCount = Integer.parseInt(parts[1]);
                    break;
                case "outputDir":
                    cfg.outputDir = parts[1];
                    break;
                default:
                    break;
            }
        }
        return cfg;
    }

    private static int[] parseInts(String value) {
        return Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty()).mapToInt(Integer::parseInt).toArray();
    }

    private static long[] parseLongs(String value) {
        return Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty()).mapToLong(Long::parseLong).toArray();
    }

    private static List<Record> runMain(Config cfg) {
        List<Record> records = new ArrayList<>();
        int total = cfg.tasks.length * cfg.seeds.length * cfg.algorithms.size();
        int done = 0;

        for (int taskCount : cfg.tasks) {
            for (long seed : cfg.seeds) {
                int[] vmMips = generateVmMips(cfg.vmCount, seed);
                long[] taskLengths = generateTaskLengths(taskCount, seed);
                for (String algorithm : cfg.algorithms) {
                    done++;
                    System.out.printf(Locale.US, "[%d/%d] %s M=%d VM=%d Seed=%d%n", done, total, algorithm, taskCount, cfg.vmCount, seed);
                    Record record = runOne("main_scale", algorithm, taskCount, cfg.vmCount, seed, vmMips, taskLengths);
                    records.add(record);
                    System.out.printf(Locale.US, "         makespan=%.3f lbr=%.4f runtime=%dms%n", record.makespan, record.lbr, record.runtimeMs);
                }
            }
        }
        return records;
    }

    private static List<Record> runVmScenario(Config cfg) {
        List<Record> records = new ArrayList<>();
        int total = cfg.vmScenarios.length * cfg.seeds.length * cfg.algorithms.size();
        int done = 0;

        for (int vmCount : cfg.vmScenarios) {
            for (long seed : cfg.seeds) {
                int[] vmMips = generateVmMips(vmCount, seed);
                long[] taskLengths = generateTaskLengths(cfg.vmScenarioTaskCount, seed);
                for (String algorithm : cfg.algorithms) {
                    done++;
                    System.out.printf(Locale.US, "[VM %d/%d] %s M=%d VM=%d Seed=%d%n", done, total, algorithm, cfg.vmScenarioTaskCount, vmCount, seed);
                    records.add(runOne("vm_scenario", algorithm, cfg.vmScenarioTaskCount, vmCount, seed, vmMips, taskLengths));
                }
            }
        }
        return records;
    }

    private static Record runOne(String scenario, String algorithm, int taskCount, int vmCount, long seed, int[] vmMips, long[] taskLengths) {
        CloudSimPlus sim = new CloudSimPlus();
        createDatacenter(sim, vmCount);
        DatacenterBrokerSimple broker = createBroker(sim, algorithm, seed, taskCount);
        List<Vm> vms = createVms(vmMips);
        List<Cloudlet> cloudlets = createCloudlets(taskLengths);
        broker.submitVmList(vms);
        broker.submitCloudletList(cloudlets);

        long start = System.nanoTime();
        runQuietly(sim);
        long end = System.nanoTime();

        int[] schedule = extractSchedule(cloudlets, vms);
        Record record = new Record();
        record.scenario = scenario;
        record.taskCount = taskCount;
        record.vmCount = vmCount;
        record.seed = seed;
        record.algorithm = algorithm;
        record.makespan = makespan(schedule, cloudlets, vms);
        record.lbr = LoadBalanceCalculator.calculateLoadBalanceRatio(schedule, taskCount, vmCount, cloudlets, vms);
        record.cost = CostCalculator.calculateCost(schedule, taskCount, vmCount, cloudlets, vms);
        record.fitness = CostCalculator.calculateWeightedCostDetails(schedule, taskCount, vmCount, cloudlets, vms).fitness;
        record.runtimeMs = (end - start) / 1_000_000;
        return record;
    }

    private static void runQuietly(CloudSimPlus sim) {
        PrintStream out = System.out;
        PrintStream err = System.err;
        PrintStream sink = new PrintStream(OutputStream.nullOutputStream());
        try {
            System.setOut(sink);
            System.setErr(sink);
            sim.start();
        } finally {
            System.setOut(out);
            System.setErr(err);
            sink.close();
        }
    }

    private static DatacenterBrokerSimple createBroker(CloudSimPlus sim, String algorithm, long seed, int taskCount) {
        switch (algorithm) {
            case LF_CBO:
                return new LSCBO_Broker_Fixed(sim, seed, "M" + taskCount);
            case CBO:
                return new CBO_Broker(sim, seed);
            case WOA:
                return new WOA_Broker(sim, seed);
            case FCFS:
                return new FCFS_Broker(sim, seed);
            case MIN_MIN:
                return new MinMin_Broker(sim, seed);
            case MAX_MIN:
                return new MaxMin_Broker(sim, seed);
            default:
                throw new IllegalArgumentException("Unknown algorithm: " + algorithm);
        }
    }

    private static void createDatacenter(CloudSimPlus sim, int vmCount) {
        List<Host> hosts = new ArrayList<>();
        for (int i = 0; i < vmCount; i++) {
            List<Pe> pes = new ArrayList<>();
            for (int j = 0; j < 4; j++) {
                pes.add(new PeSimple(HOST_MIPS));
            }
            hosts.add(new HostSimple(HOST_RAM, HOST_BW, HOST_STORAGE, pes));
        }
        new DatacenterSimple(sim, hosts, new VmAllocationPolicySimple());
    }

    private static List<Vm> createVms(int[] vmMips) {
        List<Vm> vms = new ArrayList<>();
        for (int i = 0; i < vmMips.length; i++) {
            vms.add(new VmSimple(i, vmMips[i], 1).setRam(VM_RAM).setBw(VM_BW).setSize(VM_SIZE));
        }
        return vms;
    }

    private static List<Cloudlet> createCloudlets(long[] taskLengths) {
        List<Cloudlet> cloudlets = new ArrayList<>();
        for (int i = 0; i < taskLengths.length; i++) {
            cloudlets.add(new CloudletSimple(i, taskLengths[i], 1)
                    .setFileSize(300)
                    .setOutputSize(300)
                    .setUtilizationModel(new UtilizationModelFull()));
        }
        return cloudlets;
    }

    private static int[] generateVmMips(int vmCount, long seed) {
        Random random = new Random(seed);
        int[] values = new int[vmCount];
        for (int i = 0; i < vmCount; i++) {
            values[i] = VM_MIPS_MIN + random.nextInt(VM_MIPS_MAX - VM_MIPS_MIN + 1);
        }
        return values;
    }

    private static long[] generateTaskLengths(int taskCount, long seed) {
        Random random = new Random(seed + 10000);
        long[] values = new long[taskCount];
        for (int i = 0; i < taskCount; i++) {
            values[i] = TASK_LENGTH_MIN + random.nextInt((int) (TASK_LENGTH_MAX - TASK_LENGTH_MIN + 1));
        }
        return values;
    }

    private static int[] extractSchedule(List<Cloudlet> cloudlets, List<Vm> vms) {
        Map<Long, Integer> vmIndex = new HashMap<>();
        for (int i = 0; i < vms.size(); i++) {
            vmIndex.put(vms.get(i).getId(), i);
        }
        int[] schedule = new int[cloudlets.size()];
        for (int i = 0; i < cloudlets.size(); i++) {
            Vm vm = cloudlets.get(i).getVm();
            if (vm == null || !vmIndex.containsKey(vm.getId())) {
                throw new IllegalStateException("Missing VM assignment for cloudlet " + i);
            }
            schedule[i] = vmIndex.get(vm.getId());
        }
        return schedule;
    }

    private static double makespan(int[] schedule, List<Cloudlet> cloudlets, List<Vm> vms) {
        double[] loads = new double[vms.size()];
        for (int i = 0; i < schedule.length; i++) {
            int vm = schedule[i];
            loads[vm] += cloudlets.get(i).getLength() / vms.get(vm).getMips();
        }
        double max = 0.0;
        for (double load : loads) {
            max = Math.max(max, load);
        }
        return max;
    }

    private static List<Summary> summarize(List<Record> records) {
        Map<String, List<Record>> grouped = new LinkedHashMap<>();
        for (Record record : records) {
            String key = record.scenario + "|" + record.taskCount + "|" + record.vmCount + "|" + record.algorithm;
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(record);
        }

        List<Summary> rows = new ArrayList<>();
        for (List<Record> group : grouped.values()) {
            Record first = group.get(0);
            Summary summary = new Summary();
            summary.scenario = first.scenario;
            summary.taskCount = first.taskCount;
            summary.vmCount = first.vmCount;
            summary.algorithm = first.algorithm;
            summary.runs = group.size();
            summary.makespanMean = mean(group.stream().map(r -> r.makespan).collect(Collectors.toList()));
            summary.makespanStd = std(group.stream().map(r -> r.makespan).collect(Collectors.toList()), summary.makespanMean);
            summary.lbrMean = mean(group.stream().map(r -> r.lbr).collect(Collectors.toList()));
            summary.lbrStd = std(group.stream().map(r -> r.lbr).collect(Collectors.toList()), summary.lbrMean);
            summary.runtimeMean = mean(group.stream().map(r -> (double) r.runtimeMs).collect(Collectors.toList()));
            summary.runtimeStd = std(group.stream().map(r -> (double) r.runtimeMs).collect(Collectors.toList()), summary.runtimeMean);
            rows.add(summary);
        }

        rows.sort(Comparator.comparing((Summary r) -> r.scenario).thenComparingInt(r -> r.taskCount).thenComparingInt(r -> r.vmCount).thenComparing(r -> r.algorithm));
        return rows;
    }

    private static double mean(List<Double> values) {
        if (values.isEmpty()) {
            return 0.0;
        }
        double sum = 0.0;
        for (double value : values) {
            sum += value;
        }
        return sum / values.size();
    }

    private static double std(List<Double> values, double mean) {
        if (values.size() <= 1) {
            return 0.0;
        }
        double variance = 0.0;
        for (double value : values) {
            double diff = value - mean;
            variance += diff * diff;
        }
        return Math.sqrt(variance / values.size());
    }

    private static void writeRaw(List<Record> records, Path file) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            writer.write("Scenario,TaskCount,VmCount,Seed,Algorithm,Makespan,LBR,Cost,WeightedFitness,RuntimeMs");
            writer.newLine();
            for (Record record : records) {
                writer.write(String.format(Locale.US, "%s,%d,%d,%d,%s,%.6f,%.6f,%.6f,%.6f,%d",
                        record.scenario, record.taskCount, record.vmCount, record.seed, record.algorithm,
                        record.makespan, record.lbr, record.cost, record.fitness, record.runtimeMs));
                writer.newLine();
            }
        }
    }

    private static void writeSummary(List<Summary> rows, Path file) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            writer.write("Scenario,TaskCount,VmCount,Algorithm,Runs,MakespanMean,MakespanStd,LBRMean,LBRStd,RuntimeMeanMs,RuntimeStdMs");
            writer.newLine();
            for (Summary row : rows) {
                writer.write(String.format(Locale.US, "%s,%d,%d,%s,%d,%.6f,%.6f,%.6f,%.6f,%.3f,%.3f",
                        row.scenario, row.taskCount, row.vmCount, row.algorithm, row.runs,
                        row.makespanMean, row.makespanStd, row.lbrMean, row.lbrStd,
                        row.runtimeMean, row.runtimeStd));
                writer.newLine();
            }
        }
    }

    private static void writeMainTables(List<Summary> rows, Path out) throws IOException {
        List<Summary> mainRows = rows.stream().filter(r -> "main_scale".equals(r.scenario)).collect(Collectors.toList());
        writeMetricTable(mainRows, out.resolve("table_main_makespan.tex"), "Main supplementary comparison on makespan (mean $\\pm$ std, lower is better)", "tab:supp_main_makespan", "makespan");
        writeMetricTable(mainRows, out.resolve("table_main_lbr.tex"), "Main supplementary comparison on load balance ratio (mean $\\pm$ std, lower is better)", "tab:supp_main_lbr", "lbr");
        writeMetricTable(mainRows, out.resolve("table_main_runtime.tex"), "Main supplementary comparison on wall-clock runtime (ms, mean $\\pm$ std)", "tab:supp_main_runtime", "runtime");
    }

    private static void writeMetricTable(List<Summary> rows, Path file, String caption, String label, String metric) throws IOException {
        Map<Integer, List<Summary>> byTask = rows.stream().collect(Collectors.groupingBy(r -> r.taskCount, LinkedHashMap::new, Collectors.toList()));
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            writer.write("\\begin{table*}[htbp]\n\\centering\n\\caption{" + caption + "}\n\\label{" + label + "}\n\\small\n");
            writer.write("\\begin{tabular}{l" + "c".repeat(ALGORITHMS.size()) + "}\n\\toprule\nTask count");
            for (String algorithm : ALGORITHMS) {
                writer.write(" & " + algorithm.replace("_", "\\_"));
            }
            writer.write(" \\\\\n\\midrule\n");
            for (Map.Entry<Integer, List<Summary>> entry : byTask.entrySet()) {
                double best = entry.getValue().stream().mapToDouble(r -> r.makespanMean).min().orElse(Double.NaN);
                writer.write(String.valueOf(entry.getKey()));
                for (String algorithm : ALGORITHMS) {
                    Summary row = entry.getValue().stream().filter(r -> r.algorithm.equals(algorithm)).findFirst().orElse(null);
                    String value = formatCell(row, metric);
                    if ("makespan".equals(metric) && row != null && Math.abs(row.makespanMean - best) < 1e-9) {
                        value = "\\textbf{" + value + "}";
                    }
                    writer.write(" & " + value);
                }
                writer.write(" \\\\\n");
            }
            writer.write("\\bottomrule\n\\end{tabular}\n\\end{table*}\n");
        }
    }

    private static String formatCell(Summary row, String metric) {
        if (row == null) {
            return "--";
        }
        if ("makespan".equals(metric)) {
            return DF3.format(row.makespanMean) + " $\\pm$ " + DF3.format(row.makespanStd);
        }
        if ("lbr".equals(metric)) {
            return DF4.format(row.lbrMean) + " $\\pm$ " + DF4.format(row.lbrStd);
        }
        return DF3.format(row.runtimeMean) + " $\\pm$ " + DF3.format(row.runtimeStd);
    }

    private static void writeVmScenarioTable(List<Summary> rows, Path file) throws IOException {
        Map<Integer, List<Summary>> byVm = rows.stream().collect(Collectors.groupingBy(r -> r.vmCount, LinkedHashMap::new, Collectors.toList()));
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            writer.write("\\begin{table*}[htbp]\n\\centering\n");
            writer.write("\\caption{Additional VM-count scenario on makespan (mean $\\pm$ std, lower is better)}\n");
            writer.write("\\label{tab:supp_vm_scenario}\n\\small\n");
            writer.write("\\begin{tabular}{l" + "c".repeat(ALGORITHMS.size()) + "}\n\\toprule\nVM count");
            for (String algorithm : ALGORITHMS) {
                writer.write(" & " + algorithm);
            }
            writer.write(" \\\\\n\\midrule\n");
            for (Map.Entry<Integer, List<Summary>> entry : byVm.entrySet()) {
                double best = entry.getValue().stream().mapToDouble(r -> r.makespanMean).min().orElse(Double.NaN);
                writer.write(String.valueOf(entry.getKey()));
                for (String algorithm : ALGORITHMS) {
                    Summary row = entry.getValue().stream().filter(r -> r.algorithm.equals(algorithm)).findFirst().orElse(null);
                    String value = formatCell(row, "makespan");
                    if (row != null && Math.abs(row.makespanMean - best) < 1e-9) {
                        value = "\\textbf{" + value + "}";
                    }
                    writer.write(" & " + value);
                }
                writer.write(" \\\\\n");
            }
            writer.write("\\bottomrule\n\\end{tabular}\n\\end{table*}\n");
        }
    }

    private static void writeComparison(List<Record> records, Path file) throws IOException {
        List<Record> main = records.stream().filter(r -> "main_scale".equals(r.scenario)).collect(Collectors.toList());
        List<Integer> tasks = main.stream().map(r -> r.taskCount).distinct().sorted().collect(Collectors.toList());
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            writer.write("TaskCount,Baseline,LFCBO_Mean,Baseline_Mean,ImprovementPct,PValue,CohensD,Significance");
            writer.newLine();
            for (int task : tasks) {
                List<Double> lf = main.stream().filter(r -> r.taskCount == task && LF_CBO.equals(r.algorithm)).sorted(Comparator.comparingLong(r -> r.seed)).map(r -> r.makespan).collect(Collectors.toList());
                for (String baseline : ALGORITHMS) {
                    if (LF_CBO.equals(baseline)) {
                        continue;
                    }
                    List<Double> other = main.stream().filter(r -> r.taskCount == task && baseline.equals(r.algorithm)).sorted(Comparator.comparingLong(r -> r.seed)).map(r -> r.makespan).collect(Collectors.toList());
                    double lfMean = mean(lf);
                    double otherMean = mean(other);
                    double improvement = otherMean == 0.0 ? 0.0 : (otherMean - lfMean) / otherMean * 100.0;
                    double pValue = StatisticalTest.wilcoxonTest(other, lf);
                    double d = StatisticalTest.cohensD(other, lf);
                    writer.write(String.format(Locale.US, "%d,%s,%.6f,%.6f,%.4f,%.8f,%.6f,%s",
                            task, baseline, lfMean, otherMean, improvement, pValue, d, StatisticalTest.interpretPValue(pValue)));
                    writer.newLine();
                }
            }
        }
    }

    private static void writeRanks(List<Summary> rows, Path file) throws IOException {
        List<Summary> main = rows.stream().filter(r -> "main_scale".equals(r.scenario)).collect(Collectors.toList());
        List<Integer> tasks = main.stream().map(r -> r.taskCount).distinct().sorted().collect(Collectors.toList());
        double[][] data = new double[ALGORITHMS.size()][tasks.size()];
        for (int i = 0; i < ALGORITHMS.size(); i++) {
            final String algorithm = ALGORITHMS.get(i);
            for (int j = 0; j < tasks.size(); j++) {
                int task = tasks.get(j);
                Summary row = main.stream().filter(r -> r.taskCount == task && r.algorithm.equals(algorithm)).findFirst().orElse(null);
                data[i][j] = row == null ? Double.MAX_VALUE : row.makespanMean;
            }
        }

        double[] ranks = StatisticalTest.calculateAverageRanks(data);
        double p = StatisticalTest.friedmanTest(data);
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            writer.write("Algorithm,AverageRank,FriedmanPValue");
            writer.newLine();
            for (int i = 0; i < ALGORITHMS.size(); i++) {
                writer.write(String.format(Locale.US, "%s,%.6f,%.8f", ALGORITHMS.get(i), ranks[i], p));
                writer.newLine();
            }
        }
    }
}

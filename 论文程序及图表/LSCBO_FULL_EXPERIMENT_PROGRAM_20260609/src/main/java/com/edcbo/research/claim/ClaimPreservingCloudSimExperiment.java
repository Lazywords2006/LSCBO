package com.edcbo.research.claim;

import org.apache.commons.math3.special.Gamma;
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
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class ClaimPreservingCloudSimExperiment {
    private static final int POPULATION_SIZE = 30;
    private static final int MAX_ITERATIONS = 200;
    private static final int TOTAL_EVALUATION_BUDGET = POPULATION_SIZE * MAX_ITERATIONS;
    private static final int LSCBO_GLOBAL_BUDGET = 600;
    private static final int LOCAL_SEARCH_MAX_TRIALS = TOTAL_EVALUATION_BUDGET - LSCBO_GLOBAL_BUDGET;
    private static final int LOCAL_SEARCH_ROUND_TRIALS = 400;
    private static final double LEVY_BETA = 1.5;
    private static final double LEVY_ALPHA = 0.03;
    private static final double LEVY_FRACTION = 0.30;
    private static final double LEVY_SIGMA_U = levySigma(LEVY_BETA);

    private static final int[] SMOKE_SCALES = {50, 100};
    private static final int[] PILOT_SCALES = {200, 500};
    private static final int[] FULL_SCALES = {200, 500, 1000, 2000, 5000};
    private static final int[] WEIGHT_SCALES = {100, 300, 500, 800, 1000};
    private static final int[] ROBUSTNESS_SCALES = {500, 1000, 2000};
    private static final long[] FULL_SEEDS = rangeSeeds(43, 72);
    private static final long[] SMOKE_SEEDS = {43, 44};
    private static final long[] PILOT_SEEDS = {43, 44, 45, 46, 47};
    private static final Algorithm[] ROBUSTNESS_ALGORITHMS = {
            Algorithm.LSCBO, Algorithm.CBO, Algorithm.GREEDY_LPT
    };

    private ClaimPreservingCloudSimExperiment() {
    }

    enum Algorithm {
        LSCBO, CBO, PSO, GWO, WOA, AOA, GREEDY_LPT
    }

    enum Perturbation {
        LEVY, GAUSSIAN, CAUCHY, RANDOM_RESTART, NONE
    }

    enum LocalSearchMode {
        NONE, PAIR_PARETO, PAIR_OBJECTIVE_ONLY, RELOCATE_PARETO
    }

    enum VmProfile {
        DEFAULT(500, 2000, "default_4_to_1"),
        HETEROGENEOUS_20_TO_1(100, 2000, "heterogeneous_20_to_1"),
        MEDIUM_5_TO_1(400, 2000, "medium_5_to_1"),
        NEAR_HOMOGENEOUS_1_5_TO_1(1333, 2000, "near_homogeneous_1_5_to_1");

        final int minMips;
        final int maxMips;
        final String label;

        VmProfile(int minMips, int maxMips, String label) {
            this.minMips = minMips;
            this.maxMips = maxMips;
            this.label = label;
        }
    }

    enum WeightProfile {
        W0_EQUAL("W0_equal", 0.25, 0.25, 0.25, 0.25),
        W1_MAKESPAN("W1_makespan_priority", 0.55, 0.15, 0.15, 0.15),
        W2_ENERGY("W2_energy_priority", 0.15, 0.55, 0.15, 0.15),
        W3_COST("W3_cost_priority", 0.15, 0.15, 0.55, 0.15),
        W4_BALANCE("W4_balance_priority", 0.15, 0.15, 0.15, 0.55);

        final String label;
        final double makespan;
        final double energy;
        final double cost;
        final double imbalance;

        WeightProfile(String label, double makespan, double energy, double cost, double imbalance) {
            this.label = label;
            this.makespan = makespan;
            this.energy = energy;
            this.cost = cost;
            this.imbalance = imbalance;
        }
    }

    static final class Args {
        String mode = "smoke";
        Path out = Paths.get("results", "smoke");
        boolean cloudsim = true;
        long seedStart = 43;
        long seedEnd = 72;
        boolean seedRangeSpecified = false;
        String experiments = "all";
        int parallelism = -1;
    }

    static final class Scenario {
        final int taskCount;
        final int vmCount;
        final long seed;
        final VmProfile vmProfile;
        final double[] taskLengths;
        final double[] vmMips;

        Scenario(int taskCount, int vmCount, long seed, VmProfile vmProfile,
                 double[] taskLengths, double[] vmMips) {
            this.taskCount = taskCount;
            this.vmCount = vmCount;
            this.seed = seed;
            this.vmProfile = vmProfile;
            this.taskLengths = taskLengths;
            this.vmMips = vmMips;
        }
    }

    static final class Metrics {
        final double makespan;
        final double energy;
        final double cost;
        final double imbalance;
        final double objective;

        Metrics(double makespan, double energy, double cost, double imbalance, double objective) {
            this.makespan = makespan;
            this.energy = energy;
            this.cost = cost;
            this.imbalance = imbalance;
            this.objective = objective;
        }
    }

    static final class Norm {
        final double makespan;
        final double energy;
        final double cost;
        final double imbalance;

        Norm(Metrics reference) {
            this.makespan = Math.max(1e-9, reference.makespan);
            this.energy = Math.max(1e-9, reference.energy);
            this.cost = Math.max(1e-9, reference.cost);
            this.imbalance = Math.max(1e-9, reference.imbalance);
        }
    }

    static final class FeatureSet {
        final boolean levyPhase;
        final boolean phase2;
        final boolean phase3;
        final boolean localSearch;
        final boolean lsOnly;
        final Perturbation perturbation;
        final LocalSearchMode localSearchMode;

        FeatureSet(boolean levyPhase, boolean phase2, boolean phase3,
                   boolean localSearch, boolean lsOnly, Perturbation perturbation) {
            this(levyPhase, phase2, phase3, lsOnly, perturbation,
                    localSearch ? LocalSearchMode.PAIR_PARETO : LocalSearchMode.NONE);
        }

        FeatureSet(boolean levyPhase, boolean phase2, boolean phase3,
                   boolean lsOnly, Perturbation perturbation, LocalSearchMode localSearchMode) {
            this.levyPhase = levyPhase;
            this.phase2 = phase2;
            this.phase3 = phase3;
            this.localSearch = localSearchMode != LocalSearchMode.NONE;
            this.lsOnly = lsOnly;
            this.perturbation = perturbation;
            this.localSearchMode = localSearchMode;
        }

        static FeatureSet lscbo() {
            return new FeatureSet(true, true, true, true, false, Perturbation.LEVY);
        }

        static FeatureSet cbo() {
            return new FeatureSet(false, true, true, false, false, Perturbation.NONE);
        }
    }

    static final class OptimizationResult {
        int[] schedule;
        Metrics metrics;
        int initialEvaluations;
        int iterationEvaluations;
        int localSearchEvaluations;
    }

    static final class SimResult {
        final int finishedCloudlets;
        final double cloudsimMakespan;

        SimResult(int finishedCloudlets, double cloudsimMakespan) {
            this.finishedCloudlets = finishedCloudlets;
            this.cloudsimMakespan = cloudsimMakespan;
        }
    }

    static final class ResultRow {
        String mode;
        String experiment;
        String algorithm;
        String variant;
        String weightProfile;
        String vmProfile;
        String perturbation;
        int taskCount;
        int vmCount;
        long seed;
        int populationSize;
        int maxIterations;
        int initialEvaluations;
        int iterationEvaluations;
        int localSearchEvaluations;
        int totalEvaluations;
        double normMakespan;
        double normEnergy;
        double normCost;
        double normImbalance;
        double makespan;
        double energy;
        double cost;
        double imbalance;
        double objective;
        int cloudsimFinishedCloudlets;
        double cloudsimMakespan;
        long runtimeMs;

        String csv() {
            return String.format(Locale.US,
                    "%s,%s,%s,%s,%s,%s,%s,%d,%d,%d,%d,%d,%d,%d,%d,%d,%.8f,%.8f,%.8f,%.8f,%.8f,%.8f,%.8f,%.8f,%.8f,%d,%.8f,%d",
                    mode, experiment, algorithm, variant, weightProfile, vmProfile, perturbation,
                    taskCount, vmCount, seed, populationSize, maxIterations, initialEvaluations,
                    iterationEvaluations, localSearchEvaluations, totalEvaluations,
                    normMakespan, normEnergy, normCost, normImbalance,
                    makespan, energy, cost, imbalance, objective,
                    cloudsimFinishedCloudlets, cloudsimMakespan, runtimeMs);
        }
    }

    static final class Summary {
        int n;
        double makespan;
        double energy;
        double cost;
        double imbalance;
        double objective;

        void add(ResultRow row) {
            n++;
            makespan += row.makespan;
            energy += row.energy;
            cost += row.cost;
            imbalance += row.imbalance;
            objective += row.objective;
        }

        String csv(String key) {
            return String.format(Locale.US, "%s,%d,%.8f,%.8f,%.8f,%.8f,%.8f",
                    key, n, makespan / n, energy / n, cost / n, imbalance / n, objective / n);
        }
    }

    public static void main(String[] rawArgs) throws Exception {
        Args args = parse(rawArgs);
        Files.createDirectories(args.out);
        silenceLogs();

        // CloudSim Plus is not guaranteed thread-safe across concurrent simulations.
        // When --cloudsim true, fall back to single-threaded to be safe.
        int threads = args.cloudsim ? 1
                : (args.parallelism > 0 ? args.parallelism
                        : Math.max(1, Runtime.getRuntime().availableProcessors() - 1));
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        long start = System.currentTimeMillis();
        List<ResultRow> rows = new ArrayList<>();

        try {
            if ("smoke".equalsIgnoreCase(args.mode)) {
                rows.addAll(runE4(pool, args.mode, SMOKE_SCALES, SMOKE_SEEDS,
                        new Algorithm[]{Algorithm.LSCBO, Algorithm.CBO, Algorithm.GREEDY_LPT},
                        10, VmProfile.DEFAULT, WeightProfile.W0_EQUAL, args.cloudsim));
            } else if ("pilot".equalsIgnoreCase(args.mode)) {
                rows.addAll(runE4(pool, args.mode, PILOT_SCALES, PILOT_SEEDS,
                        Algorithm.values(), 50, VmProfile.DEFAULT, WeightProfile.W0_EQUAL, args.cloudsim));
            } else if ("mainfull".equalsIgnoreCase(args.mode)) {
                rows.addAll(runE4(pool, args.mode, FULL_SCALES, seedsFor(args, FULL_SEEDS),
                        Algorithm.values(), 50, VmProfile.DEFAULT, WeightProfile.W0_EQUAL, args.cloudsim));
            } else if ("mainmini".equalsIgnoreCase(args.mode)) {
                rows.addAll(runE4(pool, args.mode, FULL_SCALES, PILOT_SEEDS,
                        Algorithm.values(), 50, VmProfile.DEFAULT, WeightProfile.W0_EQUAL, args.cloudsim));
            } else if ("full".equalsIgnoreCase(args.mode) || "formalfull".equalsIgnoreCase(args.mode)) {
                long[] seeds = seedsFor(args, FULL_SEEDS);
                if (shouldRun(args, "E4_main")) {
                    rows.addAll(runE4(pool, args.mode, FULL_SCALES, seeds,
                            Algorithm.values(), 50, VmProfile.DEFAULT, WeightProfile.W0_EQUAL, args.cloudsim));
                }
                if (shouldRun(args, "E2_ablation")) {
                    rows.addAll(runE2(pool, args.mode, FULL_SCALES, seeds, 50, args.cloudsim));
                }
                if (shouldRun(args, "E3_evolution")) {
                    rows.addAll(runE3(pool, args.mode, FULL_SCALES, seeds, 50, args.cloudsim));
                }
                if (shouldRun(args, "weight_sensitivity")) {
                    rows.addAll(runWeightSensitivity(pool, args.mode, WEIGHT_SCALES, seeds, 50, args.cloudsim));
                }
                if (shouldRun(args, "vm_heterogeneity")) {
                    rows.addAll(runVmHeterogeneity(pool, args.mode, ROBUSTNESS_SCALES, seeds, 50, args.cloudsim));
                }
                if (shouldRun(args, "perturbation")) {
                    rows.addAll(runPerturbation(pool, args.mode, ROBUSTNESS_SCALES, seeds, 50, args.cloudsim));
                }
            } else {
                throw new IllegalArgumentException("Unknown mode: " + args.mode);
            }
        } finally {
            pool.shutdown();
            pool.awaitTermination(24, TimeUnit.HOURS);
        }

        writeRaw(args.out.resolve("claim_" + args.mode.toLowerCase(Locale.ROOT) + "_raw.csv"), rows);
        writeSummary(args.out.resolve("claim_" + args.mode.toLowerCase(Locale.ROOT) + "_summary.csv"), rows);
        writeMetadata(args.out.resolve("claim_metadata.csv"), args, rows, System.currentTimeMillis() - start);
        writeReport(args.out.resolve("run_report.txt"), args, rows, System.currentTimeMillis() - start);

        System.out.println("[LSCBO] mode=" + args.mode);
        System.out.println("[LSCBO] parallelism=" + threads);
        System.out.println("[LSCBO] experiments=" + args.experiments);
        System.out.println("[LSCBO] seeds=" + seedDescription(args));
        System.out.println("[LSCBO] rows=" + rows.size());
        System.out.println("[LSCBO] out=" + args.out.toAbsolutePath());
    }

    private static Args parse(String[] rawArgs) {
        Args args = new Args();
        for (int i = 0; i < rawArgs.length; i++) {
            String token = rawArgs[i];
            if ("--mode".equals(token) && i + 1 < rawArgs.length) {
                args.mode = rawArgs[++i].trim();
            } else if ("--out".equals(token) && i + 1 < rawArgs.length) {
                args.out = Paths.get(rawArgs[++i].trim());
            } else if ("--cloudsim".equals(token) && i + 1 < rawArgs.length) {
                args.cloudsim = Boolean.parseBoolean(rawArgs[++i].trim());
            } else if ("--seedStart".equals(token) && i + 1 < rawArgs.length) {
                args.seedStart = Long.parseLong(rawArgs[++i].trim());
                args.seedRangeSpecified = true;
            } else if ("--seedEnd".equals(token) && i + 1 < rawArgs.length) {
                args.seedEnd = Long.parseLong(rawArgs[++i].trim());
                args.seedRangeSpecified = true;
            } else if ("--seedRange".equals(token) && i + 1 < rawArgs.length) {
                String[] parts = rawArgs[++i].trim().split("-");
                if (parts.length != 2) {
                    throw new IllegalArgumentException("--seedRange must use start-end format, for example 43-45");
                }
                args.seedStart = Long.parseLong(parts[0]);
                args.seedEnd = Long.parseLong(parts[1]);
                args.seedRangeSpecified = true;
            } else if ("--experiments".equals(token) && i + 1 < rawArgs.length) {
                args.experiments = rawArgs[++i].trim();
            } else if ("--parallelism".equals(token) && i + 1 < rawArgs.length) {
                args.parallelism = Integer.parseInt(rawArgs[++i].trim());
            }
        }
        if (args.seedEnd < args.seedStart) {
            throw new IllegalArgumentException("seedEnd must be greater than or equal to seedStart.");
        }
        return args;
    }

    private static long[] seedsFor(Args args, long[] defaults) {
        if (!args.seedRangeSpecified) {
            return defaults;
        }
        return rangeSeeds((int) args.seedStart, (int) args.seedEnd);
    }

    private static String seedDescription(Args args) {
        return args.seedRangeSpecified ? args.seedStart + "-" + args.seedEnd : "default";
    }

    private static boolean shouldRun(Args args, String experiment) {
        String requested = normalizeExperiment(args.experiments);
        if ("all".equals(requested)) {
            return true;
        }
        String target = normalizeExperiment(experiment);
        for (String token : requested.split(",")) {
            String normalized = normalizeExperiment(token);
            if (normalized.equals(target)) {
                return true;
            }
            if ("e4".equals(normalized) && "e4main".equals(target)) {
                return true;
            }
            if ("e2".equals(normalized) && "e2ablation".equals(target)) {
                return true;
            }
            if ("e3".equals(normalized) && "e3evolution".equals(target)) {
                return true;
            }
            if ("weight".equals(normalized) && "weightsensitivity".equals(target)) {
                return true;
            }
            if ("vm".equals(normalized) && "vmheterogeneity".equals(target)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeExperiment(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9,]", "");
    }

    private static List<ResultRow> invokeAllOrdered(ExecutorService pool, List<Callable<ResultRow>> tasks) {
        try {
            List<Future<ResultRow>> futures = pool.invokeAll(tasks);
            List<ResultRow> result = new ArrayList<>(futures.size());
            for (Future<ResultRow> future : futures) {
                result.add(future.get());
            }
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted waiting for experiment tasks", e);
        } catch (java.util.concurrent.ExecutionException e) {
            throw new RuntimeException("Experiment task failed", e.getCause());
        }
    }

    private static List<ResultRow> runE4(ExecutorService pool, String mode, int[] scales, long[] seeds,
                                         Algorithm[] algorithms, int vmCount,
                                         VmProfile vmProfile, WeightProfile weight,
                                         boolean cloudsim) {
        List<Callable<ResultRow>> tasks = new ArrayList<>();
        for (int scale : scales) {
            for (long seed : seeds) {
                Scenario scenario = createScenario(scale, vmCount, seed, vmProfile);
                Norm norm = defaultNorm(scenario);
                for (Algorithm algorithm : algorithms) {
                    final Scenario s = scenario;
                    final Norm n = norm;
                    final long sd = seed;
                    tasks.add(() -> {
                        FeatureSet features = featureForAlgorithm(algorithm);
                        return runOne(mode, "E4_main", algorithm.name(), algorithm.name(),
                                algorithm, features, weight, vmProfile,
                                features.perturbation, s, n, sd, cloudsim);
                    });
                }
            }
        }
        return invokeAllOrdered(pool, tasks);
    }

    private static List<ResultRow> runE2(ExecutorService pool, String mode, int[] scales, long[] seeds, int vmCount, boolean cloudsim) {
        Map<String, FeatureSet> variants = new HashMap<>();
        variants.put("LSCBO_full", FeatureSet.lscbo());
        variants.put("no_levy", new FeatureSet(false, true, true, true, false, Perturbation.NONE));
        variants.put("no_local_search", new FeatureSet(true, true, true, false, false, Perturbation.LEVY));
        variants.put("ls_only", new FeatureSet(false, false, false, true, true, Perturbation.NONE));
        variants.put("objective_only_pair_ls", new FeatureSet(false, false, false, true,
                Perturbation.NONE, LocalSearchMode.PAIR_OBJECTIVE_ONLY));
        variants.put("pareto_relocate_ls", new FeatureSet(false, false, false, true,
                Perturbation.NONE, LocalSearchMode.RELOCATE_PARETO));
        variants.put("cbo_base", FeatureSet.cbo());
        variants.put("greedy_reference", new FeatureSet(false, false, false, false, false, Perturbation.NONE));
        List<Callable<ResultRow>> tasks = new ArrayList<>();
        for (int scale : scales) {
            for (long seed : seeds) {
                Scenario scenario = createScenario(scale, vmCount, seed, VmProfile.DEFAULT);
                Norm norm = defaultNorm(scenario);
                for (Map.Entry<String, FeatureSet> entry : variants.entrySet()) {
                    final Scenario s = scenario;
                    final Norm n = norm;
                    final long sd = seed;
                    final String variantName = entry.getKey();
                    final FeatureSet features = entry.getValue();
                    tasks.add(() -> {
                        Algorithm algorithm;
                        if ("cbo_base".equals(variantName)) {
                            algorithm = Algorithm.CBO;
                        } else if ("greedy_reference".equals(variantName)) {
                            algorithm = Algorithm.GREEDY_LPT;
                        } else {
                            algorithm = Algorithm.LSCBO;
                        }
                        return runOne(mode, "E2_ablation", algorithm.name(), variantName,
                                algorithm, features, WeightProfile.W0_EQUAL, VmProfile.DEFAULT,
                                features.perturbation, s, n, sd, cloudsim);
                    });
                }
            }
        }
        return invokeAllOrdered(pool, tasks);
    }

    private static List<ResultRow> runE3(ExecutorService pool, String mode, int[] scales, long[] seeds, int vmCount, boolean cloudsim) {
        Map<String, FeatureSet> stages = new HashMap<>();
        stages.put("V1_CBO", FeatureSet.cbo());
        stages.put("V2_CBO_plus_Levy", new FeatureSet(true, true, true, false, false, Perturbation.LEVY));
        stages.put("V3_CBO_plus_LS", new FeatureSet(false, true, true, true, false, Perturbation.NONE));
        stages.put("V4_CBO_plus_Levy_plus_LS", FeatureSet.lscbo());
        stages.put("V5_LSCBO_final", FeatureSet.lscbo());
        List<Callable<ResultRow>> tasks = new ArrayList<>();
        for (int scale : scales) {
            for (long seed : seeds) {
                Scenario scenario = createScenario(scale, vmCount, seed, VmProfile.DEFAULT);
                Norm norm = defaultNorm(scenario);
                for (Map.Entry<String, FeatureSet> entry : stages.entrySet()) {
                    final Scenario s = scenario;
                    final Norm n = norm;
                    final long sd = seed;
                    final String stageName = entry.getKey();
                    final FeatureSet features = entry.getValue();
                    tasks.add(() -> {
                        Algorithm algorithm = stageName.startsWith("V1") ? Algorithm.CBO : Algorithm.LSCBO;
                        return runOne(mode, "E3_evolution", algorithm.name(), stageName,
                                algorithm, features, WeightProfile.W0_EQUAL, VmProfile.DEFAULT,
                                features.perturbation, s, n, sd, cloudsim);
                    });
                }
            }
        }
        return invokeAllOrdered(pool, tasks);
    }

    private static List<ResultRow> runWeightSensitivity(ExecutorService pool, String mode, int[] scales, long[] seeds,
                                                        int vmCount, boolean cloudsim) {
        List<Callable<ResultRow>> tasks = new ArrayList<>();
        for (WeightProfile weight : WeightProfile.values()) {
            for (int scale : scales) {
                for (long seed : seeds) {
                    Scenario scenario = createScenario(scale, vmCount, seed, VmProfile.DEFAULT);
                    Norm norm = defaultNorm(scenario);
                    for (Algorithm algorithm : ROBUSTNESS_ALGORITHMS) {
                        final Scenario s = scenario;
                        final Norm n = norm;
                        final long sd = seed;
                        final WeightProfile w = weight;
                        tasks.add(() -> {
                            FeatureSet features = featureForAlgorithm(algorithm);
                            return runOne(mode, "weight_sensitivity", algorithm.name(), algorithm.name(),
                                    algorithm, features, w, VmProfile.DEFAULT,
                                    features.perturbation, s, n, sd, cloudsim);
                        });
                    }
                }
            }
        }
        return invokeAllOrdered(pool, tasks);
    }

    private static List<ResultRow> runVmHeterogeneity(ExecutorService pool, String mode, int[] scales, long[] seeds,
                                                      int vmCount, boolean cloudsim) {
        VmProfile[] profiles = {
                VmProfile.HETEROGENEOUS_20_TO_1,
                VmProfile.MEDIUM_5_TO_1,
                VmProfile.NEAR_HOMOGENEOUS_1_5_TO_1
        };
        List<Callable<ResultRow>> tasks = new ArrayList<>();
        for (VmProfile profile : profiles) {
            for (int scale : scales) {
                for (long seed : seeds) {
                    Scenario scenario = createScenario(scale, vmCount, seed, profile);
                    Norm norm = defaultNorm(scenario);
                    for (Algorithm algorithm : ROBUSTNESS_ALGORITHMS) {
                        final Scenario s = scenario;
                        final Norm n = norm;
                        final long sd = seed;
                        final VmProfile p = profile;
                        tasks.add(() -> {
                            FeatureSet features = featureForAlgorithm(algorithm);
                            return runOne(mode, "vm_heterogeneity", algorithm.name(), algorithm.name(),
                                    algorithm, features, WeightProfile.W0_EQUAL,
                                    p, features.perturbation, s, n, sd, cloudsim);
                        });
                    }
                }
            }
        }
        return invokeAllOrdered(pool, tasks);
    }

    private static List<ResultRow> runPerturbation(ExecutorService pool, String mode, int[] scales, long[] seeds,
                                                   int vmCount, boolean cloudsim) {
        Perturbation[] perturbations = {
                Perturbation.LEVY,
                Perturbation.GAUSSIAN,
                Perturbation.CAUCHY,
                Perturbation.RANDOM_RESTART,
                Perturbation.NONE
        };
        List<Callable<ResultRow>> tasks = new ArrayList<>();
        for (Perturbation perturbation : perturbations) {
            for (int scale : scales) {
                for (long seed : seeds) {
                    Scenario scenario = createScenario(scale, vmCount, seed, VmProfile.DEFAULT);
                    Norm norm = defaultNorm(scenario);
                    final Scenario s = scenario;
                    final Norm n = norm;
                    final long sd = seed;
                    final Perturbation p = perturbation;
                    tasks.add(() -> {
                        FeatureSet features = new FeatureSet(p != Perturbation.NONE,
                                true, true, true, false, p);
                        return runOne(mode, "perturbation", "LSCBO", p.name(),
                                Algorithm.LSCBO, features, WeightProfile.W0_EQUAL, VmProfile.DEFAULT,
                                p, s, n, sd, cloudsim);
                    });
                }
            }
        }
        return invokeAllOrdered(pool, tasks);
    }

    private static ResultRow runOne(String mode, String experiment, String algorithmLabel,
                                    String variant, Algorithm algorithm, FeatureSet features,
                                    WeightProfile weight, VmProfile vmProfile,
                                    Perturbation perturbation, Scenario scenario, Norm norm,
                                    long seed, boolean cloudsim) {
        long start = System.currentTimeMillis();
        OptimizationResult optimized = optimize(algorithm, features, scenario, weight, norm,
                seed * 1_000_003L + experiment.hashCode() * 31L + variant.hashCode());
        SimResult sim = cloudsim ? runCloudSim(scenario, optimized.schedule) : new SimResult(-1, -1);
        ResultRow row = new ResultRow();
        row.mode = mode;
        row.experiment = experiment;
        row.algorithm = algorithmLabel;
        row.variant = variant;
        row.weightProfile = weight.label;
        row.vmProfile = vmProfile.label;
        row.perturbation = perturbation.name();
        row.taskCount = scenario.taskCount;
        row.vmCount = scenario.vmCount;
        row.seed = seed;
        row.populationSize = POPULATION_SIZE;
        row.maxIterations = MAX_ITERATIONS;
        row.initialEvaluations = optimized.initialEvaluations;
        row.iterationEvaluations = optimized.iterationEvaluations;
        row.localSearchEvaluations = optimized.localSearchEvaluations;
        row.totalEvaluations = optimized.initialEvaluations + optimized.iterationEvaluations
                + optimized.localSearchEvaluations;
        row.normMakespan = norm.makespan;
        row.normEnergy = norm.energy;
        row.normCost = norm.cost;
        row.normImbalance = norm.imbalance;
        row.makespan = optimized.metrics.makespan;
        row.energy = optimized.metrics.energy;
        row.cost = optimized.metrics.cost;
        row.imbalance = optimized.metrics.imbalance;
        row.objective = optimized.metrics.objective;
        row.cloudsimFinishedCloudlets = sim.finishedCloudlets;
        row.cloudsimMakespan = sim.cloudsimMakespan;
        row.runtimeMs = System.currentTimeMillis() - start;
        return row;
    }

    private static FeatureSet featureForAlgorithm(Algorithm algorithm) {
        if (algorithm == Algorithm.LSCBO) {
            return FeatureSet.lscbo();
        }
        if (algorithm == Algorithm.CBO) {
            return FeatureSet.cbo();
        }
        return new FeatureSet(false, true, true, false, false, Perturbation.NONE);
    }

    private static OptimizationResult optimize(Algorithm algorithm, FeatureSet features,
                                               Scenario scenario, WeightProfile weights,
                                               Norm norm, long seed) {
        Random random = new Random(seed);
        OptimizationResult result = new OptimizationResult();

        if (algorithm == Algorithm.GREEDY_LPT) {
            result.schedule = greedyReferenceSchedule(scenario);
            result.metrics = evaluate(result.schedule, scenario, weights, norm);
            result.initialEvaluations = 1;
            return result;
        }

        int dimensions = scenario.taskCount;
        double[][] population = new double[POPULATION_SIZE][dimensions];
        double[] fitness = new double[POPULATION_SIZE];
        double[][] velocities = new double[POPULATION_SIZE][dimensions];
        double[][] personalBest = new double[POPULATION_SIZE][dimensions];
        double[] personalBestFitness = new double[POPULATION_SIZE];

        result.initialEvaluations = POPULATION_SIZE;
        int globalBudget = features.localSearch ? LSCBO_GLOBAL_BUDGET : TOTAL_EVALUATION_BUDGET;
        int used = 0;

        double[] bestPosition = null;
        int[] bestSchedule = null;
        Metrics bestMetrics = null;
        double bestFitness = Double.MAX_VALUE;
        int[] greedySchedule = greedyReferenceSchedule(scenario);
        double[] greedyPosition = encodeDirectAssignment(greedySchedule, scenario.vmCount);

        for (int i = 0; i < POPULATION_SIZE; i++) {
            for (int d = 0; d < dimensions; d++) {
                population[i][d] = i == 0 ? greedyPosition[d] : random.nextDouble();
                velocities[i][d] = 0.1 * random.nextGaussian();
                personalBest[i][d] = population[i][d];
            }
            int[] schedule = decodeDirectAssignment(population[i], scenario);
            Metrics metrics = evaluate(schedule, scenario, weights, norm);
            fitness[i] = metrics.objective;
            personalBestFitness[i] = metrics.objective;
            if (metrics.objective < bestFitness) {
                bestFitness = metrics.objective;
                bestPosition = population[i].clone();
                bestSchedule = schedule;
                bestMetrics = metrics;
            }
        }

        while (result.initialEvaluations + used < globalBudget) {
            double progress = (double) (result.initialEvaluations + used) / globalBudget;
            int[] top = topIndices(fitness);
            for (int i = 0; i < POPULATION_SIZE; i++) {
                if (result.initialEvaluations + used >= globalBudget) {
                    break;
                }
                double[] candidate = makeCandidate(algorithm, features, population, fitness, velocities,
                        personalBest, bestPosition, top, i, progress, random);
                int[] schedule = decodeDirectAssignment(candidate, scenario);
                Metrics metrics = evaluate(schedule, scenario, weights, norm);
                used++;
                double newFitness = metrics.objective;

                if (algorithm == Algorithm.PSO && newFitness < personalBestFitness[i]) {
                    personalBestFitness[i] = newFitness;
                    personalBest[i] = candidate.clone();
                }

                if (algorithm == Algorithm.PSO || algorithm == Algorithm.GWO || algorithm == Algorithm.WOA) {
                    population[i] = candidate;
                    fitness[i] = newFitness;
                } else if (newFitness <= fitness[i]) {
                    population[i] = candidate;
                    fitness[i] = newFitness;
                }
                if (newFitness < bestFitness) {
                    bestFitness = newFitness;
                    bestPosition = candidate.clone();
                    bestSchedule = schedule;
                    bestMetrics = metrics;
                }

                if (algorithm == Algorithm.LSCBO && features.levyPhase
                        && progress < 1.0 / 3.0
                        && result.initialEvaluations + used < globalBudget
                        && random.nextDouble() < LEVY_FRACTION) {
                    double[] levy = population[i].clone();
                    double decay = Math.max(0.05,
                            1.0 - (double) (result.initialEvaluations + used) / globalBudget);
                    for (int d = 0; d < dimensions; d++) {
                        levy[d] = clamp(levy[d] + LEVY_ALPHA * decay
                                * additivePerturbation(features.perturbation, random));
                    }
                    int[] levySchedule = decodeDirectAssignment(levy, scenario);
                    Metrics levyMetrics = evaluate(levySchedule, scenario, weights, norm);
                    used++;
                    if (levyMetrics.objective <= fitness[i]) {
                        population[i] = levy;
                        fitness[i] = levyMetrics.objective;
                    }
                    if (levyMetrics.objective < bestFitness) {
                        bestFitness = levyMetrics.objective;
                        bestPosition = levy.clone();
                        bestSchedule = levySchedule;
                        bestMetrics = levyMetrics;
                    }
                }
            }
        }

        result.iterationEvaluations = used;
        if (features.localSearch) {
            int remaining = TOTAL_EVALUATION_BUDGET - result.initialEvaluations - used;
            long localSeed = 0x5DEECE66DL ^ (scenario.seed * 1_000_003L)
                    ^ ((long) scenario.taskCount << 17)
                    ^ ((long) scenario.vmProfile.ordinal() << 9)
                    ^ weights.ordinal();
            LocalSearchResult local = localSearch(bestSchedule, scenario, weights, norm,
                    new Random(localSeed), remaining, features.localSearchMode);
            result.localSearchEvaluations = local.evaluations;
            if (local.metrics.objective < bestFitness) {
                bestSchedule = local.schedule;
                bestMetrics = local.metrics;
            }
        }

        result.schedule = bestSchedule;
        result.metrics = bestMetrics;
        return result;
    }

    private static double[] makeCandidate(Algorithm algorithm, FeatureSet features,
                                          double[][] population, double[] fitness,
                                          double[][] velocities, double[][] personalBest,
                                          double[] bestPosition, int[] top,
                                          int i, double progress, Random random) {
        int dimensions = population[i].length;
        double[] current = population[i];
        double[] candidate = new double[dimensions];

        if (features.lsOnly) {
            return current.clone();
        }

        switch (algorithm) {
            case LSCBO:
            case CBO:
                cboCandidate(features, population, fitness, i, progress, random, candidate);
                break;
            case WOA:
                double a = 2.0 * (1.0 - progress);
                double a2 = -1.0 - progress;
                double r = random.nextDouble();
                double coefficientA = 2.0 * a * r - a;
                double coefficientC = 2.0 * random.nextDouble();
                double branch = random.nextDouble();
                double spiralL = (a2 - 1.0) * random.nextDouble() + 1.0;
                double[] target = Math.abs(coefficientA) < 1.0
                        ? bestPosition : population[random.nextInt(POPULATION_SIZE)];
                for (int d = 0; d < dimensions; d++) {
                    if (branch < 0.5) {
                        double distance = Math.abs(coefficientC * target[d] - current[d]);
                        candidate[d] = target[d] - coefficientA * distance;
                    } else {
                        double distance = Math.abs(bestPosition[d] - current[d]);
                        candidate[d] = distance * Math.exp(spiralL)
                                * Math.cos(2.0 * Math.PI * spiralL) + bestPosition[d];
                    }
                }
                break;
            case GWO:
                for (int d = 0; d < dimensions; d++) {
                    double gwoA = 2.0 * (1.0 - progress);
                    double x1 = gwoAttract(population[top[0]][d], current[d], gwoA, random);
                    double x2 = gwoAttract(population[top[1]][d], current[d], gwoA, random);
                    double x3 = gwoAttract(population[top[2]][d], current[d], gwoA, random);
                    candidate[d] = (x1 + x2 + x3) / 3.0;
                }
                break;
            case PSO:
                double inertia = 0.9 - 0.5 * progress;
                for (int d = 0; d < dimensions; d++) {
                    velocities[i][d] = inertia * velocities[i][d]
                            + 2.0 * random.nextDouble() * (personalBest[i][d] - current[d])
                            + 2.0 * random.nextDouble() * (bestPosition[d] - current[d]);
                    velocities[i][d] = clip(velocities[i][d], -0.2, 0.2);
                    candidate[d] = current[d] + velocities[i][d];
                }
                break;
            case AOA:
                double mop = 1.0 - Math.pow(Math.max(0.0, progress), 1.0 / 5.0);
                double moa = 0.2 + 0.8 * progress;
                double arithmeticSpan = 0.499;
                for (int d = 0; d < dimensions; d++) {
                    if (random.nextDouble() < moa) {
                        if (random.nextDouble() > 0.5) {
                            candidate[d] = bestPosition[d] / (mop + Math.ulp(1.0)) * arithmeticSpan;
                        } else {
                            candidate[d] = bestPosition[d] * mop * arithmeticSpan;
                        }
                    } else if (random.nextDouble() > 0.5) {
                        candidate[d] = bestPosition[d] - mop * arithmeticSpan;
                    } else {
                        candidate[d] = bestPosition[d] + mop * arithmeticSpan;
                    }
                }
                break;
            case GREEDY_LPT:
                return current.clone();
            default:
                throw new IllegalStateException("Unexpected algorithm: " + algorithm);
        }

        for (int d = 0; d < dimensions; d++) {
            candidate[d] = clamp(candidate[d]);
        }
        return candidate;
    }

    private static void cboCandidate(FeatureSet features, double[][] population, double[] fitness,
                                     int i, double progress, Random random, double[] candidate) {
        int dimensions = population[i].length;
        int pairCount = POPULATION_SIZE / 2;
        int pair = i % pairCount;
        int coyote = pair;
        int badger = Math.min(pair + pairCount, POPULATION_SIZE - 1);

        System.arraycopy(population[i], 0, candidate, 0, dimensions);
        if (progress < 1.0 / 3.0 || (!features.phase2 && progress < 2.0 / 3.0)
                || (!features.phase3 && progress >= 2.0 / 3.0)) {
            for (int d = 0; d < dimensions; d++) {
                double prey = 0.5 * (population[coyote][d] + population[badger][d]);
                double sense = random.nextDouble() - 0.5;
                candidate[d] *= 1.0 + sense * Math.tanh(prey);
            }
        } else if (progress < 2.0 / 3.0 && features.phase2) {
            int first = random.nextInt(dimensions);
            int second = random.nextInt(dimensions);
            while (second == first && dimensions > 1) {
                second = random.nextInt(dimensions);
            }
            double local = (progress - 1.0 / 3.0) * 3.0;
            double angle = Math.PI * (1.0 - Math.min(1.0, local));
            double cosine = Math.cos(angle);
            double sine = Math.sin(angle);
            double preyFirst = 0.5 * (population[coyote][first] + population[badger][first]);
            double preySecond = 0.5 * (population[coyote][second] + population[badger][second]);
            double offsetFirst = candidate[first] - preyFirst;
            double offsetSecond = candidate[second] - preySecond;
            candidate[first] = preyFirst + cosine * offsetFirst - sine * offsetSecond;
            candidate[second] = preySecond + sine * offsetFirst + cosine * offsetSecond;
        } else {
            int split = Math.min(pairCount, POPULATION_SIZE);
            int start = i < split ? 0 : split;
            int end = i < split ? split : POPULATION_SIZE;
            int leader = start;
            for (int index = start + 1; index < end; index++) {
                if (fitness[index] < fitness[leader]) {
                    leader = index;
                }
            }
            for (int d = 0; d < dimensions; d++) {
                candidate[d] = 0.5 * (candidate[d] + population[leader][d]);
            }
        }
    }

    static final class LocalSearchResult {
        int[] schedule;
        Metrics metrics;
        int evaluations;
    }

    private static LocalSearchResult localSearch(int[] startingSchedule, Scenario scenario,
                                                 WeightProfile weights, Norm norm,
                                                 Random random, int maxTrials,
                                                 LocalSearchMode mode) {
        LocalSearchResult result = new LocalSearchResult();
        int[] best = startingSchedule.clone();
        Metrics bestMetrics = evaluate(best, scenario, weights, norm);
        int evaluations = 0;

        while (evaluations < maxTrials) {
            int roundLimit = Math.min(maxTrials, evaluations + LOCAL_SEARCH_ROUND_TRIALS);
            int[] roundBest = null;
            Metrics roundBestMetrics = bestMetrics;
            while (evaluations < roundLimit) {
                int first = random.nextInt(best.length);
                int[] trial = best.clone();
                if (mode == LocalSearchMode.RELOCATE_PARETO) {
                    int targetVm = random.nextInt(scenario.vmCount);
                    if (targetVm == trial[first]) {
                        continue;
                    }
                    trial[first] = targetVm;
                } else {
                    int second = random.nextInt(best.length);
                    if (first == second || best[first] == best[second]) {
                        continue;
                    }
                    int firstVm = trial[first];
                    trial[first] = trial[second];
                    trial[second] = firstVm;
                }
                Metrics metrics = evaluate(trial, scenario, weights, norm);
                evaluations++;
                boolean admissible = mode == LocalSearchMode.PAIR_OBJECTIVE_ONLY
                        || componentWiseNonWorse(metrics, bestMetrics);
                if (admissible
                        && metrics.objective < roundBestMetrics.objective) {
                    roundBest = trial;
                    roundBestMetrics = metrics;
                }
            }
            if (roundBest != null) {
                best = roundBest;
                bestMetrics = roundBestMetrics;
            }
        }

        result.schedule = best;
        result.metrics = bestMetrics;
        result.evaluations = evaluations;
        return result;
    }

    private static boolean componentWiseNonWorse(Metrics candidate, Metrics incumbent) {
        return noWorse(candidate.makespan, incumbent.makespan)
                && noWorse(candidate.energy, incumbent.energy)
                && noWorse(candidate.cost, incumbent.cost)
                && noWorse(candidate.imbalance, incumbent.imbalance);
    }

    private static boolean noWorse(double candidate, double incumbent) {
        double tolerance = 1e-12 * Math.max(1.0, Math.abs(incumbent));
        return candidate <= incumbent + tolerance;
    }

    static Scenario createScenario(int taskCount, int vmCount, long seed, VmProfile profile) {
        double[] taskLengths = new double[taskCount];
        double[] vmMips = new double[vmCount];
        Random taskRandom = new Random(10_000L + seed * 97L + taskCount * 13L);
        Random vmRandom = new Random(20_000L + seed * 131L + profile.ordinal() * 17L);

        for (int i = 0; i < taskCount; i++) {
            taskLengths[i] = 1000 + taskRandom.nextInt(19_001);
        }
        for (int i = 0; i < vmCount; i++) {
            vmMips[i] = profile.minMips + vmRandom.nextInt(profile.maxMips - profile.minMips + 1);
        }
        return new Scenario(taskCount, vmCount, seed, profile, taskLengths, vmMips);
    }

    static Norm defaultNorm(Scenario scenario) {
        int[] reference = greedyReferenceSchedule(scenario);
        Metrics metrics = evaluateRaw(reference, scenario, WeightProfile.W0_EQUAL, null);
        return new Norm(metrics);
    }

    static int[] greedyReferenceSchedule(Scenario scenario) {
        Integer[] order = new Integer[scenario.taskCount];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        Arrays.sort(order, (a, b) -> Double.compare(scenario.taskLengths[b], scenario.taskLengths[a]));
        int[] schedule = new int[scenario.taskCount];
        double[] loads = new double[scenario.vmCount];
        for (int task : order) {
            int bestVm = 0;
            double bestFinish = Double.MAX_VALUE;
            for (int vm = 0; vm < scenario.vmCount; vm++) {
                double finish = loads[vm] + scenario.taskLengths[task] / scenario.vmMips[vm];
                if (finish < bestFinish) {
                    bestFinish = finish;
                    bestVm = vm;
                }
            }
            schedule[task] = bestVm;
            loads[bestVm] = bestFinish;
        }
        return schedule;
    }

    static int[] decodeDirectAssignment(double[] position, Scenario scenario) {
        int[] schedule = new int[position.length];
        for (int task = 0; task < position.length; task++) {
            int vm = (int) Math.floor(clamp(position[task]) * scenario.vmCount);
            schedule[task] = Math.min(scenario.vmCount - 1, vm);
        }
        return schedule;
    }

    static double[] encodeDirectAssignment(int[] schedule, int vmCount) {
        double[] position = new double[schedule.length];
        for (int task = 0; task < schedule.length; task++) {
            position[task] = (schedule[task] + 0.5) / vmCount;
        }
        return position;
    }

    static Metrics evaluate(int[] schedule, Scenario scenario, WeightProfile weights, Norm norm) {
        return evaluateRaw(schedule, scenario, weights, norm);
    }

    static Metrics evaluateRaw(int[] schedule, Scenario scenario, WeightProfile weights, Norm norm) {
        double[] vmLoads = loads(schedule, scenario);
        double totalLoad = 0.0;
        double maxLoad = 0.0;
        for (double load : vmLoads) {
            totalLoad += load;
            maxLoad = Math.max(maxLoad, load);
        }
        double makespan = maxLoad;
        double avgLoad = totalLoad / scenario.vmCount;
        double squaredDeviation = 0.0;
        for (double load : vmLoads) {
            double delta = load - avgLoad;
            squaredDeviation += delta * delta;
        }
        double loadStdDev = Math.sqrt(squaredDeviation / scenario.vmCount);
        double imbalance = avgLoad <= 0 ? 0.0 : loadStdDev / avgLoad;

        double energy = 0.0;
        double cost = 0.0;
        for (int vm = 0; vm < scenario.vmCount; vm++) {
            double util = makespan <= 0 ? 0.0 : Math.min(1.0, vmLoads[vm] / makespan);
            double maxPower = 250.0;
            double idlePower = 0.6 * maxPower;
            energy += (idlePower + (maxPower - idlePower) * Math.pow(util, 3.0))
                    * makespan / 3600.0;
            double mipsSpan = Math.max(1.0, scenario.vmProfile.maxMips - scenario.vmProfile.minMips);
            double normalizedMips = (scenario.vmMips[vm] - scenario.vmProfile.minMips) / mipsSpan;
            double pricePerSecond = 0.05 + 0.15 * Math.sqrt(Math.max(0.0, normalizedMips));
            cost += vmLoads[vm] * pricePerSecond;
        }

        double objective;
        if (norm == null) {
            objective = makespan;
        } else {
            objective = weights.makespan * makespan / norm.makespan
                    + weights.energy * energy / norm.energy
                    + weights.cost * cost / norm.cost
                    + weights.imbalance * imbalance / norm.imbalance;
        }
        return new Metrics(makespan, energy, cost, imbalance, objective);
    }

    private static double[] loads(int[] schedule, Scenario scenario) {
        double[] loads = new double[scenario.vmCount];
        for (int task = 0; task < schedule.length; task++) {
            int vm = schedule[task];
            loads[vm] += scenario.taskLengths[task] / scenario.vmMips[vm];
        }
        return loads;
    }

    private static SimResult runCloudSim(Scenario scenario, int[] schedule) {
        CloudSimPlus simulation = new CloudSimPlus();
        createDatacenter(simulation, scenario.vmCount);
        FixedScheduleBroker broker = new FixedScheduleBroker(simulation, schedule);
        broker.submitVmList(createVms(scenario));
        broker.submitCloudletList(createCloudlets(scenario));
        simulation.start();

        double maxFinish = 0.0;
        for (Cloudlet cloudlet : broker.getCloudletFinishedList()) {
            maxFinish = Math.max(maxFinish, cloudlet.getFinishTime());
        }
        return new SimResult(broker.getCloudletFinishedList().size(), maxFinish);
    }

    private static DatacenterSimple createDatacenter(CloudSimPlus simulation, int vmCount) {
        List<Host> hosts = new ArrayList<>();
        int hostCount = Math.max(10, (int) Math.ceil(vmCount / 4.0));
        for (int i = 0; i < hostCount; i++) {
            List<Pe> peList = new ArrayList<>();
            for (int j = 0; j < 4; j++) {
                peList.add(new PeSimple(5000));
            }
            Host host = new HostSimple(16_384, 10_000, 1_000_000, peList);
            hosts.add(host);
        }
        return new DatacenterSimple(simulation, hosts, new VmAllocationPolicySimple());
    }

    private static List<Vm> createVms(Scenario scenario) {
        List<Vm> vms = new ArrayList<>();
        for (int i = 0; i < scenario.vmCount; i++) {
            Vm vm = new VmSimple(i, scenario.vmMips[i], 1);
            vm.setRam(2048).setBw(1000).setSize(10_000);
            vms.add(vm);
        }
        return vms;
    }

    private static List<Cloudlet> createCloudlets(Scenario scenario) {
        List<Cloudlet> cloudlets = new ArrayList<>();
        for (int i = 0; i < scenario.taskCount; i++) {
            Cloudlet cloudlet = new CloudletSimple(i, (long) scenario.taskLengths[i], 1);
            cloudlet.setUtilizationModelCpu(new UtilizationModelFull());
            cloudlets.add(cloudlet);
        }
        return cloudlets;
    }

    static final class FixedScheduleBroker extends DatacenterBrokerSimple {
        private final int[] schedule;

        FixedScheduleBroker(CloudSimPlus simulation, int[] schedule) {
            super(simulation);
            this.schedule = schedule;
        }

        @Override
        protected Vm defaultVmMapper(Cloudlet cloudlet) {
            List<Vm> created = getVmCreatedList();
            if (created.isEmpty()) {
                return super.defaultVmMapper(cloudlet);
            }
            int task = (int) cloudlet.getId();
            if (task >= 0 && task < schedule.length) {
                int vm = Math.max(0, Math.min(created.size() - 1, schedule[task]));
                return created.get(vm);
            }
            return created.get(Math.floorMod(task, created.size()));
        }
    }

    private static void writeRaw(Path file, List<ResultRow> rows) throws IOException {
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8))) {
            out.println("mode,experiment,algorithm,variant,weightProfile,vmProfile,perturbation,taskCount,vmCount,seed,populationSize,maxIterations,initialEvaluations,iterationEvaluations,localSearchEvaluations,totalEvaluations,normMakespan,normEnergy,normCost,normImbalance,makespan,energy,cost,imbalance,objective,cloudsimFinishedCloudlets,cloudsimMakespan,runtimeMs");
            for (ResultRow row : rows) {
                out.println(row.csv());
            }
        }
    }

    private static void writeSummary(Path file, List<ResultRow> rows) throws IOException {
        Map<String, Summary> summaries = new HashMap<>();
        for (ResultRow row : rows) {
            String key = row.mode + "|" + row.experiment + "|" + row.algorithm + "|" + row.variant
                    + "|" + row.weightProfile + "|" + row.vmProfile + "|" + row.perturbation
                    + "|N" + row.taskCount;
            summaries.computeIfAbsent(key, k -> new Summary()).add(row);
        }
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8))) {
            out.println("group,n,meanMakespan,meanEnergy,meanCost,meanImbalance,meanObjective");
            summaries.entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> out.println(entry.getValue().csv(entry.getKey())));
        }
    }

    private static void writeMetadata(Path file, Args args, List<ResultRow> rows, long runtimeMs) throws IOException {
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8))) {
            out.println("key,value");
            out.println("createdAt," + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            out.println("mode," + args.mode);
            out.println("rows," + rows.size());
            out.println("runtimeMs," + runtimeMs);
            out.println("cloudsimValidation," + args.cloudsim);
            out.println("seedRange," + seedDescription(args));
            out.println("experiments," + args.experiments);
            out.println("parallelism," + args.parallelism);
            out.println("cloudsimVersion,8.0.0");
            out.println("populationSize," + POPULATION_SIZE);
            out.println("maxIterations," + MAX_ITERATIONS);
            out.println("totalEvaluationBudget," + TOTAL_EVALUATION_BUDGET);
            out.println("lscboGlobalBudget," + LSCBO_GLOBAL_BUDGET);
            out.println("localSearchMaxTrials," + LOCAL_SEARCH_MAX_TRIALS);
            out.println("localSearchRoundTrials," + LOCAL_SEARCH_ROUND_TRIALS);
            out.println("levyBeta," + LEVY_BETA);
            out.println("levyAlpha," + LEVY_ALPHA);
            out.println("levyFraction," + LEVY_FRACTION);
            out.println("objective,weighted normalized makespan energy cost imbalance");
            out.println("decoder,direct continuous-to-VM assignment; any task-to-VM schedule is representable");
            out.println("imbalance,standard deviation of VM completion times divided by mean completion time");
            out.println("initialization,common greedy LPT earliest-finish warm start plus random positions");
            out.println("datacenter,1 datacenter, 4 cores per host, host count expands when needed to allocate all VMs");
        }
    }

    private static void writeReport(Path file, Args args, List<ResultRow> rows, long runtimeMs) throws IOException {
        try (BufferedWriter out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            out.write("LSCBO claim-preserving data run\n");
            out.write("mode: " + args.mode + "\n");
            out.write("rows: " + rows.size() + "\n");
            out.write("runtimeMs: " + runtimeMs + "\n");
            out.write("cloudsimValidation: " + args.cloudsim + "\n");
            out.write("seedRange: " + seedDescription(args) + "\n");
            out.write("experiments: " + args.experiments + "\n");
            long finishedOk = rows.stream()
                    .filter(r -> !args.cloudsim || r.cloudsimFinishedCloudlets == r.taskCount)
                    .count();
            out.write("finishedCloudSimRowsOk: " + finishedOk + "/" + rows.size() + "\n");
            out.write("rawCsv: claim_" + args.mode.toLowerCase(Locale.ROOT) + "_raw.csv\n");
            out.write("summaryCsv: claim_" + args.mode.toLowerCase(Locale.ROOT) + "_summary.csv\n");
        }
    }

    private static double perturbation(Perturbation perturbation, Random random) {
        switch (perturbation) {
            case LEVY:
                return clip(levyStep(random), -1.0, 1.0);
            case GAUSSIAN:
                return clip(random.nextGaussian(), -1.0, 1.0);
            case CAUCHY:
                return clip(Math.tan(Math.PI * (random.nextDouble() - 0.5)), -1.0, 1.0);
            case RANDOM_RESTART:
                return random.nextDouble() < 0.05 ? (2 * random.nextDouble() - 1) : 0.0;
            case NONE:
            default:
                return 0.0;
        }
    }

    private static double additivePerturbation(Perturbation perturbation, Random random) {
        switch (perturbation) {
            case LEVY:
                return levyStep(random);
            case GAUSSIAN:
                return random.nextGaussian();
            case CAUCHY:
                return Math.tan(Math.PI * (random.nextDouble() - 0.5));
            case RANDOM_RESTART:
                return 2.0 * random.nextDouble() - 1.0;
            case NONE:
            default:
                return 0.0;
        }
    }

    private static double levyStep(Random random) {
        double u = random.nextGaussian() * LEVY_SIGMA_U;
        double v = random.nextGaussian();
        return u / Math.pow(Math.abs(v) + 1e-10, 1.0 / LEVY_BETA);
    }

    private static double levySigma(double beta) {
        double numerator = Math.exp(Gamma.logGamma(1 + beta)) * Math.sin(Math.PI * beta / 2.0);
        double denominator = Math.exp(Gamma.logGamma((1 + beta) / 2.0)) * beta
                * Math.pow(2.0, (beta - 1.0) / 2.0);
        return Math.pow(numerator / denominator, 1.0 / beta);
    }

    private static double gwoAttract(double leader, double current, double a, Random random) {
        double A = 2.0 * a * random.nextDouble() - a;
        double C = 2.0 * random.nextDouble();
        return leader - A * Math.abs(C * leader - current);
    }

    private static int[] topIndices(double[] fitness) {
        int[] top = {0, 0, 0};
        double[] best = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
        for (int i = 0; i < fitness.length; i++) {
            double value = fitness[i];
            if (value < best[0]) {
                best[2] = best[1];
                top[2] = top[1];
                best[1] = best[0];
                top[1] = top[0];
                best[0] = value;
                top[0] = i;
            } else if (value < best[1]) {
                best[2] = best[1];
                top[2] = top[1];
                best[1] = value;
                top[1] = i;
            } else if (value < best[2]) {
                best[2] = value;
                top[2] = i;
            }
        }
        return top;
    }

    private static int maxIndex(double[] values) {
        int index = 0;
        for (int i = 1; i < values.length; i++) {
            if (values[i] > values[index]) {
                index = i;
            }
        }
        return index;
    }

    private static int minIndex(double[] values) {
        int index = 0;
        for (int i = 1; i < values.length; i++) {
            if (values[i] < values[index]) {
                index = i;
            }
        }
        return index;
    }

    private static List<Integer> tasksOnVmSortedByLength(int[] schedule, Scenario scenario, int vm) {
        List<Integer> tasks = new ArrayList<>();
        for (int i = 0; i < schedule.length; i++) {
            if (schedule[i] == vm) {
                tasks.add(i);
            }
        }
        tasks.sort((a, b) -> Double.compare(scenario.taskLengths[b], scenario.taskLengths[a]));
        return tasks;
    }

    private static double clamp(double value) {
        return clip(value, 0.0, 1.0);
    }

    private static double clip(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static long[] rangeSeeds(int start, int end) {
        long[] values = new long[end - start + 1];
        for (int i = 0; i < values.length; i++) {
            values[i] = start + i;
        }
        return values;
    }

    private static void silenceLogs() {
        try {
            ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger)
                    org.slf4j.LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
            root.setLevel(ch.qos.logback.classic.Level.ERROR);
        } catch (Throwable ignored) {
            // Logging stack is optional.
        }
    }
}

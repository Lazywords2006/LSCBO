package com.edcbo.research.claim;

import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import static com.edcbo.research.claim.ClaimPreservingCloudSimExperiment.*;

/** Greedy-initialized diagnostic controls beside the cold-population E4 path. */
public final class ColdDirectPairStudy {
    private static final String[] METHODS = {"full_cold", "pair_only_9400", "pair_only_10000"};
    private static final WeightProfile WEIGHTS = WeightProfile.W0_EQUAL;
    private static final VmProfile PROFILE = VmProfile.DEFAULT;
    private record Observation(String method, int tasks, long seed, Metrics metrics, Norm norm,
                               int[] schedule, int[] localStart, long scoreCalls, int moves,
                               int coreCandidates, int localCandidates, double elapsedMs) {}

    private static Observation run(Scenario scenario, int method, boolean ablation) {
        ColdAudit audit = new ColdAudit();
        COLD_AUDIT.set(audit);
        try {
            long start = System.nanoTime();
            int[] reference = greedyReferenceSchedule(scenario);
            Norm norm = new Norm(evaluateRaw(reference, scenario, WEIGHTS, null));
            int[] schedule;
            Metrics metrics;
            int coreCandidates;
            int localCandidates;
            if (method == 0) {
                String experiment = ablation ? "E2_ablation" : "E4_main";
                String variant = ablation ? "LSCBO_full" : "LSCBO";
                long coreSeed = scenario.seed * 1_000_003L + experiment.hashCode() * 31L + variant.hashCode();
                OptimizationResult result = optimize(Algorithm.LSCBO, FeatureSet.lscbo(), scenario,
                        WEIGHTS, norm, coreSeed);
                schedule = result.schedule;
                metrics = result.metrics;
                coreCandidates = result.initialEvaluations + result.iterationEvaluations;
                localCandidates = result.localSearchEvaluations;
            } else {
                long localSeed = 0x5DEECE66DL ^ (scenario.seed * 1_000_003L)
                        ^ ((long) scenario.taskCount << 17)
                        ^ ((long) scenario.vmProfile.ordinal() << 9) ^ WEIGHTS.ordinal();
                LocalSearchResult result = localSearch(reference, scenario, WEIGHTS, norm,
                        new Random(localSeed), method == 1 ? 9400 : 10000, LocalSearchMode.PAIR_PARETO);
                schedule = result.schedule;
                metrics = result.metrics;
                coreCandidates = 0;
                localCandidates = result.evaluations;
            }
            double elapsedMs = (System.nanoTime() - start) / 1_000_000.0;
            long expectedCalls = method == 1 ? 9402 : 10002;
            if (audit.scoreCalls != expectedCalls) {
                throw new IllegalStateException("Scoring-call mismatch: " + audit.scoreCalls);
            }
            return new Observation(METHODS[method], scenario.taskCount, scenario.seed, metrics, norm,
                    schedule, audit.localStart, audit.scoreCalls, audit.acceptedExchanges,
                    coreCandidates, localCandidates, elapsedMs);
        } finally {
            COLD_AUDIT.remove();
        }
    }

    private static String hash(int[] schedule) throws Exception {
        ByteBuffer bytes = ByteBuffer.allocate(schedule.length * Integer.BYTES);
        for (int vm : schedule) bytes.putInt(vm);
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.array()));
    }

    private static double[] metricValues(Metrics m) {
        return new double[]{m.makespan, m.energy, m.cost, m.imbalance, m.objective};
    }

    private static Map<String, String[]> canonical(Path path, boolean ablation) throws Exception {
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        List<String> header = Arrays.asList(lines.get(0).replace("\ufeff", "").split(","));
        String[] names = {"makespan", "energy", "cost", "imbalance", "objective"};
        Map<String, String[]> expected = new HashMap<>();
        for (int line = 1; line < lines.size(); line++) {
            String[] row = lines.get(line).split(",");
            if (!row[header.indexOf("experiment")].equals(ablation ? "E2_ablation" : "E4_main")
                    || !row[header.indexOf("variant")].equals(ablation ? "LSCBO_full" : "LSCBO")) continue;
            int count = Integer.parseInt(row[header.indexOf("taskCount")]);
            if (count < 100 || count > 1000) continue;
            String[] values = new String[names.length];
            for (int i = 0; i < names.length; i++) values[i] = row[header.indexOf(names[i])];
            String key = count + "/" + row[header.indexOf("seed")];
            if (expected.put(key, values) != null) throw new IllegalStateException("Duplicate " + key);
        }
        if (expected.size() != 300) throw new IllegalStateException("Expected 300 candidate full blocks");
        return expected;
    }

    private static void validateCanonical(Observation value, Map<String, String[]> canonical) {
        String key = value.tasks + "/" + value.seed;
        String[] expected = canonical.get(key);
        double[] actual = metricValues(value.metrics);
        for (int i = 0; i < actual.length; i++) {
            String rounded = String.format(Locale.ROOT, "%.8f", actual[i]);
            if (!rounded.equals(expected[i])) throw new IllegalStateException("Candidate reproduction differs at "
                    + key + " metric " + i + ": " + rounded + " vs " + expected[i]);
        }
    }

    private static void write(PrintWriter output, Observation row, int repetition, int order) throws Exception {
        int changes = 0;
        for (int i = 0; i < row.schedule.length; i++) if (row.localStart[i] != row.schedule[i]) changes++;
        output.printf(Locale.ROOT, "%s,%d,%d,%d,%d,%d,%d,%d,%d,%d,%.9f", row.method, row.tasks,
                row.seed, repetition, order, row.scoreCalls, row.coreCandidates, row.localCandidates,
                row.moves, changes, row.elapsedMs);
        for (double value : metricValues(row.metrics)) output.printf(Locale.ROOT, ",%.17g", value);
        output.printf(Locale.ROOT, ",%.17g,%.17g,%.17g,%.17g,%s,%s%n", row.norm.makespan,
                row.norm.energy, row.norm.cost, row.norm.imbalance, hash(row.localStart), hash(row.schedule));
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("main.csv output-directory repeats");
        Locale.setDefault(Locale.ROOT);
        Path out = Path.of(args[1]);
        Files.createDirectories(out);
        int repeats = Integer.parseInt(args[2]);
        if (repeats < 1) throw new IllegalArgumentException("repeats must be positive");
        Map<String, String[]> expectedMain = canonical(Path.of(args[0]), false);
        for (int tasks = 100; tasks <= 1000; tasks += 100) {
            for (long seed = 43; seed <= 72; seed++) {
                Scenario scenario = createScenario(tasks, 50, seed, PROFILE);
                validateCanonical(run(scenario, 0, false), expectedMain);
            }
            System.out.println("Batch-rental E4 LSCBO reproduced: N=" + tasks + ", 30 blocks");
        }
        for (int cycle = 0; cycle < 2; cycle++) {
            for (int tasks = 100; tasks <= 1000; tasks += 100) {
                Scenario scenario = createScenario(tasks, 50, 43 + cycle, PROFILE);
                for (int method = 0; method < METHODS.length; method++) run(scenario, method, false);
            }
        }
        try (PrintWriter raw = new PrintWriter(Files.newBufferedWriter(out.resolve("direct_pair_raw.csv")))) {
            raw.println("method,taskCount,seed,repetition,order,actualScoringCalls,coreCandidates,localCandidates,acceptedExchanges,netChangedTasks,elapsedMs,makespan,energy,cost,imbalance,objective,normMakespan,normEnergy,normCost,normImbalance,localStartSHA256,finalSHA256");
            Map<String, String> previousHashes = new HashMap<>();
            for (int repetition = 0; repetition < repeats; repetition++) {
                int block = 0;
                for (int tasks = 100; tasks <= 1000; tasks += 100) {
                    for (long seed = 43; seed <= 72; seed++) {
                        Scenario scenario = createScenario(tasks, 50, seed, PROFILE);
                        for (int order = 0; order < METHODS.length; order++) {
                            int method = (block + repetition + order) % METHODS.length;
                            Observation row = run(scenario, method, false);
                            if (method == 0) validateCanonical(row, expectedMain);
                            String key = row.method + "/" + tasks + "/" + seed;
                            String digest = hash(row.schedule);
                            String previous = previousHashes.putIfAbsent(key, digest);
                            if (previous != null && !previous.equals(digest)) throw new IllegalStateException("Nondeterministic " + key);
                            write(raw, row, repetition, order);
                        }
                        block++;
                    }
                }
                raw.flush();
                if (raw.checkError()) throw new IllegalStateException("CSV write failure");
                System.out.println("Completed repetition " + (repetition + 1) + "/" + repeats);
            }
        }
        Files.writeString(out.resolve("direct_pair_validation.txt"),
                "PASS: all 300 E4 LSCBO blocks reproduce all five candidate metrics at eight decimals. No E2 experiment is run.\n"
                + "PASS: actual calls are full=10002, direct9400=9402 and direct10000=10002 including normalization/local entry.\n"
                + "PASS: repeated final assignment hashes deterministic; no equality of cold full and Greedy-start controls assumed.\n"
                + "Direct paths deliberately begin with Greedy-LPT; they are initialization controls, not cold population methods.\n"
                + "Time includes Greedy reference, normalization and optimize/local entry; excludes scenario generation/hashing/CSV.\n"
                + "Single application thread; two complete warmup cycles after 300-block candidate reproduction.\n"
                + "Repetitions=" + repeats + "\n");
        System.out.println("All direct study checks passed.");
    }
}

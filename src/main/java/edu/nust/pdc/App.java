    package edu.nust.pdc;

import edu.nust.pdc.config.ScaleProfile;
import edu.nust.pdc.data.Dataset;
import edu.nust.pdc.data.DatasetIO;
import edu.nust.pdc.data.MatchResult;
import edu.nust.pdc.dataset.DatasetGenerator;
import edu.nust.pdc.matching.SequentialMatcher;
import edu.nust.pdc.net.MasterServer;
import edu.nust.pdc.net.WorkerClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public final class App {
    private App() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            printUsage();
            return;
        }

        String command = args[0].toLowerCase(java.util.Locale.ROOT);
        switch (command) {
            case "generate-data" -> generateData(args);
            case "baseline" -> runBaseline(args);
            case "master" -> runMaster(args);
            case "worker" -> runWorker(args);
            case "benchmark" -> runBenchmark(args);
            default -> printUsage();
        }
    }

    private static void generateData(String[] args) throws Exception {
        ScaleProfile profile = ScaleProfile.fromName(argValue(args, "--scale", "small"));
        Path output = Paths.get(argValue(args, "--output", "data/" + profile.datasetName));
        DatasetGenerator.generateToDirectory(profile, output);
        System.out.println("Generated dataset at " + output.toAbsolutePath());
    }

    private static void runBaseline(String[] args) throws Exception {
        Path datasetPath = Paths.get(argValue(args, "--dataset", "data/small"));
        Path output = Paths.get(argValue(args, "--output", "results/baseline-output.csv"));
        Dataset dataset = DatasetIO.loadDataset(datasetPath);
        long start = System.nanoTime();
        List<MatchResult> results = new SequentialMatcher(dataset).match();
        long elapsed = System.nanoTime() - start;
        DatasetIO.writeResults(output, results);
        System.out.println("Baseline completed in " + formatMillis(elapsed) + " ms");
        System.out.println("Results written to " + output.toAbsolutePath());
    }

    private static void runMaster(String[] args) throws Exception {
        Path datasetPath = Paths.get(argValue(args, "--dataset", "data/small"));
        int port = Integer.parseInt(argValue(args, "--port", "5000"));
        int workers = Integer.parseInt(argValue(args, "--workers", "3"));
        int batchSize = Integer.parseInt(argValue(args, "--batch-size", "1024"));
        int maxCandidates = Integer.parseInt(argValue(args, "--max-candidates", "0"));
        Path output = Paths.get(argValue(args, "--output", "results/distributed-output.csv"));
        Dataset dataset = DatasetIO.loadDataset(datasetPath);
        if (maxCandidates > 0) {
            System.out.println("Max candidates per rider: " + maxCandidates);
        }
        long totalStart = System.nanoTime();
        long computeNanos = new MasterServer(dataset, port, batchSize, maxCandidates).serve(output);
        long totalElapsed = System.nanoTime() - totalStart;
        System.out.println("Distributed completed in " + formatMillis(computeNanos) + " ms (compute only)");
        System.out.println("Distributed total time   " + formatMillis(totalElapsed) + " ms (includes connection wait)");
        System.out.println("Distributed output written to " + output.toAbsolutePath());
    }

    private static void runWorker(String[] args) throws Exception {
        String workerId = argValue(args, "--id", "worker-1");
        String host = argValue(args, "--host", "127.0.0.1");
        int port = Integer.parseInt(argValue(args, "--port", "5000"));
        int threads = Integer.parseInt(argValue(args, "--threads", "4"));
        new WorkerClient(workerId, host, port, threads).run();
    }

    private static void runBenchmark(String[] args) throws Exception {
        Path datasetPath = Paths.get(argValue(args, "--dataset", "data/small"));
        Dataset dataset = DatasetIO.loadDataset(datasetPath);
        List<MatchResult> baselineResults = new SequentialMatcher(dataset).match();
        DatasetIO.writeResults(Paths.get("results/benchmark-baseline.csv"), baselineResults);
        System.out.println("Benchmark baseline generated for " + dataset.riders.size() + " riders.");
    }

    private static void printUsage() {
        System.out.println("Commands:");
        System.out.println("  generate-data --scale small|medium|large --output data/<name>");
        System.out.println("  baseline --dataset data/<name> --output results/baseline-output.csv");
        System.out.println("  master --dataset data/<name> --workers 3 --port 5000 --batch-size 1024 --output results/distributed-output.csv");
        System.out.println("  worker --id worker-1 --host 127.0.0.1 --port 5000 --threads 4");
        System.out.println("  benchmark --dataset data/<name>");
    }

    private static String argValue(String[] args, String key, String defaultValue) {
        for (int index = 0; index < args.length - 1; index++) {
            if (key.equals(args[index])) {
                return args[index + 1];
            }
        }
        return defaultValue;
    }

    private static String formatMillis(long nanos) {
        return String.format(java.util.Locale.ROOT, "%.3f", nanos / 1_000_000.0);
    }
}
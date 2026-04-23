package edu.nust.pdc;

import edu.nust.pdc.config.ScaleProfile;
import edu.nust.pdc.data.Dataset;
import edu.nust.pdc.data.DatasetIO;
import edu.nust.pdc.data.MatchResult;
import edu.nust.pdc.dataset.DatasetGenerator;
import edu.nust.pdc.matching.SequentialMatcher;

import java.nio.file.Path;
import java.nio.file.Paths;
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
            default -> {
                System.err.println("Unknown command: " + command);
                printUsage();
            }
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

    private static void printUsage() {
        System.out.println("Commands:");
        System.out.println("  generate-data --scale small|medium|large --output data/<n>");
        System.out.println("  baseline --dataset data/<n> --output results/baseline-output.csv");
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
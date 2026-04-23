package edu.nust.pdc;

import edu.nust.pdc.config.ScaleProfile;
import edu.nust.pdc.dataset.DatasetGenerator;

import java.nio.file.Path;
import java.nio.file.Paths;

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

    private static void printUsage() {
        System.out.println("Commands:");
        System.out.println("  generate-data --scale small|medium|large --output data/<name>");
    }

    private static String argValue(String[] args, String key, String defaultValue) {
        for (int index = 0; index < args.length - 1; index++) {
            if (key.equals(args[index])) {
                return args[index + 1];
            }
        }
        return defaultValue;
    }
}
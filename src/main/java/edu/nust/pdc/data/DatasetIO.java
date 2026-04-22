package edu.nust.pdc.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class DatasetIO {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private DatasetIO() {}

    public static void writeDataset(Path directory, Dataset dataset) throws IOException {
        Files.createDirectories(directory);
        writeRiders(directory.resolve("riders.csv"), dataset.riders);
        writeDrivers(directory.resolve("drivers.csv"), dataset.drivers);
        writeGrid(directory.resolve("grid.csv"), dataset.grid);
        try (BufferedWriter writer = Files.newBufferedWriter(directory.resolve("metadata.json"), StandardCharsets.UTF_8)) {
            GSON.toJson(new Metadata(dataset.name, dataset.searchRadius,
                    dataset.grid.width, dataset.grid.height, dataset.grid.cellSize), writer);
        }
    }

    public static Dataset loadDataset(Path directory) throws IOException {
        Metadata metadata;
        try (BufferedReader reader = Files.newBufferedReader(directory.resolve("metadata.json"), StandardCharsets.UTF_8)) {
            metadata = GSON.fromJson(reader, Metadata.class);
        }
        List<Rider>  riders  = readRiders(directory.resolve("riders.csv"));
        List<Driver> drivers = readDrivers(directory.resolve("drivers.csv"));
        TrafficGrid  grid    = readGrid(directory.resolve("grid.csv"),
                metadata.gridWidth, metadata.gridHeight, metadata.cellSize);
        return new Dataset(metadata.name, riders, drivers, grid, metadata.searchRadius);
    }

    public static Dataset loadWorkerDataset(Path directory) throws IOException {
        Metadata metadata;
        try (BufferedReader reader = Files.newBufferedReader(directory.resolve("metadata.json"), StandardCharsets.UTF_8)) {
            metadata = GSON.fromJson(reader, Metadata.class);
        }
        List<Driver> drivers = readDrivers(directory.resolve("drivers.csv"));
        TrafficGrid  grid    = readGrid(directory.resolve("grid.csv"),
                metadata.gridWidth, metadata.gridHeight, metadata.cellSize);
        return new Dataset(metadata.name, List.of(), drivers, grid, metadata.searchRadius);
    }

    public static void writeResults(Path file, List<MatchResult> results) throws IOException {
        Files.createDirectories(file.getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            writer.write("riderId,driverId,travelTime,matched");
            writer.newLine();
            for (MatchResult r : results) {
                String tt = r.travelTime == null ? "" : String.format(java.util.Locale.ROOT, "%.6f", r.travelTime);
                writer.write(r.riderId + "," + (r.driverId == null ? "" : r.driverId) + "," + tt + "," + r.matched);
                writer.newLine();
            }
        }
    }

    public static List<MatchResult> readResults(Path file) throws IOException {
        List<MatchResult> results = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            reader.readLine(); // skip header
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                String[]  parts      = line.split(",", -1);
                Integer   driverId   = parts[1].isEmpty() ? null : Integer.parseInt(parts[1]);
                Double    travelTime = parts[2].isEmpty() ? null : Double.parseDouble(parts[2]);
                results.add(new MatchResult(Integer.parseInt(parts[0]), driverId, travelTime, Boolean.parseBoolean(parts[3])));
            }
        }
        return results;
    }

    // ── private helpers ──────────────────────────────────────────────────────

    private static void writeRiders(Path file, List<Rider> riders) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            w.write("riderId,pickupX,pickupY"); w.newLine();
            for (Rider r : riders) {
                w.write(r.riderId + "," + fmt(r.pickupX) + "," + fmt(r.pickupY)); w.newLine();
            }
        }
    }

    private static void writeDrivers(Path file, List<Driver> drivers) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            w.write("driverId,locationX,locationY,available"); w.newLine();
            for (Driver d : drivers) {
                w.write(d.driverId + "," + fmt(d.locationX) + "," + fmt(d.locationY) + "," + d.available); w.newLine();
            }
        }
    }

    private static void writeGrid(Path file, TrafficGrid grid) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            w.write("width," + grid.width + ",height," + grid.height + ",cellSize," + fmt(grid.cellSize)); w.newLine();
            for (int y = 0; y < grid.height; y++) {
                for (int x = 0; x < grid.width; x++) {
                    if (x > 0) w.write(',');
                    w.write(fmt(grid.congestion[y][x]));
                }
                w.newLine();
            }
        }
    }

    private static List<Rider> readRiders(Path file) throws IOException {
        List<Rider> list = new ArrayList<>();
        try (BufferedReader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            r.readLine();
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] p = line.split(",");
                list.add(new Rider(Integer.parseInt(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2])));
            }
        }
        return list;
    }

    private static List<Driver> readDrivers(Path file) throws IOException {
        List<Driver> list = new ArrayList<>();
        try (BufferedReader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            r.readLine();
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] p = line.split(",");
                list.add(new Driver(Integer.parseInt(p[0]), Double.parseDouble(p[1]),
                        Double.parseDouble(p[2]), Boolean.parseBoolean(p[3])));
            }
        }
        return list;
    }

    private static TrafficGrid readGrid(Path file, int width, int height, double cellSize) throws IOException {
        double[][] congestion = new double[height][width];
        try (BufferedReader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            r.readLine(); // header
            String line; int row = 0;
            while ((line = r.readLine()) != null && row < height) {
                String[] p = line.split(",");
                for (int col = 0; col < width; col++) congestion[row][col] = Double.parseDouble(p[col]);
                row++;
            }
        }
        return new TrafficGrid(width, height, cellSize, congestion);
    }

    private static String fmt(double v) {
        return String.format(java.util.Locale.ROOT, "%.6f", v);
    }

    private static final class Metadata {
        String name; int searchRadius; int gridWidth; int gridHeight; double cellSize;
        Metadata(String name, int searchRadius, int gridWidth, int gridHeight, double cellSize) {
            this.name = name; this.searchRadius = searchRadius;
            this.gridWidth = gridWidth; this.gridHeight = gridHeight; this.cellSize = cellSize;
        }
    }
}
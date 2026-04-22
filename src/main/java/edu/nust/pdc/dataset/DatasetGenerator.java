package edu.nust.pdc.dataset;

import edu.nust.pdc.config.ScaleProfile;
import edu.nust.pdc.data.Dataset;
import edu.nust.pdc.data.DatasetIO;
import edu.nust.pdc.data.Driver;
import edu.nust.pdc.data.Rider;
import edu.nust.pdc.data.TrafficGrid;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class DatasetGenerator {
    private DatasetGenerator() {}

    public static Dataset generate(ScaleProfile profile) {
        Random random = new Random(20260421L + profile.ordinal());
        double extent = profile.gridSize * profile.cellSize;

        List<Driver> drivers = new ArrayList<>(profile.driverCount);
        for (int i = 0; i < profile.driverCount; i++)
            drivers.add(new Driver(i, random.nextDouble() * extent, random.nextDouble() * extent, true));

        List<Rider> riders = new ArrayList<>(profile.riderCount);
        for (int i = 0; i < profile.riderCount; i++)
            riders.add(new Rider(i, random.nextDouble() * extent, random.nextDouble() * extent));

        double[][] congestion = new double[profile.gridSize][profile.gridSize];
        for (int row = 0; row < profile.gridSize; row++) {
            for (int col = 0; col < profile.gridSize; col++) {
                double wave  = 0.75 + 0.35 * Math.sin((row + 1) * 0.22) + 0.25 * Math.cos((col + 1) * 0.19);
                double noise = 0.15 * random.nextDouble();
                congestion[row][col] = clamp(0.5, 2.0, wave + noise);
            }
        }

        TrafficGrid grid = new TrafficGrid(profile.gridSize, profile.gridSize, profile.cellSize, congestion);
        return new Dataset(profile.datasetName, riders, drivers, grid, profile.searchRadius);
    }

    public static Path generateToDirectory(ScaleProfile profile, Path directory) throws IOException {
        DatasetIO.writeDataset(directory, generate(profile));
        return directory;
    }

    private static double clamp(double min, double max, double value) {
        return Math.max(min, Math.min(max, value));
    }
}
package edu.nust.pdc.config;

public enum ScaleProfile {
    SMALL("small",   10_000,    50_000,  50, 10.0, 3),
    MEDIUM("medium", 100_000,  500_000, 100, 10.0, 4),
    LARGE("large",   200_000, 1_000_000, 150, 10.0, 5);

    public final String datasetName;
    public final int driverCount;
    public final int riderCount;
    public final int gridSize;
    public final double cellSize;
    public final int searchRadius;

    ScaleProfile(String datasetName, int driverCount, int riderCount,
                 int gridSize, double cellSize, int searchRadius) {
        this.datasetName  = datasetName;
        this.driverCount  = driverCount;
        this.riderCount   = riderCount;
        this.gridSize     = gridSize;
        this.cellSize     = cellSize;
        this.searchRadius = searchRadius;
    }

    public static ScaleProfile fromName(String name) {
        return ScaleProfile.valueOf(name.trim().toUpperCase());
    }
}
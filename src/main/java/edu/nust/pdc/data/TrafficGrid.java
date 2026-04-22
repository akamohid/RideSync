package edu.nust.pdc.data;

public class TrafficGrid {
    public int width;
    public int height;
    public double cellSize;
    public double[][] congestion;

    public TrafficGrid() {}

    public TrafficGrid(int width, int height, double cellSize, double[][] congestion) {
        this.width      = width;
        this.height     = height;
        this.cellSize   = cellSize;
        this.congestion = congestion;
    }

    public double congestionAt(double x, double y) {
        int cellX = clampIndex((int) Math.floor(x / cellSize), width);
        int cellY = clampIndex((int) Math.floor(y / cellSize), height);
        return congestion[cellY][cellX];
    }

    private int clampIndex(int value, int bound) {
        if (value < 0)      return 0;
        if (value >= bound) return bound - 1;
        return value;
    }
}
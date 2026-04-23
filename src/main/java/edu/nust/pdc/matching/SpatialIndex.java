package edu.nust.pdc.matching;

import edu.nust.pdc.data.Candidate;
import edu.nust.pdc.data.Driver;
import edu.nust.pdc.data.Rider;
import edu.nust.pdc.data.TrafficGrid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class SpatialIndex {
    private final int width;
    private final int height;
    private final double cellSize;
    private final List<Driver>[][] buckets;
    private final TrafficGrid grid;

    @SuppressWarnings("unchecked")
    public SpatialIndex(List<Driver> drivers, TrafficGrid grid) {
        this.width = grid.width;
        this.height = grid.height;
        this.cellSize = grid.cellSize;
        this.grid = grid;
        this.buckets = new ArrayList[height][width];
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                buckets[row][col] = new ArrayList<>();
            }
        }
        for (Driver driver : drivers) {
            int cellX = clampIndex((int) Math.floor(driver.locationX / cellSize), width);
            int cellY = clampIndex((int) Math.floor(driver.locationY / cellSize), height);
            buckets[cellY][cellX].add(driver);
        }
    }

    public List<Candidate> rankCandidates(Rider rider, int searchRadius) {
        List<Candidate> candidates = new ArrayList<>();
        int centerX = clampIndex((int) Math.floor(rider.pickupX / cellSize), width);
        int centerY = clampIndex((int) Math.floor(rider.pickupY / cellSize), height);
        int radius = Math.max(0, searchRadius);
        int minRow = Math.max(0, centerY - radius);
        int maxRow = Math.min(height - 1, centerY + radius);
        int minCol = Math.max(0, centerX - radius);
        int maxCol = Math.min(width - 1, centerX + radius);
        for (int row = minRow; row <= maxRow; row++) {
            for (int col = minCol; col <= maxCol; col++) {
                for (Driver driver : buckets[row][col]) {
                    candidates.add(new Candidate(driver.driverId, score(rider, driver)));
                }
            }
        }
        candidates.sort(Comparator.naturalOrder());
        return candidates;
    }

    public double score(Rider rider, Driver driver) {
        double dx = rider.pickupX - driver.locationX;
        double dy = rider.pickupY - driver.locationY;
        double distance = Math.sqrt(dx * dx + dy * dy);
        double midX = (rider.pickupX + driver.locationX) / 2.0;
        double midY = (rider.pickupY + driver.locationY) / 2.0;
        return distance * grid.congestionAt(midX, midY);
    }

    public int clampIndex(int value, int bound) {
        if (value < 0) {
            return 0;
        }
        if (value >= bound) {
            return bound - 1;
        }
        return value;
    }

    /**
     * High-performance candidate ranking returning only sorted driverIds.
     * Uses primitive arrays to avoid millions of Candidate object allocations.
     * Used by the distributed worker path for maximum throughput.
     *
     * @param maxK if positive, return only the top-K closest candidates
     *             (reduces network transfer and master resolution work).
     *             Use 0 or negative to return all candidates.
     */
    public int[] rankCandidateIds(Rider rider, int searchRadius, int maxK) {
        int centerX = clampIndex((int) Math.floor(rider.pickupX / cellSize), width);
        int centerY = clampIndex((int) Math.floor(rider.pickupY / cellSize), height);
        int radius = Math.max(0, searchRadius);
        int minRow = Math.max(0, centerY - radius);
        int maxRow = Math.min(height - 1, centerY + radius);
        int minCol = Math.max(0, centerX - radius);
        int maxCol = Math.min(width - 1, centerX + radius);

        // Count candidates first to pre-allocate arrays
        int count = 0;
        for (int row = minRow; row <= maxRow; row++) {
            for (int col = minCol; col <= maxCol; col++) {
                count += buckets[row][col].size();
            }
        }
        if (count == 0) return new int[0];

        // Fill parallel arrays (avoids Candidate object allocation)
        double[] scores = new double[count];
        int[] ids = new int[count];
        int idx = 0;
        for (int row = minRow; row <= maxRow; row++) {
            for (int col = minCol; col <= maxCol; col++) {
                for (Driver driver : buckets[row][col]) {
                    scores[idx] = score(rider, driver);
                    ids[idx] = driver.driverId;
                    idx++;
                }
            }
        }

        // Determine how many results to return
        int k = (maxK > 0 && maxK < count) ? maxK : count;

        // Sort by score (using index array to avoid object creation)
        // Simple insertion sort for small arrays; Arrays.sort for larger
        if (count <= 64) {
            for (int i = 1; i < count; i++) {
                double keyScore = scores[i];
                int keyId = ids[i];
                int j = i - 1;
                while (j >= 0 && (scores[j] > keyScore ||
                       (scores[j] == keyScore && ids[j] > keyId))) {
                    scores[j + 1] = scores[j];
                    ids[j + 1] = ids[j];
                    j--;
                }
                scores[j + 1] = keyScore;
                ids[j + 1] = keyId;
            }
            // Truncate to top-K
            if (k < count) {
                int[] truncated = new int[k];
                System.arraycopy(ids, 0, truncated, 0, k);
                return truncated;
            }
            return ids;
        } else {
            // For larger arrays, build index and sort
            Integer[] order = new Integer[count];
            for (int i = 0; i < count; i++) order[i] = i;
            java.util.Arrays.sort(order, (a, b) -> {
                int cmp = Double.compare(scores[a], scores[b]);
                return cmp != 0 ? cmp : Integer.compare(ids[a], ids[b]);
            });
            int[] sortedIds = new int[k];
            for (int i = 0; i < k; i++) sortedIds[i] = ids[order[i]];
            return sortedIds;
        }
    }

    /** Backward-compatible overload (returns all candidates). */
    public int[] rankCandidateIds(Rider rider, int searchRadius) {
        return rankCandidateIds(rider, searchRadius, 0);
    }

    public List<Driver> allDriversInCell(int row, int col) {
        return Collections.unmodifiableList(buckets[row][col]);
    }
}
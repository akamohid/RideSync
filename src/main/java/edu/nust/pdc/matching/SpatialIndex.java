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

    // Struct-of-arrays (SoA) layout for the high-frequency distributed path.
    // Instead of iterating List<Driver> objects (heap-scattered, pointer-chased),
    // the X/Y coordinates for all drivers in each cell are packed into contiguous
    // primitive arrays. Sequential access within a cell fits in L1/L2 cache lines,
    // eliminating per-driver cache misses in the rankCandidateIds() hot loop.
    // rankCandidates() (sequential baseline) still uses the original buckets.
    private final double[][][] cellX;
    private final double[][][] cellY;
    private final int[][][] cellIds;

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

        // Build SoA parallel arrays from the populated buckets.
        this.cellX   = new double[height][width][];
        this.cellY   = new double[height][width][];
        this.cellIds = new int[height][width][];
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                List<Driver> cell = buckets[row][col];
                int n = cell.size();
                double[] xs  = new double[n];
                double[] ys  = new double[n];
                int[]    ids = new int[n];
                for (int k = 0; k < n; k++) {
                    Driver d = cell.get(k);
                    xs[k]  = d.locationX;
                    ys[k]  = d.locationY;
                    ids[k] = d.driverId;
                }
                this.cellX[row][col]   = xs;
                this.cellY[row][col]   = ys;
                this.cellIds[row][col] = ids;
            }
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
     * <p>The fill loop uses the SoA (struct-of-arrays) cell layout so that
     * driver X/Y coordinates are read sequentially from contiguous double[]
     * arrays rather than chasing pointers to scattered Driver heap objects.
     * For a cell with N drivers, all N X-coordinates occupy N×8 bytes —
     * typically 1–2 cache lines — compared to N separate heap object accesses
     * that each may miss L1/L2 cache.
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

        // Count candidates first to pre-allocate arrays (SoA: use array length)
        int count = 0;
        for (int row = minRow; row <= maxRow; row++) {
            for (int col = minCol; col <= maxCol; col++) {
                count += cellX[row][col].length;
            }
        }
        if (count == 0) return new int[0];

        // Fill parallel arrays using SoA layout.
        // bX[k] / bY[k] are contiguous doubles — sequential cache-line reads.
        // This eliminates the per-driver pointer dereference from List<Driver>.
        double[] scores = new double[count];
        int[] ids = new int[count];
        int idx = 0;
        final double riderX = rider.pickupX;
        final double riderY = rider.pickupY;
        for (int row = minRow; row <= maxRow; row++) {
            for (int col = minCol; col <= maxCol; col++) {
                final double[] bX  = cellX[row][col];
                final double[] bY  = cellY[row][col];
                final int[]    bId = cellIds[row][col];
                final int n = bX.length;
                for (int k = 0; k < n; k++) {
                    double dx = riderX - bX[k];
                    double dy = riderY - bY[k];
                    double distance = Math.sqrt(dx * dx + dy * dy);
                    double midX = (riderX + bX[k]) * 0.5;
                    double midY = (riderY + bY[k]) * 0.5;
                    scores[idx] = distance * grid.congestionAt(midX, midY);
                    ids[idx]    = bId[k];
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
            // For larger arrays, sort a primitive index array to avoid boxing overhead.
            int[] order = new int[count];
            for (int i = 0; i < count; i++) {
                order[i] = i;
            }
            sortIndices(order, scores, ids, 0, count - 1);
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

    private static void sortIndices(int[] order, double[] scores, int[] ids, int left, int right) {
        int i = left;
        int j = right;
        int pivot = order[left + ((right - left) >>> 1)];

        while (i <= j) {
            while (compare(order[i], pivot, scores, ids) < 0) {
                i++;
            }
            while (compare(order[j], pivot, scores, ids) > 0) {
                j--;
            }
            if (i <= j) {
                int tmp = order[i];
                order[i] = order[j];
                order[j] = tmp;
                i++;
                j--;
            }
        }

        if (left < j) {
            sortIndices(order, scores, ids, left, j);
        }
        if (i < right) {
            sortIndices(order, scores, ids, i, right);
        }
    }

    private static int compare(int a, int b, double[] scores, int[] ids) {
        int cmp = Double.compare(scores[a], scores[b]);
        return cmp != 0 ? cmp : Integer.compare(ids[a], ids[b]);
    }
}
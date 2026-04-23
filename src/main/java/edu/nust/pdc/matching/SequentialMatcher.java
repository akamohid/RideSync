package edu.nust.pdc.matching;

import edu.nust.pdc.data.Candidate;
import edu.nust.pdc.data.Dataset;
import edu.nust.pdc.data.Driver;
import edu.nust.pdc.data.MatchResult;
import edu.nust.pdc.data.Rider;

import java.util.ArrayList;
import java.util.List;

public final class SequentialMatcher {
    private final Dataset dataset;
    private final SpatialIndex spatialIndex;

    public SequentialMatcher(Dataset dataset) {
        this.dataset = dataset;
        this.spatialIndex = new SpatialIndex(dataset.drivers, dataset.grid);
    }

    public List<MatchResult> match() {
        boolean[] available = new boolean[dataset.drivers.size()];
        for (int index = 0; index < dataset.drivers.size(); index++) {
            available[index] = dataset.drivers.get(index).available;
        }
        List<MatchResult> results = new ArrayList<>(dataset.riders.size());
        for (Rider rider : dataset.riders) {
            List<Candidate> candidates = spatialIndex.rankCandidates(rider, dataset.searchRadius);
            MatchResult result = selectCandidate(rider, candidates, available);
            results.add(result);
        }
        return results;
    }

    private MatchResult selectCandidate(Rider rider, List<Candidate> candidates, boolean[] available) {
        for (Candidate candidate : candidates) {
            int index = candidate.driverId;
            if (index >= 0 && index < available.length && available[index]) {
                available[index] = false;
                return new MatchResult(rider.riderId, candidate.driverId, candidate.travelTime, true);
            }
        }
        return new MatchResult(rider.riderId, null, null, false);
    }
}
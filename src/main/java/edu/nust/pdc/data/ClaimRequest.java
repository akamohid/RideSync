package edu.nust.pdc.data;

import java.util.List;
import java.util.Map;

public class ClaimRequest {
    public Integer riderIndex;
    public Integer riderId;
    public List<Integer> candidates;
    public Map<Integer, Double> candidateScores;

    public ClaimRequest() {
    }

    public ClaimRequest(Integer riderIndex, Integer riderId, List<Integer> candidates, Map<Integer, Double> candidateScores) {
        this.riderIndex = riderIndex;
        this.riderId = riderId;
        this.candidates = candidates;
        this.candidateScores = candidateScores;
    }
}
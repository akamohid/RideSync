package edu.nust.pdc.data;

public class MatchResult {
    public int riderId;
    public Integer driverId;
    public Double travelTime;
    public boolean matched;

    public MatchResult() {}

    public MatchResult(int riderId, Integer driverId, Double travelTime, boolean matched) {
        this.riderId     = riderId;
        this.driverId    = driverId;
        this.travelTime  = travelTime;
        this.matched     = matched;
    }
}
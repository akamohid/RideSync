package edu.nust.pdc.data;

public class Candidate implements Comparable<Candidate> {
    public int driverId;
    public double travelTime;

    public Candidate() {
    }

    public Candidate(int driverId, double travelTime) {
        this.driverId = driverId;
        this.travelTime = travelTime;
    }

    @Override
    public int compareTo(Candidate other) {
        int timeCompare = Double.compare(this.travelTime, other.travelTime);
        if (timeCompare != 0) {
            return timeCompare;
        }
        return Integer.compare(this.driverId, other.driverId);
    }
}
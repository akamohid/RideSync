package edu.nust.pdc.data;

public class Rider {
    public int riderId;
    public double pickupX;
    public double pickupY;

    public Rider() {}

    public Rider(int riderId, double pickupX, double pickupY) {
        this.riderId  = riderId;
        this.pickupX  = pickupX;
        this.pickupY  = pickupY;
    }
}
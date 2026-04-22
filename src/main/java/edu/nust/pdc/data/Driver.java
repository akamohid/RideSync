package edu.nust.pdc.data;

public class Driver {
    public int driverId;
    public double locationX;
    public double locationY;
    public boolean available;

    public Driver() {}

    public Driver(int driverId, double locationX, double locationY, boolean available) {
        this.driverId   = driverId;
        this.locationX  = locationX;
        this.locationY  = locationY;
        this.available  = available;
    }
}
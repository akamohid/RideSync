package edu.nust.pdc.data;

import java.util.List;

public class Dataset {
    public String name;
    public List<Rider> riders;
    public List<Driver> drivers;
    public TrafficGrid grid;
    public int searchRadius;

    public Dataset() {}

    public Dataset(String name, List<Rider> riders, List<Driver> drivers,
                   TrafficGrid grid, int searchRadius) {
        this.name         = name;
        this.riders       = riders;
        this.drivers      = drivers;
        this.grid         = grid;
        this.searchRadius = searchRadius;
    }
}
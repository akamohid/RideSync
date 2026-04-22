package edu.nust.pdc.data;

import java.util.List;

public class Batch {
    public int batchId;
    public int startIndex;
    public List<Rider> riders;

    public Batch() {}

    public Batch(int batchId, int startIndex, List<Rider> riders) {
        this.batchId    = batchId;
        this.startIndex = startIndex;
        this.riders     = riders;
    }
}
package edu.nust.pdc.data;

public class ClaimResponse {
    public Integer riderIndex;
    public Integer riderId;
    public Integer grantedDriverId;
    public Double travelTime;

    public ClaimResponse() {
    }

    public ClaimResponse(Integer riderIndex, Integer riderId, Integer grantedDriverId, Double travelTime) {
        this.riderIndex = riderIndex;
        this.riderId = riderId;
        this.grantedDriverId = grantedDriverId;
        this.travelTime = travelTime;
    }
}
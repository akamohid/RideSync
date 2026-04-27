package edu.nust.pdc.data;

import java.util.List;

public class ProtocolMessage {
    public String type;
    public String workerId;
    public Integer batchId;
    public Integer riderIndex;
    public Integer riderCount;
    public Integer riderId;
    public Integer grantedDriverId;
    public Double travelTime;
    public Integer expectedWorkers;
    public Integer threadsPerWorker;
    public Integer batchSize;
    public Integer searchRadius;
    public String datasetName;
    public String datasetPath;
    public String claimFilePath;
    public String message;

    public List<Rider> riders;
    public List<Driver> drivers;
    public List<Candidate> candidates;
    public List<ClaimRequest> claimRequests;
    public List<ClaimResponse> claimResponses;
    public List<MatchResult> results;
    public TrafficGrid grid;

    public static ProtocolMessage of(String type) {
        ProtocolMessage message = new ProtocolMessage();
        message.type = type;
        return message;
    }
}
package edu.nust.pdc;

import edu.nust.pdc.data.Dataset;
import edu.nust.pdc.data.Driver;
import edu.nust.pdc.data.MatchResult;
import edu.nust.pdc.data.Rider;
import edu.nust.pdc.data.TrafficGrid;
import edu.nust.pdc.matching.SequentialMatcher;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class SequentialMatcherTest {
    @Test
    void matchesClosestAvailableDriverDeterministically() {
        List<Rider> riders = List.of(
                new Rider(0, 1.0, 1.0),
                new Rider(1, 8.0, 8.0)
        );
        List<Driver> drivers = List.of(
                new Driver(0, 0.0, 0.0, true),
                new Driver(1, 9.0, 9.0, true)
        );
        double[][] congestion = {{1.0, 1.0}, {1.0, 1.0}};
        Dataset dataset = new Dataset("test", riders, drivers, new TrafficGrid(2, 2, 10.0, congestion), 2);

        List<MatchResult> results = new SequentialMatcher(dataset).match();

        Assertions.assertEquals(2, results.size());
        Assertions.assertEquals(0, results.get(0).driverId);
        Assertions.assertEquals(1, results.get(1).driverId);
    }
}
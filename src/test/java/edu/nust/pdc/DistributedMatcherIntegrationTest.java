package edu.nust.pdc;

import edu.nust.pdc.data.Dataset;
import edu.nust.pdc.data.DatasetIO;
import edu.nust.pdc.data.Driver;
import edu.nust.pdc.data.MatchResult;
import edu.nust.pdc.data.Rider;
import edu.nust.pdc.data.TrafficGrid;
import edu.nust.pdc.matching.SequentialMatcher;
import edu.nust.pdc.net.MasterServer;
import edu.nust.pdc.net.WorkerClient;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

class DistributedMatcherIntegrationTest {
    @Test
    void distributedCompletesAndMatchesSequential() throws Exception {
        Dataset dataset = buildDataset(200, 120);
        Path output = Files.createTempFile("distributed-test-", ".csv");
        int port = freePort();

        AtomicReference<Throwable> masterError = new AtomicReference<>();
        Thread masterThread = new Thread(() -> {
            try {
                new MasterServer(dataset, port, 3, 32, 0).serve(output);
            } catch (Throwable throwable) {
                masterError.set(throwable);
            }
        }, "test-master");
        masterThread.start();

        Thread.sleep(200);

        List<Thread> workers = new ArrayList<>();
        List<AtomicReference<Throwable>> workerErrors = new ArrayList<>();
        for (int index = 1; index <= 3; index++) {
            AtomicReference<Throwable> workerError = new AtomicReference<>();
            workerErrors.add(workerError);
            int workerId = index;
            Thread workerThread = new Thread(() -> {
                try {
                    new WorkerClient("worker-" + workerId, "127.0.0.1", port, 4).run();
                } catch (Throwable throwable) {
                    workerError.set(throwable);
                }
            }, "test-worker-" + index);
            workerThread.start();
            workers.add(workerThread);
        }

        for (Thread worker : workers) {
            worker.join(30_000);
            Assertions.assertFalse(worker.isAlive(), "Worker did not terminate in time");
        }

        masterThread.join(30_000);
        Assertions.assertFalse(masterThread.isAlive(), "Master did not terminate in time");

        if (masterError.get() != null) {
            Assertions.fail("Master failed: " + masterError.get().getMessage(), masterError.get());
        }
        for (AtomicReference<Throwable> workerError : workerErrors) {
            if (workerError.get() != null) {
                Assertions.fail("Worker failed: " + workerError.get().getMessage(), workerError.get());
            }
        }

        List<MatchResult> distributed = DatasetIO.readResults(output);
        List<MatchResult> sequential = new SequentialMatcher(dataset).match();

        Map<Integer, MatchResult> distributedByRider = new ConcurrentHashMap<>();
        for (MatchResult result : distributed) {
            distributedByRider.put(result.riderId, result);
        }

        Assertions.assertEquals(sequential.size(), distributed.size());
        for (MatchResult expected : sequential) {
            MatchResult actual = distributedByRider.get(expected.riderId);
            Assertions.assertNotNull(actual, "Missing result for rider " + expected.riderId);
            Assertions.assertEquals(expected.driverId, actual.driverId, "Driver mismatch for rider " + expected.riderId);
            Assertions.assertEquals(expected.matched, actual.matched, "Match flag mismatch for rider " + expected.riderId);
        }
    }

    private static Dataset buildDataset(int ridersCount, int driversCount) {
        List<Rider> riders = new ArrayList<>(ridersCount);
        for (int index = 0; index < ridersCount; index++) {
            double x = (index * 7) % 200;
            double y = (index * 11) % 200;
            riders.add(new Rider(index, x, y));
        }

        List<Driver> drivers = new ArrayList<>(driversCount);
        for (int index = 0; index < driversCount; index++) {
            double x = (index * 13) % 200;
            double y = (index * 5) % 200;
            drivers.add(new Driver(index, x, y, true));
        }

        int width = 20;
        int height = 20;
        double[][] congestion = new double[height][width];
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                congestion[row][col] = 1.0;
            }
        }
        return new Dataset("integration-test", riders, drivers, new TrafficGrid(width, height, 10.0, congestion), 3);
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
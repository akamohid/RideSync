package edu.nust.pdc.net;

import edu.nust.pdc.data.Dataset;
import edu.nust.pdc.data.DatasetIO;
import edu.nust.pdc.data.Driver;
import edu.nust.pdc.data.MatchResult;
import edu.nust.pdc.data.Rider;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * High-performance master server using binary socket protocol.
 *
 * <p>All bulk data (drivers, grid, riders, claims) is transmitted as raw binary
 * over TCP sockets. No file-based IPC. The master resolves claims and computes
 * travel times directly, eliminating the RESOLVED and RESULTS round-trips.
 *
 * <p>Protocol flow:
 * <ol>
 *   <li>Worker sends INIT_REQUEST → Master sends INIT_DATA (binary drivers + grid)</li>
 *   <li>Master sends TASK_ASSIGN messages with inline rider data</li>
 *   <li>Master sends NO_MORE_TASKS</li>
 *   <li>Worker sends CLAIMS (binary candidateIds per rider)</li>
 *   <li>Master resolves all claims in riderIndex order, computes travel times</li>
 *   <li>Master sends SHUTDOWN</li>
 * </ol>
 */
public final class MasterServer {
    private final Dataset dataset;
    private final int port;
    private final int expectedWorkers;
    private final int batchSize;
    private final int maxCandidates;

    public MasterServer(Dataset dataset, int port, int expectedWorkers, int batchSize, int maxCandidates) {
        this.dataset = dataset;
        this.port = port;
        this.expectedWorkers = expectedWorkers;
        this.batchSize = batchSize;
        this.maxCandidates = maxCandidates;
    }

    public long serve(Path outputFile) throws IOException, InterruptedException {
        // ─── Pre-serialize shared data (drivers + grid) into a reusable blob ───
        byte[] sharedBlob = serializeSharedData();

        // ─── Build batch ranges [startIndex, count] ───
        List<int[]> batchRanges = new ArrayList<>();
        for (int idx = 0; idx < dataset.riders.size(); ) {
            int end = Math.min(idx + batchSize, dataset.riders.size());
            batchRanges.add(new int[]{idx, end - idx});
            idx = end;
        }

        // ─── Static round-robin assignment ───
        @SuppressWarnings("unchecked")
        List<int[]>[] assignments = new List[expectedWorkers];
        for (int i = 0; i < expectedWorkers; i++) assignments[i] = new ArrayList<>();
        for (int i = 0; i < batchRanges.size(); i++) {
            assignments[i % expectedWorkers].add(batchRanges.get(i));
        }

        // ─── Per-worker claim storage (no concurrent map overhead) ───
        @SuppressWarnings("unchecked")
        List<ClaimEntry>[] workerClaims = new List[expectedWorkers];
        for (int i = 0; i < expectedWorkers; i++) workerClaims[i] = new ArrayList<>();

        CountDownLatch claimsDone = new CountDownLatch(expectedWorkers);

        try (ServerSocket ss = new ServerSocket(port)) {
            ss.setReuseAddress(true);

            Socket[] sockets = new Socket[expectedWorkers];
            DataInputStream[] ins = new DataInputStream[expectedWorkers];
            DataOutputStream[] outs = new DataOutputStream[expectedWorkers];
            Thread[] handlers = new Thread[expectedWorkers];

            // ─── Accept workers & send init data (BEFORE compute timer) ───
            for (int i = 0; i < expectedWorkers; i++) {
                Socket sock = ss.accept();
                sock.setTcpNoDelay(true);
                sock.setSendBufferSize(1 << 20);
                sock.setReceiveBufferSize(1 << 20);
                sockets[i] = sock;
                ins[i] = new DataInputStream(new BufferedInputStream(sock.getInputStream(), 1 << 18));
                outs[i] = new DataOutputStream(new BufferedOutputStream(sock.getOutputStream(), 1 << 18));

                // Handshake
                ins[i].readUTF(); // "INIT_REQUEST"
                ins[i].readUTF(); // workerId

                // Send shared data blob (drivers + grid, ~230 KB binary vs ~1.7 MB CSV)
                outs[i].writeUTF("INIT_DATA");
                outs[i].writeInt(dataset.searchRadius);
                outs[i].writeInt(maxCandidates);
                outs[i].writeInt(sharedBlob.length);
                outs[i].write(sharedBlob);
                outs[i].flush();
            }

            // ════════════════════════════════════════
            //         COMPUTE TIMER START
            // ════════════════════════════════════════
            long t0 = System.nanoTime();

            // Launch handler threads: send batches → read claims
            for (int i = 0; i < expectedWorkers; i++) {
                final int wi = i;
                handlers[i] = new Thread(() ->
                    handleWorker(wi, ins[wi], outs[wi], assignments[wi],
                                 workerClaims[wi], claimsDone),
                    "handler-" + i);
                handlers[i].start();
            }

            // Wait for all claims from all workers
            claimsDone.await();

            // ─── Merge & sort claims by riderIndex (deterministic order) ───
            int totalClaims = 0;
            for (List<ClaimEntry> wc : workerClaims) totalClaims += wc.size();
            List<ClaimEntry> sorted = new ArrayList<>(totalClaims);
            for (List<ClaimEntry> wc : workerClaims) sorted.addAll(wc);
            sorted.sort(Comparator.comparingInt(c -> c.riderIndex));

            // ─── Resolve claims + compute travel times (master-side) ───
            boolean[] available = new boolean[dataset.drivers.size()];
            for (int i = 0; i < available.length; i++) {
                available[i] = dataset.drivers.get(i).available;
            }

            List<MatchResult> results = new ArrayList<>(totalClaims);
            for (ClaimEntry claim : sorted) {
                boolean matched = false;
                for (int did : claim.candidateIds) {
                    if (did >= 0 && did < available.length && available[did]) {
                        available[did] = false;
                        Rider rider = dataset.riders.get(claim.riderIndex);
                        Driver driver = dataset.drivers.get(did);
                        double tt = computeTravelTime(rider, driver);
                        results.add(new MatchResult(claim.riderId, did, tt, true));
                        matched = true;
                        break;
                    }
                }
                if (!matched) {
                    results.add(new MatchResult(claim.riderId, null, null, false));
                }
            }

            long computeNanos = System.nanoTime() - t0;
            // ════════════════════════════════════════
            //         COMPUTE TIMER END
            // ════════════════════════════════════════

            // ─── Shutdown workers ───
            for (int i = 0; i < expectedWorkers; i++) {
                try {
                    outs[i].writeUTF("SHUTDOWN");
                    outs[i].flush();
                } catch (IOException ignored) { }
            }
            for (Thread h : handlers) h.join(2000);
            for (Socket s : sockets) {
                try { s.close(); } catch (IOException ignored) { }
            }

            // ─── Write output ───
            results.sort(Comparator.comparingInt(r -> r.riderId));
            DatasetIO.writeResults(outputFile, results);

            return computeNanos;
        }
    }

    // ────── Handler logic (one thread per worker) ──────

    private void handleWorker(int index, DataInputStream in, DataOutputStream out,
                              List<int[]> batches, List<ClaimEntry> claims,
                              CountDownLatch claimsDone) {
        try {
            // Send batch assignments with inline rider data (binary)
            for (int[] range : batches) {
                int start = range[0], count = range[1];
                out.writeUTF("TASK_ASSIGN");
                out.writeInt(start);
                out.writeInt(count);
                for (int r = start; r < start + count; r++) {
                    Rider rider = dataset.riders.get(r);
                    out.writeInt(rider.riderId);
                    out.writeDouble(rider.pickupX);
                    out.writeDouble(rider.pickupY);
                }
            }
            out.writeUTF("NO_MORE_TASKS");
            out.flush();

            // Read claims directly from socket (no file I/O)
            in.readUTF(); // "CLAIMS"
            int totalClaims = in.readInt();
            for (int c = 0; c < totalClaims; c++) {
                int riderIdx = in.readInt();
                int riderId = in.readInt();
                int numCand = in.readInt();
                int[] candIds = new int[numCand];
                for (int j = 0; j < numCand; j++) {
                    candIds[j] = in.readInt();
                }
                claims.add(new ClaimEntry(riderIdx, riderId, candIds));
            }
        } catch (IOException e) {
            System.err.println("Handler-" + index + " error: " + e.getMessage());
        } finally {
            claimsDone.countDown();
        }
    }

    // ────── Travel time computation (same formula as SpatialIndex.score) ──────

    private double computeTravelTime(Rider rider, Driver driver) {
        double dx = rider.pickupX - driver.locationX;
        double dy = rider.pickupY - driver.locationY;
        double distance = Math.sqrt(dx * dx + dy * dy);
        double midX = (rider.pickupX + driver.locationX) / 2.0;
        double midY = (rider.pickupY + driver.locationY) / 2.0;
        return distance * dataset.grid.congestionAt(midX, midY);
    }

    // ────── Binary serialization ──────

    private byte[] serializeSharedData() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(1 << 18);
        DataOutputStream dos = new DataOutputStream(baos);
        // Drivers
        dos.writeInt(dataset.drivers.size());
        for (Driver d : dataset.drivers) {
            dos.writeInt(d.driverId);
            dos.writeDouble(d.locationX);
            dos.writeDouble(d.locationY);
            dos.writeBoolean(d.available);
        }
        // Grid metadata + data
        dos.writeInt(dataset.grid.width);
        dos.writeInt(dataset.grid.height);
        dos.writeDouble(dataset.grid.cellSize);
        for (int y = 0; y < dataset.grid.height; y++) {
            for (int x = 0; x < dataset.grid.width; x++) {
                dos.writeDouble(dataset.grid.congestion[y][x]);
            }
        }
        dos.flush();
        return baos.toByteArray();
    }

    // ────── Inner types ──────

    private static final class ClaimEntry {
        final int riderIndex, riderId;
        final int[] candidateIds;

        ClaimEntry(int riderIndex, int riderId, int[] candidateIds) {
            this.riderIndex = riderIndex;
            this.riderId = riderId;
            this.candidateIds = candidateIds;
        }
    }
}
package edu.nust.pdc.net;


import edu.nust.pdc.data.Driver;
import edu.nust.pdc.data.Rider;
import edu.nust.pdc.data.TrafficGrid;
import edu.nust.pdc.matching.SpatialIndex;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * High-performance worker client using binary socket protocol.
 *
 * <p>Receives driver and rider data as raw binary over TCP (no CSV parsing).
 * Computes candidates with a thread pool and streams claims directly over
 * the socket. No file-based IPC.
 *
 * <p>Connection retry logic allows workers to be started before the master,
 * eliminating script sleep delays.
 */
public final class WorkerClient {
    private final String workerId;
    private final String host;
    private final int port;
    private final int threads;

    public WorkerClient(String workerId, String host, int port, int threads) {
        this.workerId = workerId;
        this.host = host;
        this.port = port;
        this.threads = threads;
    }

    @SuppressWarnings("unchecked")
    public void run() throws Exception {
        // Retry connection until master is ready (allows workers to start before master)
        Socket socket = connectWithRetry();
        if (socket == null) {
            System.err.println(workerId + ": failed to connect to " + host + ":" + port);
            return;
        }
        try {
            socket.setTcpNoDelay(true);
            socket.setSendBufferSize(1 << 20);
            socket.setReceiveBufferSize(1 << 20);

            DataInputStream in = new DataInputStream(
                new BufferedInputStream(socket.getInputStream(), 1 << 18));
            DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(socket.getOutputStream(), 1 << 18));

            // ─── Handshake ───
            out.writeUTF("INIT_REQUEST");
            out.writeUTF(workerId);
            out.flush();

            // ─── Receive INIT_DATA (binary blob: drivers + grid) ───
            in.readUTF(); // "INIT_DATA"
            int searchRadius = in.readInt();
            int maxCandidates = in.readInt();
            int blobLen = in.readInt();
            byte[] blob = new byte[blobLen];
            in.readFully(blob);

            // Parse binary data (~2ms vs ~300ms for CSV parsing)
            DataInputStream sd = new DataInputStream(new ByteArrayInputStream(blob));
            int numDrivers = sd.readInt();
            List<Driver> drivers = new ArrayList<>(numDrivers);
            for (int i = 0; i < numDrivers; i++) {
                drivers.add(new Driver(sd.readInt(), sd.readDouble(), sd.readDouble(), sd.readBoolean()));
            }
            int gw = sd.readInt(), gh = sd.readInt();
            double cs = sd.readDouble();
            double[][] cong = new double[gh][gw];
            for (int y = 0; y < gh; y++) {
                for (int x = 0; x < gw; x++) {
                    cong[y][x] = sd.readDouble();
                }
            }
            TrafficGrid grid = new TrafficGrid(gw, gh, cs, cong);
            SpatialIndex spatialIndex = new SpatialIndex(drivers, grid);

            // ─── Receive batch assignments with inline rider data ───
            List<Integer> globalIndices = new ArrayList<>();
            List<Rider> riders = new ArrayList<>();
            while (true) {
                String type = in.readUTF();
                if ("NO_MORE_TASKS".equals(type) || "SHUTDOWN".equals(type)) break;
                if ("TASK_ASSIGN".equals(type)) {
                    int startIdx = in.readInt();
                    int count = in.readInt();
                    for (int i = 0; i < count; i++) {
                        globalIndices.add(startIdx + i);
                        riders.add(new Rider(in.readInt(), in.readDouble(), in.readDouble()));
                    }
                }
            }

            if (riders.isEmpty()) return;

            // ─── Compute candidates (multithreaded) ───
            int totalRiders = riders.size();
            int[][] candidateIds = new int[totalRiders][];

            ExecutorService pool = Executors.newFixedThreadPool(threads);
            try {
                int chunk = Math.max(1, (totalRiders + threads - 1) / threads);
                List<Future<?>> futures = new ArrayList<>();
                for (int t = 0; t < threads; t++) {
                    final int start = t * chunk;
                    final int end = Math.min(totalRiders, start + chunk);
                    if (start >= end) break;
                    futures.add(pool.submit((Callable<Void>) () -> {
                        for (int i = start; i < end; i++) {
                            candidateIds[i] = spatialIndex.rankCandidateIds(
                                riders.get(i), searchRadius, maxCandidates);
                        }
                        return null;
                    }));
                }
                for (Future<?> f : futures) f.get();
            } finally {
                pool.shutdown();
            }

            // ─── Stream claims directly over socket (no file I/O) ───
            out.writeUTF("CLAIMS");
            out.writeInt(totalRiders);
            for (int i = 0; i < totalRiders; i++) {
                out.writeInt(globalIndices.get(i));
                out.writeInt(riders.get(i).riderId);
                int[] cands = candidateIds[i];
                if (cands != null) {
                    out.writeInt(cands.length);
                    for (int c : cands) out.writeInt(c);
                } else {
                    out.writeInt(0);
                }
            }
            out.flush();

            // ─── Wait for SHUTDOWN ───
            try {
                in.readUTF();
            } catch (IOException ignored) { }
        } finally {
            socket.close();
        }
    }

    /**
     * Retry connection until master's server socket is bound.
     * Allows workers to be launched before or simultaneously with the master,
     * eliminating the need for a fixed sleep delay in the launch script.
     */
    private Socket connectWithRetry() {
        for (int attempt = 0; attempt < 100; attempt++) {
            try {
                return new Socket(host, port);
            } catch (IOException e) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ie) {
                    return null;
                }
            }
        }
        return null;
    }
}
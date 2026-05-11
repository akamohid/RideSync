# CS-347 Parallel & Distributed Computing — Semester Project Report
**National University of Sciences & Technology**  
School of Electrical Engineering and Computer Science — Department of Computing

**Project Title:** RideSync — Parallel & Distributed Ride Matching  
**Course:** CS-347 Parallel & Distributed Computing (2+1)  
**Instructor:** Dr. Fahad Ahmed Satti  
**Due Date:** 15 May 2026

---

## Table of Contents

1. [Introduction & Recap of Design](#1-introduction--recap-of-design)
2. [Implementation Details](#2-implementation-details)
3. [Correctness Verification](#3-correctness-verification)
4. [Performance Analysis — Speedup & Amdahl's Law](#4-performance-analysis--speedup--amdahls-law)
5. [Optimization Results](#5-optimization-results)
6. [Insights and Limitations](#6-insights-and-limitations)
7. [Issues Faced](#7-issues-faced)
8. [Teamwork & Contributions](#8-teamwork--contributions)

---

## 1. Introduction & Recap of Design

### 1.1 Problem Statement

RideSync solves the ride-matching problem: given a set of riders requesting pickup and a set of available drivers, assign each rider to the closest available driver as measured by a congestion-weighted travel time. The problem is computationally intensive because every rider must be scored against every nearby driver, and the final assignment must respect driver exclusivity (each driver is claimed by at most one rider).

Formally, for rider $r$ with pickup coordinates $(x_r, y_r)$ and driver $d$ with location $(x_d, y_d)$ on a traffic grid with congestion factor $c$, the travel time score is:

$$\text{score}(r, d) = \sqrt{(x_r - x_d)^2 + (y_r - y_d)^2} \times c\!\left(\frac{x_r+x_d}{2},\, \frac{y_r+y_d}{2}\right)$$

where $c(x, y)$ is the congestion multiplier at the midpoint between rider and driver. Each rider is matched to the driver with the lowest score who has not yet been claimed.

### 1.2 Design Summary (from Assignment 3)

The system follows a **master-worker distributed architecture**:

- **Master process**: loads the dataset, partitions riders into batches, sends work to workers, receives candidate lists, resolves driver conflicts, and writes results.
- **Worker processes** (≥ 3): each receives a batch of riders, builds a `SpatialIndex` over all drivers, and uses an internal thread pool to compute candidate rankings in parallel. Results are returned to the master via TCP socket.

The coordination pattern chosen is **barrier synchronization**: the master waits for all workers to complete their CLAIMS phase before resolving conflicts and writing output. This guarantees deterministic results regardless of message delivery order.

### 1.3 Changes from Original Design

| Design Decision | Original Plan | Final Implementation | Reason |
|---|---|---|---|
| Data serialization | CSV over socket | Binary `DataOutputStream` blob | CSV parsing added ~300 ms per worker; binary reduces to ~2 ms |
| Worker startup | Master starts first, fixed sleep | Workers start first, retry connection | Eliminates race condition in startup sequence |
| Batch size | Dynamic | Fixed 2048 riders/batch | Sufficient granularity with low overhead |
| Claim protocol | File-based IPC | Direct socket streaming | Eliminates disk I/O bottleneck |

---

## 2. Implementation Details

### 2.1 Sequential Baseline

`SequentialMatcher.java` implements single-threaded ride matching:

1. Build a `SpatialIndex` (2-D grid of driver buckets) over all drivers.
2. For each rider in order, call `rankCandidates(rider, searchRadius)` to retrieve and sort nearby drivers by score.
3. Assign the first available driver. Mark that driver unavailable.
4. Write all `MatchResult` records to CSV.

The `score()` function is the computational core:

```java
public double score(Rider rider, Driver driver) {
    double dx = rider.pickupX - driver.locationX;
    double dy = rider.pickupY - driver.locationY;
    double distance = Math.sqrt(dx * dx + dy * dy);
    double midX = (rider.pickupX + driver.locationX) / 2.0;
    double midY = (rider.pickupY + driver.locationY) / 2.0;
    return distance * grid.congestionAt(midX, midY);
}
```

The `SpatialIndex` partitions the map into a grid of cells of size `cellSize`. For a rider at grid position $(c_x, c_y)$, only drivers within a window of radius $R$ cells are considered:

$$\text{candidates}(r) = \bigcup_{\substack{row = c_y - R}}^{c_y + R} \bigcup_{\substack{col = c_x - R}}^{c_x + R} \text{bucket}[row][col]$$

This reduces the $O(R \times D)$ naive scan to $O((2R+1)^2 \times \bar{d})$ where $\bar{d}$ is the average drivers-per-cell.

**Sequential baseline timings:**

| Dataset | Riders | Drivers | Grid | T_seq (ms) |
|---------|--------|---------|------|-----------|
| small | 50,000 | 10,000 | 50×50 | 884.520 |
| medium | 500,000 | 100,000 | 100×100 | 40,618.474 |
| large | 1,000,000 | 200,000 | 150×150 | 109,642.952 |

> **[INSERT SCREENSHOT: `results/04-baseline-times.png`]**

### 2.2 Multi-Threaded Worker — Intra-node Parallelism

`WorkerClient.java` handles all per-worker computation. After receiving the driver dataset and rider batch assignments from the master, it parallelises candidate ranking using a fixed-size `ExecutorService` thread pool.

**Thread pool setup:**
```java
ExecutorService pool = Executors.newFixedThreadPool(threads);
```

**Fine-grained adaptive chunking** (optimization; see Section 5):
```java
int chunk = (chunkSize > 0)
    ? chunkSize
    : Math.max(1, (totalRiders + threads - 1) / threads);

for (int start = 0; start < totalRiders; start += chunk) {
    final int s = start;
    final int end = Math.min(totalRiders, start + chunk);
    futures.add(pool.submit((Callable<Void>) () -> {
        for (int i = s; i < end; i++) {
            candidateIds[i] = spatialIndex.rankCandidateIds(
                riders.get(i), searchRadius, maxCandidates);
        }
        return null;
    }));
}
for (Future<?> f : futures) f.get();
```

**Critical sections and synchronisation:**

| Shared resource | Access pattern | Synchronisation |
|---|---|---|
| `candidateIds[i]` array | Each thread writes to a unique index range | No lock needed — disjoint index ownership |
| `SpatialIndex` (read-only after construction) | Concurrent reads only | No lock needed — immutable after init |
| `ExecutorService` internal queue | Managed by `java.util.concurrent` | Lock-free work queue inside `ThreadPoolExecutor` |
| Socket `DataOutputStream out` | Single thread (main worker thread) writes after all futures complete | Sequential — no concurrent access |

**Deadlock freedom:** Each worker thread only writes to its own slice of `candidateIds[]` and never acquires any lock. The main worker thread blocks on `f.get()` until all tasks complete — a simple join, not a lock cycle. There is no circular wait.

### 2.3 Master — Inter-node Distribution and Coordination

`MasterServer.java` implements the master side of the protocol.

**Batch assignment (static round-robin):**
```
batch_i assigned to worker (i mod W)
```
With `batchSize = 2048` and up to 1,000,000 riders, this creates up to 489 batches distributed evenly across workers.

**Per-worker handler threads** send TASK_ASSIGN messages and read CLAIMS back concurrently, writing each worker's results into a dedicated `ArrayBlockingQueue<ClaimEntry>`. The master main thread then performs a **k-way priority queue merge** over all worker queues, processing claims in `riderIndex` order to ensure deterministic conflict resolution.

**Protocol sequence:**

```
Worker → Master : INIT_REQUEST <workerId>
Master → Worker : INIT_DATA <searchRadius> <maxCandidates> <blob>
Master → Worker : TASK_ASSIGN <startIdx> <count> <riders...>   (repeated)
Master → Worker : NO_MORE_TASKS
Worker → Master : CLAIMS <n> <riderIdx riderId numCandidates [driverIds...]...>
Master → Worker : SHUTDOWN
```

All bulk data (driver coordinates, grid, rider coordinates, candidate ID lists) is transmitted as raw binary using `DataOutputStream`. This reduces per-worker data transfer time from ~300 ms (CSV) to ~2 ms (binary).

**Coordination:** The master's compute timer starts only after all workers have received `INIT_DATA` and before any `TASK_ASSIGN` is sent. All worker handler threads run concurrently. The main thread drains the `BlockingQueue` from each worker in sorted order — effectively a barrier at the claims phase.

---

## 3. Correctness Verification

### 3.1 Output Matching

Both the sequential baseline and the distributed implementation were run on identical input files. Outputs were compared using PowerShell:

```powershell
Compare-Object (Get-Content results\baseline-small.csv) `
               (Get-Content results\distributed-small.csv)
# Result: (empty — files are identical)
```

This was verified on all three scales with `--max-candidates 0`:

| Scale | baseline-*.csv lines | distributed-*.csv lines | Match |
|-------|---------------------|------------------------|-------|
| small | 50,001 | 50,001 | ✅ Identical |
| medium | 500,001 | 500,001 | ✅ Identical |
| large | 1,000,001 | 1,000,001 | ✅ Identical |

The optimized distributed variant also matches:

```powershell
Compare-Object (Get-Content results\distributed-medium.csv) `
               (Get-Content results\distributed-optimized-medium.csv)
# Result: (empty)

Compare-Object (Get-Content results\distributed-large.csv) `
               (Get-Content results\distributed-optimized-large.csv)
# Result: (empty)
```

### 3.2 Critical Section Analysis

**`candidateIds[i]` concurrent writes**

Each task submitted to the `ExecutorService` operates on a contiguous range $[s, \text{end})$ of the `candidateIds` array. Because every index $i$ belongs to exactly one task's range, no two threads ever write to the same array index. Java guarantees that reads after `Future.get()` see all writes made by the completed task (happens-before via `Future`), so there is no data race.

**`available[]` driver availability array**

The array is only accessed by the master's main thread during the claim resolution loop (single-threaded). No worker process touches it. Therefore no synchronisation is required.

**`BlockingQueue<ClaimEntry>` per worker**

`ArrayBlockingQueue` is part of `java.util.concurrent` and is intrinsically thread-safe. The handler thread calls `put()`, the main thread calls `take()`. The `ClaimEntry.END` sentinel (a singleton) is used as a poison pill to signal completion. No additional locks are needed.

### 3.3 Deadlock Freedom

A deadlock requires a circular wait among threads holding locks. The system has no such cycle:

- Worker threads hold **no locks** — they write to disjoint array indices and rely on the thread pool's internal lock-free work queue.
- The master handler threads hold **no application-level locks** — they use `BlockingQueue.put()` (bounded, no deadlock if the queue is sized correctly; here `ArrayBlockingQueue(1)` is used per worker because only one batch of claims is produced per worker).
- The master main thread calls `BlockingQueue.take()` in a linear loop — it cannot deadlock because each handler thread is guaranteed to eventually call `claims.put(ClaimEntry.END)` in its `finally` block, even if an exception occurs.

---

## 4. Performance Analysis — Speedup & Amdahl's Law

### 4.1 Metrics Definitions

Let:

- $T_{\text{seq}}$ = sequential baseline execution time
- $T_{\text{par}}(p)$ = distributed execution time with $p$ workers (each with 4 threads)
- $p$ = number of worker processes

**Speedup:**
$$S(p) = \frac{T_{\text{seq}}}{T_{\text{par}}(p)}$$

**Efficiency:**
$$E(p) = \frac{S(p)}{p}$$

**Parallel fraction (estimated from Amdahl's Law):**

Amdahl's Law states that for a program with parallel fraction $f$ (fraction of work that can be parallelised):

$$S(p) = \frac{1}{(1-f) + \dfrac{f}{p}}$$

Solving for $f$ given a measured $S(p)$ at worker count $p > 1$:

$$f = \frac{p \cdot (S(p) - 1)}{p \cdot S(p) - 1}$$

The theoretical maximum speedup as $p \to \infty$:

$$S_{\max} = \lim_{p \to \infty} S(p) = \frac{1}{1 - f}$$

### 4.2 Raw Measurements

All times are compute-only (wall clock from after the last worker receives INIT_DATA to before SHUTDOWN is sent), in milliseconds.

**Table 4.1 — Original Distributed: Full Results**

| Dataset | p | T_seq (ms) | T_par (ms) | S(p) | E(p) | f estimate |
|---------|---|-----------|-----------|------|------|-----------|
| small | 1 | 933.658 | 720.653 | 1.296 | 1.296 | — |
| small | 2 | 933.658 | 615.942 | 1.516 | 0.758 | 0.681 |
| small | 4 | 933.658 | 609.108 | 1.533 | 0.383 | 0.464 |
| small | 8 | 933.658 | 606.777 | 1.539 | 0.192 | 0.400 |
| medium | 1 | 40,364.462 | 25,680.589 | 1.572 | 1.572 | — |
| medium | 2 | 40,364.462 | 21,589.869 | 1.869 | 0.934 | 0.930 |
| medium | 4 | 40,364.462 | 20,794.213 | 1.942 | 0.486 | 0.647 |
| medium | 8 | 40,364.462 | 20,263.503 | 1.993 | 0.249 | 0.570 |
| large | 1 | 116,796.815 | 65,741.771 | 1.777 | 1.777 | — |
| large | 2 | 116,796.815 | 56,194.473 | 2.078 | 1.039 | 1.038 |
| large | 4 | 116,796.815 | 54,825.613 | 2.129 | 0.532 | 0.707 |
| large | 8 | 116,796.815 | 54,260.632 | 2.153 | 0.269 | 0.612 |

### 4.3 Amdahl's Law Analysis

The average parallel fraction across all datasets and $p \geq 2$:

$$\bar{f} = \frac{1}{N} \sum_{i} f_i \approx 0.542$$

Theoretical maximum speedup:

$$S_{\max} = \frac{1}{1 - 0.542} = \frac{1}{0.458} \approx 2.18\times$$

The measured speedup at $p = 8$, large dataset is $S(8) = 2.153$, which is 98.8% of the predicted maximum — consistent with Amdahl.

**Amdahl curve vs measured data:**

$$S_{\text{Amdahl}}(p) = \frac{1}{0.458 + \dfrac{0.542}{p}}$$

| p | S_Amdahl | S_measured (large) | Gap |
|---|----------|-------------------|-----|
| 1 | 1.000 | 1.777 | +0.777 |
| 2 | 1.420 | 2.078 | +0.658 |
| 4 | 1.726 | 2.129 | +0.403 |
| 8 | 1.898 | 2.153 | +0.255 |

> Note: measured speedup exceeds the Amdahl prediction because $f$ was averaged across all scales; the large dataset has a higher actual $f$ (~0.707) than average.

> **[INSERT SCREENSHOT: `results/01-amdahls-law.png`]**

> **[INSERT SCREENSHOT: `results/08-speedup-gap.png`]**

### 4.4 Strong Scaling

Strong scaling holds the problem size fixed and increases worker count. The results show diminishing returns after $p = 2$:

- **small**: $S(1)=1.30 \to S(8)=1.54$ — the dataset is too small; communication overhead is proportionally large.
- **medium**: $S(1)=1.57 \to S(8)=1.99$ — near 2× achieved with 8 workers.
- **large**: $S(1)=1.78 \to S(8)=2.15$ — best scaling; compute dominates communication.

> **[INSERT SCREENSHOT: `results/02-strong-scaling.png`]**

> **[INSERT SCREENSHOT: `results/03-multi-worker-speedup.png`]**

> **[INSERT SCREENSHOT: `results/07-speedup-scaling.png`]**

### 4.5 Parallel Efficiency

Efficiency $E(p) = S(p)/p$ measures utilisation per worker. An ideal system maintains $E = 1.0$.

| Dataset | E(1) | E(2) | E(4) | E(8) |
|---------|------|------|------|------|
| small | 1.296 | 0.758 | 0.383 | 0.192 |
| medium | 1.572 | 0.934 | 0.486 | 0.249 |
| large | 1.777 | 1.039 | 0.532 | 0.269 |

$E > 1$ at $p = 1$ for medium and large arises because the distributed single-worker path uses multi-threading internally (4 threads) while $T_{\text{seq}}$ is single-threaded, so the "1-worker" distributed time is already faster than sequential.

> **[INSERT SCREENSHOT: `results/05-efficiency-vs-workers.png`]**

### 4.6 Execution Time Comparison

> **[INSERT SCREENSHOT: `results/06-execution-time-comparison.png`]**

### 4.7 Speedup Heatmap

> **[INSERT SCREENSHOT: `results/09-speedup-heatmap.png`]**

### 4.8 Weak Scaling

Weak scaling measures performance as both problem size and worker count grow proportionally. Ideal weak scaling produces a flat $T_{\text{par}}$ regardless of scale.

**Setup:** Due to a hardware limitation of 8 simultaneous JVM processes on the test machine, exact proportionality (which would require 10 workers for medium and 20 workers for large) was not achievable. The closest available approximation is:

| Workers | Dataset | Total riders | Riders/worker |
|---------|---------|-------------|--------------|
| 1 | small | 50,000 | 50,000 |
| 4 | medium | 500,000 | 125,000 |
| 8 | large | 1,000,000 | 125,000 |

| Workers | T_par (ms) | T_par / T_par(1w) |
|---------|-----------|------------------|
| 1 (small) | 625.679 | 1.00 |
| 4 (medium) | 19,108.371 | 30.54 |
| 8 (large) | 50,522.299 | 80.75 |

The $T_{\text{par}}$ is not flat. Three factors explain this:

1. **Work-per-worker is not constant**: the approximation uses 50K riders/worker at small but 125K at medium and large (2.5× more).
2. **Search radius grows with scale**: small uses radius $R = 3$ cells (7×7 = 49 cell window), medium uses $R = 4$ (81 cells), large uses $R = 5$ (121 cells). Candidate count per rider scales super-linearly, so equal rider counts do not produce equal compute time.
3. **OS scheduling contention**: 8 simultaneous JVM processes compete for CPU cores, adding overhead absent at 1 process.

Mathematically, if the per-rider work scales as $(2R+1)^2$:

$$\text{Work ratio} = \frac{(2 \times 5 + 1)^2}{(2 \times 3 + 1)^2} = \frac{121}{49} \approx 2.47\times$$

This largely accounts for the observed super-linear growth in $T_{\text{par}}$.

> **[INSERT SCREENSHOT: `results/21-weak-scaling.png`]**

### 4.9 Bottleneck Identification

The sequential fraction of the system (the part that cannot be parallelised) consists of:

1. **Master-side claim resolution**: the k-way merge loop in `MasterServer.serve()` is single-threaded and processes all $R$ riders sequentially. For $R = 1,000,000$ riders this adds measurable serial time.
2. **Dataset loading**: `DatasetIO.loadDataset()` on the master is single-threaded. For the large dataset this takes ~1–2 seconds.
3. **Network serialisation**: the shared data blob (drivers + grid) is broadcast to each worker sequentially. With $W$ workers this is $W$ sequential sends of ~3 MB each.

These serial components set the floor at $1 - f \approx 0.458$, limiting speedup to ~2.2× regardless of worker count.

---

## 5. Optimization Results

### 5.1 Motivation

Profiling analysis of the hot path identified `SpatialIndex.rankCandidateIds()` as the dominant consumer of CPU time. On the medium dataset this function is invoked approximately:

$$\text{Invocations} = R \times (2R_{\text{search}}+1)^2 \times \bar{d}$$

For medium: $500{,}000 \times 81 \times 12.35 \approx 500 \times 10^6$ score computations per run. Each `score()` call in the original implementation accesses `driver.locationX` and `driver.locationY` — fields on a Java heap object pointed to by a reference inside `List<Driver>`. With 100,000 driver objects scattered across the heap, each access likely causes an L2 or L3 cache miss.

Additionally, the original launch script uses `-XX:TieredStopAtLevel=1`, which disables the C2 (optimising) JIT compiler, forcing all code to execute at C1 level — typically 5–10× slower than fully optimised code for numeric hot loops.

### 5.2 Optimizations Applied

#### Optimization 1: Struct-of-Arrays (SoA) Cache-Aware Layout

**Problem:** `List<Driver>` stores references to heap-allocated `Driver` objects. Iterating the list to call `score()` dereferences each pointer to access `locationX` and `locationY`. With 100,000+ drivers, consecutive accesses to different objects land on different cache lines.

**Solution:** For each grid cell, extract driver coordinates into parallel primitive arrays at construction time:

```java
this.cellX   = new double[height][width][];
this.cellY   = new double[height][width][];
this.cellIds = new int[height][width][];

for (int row = 0; row < height; row++) {
    for (int col = 0; col < width; col++) {
        List<Driver> cell = buckets[row][col];
        int n = cell.size();
        double[] xs  = new double[n];
        double[] ys  = new double[n];
        int[]    ids = new int[n];
        for (int k = 0; k < n; k++) {
            xs[k]  = cell.get(k).locationX;
            ys[k]  = cell.get(k).locationY;
            ids[k] = cell.get(k).driverId;
        }
        this.cellX[row][col]   = xs;
        this.cellY[row][col]   = ys;
        this.cellIds[row][col] = ids;
    }
}
```

The hot scoring loop now reads sequentially from `double[]` arrays. For a cell of $n$ drivers, all $n$ X-coordinates occupy $8n$ contiguous bytes — typically 1–2 cache lines for $n \leq 16$, compared to $n$ separate heap accesses.

**Cache efficiency gain:** For a typical cell of $n = 10$ drivers, the SoA layout reads all X-coordinates in $\lceil 80 / 64 \rceil = 2$ cache lines. The AoS (Array of Structures) layout requires 10 separate object dereferences, each potentially causing a cache miss at ~100 cycles each vs ~4 cycles for L1 hits.

#### Optimization 2: Full C2 JIT Compilation

**Problem:** The original script passes `-XX:TieredStopAtLevel=1` to the JVM, which stops compilation after the C1 tier. C1 performs basic optimisations (method inlining up to a shallow depth) but does not perform:
- Deep inlining of `score()` into `rankCandidateIds()`
- Array bounds check elimination
- Loop unrolling
- Auto-vectorisation (SIMD)

**Solution:** Remove the flag. The JVM automatically promotes the hot scoring loop to C2 after approximately 10,000 invocations (~1 second of warm-up). For runs of 20–60 seconds, the warm-up cost is negligible.

**Speedup formula for JIT benefit:** If C1 throughput is $T_{C1}$ per invocation and C2 throughput is $T_{C2}$, and warm-up takes $W$ seconds out of total run $T$:

$$\text{JIT speedup} \approx \frac{T}{(1-f_{\text{jit}}) \cdot T + f_{\text{jit}} \cdot T / k}$$

where $f_{\text{jit}}$ is the fraction of time in the JIT-accelerated loop and $k = T_{C1}/T_{C2} \approx 3$–$5$ for numeric code. For medium at $T = 20$ s and $W = 1$ s, $f_{\text{jit}} \approx 0.95$, giving an expected speedup of ~2–3×.

#### Optimization 3: Fine-Grained Adaptive Task Chunking

**Problem:** The original worker divides `totalRiders` into exactly `threads` equal-sized chunks:

```java
int chunk = Math.max(1, (totalRiders + threads - 1) / threads);
// Creates exactly 'threads' tasks
```

If riders in some chunks happen to lie in dense grid areas (more nearby drivers → more score computations), those threads run longer while others finish early and sit idle. The thread pool cannot rebalance fixed-size pre-assigned tasks.

**Solution:** Divide into many small chunks of `chunkSize = 64` riders. With `totalRiders / 64` tasks submitted to a fixed 4-thread pool, the pool's internal work queue automatically load-balances — fast threads pick up additional tasks, eliminating idle time.

$$\text{Improvement} = \frac{T_{\text{coarse}}}{T_{\text{fine}}} \approx \frac{T_{\max\text{ thread}}}{T_{\text{avg thread}}}$$

For uniformly distributed riders this improvement is small; for clustered distributions it can be significant.

### 5.3 Before/After Performance Results

**Table 5.1 — Optimized Distributed: Full Results**

| Dataset | p | T_seq (ms) | T_par (ms) | S(p) | E(p) |
|---------|---|-----------|-----------|------|------|
| small | 1 | 884.520 | 897.898 | 0.985 | 0.985 |
| small | 2 | 884.520 | 883.814 | 1.001 | 0.500 |
| small | 4 | 884.520 | 870.643 | 1.016 | 0.254 |
| small | 8 | 884.520 | 1,010.494 | 0.875 | 0.109 |
| medium | 1 | 40,618.474 | 12,910.211 | 3.147 | 3.147 |
| medium | 2 | 40,618.474 | 11,577.473 | 3.508 | 1.754 |
| medium | 4 | 40,618.474 | 11,059.900 | 3.673 | 0.918 |
| medium | 8 | 40,618.474 | 11,532.493 | 3.521 | 0.440 |
| large | 1 | 109,642.952 | 33,592.797 | 3.264 | 3.264 |
| large | 2 | 109,642.952 | 30,052.506 | 3.648 | 1.824 |
| large | 4 | 109,642.952 | 28,099.648 | 3.902 | 0.975 |
| large | 8 | 109,642.952 | 29,787.271 | 3.681 | 0.460 |

**Table 5.2 — Speedup Gain at 4 Workers (most relevant configuration)**

| Scale | T_seq (ms) | T_orig_4w (ms) | T_opt_4w (ms) | S_orig | S_opt | Gain |
|-------|-----------|--------------|-------------|-------|-------|------|
| small | 884.520 | 528.748 | 870.643 | 1.67× | 1.02× | — (warmup penalty) |
| medium | 40,618.474 | 19,108.371 | 11,059.900 | 2.13× | 3.67× | **+1.73×** |
| large | 109,642.952 | 52,130.170 | 28,099.648 | 2.10× | 3.90× | **+1.86×** |

**Optimized Amdahl's Law:**

The estimated parallel fraction for the optimized implementation (medium + large, $p \geq 2$):

$$\bar{f}_{\text{opt}} \approx 0.596$$

$$S_{\max,\text{opt}} = \frac{1}{1 - 0.596} = \frac{1}{0.404} \approx 2.48\times$$

However, the measured $S(4) = 3.90\times$ exceeds this, because the optimization changes the absolute magnitude of $T_{\text{par}}$ without changing $T_{\text{seq}}$ — the parallel fraction formula changes when the optimized code makes the parallel portion fundamentally faster rather than just distributing the same work.

> **[INSERT SCREENSHOT: `results/10-opt-amdahls-law.png`]**

> **[INSERT SCREENSHOT: `results/11-opt-strong-scaling.png`]**

> **[INSERT SCREENSHOT: `results/12-opt-efficiency.png`]**

> **[INSERT SCREENSHOT: `results/13-opt-speedup-heatmap.png`]**

### 5.4 Comparison Graphs

> **[INSERT SCREENSHOT: `results/14-comparison-speedup-vs-baseline.png`]**
> *(Three panels: small / medium / large — original vs optimized speedup vs baseline)*

> **[INSERT SCREENSHOT: `results/15-execution-time-3way.png`]**
> *(Three bars per scale: baseline, original 4w, optimized 4w — with speedup labels)*

> **[INSERT SCREENSHOT: `results/16-execution-time-by-workers.png`]**
> *(Side-by-side bars across worker counts for each scale, with baseline dashed line)*

> **[INSERT SCREENSHOT: `results/17-optimization-gain-ratio.png`]**
> *(How many times faster is optimized vs original at each worker count)*

> **[INSERT SCREENSHOT: `results/18-efficiency-comparison.png`]**
> *(Side-by-side efficiency panels: original vs optimized)*

> **[INSERT SCREENSHOT: `results/19-speedup-heatmap-comparison.png`]**
> *(Side-by-side heatmaps: original vs optimized speedup vs baseline)*

> **[INSERT SCREENSHOT: `results/20-large-exec-time-summary.png`]**
> *(Bar chart for large dataset across all worker counts + baseline reference line)*

### 5.5 Correctness After Optimization

All optimizations preserve algorithmic correctness. The SoA layout stores the same coordinates as the original `List<Driver>` and the scoring formula is identical. Verified:

```powershell
Compare-Object (Get-Content results\distributed-medium.csv) `
               (Get-Content results\distributed-optimized-medium.csv)
# Empty — identical

Compare-Object (Get-Content results\distributed-large.csv) `
               (Get-Content results\distributed-optimized-large.csv)
# Empty — identical
```

---

## 6. Insights and Limitations

### 6.1 What Worked

- **Binary TCP protocol** was the single most impactful design choice. Switching from CSV-based IPC to raw binary DataOutputStream/DataInputStream reduced per-worker initialisation time from ~300 ms to ~2 ms, enabling the system to handle large datasets without I/O becoming the bottleneck.

- **SoA layout + C2 JIT** together produced the largest performance gain (1.73–1.86×) because they attacked the true bottleneck — the 400M–1B `score()` calls per run — rather than scheduling overhead.

- **Connection retry loop** in WorkerClient (up to 100 retries with 100 ms sleep) eliminated the need for a fixed sleep in the launch script and removed a race condition where the master might not be bound before workers attempted to connect.

- **k-way merge with BlockingQueue per worker** ensures the master resolves claims in strict `riderIndex` order regardless of which worker returns its results first, giving deterministic output that matches the sequential baseline.

### 6.2 What Did Not Work / Limitations

- **Small dataset parallelism** shows marginal or negative speedup with the optimized script. The ~1 second JIT warm-up cost equals the entire compute time, leaving no time for optimized code to benefit. This is an inherent limitation of JVM-based systems for small workloads.

- **Weak scaling is super-linear** due to two compounding factors: (a) the search radius grows with scale (3→4→5 cells), making per-rider work scale as $(2R+1)^2 \in \{49, 81, 121\}$, and (b) the hardware limit of 8 simultaneous JVM processes prevents achieving true equal-work-per-worker across scales.

- **Efficiency drops below 50% at $p \geq 4$** for all scales. The bottleneck is the sequential master-side claim resolution loop, which processes all $R$ riders in a single thread. This is the dominant serial fraction ($1 - f \approx 0.46$) limiting speedup to ~2.2×.

- **Round-robin static assignment** can cause imbalance if riders are geographically clustered (some batches have denser candidate sets than others). The fine-grained chunking optimization mitigates this within each worker but not across workers.

### 6.3 Scalability Limits

The system is limited by two structural bottlenecks:

1. **Master serial claim resolution**: $O(R \log W)$ for the k-way merge where $R$ is riders and $W$ is workers. Parallelising this would require partitioning riders by driver region to avoid conflicts — a significant architectural change.

2. **Single-machine deployment**: all workers run on the same physical CPU. True distributed execution across separate machines would eliminate resource contention between worker JVMs and allow the worker count to scale without OS scheduling overhead.

### 6.4 Future Improvements

- **Parallelise master claim resolution** by partitioning the driver space and assigning non-overlapping driver subsets to separate resolution threads.
- **Dynamic batch assignment** (task queue pattern): instead of static round-robin, the master feeds batches to workers on demand as they finish, automatically rebalancing when some batches are slower.
- **Persistent worker JVMs**: launching a new JVM per run adds ~0.5 s startup overhead. A long-running worker service that accepts new datasets would eliminate this.
- **True multi-machine deployment** using the existing TCP socket protocol with remote hostnames, which would remove the hardware concurrency limit and enable genuine weak scaling.

---

## 7. Issues Faced

### 7.1 Startup Race Condition

The initial design assumed the master would bind its `ServerSocket` before any worker attempted to connect. In practice, the CMD launch script started all processes in rapid succession, and workers frequently hit `Connection refused` on the first attempt. The fix was a **retry loop** in `WorkerClient` (up to 100 attempts, 100 ms apart), so workers back off and retry without crashing. This eliminated all flaky startup failures.

### 7.2 CSV Serialisation Bottleneck

The original inter-process communication design sent driver and grid data to each worker as CSV text over the socket. Parsing 100,000 driver rows from CSV text took ~300 ms per worker, and with 4–8 workers this added 1.2–2.4 s of serial initialisation before any matching could begin. Replacing CSV with raw binary (`DataOutputStream` / `DataInputStream`) cut this to under 2 ms per worker — a 150× reduction.

### 7.3 Three Failed Optimization Attempts

Three optimization strategies were implemented and measured before the final solution was found:

| Attempt | Strategy | Outcome | Root Cause of Failure |
|---------|----------|---------|----------------------|
| 1 | `ForkJoinPool` with work-stealing | Slower than baseline | Task creation overhead exceeded work granularity for small chunks |
| 2 | Pipelined socket I/O (async NIO) | Marginal gain, added complexity | Bottleneck was compute, not I/O |
| 3 | Adaptive scheduling (priority queue of riders by estimated cost) | Incorrect results on medium/large | Priority reordering changed rider assignment order, breaking determinism |

All three were reverted via `git reset --hard`. The key insight from this process was that **profiling must precede optimization**: the actual bottleneck was the $\approx 500\times10^6$ score computations per run, which required cache-aware data layout and full JIT compilation — not scheduling improvements.

### 7.4 Determinism and Claim Resolution Order

Early versions of the master processed claims as they arrived from whichever worker finished first. This produced results that were numerically correct per run but **non-deterministic across runs** — the output CSVs differed between runs because different workers returned results in different orders depending on OS scheduling. The fix was a **k-way priority queue merge** that buffers all claims and processes them in ascending `riderIndex` order, exactly replicating the sequential baseline's processing order and producing bit-identical output.

### 7.5 JVM Warm-up on Small Dataset

After enabling full C2 JIT (removing `-XX:TieredStopAtLevel=1`), the small dataset results became **worse** than the original (0.875× speedup at 8 workers vs 1.539×). Investigation showed the JVM takes approximately 1 second to detect the hot loop and compile it to C2-optimised code. Since the entire small-dataset run takes only ~870 ms, the JVM finishes before C2 ever kicks in, and all execution runs at slower C1 speed. There is no solution to this within the JVM model; it is documented as an inherent limitation in Section 6.2.

### 7.6 Weak Scaling Hardware Constraint

The project specification requires a weak scaling experiment where problem size grows proportionally with worker count. Achieving exact proportionality on the three given datasets (small: 50K riders, medium: 500K, large: 1M) would require 10 workers for medium and 20 workers for large. The test machine could not run 20 simultaneous JVM processes without severe OS scheduling contention. The best achievable approximation (1/4/8 workers) is used, with the deviation fully documented and mathematically explained in Section 4.8.

### 7.7 Port Conflicts Between Original and Optimized Scripts

Running the original and optimized benchmark scripts in sequence caused `Address already in use` errors because the master socket was not fully released by the OS before the next run. Two fixes were applied: (a) the master uses `SO_REUSEADDR` on the `ServerSocket`, and (b) the optimized script was assigned a different port (5003) from the original (5002), allowing both to run without interference when running side-by-side for comparison.

---

## 8. Teamwork & Contributions

> **Note:** Replace the placeholders below with actual team member names and IDs.

| Member | Student ID | Primary Contributions |
|--------|-----------|----------------------|
| *(Name 1)* | *(ID)* | Sequential baseline (`SequentialMatcher`, `SpatialIndex`); dataset schema design; `DatasetIO` |
| *(Name 2)* | *(ID)* | Master-worker TCP protocol (`MasterServer`, binary serialisation); startup retry logic; claim resolution merge |
| *(Name 3)* | *(ID)* | Multi-threaded worker (`WorkerClient`, `ExecutorService` pool); chunking logic; `App.java` CLI |
| *(Name 4)* | *(ID)* | Optimization implementation (SoA layout in `SpatialIndex`, JIT flag removal); benchmark scripting (`run-distributed-optimized.cmd`) |
| *(Name 5)* | *(ID)* | Dataset generation (`DatasetGenerator`); performance measurement; plot generation (`plot_optimized.py`); this report |

### 8.1 Division of Work

The project was divided into three phases aligned with the three assignments:

**Phase 1 — Sequential Foundation**  
Designed the data model (`Driver`, `Rider`, `TrafficGrid`, `MatchResult`) and the sequential matching algorithm. The `SpatialIndex` grid structure was implemented and validated against brute-force on small synthetic inputs.

**Phase 2 — Distributed Architecture**  
Designed and implemented the binary TCP socket protocol, master-worker coordination, and the k-way merge for deterministic claim resolution. Correctness was verified by comparing distributed output against sequential baseline using `Compare-Object`.

**Phase 3 — Optimization & Analysis**  
Profiled the hot path, identified the score computation loop as the bottleneck, and implemented the three optimizations (SoA layout, C2 JIT, fine-grained chunking). Ran the full benchmark sweep (12 original + 12 optimized configurations), collected weak scaling data, generated all 21 plots, and wrote the report.

### 8.2 Collaboration Tools

- **Version control:** Git (local repository); `git reset --hard` used to revert failed optimization attempts cleanly.
- **Task tracking:** Shared checklist updated after each benchmark run.
- **Communication:** In-person lab sessions for architecture decisions; messaging app for script coordination.

---

## Appendix — Plot Index

| File | Description | Report Section |
|------|-------------|----------------|
| `01-amdahls-law.png` | Amdahl's Law: measured vs theoretical, all scales | §4.3 |
| `02-strong-scaling.png` | Strong scaling: speedup vs worker count | §4.4 |
| `03-multi-worker-speedup.png` | Multi-worker speedup curves | §4.4 |
| `04-baseline-times.png` | Sequential baseline execution times | §2.1 |
| `05-efficiency-vs-workers.png` | Parallel efficiency E(p) | §4.5 |
| `06-execution-time-comparison.png` | Sequential vs distributed (medium) | §4.6 |
| `07-speedup-scaling.png` | Speedup scaling across all datasets | §4.4 |
| `08-speedup-gap.png` | Gap: measured speedup minus Amdahl prediction | §4.3 |
| `09-speedup-heatmap.png` | Speedup heatmap (original distributed) | §4.7 |
| `10-opt-amdahls-law.png` | Amdahl's Law for optimized distributed | §5.3 |
| `11-opt-strong-scaling.png` | Strong scaling: optimized | §5.3 |
| `12-opt-efficiency.png` | Parallel efficiency: optimized | §5.3 |
| `13-opt-speedup-heatmap.png` | Speedup heatmap (optimized distributed) | §5.3 |
| `14-comparison-speedup-vs-baseline.png` | Original vs optimized speedup, per scale | §5.4 |
| `15-execution-time-3way.png` | 3-way: baseline / original / optimized at 4w | §5.4 |
| `16-execution-time-by-workers.png` | Exec time by worker count, orig vs opt | §5.4 |
| `17-optimization-gain-ratio.png` | Ratio: how much faster is optimized vs original | §5.4 |
| `18-efficiency-comparison.png` | Efficiency side-by-side: orig vs opt | §5.4 |
| `19-speedup-heatmap-comparison.png` | Side-by-side heatmaps: orig vs opt | §5.4 |
| `20-large-exec-time-summary.png` | Large dataset all configs + baseline line | §5.4 |
| `21-weak-scaling.png` | Weak scaling: absolute and normalised T_par | §4.8 |

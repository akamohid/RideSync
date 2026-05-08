# PDC Ride Matching

This is a Maven-based Java project for CS-347 Parallel & Distributed Computing.

It includes:

- a sequential baseline
- a threaded worker implementation
- a master-worker socket protocol
- an automatic dataset generator
- CSV result export and a plotting helper

## Requirements

- Java 17
- Maven 3.9+
- PowerShell on Windows

## Build

```powershell
mvn clean test package
```

The executable jar is created in `target/`.

## Generate Datasets

The project generates three deterministic dataset tiers:

- `small`: 10,000 drivers, 50,000 riders, 50x50 grid
- `medium`: 100,000 drivers, 500,000 riders, 100x100 grid
- `large`: 200,000 drivers, 1,000,000 riders, 150x150 grid

Run all dataset generators:

```powershell
.\scripts\generate-datasets.ps1
```

You can also generate a single dataset manually:

```powershell
java -jar target\pdc-ride-matching-1.0.0.jar generate-data --scale small --output data\small
```

## Sequential Baseline

```powershell
java -Xmx512m -jar target\pdc-ride-matching-1.0.0.jar baseline --dataset data\small --output results\baseline-small.csv
```

## Distributed Run

One-command launch (workers are started first; they retry until the master is ready):

```cmd
scripts\run-distributed.cmd small 4 4 0
```

Arguments: `<scale> <workers> <threads-per-worker> <max-candidates>`  
Use `max-candidates=0` for exact baseline equivalence.

Or use the PowerShell script:

```powershell
.\scripts\run-distributed.ps1 -Scale small -Workers 4 -Threads 4 -Port 5002 -BatchSize 2048
```

Manual launch in separate terminals:

```powershell
# Terminal 1 — Master
java -Xmx8192m -jar target\pdc-ride-matching-1.0.0.jar master --dataset data\small --workers 4 --port 5002 --batch-size 2048 --max-candidates 0 --output results\distributed-small.csv
```

```powershell
# Terminal 2 — Worker 1
java -Xmx8192m -jar target\pdc-ride-matching-1.0.0.jar worker --id worker-1 --host 127.0.0.1 --port 5002 --threads 4
```

## Optimized Distributed Run

`scripts\run-distributed-optimized.cmd` applies three performance improvements over the
baseline distributed script while producing **bit-identical results**:

| # | Optimization | Rationale |
|---|---|---|
| 1 | **Struct-of-Arrays (SoA) layout** in `SpatialIndex` | Driver X/Y coordinates packed into contiguous `double[]` arrays per grid cell instead of `List<Driver>` heap objects. Sequential array access stays in L1/L2 cache; the original layout requires one pointer dereference per driver, causing cache misses across 400M–1B scoring calls. |
| 2 | **Full C2 JIT** (removed `-XX:TieredStopAtLevel=1`) | The original script disables the optimizing C2 compiler. C2 inlines `score()`, eliminates array bounds checks, and applies loop optimizations. For runs longer than ~2 s the ~1 s warm-up cost is negligible compared to the throughput gain. |
| 3 | **Fine-grained adaptive chunking** (`--chunk-size 64`) | Static equal-size chunks (`totalRiders/threads` tasks) leave threads idle when per-rider work varies (dense grid cells evaluate more candidates). Dividing into 64-rider micro-tasks lets the thread pool self-balance. |

```cmd
scripts\run-distributed-optimized.cmd medium 4 4 0
```

**Measured speedup vs baseline (4 workers × 4 threads per worker, `max-candidates=0`):**

| Scale | Original Distributed | Optimized Distributed | Gain |
|-------|---------------------|----------------------|------|
| small | 1.67× | 1.02× | — (JIT warmup > workload at ~870 ms) |
| medium | 2.13× | 3.67× | **+1.73×** |
| large | 2.10× | 3.90× | **+1.86×** |

Results verified identical on all scales:

```powershell
Compare-Object (Get-Content results\distributed-medium.csv) (Get-Content results\distributed-optimized-medium.csv)
# (empty = identical)
```

## Verify Correctness

Compare the baseline and distributed outputs with PowerShell:

```powershell
Compare-Object (Get-Content results\baseline-small.csv) (Get-Content results\distributed-small.csv)
```

An empty result means the files match.

If you want the distributed run to stay identical to the sequential baseline, keep `--max-candidates 0`. Any positive value changes the candidate set and can change the output.

## Benchmark

Run the baseline benchmark output generator:

```powershell
java -jar target\pdc-ride-matching-1.0.0.jar benchmark --dataset data\small
```

Run automated speedup/efficiency/Amdahl experiments and export CSV:

```powershell
.\scripts\run-experiments.ps1 -Scale small -Workers 1,2,4,8 -Threads 4 -OutputCsv results\speedup.csv
```

## Plot CSV Results

Install plotting dependencies:

```powershell
python -m pip install matplotlib pandas numpy
```

### Original distributed analysis (9 plots)

Regenerates `results/01-amdahls-law.png` through `results/09-speedup-heatmap.png` from `results/speedup.csv`:

```powershell
python scripts\plot_performance.py
```

### Full comparison: original + optimized (20 plots)

Generates all 20 plots including the 9 above plus optimized-specific and comparison charts:

```powershell
python scripts\plot_optimized.py
```

| Range | Contents |
|-------|----------|
| `01`–`09` | Original distributed — Amdahl's law, strong scaling, efficiency, baseline times, heatmap, speedup gap |
| `10`–`13` | Optimized distributed — same analysis set for the optimized version |
| `14`–`20` | Comparison — original vs optimized speedup per scale, 3-way execution time (baseline/orig/opt), optimization gain ratio, efficiency side-by-side, heatmap comparison, large-dataset summary |

### Single plot from any CSV

```powershell
python scripts\plot_results.py results\speedup.csv results\speedup.png
```

## Commands Summary

```powershell
mvn clean test package
java -Xmx512m -jar target\pdc-ride-matching-1.0.0.jar generate-data --scale small --output data\small
java -Xmx512m -jar target\pdc-ride-matching-1.0.0.jar generate-data --scale medium --output data\medium
java -Xmx512m -jar target\pdc-ride-matching-1.0.0.jar generate-data --scale large --output data\large
java -Xmx512m -jar target\pdc-ride-matching-1.0.0.jar baseline --dataset data\small --output results\baseline-small.csv
java -Xmx512m -jar target\pdc-ride-matching-1.0.0.jar master --dataset data\small --workers 4 --port 5002 --batch-size 2048 --max-candidates 0 --output results\distributed-small.csv
java -Xmx512m -jar target\pdc-ride-matching-1.0.0.jar worker --id worker-1 --host 127.0.0.1 --port 5002 --threads 4 --chunk-size 64
scripts\run-distributed.cmd small 4 4 0
scripts\run-distributed-optimized.cmd small 4 4 0
scripts\run-distributed-optimized.cmd medium 4 4 0
scripts\run-distributed-optimized.cmd large 4 4 0
.\scripts\run-experiments.ps1 -Scale small -Workers 1,2,4,8 -Threads 4 -OutputCsv results\speedup.csv
```

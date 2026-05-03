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

Start the master and 3 workers on the small dataset:

```powershell
.\scripts\run-distributed.ps1 -Scale small -Workers 3 -Threads 4 -Port 5002 -BatchSize 2048
```

Or use the CMD script (recommended — launches workers simultaneously):
# One-command launch with K=10

```cmd
scripts\run-distributed.cmd small 3 4 10
```

Manual launch in separate terminals:

```powershell
# Terminal 1 — Master
# Or manually with --max-candidates
java -Xmx512m -jar target\pdc-ride-matching-1.0.0.jar master --dataset data\small --workers 3 --port 5002 --batch-size 2048 --max-candidates 10 --output results\distributed-small.csv
```

```powershell
# Terminal 2 — Worker 1
java -Xmx512m -jar target\pdc-ride-matching-1.0.0.jar worker --id worker-1 --host 127.0.0.1 --port 5002 --threads 4
```

```powershell
# Terminal 3 — Worker 2
java -Xmx512m -jar target\pdc-ride-matching-1.0.0.jar worker --id worker-2 --host 127.0.0.1 --port 5002 --threads 4
```

```powershell
# Terminal 4 — Worker 3
java -Xmx512m -jar target\pdc-ride-matching-1.0.0.jar worker --id worker-3 --host 127.0.0.1 --port 5002 --threads 4
```

## Verify Correctness

Compare the baseline and distributed outputs with PowerShell:

```powershell
Compare-Object (Get-Content results\baseline-small.csv) (Get-Content results\distributed-small.csv)
```

An empty result means the files match.

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
python -m pip install matplotlib
```

Generate a graph from a CSV file with columns such as `workers,speedup,efficiency`:

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
java -Xmx512m -jar target\pdc-ride-matching-1.0.0.jar master --dataset data\small --workers 3 --port 5002 --batch-size 2048 --max-candidates 10 --output results\distributed-small.csv
java -Xmx512m -jar target\pdc-ride-matching-1.0.0.jar worker --id worker-1 --host 127.0.0.1 --port 5002 --threads 4
java -Xmx512m -jar target\pdc-ride-matching-1.0.0.jar worker --id worker-2 --host 127.0.0.1 --port 5002 --threads 4
java -Xmx512m -jar target\pdc-ride-matching-1.0.0.jar worker --id worker-3 --host 127.0.0.1 --port 5002 --threads 4
scripts\run-distributed.cmd small 3 4 10
.\scripts\run-experiments.ps1 -Scale small -Workers 1,2,4,8 -Threads 4 -OutputCsv results\speedup.csv
```

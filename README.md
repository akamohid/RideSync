# RideSync⚡ – PDC Ride Match x Traffic-aware Routing

Distributed ride-matching system built with Java 17 and Maven.

## Members
- **Mohid Arshad** — Data Layer & Dataset Generation  ([akamohid](https://github.com/akamohid))  
- **Fatima Ehsan Niazi** — Matching Engine  ([fatimaehsanniazi](https://github.com/fatimaehsanniazi))  
- **M Umair Shakoor & Mohid Arshad** — Distributed Network Layer  ([Big-Raga](https://github.com/Big-Raga))

## Prerequisites
- Java 17+
- Maven 3.8+

## Build
```bash
mvn clean package
```

## Generate Datasets
```powershell
powershell -ExecutionPolicy Bypass -File scripts\generate-datasets.ps1
```
Generates `small`, `medium`, and `large` CSV datasets under `data\`.

## Run (Distributed Mode)

```bash
# Window 1 – Master
java -Xmx512m -jar target\pdc-ride-matching-1.0.0.jar master --dataset data\small --workers 3 --port 5002 --batch-size 2048 --max-candidates 10 --output results\distributed-small.csv

# Window 2
java -Xmx512m -jar target\pdc-ride-matching-1.0.0.jar worker --id worker-1 --host 127.0.0.1 --port 5002 --threads 4

# Window 3
java -Xmx512m -jar target\pdc-ride-matching-1.0.0.jar worker --id worker-2 --host 127.0.0.1 --port 5002 --threads 4

# Window 4
java -Xmx512m -jar target\pdc-ride-matching-1.0.0.jar worker --id worker-3 --host 127.0.0.1 --port 5002 --threads 4
```

## Project Structure
```
src/main/java/edu/nust/pdc/
├── config/       # Scale profiles (small / medium / large)
├── data/         # POJOs and I/O
├── dataset/      # Dataset generator
├── matching/     # Matching engine
└── net/          # Network layer
```

## Data Models
| Class | Description |
|---|---|
| `Rider` | Rider ID + pickup coordinates |
| `Driver` | Driver ID + location + availability |
| `Dataset` | Container for riders, drivers, and grid |
| `TrafficGrid` | 2D congestion grid with congestion lookup |
| `Batch` | Subset of riders sent to a worker |
| `MatchResult` | Final matched rider-driver pair |

## I/O & Generation
| Class | Description |
|---|---|
| `DatasetIO` | Reads & writes CSV/JSON datasets and results |
| `DatasetGenerator` | Seeded deterministic data generation for all 3 scales |

## Status
- [x] Step 1 — Project setup & config
- [x] Step 2 — Data model POJOs
- [x] Step 3 — I/O & Generation ✅

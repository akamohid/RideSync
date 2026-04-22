# RideSync — PDC Ride Matching with Traffic-Aware Routing

Distributed ride-matching system built with Java 17 and Maven.

## Members
- **Mohid Arshad** — Data Layer & Dataset Generation  
- **Fatima Ehsan Niazi** — Matching Engine  
- **M Umair Shakoor & Mohid Arshad** — Distributed Network Layer  

## Prerequisites
- Java 17+
- Maven 3.8+

## Build
```
mvn clean package
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
| `TrafficGrid` | 2D congestion grid with lookup |
| `Batch` | Subset of riders sent to a worker |
| `MatchResult` | Final matched rider-driver pair |

## Status
- [x] Step 1 — Project setup & config
- [x] Step 2 — Data model POJOs
- [ ] Step 3 — I/O & Generation
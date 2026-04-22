# RideSync — PDC Ride Matching

A distributed ride-matching system built using Java 17 and Maven.

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
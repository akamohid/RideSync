# RideSync Report Fixes (Targeted Corrections)

This file contains corrected text and replacement snippets for `report.md`.

---

## 1) Critical Correction: Which mode uses which optimization

### Problem in current report
Some wording in Sections 5.1/5.2 implies the normal distributed mode does not use the SoA/rankCandidateIds path.

### Correct statement
In the **current codebase**, both distributed modes (normal and optimized) execute `WorkerClient -> SpatialIndex.rankCandidateIds(...)`, which uses the SoA arrays (`cellX`, `cellY`, `cellIds`).

The practical differences between normal and optimized runs are:
- Normal script keeps `-XX:TieredStopAtLevel=1` (C1-only JIT)
- Optimized script removes that flag (C2 JIT allowed)
- Normal script uses default coarse chunking (`--chunk-size` omitted => `0`)
- Optimized script sets `--chunk-size 64`

---

## 2) Add this new subsection under Section 5.2

### 5.2.1 Optimization Coverage by Execution Mode

| Capability / Setting | Sequential Baseline | Distributed (Normal Script) | Distributed (Optimized Script) |
|---|---:|---:|---:|
| Uses `rankCandidates()` object path | ✅ | ❌ | ❌ |
| Uses `rankCandidateIds()` primitive path | ❌ | ✅ | ✅ |
| Uses SoA arrays (`cellX/cellY/cellIds`) | ❌ | ✅ | ✅ |
| `-XX:TieredStopAtLevel=1` (C1-only) | N/A | ✅ | ❌ |
| C2 JIT eligible | N/A | ❌ | ✅ |
| Worker chunking mode | N/A | Coarse (`chunkSize=0`) | Fine (`chunkSize=64`) |

**Interpretation:**
- The distributed implementation is the same protocol and same worker code path in both scripts.
- The optimized script accelerates that same path using C2 and fine-grained chunking.

---

## 3) Replace paragraph in Section 5.1 (Motivation)

### Replace with:
Profiling identified `SpatialIndex.rankCandidateIds()` as the dominant CPU consumer in distributed execution. On the medium dataset, the scoring loop is executed on the order of `5e8` times per run (`500,000 * 81 * 12.35`).

In the current implementation, distributed workers already use SoA-backed arrays in `rankCandidateIds()`. Therefore, the key runtime differences between the normal and optimized scripts are:
1. **JIT tiering policy** (`-XX:TieredStopAtLevel=1` vs full tiered C2), and
2. **Task granularity** (coarse chunking vs `chunkSize=64`).

These two settings materially change throughput of the same distributed code path.

---

## 4) Replace wording in Section 5.2 Optimization 1

### Replace first two paragraphs with:
**Problem (historical design issue):** score computation over heap-object layouts is cache-unfriendly due to pointer chasing.

**Current implementation status:** this project already implements a SoA layout inside `SpatialIndex` and the distributed worker path uses it through `rankCandidateIds()`. This optimization is active in both normal and optimized distributed runs. The optimized script does not toggle SoA on/off; it improves JIT tier and chunk granularity on top of the same SoA path.

---

## 5) Add clarification to Section 5.3 (Before/After)

### Add after Table 5.1
**Important scope note:** the measured “original” vs “optimized” differences in this report should be interpreted as **script/runtime configuration differences** on the distributed path (JIT tier + chunking), not a protocol change and not a sequential-vs-SoA code switch.

---

## 6) Small dataset slowdown explanation (final wording)

### Replace Section 7.5 with:
After enabling full C2 JIT (removing `-XX:TieredStopAtLevel=1`), small-dataset performance can degrade because total runtime is near the JVM warm-up window. The JIT promotion/optimization overhead is a larger fraction of execution time on small runs, while medium/large runs amortize that cost and benefit from faster steady-state machine code.

In addition, for small workloads, fixed distributed overheads (process startup, socket setup, synchronization, and merge coordination) become a dominant fraction, so optimizations aimed at long hot loops show limited net gain.

---

## 7) Amdahl / speedup reasoning corrections

### 7.1 Correctness note on `f > 1`
In Table 4.1, one estimated `f` value exceeds 1.0 (`large, p=2` gives `1.038`), which is non-physical. Keep this as measurement noise due to timing variability and model mismatch, and add this note:

> `f` estimates are reported from direct algebraic inversion and may slightly exceed [0,1] under noisy measurements. For interpretation, clamp to [0,1] or use regression across multiple p values.

### 7.2 Clarify what p means in this report
Your `p` is number of workers, while each worker has 4 threads. So Amdahl analysis is an empirical worker-level scaling model, not a strict total-core model. Add:

> Amdahl fitting here uses worker count as the scaling axis. Since each worker also uses intra-worker threading, this is a coarse-grained practical model rather than a strict single-level parallelism decomposition.

### 7.3 Keep these points (they are valid)
- `E(1) > 1` explanation is valid because distributed p=1 still has internal multithreading while baseline is single-threaded.
- Weak-scaling deviation explanation (riders/worker mismatch + radius growth + hardware contention) is valid.
- Bottleneck list in 4.9 is directionally valid.

---

## 8) Recommended text patch for Section 1.3 table (optional)

If you keep “Optimization 1” as already present in all distributed runs, then this row should not imply a mode toggle. Keep it as a historical design change:

| Design Decision | Original Plan | Final Implementation | Reason |
|---|---|---|---|
| Candidate scoring layout | Object-oriented bucket iteration | SoA-backed primitive arrays in distributed path | Improved cache locality in hot loops |

---

## 9) Quick verification checklist before final submission

- [ ] `report.md` explicitly states both distributed modes call `rankCandidateIds()`.
- [ ] `report.md` distinguishes **mode differences** as JIT tier + chunking, not protocol path.
- [ ] Section 7.5 explains small-run warm-up and fixed-overhead dominance.
- [ ] Amdahl caveat about `f > 1` and worker-level `p` is included.
- [ ] Team/contribution section names/IDs are final and no placeholders remain.

---

## 10) One-paragraph final summary you can paste in report

In the current codebase, both distributed modes (normal and optimized) use the same binary protocol and the same SoA-based worker scoring path (`rankCandidateIds`). The optimized script improves performance primarily by enabling full C2 JIT compilation (removing `-XX:TieredStopAtLevel=1`) and by using finer chunk granularity (`chunkSize=64`) to reduce thread idle time. Therefore, observed speedup differences between distributed modes are runtime-configuration effects over the same distributed algorithmic path, not a switch between different matching implementations.

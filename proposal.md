TABLE OF CONTENTS                              

TASK 1: PROBLEM SELECTION & COMPUTATIONAL MOTIVATION	3
Problem Statement	3
Input Characteristics	3
Complexity Justification	3
Why Parallelism Helps	4
Why Distribution Helps	4
Real World Relevance	5

TASK 2: PARALLEL ALGORITHM DESIGN	6
Decomposition Strategy	6
Exclusive Driver Assignment — Conflict Resolution	6
Task Dependency Graph	7
Communication Pattern	9
Granularity Analysis	9
Load Balancing	10

TASK 3: SYSTEM ARCHITECTURE DESIGN	11
Architecture Diagram	11
Message Protocol Design	12
Coordination Pattern	13
Intra-node Threading Plan	15
Deadlock Freedom Argument	16

 
TASK 1: PROBLEM SELECTION & COMPUTATIONAL MOTIVATION

Problem Statement
The selected computational problem is Smart Ride Matching with Traffic-Aware Routing. Given a set of R riders (each with a pickup coordinate) and D drivers (each with a current location and availability flag), the system must assign to every rider exactly one driver  and every driver to at most one rider, such that the assigned driver minimizes the estimated travel time for that rider. Travel time is the Euclidean distance between rider and driver weighted by a real-time traffic congestion multiplier drawn from a 2-D grid.

What makes the problem computationally challenging is the sheer number of pair-wise calculations required, on top of  the requirement of ensuring that each driver can be paired exclusively. The algorithm would have to match all riders with all possible drivers. In the worst-case scenario, R = 50,000 and D = 10,000, five hundred million (500,000,000) pair-wise calculations would have to take place every time the program executes. Every calculation requires the following operations to complete: (a) two subtraction operations and a square root operation for the Euclidean distance, (b) two division operations to find the grid cell that includes the midpoint, (c) one array read for the congestion factor, (d) one multiplication operation for the travel time, and (e) thread-safe write access to the driver table.

Input Characteristics
The input to the system consists of three distinct data structures:
•	Rider list: A file of R records, each containing riderId (int), pickupX (double), pickupY (double). The rider list scales linearly with the number of service requests.
•	Driver list: A file of D records, each containing driverId (int), locationX (double), locationY (double), available (boolean). Only available drivers participate in matching. Crucially, the available flag transitions from true to false once a driver is successfully claimed by a rider, ensuring exclusive assignment.
•	Traffic grid: An N x N matrix of doubles representing congestion multipliers in the range [0.5, 2.0]. A cell value of 1.0 represents free-flow traffic; values above 1.0 represent congestion.

The problem scales in two independent dimensions: adding more riders increases the outer-loop iterations and intensifies competition for a fixed pool of drivers; using a finer grid increases the spatial fidelity of congestion modelling. Both growth axes are independently parallelizable.

Complexity Justification
The naive sequential matching algorithm iterates over all riders in an outer loop and all drivers in an inner loop:

Sequential complexity: O(R x D)

For R = 50,000 riders and D = 10,000 drivers: Total evaluations = R x D = 50,000 x 10,000 = 500,000,000. At a conservative throughput of 500 million simple floating-point operations per second on a modern CPU, the sequential runtime approaches 1 second even without memory-access latency, cache misses, or function-call overhead. With all overheads included, realistic sequential runtimes for this input size exceed 10-15 seconds on a single core.

This quadratic relationship between inputs makes the sequential approach clearly insufficient for production-scale ride-matching services. Uber and Lyft, for example, must match thousands of concurrent requests in well under one second. The O(R x D) sequential algorithm cannot meet this SLA without parallelism.
With spatial filtering applied (the planned optimization), the matching complexity reduces from O(R x D) to O(R x k) where k is the average number of candidate drivers per spatial cell (k << D), achieving near-linear scaling in the common case.

Why Parallelism Helps
The outer loop over riders is embarrassingly parallel: the computation for rider i is entirely independent of the computation for rider j in the sense that each rider's optimal-driver search scans the same shared read-only driver location and traffic data. However, the act of claiming a driver introduces a shared write: once rider i claims driver d, driver d must be marked unavailable so that no other rider can also be assigned to d. This write is the only inter-rider dependency and is handled via atomic compare-and-set operations on the driver availability flags, preserving near-linear speedup while guaranteeing exclusive assignment.

This independence enables data parallelism: the rider list is partitioned into B equal-sized batches, and each batch is processed simultaneously. With P processors each handling R/P riders, the theoretical compute time reduces from T_seq = O(R x D) to T_par = O((R/P) x D) = O(R x D / P), achieving linear speedup in the compute phase. Multi-threading within each worker further exploits all available CPU cores on a single machine.

Why Distribution Helps
Distribution across multiple JVM processes is necessary for several reasons:
•	Memory capacity: The driver list (D = 10,000 entries) and traffic grid (50 x 50 = 2,500 cells) are small enough to fit in each worker's JVM heap. However, the rider list at 50,000 entries and the result map would together approach 100 MB in a single JVM. Distributing riders across workers keeps each worker's heap usage small and GC pressure low.
•	CPU core utilization: A single multi-core machine may be saturated by a sufficiently large input. Distributing work across multiple physical machines (or simulated separate JVMs) multiplies available CPU resources.
•	Fault isolation: If one worker JVM crashes, the master can reassign that worker's batch to a healthy worker. A single-process system has no such resilience.
•	Scalability demonstration: Adding a fourth or fifth worker reduces total wall-clock time proportionally (up to the Amdahl limit), validating horizontal scalability of the design.

Real World Relevance

Domain	Example Organization	Why Parallel & Distributed?
Ride-Sharing	Uber, Lyft, Careem	Millions of active riders; sub-second SLA; exclusive driver assignment required
Food Delivery	DoorDash, Food-panda	Simultaneous order dispatch across hundreds of delivery zones
Logistics Routing	Amazon Flex, FedEx	Package-to-driver matching-across city-scale grids with live traffic data
Emergency Dispatch	Ambulance & Fire Services	Nearest-vehicle assignment under strict latency constraints
Autonomous Vehicles	Waymo, Cruise	Continuous re-optimization of vehicles routing across a fleet

The Smart Ride Matching problem is therefore a simplified but structurally faithful model of a critical, latency-sensitive computation deployed at scale across the global transportation technology sector.  
TASK 2: PARALLEL ALGORITHM DESIGN

Decomposition Strategy
The parallelism used in this system is Data Parallelism. The total computation is the evaluation of every (rider, driver) pair followed by an exclusive claim of the best available driver. Since a driver can be claimed by at most one rider, the rider list is the natural decomposition axis while the driver availability state is the shared resource that must be protected.

The rider list of R entries is partitioned into B batches, each of size B_size = ceil(R / (W x T)) where W is the number of workers and T is the number of threads per worker. Each batch is an independent unit of work with one exception: the driver availability flags form a globally shared, writable structure that all threads across all workers must access atomically.

This decomposition is appropriate because: (1) the scoring phase (Euclidean distance + traffic multiplier) has zero inter-rider dependency and is fully parallel; (2) the claim phase uses a single atomic compare-and-set per rider on the shared driver availability array, which is a minimal and fast synchronization point; (3) the grain of a single rider (evaluating D drivers and then atomically claiming one) is large enough to amortize thread-scheduling overhead.

Exclusive Driver Assignment — Conflict Resolution
Because multiple threads (across the same worker or different workers) may simultaneously identify the same driver as optimal for their respective riders, a conflict resolution mechanism is essential. Without it, two riders could both be assigned driver d, violating the real-world constraint that a driver can serve only one rider at a time.

Shared Driver Availability Array
The master broadcasts a driver availability array, which is  an array of D Boolean flags, one per driver, initialized to true for every available driver, alongside the driver list during the INIT_DATA phase. Each worker JVM holds this array in its local heap. On its own, a local copy would allow two workers to both claim the same driver independently. To prevent this, the master maintains the single authoritative availability array and arbitrates all claim requests.

Optimistic Claim Protocol
Within a single worker, multiple threads share the same local availability array. Thread-safe claiming is achieved via AtomicBoolean flags (one per driver). Each thread uses compareAndSet(true, false) to attempt a claim: if it succeeds, the driver is exclusively assigned to that rider; if it fails (another thread already claimed that driver), the thread falls back to the next-best driver and retries. This optimistic compare-and-set loop continues down the rider's sorted candidate list until a free driver is found or no drivers remain.

Cross-Worker Conflict Resolution — CLAIM_REQUEST / CLAIM_RESPONSE
Because each worker holds a local copy of the availability array, two workers on different JVMs could independently claim the same driver without knowing it. To prevent cross-worker conflicts, a two-phase claim protocol is added to the message interface:
•	Phase 1 - Local scoring: Each worker threads scores all available drivers for its assigned rider and produces an ordered candidate list (best to worst travel time), using its local AtomicBoolean array to filter out already-claimed drivers within the same JVM.
•	Phase 2 - Master arbitration: The worker sends a CLAIM_REQUEST message to the master containing the riderId and an ordered list of candidate driverIds. The master atomically checks and marks the first unclaimed driver in the candidate list as claimed in its authoritative availability map, then replies with a CLAIM_RESPONSE containing the granted driverId (or NONE if all candidates were taken). The worker then records the final assignment.

This two-phase approach keeps all the expensive floating-point scoring work parallel and local, while delegating the lightweight exclusive-claim decision to the master as the single source of truth. The master processes CLAIM_REQUEST messages sequentially within each WorkerHandler thread, using a synchronized block on the shared claim map to prevent concurrent claim races at the master level.

Fallback on Exhausted Candidates
If a CLAIM_RESPONSE return NONE (all candidate drivers for a rider have been claimed by the time the master processes the request), the worker re-scores the remaining globally available drivers (fetched via a DRIVER_STATUS_REQUEST) and resubmits a new CLAIM_REQUEST. In practice, with D = 10,000 drivers and R = 50,000 riders, each rider has on average 1 driver assigned per 5 riders, so candidate exhaustion is rare in early batches but may occur as the driver pool shrinks. The fallback loop terminates when either a driver is granted or the master reports zero available drivers, in which case the rider is recorded as unmatched.

Task Dependency Graph
The task dependency graph below shows the full execution pipeline from input loading to final output aggregation. Tasks on the same horizontal level can be executed concurrently. The conflict resolution steps are shown as additional nodes in the per-rider processing path.

 
The critical path runs through a single worker's execution chain: Load Input -> Partition Riders 
-> Broadcast INIT_DATA -> [per-batch: Score Drivers -> Attempt Local Claim (CAS) -> CLAIM_REQUEST -> CLAIM_RESPONSE from Master] -> Return Results -> Aggregate -> Write Output. Tasks Score Drivers, Local CAS, and CLAIM_REQUEST/RESPONSE are replicated across all workers and execute concurrently per batch. The sequential bottlenecks are Load Input, Partition, Aggregate, and Write Output, which directly determine the lower bound on total execution time (Amdahl's Law constraint).




Communication Pattern
Message	Sender	Receiver	Data Carried
INIT_DATA	Master	All Workers	Full driver list (D entries) + availability flags + traffic grid (N x N) + cellSize
TASK_ASSIGN	Master	One Worker	Batch of riders (batchId + List<Rider>)
CLAIM_REQUEST	One Worker	Master	riderId + ordered List<driverId> (best to worst candidate)
CLAIM_RESPONSE	Master	One Worker	riderId + grantedDriverId (or NONE if all exhausted)
RESULT_RETURN	One Worker	Master	batchId + List<{riderId, driverId, travelTime}> (only confirmed claims)
NO_MORE_TASKS	Master	One Worker	Empty payload; signals queue exhaustion
SHUTDOWN	Master	All Workers	Empty payload; triggers JVM termination

There is no direct inter-worker communication. Workers communicate exclusively with the Master via point-to-point TCP sockets. This star topology eliminates any possibility of worker-to-worker deadlock and simplifies fault handling. 

Granularity Analysis
Batch size is the primary granularity parameter. Let R = 50,000 riders, W = 4 workers, T = 4 threads per worker. A natural batch size is R / (W x 2) = 3,125 riders per batch, giving 16 total batches. This provides enough tasks for dynamic load balancing while keeping per-message JSON payload sizes manageable (~250 KB per TASK_ASSIGN message at 80 bytes per rider entry). Note that with the conflict resolution protocol, each batch also generates up to batch_size CLAIM_REQUEST / CLAIM_RESPONSE roundtrips; the medium granularity of 3,125 riders thus produces at most 3,125 extra small messages per batch, which is negligible compared to the compute time saved by parallelism.
Granularity	Batch Size	Pros	Cons
Very Fine	10 riders	Maximum load balance	High per-message overhead; many claim round-trips
Fine	500 riders	Good load balance	Moderate overhead; ~100 TCP messages + claim traffic
Medium (chosen)	3,125 riders	Balanced: low overhead + good balance	Slight imbalance on final batch possible
Coarse	12,500 riders	Very low overhead	Workers may be idle late; larger claim bursts
Very Coarse	50,000 riders	Zero overhead	No parallelism; sequential execution

Load Balancing

The system uses dynamic task assignment (pull model). The Master maintains a FIFO queue of B batches. When a worker completes a batch and has returned all CLAIM_REQUEST messages for that batch, it immediately sends a TASK_REQUEST to pull the next available batch. This self-scheduling approach naturally handles load imbalance: faster workers pull more work from the queue. 

A secondary load imbalance risk arises from the conflict resolution protocol: riders processed later in the run face a more depleted driver pool and may require multiple CLAIM_REQUEST retries, increasing their per-rider processing time. This is partially mitigated by processing batches dynamically and by the master's fast sequential claim arbitration (the master's claim check is O(1) per candidate).





 
TASK 3: SYSTEM ARCHITECTURE DESIGN

Architecture Diagram
The system follows a Master-Worker distributed architecture. The Master is a single JVM process acting as coordinator and claim arbitrator. Three or more Worker JVMs connect to the Master via TCP sockets, receive task batches, perform parallel scoring using internal thread pools, submit CLAIM_REQUEST messages to the master for exclusive driver assignment, and return confirmed results. All communication is strictly through Java TCP sockets.


 
 
Component	Role	Key Responsibilities
Master JVM	Coordinator / Task Distributor / Claim Arbitrator	Load input files; partition rider list into batches; maintain task queue; broadcast INIT_DATA (including driver availability flags); dispatch batches; arbitrate CLAIM_REQUEST messages (exclusive driver assignment); aggregate confirmed results; detect completion; broadcast SHUTDOWN
WorkerHandler Threads	Per-worker I/O Manager (on Master)	One thread per worker socket; reads TASK_REQUEST, CLAIM_REQUEST, RESULT_RETURN; writes TASK_ASSIGN, CLAIM_RESPONSE, NO_MORE_TASKS, SHUTDOWN; accesses task queue and claim map under synchronization
Worker JVM (x3+)	Compute Node	Connect to Master; receive INIT_DATA; enter pull loop; receive batches; launch thread pool; score rider-driver pairs; perform local CAS claims; send CLAIM_REQUEST to master for arbitration; receive CLAIM_RESPONSE; return confirmed results; terminate on SHUTDOWN
Worker Thread Pool	Intra-node Parallelism	Fixed ExecutorService of T threads; each thread scores all drivers for one rider, performs local AtomicBoolean CAS, sends CLAIM_REQUEST, awaits CLAIM_RESPONSE, stores confirmed result in ConcurrentHashMap; Future.get() blocks until all threads complete

Message Protocol Design
All messages are serialized as JSON strings terminated by a newline character (\n) and transmitted over a BufferedWriter/BufferedReader pair wrapping the raw TCP socket streams. JSON is chosen for human-readability during debugging and ease of parsing with standard Java libraries (Gson or Jackson).

Message Type	Direction	Payload Fields	When Sent
INIT_REQUEST	Worker -> Master	{ type, workerId }	Immediately after TCP connection established
INIT_DATA	Master -> Worker	{ type, drivers:[...], availabilityFlags:[...], trafficGrid:[[...]], cellSize }	After all W workers have sent INIT_REQUEST
TASK_REQUEST	Worker -> Master	{ type, workerId }	After INIT_DATA received; after each completed batch (all claims resolved)
TASK_ASSIGN	Master -> Worker	{ type, batchId, riders:[{riderId, x, y}, ...] }	When a batch is dequeued from the task queue
CLAIM_REQUEST	Worker -> Master	{ type, workerId, riderId, candidateDriverIds:[id1, id2, ...] }	After thread scores all drivers and sorts candidates; one message per rider
CLAIM_RESPONSE	Master -> Worker	{ type, riderId, grantedDriverId, travelTime } or { type, riderId, grantedDriverId: null }	Immediately after master arbitrates the claim
RESULT_RETURN	Worker -> Master	{ type, batchId, results:[{riderId, driverId, travelTime}] }	After all CLAIM_RESPONSE messages for the batch are received
NO_MORE_TASKS	Master -> Worker	{ type }	When task queue is empty and worker requests work
ERROR	Either -> Either	{ type, workerId, batchId, message }	On exception during processing; triggers retry
SHUTDOWN	Master -> Worker	{ type }	After completedBatches == totalBatches

Serialization Detail
Each JSON message is written as a single line followed by \n using BufferedWriter.write(json + "\n") and flushed immediately. The receiver reads one line at a time using BufferedReader.readLine(). This line-delimited protocol eliminates the need for a separate length-prefix framing mechanism. A socket read timeout of 30 seconds is configured on all sockets to detect dead workers.

Coordination Pattern
The system uses a Task Queue with Dynamic Assignment (pull model) combined with per-rider Master Arbitration for exclusive driver claiming. This pattern is selected over barrier synchronization and pipeline for the following reasons:

Pattern	Considered	Why Rejected / Chosen
Barrier Synchronization	Yes	Rejected: requires all workers to complete their assigned batch before any can receive a new one. A single slow worker stalls all others, wasting available compute capacity.
Pipeline	Yes	Rejected: the ride-matching computation is a single-stage reduction with no natural pipeline decomposition.
Task Queue with Dynamic Assignment	Yes — CHOSEN	Chosen: workers pull tasks independently. Fast workers naturally pull more batches. Stragglers only delay their own final batch, not others.
Global Lock on Driver Pool	Yes	Rejected: a single global mutex on the driver availability array would serialize all claim operations, eliminating parallelism in the claim phase and becoming a bottleneck as R grows.
Two-Phase (Local CAS + Master Arbitration)	Yes — CHOSEN	Chosen for conflict resolution: local AtomicBoolean CAS handles intra-worker conflicts with zero network overhead; master arbitration handles cross-worker conflicts with a single lightweight TCP round-trip per rider.

Completion Detection
The Master tracks completion using three counters:
•	totalBatches: computed when the task queue is built (totalBatches = ceil(R / batchSize)).
•	completedBatches: an AtomicInteger incremented by each WorkerHandler thread upon receiving a RESULT_RETURN message.
•	Termination condition: when completedBatches.get() == totalBatches, the Master broadcasts SHUTDOWN to all connected workers and begins result aggregation.

This condition is checked after each AtomicInteger increment in the RESULT_RETURN handler, ensuring the Master detects completion at the earliest possible moment without polling.

Result Aggregation
The Master aggregates results into a single HashMap<Integer, MatchResult> keyed by riderId. Since each WorkerHandler thread appends RESULT_RETURN payloads to this map sequentially (from within its own single reader thread), there is no concurrent write contention on the master-side result map. After all results are received, the map is sorted by riderId and written to the output file. Because all assignments in the result set were arbitrated by the master, uniqueness of driverIds in the output is structurally guaranteed: no driverId can appear more than once.

Fault Handling — ERROR Message Response Policy
When the master receives an ERROR message from a worker (containing workerId, batchId, and an error description), it follows this policy:
1.	Re-enqueue the batch: the failed batchId is pushed back to the front of the task queue. Any CLAIM_REQUEST messages from that batch that were already processed and granted by the master are revoked: the granted driverIds are returned to the available pool in the master's authoritative claim map so they can be reassigned when the batch is retried.
2.	Mark the worker suspect: the master logs the worker as having failed. If the same worker sends another ERROR for a second batch, its socket is closed and it is removed from the active worker pool.
3.	No worker replacement: the system does not spawn new workers at runtime. Remaining workers absorb the retried batches naturally via the pull model.
4.	Unrecoverable failure: if fewer than 1 worker remains active and batches are still pending, the master logs a fatal error and terminates.

Claim revocation on batch failure is critical: without it, drivers granted to a failed batch would remain permanently marked as unavailable in the master's claim map even though no rider was actually assigned to them, reducing the effective driver pool and potentially leaving riders unmatched unnecessarily.

Intra-node Threading Plan
Each Worker JVM creates a fixed-size thread pool using Executors.newFixedThreadPool(nThreads), where the thread count is passed as a command-line argument (default: 4). When a batch arrives, the worker loops through every rider in that batch and submits a separate Callable<MatchResult> task to the pool for each one.

Each Callable task performs the following steps:
5.	Score all drivers: iterate over the driver list, compute the traffic-weighted Euclidean distance for each available driver, and produce a sorted candidate list (ascending travel time).
6.	Local CAS claim: attempt compareAndSet(true, false) on the local AtomicBoolean for the top candidate. If successful, proceed to step 3. If unsuccessful (another thread in the same JVM already claimed that driver), move to the next candidate and retry.
7.	Master arbitration: send a CLAIM_REQUEST containing the riderId and the ordered candidate list to the master. Block on the corresponding CLAIM_RESPONSE.
8.	Record result: if the master grants a driver, store {riderId, grantedDriverId, travelTime} in the ConcurrentHashMap. If the master returns NONE, record the rider as unmatched.

The worker collects all results by calling Future.get() on each submitted task, which blocks until that thread finishes. Only once every thread in the batch has completed and all CLAIM_RESPONSE messages have been received do the worker send its RESULT_RETURN message back to the master.

Java Concurrency Primitives
•	Thread pool management: ExecutorService (via newFixedThreadPool) manages the worker's thread lifecycle. It limits how many threads run at once and reuses threads across multiple batches, avoiding the overhead of creating new threads for every task.
•	Per-rider task wrapper: Callable<MatchResult> wraps each per-rider computation as a self-contained task that returns a typed result.
•	Result tracking: Future<MatchResult> lets the submitter track when each task finishes. Calling .get() blocks until the result is ready, ensuring all riders in a batch are matched before results are returned.
•	Local exclusive claiming: AtomicBoolean[] (one per driver) enables lock-free compareAndSet(true, false) for intra-worker conflict resolution without a mutex.
•	Thread-safe result storage: ConcurrentHashMap<Integer, MatchResult> provides thread-safe storage for results. Its internal lock striping means threads writing different rider keys never block each other.
•	Lock-free completion counter: AtomicInteger (for completedBatches on the master) allows lock-free incrementing each time a RESULT_RETURN message arrives.
•	Queue and claim map access control: synchronized blocks guard both the master's task queue and the master's authoritative driver claim map so that only one WorkerHandler thread can dequeue a batch or arbitrate a claim at a time.

Critical Sections and Synchronisation
•	Task queue: The LinkedList<Batch> on the master is accessed by multiple WorkerHandler threads simultaneously. Guarded with a synchronized block inside dispatchTask().
•	Driver claims map (master): The master's HashMap<Integer, Boolean> tracking which drivers have been claimed must be accessed atomically. A synchronized block on a dedicated claimLock object ensures only one WorkerHandler thread arbitrates a CLAIM_REQUEST at a time, preventing two workers from being granted the same driver.
•	Local AtomicBoolean driver flags (worker): The worker's AtomicBoolean[] array is accessed concurrently by all threads in the pool. compareAndSet provides exclusive lock-free access; no additional mutex is needed for the local claim step.
•	Completion counter: AtomicInteger on the master for completedBatches; incremented lock-free when RESULT_RETURN arrives.
•	Result map (worker): ConcurrentHashMap inside each worker handles concurrent writes from multiple threads in the same batch.
•	Socket output stream: Each socket OutputStream on the master is owned exclusively by one WorkerHandler thread. No two threads share the same socket, so there is no risk of interleaved JSON writes corrupting the message stream.

Deadlock Freedom Argument
A deadlock requires a circular wait: thread A holds lock X and waits for lock Y, while thread B holds lock Y and waits for lock X. We demonstrate this cannot occur in our system:
•	Lock inventory: The system uses three lock domains—taskQueue (for batch scheduling), claimLock (for driver claim arbitration), and ConcurrentHashMap internals (used implicitly by workers).
•	No nested locking: Threads never hold more than one lock at a time; a WorkerHandler acquires taskQueue to dequeue work, releases it, and later acquires claimLock for claim processing—these are sequential, not nested.
•	No circular wait: Worker threads may block on socket I/O while waiting for responses, but they hold no locks during this time, while the Master acquires claimLock only briefly and releases it before any blocking operation.
•	No inter-worker dependency: Workers communicate only with the Master and never wait on each other, eliminating cross-dependencies.
•	Thread pool termination: ExecutorService.shutdown() and awaitTermination() are invoked without holding any locks, so no blocking occurs while owning a resource, preventing deadlock.

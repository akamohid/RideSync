
National University of Sciences & Technology
School of Electrical Engineering and Computer Science
Department of Computing

Semester Project: Parallel & Distributed Computing Solution
Maximum Marks: Based on the Rubric 	Instructor: Dr. Fahad Ahmed Satti 
Announcement Date:  3rd April 2026	Due Date :  15th May 2026
Course: CS-347 Parallel & Distributed Computing (2+1)	Group Size : 3 Students

Introduction
This is the implementation and evaluation phase of your Parallel & Distributed Computing project. Building on the problem selection, algorithm design, and architecture plan submitted in Assignment 3, you will now implement, test, measure, and optimize a full parallel and distributed computing solution in Java.
Both dimensions are mandatory your solution must use multi-threading within each node AND distribute computation across multiple independent JVM processes communicating via sockets. A solution that only uses threads on a single process is incomplete.
Objective
Implement the parallel and distributed computing solution designed in Assignment 3, evaluate its performance rigorously, and demonstrate a measurable speedup over the sequential baseline. Your system must:
•	Run across a minimum of 3 distributed worker processes (separate JVMs) coordinated by a master process
•	Use multi-threading inside each worker to exploit local CPU cores
•	Communicate exclusively via message passing over Java sockets no shared memory across processes
•	Produce results that are correct and identical to the sequential baseline for all inputs
•	Be evaluated with speedup, efficiency, and scalability metrics
•	Be compared against the theoretical bound predicted by Amdahl's Law
Project Requirements
1. Implementation in Java
Your implementation must include all three of the following components:
Component 1 : Sequential Baseline:
◦	Single-threaded, single-process implementation of the complete algorithm
◦	Used as the correctness reference and performance baseline (T_seq)
◦	Must be functionally identical to the parallel version for the same input
Component 2 : Intra-node Parallelism (Multi-threading):
◦	Each worker process uses Java threads to process its assigned partition in parallel
◦	Use java.util.concurrent: ExecutorService, ForkJoinPool, or explicit Thread management
◦	Critical sections must be protected using locks, synchronized blocks, or atomic variables
◦	Demonstrate freedom from race conditions and deadlocks
Component 3 : Inter-node Distribution (Message Passing):
◦	Master partitions the input and sends work to each worker via TCP sockets
◦	Workers compute their partition (using threads internally) and send results back
◦	Master aggregates all partial results into the final answer
◦	All messages must follow the protocol designed in Assignment 3


2. Correctness & Synchronization
Your parallel and distributed implementation must produce results that exactly match the sequential baseline for all test inputs. You must:
•	Run both sequential and parallel versions on the same inputs and verify output matches
•	Identify every critical section in your code and justify your synchronization choice
•	Demonstrate that your system is free from race conditions under concurrent execution
•	Explain how deadlock is avoided in your design
3. Performance Analysis Speedup & Amdahl's Law (MANDATORY)
Performance analysis is the scientific core of this project. You must measure and report the following metrics:
•	T_seq execution time of the sequential baseline
•	T_par(p)  execution time of the parallel/distributed version with p workers/threads
•	Speedup S(p) = T_seq / T_par(p)
•	Efficiency E(p) = S(p) / p
•	Parallel fraction f estimated from your measurements using Amdahl's Law
You must compare your measured speedup against the theoretical maximum predicted by Amdahl's Law:  S_max = 1 / ((1 - f) + f/p).  Explain any gap between theory and experiment.
Experiments to conduct:
•	Vary worker/thread count: 1, 2, 4, 8 (and more if hardware allows)
•	Vary input/problem size: small, medium, large observe strong and weak scaling
•	Identify and quantify the sequential bottleneck in your system
All results must be saved as CSV files and visualized as clearly labelled graphs.
4. Distributed Communication & Coordination
Implement the message passing protocol designed in Assignment 3. Your running system must demonstrate:
•	Correct message delivery with no lost or corrupted messages
•	Proper coordination master correctly detects when all workers have finished
•	Deterministic result aggregation the same input always produces the same output
•	Correct behaviour under concurrent message delivery
You must implement the coordination pattern chosen in Assignment 3:
•	Barrier synchronization master waits for all workers before proceeding to the next phase
•	Task queue with dynamic assignment master distributes tasks on-demand as workers finish
•	Pipeline workers pass partial results along a chain of stages
5. Optimization (Open-Ended Contribution)
Design and implement at least one optimization that produces a measurable improvement over your baseline parallel implementation. Suggested directions:
•	Cache-aware data layout : restructure data to improve memory locality and reduce cache misses
•	Work stealing : idle workers steal tasks from busy workers to improve load balance
•	Overlapping communication and computation : pipeline socket I/O with local computation
•	Adaptive task granularity : dynamically adjust chunk size based on observed worker load
•	Pipeline parallelism : decompose computation into stages and run stages concurrently
Your optimization must be motivated by profiling data or analysis, implemented and tested, and evaluated with before/after performance results.
Group Work Requirements
Each group of 3 must clearly define, document. All members must contribute to implementation and participate fully in the demo and viva. Members who cannot explain their part during viva will be marked accordingly.



Project Report Requirements
Your final report must build on and reference your Assignment 3 design document. It must include at least the following sections:
1.  Introduction & Recap of Design
◦	Brief summary of the problem and design decisions from Assignment 3
◦	Any changes made to the original design during implementation, and why
2.  Implementation Details
◦	Sequential baseline description
◦	Multi-threaded worker implementation and synchronization mechanisms
◦	Socket communication implementation and coordination pattern
3.  Correctness Verification
◦	Evidence that parallel output matches sequential baseline
◦	Critical section identification and synchronization justification
◦	Argument for deadlock freedom
4.  Performance Analysis
◦	Speedup and efficiency tables and graphs
◦	Amdahl's Law comparison with measured parallel fraction
◦	Strong and weak scaling results
◦	Bottleneck identification and trade-off discussion
5.  Optimization Results
◦	Optimization motivation and design
◦	Before/after performance comparison with graphs
6.  Insights and Limitations
◦	What worked and what did not
◦	Scalability limits and root causes
◦	Future improvements
Submission Requirements
One group member should submit the following on LMS before the due date:
•	Project Report (PDF)
•	Source Code (Java) fully compilable and runnable
•	CSV results files and all performance graphs
•	README  instructions to compile, configure worker count, and run experiments
Demo & Viva
Each group must demonstrate a fully working system live. The demo must show:
•	Sequential baseline executing and producing correct output
•	Master process launching and coordinating distributed worker processes
•	Workers computing in parallel and returning results
•	Correct final output matching the sequential baseline
•	Speedup graphs showing improvement with increasing workers/threads
Students must be prepared to answer questions on:
•	Algorithm decomposition choices and trade-offs
•	Synchronization mechanisms and correctness guarantees
•	Amdahl's Law analysis and what limits further speedup
•	Message protocol design and coordination pattern
•	Optimization impact and what else could be improved
Viva participation is mandatory to receive any marks for the Semester Project.

Marking Rubric
Assessment Item	CLO	0 Marks	1 Mark	2 Marks	3 Marks	4 Marks	5 Marks
Sequential Baseline & Correctness Verification	CLO 3 (Create)	No baseline implemented.	Baseline exists but output is incorrect or incomplete.	Baseline runs correctly; parallel output partially matches for small inputs.	Baseline correct; parallel output consistently matches for all tested inputs.	Full correctness demonstrated; critical sections identified and synchronization justified.	Excellent: output verified across all inputs, deadlock freedom argued, synchronization formally justified.
Intra-node Parallelism (Multi-threading)	CLO 3 (Create)	No multithreading implemented.	Threads created but execute sequentially or produce incorrect results.	Multithreading works but has race conditions or deadlocks.	Correct multithreading with protected critical sections; no observable race conditions.	Good parallel execution with efficient thread management and minimal synchronization overhead.	Excellent: clean, correct, efficient multi-threaded workers with formal correctness justification.
Distributed Architecture (Master-Worker, Sockets)	CLO 3 (Create)	No distributed implementation.	Single process only; no socket communication.	Two processes communicate but coordination is broken or messages are lost.	Master and 3+ workers communicate correctly; task distribution and aggregation work.	Good architecture: reliable sockets, correct task distribution, and result aggregation.	Excellent: robust master-worker system with correct protocol, error handling, and clean aggregation.
Speedup & Amdahl's Law Analysis	CLO 1 (Analyze)	No performance analysis.	Runtime measured for one configuration; no speedup computed.	Speedup computed but not compared to Amdahl's Law; no efficiency analysis.	Speedup and efficiency computed; compared to Amdahl's bound with basic analysis.	Good: speedup curves, efficiency, scalability analysis, and Amdahl's Law comparison.	Excellent: strong/weak scaling, Amdahl's Law with measured parallel fraction, bottleneck identification.
Experimental Evaluation (CSV & Graphs)	CLO 2 (Evaluate)	No experiments conducted.	One metric collected with no visualization.	Some metrics collected but experiments are incomplete or graphs are absent.	Satisfactory: 3+ metrics, CSV data, and clear graphs across worker counts and input sizes.	Good: all metrics, well-labelled graphs, CSV results, comparison across all configurations.	Excellent: comprehensive, reproducible experiments with strong statistical analysis.
Distributed Communication & Coordination	CLO 3 (Create)	No inter-process communication.	Messages sent but ordering is wrong or results are corrupted.	Basic communication works but coordination barriers or ordering are flawed.	Correct message passing and coordination; minor ordering issues under high load.	Reliable communication with correct event ordering and proper barrier synchronization.	Excellent: robust protocol, verified ordering, efficient message structure, fault awareness.
Optimization (Open-Ended)	CLO 3 (Create)	No optimization attempted.	Optimization mentioned but not implemented.	Optimization partially implemented with no measurable impact.	One optimization implemented and tested with before/after results.	Good: clear motivation, implementation, and quantified performance gain.	Excellent: well-justified optimization with strong experimental evidence and significant improvement.
Group Collaboration & Role Definition	CLO 4 (Implement)	No evidence of collaboration.	Roles mentioned but work is heavily imbalanced.	Some members contributed minimally.	Satisfactory teamwork; roles defined and all members contributed meaningfully.	Good collaboration; all members participated in demo and viva.	Excellent: clear roles, balanced contributions, full active participation in demo and viva.
Demo & Viva	CLO 4 (Implement)	No demo or viva conducted.	System non-functional; viva responses very weak.	Partial demo; basic questions answered but depth is lacking.	Satisfactory demo showing distributed execution and speedup; adequate viva answers.	Good demo: distributed workers, parallel execution, and speedup graphs shown; clear viva.	Excellent: full live demo of all components; confident answers on design, Amdahl's, and trade-offs.
Project Report & Documentation	CLO 2 (Evaluate)	No report submitted.	Report missing most required sections.	Report covers basic sections but lacks depth and clarity.	Satisfactory report covering all required sections with minor gaps.	Good, well-structured report with in-depth coverage.	Excellent professional report: all sections, graphs, analysis, insights, and clear technical writing.


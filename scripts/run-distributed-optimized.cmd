@echo off
setlocal

set SCALE=%1
if "%SCALE%"=="" set SCALE=small
set WORKERS=%2
if "%WORKERS%"=="" set WORKERS=3
set THREADS=%3
if "%THREADS%"=="" set THREADS=4
set MAX_CANDIDATES=%4
if "%MAX_CANDIDATES%"=="" set MAX_CANDIDATES=0
set PORT=5003
set BATCHSIZE=2048
set CHUNK_SIZE=64
set JAR=target\pdc-ride-matching-1.0.0.jar
set DATASET=data\%SCALE%
set OUTPUT=results\distributed-optimized-%SCALE%.csv
set JAVA_OPTS=-Xmx8192m -XX:+UseParallelGC

echo === PDC Distributed Ride Matching (Optimized: SoA Layout + Full JIT) ===
echo Scale=%SCALE% Workers=%WORKERS% Threads=%THREADS% BatchSize=%BATCHSIZE% ChunkSize=%CHUNK_SIZE% MaxCandidates=%MAX_CANDIDATES%
echo.
echo Optimizations applied:
echo   1. SoA (struct-of-arrays) driver layout in SpatialIndex: driver X/Y coordinates
echo      stored in contiguous double[] arrays per grid cell instead of List^<Driver^> objects.
echo      Sequential array access fits in L1/L2 cache lines; eliminates per-driver pointer
echo      dereference and iterator overhead in the 400M+ candidate scoring calls.
echo   2. Full C2 JIT compilation: original script uses -XX:TieredStopAtLevel=1 which
echo      disables the optimizing C2 compiler. C2 inlines score(), eliminates bounds
echo      checks, and may vectorize the inner loop. Removing TieredStopAtLevel=1 lets
echo      the JVM promote the hot scoring loop to C2 after warm-up (~1 second), giving
echo      substantially better throughput for the remaining 20+ seconds of computation.
echo   3. Fine-grained adaptive chunking (--chunk-size %CHUNK_SIZE%): divides each worker's
echo      rider batch into small %CHUNK_SIZE%-rider chunks so the thread pool self-balances
echo      when per-rider work varies (dense grid cells have more candidate drivers).
echo.

REM Start workers FIRST (they retry connection until master is ready)
for /L %%i in (1,1,%WORKERS%) do (
    start /B java %JAVA_OPTS% -jar %JAR% worker --id worker-%%i --host 127.0.0.1 --port %PORT% --threads %THREADS% --chunk-size %CHUNK_SIZE%
)

REM Start master (foreground, waits for completion)
java %JAVA_OPTS% -jar %JAR% master --dataset %DATASET% --workers %WORKERS% --port %PORT% --batch-size %BATCHSIZE% --max-candidates %MAX_CANDIDATES% --output %OUTPUT%

echo.
echo === Done ===

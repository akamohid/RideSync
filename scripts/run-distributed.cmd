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
set PORT=5002
set BATCHSIZE=2048
set JAR=target\pdc-ride-matching-1.0.0.jar
set DATASET=data\%SCALE%
set OUTPUT=results\distributed-%SCALE%.csv
set JAVA_OPTS=-Xmx8192m -XX:TieredStopAtLevel=1 -XX:+UseParallelGC

echo === PDC Distributed Ride Matching ===
echo Scale=%SCALE% Workers=%WORKERS% Threads=%THREADS% BatchSize=%BATCHSIZE% MaxCandidates=%MAX_CANDIDATES%
echo.

REM Start workers FIRST (they retry connection until master is ready)
for /L %%i in (1,1,%WORKERS%) do (
    start /B java %JAVA_OPTS% -jar %JAR% worker --id worker-%%i --host 127.0.0.1 --port %PORT% --threads %THREADS%
)

REM Start master (foreground, waits for completion)
java %JAVA_OPTS% -jar %JAR% master --dataset %DATASET% --workers %WORKERS% --port %PORT% --batch-size %BATCHSIZE% --max-candidates %MAX_CANDIDATES% --output %OUTPUT%

echo.
echo === Done ===

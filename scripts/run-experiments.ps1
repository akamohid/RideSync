$ErrorActionPreference = 'Stop'

param(
    [ValidateSet('small', 'medium', 'large')]
    [string]$Scale = 'small',
    [int[]]$Workers = @(1, 2, 4, 8),
    [int]$Threads = 4,
    [int]$PortBase = 5100,
    [int]$BatchSize = 1024,
    [string]$OutputCsv = 'results\speedup.csv'
)

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

mvn -q -DskipTests package

$jar = Get-ChildItem "$root\target\pdc-ride-matching-*.jar" |
    Where-Object { $_.Name -notlike 'original-*' } |
    Sort-Object Name -Descending |
    Select-Object -First 1
if (-not $jar) {
    throw 'Could not find the project jar in target/.'
}

$dataset = "$root\data\$Scale"
if (-not (Test-Path $dataset)) {
    throw "Dataset path not found: $dataset. Run scripts\generate-datasets.ps1 first."
}

New-Item -ItemType Directory -Force -Path (Split-Path -Parent "$root\$OutputCsv") | Out-Null

$baselineOut = "$root\results\baseline-$Scale.csv"
$baselineMs = (Measure-Command {
    java -jar $jar.FullName baseline --dataset $dataset --output $baselineOut | Out-Null
}).TotalMilliseconds

$rows = @()
foreach ($workerCount in $Workers) {
    $port = $PortBase + $workerCount
    $distOut = "$root\results\distributed-$Scale-w$workerCount.csv"

    $master = Start-Process java -ArgumentList @(
        '-jar', $jar.FullName,
        'master',
        '--dataset', $dataset,
        '--workers', $workerCount,
        '--port', $port,
        '--batch-size', $BatchSize,
        '--output', $distOut
    ) -PassThru -WindowStyle Normal

    Start-Sleep -Seconds 2
    $workerProcesses = @()
    for ($i = 1; $i -le $workerCount; $i++) {
        $workerProcesses += Start-Process java -ArgumentList @(
            '-jar', $jar.FullName,
            'worker',
            '--id', "worker-$i",
            '--host', '127.0.0.1',
            '--port', $port,
            '--threads', $Threads
        ) -PassThru -WindowStyle Normal
    }

    $start = Get-Date
    Wait-Process -Id $master.Id
    $end = Get-Date
    $distributedMs = ($end - $start).TotalMilliseconds

    foreach ($p in $workerProcesses) {
        if (-not $p.HasExited) {
            Stop-Process -Id $p.Id -Force
        }
    }

    $speedup = $baselineMs / $distributedMs
    $efficiency = $speedup / $workerCount
    $parallelFraction = if ($workerCount -gt 1) { (1 - (1 / $speedup)) / (1 - (1 / $workerCount)) } else { 0.0 }
    $amdahlBound = if ($workerCount -gt 0) { 1 / ((1 - $parallelFraction) + ($parallelFraction / $workerCount)) } else { 0.0 }

    $rows += [PSCustomObject]@{
        scale = $Scale
        workers = $workerCount
        threadsPerWorker = $Threads
        baselineMs = [math]::Round($baselineMs, 3)
        distributedMs = [math]::Round($distributedMs, 3)
        speedup = [math]::Round($speedup, 4)
        efficiency = [math]::Round($efficiency, 4)
        parallelFraction = [math]::Round($parallelFraction, 4)
        amdahlBound = [math]::Round($amdahlBound, 4)
    }
}

$rows | Export-Csv -Path "$root\$OutputCsv" -NoTypeInformation

$ErrorActionPreference = 'Stop'

param(
    [ValidateSet('small', 'medium', 'large')]
    [string]$Scale = 'small',
    [int]$Workers = 3,
    [int]$Threads = 4,
    [int]$Port = 5000,
    [int]$BatchSize = 2048
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

$javaOpts = @('-Xmx512m', '-XX:TieredStopAtLevel=1', '-XX:+UseParallelGC')
$dataset = "$root\data\$Scale"
$masterArgs = @('master', '--dataset', $dataset, '--workers', $Workers, '--port', $Port, '--batch-size', $BatchSize, '--output', "$root\results\distributed-$Scale.csv")

# Start workers FIRST (they retry connection until master binds its socket)
for ($workerIndex = 1; $workerIndex -le $Workers; $workerIndex++) {
    Start-Process java -ArgumentList ($javaOpts + @('-jar', $jar.FullName, 'worker', '--id', "worker-$workerIndex", '--host', '127.0.0.1', '--port', $Port, '--threads', $Threads)) -WindowStyle Normal
}

# Start master (workers connect via retry — no sleep needed)
$master = Start-Process java -ArgumentList ($javaOpts + @('-jar', $jar.FullName) + $masterArgs) -PassThru -WindowStyle Normal

Wait-Process -Id $master.Id

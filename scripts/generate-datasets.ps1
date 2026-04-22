$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

mvn -q -DskipTests package

$jar = Get-ChildItem "$root\target\pdc-ride-matching-*.jar" |
    Where-Object { $_.Name -notlike 'original-*' } |
    Sort-Object Name -Descending |
    Select-Object -First 1

if (-not $jar) { throw 'Could not find the project jar in target/.' }

java -jar $jar.FullName generate-data --scale small  --output data\small
java -jar $jar.FullName generate-data --scale medium --output data\medium
java -jar $jar.FullName generate-data --scale large  --output data\large
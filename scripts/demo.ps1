<#
.SYNOPSIS
  Repeatable demo: upload the sample handbook and ask a few questions.

.DESCRIPTION
  Start Ollama, the database (docker compose up -d) and the app (.\mvnw.cmd spring-boot:run) first.
  Then run from the project folder:   .\scripts\demo.ps1
  If scripts are blocked:              powershell -ExecutionPolicy Bypass -File .\scripts\demo.ps1
#>
param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$Document = (Join-Path $PSScriptRoot "..\samples\acme-robotics-handbook.md")
)

$ErrorActionPreference = "Stop"

function Write-Step($text) { Write-Host "`n=== $text ===" -ForegroundColor Cyan }

Write-Step "1. Health check"
try {
    $health = Invoke-RestMethod "$BaseUrl/actuator/health"
    Write-Host "App status: $($health.status)" -ForegroundColor Green
} catch {
    Write-Host "The app is not reachable at $BaseUrl. Start it with: .\mvnw.cmd spring-boot:run" -ForegroundColor Red
    exit 1
}

Write-Step "2. Upload $(Split-Path $Document -Leaf)"
# curl.exe ships with Windows 10/11 and handles multipart uploads in every PowerShell version
$json = curl.exe -s -F "file=@$((Resolve-Path $Document).Path)" "$BaseUrl/api/documents"
$upload = $json | ConvertFrom-Json
$upload.chunksPerFile.PSObject.Properties | ForEach-Object { Write-Host "$($_.Name): $($_.Value) chunk(s) embedded and stored" }

$questions = @(
    "How many days of paid leave do employees get, and how many can be carried over?",
    "How much weight can the Kestrel X2 carry and how long does its battery last?",
    "What is the learning budget and what needs manager approval?"
)

$n = 3
foreach ($q in $questions) {
    Write-Step "$n. Question: $q"
    $body = @{ question = $q } | ConvertTo-Json
    $timer = [Diagnostics.Stopwatch]::StartNew()
    $answer = Invoke-RestMethod -Method Post "$BaseUrl/api/chat" -ContentType "application/json" -Body $body
    $timer.Stop()
    Write-Host $answer.answer -ForegroundColor Yellow
    foreach ($s in $answer.sources) {
        Write-Host ("  [{0}] {1}  (similarity {2:N2})" -f $s.index, $s.source, $s.score) -ForegroundColor DarkGray
    }
    Write-Host ("  answered in {0:N1} s" -f $timer.Elapsed.TotalSeconds) -ForegroundColor DarkGray
    $n++
}

Write-Host "`nDemo finished. Open $BaseUrl to try the web page." -ForegroundColor Green

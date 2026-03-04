param(
    [int]$Port = 8080
)

$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $scriptDir

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    throw "java command not found. Please install JDK first."
}
if (-not (Get-Command ssh -ErrorAction SilentlyContinue)) {
    throw "ssh command not found. Cannot create public tunnel."
}

Write-Host "Compiling Java server..."
javac -encoding UTF-8 --add-modules jdk.httpserver MahjongHexWebApp.java
if ($LASTEXITCODE -ne 0) {
    throw "Compilation failed. Please fix Java errors first."
}

$portBusy = $false
try {
    $portBusy = @(Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue).Count -gt 0
} catch {
    $portBusy = (netstat -ano | Select-String ":$Port\s+.*LISTENING").Count -gt 0
}
if ($portBusy) {
    throw "Port $Port is already in use. Stop the old server first."
}

$env:PORT = "$Port"
Write-Host "Starting local room server on http://localhost:$Port ..."
$javaProc = Start-Process -FilePath "java" `
    -ArgumentList "--add-modules", "jdk.httpserver", "MahjongHexWebApp" `
    -WorkingDirectory $scriptDir `
    -PassThru

$ready = $false
for ($i = 0; $i -lt 20; $i++) {
    if ($javaProc.HasExited) {
        break
    }
    try {
        $res = Invoke-WebRequest -Uri "http://localhost:$Port/" -UseBasicParsing -TimeoutSec 2
        if ($res.StatusCode -ge 200) {
            $ready = $true
            break
        }
    } catch {
        # waiting for startup
    }
    Start-Sleep -Milliseconds 500
}

if (-not $ready) {
    if ($javaProc.HasExited) {
        throw "Java server exited early. Check console logs and port usage."
    }
    throw "Java server did not become ready in time."
}

Write-Host ""
Write-Host "Local:  http://localhost:$Port"
Write-Host "Public: creating tunnel via localhost.run ..."
Write-Host "Tip: you will see an URL like https://xxxx.localhost.run"
Write-Host "Share that URL so others can join from different networks."
Write-Host "Stop: press Ctrl+C in this terminal."
Write-Host ""

try {
    ssh -o StrictHostKeyChecking=accept-new -R 80:localhost:$Port nokey@localhost.run
} finally {
    if (Get-Process -Id $javaProc.Id -ErrorAction SilentlyContinue) {
        Stop-Process -Id $javaProc.Id -Force
    }
}

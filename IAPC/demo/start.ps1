$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
function Wait-Service([string]$Name, [scriptblock]$Check) {
    $deadline = (Get-Date).AddMinutes(3)
    while ((Get-Date) -lt $deadline) {
        if (& $Check) { return }
        Start-Sleep -Seconds 2
    }
    throw "$Name did not become ready. Open it and run start.cmd again."
}
& java -version
if ($LASTEXITCODE -ne 0) { throw 'Install JDK 25 and configure JAVA_HOME.' }
function Test-DockerReady {
    # Windows PowerShell 5 treats some native stderr as terminating errors.
    $savedPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    & docker info *> $null
    $ready = $LASTEXITCODE -eq 0
    $ErrorActionPreference = $savedPreference
    return $ready
}
if (!(Test-DockerReady)) {
    $dockerDesktop = Join-Path $env:LOCALAPPDATA 'Programs\DockerDesktop\Docker Desktop.exe'
    if (!(Test-Path -LiteralPath $dockerDesktop)) { $dockerDesktop = 'C:\Program Files\Docker\Docker\Docker Desktop.exe' }
    Start-Process -FilePath $dockerDesktop -WindowStyle Hidden
    Wait-Service 'Docker Desktop' { Test-DockerReady }
}
try { $null = Invoke-RestMethod 'http://localhost:11434/api/tags' -TimeoutSec 3 }
catch {
    Start-Process -FilePath (Get-Command ollama).Source -ArgumentList 'serve' -WindowStyle Hidden
    Wait-Service 'Ollama' { try { $null = Invoke-RestMethod 'http://localhost:11434/api/tags' -TimeoutSec 3; return $true } catch { return $false } }
}
$models = (Invoke-RestMethod 'http://localhost:11434/api/tags').models.name
foreach ($model in @('gemma3:1b','nomic-embed-text')) {
    if ($models -notcontains $model -and $models -notcontains "${model}:latest") {
        Write-Host "Downloading Ollama model $model (first run only)..."
        $result = Invoke-RestMethod 'http://localhost:11434/api/pull' -Method Post -ContentType 'application/json' -Body (@{model=$model; stream=$false} | ConvertTo-Json) -TimeoutSec 1800
        if ($result.status -ne 'success') { throw "Model download failed: $result" }
    }
}
& docker compose -p university-demo -f docker/postgres/pgvector.yml up -d --wait
if ($LASTEXITCODE -ne 0) { throw 'Could not start PostgreSQL. Check Docker Desktop and port 5433.' }
Write-Host 'Starting application on http://localhost:8081. Press Ctrl+C to stop.'
& "$PSScriptRoot\mvnw.cmd" -B -ntp spring-boot:run
exit $LASTEXITCODE


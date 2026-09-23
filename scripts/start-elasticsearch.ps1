<#
Starts Elasticsearch via Docker Compose, ensuring WSL kernel setting is applied.

Usage (PowerShell):
  .\scripts\start-elasticsearch.ps1

This script will:
- verify Docker is available
- set vm.max_map_count inside WSL (persistent)
- run `docker compose up -d elasticsearch` (falls back to docker-compose)
- poll the ES HTTP endpoint and print status/logs on failure
#>

Write-Host "Starting Elasticsearch setup..."

# Check docker
try {
    & docker version | Out-Null
} catch {
    Write-Error "Docker does not appear to be available. Please start Docker Desktop and ensure CLI is on PATH."
    exit 1
}

Write-Host "Setting vm.max_map_count inside WSL (requires WSL installed)..."

# Use WSL to set the kernel setting persistently. Run as root in the default distro.
$wslCmd = "sysctl -w vm.max_map_count=262144 || true; grep -qF 'vm.max_map_count=262144' /etc/sysctl.conf || echo 'vm.max_map_count=262144' >> /etc/sysctl.conf"
try {
    wsl --user root -- bash -lc "$wslCmd" > $null 2>&1
    Write-Host "vm.max_map_count set (or already set) in WSL."
} catch {
    Write-Warning "Failed to set vm.max_map_count via WSL. If you are not using WSL, set vm.max_map_count on your Linux host manually."
}

Write-Host "Bringing up Elasticsearch container (docker compose)..."

# Move to repository root (one level up from scripts)
$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
Set-Location $repoRoot

# Try docker compose (v2) then fall back
try {
    Write-Host "Running: docker compose up -d elasticsearch"
    & docker compose up -d elasticsearch
} catch {
    Write-Host "docker compose failed, trying docker-compose..."
    try {
        & docker-compose up -d elasticsearch
    } catch {
        Write-Error "Both docker compose and docker-compose failed. Check Docker installation and compose availability."
        exit 1
    }
}

Write-Host "Waiting for Elasticsearch to become ready on http://localhost:9200 ..."

$ok = $false
for ($i=0; $i -lt 12; $i++) {
    Start-Sleep -Seconds 5
    try {
        $resp = & curl.exe -s http://localhost:9200/_cluster/health
        if ($resp -and $resp.Length -gt 0) {
            Write-Host "Elasticsearch responded:"
            Write-Host $resp
            $ok = $true
            break
        }
    } catch {
        # ignore and retry
    }
}

if (-not $ok) {
    Write-Error "Elasticsearch did not respond within timeout. Showing container logs (last 200 lines):"
    try {
        & docker compose logs --no-color --tail 200 elasticsearch
    } catch {
        & docker-compose logs --no-color --tail 200 elasticsearch
    }
    exit 1
}

Write-Host "Elasticsearch is up and responding on http://localhost:9200"

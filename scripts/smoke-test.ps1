$ErrorActionPreference = 'Stop'
Write-Host 'Checking Compose model...'
docker compose config | Out-Null
$services = docker compose config --services
foreach ($required in @('mysql','redis','zookeeper','rocketmq-namesrv','rocketmq-broker','tim-node-1','tim-node-2')) {
    if ($services -notcontains $required) { throw "Missing compose service: $required" }
}
docker compose up -d --build
try {
    foreach ($port in @(8081, 8082)) {
        $ok = $false
        for ($i = 0; $i -lt 30; $i++) {
            try { Invoke-RestMethod "http://localhost:$port/actuator/health" -TimeoutSec 2 | Out-Null; $ok = $true; break } catch { Start-Sleep -Seconds 2 }
        }
        if (-not $ok) { throw "TIM node health check failed on port $port" }
    }
    Write-Host 'TIM node health checks passed.'
}
finally { docker compose down }

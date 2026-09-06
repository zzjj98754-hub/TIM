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
    $message = @{ messageId = 'smoke-message-1'; clientMessageId = 'smoke-client-1'; fromUserId = 9101; toUserId = 9102; content = 'compose smoke'; createdAt = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() } | ConvertTo-Json
    Invoke-RestMethod 'http://localhost:8081/demo/messages' -Method Post -ContentType 'application/json' -Body $message | Out-Null
    Invoke-RestMethod 'http://localhost:8081/demo/messages' -Method Post -ContentType 'application/json' -Body $message | Out-Null
    $offline = @()
    for ($i = 0; $i -lt 15; $i++) {
        $offline = @(Invoke-RestMethod 'http://localhost:8082/demo/offline/9102?cursor=0&limit=20')
        if ($offline | Where-Object { $_.messageId -eq 'smoke-message-1' }) { break }
        Start-Sleep -Seconds 1
    }
    if ($offline.Count -ne 1 -or $offline[0].messageId -ne 'smoke-message-1') { throw 'Durable offline idempotency check failed' }
    Invoke-RestMethod 'http://localhost:8081/demo/groups/9100/members/9101' -Method Put | Out-Null
    Invoke-RestMethod 'http://localhost:8081/demo/groups/9100/members/9102' -Method Put | Out-Null
    $group = @{ fromUserId = 9101; content = 'group smoke' } | ConvertTo-Json
    Invoke-RestMethod 'http://localhost:8081/demo/groups/9100/messages' -Method Post -ContentType 'application/json' -Body $group | Out-Null
    $groupMessages = @(Invoke-RestMethod 'http://localhost:8082/demo/groups/9100/messages/9102?cursor=0&limit=20')
    if ($groupMessages.Count -lt 1) { throw 'Cross-node durable group read check failed' }
    $restartMessage = @{ messageId = 'smoke-restart-message-1'; clientMessageId = 'smoke-restart-client-1'; fromUserId = 9103; toUserId = 9104; content = 'restart smoke'; createdAt = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() } | ConvertTo-Json
    Invoke-RestMethod 'http://localhost:8081/demo/messages' -Method Post -ContentType 'application/json' -Body $restartMessage | Out-Null
    docker compose stop tim-node-1 | Out-Null
    docker compose up -d tim-node-1 | Out-Null
    $restarted = $false
    for ($i = 0; $i -lt 30; $i++) {
        try { Invoke-RestMethod 'http://localhost:8081/actuator/health' -TimeoutSec 2 | Out-Null; $restarted = $true; break } catch { Start-Sleep -Seconds 2 }
    }
    if (-not $restarted) { throw 'TIM node 1 restart health check failed' }
    $restartOffline = @(Invoke-RestMethod 'http://localhost:8082/demo/offline/9104?cursor=0&limit=20')
    if (-not ($restartOffline | Where-Object { $_.messageId -eq 'smoke-restart-message-1' })) { throw 'Durable message was not readable after node restart' }
    Write-Host 'TIM node health, durable offline idempotency, cross-node group persistence, and restart durability checks passed.'
}
finally { docker compose down }

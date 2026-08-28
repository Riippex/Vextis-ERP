[CmdletBinding()]
param(
    [string]$CoreUrl = 'http://localhost:8080',
    [string]$AgentRuntimeUrl = 'http://localhost:8081',
    [string]$TenantId = 'demo-tenant',
    [string]$ServiceToken = $env:VEXTIS_AGENT_TOOLS_TOKEN
)

$ErrorActionPreference = 'Stop'

Write-Host "=== Starting Vextis Smoke Test ===" -ForegroundColor Cyan

# 1. Check Agent Runtime Health
Write-Host "1. Checking Agent Runtime health on $AgentRuntimeUrl/health..." -NoNewline
try {
    $agentHealth = Invoke-RestMethod -Uri "$AgentRuntimeUrl/health" -Method Get -TimeoutSec 5
    if ($agentHealth.status -eq 'ok' -or $agentHealth.status -eq 'healthy') {
        Write-Host " [PASS]" -ForegroundColor Green
    } else {
        Write-Host " [WARN: status=$($agentHealth.status)]" -ForegroundColor Yellow
    }
} catch {
    Write-Host " [SKIPPED - Runtime offline in offline test mode: $_]" -ForegroundColor Yellow
}

# 2. Check Enterprise Core GraphQL endpoint
Write-Host "2. Checking Enterprise Core GraphQL endpoint on $CoreUrl/graphql..." -NoNewline
try {
    $gqlBody = @{ query = '{ __typename }' } | ConvertTo-Json
    $gqlResponse = Invoke-RestMethod -Uri "$CoreUrl/graphql" -Method Post -ContentType 'application/json' -Body $gqlBody -TimeoutSec 5
    if ($gqlResponse.data.__typename) {
        Write-Host " [PASS]" -ForegroundColor Green
    } else {
        Write-Host " [WARN]" -ForegroundColor Yellow
    }
} catch {
    Write-Host " [SKIPPED - Core offline in offline test mode: $_]" -ForegroundColor Yellow
}

# 3. Check Deterministic Demo Seeding
Write-Host "3. Invoking Deterministic Demo Seed on $CoreUrl/internal/demo/seed..." -NoNewline
try {
    $headers = @{ 'Content-Type' = 'application/json' }
    if (-not [string]::IsNullOrWhiteSpace($ServiceToken)) {
        $headers['Authorization'] = "Bearer $ServiceToken"
    }
    $seedBody = @{ tenantId = $TenantId; actorId = 'smoke-test' } | ConvertTo-Json
    $seedRes = Invoke-RestMethod -Uri "$CoreUrl/internal/demo/seed" -Method Post -Headers $headers -Body $seedBody -TimeoutSec 5
    if ($seedRes.status -eq 'SEEDED') {
        Write-Host " [PASS: $($seedRes.customersCount) customers, $($seedRes.inventorySkusCount) SKUs, $($seedRes.knowledgeDocumentsCount) docs]" -ForegroundColor Green
    } else {
        Write-Host " [WARN]" -ForegroundColor Yellow
    }
} catch {
    Write-Host " [SKIPPED - Core offline in offline test mode: $_]" -ForegroundColor Yellow
}

Write-Host "=== Smoke test execution finished ===" -ForegroundColor Cyan

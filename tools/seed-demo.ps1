[CmdletBinding()]
param(
    [string]$CoreUrl = 'http://localhost:8080',
    [string]$TenantId = 'demo-tenant',
    [string]$ActorId = 'demo-seeder',
    [string]$ServiceToken = $env:VEXTIS_AGENT_TOOLS_TOKEN
)

$ErrorActionPreference = 'Stop'

Write-Host "Seeding deterministic demo data on $CoreUrl for tenant '$TenantId'..." -ForegroundColor Cyan

$headers = @{
    'Content-Type' = 'application/json'
}

if (-not [string]::IsNullOrWhiteSpace($ServiceToken)) {
    $headers['Authorization'] = "Bearer $ServiceToken"
}

$body = @{
    tenantId = $TenantId
    actorId = $ActorId
} | ConvertTo-Json

try {
    $response = Invoke-RestMethod -Uri "$CoreUrl/internal/demo/seed" `
        -Method Post `
        -Headers $headers `
        -Body $body `
        -TimeoutSec 15

    Write-Host "Successfully seeded demo data:" -ForegroundColor Green
    Write-Host "  Tenant:               $($response.tenantId)"
    Write-Host "  Customers:            $($response.customersCount)"
    Write-Host "  Credit Profiles:      $($response.creditProfilesCount)"
    Write-Host "  Inventory SKUs:       $($response.inventorySkusCount)"
    Write-Host "  Knowledge Documents:  $($response.knowledgeDocumentsCount)"
}
catch {
    Write-Error "Failed to seed demo data: $_"
    exit 1
}

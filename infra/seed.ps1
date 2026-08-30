[CmdletBinding()]
param(
    [string]$CoreUrl = 'http://localhost:8080',
    [string]$TenantId = 'demo-tenant',
    [string]$ActorId = 'demo-seeder',
    [string]$ServiceToken = $env:VEXTIS_AGENT_TOOLS_TOKEN
)

$ErrorActionPreference = 'Stop'

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$toolsSeedScript = Join-Path (Split-Path -Parent $scriptDir) 'tools/seed-demo.ps1'

if (Test-Path $toolsSeedScript) {
    & $toolsSeedScript -CoreUrl $CoreUrl -TenantId $TenantId -ActorId $ActorId -ServiceToken $ServiceToken
} else {
    Write-Error "Seed script not found at $toolsSeedScript"
    exit 1
}

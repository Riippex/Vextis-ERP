<#
.SYNOPSIS
    Verifies that a running Vextis deployment answers on its Core and Agent
    Runtime entry points and can reset its demo tenant.

.DESCRIPTION
    Exits non-zero when any check fails, so CI, a deployment gate or an operator
    running it by hand learns that the deployment is down. Unreachable services
    are failures, not skips.

    Against Cloud Run, the private Enterprise Core requires an IAM identity
    token. Cloud Run consumes X-Serverless-Authorization for that and forwards
    Authorization to the application, which is where the agent-tools service
    token goes, so both travel on the same request.

.PARAMETER Offline
    Explicitly opt out of the network checks, for a machine with nothing
    running. Without it, an unreachable service fails the run.

.PARAMETER IdentityToken
    Google-signed identity token for a private Cloud Run service. Use
    -UseGcloudIdentityToken to mint one from the active gcloud credentials.

.EXAMPLE
    ./tools/smoke-test.ps1

.EXAMPLE
    ./tools/smoke-test.ps1 -CoreUrl https://vextis-enterprise-core-xxxx.run.app `
        -AgentRuntimeUrl https://vextis-agent-runtime-xxxx.run.app `
        -UseGcloudIdentityToken -ServiceToken $env:VEXTIS_AGENT_TOOLS_TOKEN
#>
[CmdletBinding()]
param(
    [string]$CoreUrl = 'http://localhost:8080',
    [string]$AgentRuntimeUrl = 'http://localhost:8081',
    [string]$TenantId = 'demo-tenant',
    [string]$ServiceToken = $env:VEXTIS_AGENT_TOOLS_TOKEN,
    [string]$IdentityToken = $env:VEXTIS_SMOKE_IDENTITY_TOKEN,
    [switch]$UseGcloudIdentityToken,
    [switch]$SkipDemoReset,
    [switch]$Offline,
    [int]$TimeoutSec = 10
)

$ErrorActionPreference = 'Stop'

$script:Failures = @()
$script:Skipped = @()

function Write-Pass([string]$Name, [string]$Detail = '') {
    $suffix = if ($Detail) { " ($Detail)" } else { '' }
    Write-Host "  [PASS] $Name$suffix" -ForegroundColor Green
}

function Write-Fail([string]$Name, [string]$Detail) {
    $script:Failures += "$Name`: $Detail"
    Write-Host "  [FAIL] $Name - $Detail" -ForegroundColor Red
}

function Write-Skip([string]$Name, [string]$Reason) {
    $script:Skipped += $Name
    Write-Host "  [SKIP] $Name - $Reason" -ForegroundColor Yellow
}

function Get-IdentityToken([string]$Audience) {
    if ($IdentityToken) {
        return $IdentityToken
    }
    if (-not $UseGcloudIdentityToken) {
        return $null
    }
    $token = & gcloud auth print-identity-token "--audiences=$Audience" 2>$null
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($token)) {
        throw "gcloud could not mint an identity token for $Audience"
    }
    return ($token | Select-Object -First 1).Trim()
}

function New-CoreHeaders([string]$BaseUrl, [switch]$WithServiceToken) {
    $headers = @{}
    if ($WithServiceToken -and -not [string]::IsNullOrWhiteSpace($ServiceToken)) {
        $headers['Authorization'] = "Bearer $ServiceToken"
    }
    $identity = Get-IdentityToken -Audience $BaseUrl
    if ($identity) {
        # Cloud Run strips this one for its own IAM check and leaves
        # Authorization for the application.
        $headers['X-Serverless-Authorization'] = "Bearer $identity"
        if (-not $headers.ContainsKey('Authorization')) {
            $headers['Authorization'] = "Bearer $identity"
        }
    }
    return $headers
}

function Invoke-Check {
    param(
        [string]$Name,
        [scriptblock]$Action,
        [scriptblock]$Assert
    )

    if ($Offline) {
        Write-Skip $Name 'offline mode requested'
        return
    }

    try {
        $response = & $Action
    } catch {
        Write-Fail $Name "request failed: $($_.Exception.Message)"
        return
    }

    try {
        $detail = & $Assert $response
    } catch {
        Write-Fail $Name $_.Exception.Message
        return
    }

    Write-Pass $Name $detail
}

Write-Host '=== Vextis smoke test ===' -ForegroundColor Cyan
Write-Host "  Core:          $CoreUrl"
Write-Host "  Agent Runtime: $AgentRuntimeUrl"
Write-Host "  Tenant:        $TenantId"
if ($Offline) {
    Write-Host '  Mode:          OFFLINE (checks are skipped on request)' -ForegroundColor Yellow
}
Write-Host ''

Invoke-Check -Name 'Agent Runtime health' -Action {
    $headers = New-CoreHeaders -BaseUrl $AgentRuntimeUrl
    Invoke-RestMethod -Uri "$AgentRuntimeUrl/health" -Method Get -Headers $headers -TimeoutSec $TimeoutSec
} -Assert {
    param($response)
    $status = "$($response.status)"
    if ($status -notmatch '^(?i)(UP|ok|healthy)$') {
        throw "unexpected status '$status'"
    }
    "status=$status"
}

Invoke-Check -Name 'Enterprise Core GraphQL' -Action {
    $headers = New-CoreHeaders -BaseUrl $CoreUrl
    $headers['Content-Type'] = 'application/json'
    $body = @{ query = '{ __typename }' } | ConvertTo-Json -Compress
    Invoke-RestMethod -Uri "$CoreUrl/graphql" -Method Post -Headers $headers -Body $body -TimeoutSec $TimeoutSec
} -Assert {
    param($response)
    if (-not $response.data.__typename) {
        throw 'response carried no data.__typename'
    }
    "typename=$($response.data.__typename)"
}

Invoke-Check -Name 'Demo seed' -Action {
    $headers = New-CoreHeaders -BaseUrl $CoreUrl -WithServiceToken
    $headers['Content-Type'] = 'application/json'
    $body = @{ tenantId = $TenantId; actorId = 'smoke-test' } | ConvertTo-Json -Compress
    Invoke-RestMethod -Uri "$CoreUrl/internal/demo/seed" -Method Post -Headers $headers -Body $body -TimeoutSec $TimeoutSec
} -Assert {
    param($response)
    if ($response.status -ne 'SEEDED') {
        throw "unexpected status '$($response.status)'"
    }
    "$($response.customersCount) customers, $($response.inventorySkusCount) SKUs, $($response.knowledgeDocumentsCount) docs"
}

if (-not $SkipDemoReset) {
    Invoke-Check -Name 'Demo reset' -Action {
        $headers = New-CoreHeaders -BaseUrl $CoreUrl -WithServiceToken
        $headers['Content-Type'] = 'application/json'
        $body = @{ tenantId = $TenantId; actorId = 'smoke-test' } | ConvertTo-Json -Compress
        Invoke-RestMethod -Uri "$CoreUrl/internal/demo/reset" -Method Post -Headers $headers -Body $body -TimeoutSec $TimeoutSec
    } -Assert {
        param($response)
        if ($response.status -ne 'RESET') {
            throw "unexpected status '$($response.status)'; /internal/demo/reset must purge, not re-seed"
        }
        "purged $($response.purgedRowsTotal) rows, reseeded $($response.customersCount) customers"
    }
}

Write-Host ''
if ($script:Failures.Count -gt 0) {
    Write-Host "=== Smoke test FAILED ($($script:Failures.Count) check(s)) ===" -ForegroundColor Red
    foreach ($failure in $script:Failures) {
        Write-Host "  - $failure" -ForegroundColor Red
    }
    exit 1
}

if ($Offline) {
    Write-Host "=== Smoke test skipped $($script:Skipped.Count) check(s) in offline mode ===" -ForegroundColor Yellow
    exit 0
}

Write-Host '=== Smoke test PASSED ===' -ForegroundColor Green
exit 0

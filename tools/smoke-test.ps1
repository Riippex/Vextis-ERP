<#
.SYNOPSIS
    Verifies that a running Vextis deployment answers on each of its exposures
    and can reset its demo tenant.

.DESCRIPTION
    Exits non-zero when any check fails, so CI, a deployment gate or an operator
    running it by hand learns that the deployment is down. Unreachable services
    are failures, not skips.

    Enterprise Core runs behind two different security postures and the checks
    differ accordingly:

      INTERNAL (vextis-enterprise-core)
        /internal/** is reachable, /graphql is denied. Probing /graphql here was
        the old script's mistake: it returns 403 by design, so the smoke test
        could never pass against the private service in GCP.

      PUBLIC (vextis-enterprise-core-public)
        /graphql is reachable and Firebase-authenticated, /internal/** is denied.
        Both facts are asserted positively: an unauthenticated /graphql that does
        not return 401, or an /internal/** that answers at all, is a broken
        exposure boundary and fails the run.

      LOCAL (tools/dev.ps1)
        One service permits everything. Without -PublicCoreUrl the script assumes
        this and probes /graphql on -CoreUrl directly.

    Against Cloud Run, the private services also need an IAM identity token.
    Cloud Run consumes X-Serverless-Authorization for that and forwards
    Authorization to the application, which is where the demo administration
    credential goes, so both travel on the same request.

.PARAMETER PublicCoreUrl
    Public, Firebase-authenticated Enterprise Core. Supplying it switches the
    script into GCP mode and enables the exposure-boundary assertions.

.PARAMETER AdminToken
    Credential for /internal/demo/**. This is vextis.demo.admin-token, not the
    agent-tools token; the demo reset is destructive and has its own secret.

.PARAMETER Offline
    Explicitly opt out of the network checks, for a machine with nothing
    running. Without it, an unreachable service fails the run.

.EXAMPLE
    ./tools/smoke-test.ps1

.EXAMPLE
    ./tools/smoke-test.ps1 -CoreUrl https://vextis-enterprise-core-xxxx.run.app `
        -PublicCoreUrl https://vextis-enterprise-core-public-xxxx.run.app `
        -AgentRuntimeUrl https://vextis-agent-runtime-xxxx.run.app `
        -UseGcloudIdentityToken -AdminToken $env:VEXTIS_DEMO_ADMIN_TOKEN
#>
[CmdletBinding()]
param(
    [string]$CoreUrl = 'http://localhost:8080',
    [string]$PublicCoreUrl = '',
    [string]$AgentRuntimeUrl = 'http://localhost:8081',
    [string]$TenantId = 'demo-tenant',
    [string]$AdminToken = $env:VEXTIS_DEMO_ADMIN_TOKEN,
    [string]$IdentityToken = $env:VEXTIS_SMOKE_IDENTITY_TOKEN,
    [switch]$UseGcloudIdentityToken,
    [switch]$SkipDemoReset,
    [switch]$Offline,
    [int]$TimeoutSec = 10
)

$ErrorActionPreference = 'Stop'

$script:Failures = @()
$script:Skipped = @()
$script:GcpMode = -not [string]::IsNullOrWhiteSpace($PublicCoreUrl)

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

function New-Headers([string]$BaseUrl, [switch]$WithAdminToken, [switch]$Anonymous) {
    $headers = @{}
    if ($WithAdminToken -and -not [string]::IsNullOrWhiteSpace($AdminToken)) {
        $headers['Authorization'] = "Bearer $AdminToken"
    }
    if ($Anonymous) {
        # Deliberately no application credential: the point is what the exposure
        # does with a caller who has none.
        return $headers
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

<#
    One request, reporting the status code whether the response was a success or
    an error. Invoke-RestMethod throws on 4xx, and several checks here assert a
    4xx is exactly what came back.
#>
function Invoke-Status {
    param(
        [string]$Uri,
        [string]$Method = 'Get',
        [hashtable]$Headers = @{},
        [string]$Body = $null
    )

    $arguments = @{
        Uri             = $Uri
        Method          = $Method
        Headers         = $Headers
        TimeoutSec      = $TimeoutSec
        UseBasicParsing = $true
    }
    if ($Body) {
        $arguments['Body'] = $Body
        $arguments['ContentType'] = 'application/json'
    }

    try {
        $response = Invoke-WebRequest @arguments
        return [pscustomobject]@{ Status = [int]$response.StatusCode; Content = $response.Content }
    } catch {
        # Windows PowerShell raises WebException and PowerShell 7 raises
        # HttpResponseException; the latter type does not exist on 5.1, so this
        # cannot be a typed catch. A connection failure has no Response and is
        # rethrown as the failure it is.
        $response = $_.Exception.PSObject.Properties['Response']
        if ($null -eq $response -or $null -eq $response.Value) {
            throw
        }
        return [pscustomobject]@{ Status = [int]$response.Value.StatusCode; Content = '' }
    }
}

function Invoke-Check {
    param(
        [string]$Name,
        [scriptblock]$Action
    )

    if ($Offline) {
        Write-Skip $Name 'offline mode requested'
        return
    }

    try {
        $detail = & $Action
    } catch {
        Write-Fail $Name "request failed: $($_.Exception.Message)"
        return
    }

    Write-Pass $Name $detail
}

function New-DemoBody {
    return (@{ tenantId = $TenantId; actorId = 'smoke-test' } | ConvertTo-Json -Compress)
}

Write-Host '=== Vextis smoke test ===' -ForegroundColor Cyan
Write-Host "  Core (internal): $CoreUrl"
if ($script:GcpMode) {
    Write-Host "  Core (public):   $PublicCoreUrl"
} else {
    Write-Host '  Core (public):   not supplied; assuming a single LOCAL exposure'
}
Write-Host "  Agent Runtime:   $AgentRuntimeUrl"
Write-Host "  Tenant:          $TenantId"
if ($Offline) {
    Write-Host '  Mode:            OFFLINE (checks are skipped on request)' -ForegroundColor Yellow
}
Write-Host ''

# --- Agent Runtime ----------------------------------------------------------

Invoke-Check -Name 'Agent Runtime health' -Action {
    $result = Invoke-Status -Uri "$AgentRuntimeUrl/health" -Headers (New-Headers -BaseUrl $AgentRuntimeUrl)
    if ($result.Status -ne 200) {
        throw "expected 200, got $($result.Status)"
    }
    $status = ($result.Content | ConvertFrom-Json).status
    if ("$status" -notmatch '^(?i)(UP|ok|healthy)$') {
        throw "unexpected status '$status'"
    }
    "status=$status"
}

# --- Internal Enterprise Core ----------------------------------------------

Invoke-Check -Name 'Enterprise Core (internal) health' -Action {
    # /actuator/health is the only unauthenticated path under INTERNAL exposure.
    $result = Invoke-Status -Uri "$CoreUrl/actuator/health" -Headers (New-Headers -BaseUrl $CoreUrl)
    if ($result.Status -ne 200) {
        throw "expected 200, got $($result.Status)"
    }
    "status=$((($result.Content | ConvertFrom-Json).status))"
}

if ($script:GcpMode) {
    Invoke-Check -Name 'Private Core denies /graphql' -Action {
        # The exposure boundary this deployment relies on: /graphql is Core's
        # public read surface, and the private service must never answer it,
        # authenticated or not. A 200 here would mean the private service is
        # accidentally serving the same API the public one is meant to gate
        # behind Firebase auth.
        $body = @{ query = '{ __typename }' } | ConvertTo-Json -Compress
        $result = Invoke-Status -Uri "$CoreUrl/graphql" -Method Post -Body $body `
            -Headers (New-Headers -BaseUrl $CoreUrl)
        if ($result.Status -ne 403) {
            throw "expected 403, got $($result.Status); the private Core must not serve /graphql"
        }
        'private /graphql denied with 403'
    }
} else {
    Invoke-Check -Name 'Enterprise Core GraphQL (LOCAL exposure)' -Action {
        $body = @{ query = '{ __typename }' } | ConvertTo-Json -Compress
        $result = Invoke-Status -Uri "$CoreUrl/graphql" -Method Post -Body $body `
            -Headers (New-Headers -BaseUrl $CoreUrl)
        if ($result.Status -ne 200) {
            throw "expected 200, got $($result.Status)"
        }
        $typename = ($result.Content | ConvertFrom-Json).data.__typename
        if (-not $typename) {
            throw 'response carried no data.__typename'
        }
        "typename=$typename"
    }
}

Invoke-Check -Name 'Demo seed' -Action {
    $result = Invoke-Status -Uri "$CoreUrl/internal/demo/seed" -Method Post -Body (New-DemoBody) `
        -Headers (New-Headers -BaseUrl $CoreUrl -WithAdminToken)
    if ($result.Status -ne 200) {
        throw "expected 200, got $($result.Status); /internal/demo/** needs the demo admin credential"
    }
    $seed = $result.Content | ConvertFrom-Json
    if ($seed.status -ne 'SEEDED') {
        throw "unexpected status '$($seed.status)'"
    }
    "$($seed.customersCount) customers, $($seed.inventorySkusCount) SKUs, $($seed.knowledgeDocumentsCount) docs"
}

if (-not $SkipDemoReset) {
    Invoke-Check -Name 'Demo reset' -Action {
        $result = Invoke-Status -Uri "$CoreUrl/internal/demo/reset" -Method Post -Body (New-DemoBody) `
            -Headers (New-Headers -BaseUrl $CoreUrl -WithAdminToken)
        if ($result.Status -ne 200) {
            throw "expected 200, got $($result.Status)"
        }
        $reset = $result.Content | ConvertFrom-Json
        if ($reset.status -ne 'RESET') {
            throw "unexpected status '$($reset.status)'; /internal/demo/reset must purge, not re-seed"
        }
        "purged $($reset.purgedRowsTotal) rows, reseeded $($reset.customersCount) customers"
    }
}

Invoke-Check -Name 'Demo administration refuses a foreign tenant' -Action {
    $body = @{ tenantId = 'smoke-test-foreign-tenant'; actorId = 'smoke-test' } | ConvertTo-Json -Compress
    $result = Invoke-Status -Uri "$CoreUrl/internal/demo/reset" -Method Post -Body $body `
        -Headers (New-Headers -BaseUrl $CoreUrl -WithAdminToken)
    if ($result.Status -ne 403) {
        throw "expected 403, got $($result.Status); the demo credential must not reach another tenant"
    }
    'foreign tenant refused with 403'
}

# --- Public Enterprise Core -------------------------------------------------

if ($script:GcpMode) {
    Invoke-Check -Name 'Public Core requires authentication on /graphql' -Action {
        $body = @{ query = '{ __typename }' } | ConvertTo-Json -Compress
        $result = Invoke-Status -Uri "$PublicCoreUrl/graphql" -Method Post -Body $body `
            -Headers (New-Headers -BaseUrl $PublicCoreUrl -Anonymous)
        if ($result.Status -ne 401) {
            throw "expected 401 for an unauthenticated caller, got $($result.Status)"
        }
        'anonymous GraphQL rejected with 401'
    }

    Invoke-Check -Name 'Public Core does not expose /internal/**' -Action {
        $result = Invoke-Status -Uri "$PublicCoreUrl/internal/demo/reset" -Method Post -Body (New-DemoBody) `
            -Headers (New-Headers -BaseUrl $PublicCoreUrl -WithAdminToken)
        if ($result.Status -ne 403) {
            throw "expected 403, got $($result.Status); the internal surface must not be publicly reachable"
        }
        'internal surface denied with 403'
    }

    Invoke-Check -Name 'Public Core health probe is reachable' -Action {
        $result = Invoke-Status -Uri "$PublicCoreUrl/actuator/health" `
            -Headers (New-Headers -BaseUrl $PublicCoreUrl -Anonymous)
        if ($result.Status -ne 200) {
            throw "expected 200, got $($result.Status)"
        }
        "status=$((($result.Content | ConvertFrom-Json).status))"
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

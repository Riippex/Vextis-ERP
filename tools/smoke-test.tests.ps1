<#
.SYNOPSIS
    Exercises the exit-code contract of tools/smoke-test.ps1.

.DESCRIPTION
    The smoke test used to catch every connection error and print SKIPPED, so it
    exited 0 with Core and Agent Runtime both down and nothing in CI or a
    deployment gate could tell. It also probed /graphql on whatever Core URL it
    was given, which under INTERNAL exposure is denied by design, so it could
    never pass against the private service in GCP.

    These cases pin the contract: down means non-zero, healthy means zero,
    skipping only happens when -Offline is asked for explicitly, and a broken
    INTERNAL/PUBLIC exposure boundary fails the run.

    Runs throwaway HttpListeners on loopback ports to stand in for the services;
    no Pester or other module is required.
#>
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$smokeTest = Join-Path $PSScriptRoot 'smoke-test.ps1'
if (-not (Test-Path $smokeTest)) {
    throw "smoke-test.ps1 not found next to this script at $smokeTest"
}

$powershell = (Get-Process -Id $PID).Path
$script:Failed = 0

function Assert-ExitCode {
    param(
        [string]$Name,
        [int]$Expected,
        [string[]]$Arguments
    )

    & $powershell -NoProfile -NonInteractive -File $smokeTest @Arguments *> $null
    $actual = $LASTEXITCODE

    if ($actual -eq $Expected) {
        Write-Host "  [PASS] $Name (exit $actual)" -ForegroundColor Green
        return
    }

    $script:Failed++
    Write-Host "  [FAIL] $Name - expected exit $Expected, got $actual" -ForegroundColor Red
}

function Get-FreeLoopbackPort {
    $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, 0)
    $listener.Start()
    try {
        return $listener.LocalEndpoint.Port
    } finally {
        $listener.Stop()
    }
}

$stubServer = {
    param([string]$Prefix, [hashtable]$Routes)

    $listener = [System.Net.HttpListener]::new()
    $listener.Prefixes.Add($Prefix)
    $listener.Start()
    try {
        while ($listener.IsListening) {
            try {
                $context = $listener.GetContext()
            } catch {
                break
            }

            $path = $context.Request.Url.AbsolutePath
            if ($path -eq '/__shutdown') {
                $context.Response.StatusCode = 200
                $context.Response.Close()
                break
            }

            $requestBody = ''
            if ($context.Request.HasEntityBody) {
                $reader = [System.IO.StreamReader]::new($context.Request.InputStream)
                $requestBody = $reader.ReadToEnd()
                $reader.Close()
            }
            $authorization = $context.Request.Headers['Authorization']

            $status = 404
            $payload = '{"error":"not found"}'
            foreach ($rule in @($Routes[$path])) {
                if ($null -eq $rule) { continue }
                if ($rule.ContainsKey('BodyContains') -and $requestBody -notlike "*$($rule.BodyContains)*") { continue }
                if ($rule.ContainsKey('RequiresAuth') -and $rule.RequiresAuth -and -not $authorization) { continue }
                if ($rule.ContainsKey('WithoutAuth') -and $rule.WithoutAuth -and $authorization) { continue }
                $status = [int]$rule.Status
                $payload = [string]$rule.Body
                break
            }

            $bytes = [System.Text.Encoding]::UTF8.GetBytes($payload)
            $context.Response.StatusCode = $status
            $context.Response.ContentType = 'application/json'
            $context.Response.ContentLength64 = $bytes.Length
            $context.Response.OutputStream.Write($bytes, 0, $bytes.Length)
            $context.Response.OutputStream.Close()
        }
    } finally {
        $listener.Close()
    }
}

function Start-StubService {
    param([hashtable]$Routes, [int]$Port)

    # A dedicated runspace, not a Task: a scriptblock delegate would queue
    # against this runspace, which is busy waiting on the smoke test, and the
    # stub would never answer.
    $runspace = [runspacefactory]::CreateRunspace()
    $runspace.Open()
    $shell = [powershell]::Create()
    $shell.Runspace = $runspace
    [void]$shell.AddScript($stubServer).AddArgument("http://localhost:$Port/").AddArgument($Routes)
    $handle = $shell.BeginInvoke()

    $stub = [pscustomobject]@{
        Shell    = $shell
        Runspace = $runspace
        Handle   = $handle
        Port     = $Port
        Url      = "http://localhost:$Port"
    }

    $deadline = (Get-Date).AddSeconds(10)
    while ((Get-Date) -lt $deadline) {
        $probe = [System.Net.Sockets.TcpClient]::new()
        try {
            $probe.Connect('localhost', $Port)
            $probe.Close()
            return $stub
        } catch {
            $probe.Dispose()
        }
    }

    Stop-StubService $stub
    throw "Stub service on port $Port never became reachable"
}

function Stop-StubService($stub) {
    if ($null -eq $stub) {
        return
    }
    try {
        Invoke-WebRequest -Uri "$($stub.Url)/__shutdown" -TimeoutSec 3 -UseBasicParsing | Out-Null
    } catch {
        # The listener may already be gone; the runspace teardown below is enough.
    }
    try {
        $stub.Shell.EndInvoke($stub.Handle) | Out-Null
    } catch {
        # Nothing actionable if the stub already unwound.
    }
    $stub.Shell.Dispose()
    $stub.Runspace.Dispose()
}

# Canonical stub responses -----------------------------------------------------

$healthOk = @{ Status = 200; Body = '{"status":"UP","service":"agent-runtime"}' }
$graphqlOk = @{ Status = 200; Body = '{"data":{"__typename":"Query"}}' }
$seededOk = @{ Status = 200; Body = '{"status":"SEEDED","tenantId":"demo-tenant","customersCount":2,"creditProfilesCount":2,"inventorySkusCount":3,"knowledgeDocumentsCount":2}' }
$resetOk = @{ Status = 200; Body = '{"status":"RESET","tenantId":"demo-tenant","purgedRowsTotal":7,"purgedRowsByArea":{"knowledge":2},"customersCount":2,"creditProfilesCount":2,"inventorySkusCount":3,"knowledgeDocumentsCount":2}' }
$foreignTenantDenied = @{ Status = 403; BodyContains = 'smoke-test-foreign-tenant'; Body = '{"error":"forbidden"}' }

# A correct INTERNAL exposure: /internal/** reachable, /graphql denied.
function New-InternalCoreRoutes {
    param($ResetRule = $resetOk)
    return @{
        '/actuator/health'     = @{ Status = 200; Body = '{"status":"UP"}' }
        '/health'              = $healthOk
        '/graphql'             = @{ Status = 403; Body = '{"error":"denied"}' }
        '/internal/demo/seed'  = @($foreignTenantDenied, $seededOk)
        '/internal/demo/reset' = @($foreignTenantDenied, $ResetRule)
    }
}

# A correct PUBLIC exposure: Firebase-authenticated /graphql, /internal/** denied.
function New-PublicCoreRoutes {
    return @{
        '/actuator/health'     = @{ Status = 200; Body = '{"status":"UP"}' }
        '/graphql'             = @{ Status = 401; Body = '{"error":"unauthenticated"}' }
        '/internal/demo/reset' = @{ Status = 403; Body = '{"error":"denied"}' }
        '/internal/demo/seed'  = @{ Status = 403; Body = '{"error":"denied"}' }
    }
}

Write-Host '=== smoke-test.ps1 exit-code contract ===' -ForegroundColor Cyan

# --- availability -----------------------------------------------------------

$deadPort = Get-FreeLoopbackPort
$deadUrl = "http://localhost:$deadPort"

Assert-ExitCode -Name 'both services down exits non-zero' -Expected 1 -Arguments @(
    '-CoreUrl', $deadUrl, '-AgentRuntimeUrl', $deadUrl, '-TimeoutSec', '2'
)

Assert-ExitCode -Name 'explicit offline mode exits zero' -Expected 0 -Arguments @(
    '-CoreUrl', $deadUrl, '-AgentRuntimeUrl', $deadUrl, '-TimeoutSec', '2', '-Offline'
)

# --- LOCAL exposure ---------------------------------------------------------

$localStub = $null
try {
    $localStub = Start-StubService -Port (Get-FreeLoopbackPort) -Routes @{
        '/health'              = $healthOk
        '/actuator/health'     = @{ Status = 200; Body = '{"status":"UP"}' }
        '/graphql'             = $graphqlOk
        '/internal/demo/seed'  = @($foreignTenantDenied, $seededOk)
        '/internal/demo/reset' = @($foreignTenantDenied, $resetOk)
    }

    Assert-ExitCode -Name 'healthy LOCAL deployment exits zero' -Expected 0 -Arguments @(
        '-CoreUrl', $localStub.Url, '-AgentRuntimeUrl', $localStub.Url, '-TimeoutSec', '5'
    )
} finally {
    Stop-StubService $localStub
}

$brokenGraphql = $null
try {
    $brokenGraphql = Start-StubService -Port (Get-FreeLoopbackPort) -Routes @{
        '/health'          = $healthOk
        '/actuator/health' = @{ Status = 200; Body = '{"status":"UP"}' }
        '/graphql'         = @{ Status = 500; Body = '{"error":"boom"}' }
    }

    Assert-ExitCode -Name 'LOCAL Core erroring exits non-zero' -Expected 1 -Arguments @(
        '-CoreUrl', $brokenGraphql.Url, '-AgentRuntimeUrl', $brokenGraphql.Url, '-TimeoutSec', '5'
    )
} finally {
    Stop-StubService $brokenGraphql
}

$reseedOnly = $null
try {
    # A /internal/demo/reset that only re-seeds reports SEEDED. That is the
    # regression this suite exists to keep out.
    $reseedOnly = Start-StubService -Port (Get-FreeLoopbackPort) -Routes @{
        '/health'              = $healthOk
        '/actuator/health'     = @{ Status = 200; Body = '{"status":"UP"}' }
        '/graphql'             = $graphqlOk
        '/internal/demo/seed'  = @($foreignTenantDenied, $seededOk)
        '/internal/demo/reset' = @($foreignTenantDenied, $seededOk)
    }

    Assert-ExitCode -Name 'reset that only re-seeds exits non-zero' -Expected 1 -Arguments @(
        '-CoreUrl', $reseedOnly.Url, '-AgentRuntimeUrl', $reseedOnly.Url, '-TimeoutSec', '5'
    )
} finally {
    Stop-StubService $reseedOnly
}

# --- INTERNAL / PUBLIC exposures -------------------------------------------

$internalStub = $null
$publicStub = $null
try {
    $internalStub = Start-StubService -Port (Get-FreeLoopbackPort) -Routes (New-InternalCoreRoutes)
    $publicStub = Start-StubService -Port (Get-FreeLoopbackPort) -Routes (New-PublicCoreRoutes)

    # The regression: the old script probed /graphql on the private Core, which
    # denies it by design, so a perfectly healthy GCP deployment failed.
    Assert-ExitCode -Name 'correct INTERNAL and PUBLIC exposures exit zero' -Expected 0 -Arguments @(
        '-CoreUrl', $internalStub.Url,
        '-PublicCoreUrl', $publicStub.Url,
        '-AgentRuntimeUrl', $internalStub.Url,
        '-TimeoutSec', '5'
    )
} finally {
    Stop-StubService $internalStub
    Stop-StubService $publicStub
}

$internalStub = $null
$leakyPublic = $null
try {
    $internalStub = Start-StubService -Port (Get-FreeLoopbackPort) -Routes (New-InternalCoreRoutes)
    $routes = New-PublicCoreRoutes
    # The internal surface answering on the public service is a broken boundary.
    $routes['/internal/demo/reset'] = $resetOk
    $leakyPublic = Start-StubService -Port (Get-FreeLoopbackPort) -Routes $routes

    Assert-ExitCode -Name 'public Core exposing /internal exits non-zero' -Expected 1 -Arguments @(
        '-CoreUrl', $internalStub.Url,
        '-PublicCoreUrl', $leakyPublic.Url,
        '-AgentRuntimeUrl', $internalStub.Url,
        '-TimeoutSec', '5'
    )
} finally {
    Stop-StubService $internalStub
    Stop-StubService $leakyPublic
}

$internalStub = $null
$unauthenticatedPublic = $null
try {
    $internalStub = Start-StubService -Port (Get-FreeLoopbackPort) -Routes (New-InternalCoreRoutes)
    $routes = New-PublicCoreRoutes
    # Firebase authentication not enforced: anonymous GraphQL answers.
    $routes['/graphql'] = $graphqlOk
    $unauthenticatedPublic = Start-StubService -Port (Get-FreeLoopbackPort) -Routes $routes

    Assert-ExitCode -Name 'public Core serving anonymous GraphQL exits non-zero' -Expected 1 -Arguments @(
        '-CoreUrl', $internalStub.Url,
        '-PublicCoreUrl', $unauthenticatedPublic.Url,
        '-AgentRuntimeUrl', $internalStub.Url,
        '-TimeoutSec', '5'
    )
} finally {
    Stop-StubService $internalStub
    Stop-StubService $unauthenticatedPublic
}

$foreignTenantAccepted = $null
try {
    # Demo administration that purges a tenant it was never meant to touch.
    $routes = New-InternalCoreRoutes
    $routes['/internal/demo/reset'] = $resetOk
    $foreignTenantAccepted = Start-StubService -Port (Get-FreeLoopbackPort) -Routes $routes

    Assert-ExitCode -Name 'demo reset accepting a foreign tenant exits non-zero' -Expected 1 -Arguments @(
        '-CoreUrl', $foreignTenantAccepted.Url,
        '-AgentRuntimeUrl', $foreignTenantAccepted.Url,
        '-TimeoutSec', '5'
    )
} finally {
    Stop-StubService $foreignTenantAccepted
}

Write-Host ''
if ($script:Failed -gt 0) {
    Write-Host "=== $script:Failed smoke-test contract case(s) failed ===" -ForegroundColor Red
    exit 1
}

Write-Host '=== smoke-test contract holds ===' -ForegroundColor Green
exit 0

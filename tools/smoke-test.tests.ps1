<#
.SYNOPSIS
    Exercises the exit-code contract of tools/smoke-test.ps1.

.DESCRIPTION
    The smoke test used to catch every connection error and print SKIPPED, so it
    exited 0 with Core and Agent Runtime both down and nothing in CI or a
    deployment gate could tell. These cases pin the contract: down means
    non-zero, healthy means zero, and skipping only happens when -Offline is
    asked for explicitly.

    Runs a throwaway HttpListener on a loopback port to stand in for the two
    services; no Pester or other module is required.
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

            $route = $Routes[$path]
            if ($null -eq $route) {
                $context.Response.StatusCode = 404
                $payload = '{"error":"not found"}'
            } else {
                $context.Response.StatusCode = [int]$route.Status
                $payload = [string]$route.Body
            }

            $bytes = [System.Text.Encoding]::UTF8.GetBytes($payload)
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
        Invoke-WebRequest -Uri "http://localhost:$($stub.Port)/__shutdown" -TimeoutSec 3 -UseBasicParsing | Out-Null
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

Write-Host '=== smoke-test.ps1 exit-code contract ===' -ForegroundColor Cyan

# A port nothing listens on: both services are, as far as the script can tell, down.
$deadPort = Get-FreeLoopbackPort
$deadCore = "http://localhost:$deadPort"

Assert-ExitCode -Name 'both services down exits non-zero' -Expected 1 -Arguments @(
    '-CoreUrl', $deadCore,
    '-AgentRuntimeUrl', $deadCore,
    '-TimeoutSec', '2'
)

Assert-ExitCode -Name 'explicit offline mode exits zero' -Expected 0 -Arguments @(
    '-CoreUrl', $deadCore,
    '-AgentRuntimeUrl', $deadCore,
    '-TimeoutSec', '2',
    '-Offline'
)

$healthyPort = Get-FreeLoopbackPort
$healthy = $null
try {
    $healthy = Start-StubService -Port $healthyPort -Routes @{
        '/health'                = @{ Status = 200; Body = '{"status":"UP","service":"agent-runtime"}' }
        '/graphql'               = @{ Status = 200; Body = '{"data":{"__typename":"Query"}}' }
        '/internal/demo/seed'    = @{ Status = 200; Body = '{"status":"SEEDED","tenantId":"demo-tenant","customersCount":2,"creditProfilesCount":2,"inventorySkusCount":3,"knowledgeDocumentsCount":2}' }
        '/internal/demo/reset'   = @{ Status = 200; Body = '{"status":"RESET","tenantId":"demo-tenant","purgedRowsTotal":7,"purgedRowsByArea":{"knowledge":2},"customersCount":2,"creditProfilesCount":2,"inventorySkusCount":3,"knowledgeDocumentsCount":2}' }
    }
    $healthyUrl = "http://localhost:$healthyPort"

    Assert-ExitCode -Name 'healthy deployment exits zero' -Expected 0 -Arguments @(
        '-CoreUrl', $healthyUrl,
        '-AgentRuntimeUrl', $healthyUrl,
        '-TimeoutSec', '5'
    )
} finally {
    Stop-StubService $healthy
}

$brokenPort = Get-FreeLoopbackPort
$broken = $null
try {
    # Agent Runtime answers, Core is up but erroring: still a failed deployment.
    $broken = Start-StubService -Port $brokenPort -Routes @{
        '/health'   = @{ Status = 200; Body = '{"status":"UP"}' }
        '/graphql'  = @{ Status = 500; Body = '{"error":"boom"}' }
    }
    $brokenUrl = "http://localhost:$brokenPort"

    Assert-ExitCode -Name 'Core erroring exits non-zero' -Expected 1 -Arguments @(
        '-CoreUrl', $brokenUrl,
        '-AgentRuntimeUrl', $brokenUrl,
        '-TimeoutSec', '5'
    )
} finally {
    Stop-StubService $broken
}

$reseedPort = Get-FreeLoopbackPort
$reseed = $null
try {
    # A /internal/demo/reset that only re-seeds reports SEEDED. That is the
    # regression this suite exists to keep out.
    $reseed = Start-StubService -Port $reseedPort -Routes @{
        '/health'              = @{ Status = 200; Body = '{"status":"UP"}' }
        '/graphql'             = @{ Status = 200; Body = '{"data":{"__typename":"Query"}}' }
        '/internal/demo/seed'  = @{ Status = 200; Body = '{"status":"SEEDED","customersCount":2,"inventorySkusCount":3,"knowledgeDocumentsCount":2}' }
        '/internal/demo/reset' = @{ Status = 200; Body = '{"status":"SEEDED","customersCount":2,"inventorySkusCount":3,"knowledgeDocumentsCount":2}' }
    }
    $reseedUrl = "http://localhost:$reseedPort"

    Assert-ExitCode -Name 'reset that only re-seeds exits non-zero' -Expected 1 -Arguments @(
        '-CoreUrl', $reseedUrl,
        '-AgentRuntimeUrl', $reseedUrl,
        '-TimeoutSec', '5'
    )
} finally {
    Stop-StubService $reseed
}

Write-Host ''
if ($script:Failed -gt 0) {
    Write-Host "=== $script:Failed smoke-test contract case(s) failed ===" -ForegroundColor Red
    exit 1
}

Write-Host '=== smoke-test contract holds ===' -ForegroundColor Green
exit 0

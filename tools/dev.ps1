[CmdletBinding()]
param(
    [ValidateSet('infra', 'core', 'web', 'agents')]
    [string]$Target = 'infra'
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot

switch ($Target) {
    'infra' { docker compose --file (Join-Path $repositoryRoot 'compose.yaml') up -d postgres }
    'core' {
        Push-Location (Join-Path $repositoryRoot 'services/enterprise-core')
        try { & ./gradlew.bat bootRun } finally { Pop-Location }
    }
    'web' {
        Push-Location (Join-Path $repositoryRoot 'apps/web')
        try { & pnpm start } finally { Pop-Location }
    }
    'agents' {
        Push-Location (Join-Path $repositoryRoot 'services/agent-runtime')
        try { & uv run uvicorn vextis_agents.app.main:app --reload --port 8081 } finally { Pop-Location }
    }
}

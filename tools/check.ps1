[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot

Push-Location (Join-Path $repositoryRoot 'services/enterprise-core')
try {
    & ./gradlew.bat check
    if ($LASTEXITCODE -ne 0) { throw 'Enterprise Core verification failed.' }
}
finally {
    Pop-Location
}

Push-Location (Join-Path $repositoryRoot 'apps/web')
try {
    & pnpm lint
    if ($LASTEXITCODE -ne 0) { throw 'Angular lint failed.' }
    & pnpm build
    if ($LASTEXITCODE -ne 0) { throw 'Angular build failed.' }
    & pnpm test
    if ($LASTEXITCODE -ne 0) { throw 'Angular tests failed.' }
}
finally {
    Pop-Location
}

Push-Location (Join-Path $repositoryRoot 'services/agent-runtime')
try {
    & uv run ruff check .
    if ($LASTEXITCODE -ne 0) { throw 'Agent Runtime lint failed.' }
    & uv run mypy src tests
    if ($LASTEXITCODE -ne 0) { throw 'Agent Runtime type check failed.' }
    & uv run pytest
    if ($LASTEXITCODE -ne 0) { throw 'Agent Runtime tests failed.' }
}
finally {
    Pop-Location
}

param(
    [switch]$Install,
    [switch]$Integration,
    [switch]$E2E
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path

function Invoke-Checked {
    param(
        [Parameter(Mandatory = $true)][string]$Label,
        [Parameter(Mandatory = $true)][string]$WorkingDirectory,
        [Parameter(Mandatory = $true)][string]$Command,
        [Parameter(Mandatory = $true)][string[]]$Arguments
    )

    Write-Host ([Environment]::NewLine + "==> $Label") -ForegroundColor Cyan
    Push-Location $WorkingDirectory
    try {
        & $Command @Arguments
        if ($LASTEXITCODE -ne 0) {
            throw "$Label failed with exit code $LASTEXITCODE"
        }
    }
    finally {
        Pop-Location
    }
}

$backend = Join-Path $repoRoot 'movie-ticket-backend'
$admin = Join-Path $repoRoot 'movie-ticket-admin'
$user = Join-Path $repoRoot 'movie-ticket-user'
$mavenWrapper = Join-Path $backend 'mvnw.cmd'

if (-not (Test-Path -LiteralPath $mavenWrapper)) {
    throw "Maven Wrapper not found: $mavenWrapper"
}

foreach ($frontend in @($admin, $user)) {
    if ($Install -or -not (Test-Path -LiteralPath (Join-Path $frontend 'node_modules'))) {
        Invoke-Checked -Label "Install dependencies: $(Split-Path $frontend -Leaf)" -WorkingDirectory $frontend -Command 'npm.cmd' -Arguments @('ci')
    }
}

if ($Integration) {
    Invoke-Checked -Label 'Backend unit and real-infrastructure integration tests' -WorkingDirectory $backend -Command $mavenWrapper -Arguments @('verify', '-Pintegration')
}
else {
    Invoke-Checked -Label 'Backend unit and API tests' -WorkingDirectory $backend -Command $mavenWrapper -Arguments @('test')
}
Invoke-Checked -Label 'Admin production build' -WorkingDirectory $admin -Command 'npm.cmd' -Arguments @('run', 'build')
Invoke-Checked -Label 'User production build' -WorkingDirectory $user -Command 'npm.cmd' -Arguments @('run', 'build')

if ($E2E) {
    Invoke-Checked -Label 'Install Playwright Chromium' -WorkingDirectory $user -Command 'npx.cmd' -Arguments @('playwright', 'install', 'chromium')
    Invoke-Checked -Label 'Admin browser E2E' -WorkingDirectory $admin -Command 'npm.cmd' -Arguments @('run', 'test:e2e')
    Invoke-Checked -Label 'User browser E2E' -WorkingDirectory $user -Command 'npm.cmd' -Arguments @('run', 'test:e2e')
}

Write-Host ([Environment]::NewLine + 'All verification steps passed.') -ForegroundColor Green

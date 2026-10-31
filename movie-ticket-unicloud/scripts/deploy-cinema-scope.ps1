param(
    [Parameter(Mandatory = $true)][string]$Stage,
    [string]$Cli = 'C:/Tools/HBuilderX/cli.exe'
)

$ErrorActionPreference = 'Stop'
$cloudRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$stagePath = (Resolve-Path $Stage).Path
$allowedParent = Join-Path $cloudRoot '.local'
if ((Split-Path $stagePath -Parent) -ne $allowedParent -or (Split-Path $stagePath -Leaf) -notlike 'hosting-stage-*') {
    throw 'Stage must be an existing .local/hosting-stage-* build directory.'
}
$space = 'mp-e7a16837-eb5c-4898-8fbd-e967f4d747d6'
$apiBase = "https://fc-$space.next.bspapp.com/api"
$manifest = Get-Content -LiteralPath (Join-Path $stagePath 'deployment-manifest.json') -Raw | ConvertFrom-Json
if ($manifest.apiBase -ne $apiBase) { throw 'Build API target differs from the deployment space.' }
foreach ($entry in @('index.html', 'admin/index.html')) {
    if (-not (Test-Path -LiteralPath (Join-Path $stagePath $entry))) { throw "Missing $entry" }
}
$relativeStage = '.local/' + (Split-Path $stagePath -Leaf)

# HBuilderX must be running, logged in, and have an initialized uniCloud plugin.
# No snapshot imports, database resets, or cloud resource deletions are performed.
& $Cli cloud functions --list cloudfunction --prj movie-ticket-unicloud-cloud --provider aliyun --cloud
if ($LASTEXITCODE -ne 0) { throw 'Unable to inspect existing cloud functions.' }
& $Cli cloud functions --upload db --prj movie-ticket-unicloud-cloud --provider aliyun --name mts_user
if ($LASTEXITCODE -ne 0) { throw 'User schema upload failed.' }
& $Cli cloud functions --upload cloudfunction --prj movie-ticket-unicloud-cloud --provider aliyun --name movie-api
if ($LASTEXITCODE -ne 0) { throw 'API upload failed.' }
$health = Invoke-RestMethod -Uri "$apiBase/health" -TimeoutSec 60
if ($health.code -ne 200 -or $health.data.release -ne 'cinema-scope-20260927') {
    throw 'Cloud API does not report the expected release; hosting was not uploaded.'
}
& $Cli hosting deploy --prj movie-ticket-unicloud --space $space --provider aliyun --source $relativeStage
if ($LASTEXITCODE -ne 0) { throw 'Hosting upload failed.' }
Write-Output 'Cloud API and hosting upload commands completed. Verify live assets and authenticated API responses next.'

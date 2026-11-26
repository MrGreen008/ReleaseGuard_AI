param([switch]$SkipModel)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath (Join-Path $PSScriptRoot '..')

& "$PSScriptRoot/setup.ps1" -SkipModel:$SkipModel
docker compose --env-file .env --env-file .env.flagsmith up -d --build --wait --wait-timeout 180
if ($LASTEXITCODE -ne 0) { throw 'ReleaseGuard services failed to start.' }
Write-Host 'Dashboard http://localhost:3000'
Write-Host 'Flagsmith http://localhost:8000'
Write-Host 'Local sign-in credentials are in .env.'

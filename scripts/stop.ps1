$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath (Join-Path $PSScriptRoot '..')
if (Test-Path -LiteralPath '.env.flagsmith') {
    docker compose --env-file .env --env-file .env.flagsmith down
} else {
    docker compose -f compose.dependencies.yml down
}
if ($LASTEXITCODE -ne 0) { throw 'Docker Compose could not stop the project.' }

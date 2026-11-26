param([switch]$Browser)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath (Join-Path $PSScriptRoot '..')
& .\mvnw.cmd -B verify
if ($LASTEXITCODE -ne 0) { throw 'Java verification failed.' }
npm.cmd --prefix frontend ci --no-audit --no-fund
if ($LASTEXITCODE -ne 0) { throw 'Frontend dependency installation failed.' }
foreach ($task in 'typecheck', 'lint', 'build') {
    npm.cmd --prefix frontend run $task
    if ($LASTEXITCODE -ne 0) { throw "Frontend $task failed." }
}
if ($Browser) {
    npm.cmd --prefix frontend test
    if ($LASTEXITCODE -ne 0) { throw 'Browser verification failed.' }
}

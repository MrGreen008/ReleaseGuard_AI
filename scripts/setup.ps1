param([switch]$SkipModel)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath (Join-Path $PSScriptRoot '..')

function New-LocalSecret {
    return [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(24)).ToLowerInvariant()
}

if (!(Test-Path -LiteralPath '.env')) {
    @(
        "RELEASE_DB_PASSWORD=$(New-LocalSecret)"
        "FLAGSMITH_DB_PASSWORD=$(New-LocalSecret)"
        "FLAGSMITH_DJANGO_SECRET=$(New-LocalSecret)"
        "FLAGSMITH_ADMIN_PASSWORD=$(New-LocalSecret)"
        "RELEASE_VIEWER_PASSWORD=$(New-LocalSecret)"
        "RELEASE_APPROVER_PASSWORD=$(New-LocalSecret)"
        "RELEASE_SHOP_PASSWORD=$(New-LocalSecret)"
        'OLLAMA_MODEL=qwen3:4b'
    ) | Set-Content -LiteralPath '.env' -Encoding utf8
}

if (!$env:RELEASE_SETUP_CONFIG_ONLY) {
    docker compose -f compose.dependencies.yml up -d --wait --wait-timeout 240 db flagsmith
    if ($LASTEXITCODE -ne 0) { throw 'Local dependencies failed to start.' }
    if (!(Test-Path -LiteralPath '.env.flagsmith')) {
        $seed = Get-Content -LiteralPath 'flagsmith/seed.py' -Raw
        $command = "exec(" + (ConvertTo-Json -InputObject $seed -Compress) + ")"
        $output = $command | docker compose -f compose.dependencies.yml exec -T flagsmith python manage.py shell
        if ($LASTEXITCODE -ne 0) { throw 'Flagsmith seed failed.' }
        $marker = 'RELEASEGUARD_CONFIG='
        $line = $output | Where-Object { $_ -like "$marker*" } | Select-Object -Last 1
        if (!$line) { throw 'Flagsmith seed returned no configuration.' }
        $config = $line.Substring($marker.Length) | ConvertFrom-Json -AsHashtable
        $config.GetEnumerator() | ForEach-Object { "$($_.Key)=$($_.Value)" } |
            Set-Content -LiteralPath '.env.flagsmith' -Encoding utf8
    }
    if (!$SkipModel) {
        $model = (Get-Content -LiteralPath '.env' | Where-Object { $_ -like 'OLLAMA_MODEL=*' }) -replace '^OLLAMA_MODEL=', ''
        if (!$model) { $model = 'qwen3:4b' }
        docker compose -f compose.dependencies.yml up -d ollama
        if ($LASTEXITCODE -ne 0) { throw 'Ollama failed to start.' }
        docker compose -f compose.dependencies.yml exec -T ollama ollama pull $model
        if ($LASTEXITCODE -ne 0) { throw 'Failed to pull the configured Ollama model.' }
    }
}

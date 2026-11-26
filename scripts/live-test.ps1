param([switch]$Capture)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath (Join-Path $PSScriptRoot '..')

$config = ConvertFrom-StringData -StringData (Get-Content -LiteralPath '.env' -Raw)
$auth = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("approver:$($config.RELEASE_APPROVER_PASSWORD)"))
$headers = @{ Authorization = "Basic $auth"; 'X-ReleaseGuard-Intent' = '1' }
$base = 'http://127.0.0.1:8080/api/v1'

function Request([string]$Path, [string]$Method = 'GET', $Payload = $null) {
    $requestArgs = @{ Uri = $base + $Path; Headers = $headers; Method = $Method; TimeoutSec = 140 }
    if ($null -ne $Payload) {
        $requestArgs.ContentType = 'application/json'
        $requestArgs.Body = ConvertTo-Json -InputObject $Payload -Compress
    }
    return Invoke-RestMethod @requestArgs
}

function Wait-Until([string]$Description, [scriptblock]$Check, [int]$Seconds = 50) {
    $until = [DateTime]::UtcNow.AddSeconds($Seconds)
    do {
        $result = & $Check
        if ($result) { return $result }
        Start-Sleep -Seconds 2
    } while ([DateTime]::UtcNow -lt $until)
    throw "Timed out waiting for $Description."
}

function PaymentFlag {
    return (Request '/integrations').flags | Where-Object name -eq 'new_payment_engine'
}

function New-Incident([string]$Mode) {
    $old = @((Request '/dashboard').incidents.id)
    Request '/lab/fault' 'POST' @{ fault = $Mode } | Out-Null
    return Wait-Until "a new $Mode incident" {
        (Request '/dashboard').incidents | Where-Object { $_.id -notin $old } | Select-Object -First 1
    }
}

$integrations = Request '/integrations'
if (!$integrations.flagsmith -or !$integrations.ai) {
    throw 'Flagsmith and the configured Ollama model must both be available.'
}
if (!(PaymentFlag).enabled) {
    throw 'Use the isolated Flagsmith dashboard to enable new_payment_engine before starting this test.'
}
$initial = Request '/dashboard'
if ($initial.lab.running) { throw 'Stop the existing lab traffic run before starting this test.' }

try {
    Request '/lab/fault' 'POST' @{ fault = 'HEALTHY' } | Out-Null
    Request '/lab/traffic' 'POST' @{ rate = 12; seconds = 750; seed = 18 } | Out-Null
    $baseline = Wait-Until 'healthy baseline in both real Flagsmith cohorts' {
        $overview = Request '/dashboard'
        $stats = $overview.metrics
        if ($stats.total.requests -ge 30 -and $stats.total.failures -eq 0 -and
            $stats.cohorts.STABLE.requests -ge 8 -and $stats.cohorts.EXPERIMENTAL.requests -ge 8) {
            $overview
        }
    }
    if (@($baseline.incidents).Count -ne @($initial.incidents).Count) {
        throw 'Healthy baseline generated an incident.'
    }
    Write-Host "PASS baseline $($baseline.metrics.total.requests) requests, zero server errors."

    $shared = New-Incident 'DEPENDENCY_FAILURE'
    $finding = Request "/incidents/$($shared.id)/investigation" 'POST'
    $same = (Request '/dashboard').proposals | Where-Object incidentId -eq $shared.id
    if ($same -or $finding.finding.recommendation -ne 'INVESTIGATE_DEPENDENCY' -or
        $shared.current.cohorts.STABLE.failures -eq 0 -or $shared.current.cohorts.EXPERIMENTAL.failures -eq 0) {
        throw 'Shared failure did not produce the expected read-only dependency finding.'
    }
    Write-Host "PASS shared dependency, $($finding.tools.Count) real tool calls, no proposal."

    Request '/lab/fault' 'POST' @{ fault = 'HEALTHY' } | Out-Null
    # Clear the prior outage from the full baseline window.
    Start-Sleep -Seconds 65
    $payment = New-Incident 'PAYMENT_REGRESSION'
    $finding = Request "/incidents/$($payment.id)/investigation" 'POST'
    $proposal = (Request '/dashboard').proposals | Where-Object incidentId -eq $payment.id
    if (!$proposal -or $proposal.status -ne 'PENDING_APPROVAL' -or !(PaymentFlag).enabled) {
        throw "Expected a pending real rollback proposal. Policy result $($finding.policy)"
    }
    Write-Host "PASS variant regression, $($finding.tools.Count) real tool calls, flag still enabled."
    if ($Capture) {
        node frontend/scripts/capture.mjs
        if ($LASTEXITCODE -ne 0) { throw 'Dashboard screenshot capture failed.' }
    }

    $decision = Request "/proposals/$($proposal.id)/decision" 'POST' @{ decision = 'APPROVE' }
    if ($decision.status -ne 'VERIFYING' -or (PaymentFlag).enabled) {
        throw "Approved mutation did not verify in Flagsmith. Result $($decision.status)"
    }
    Write-Host 'PASS explicit approval and real disabled-state readback.'
    $recovered = Wait-Until 'verified recovery from fresh stable traffic' {
        $entry = (Request '/dashboard').proposals | Where-Object id -eq $proposal.id
        if ($entry.status -eq 'RECOVERED') { $entry }
        elseif ($entry.status -ne 'VERIFYING') { throw "Unexpected recovery state $($entry.status)." }
    }
    Write-Host "PASS recovery from $($recovered.recovery.total.requests) fresh requests with $($recovered.recovery.total.failures) server errors."
} finally {
    try {
        Request '/lab/fault' 'POST' @{ fault = 'HEALTHY' } | Out-Null
        Request '/lab/traffic/stop' 'POST' | Out-Null
    } catch {
        Write-Error -ErrorAction Continue -Message $_.Exception.Message -Exception $_.Exception
    }
}

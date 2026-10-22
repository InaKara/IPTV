# Test double only: no network/device operations. The caller supplies isolated log/config.
if (-not $env:AYPI_TEST_ADB_LOG) { throw 'Missing test fixture log.' }
$record = [ordered]@{ arguments = [string[]]$args }
[IO.File]::AppendAllText($env:AYPI_TEST_ADB_LOG, (ConvertTo-Json -InputObject $record -Compress) + "`n")
if ($args[2] -eq 'install') {
    if ($env:AYPI_TEST_ADB_SCENARIO -eq 'install-failure') { exit 7 }
    Write-Output 'Success'
    exit 0
}
if ($args[2] -eq 'shell' -and $args[3] -eq 'am') {
    if ($args[4] -eq 'force-stop') { exit 0 }
    if ($args[4] -eq 'start') {
        if ($env:AYPI_TEST_ADB_SCENARIO -eq 'launch-error') { Write-Output 'Status: error' }
        else { Write-Output 'Status: ok' }
        exit 0
    }
}
throw 'Unexpected synthetic ADB command.'

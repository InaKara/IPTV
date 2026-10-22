[CmdletBinding()]
param([string]$ResultPath)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'LocalEnvironment.ps1')
$fixtures = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'env-fixtures.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$fixturePath = [IO.Path]::GetTempFileName()
$results = @()
try {
    foreach ($fixture in $fixtures) {
        $status = 'Passed'
        try {
            [IO.File]::WriteAllText($fixturePath, $fixture.text, [Text.UTF8Encoding]::new($false))
            $invalid = $null -ne $fixture.PSObject.Properties['invalid'] -and $fixture.invalid
            $failed = $false
            try { $actual = Read-LocalEnvironment $fixturePath } catch { $failed = $true }
            if ($invalid) {
                if (-not $failed) { throw 'Accepted invalid fixture.' }
            } else {
                if ($failed) { throw 'Rejected valid fixture.' }
                $expectedKeys = @($fixture.expected.PSObject.Properties | ForEach-Object { $_.Name })
                if ($actual.Count -ne $expectedKeys.Count) { throw 'Literal fixture key mismatch.' }
                foreach ($key in $expectedKeys) {
                    if ($actual[$key] -cne $fixture.expected.$key) { throw 'Literal fixture value mismatch.' }
                }
            }
        } catch { $status = 'Failed' }
        $results += [pscustomobject]@{ key = 'env-powershell#' + $fixture.name; status = $status }
    }
} finally { Remove-Item -LiteralPath $fixturePath }
if ($ResultPath) { [IO.File]::WriteAllText($ResultPath, (ConvertTo-Json -InputObject @($results) -Depth 4), [Text.UTF8Encoding]::new($false)) }
$passed = @($results | Where-Object status -eq 'Passed').Count
Write-Host "PowerShell environment: $passed/$($results.Count) passed."
if ($passed -ne $results.Count -or $results.Count -eq 0) { throw 'Environment fixture checks failed.' }

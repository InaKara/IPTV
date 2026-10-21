Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'LocalEnvironment.ps1')
$fixtures = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'env-fixtures.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$fixturePath = [IO.Path]::GetTempFileName()
try {
    foreach ($fixture in $fixtures) {
        [IO.File]::WriteAllText($fixturePath, $fixture.text, [Text.UTF8Encoding]::new($false))
        $invalid = $null -ne $fixture.PSObject.Properties['invalid'] -and $fixture.invalid
        $failed = $false
        try { $actual = Read-LocalEnvironment $fixturePath } catch { $failed = $true }
        if ($invalid) {
            if (-not $failed) { throw "Accepted invalid fixture: $($fixture.name)" }
        } else {
            if ($failed) { throw "Rejected valid fixture: $($fixture.name)" }
            $expectedKeys = @($fixture.expected.PSObject.Properties | ForEach-Object { $_.Name })
            if ($actual.Count -ne $expectedKeys.Count) { throw 'Literal fixture key mismatch.' }
            foreach ($key in $expectedKeys) {
                if ($actual[$key] -cne $fixture.expected.$key) { throw 'Literal fixture value mismatch.' }
            }
        }
    }
    Write-Host "PowerShell literal environment fixtures passed ($($fixtures.Count))."
} finally { Remove-Item -LiteralPath $fixturePath }

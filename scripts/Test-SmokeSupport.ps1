[CmdletBinding()]
param([string]$ResultPath)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'SmokeSupport.ps1')
$tempRoot = Join-Path ([IO.Path]::GetTempPath()) ('iptv-smoke-' + [Guid]::NewGuid().ToString('N'))
[IO.Directory]::CreateDirectory($tempRoot) | Out-Null
$text = "# Sample`n`n## How to read this catalog`n`n| DEV-004 | Check behavior. | Implemented | Authored evidence. |`n"
$definition = @(Get-RequirementDefinitions $text)[0]
function New-SampleCatalog {
    [pscustomobject]@{
        version = 1
        cases = @([pscustomobject]@{ id='TC-001'; key='sample#case'; title='Check behavior'; kind='App' })
        requirements = @([pscustomobject]@{ id='DEV-004'; fingerprint=$definition.Fingerprint; coverage='Partial'; gap='Device check pending.'; cases=@('TC-001') })
    }
}
function Invoke-FixtureSmoke {
    param([string]$Mode)
    $project = Join-Path $tempRoot ('fixture-' + $Mode)
    $scripts = Join-Path $project 'scripts'
    [IO.Directory]::CreateDirectory($scripts) | Out-Null
    $repository = Split-Path $PSScriptRoot -Parent
    Copy-Item -LiteralPath (Join-Path $repository 'test-smoke.ps1') -Destination $project
    foreach ($name in @('SmokeSupport.ps1','LocalEnvironment.ps1')) { Copy-Item -LiteralPath (Join-Path $PSScriptRoot $name) -Destination $scripts }
    [IO.File]::WriteAllText((Join-Path $project '.env'),'JAVA_HOME=C:/SyntheticJdk')
    [IO.File]::WriteAllText((Join-Path $project 'REQUIREMENTS.md'),$text)
    $cases = @([pscustomobject]@{id='TC-001';key='sample#case';title='Synthetic app outcome';kind='App';source='fixture'})
    $n = 1
    foreach ($suite in @(
        @{name='Test-LocalEnvironment.ps1';key='fixture#powershell';kind='EnvironmentPowerShell'},
        @{name='Test-ProjectContracts.ps1';key='fixture#project';kind='Project'},
        @{name='Test-SmokeSupport.ps1';key='fixture#runner';kind='Runner'}
    )) {
        $n++
        $cases += [pscustomobject]@{id="TC-00$n";key=$suite.key;title='Synthetic helper outcome';kind=$suite.kind;source='fixture'}
        $stub = 'param([string]$ResultPath)' + "`n" + '[IO.File]::WriteAllText($ResultPath,''[{"key":"' + $suite.key + '","status":"Passed"}]'')'
        if ($suite.kind -eq 'EnvironmentPowerShell') {
            $cases += [pscustomobject]@{id='TC-006';key='fixture#powershell-extra';title='Second JSON result';kind='EnvironmentPowerShell';source='fixture'}
            $stub = $stub.Replace('"status":"Passed"}]','"status":"Passed"},{"key":"fixture#powershell-extra","status":"Passed"}]')
        }
        [IO.File]::WriteAllText((Join-Path $scripts $suite.name),$stub)
    }
    $cases += [pscustomobject]@{id='TC-005';key='fixture#gradle';title='Synthetic Gradle fixture';kind='EnvironmentGradle';source='fixture'}
    $catalog = [pscustomobject]@{version=1;runtimeTargetSeconds=90;cases=$cases;requirements=@([pscustomobject]@{id='DEV-004';fingerprint=$definition.Fingerprint;coverage='Partial';gap='Synthetic harness.';cases=@($cases.id)})}
    [IO.File]::WriteAllText((Join-Path $scripts 'test-catalog.json'),(ConvertTo-Json -InputObject $catalog -Depth 8))
    $fakeGradle = @'
$directory = ($args | Where-Object { $_ -like '-PsmokeReportDirectory=*' }).Substring('-PsmokeReportDirectory='.Length)
$mode = [IO.File]::ReadAllText((Join-Path $PSScriptRoot 'mode.txt'))
if ($mode -eq 'build-error') { exit 7 }
[IO.Directory]::CreateDirectory((Join-Path $directory 'app-tests')) | Out-Null
$element = if ($mode -eq 'test-failure') { '<failure>synthetic-private-detail</failure>' } else { '' }
[IO.File]::WriteAllText((Join-Path $directory 'app-tests/TEST-fixture.xml'),('<testsuite><testcase classname="sample" name="case">'+$element+'</testcase></testsuite>'))
[IO.File]::WriteAllText((Join-Path $directory 'environment-gradle.json'),'[{"key":"fixture#gradle","status":"Passed"}]')
if ($mode -eq 'test-failure') {
    [Console]::Error.WriteLine('synthetic-private-detail: native stderr must not interrupt fresh XML collection')
    exit 1
} else { exit 0 }
'@
    [IO.File]::WriteAllText((Join-Path $project 'mode.txt'),$Mode)
    [IO.File]::WriteAllText((Join-Path $project 'fake-gradle.ps1'),$fakeGradle)
    [IO.File]::WriteAllText((Join-Path $project 'gradlew.bat'),("@echo off`r`npowershell.exe -NoProfile -ExecutionPolicy Bypass -File `"%~dp0fake-gradle.ps1`" %*`r`nexit /b %errorlevel%`r`n"))
    # Stale normal-location XML must never turn a failed build into a pass.
    $old = Join-Path $project 'app/build/test-results/testDebugUnitTest'
    [IO.Directory]::CreateDirectory($old) | Out-Null
    [IO.File]::WriteAllText((Join-Path $old 'TEST-old.xml'),'<testsuite><testcase classname="sample" name="case"/></testsuite>')
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $project 'test-smoke.ps1') *> (Join-Path $project 'child.log')
    $exitCode = $LASTEXITCODE
    if (-not (Test-Path -LiteralPath (Join-Path $project 'app/build/reports/smoke/latest.json'))) {
        throw ('Synthetic child did not report: ' + [IO.File]::ReadAllText((Join-Path $project 'child.log')))
    }
    $report = Get-Content -LiteralPath (Join-Path $project 'app/build/reports/smoke/latest.json') -Raw -Encoding UTF8 | ConvertFrom-Json
    [pscustomobject]@{exitCode=$exitCode;report=$report;catalog=[IO.File]::ReadAllText((Join-Path $project 'REQUIREMENTS.md'))}
}
$results = @()
try {
    $results += Invoke-SmokeCheck 'runner#definitionsIgnoreGeneratedRows' {
        $parsed = @(Get-RequirementDefinitions ($text + "| Tests for DEV-004 | TC-001 Passed | Passed | Partial |`n"))
        Assert-Smoke ($parsed.Count -eq 1 -and $parsed[0].Fingerprint -eq $definition.Fingerprint)
    }
    $results += Invoke-SmokeCheck 'runner#staleDefinitionRejected' {
        $catalog = New-SampleCatalog
        $changed = @(Get-RequirementDefinitions ($text.Replace('Check behavior.', 'Changed behavior.')))
        Assert-Smoke (@(Test-SmokeCatalog $catalog $changed | Where-Object { $_ -like 'Stale mapping:*' }).Count -eq 1)
    }
    $results += Invoke-SmokeCheck 'runner#statusChangeRequiresReview' {
        $changed = @(Get-RequirementDefinitions ($text.Replace('Implemented', 'Partial')))
        Assert-Smoke ($changed[0].Fingerprint -ne $definition.Fingerprint)
    }
    $results += Invoke-SmokeCheck 'runner#removedRequirementRejected' {
        Assert-Smoke (@(Test-SmokeCatalog (New-SampleCatalog) @() | Where-Object { $_ -like 'Removed requirement*' }).Count -eq 1)
    }
    $results += Invoke-SmokeCheck 'runner#missingMappingRejected' {
        $catalog = New-SampleCatalog; $catalog.requirements = @()
        Assert-Smoke (@(Test-SmokeCatalog $catalog @($definition) | Where-Object { $_ -like 'Missing mapping:*' }).Count -eq 1)
    }
    $results += Invoke-SmokeCheck 'runner#unknownTestcaseRejected' {
        $catalog = New-SampleCatalog; $catalog.requirements[0].cases = @('TC-MISSING')
        Assert-Smoke (@(Test-SmokeCatalog $catalog @($definition) | Where-Object { $_ -like 'Unknown testcase*' }).Count -eq 1)
    }
    $results += Invoke-SmokeCheck 'runner#missingExecutionIsError' {
        $actual = @(Get-SmokeResults (New-SampleCatalog) @())
        Assert-Smoke ($actual.Count -eq 1 -and $actual[0].status -eq 'Error')
    }
    $results += Invoke-SmokeCheck 'runner#skippedIsNotPassed' {
        $actual = @(Get-SmokeResults (New-SampleCatalog) @([pscustomobject]@{key='sample#case'; status='Skipped'}))
        $totals = Get-SmokeTotals $actual
        Assert-Smoke ($totals.total -eq 1 -and $totals.passed -eq 0 -and $totals.skipped -eq 1)
    }
    $results += Invoke-SmokeCheck 'runner#unexpectedExecutionRejected' {
        $rejected = $false
        try { Get-SmokeResults (New-SampleCatalog) @([pscustomobject]@{key='unknown#case';status='Passed'}) | Out-Null } catch { $rejected = $true }
        Assert-Smoke $rejected
    }
    $results += Invoke-SmokeCheck 'runner#duplicateExecutionRejected' {
        $rejected = $false
        try { Get-SmokeResults (New-SampleCatalog) @([pscustomobject]@{key='sample#case';status='Passed'},[pscustomobject]@{key='sample#case';status='Passed'}) | Out-Null } catch { $rejected = $true }
        Assert-Smoke $rejected
    }
    $results += Invoke-SmokeCheck 'runner#junitPreservesOutcomesAndOmitsDetails' {
        $directory = Join-Path $tempRoot 'junit'; [IO.Directory]::CreateDirectory($directory) | Out-Null
        [IO.File]::WriteAllText((Join-Path $directory 'TEST-sample.xml'), '<testsuite><testcase classname="S" name="pass"/><testcase classname="S" name="fail"><failure>synthetic-private-detail</failure></testcase><testcase classname="S" name="skip"><skipped/></testcase><testcase classname="S" name="error"><error/></testcase></testsuite>')
        $actual = @(Read-SmokeJUnit $directory)
        Assert-Smoke (($actual.status -join ',') -eq 'Passed,Failed,Skipped,Error')
        Assert-Smoke (-not (($actual | ConvertTo-Json) -match 'synthetic-private-detail'))
    }
    $results += Invoke-SmokeCheck 'runner#junitRejectsDtd' {
        $directory = Join-Path $tempRoot 'dtd'; [IO.Directory]::CreateDirectory($directory) | Out-Null
        [IO.File]::WriteAllText((Join-Path $directory 'TEST-dtd.xml'), '<!DOCTYPE testsuite [<!ENTITY secret "private">]><testsuite/>')
        $rejected = $false
        try { Read-SmokeJUnit $directory | Out-Null } catch { $rejected=$true }
        Assert-Smoke $rejected
    }
    $results += Invoke-SmokeCheck 'runner#missingDirectoryDoesNotReuseOldEvidence' {
        Assert-Smoke (@(Read-SmokeJUnit (Join-Path $tempRoot 'missing')).Count -eq 0)
    }
    $results += Invoke-SmokeCheck 'runner#rowsAreIdempotentAndPreserveAuthoredText' {
        $path = Join-Path $tempRoot 'catalog.md'; [IO.File]::WriteAllText($path,$text)
        $catalog=New-SampleCatalog
        $cases=@(Get-SmokeResults $catalog @([pscustomobject]@{key='sample#case';status='Passed'}))
        Update-RequirementTestRows $path $catalog $cases 'sample-time' 'Passed'
        $first=[IO.File]::ReadAllText($path)
        Update-RequirementTestRows $path $catalog $cases 'sample-time' 'Passed'
        $second=[IO.File]::ReadAllText($path)
        Assert-Smoke ($first -ceq $second)
        Assert-Smoke ($second.Contains('Authored evidence.') -and $second.Contains('TC-001 - Passed'))
        Assert-Smoke ([regex]::Matches($second,'\| Tests for DEV-004 \|').Count -eq 1)
    }
    $results += Invoke-SmokeCheck 'runner#staleRowsCannotClaimPassed' {
        $path=Join-Path $tempRoot 'stale.md'; [IO.File]::WriteAllText($path,$text.Replace('Check behavior.','Changed behavior.'))
        $catalog=New-SampleCatalog
        $cases=@(Get-SmokeResults $catalog @([pscustomobject]@{key='sample#case';status='Passed'}))
        Update-RequirementTestRows $path $catalog $cases 'sample-time' 'Failed'
        $actual=[IO.File]::ReadAllText($path)
        Assert-Smoke ($actual.Contains('TC-001 - Stale') -and -not $actual.Contains('TC-001 - Passed'))
    }
    $results += Invoke-SmokeCheck 'runner#entryPointPublishesSuccessfulCountsAndRows' {
        $actual=Invoke-FixtureSmoke 'pass'
        Assert-Smoke ($actual.exitCode -eq 0 -and $actual.report.passed -and $actual.report.totals.passed -eq 6)
        Assert-Smoke ($actual.catalog.Contains('TC-001 - Passed'))
    }
    $results += Invoke-SmokeCheck 'runner#entryPointFailureExitsNonzeroAndUpdatesRows' {
        $actual=Invoke-FixtureSmoke 'test-failure'
        Assert-Smoke ($actual.exitCode -ne 0 -and -not $actual.report.passed -and $actual.report.totals.failed -eq 1)
        Assert-Smoke ($actual.catalog.Contains('TC-001 - Failed') -and -not $actual.catalog.Contains('synthetic-private-detail'))
    }
    $results += Invoke-SmokeCheck 'runner#entryPointBuildErrorCannotReuseOldPassingXml' {
        $actual=Invoke-FixtureSmoke 'build-error'
        Assert-Smoke ($actual.exitCode -ne 0 -and -not $actual.report.passed -and $actual.report.totals.errors -eq 2)
        Assert-Smoke ($actual.catalog.Contains('TC-001 - Error') -and -not $actual.catalog.Contains('TC-001 - Passed'))
    }
} finally {
    $resolved = [IO.Path]::GetFullPath($tempRoot)
    $tempParent = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
    if (-not $resolved.StartsWith($tempParent,[StringComparison]::OrdinalIgnoreCase) -or
        (Split-Path $resolved -Leaf) -notmatch '^iptv-smoke-[a-f0-9]{32}$') { throw 'Unsafe temporary cleanup path.' }
    Remove-Item -LiteralPath $resolved -Recurse -Force
}
if ($ResultPath) { [IO.File]::WriteAllText($ResultPath,(ConvertTo-Json -InputObject @($results) -Depth 4),[Text.UTF8Encoding]::new($false)) }
$passed=@($results | Where-Object status -eq 'Passed').Count
Write-Host "Smoke runner checks: $passed/$($results.Count) passed."
if ($passed -ne $results.Count) {
    foreach ($case in ($results | Where-Object status -ne 'Passed')) { Write-Host ('  Failed: ' + $case.key) }
    throw 'Smoke runner checks failed.'
}

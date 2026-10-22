[CmdletBinding()]
param([switch]$Lint)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'scripts/LocalEnvironment.ps1')
. (Join-Path $PSScriptRoot 'scripts/SmokeSupport.ps1')

$projectRoot = $PSScriptRoot
$catalogPath = Join-Path $projectRoot 'scripts/test-catalog.json'
$requirementsPath = Join-Path $projectRoot 'REQUIREMENTS.md'
$previousJava = $env:JAVA_HOME
$previousAndroid = $env:ANDROID_HOME
$observed = @(); $issues = @(); $nativeExit = -1
$runId = [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssfffZ') + '-' + [Guid]::NewGuid().ToString('N').Substring(0,8)
$runDirectory = Join-Path $projectRoot ('app/build/reports/smoke/' + $runId)
[IO.Directory]::CreateDirectory($runDirectory) | Out-Null
$catalog = Get-Content -LiteralPath $catalogPath -Raw -Encoding UTF8 | ConvertFrom-Json
$requirements = @(Get-RequirementDefinitions ([IO.File]::ReadAllText($requirementsPath)))
$issues = @(Test-SmokeCatalog $catalog $requirements)
$runLabel = [TimeZoneInfo]::ConvertTimeBySystemTimeZoneId([DateTimeOffset]::UtcNow, 'W. Europe Standard Time').ToString('yyyy-MM-dd HH:mm:ss zzz')
$stopwatch = [Diagnostics.Stopwatch]::StartNew()

Push-Location -LiteralPath $projectRoot
try {
    if ($issues.Count -gt 0) { throw 'Review requirement/test mappings before running.' }
    $config = Read-LocalEnvironment (Join-Path $projectRoot '.env')
    if ($config['JAVA_HOME']) { $env:JAVA_HOME = $config['JAVA_HOME'] }
    if ($config['ANDROID_HOME']) { $env:ANDROID_HOME = $config['ANDROID_HOME'] }
    if (-not $env:JAVA_HOME) { throw 'Configure JAVA_HOME for JDK 17.' }
    foreach ($suite in @(
        @{ script = 'scripts/Test-LocalEnvironment.ps1'; report = 'environment-powershell.json' },
        @{ script = 'scripts/Test-ProjectContracts.ps1'; report = 'project.json' },
        @{ script = 'scripts/Test-SmokeSupport.ps1'; report = 'runner.json' }
    )) {
        $reportPath = Join-Path $runDirectory $suite.report
        try { & (Join-Path $projectRoot $suite.script) -ResultPath $reportPath }
        catch { $issues += 'PowerShell checks failed; see individual testcase results.' }
        if (Test-Path -LiteralPath $reportPath) { $observed += @(Read-SmokeJsonResults $reportPath) }
    }
    $tasks = @(':app:testDebugUnitTest', ':app:verifyLocalEnvironment')
    if ($Lint) { $tasks += ':app:lintDebug' }
    $arguments = $tasks + @("-PsmokeReportDirectory=$runDirectory", '--console=plain')
    Write-Host 'Running local smoke/regression checks (no device or provider connection)...'
    & (Join-Path $projectRoot 'gradlew.bat') @arguments *> (Join-Path $runDirectory 'gradle.log')
    $nativeExit = $LASTEXITCODE
    if ($nativeExit -ne 0) { $issues += 'Gradle build/test/check failure; private details are in the local run log.' }
    $observed += @(Read-SmokeJUnit (Join-Path $runDirectory 'app-tests'))
    $environmentReport = Join-Path $runDirectory 'environment-gradle.json'
    if (Test-Path -LiteralPath $environmentReport) { $observed += @(Read-SmokeJsonResults $environmentReport) }
} catch {
    # Do not copy raw exceptions, machine paths, provider strings or stack traces to the public catalog.
    $issues += 'Suite setup/execution did not complete; inspect local configuration, mappings and run artifacts.'
} finally {
    $env:JAVA_HOME = $previousJava
    $env:ANDROID_HOME = $previousAndroid
    Pop-Location
}
try { $results = @(Get-SmokeResults $catalog $observed) }
catch {
    $issues += 'Unexpected or duplicate execution results; reconcile testcase selectors.'
    $results = @(Get-SmokeResults $catalog @())
}
$totals = Get-SmokeTotals $results
$success = $issues.Count -eq 0 -and $nativeExit -eq 0 -and $totals.total -gt 0 -and $totals.failed -eq 0 -and $totals.errors -eq 0 -and $totals.skipped -eq 0
$outcome = if ($success) { 'Passed' } else { 'Failed / incomplete' }
try {
    Update-RequirementTestRows $requirementsPath $catalog $results $runLabel $outcome
    Update-TestCaseCatalog (Join-Path $projectRoot 'TEST_CASES.md') $catalog $results $runLabel
}
catch { $issues += 'Could not update requirement test rows.'; $success = $false }
$stopwatch.Stop()
$report = [ordered]@{
    version = 1; run = $runId; time = $runLabel; timezone = 'Europe/Berlin'
    passed = $success; elapsedSeconds = [Math]::Round($stopwatch.Elapsed.TotalSeconds, 2)
    gradleExitCode = $nativeExit; lintRequested = [bool]$Lint
    totals = $totals; issues = @($issues); results = @($results)
    coverage = @($catalog.requirements | Group-Object coverage | ForEach-Object { [pscustomobject]@{category=$_.Name; requirements=$_.Count} })
}
$json = $report | ConvertTo-Json -Depth 12
[IO.File]::WriteAllText((Join-Path $runDirectory 'summary.json'), $json, [Text.UTF8Encoding]::new($false))
[IO.File]::WriteAllText((Join-Path $projectRoot 'app/build/reports/smoke/latest.json'), $json, [Text.UTF8Encoding]::new($false))
Write-Host ("Smoke: {0}/{1} passed; {2} failed; {3} skipped; {4} errors. {5:N1}s." -f $totals.passed,$totals.total,$totals.failed,$totals.skipped,$totals.errors,$stopwatch.Elapsed.TotalSeconds)
foreach ($group in ($results | Where-Object kind -ne 'Manual' | Group-Object kind)) {
    Write-Host ("  {0}: {1}/{2} passed" -f $group.Name,@($group.Group | Where-Object status -eq 'Passed').Count,$group.Count)
}
foreach ($result in ($results | Where-Object { $_.status -in @('Failed','Error','Skipped') })) {
    $linked = @($catalog.requirements | Where-Object { $result.id -in $_.cases } | ForEach-Object id) -join ', '
    Write-Host ("  {0}: {1} [{2}]" -f $result.id,$result.status,$linked)
}
foreach ($issue in $issues) { Write-Host ("  " + $issue) }
Write-Host 'Requirements: test rows updated; manual/partial coverage is reported separately.'
foreach ($coverage in $report.coverage) { Write-Host ("  {0}: {1} requirements" -f $coverage.category,$coverage.requirements) }
if ($stopwatch.Elapsed.TotalSeconds -gt $catalog.runtimeTargetSeconds) { Write-Host 'Runtime exceeded the warm-run target; review startup/build time and test cost before expanding the suite.' }
Write-Host 'Local details: app/build/reports/smoke/latest.json'
if ($success) { exit 0 } else { exit 1 }

[CmdletBinding()]
param([string]$ResultPath)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'SmokeSupport.ps1')
. (Join-Path $PSScriptRoot 'LocalEnvironment.ps1')
$root = Split-Path $PSScriptRoot -Parent
function Invoke-DeploymentFixture {
    param([string]$Scenario = 'success')
    $tokens = $null; $parseErrors = $null
    $tree = [Management.Automation.Language.Parser]::ParseFile((Join-Path $root 'deploy-firestick.ps1'),[ref]$tokens,[ref]$parseErrors)
    Assert-Smoke ($parseErrors.Count -eq 0)
    foreach ($name in @('Invoke-CheckedNative','Install-FirestickApp')) {
        $function = $tree.Find({param($node) $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq $name},$true)
        Assert-Smoke ($null -ne $function)
        . ([scriptblock]::Create($function.Extent.Text))
    }
    $logPath = [IO.Path]::GetTempFileName()
    $previousLog = $env:AYPI_TEST_ADB_LOG
    $previousScenario = $env:AYPI_TEST_ADB_SCENARIO
    try {
        $env:AYPI_TEST_ADB_LOG = $logPath
        $env:AYPI_TEST_ADB_SCENARIO = $Scenario
        $rejected = $false
        try {
            Install-FirestickApp -Adb (Join-Path $PSScriptRoot 'test-fixtures/adb.ps1') `
                -Device '192.0.2.1:5555' -Apk 'fixture.apk' | Out-Null
        } catch { $rejected = $true }
        $calls = @(foreach ($line in [IO.File]::ReadAllLines($logPath)) { $line | ConvertFrom-Json })
        return [pscustomobject]@{ Rejected = $rejected; Calls = $calls }
    } finally {
        $env:AYPI_TEST_ADB_LOG = $previousLog
        $env:AYPI_TEST_ADB_SCENARIO = $previousScenario
        Remove-Item -LiteralPath $logPath
    }
}
$results = @()
$results += Invoke-SmokeCheck 'project#publicExampleHasAllSettingsAndNoAccountDefaults' {
    $example = Read-LocalEnvironment (Join-Path $root '.env.example')
    foreach ($key in @('IPTV_SERVER_URL','IPTV_USERNAME','IPTV_PASSWORD','IPTV_LEGACY_SERVER_URLS','FIRESTICK_IP','FIRESTICK_ADB_PORT','JAVA_HOME','ANDROID_HOME')) {
        Assert-Smoke ($example.ContainsKey($key))
    }
    Assert-Smoke (-not $example['IPTV_USERNAME'] -and -not $example['IPTV_PASSWORD'])
    Assert-Smoke ($example['IPTV_SERVER_URL'] -match 'provider\.example')
    foreach ($key in @('IPTV_LEGACY_SERVER_URLS','FIRESTICK_IP','JAVA_HOME','ANDROID_HOME')) {
        Assert-Smoke ([string]::IsNullOrEmpty($example[$key]))
    }
    Assert-Smoke ($example['FIRESTICK_ADB_PORT'] -eq '5555')
}
$results += Invoke-SmokeCheck 'project#nativeCommandFailureStopsCaller' {
    $tokens = $null; $parseErrors = $null
    $tree = [Management.Automation.Language.Parser]::ParseFile((Join-Path $root 'deploy-firestick.ps1'),[ref]$tokens,[ref]$parseErrors)
    Assert-Smoke ($parseErrors.Count -eq 0)
    $function = $tree.Find({param($node) $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq 'Invoke-CheckedNative'},$true)
    Assert-Smoke ($null -ne $function)
    # Evaluate only this trusted repository function, never the deploy script's main body.
    . ([scriptblock]::Create($function.Extent.Text))
    $rejected = $false
    try { Invoke-CheckedNative -Executable $env:ComSpec -Arguments @('/d','/c','exit','7') -Failure 'Synthetic failure.' }
    catch { $rejected = $true }
    Assert-Smoke $rejected
    Invoke-CheckedNative -Executable $env:ComSpec -Arguments @('/d','/c','exit','0') -Failure 'Unexpected failure.'
}
$results += Invoke-SmokeCheck 'project#gitIgnoresPrivateSettingsAndBuildArtifacts' {
    Push-Location -LiteralPath $root
    try {
        foreach ($path in @('.env','local.properties','.private/probe.log','app/build/probe.txt','probe.apk','probe.keystore')) {
            & git check-ignore --no-index -q -- $path
            Assert-Smoke ($LASTEXITCODE -eq 0)
        }
        & git check-ignore --no-index -q -- '.env.example'
        Assert-Smoke ($LASTEXITCODE -eq 1)
    } finally { Pop-Location }
}
$results += Invoke-SmokeCheck 'project#deployUpdatesNewIdentityAndLaunchesUnchangedActivity' {
    $result = Invoke-DeploymentFixture
    Assert-Smoke (-not $result.Rejected -and $result.Calls.Count -eq 3)
    Assert-Smoke (($result.Calls[0].arguments -join '|') -eq '-s|192.0.2.1:5555|install|-r|fixture.apk')
    Assert-Smoke (($result.Calls[1].arguments -join '|') -eq '-s|192.0.2.1:5555|shell|am|force-stop|com.inakara.aypitivi')
    Assert-Smoke (($result.Calls[2].arguments -join '|') -eq '-s|192.0.2.1:5555|shell|am|start|-W|-n|com.inakara.aypitivi/com.example.iptv.SplashActivity')
}
$results += Invoke-SmokeCheck 'project#failedInstallStopsBeforeFurtherDeviceChanges' {
    $result = Invoke-DeploymentFixture 'install-failure'
    Assert-Smoke ($result.Rejected -and $result.Calls.Count -eq 1)
    Assert-Smoke (($result.Calls[0].arguments -join '|') -eq '-s|192.0.2.1:5555|install|-r|fixture.apk')
}
$results += Invoke-SmokeCheck 'project#launchStatusErrorRejectsDeploymentEvenWithZeroExitCode' {
    $result = Invoke-DeploymentFixture 'launch-error'
    Assert-Smoke ($result.Rejected -and $result.Calls.Count -eq 3)
    Assert-Smoke ($result.Calls[2].arguments[-1] -eq 'com.inakara.aypitivi/com.example.iptv.SplashActivity')
}
if ($ResultPath) { [IO.File]::WriteAllText($ResultPath,(ConvertTo-Json -InputObject @($results) -Depth 4),[Text.UTF8Encoding]::new($false)) }
$passed=@($results | Where-Object status -eq 'Passed').Count
Write-Host "Project contracts: $passed/$($results.Count) passed."
if ($passed -ne $results.Count) { throw 'Project contract checks failed.' }

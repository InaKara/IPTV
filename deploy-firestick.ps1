[CmdletBinding()]
param(
    [switch]$BuildOnly,
    [switch]$InstallOnly,
    [switch]$Check
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot 'scripts/LocalEnvironment.ps1')

function Invoke-CheckedNative {
    param(
        [Parameter(Mandatory)][string]$Executable,
        [string[]]$Arguments = @(),
        [Parameter(Mandatory)][string]$Failure
    )
    & $Executable @Arguments
    if ($LASTEXITCODE -ne 0) { throw $Failure }
}

if ($BuildOnly -and $InstallOnly) { throw 'Choose either -BuildOnly or -InstallOnly.' }
if ($InstallOnly -and $Check) { throw '-Check requires a build; omit -InstallOnly.' }

$projectRoot = $PSScriptRoot
$config = Read-LocalEnvironment -Path (Join-Path $projectRoot '.env')
$apk = Join-Path $projectRoot 'app\build\outputs\apk\debug\app-debug.apk'
$previousJavaHome = $env:JAVA_HOME
$previousAndroidHome = $env:ANDROID_HOME

Push-Location -LiteralPath $projectRoot
try {
    $androidHome = $config['ANDROID_HOME']
    if (-not $androidHome) { $androidHome = $env:ANDROID_HOME }
    if (-not $androidHome) { $androidHome = $env:ANDROID_SDK_ROOT }
    if (-not $androidHome -and $env:LOCALAPPDATA) {
        $androidHome = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
    }
    if ($androidHome) { $env:ANDROID_HOME = $androidHome }

    $adb = $null
    $device = $null
    if (-not $BuildOnly) {
        $fireIp = $config['FIRESTICK_IP']
        $address = $null
        if (-not $fireIp -or -not [Net.IPAddress]::TryParse($fireIp, [ref]$address) -or
            $address.AddressFamily -ne [Net.Sockets.AddressFamily]::InterNetwork) {
            throw 'Set FIRESTICK_IP to the Firestick IPv4 address in .env.'
        }
        $port = 5555
        if ($config['FIRESTICK_ADB_PORT']) {
            if (-not [int]::TryParse($config['FIRESTICK_ADB_PORT'], [ref]$port) -or
                $port -lt 1 -or $port -gt 65535) { throw 'Invalid FIRESTICK_ADB_PORT in .env.' }
        }
        $device = "${fireIp}:$port"
        if ($androidHome) { $adb = Join-Path $androidHome 'platform-tools\adb.exe' }
        if (-not $adb -or -not (Test-Path -LiteralPath $adb -PathType Leaf)) {
            $adbCommand = Get-Command adb.exe -ErrorAction SilentlyContinue
            if (-not $adbCommand) { throw 'ADB not found. Set ANDROID_HOME in .env or install Platform Tools.' }
            $adb = $adbCommand.Source
        }
    }

    if (-not $InstallOnly) {
        $javaHome = $config['JAVA_HOME']
        if (-not $javaHome) { $javaHome = $env:JAVA_HOME }
        if (-not $javaHome) {
            $javaCommand = Get-Command java.exe -ErrorAction SilentlyContinue
            if ($javaCommand) { $javaHome = Split-Path (Split-Path $javaCommand.Source -Parent) -Parent }
        }
        if (-not $javaHome) { throw 'Set JAVA_HOME to a JDK 17 installation in .env.' }
        $java = Join-Path $javaHome 'bin\java.exe'
        if (-not (Test-Path -LiteralPath $java -PathType Leaf)) { throw 'JAVA_HOME does not contain bin/java.exe.' }
        # Java writes its version to stderr. Start-Process avoids PowerShell 5 treating it as an error.
        $versionFile = [IO.Path]::GetTempFileName()
        try {
            $javaProcess = Start-Process -FilePath $java -ArgumentList '-version' -Wait -PassThru -WindowStyle Hidden -RedirectStandardError $versionFile
            $javaVersion = [IO.File]::ReadAllText($versionFile)
            if ($javaProcess.ExitCode -ne 0 -or $javaVersion -notmatch 'version "17(?:\.|\")') {
                throw 'This project requires JDK 17. Correct JAVA_HOME in .env.'
            }
        } finally {
            Remove-Item -LiteralPath $versionFile -ErrorAction SilentlyContinue
        }
        $env:JAVA_HOME = $javaHome
        $tasks = @(':app:assembleDebug')
        if ($Check) {
            Invoke-CheckedNative -Executable 'powershell.exe' `
                -Arguments @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', (Join-Path $projectRoot 'test-smoke.ps1'), '-Lint') `
                -Failure 'Smoke tests or lint failed. Build/deployment was not continued.'
        }
        Write-Host 'Building Aypi Tivi...'
        Invoke-CheckedNative -Executable (Join-Path $projectRoot 'gradlew.bat') `
            -Arguments ($tasks + @('--console=plain')) -Failure 'Build or checks failed. Installation was not attempted.'
    }

    if (-not (Test-Path -LiteralPath $apk -PathType Leaf)) { throw 'Debug APK not found. Build it first.' }
    if ($BuildOnly) {
        Write-Host 'Build complete. APK: app/build/outputs/apk/debug/app-debug.apk'
        return
    }

    Write-Host 'Connecting to Firestick. Approve the debugging prompt on the TV if requested.'
    Invoke-CheckedNative -Executable $adb -Arguments @('connect', $device) -Failure 'Could not connect to the Firestick.'
    $stateOutput = & $adb -s $device get-state 2>&1
    if ($LASTEXITCODE -ne 0 -or ($stateOutput -join "`n").Trim() -ne 'device') {
        throw 'Firestick is not ready. Check its IP, ADB Debugging, network and TV authorization prompt.'
    }
    Write-Host 'Installing update (saved app data is kept)...'
    Invoke-CheckedNative -Executable $adb -Arguments @('-s', $device, 'install', '-r', $apk) `
        -Failure 'Installation failed. The app was not uninstalled or cleared.'
    Invoke-CheckedNative -Executable $adb -Arguments @('-s', $device, 'shell', 'am', 'force-stop', 'com.example.iptv') `
        -Failure 'Could not stop the previous app process.'
    $launchOutput = & $adb -s $device shell am start -W -n com.example.iptv/.SplashActivity 2>&1
    if ($LASTEXITCODE -ne 0 -or ($launchOutput -join "`n") -notmatch '(?m)^Status:\s*ok\s*$') {
        throw 'The update installed, but launch did not report success. Check the TV and device logs.'
    }
    Write-Host 'Installed and launched. Confirm login, picture, sound and remote controls on the TV.'
} finally {
    $env:JAVA_HOME = $previousJavaHome
    $env:ANDROID_HOME = $previousAndroidHome
    Pop-Location
}

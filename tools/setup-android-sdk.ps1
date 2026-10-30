#Requires -Version 5.1
<#
.SYNOPSIS
    Install a minimal Android SDK (command-line tools only) so the project builds.

.DESCRIPTION
    Installs cmdline-tools, platform-tools, platform and build-tools into -SdkRoot,
    accepts licenses, and sets ANDROID_HOME / ANDROID_SDK_ROOT for the current user.

    No Android Studio, no emulator. Android Studio can adopt this SDK later.

.EXAMPLE
    .\setup-android-sdk.ps1
    .\setup-android-sdk.ps1 -SdkRoot 'D:\tools\android-sdk' -ApiLevel 35 -BuildTools 35.0.0
#>
[CmdletBinding()]
param(
    [string]$SdkRoot = 'D:\tools\android-sdk',
    [string]$ApiLevel = '35',
    [string]$BuildTools = '35.0.0'
)

$ErrorActionPreference = 'Stop'
$LogPath = Join-Path $env:TEMP 'android-sdk-setup.log'

function Log {
    param([string]$Message)
    $line = '[{0}] {1}' -f (Get-Date -Format 'HH:mm:ss'), $Message
    Write-Host $line
    Add-Content -LiteralPath $LogPath -Value $line -Encoding UTF8
}

try {
    if (Test-Path -LiteralPath $LogPath) { Remove-Item -LiteralPath $LogPath -Force }

    if (-not $env:JAVA_HOME) { throw 'JAVA_HOME is not set; sdkmanager needs JDK 17+' }
    Log "JAVA_HOME=$env:JAVA_HOME"

    New-Item -ItemType Directory -Force -Path $SdkRoot | Out-Null
    $dl = Join-Path $SdkRoot '_download'
    New-Item -ItemType Directory -Force -Path $dl | Out-Null
    Log "SDK root: $SdkRoot"

    # 1. Find the current command-line tools archive name from Google's index.
    Log 'fetching repository index...'
    $indexPath = Join-Path $dl 'repository2-3.xml'
    & curl.exe -s --max-time 60 -o $indexPath 'https://dl.google.com/android/repository/repository2-3.xml'
    if (-not (Test-Path -LiteralPath $indexPath)) { throw 'repository index download failed' }
    $index = Get-Content -LiteralPath $indexPath -Raw
    $match = [regex]::Matches($index, 'commandlinetools-win-\d+_latest\.zip') | Select-Object -Last 1
    if (-not $match) { throw 'commandlinetools-win package not listed in index' }
    $zipName = $match.Value
    Log "package: $zipName"

    # 2. Download.
    $zipPath = Join-Path $dl $zipName
    if (-not (Test-Path -LiteralPath $zipPath)) {
        Log 'downloading command-line tools (~130 MB)...'
        & curl.exe -L --max-time 1800 --retry 2 -o $zipPath "https://dl.google.com/android/repository/$zipName"
        if (-not (Test-Path -LiteralPath $zipPath)) { throw 'download failed' }
    } else {
        Log 'archive already present, skipping download'
    }
    Log ('archive size: {0:N1} MB' -f ((Get-Item -LiteralPath $zipPath).Length / 1MB))

    # 3. Unpack into cmdline-tools\latest (sdkmanager expects this layout).
    Log 'extracting...'
    $unz = Join-Path $dl 'unz'
    if (Test-Path -LiteralPath $unz) { Remove-Item -Recurse -Force $unz }
    Expand-Archive -LiteralPath $zipPath -DestinationPath $unz -Force
    $inner = Get-ChildItem -LiteralPath $unz -Directory | Select-Object -First 1
    if (-not $inner) { throw 'unexpected archive layout' }
    $target = Join-Path $SdkRoot 'cmdline-tools\latest'
    if (Test-Path -LiteralPath $target) { Remove-Item -Recurse -Force $target }
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $target) | Out-Null
    Move-Item -LiteralPath $inner.FullName -Destination $target

    $sdkManager = Join-Path $target 'bin\sdkmanager.bat'
    if (-not (Test-Path -LiteralPath $sdkManager)) { throw "sdkmanager not found at $sdkManager" }
    Log "sdkmanager: $sdkManager"

    # 4. Accept licenses. Piping 'y' into a .bat through PowerShell does not reach
    #    stdin, so feed a file through cmd's '<' redirect instead.
    Log 'accepting licenses...'
    $yesFile = Join-Path $dl 'yes.txt'
    Set-Content -LiteralPath $yesFile -Value ((1..60 | ForEach-Object { 'y' }) -join "`r`n") -Encoding ASCII
    $licenseOut = & cmd.exe /c "`"$sdkManager`" --sdk_root=`"$SdkRoot`" --licenses < `"$yesFile`"" 2>&1
    $licenseOut | ForEach-Object { if ($_ -match 'accepted|Accept\?|licen') { Log "  $_" } }
    # sdkmanager prints "not accepted" *before* asking; only the final line proves success.
    if (($licenseOut -join ' ') -notmatch 'All SDK package licenses accepted') { throw 'license acceptance failed' }

    # 5. Install the packages the build needs.
    Log "installing platform-tools, platforms;android-$ApiLevel, build-tools;$BuildTools ..."
    & $sdkManager --sdk_root="$SdkRoot" 'platform-tools' "platforms;android-$ApiLevel" "build-tools;$BuildTools" 2>&1 |
        ForEach-Object { Log "  $_" }

    # 6. Persist environment for the current user.
    [Environment]::SetEnvironmentVariable('ANDROID_HOME', $SdkRoot, 'User')
    [Environment]::SetEnvironmentVariable('ANDROID_SDK_ROOT', $SdkRoot, 'User')
    $env:ANDROID_HOME = $SdkRoot
    $env:ANDROID_SDK_ROOT = $SdkRoot

    $userPath = [Environment]::GetEnvironmentVariable('Path', 'User')
    $parts = @($userPath -split ';' | Where-Object { $_ })
    foreach ($p in @((Join-Path $SdkRoot 'platform-tools'), (Join-Path $SdkRoot 'cmdline-tools\latest\bin'))) {
        if ($parts -notcontains $p) { $parts += $p }
        if (($env:Path -split ';') -notcontains $p) { $env:Path = $env:Path + ';' + $p }
    }
    [Environment]::SetEnvironmentVariable('Path', ($parts -join ';'), 'User')
    Log 'ANDROID_HOME / ANDROID_SDK_ROOT / Path updated for current user (restart shell to pick up)'

    # 7. Verify.
    Log '--- verify ---'
    $adb = Join-Path $SdkRoot 'platform-tools\adb.exe'
    if (Test-Path -LiteralPath $adb) {
        & $adb version 2>&1 | Select-Object -First 2 | ForEach-Object { Log "  $_" }
    } else {
        Log 'adb not found — packages may not have installed'
    }
    Log ('platforms dir: ' + ((Get-ChildItem -LiteralPath (Join-Path $SdkRoot 'platforms') -Directory -ErrorAction SilentlyContinue | ForEach-Object { $_.Name }) -join ', '))
    Log ('build-tools dir: ' + ((Get-ChildItem -LiteralPath (Join-Path $SdkRoot 'build-tools') -Directory -ErrorAction SilentlyContinue | ForEach-Object { $_.Name }) -join ', '))

    Log 'DONE'
}
catch {
    Log ('ERROR: ' + $_.Exception.Message)
    exit 1
}

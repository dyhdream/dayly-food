#Requires -Version 5.1
<#
.SYNOPSIS
    Cut a release: bump the version, build the APK, publish a GitHub Release.

.DESCRIPTION
    The app updates itself from the latest GitHub Release, so publishing a release IS
    the deploy step. Paths are derived from this script's location so the file stays
    pure ASCII (PowerShell 5.1 reads BOM-less .ps1 as ANSI and mangles non-ASCII paths).

.EXAMPLE
    .\tools\release.ps1 -Version 0.3.0 -Notes "- Added x"
    .\tools\release.ps1 -Version 0.3.1 -Notes "hotfix" -DryRun
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Version,
    [string]$Notes = "",
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'

if ($Version -notmatch '^\d+\.\d+\.\d+$') {
    throw "Version must look like 0.3.0 (got '$Version')"
}

$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
# Gradle/AGP and unit tests break on the CJK workspace path; build through a junction.
$buildRoot = 'D:\dev\dayly-food'
$jdk = 'D:\tools\jdk-21'
$sdk = 'D:\tools\android-sdk'
$gradle = 'D:\tools\gradle-8.11.1\bin\gradle.bat'

$env:JAVA_HOME = $jdk
$env:ANDROID_HOME = $sdk
$env:ANDROID_SDK_ROOT = $sdk
$env:GIT_TERMINAL_PROMPT = '0'
$env:PYTHONIOENCODING = 'utf-8'

$parts = $Version.Split('.')
$versionCode = [int]$parts[0] * 10000 + [int]$parts[1] * 100 + [int]$parts[2]

Write-Host "[1/6] bump to $Version (versionCode $versionCode)"
$gradleFile = Join-Path $repo 'app\build.gradle.kts'
if (-not (Test-Path -LiteralPath $gradleFile)) { throw "not found: $gradleFile" }
$content = Get-Content -LiteralPath $gradleFile -Raw
$content = [regex]::Replace($content, 'versionCode = \d+', "versionCode = $versionCode")
$content = [regex]::Replace($content, 'versionName = "[^"]*"', "versionName = `"$Version`"")
Set-Content -LiteralPath $gradleFile -Value $content -Encoding UTF8

Write-Host "[2/6] verify seed data"
# Dev mode gates on real errors only. `--release` additionally demands every brand row be
# verified with a source_url, which is only true right before a store submission.
& python (Join-Path $repo 'tools\seed_lint.py') --dir (Join-Path $repo 'tools\seed')
if ($LASTEXITCODE -ne 0) { throw 'seed data has errors; fix them before releasing' }
$strict = & python (Join-Path $repo 'tools\seed_lint.py') --dir (Join-Path $repo 'tools\seed') --release 2>&1
if ($LASTEXITCODE -ne 0) {
    $pending = ($strict | Select-String -Pattern '^ERROR' | Measure-Object).Count
    Write-Warning "$pending brand row(s) still unreviewed; fine for a test build, blocking for a store release"
}

if (-not (Test-Path -LiteralPath $buildRoot)) {
    throw "Build path $buildRoot is missing. Create it with: mklink /J `"$buildRoot`" `"$repo`""
}

Write-Host "[3/6] build release APK"
Push-Location $buildRoot
try {
    & $gradle assembleRelease testDebugUnitTest
    if ($LASTEXITCODE -ne 0) { throw 'gradle build failed' }
}
finally {
    Pop-Location
}

$apk = Join-Path $buildRoot 'app\build\outputs\apk\release\app-release.apk'
if (-not (Test-Path -LiteralPath $apk)) { throw "APK not found: $apk" }
$dist = Join-Path $repo 'dist'
New-Item -ItemType Directory -Force -Path $dist | Out-Null
$staged = Join-Path $dist "dayly-food-$Version-release.apk"
Copy-Item -LiteralPath $apk -Destination $staged -Force
$sizeMb = [math]::Round((Get-Item -LiteralPath $staged).Length / 1MB, 2)
Write-Host "      APK: $staged ($sizeMb MB)"

if ($DryRun) {
    Write-Host '[DryRun] stopping before commit/publish'
    return
}

Write-Host '[4/6] commit and push'
Push-Location $repo
try {
    git add -A
    git -c user.name='xingyu' -c user.email='2796649514@qq.com' commit -m "chore(release): v$Version" -m $Notes
    if ($LASTEXITCODE -ne 0) { throw 'git commit failed' }
    git push origin main
    if ($LASTEXITCODE -ne 0) { throw 'git push failed' }
}
finally {
    Pop-Location
}

Write-Host "[5/6] publish GitHub Release v$Version"
if ([string]::IsNullOrWhiteSpace($Notes)) { $Notes = "v$Version" }

# Re-running a release must not fail just because the tag does not exist yet.
$previous = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
gh release view "v$Version" 2>$null | Out-Null
if ($LASTEXITCODE -eq 0) {
    Write-Host '      replacing existing release'
    gh release delete "v$Version" --yes 2>$null | Out-Null
}
gh release create "v$Version" $staged --title "v$Version" --notes $Notes --latest
$created = $LASTEXITCODE
$ErrorActionPreference = $previous
if ($created -ne 0) { throw 'gh release create failed' }

Write-Host '[6/6] done'
Write-Host "      Installed clients will see v$Version on their next update check."

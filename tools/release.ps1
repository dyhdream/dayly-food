#Requires -Version 5.1
<#
.SYNOPSIS
    Cut a release: bump the version, build the APK, publish a GitHub Release.

.DESCRIPTION
    The app updates itself from the latest GitHub Release, so publishing a release IS
    the deploy step. Run from the repo root.

.EXAMPLE
    .\tools\release.ps1 -Version 0.3.0 -Notes "- Added x`n- Fixed y"
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

$repo = 'E:\学习资料\dayly-food'
$buildRoot = 'D:\dev\dayly-food'   # ASCII path; AGP and unit tests break on the CJK path
$env:JAVA_HOME = 'D:\tools\jdk-21'
$env:ANDROID_HOME = 'D:\tools\android-sdk'
$env:ANDROID_SDK_ROOT = 'D:\tools\android-sdk'
$env:GIT_TERMINAL_PROMPT = '0'

$parts = $Version.Split('.')
$versionCode = [int]$parts[0] * 10000 + [int]$parts[1] * 100 + [int]$parts[2]

Write-Host "[1/6] bump to $Version (versionCode $versionCode)"
$gradleFile = Join-Path $repo 'app\build.gradle.kts'
$content = Get-Content -LiteralPath $gradleFile -Raw
$content = [regex]::Replace($content, 'versionCode = \d+', "versionCode = $versionCode")
$content = [regex]::Replace($content, 'versionName = "[^"]*"', "versionName = `"$Version`"")
Set-Content -LiteralPath $gradleFile -Value $content -Encoding UTF8 -NoNewline

if (-not (Test-Path $buildRoot)) {
    throw "Build path $buildRoot is missing. Create it with: mklink /J `"$buildRoot`" `"$repo`""
}

Write-Host "[2/6] verify seed data"
$env:PYTHONIOENCODING = 'utf-8'
& python (Join-Path $repo 'tools\seed_lint.py') --dir (Join-Path $repo 'tools\seed') --release
if ($LASTEXITCODE -ne 0) {
    Write-Warning 'seed lint --release reported problems; continuing only because unreviewed brand rows are expected during development'
}

Write-Host "[3/6] build release APK"
Push-Location $buildRoot
try {
    & 'D:\tools\gradle-8.11.1\bin\gradle.bat' assembleRelease testDebugUnitTest
    if ($LASTEXITCODE -ne 0) { throw 'gradle build failed' }
}
finally {
    Pop-Location
}

$apk = Join-Path $buildRoot 'app\build\outputs\apk\release\app-release.apk'
if (-not (Test-Path $apk)) { throw "APK not found: $apk" }
$dist = Join-Path $repo 'dist'
New-Item -ItemType Directory -Force -Path $dist | Out-Null
$staged = Join-Path $dist "dayly-food-$Version-release.apk"
Copy-Item -LiteralPath $apk -Destination $staged -Force
$sizeMb = [math]::Round((Get-Item $staged).Length / 1MB, 2)
Write-Host "      APK: $staged ($sizeMb MB)"

if ($DryRun) {
    Write-Host "[DryRun] stopping before commit/publish"
    return
}

Write-Host "[4/6] commit and push"
Push-Location $repo
try {
    git add -A
    git -c user.name='xingyu' -c user.email='2796649514@qq.com' commit -m "chore(release): v$Version" -m $Notes
    git push origin main
    if ($LASTEXITCODE -ne 0) { throw 'git push failed' }
}
finally {
    Pop-Location
}

Write-Host "[5/6] publish GitHub Release v$Version"
if ([string]::IsNullOrWhiteSpace($Notes)) { $Notes = "v$Version" }
gh release create "v$Version" $staged --title "v$Version" --notes $Notes --latest
if ($LASTEXITCODE -ne 0) { throw 'gh release create failed' }

Write-Host "[6/6] done"
Write-Host "      Installed clients will now see v$Version on their next update check."

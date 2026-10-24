<#
.SYNOPSIS
    Make GitHub work on this Windows machine without fighting Watt Toolkit.

.DESCRIPTION
    Root cause on this host:
      Watt Toolkit (Steam++ / Steam++.Accelerator) writes a "# Steam++ Start/End"
      block into C:\Windows\System32\drivers\etc\hosts that maps every GitHub
      domain to 127.0.0.1, so all GitHub traffic is forced through its local MITM
      reverse proxy. Two consequences:
        1. The MITM certificate carries no revocation information, so Windows
           schannel rejects it -> curl / Invoke-WebRequest / .NET fail with
           CRYPT_E_NO_REVOCATION_CHECK (surfaces as "000" / connection errors).
        2. Watt rewrites hosts whenever it feels like it, so editing hosts while
           it runs gets reverted within the same second.

    This script therefore defaults to a *client-side* mode that needs no admin
    rights and survives Watt rewriting hosts:
        - git:   http.schannelCheckRevoke=false, init.defaultBranch=main
        - curl:  ssl-no-revoke in the config file curl actually reads
                 (respects XDG_CONFIG_HOME, which is why plain _curlrc is ignored
                 on this machine)
    gh (Go networking, own TLS stack) already works through the proxy once the
    token is stored; the script verifies it.

    -Mode hosts is only for when you want GitHub to bypass Watt entirely. Turn
    off "GitHub" acceleration inside Watt first, otherwise Watt will re-add the
    redirection immediately.

.PARAMETER Mode
    client (default) | hosts | watt

.PARAMETER Diagnose
    Report state only. Makes no changes, needs no admin.

.PARAMETER Restore
    Restore hosts from the newest backup this script took.

.EXAMPLE
    .\github-connect.ps1                  # client-side compat, no UAC
    .\github-connect.ps1 -Diagnose
    .\github-connect.ps1 -Mode hosts      # UAC; only after disabling Watt GitHub accel
    .\github-connect.ps1 -Restore
#>
[CmdletBinding()]
param(
    [ValidateSet('client', 'hosts', 'watt')]
    [string]$Mode = 'client',
    [switch]$Diagnose,
    [switch]$Restore,
    [switch]$NoElevate,
    [string]$LogPath = (Join-Path $env:TEMP 'github-connect.log')
)

$ErrorActionPreference = 'Stop'
$HostsPath = Join-Path $env:SystemRoot 'System32\drivers\etc\hosts'
$StateDir = Join-Path $env:ProgramData 'github-connect'
$BlockStart = '# >>> github-connect managed (pinned IPs) >>>'
$BlockEnd = '# <<< github-connect managed <<<'
$StaleTag = '#[gh-fix] '
$CurlRcBody = @(
    '# managed by dayly-food tools/github-connect.ps1'
    '# Watt Toolkit MITM cert has no revocation info; schannel rejects it.'
    'ssl-no-revoke'
) -join "`n"

function Write-Log {
    param([string]$Message, [string]$Level = 'INFO')
    $line = '[{0}] {1,-5} {2}' -f (Get-Date -Format 'HH:mm:ss'), $Level, $Message
    Write-Host $line
    try { Add-Content -LiteralPath $LogPath -Value $line -Encoding UTF8 } catch { }
}

function Test-IsAdmin {
    $id = [Security.Principal.WindowsIdentity]::GetCurrent()
    return (New-Object Security.Principal.WindowsPrincipal($id)).IsInRole(
        [Security.Principal.WindowsBuiltInRole]::Administrator)
}

function Test-WattRunning {
    return [bool](Get-Process -ErrorAction SilentlyContinue |
        Where-Object { $_.ProcessName -match 'Steam\+\+|Accelerator' })
}

function Resolve-DoH {
    param([string]$Name)
    foreach ($u in @(
            "https://dns.alidns.com/resolve?name=$Name&type=A",
            "https://doh.pub/dns-query?name=$Name&type=A")) {
        try {
            $raw = & curl.exe -s --max-time 8 -H 'accept: application/dns-json' $u 2>$null
            $text = ($raw -join '')
            if ($text -match '"data"\s*:\s*"(\d+\.\d+\.\d+\.\d+)"') { return $Matches[1] }
        } catch { }
    }
    return $null
}

# ------------------------------------------------------------ client-side mode

function Set-ClientCompat {
    Write-Log '--- git ---'
    & git config --global http.schannelCheckRevoke false
    & git config --global init.defaultBranch main
    Write-Log ("http.schannelCheckRevoke = " + (& git config --global --get http.schannelCheckRevoke)) 'OK'
    Write-Log ("init.defaultBranch       = " + (& git config --global --get init.defaultBranch)) 'OK'

    Write-Log '--- curl ---'
    $paths = @("$env:USERPROFILE\.curlrc", "$env:USERPROFILE\_curlrc")
    if ($env:XDG_CONFIG_HOME) { $paths += (Join-Path $env:XDG_CONFIG_HOME 'curlrc') }
    foreach ($p in $paths) {
        $dir = Split-Path $p -Parent
        if (-not $dir -or -not (Test-Path -LiteralPath $dir)) { continue }
        [IO.File]::WriteAllText($p, $CurlRcBody + "`r`n", (New-Object Text.UTF8Encoding($false)))
        Write-Log "curl config written: $p"
    }

    Write-Log '--- gh ---'
    $status = (& gh auth status 2>&1) -join ' '
    if ($status -match 'Logged in') {
        Write-Log 'gh already authenticated' 'OK'
    } else {
        Write-Log 'gh is NOT authenticated. Run: gh auth login' 'WARN'
    }
}

# --------------------------------------------------------------- hosts modes

function Get-HostsLines { return @([IO.File]::ReadAllLines($HostsPath)) }

function Save-HostsLines {
    param([string[]]$Lines)
    [IO.File]::WriteAllText($HostsPath, (($Lines -join "`r`n") + "`r`n"), (New-Object Text.UTF8Encoding($false)))
    & ipconfig /flushdns 2>&1 | Out-Null
}

function Backup-Hosts {
    if (-not (Test-Path -LiteralPath $StateDir)) { New-Item -ItemType Directory -Path $StateDir -Force | Out-Null }
    $dest = Join-Path $StateDir ('hosts.bak.' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
    Copy-Item -LiteralPath $HostsPath -Destination $dest -Force
    Write-Log "backup: $dest" 'OK'
}

function Update-HostsClean {
    param([switch]$Pinned)
    $out = New-Object 'System.Collections.Generic.List[string]'
    $removed = 0
    $stale = 0
    foreach ($ln in (Get-HostsLines)) {
        if ($ln -eq $BlockStart -or $ln -eq $BlockEnd) { continue }
        if ($ln.StartsWith($StaleTag)) { $stale++; continue }          # drop old leftovers
        if ($ln -match '^\s*127\.0\.0\.1\s+(\S+)') {
            if ($Matches[1].ToLowerInvariant() -match 'github') { $removed++; continue }
        }
        $out.Add($ln)
    }
    Write-Log "removed $removed live redirect(s), $stale stale line(s)"
    if ($Pinned) {
        foreach ($d in 'github.com', 'api.github.com', 'codeload.github.com', 'raw.githubusercontent.com', 'objects.githubusercontent.com', 'gist.github.com') {
            $ip = Resolve-DoH -Name $d
            if ($ip) { $out.Add("$ip $d"); Write-Log "pinned $d -> $ip" }
            else { Write-Log "DoH failed for $d" 'WARN' }
        }
        $out.Add($BlockEnd)
        $out.Insert($out.Count - 1, $BlockStart)
    }
    Save-HostsLines -Lines $out.ToArray()
}

function Update-HostsWatt {
    $out = New-Object 'System.Collections.Generic.List[string]'
    $n = 0
    foreach ($ln in (Get-HostsLines)) {
        if ($ln -eq $BlockStart -or $ln -eq $BlockEnd) { continue }
        if ($ln.StartsWith($StaleTag)) { $out.Add($ln.Substring($StaleTag.Length)); $n++; continue }
        $out.Add($ln)
    }
    Write-Log "restored $n line(s) to 127.0.0.1 (Watt path)"
    Save-HostsLines -Lines $out.ToArray()
}

function Restore-Backup {
    if (-not (Test-Path -LiteralPath $StateDir)) { throw "no backup dir: $StateDir" }
    $bak = Get-ChildItem -LiteralPath $StateDir -Filter 'hosts.bak.*' |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (-not $bak) { throw "no backup in $StateDir" }
    Copy-Item -LiteralPath $bak.FullName -Destination $HostsPath -Force
    & ipconfig /flushdns 2>&1 | Out-Null
    Write-Log "restored $($bak.FullName)" 'OK'
}

# ---------------------------------------------------------------- diagnostics

function Invoke-Diagnose {
    $watt = Test-WattRunning
    Write-Log ("Watt Toolkit running: " + $watt)

    Write-Log '--- resolution ---'
    $redirected = 0
    foreach ($d in 'github.com', 'api.github.com', 'raw.githubusercontent.com') {
        $ip = '?'
        try {
            $ip = (([Net.Dns]::GetHostAddresses($d) |
                Where-Object { $_.AddressFamily -eq 'InterNetwork' } |
                Select-Object -First 1).IPAddressToString)
        } catch { }
        if ($ip -eq '127.0.0.1') { $redirected++ }
        Write-Log ("{0,-26} -> {1}" -f $d, $ip)
    }
    if ($redirected -gt 0 -and -not $watt) {
        Write-Log 'GITHUB REDIRECTED BUT WATT IS NOT RUNNING -> broken; run -Mode hosts' 'FAIL'
    }
    if ($redirected -gt 0 -and $watt) {
        Write-Log 'redirected to Watt proxy (expected; client mode compensates)' 'WARN'
    }

    Write-Log '--- https ---'
    foreach ($u in 'https://api.github.com/', 'https://raw.githubusercontent.com/', 'https://github.com/') {
        $r = & curl.exe -s -o NUL -w '%{http_code} %{time_total}s' --max-time 12 $u 2>$null
        $code = ($r -join ' ')
        $lvl = 'INFO'; if ($code -notmatch '^[23]') { $lvl = 'FAIL' }
        Write-Log ("{0,-40} {1}" -f $u, $code) $lvl
    }

    Write-Log '--- git ---'
    $env:GIT_TERMINAL_PROMPT = '0'
    $sw = [Diagnostics.Stopwatch]::StartNew()
    $gr = & git ls-remote --heads https://github.com/android/skills.git 2>&1
    $sw.Stop()
    if ($LASTEXITCODE -eq 0) {
        Write-Log ("git ls-remote OK ({0} ms)" -f $sw.ElapsedMilliseconds) 'OK'
    } else {
        Write-Log ('git ls-remote FAILED: ' + ((@($gr) -join ' ') -replace '\s+', ' ')) 'FAIL'
    }

    Write-Log '--- gh ---'
    $gs = ((& gh auth status 2>&1) -join ' ') -replace '\s+', ' '
    if ($gs -match 'Logged in') {
        $who = ''
        try { $who = (& gh api user 2>&1 | ConvertFrom-Json).login } catch { }
        Write-Log "gh OK (login=$who)" 'OK'
    } else { Write-Log $gs 'WARN' }
}

# ---------------------------------------------------------------- entry point

try {
    if ($Diagnose) { Invoke-Diagnose; exit 0 }

    if ($Restore) {
        if (Test-IsAdmin) { Restore-Backup } else {
            $p = Start-Process powershell.exe -Verb RunAs -Wait -PassThru -ArgumentList @(
                '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', "`"$PSCommandPath`"", '-Restore', '-NoElevate')
            exit $p.ExitCode
        }
        exit 0
    }

    if ($Mode -eq 'client') {
        Set-ClientCompat
        Write-Log ''
        Invoke-Diagnose
        Write-Log ''
        Write-Log 'permanent clean fix (optional):' 
        Write-Log '  1) open Watt Toolkit -> 网络加速 -> uncheck GitHub (加速服务/自定义)'
        Write-Log '  2) then run: .\github-connect.ps1 -Mode hosts'
        Write-Log '  That removes Watt from the GitHub path entirely, so nothing breaks when Watt exits.'
        exit 0
    }

    # hosts / watt need admin
    if (-not (Test-IsAdmin)) {
        if ($NoElevate) { Write-Log 'need elevation to edit hosts' 'FAIL'; exit 2 }
        Write-Log 'requesting elevation (UAC prompt)...'
        if (Test-Path -LiteralPath $LogPath) { Remove-Item -LiteralPath $LogPath -Force -ErrorAction SilentlyContinue }
        $args2 = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', "`"$PSCommandPath`"",
            '-NoElevate', '-LogPath', "`"$LogPath`"", '-Mode', $Mode)
        $proc = Start-Process powershell.exe -ArgumentList $args2 -Verb RunAs -Wait -PassThru
        if (Test-Path -LiteralPath $LogPath) { Get-Content -LiteralPath $LogPath -Encoding UTF8 | ForEach-Object { Write-Host $_ } }
        exit $proc.ExitCode
    }

    if (Test-WattRunning -and $Mode -eq 'hosts') {
        Write-Log 'Watt is running: it will re-add GitHub redirects and undo this. Disable GitHub acceleration in Watt first.' 'WARN'
    }
    Backup-Hosts
    if ($Mode -eq 'watt') { Update-HostsWatt } else { Update-HostsClean }
    Write-Log "hosts updated (mode=$Mode)" 'OK'
    Invoke-Diagnose
    exit 0
} catch {
    Write-Log ('ERROR: ' + $_.Exception.Message) 'FAIL'
    exit 1
}

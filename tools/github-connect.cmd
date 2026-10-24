@echo off
rem Wrapper so the fix can be re-run with a double click.
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0github-connect.ps1" %*
endlocal

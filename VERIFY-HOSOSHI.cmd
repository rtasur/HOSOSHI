@echo off
setlocal
cd /d "%~dp0"
title HOSOSHI - Verification

echo.
echo ============================================================
echo                  HOSOSHI VERIFICATION
echo ============================================================
echo.

where powershell.exe >nul 2>&1
if errorlevel 1 (
    echo ERROR: Windows PowerShell was not found.
    echo.
    pause
    exit /b 1
)

echo Running verification...
echo.

powershell.exe -NoProfile -ExecutionPolicy Bypass ^
  -File "%~dp0scripts\verify-hoshoshi.ps1"

set "RC=%ERRORLEVEL%"

echo.
echo ============================================================
if "%RC%"=="0" (
    echo              HOSOSHI VERIFICATION PASSED
) else (
    echo              HOSOSHI VERIFICATION FAILED
)
echo ============================================================
echo.

echo Exit code: %RC%
echo.
echo The verification window will remain open so you can review
echo every PASS and FAIL result.
echo.
pause

exit /b %RC%
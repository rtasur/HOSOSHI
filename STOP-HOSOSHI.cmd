@echo off
setlocal
cd /d "%~dp0"
title HOSOSHI - Local Stop
docker compose down
echo.
echo HOSOSHI containers stopped.
echo Database volumes were preserved.
pause
endlocal

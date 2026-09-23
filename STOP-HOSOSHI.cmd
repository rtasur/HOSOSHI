@echo off
setlocal EnableExtensions
cd /d "%~dp0"
title HOSOSHI - Stop

echo.
echo ============================================================
echo                    HOSOSHI STOP
echo ============================================================
echo.

echo Stopping HOSOSHI containers (database volumes are preserved)...
echo.

REM Docker Compose requires these values even for "down"
REM because they are used during Compose-file interpolation.
REM Temporary values are sufficient because no container is started.
set "JWT_SECRET=hososhi-stop-only-placeholder-1234567890"
set "API_ENCRYPTION_KEY=hososhi-stop-only-placeholder-1234567890"

docker compose down --remove-orphans

if errorlevel 1 (
    echo.
    echo ============================================================
    echo              HOSOSHI STOP FAILED
    echo ============================================================
    echo.
    echo Docker Compose could not stop the application.
    echo Check the error above.
    echo.
    pause
    exit /b 1
)

echo.
echo ============================================================
echo                  HOSOSHI STOPPED
echo ============================================================
echo.
echo PostgreSQL and Neo4j volumes were preserved.
echo Your application data has NOT been deleted.
echo.
pause
endlocal
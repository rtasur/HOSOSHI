@echo off
setlocal EnableExtensions EnableDelayedExpansion
cd /d "%~dp0"
title HOSOSHI - Local Launcher

echo.
echo ============================================================
echo                    HOSOSHI LOCAL START
echo ============================================================
echo.

REM ---- Check Docker CLI ----
where docker >nul 2>&1
if errorlevel 1 (
    echo ERROR: Docker CLI was not found.
    echo Install Docker Desktop for Windows and run this file again.
    pause
    exit /b 1
)

REM ---- Check / start Docker Desktop ----
docker info >nul 2>&1
if errorlevel 1 (
    echo Docker Engine is not running.
    echo Attempting to start Docker Desktop...

    if exist "%ProgramFiles%\Docker\Docker\Docker Desktop.exe" (
        start "" "%ProgramFiles%\Docker\Docker\Docker Desktop.exe"
    ) else if exist "%LocalAppData%\Programs\Docker Desktop\Docker Desktop.exe" (
        start "" "%LocalAppData%\Programs\Docker Desktop\Docker Desktop.exe"
    ) else (
        echo ERROR: Docker Desktop executable was not found.
        echo Open Docker Desktop manually, wait for it to become Running, then retry.
        pause
        exit /b 1
    )

    echo Waiting for Docker Engine...
    set /a WAIT=0
    :WAIT_DOCKER
    timeout /t 2 /nobreak >nul
    docker info >nul 2>&1
    if not errorlevel 1 goto DOCKER_READY
    set /a WAIT+=2
    if !WAIT! GEQ 120 (
        echo.
        echo ERROR: Docker Engine did not become ready within 120 seconds.
        echo Open Docker Desktop and make sure the engine is Running.
        pause
        exit /b 1
    )
    goto WAIT_DOCKER
)

:DOCKER_READY
echo Docker Engine: READY
echo.

echo Building and starting HOSOSHI...
echo The first run may take a few minutes.
echo Local defaults are available; copy .env.example to .env to customize them.
echo.

docker compose build --progress=plain
if errorlevel 1 (
    echo.
    echo ============================================================
    echo HOSOSHI BUILD FAILED - NO RUNNING CONTAINERS WERE REPLACED
    echo ============================================================
    echo.
    echo Review the build error above.
    pause
    exit /b 1
)

docker compose up -d --remove-orphans
if errorlevel 1 (
    echo.
    echo ============================================================
    echo HOSOSHI STARTUP FAILED
    echo ============================================================
    echo.
    docker compose ps
    echo.
    echo ---- Backend log ----
    docker compose logs --tail=80 backend
    echo.
    echo Review the error above and keep this window open if needed.
    pause
    exit /b 1
)

echo.
echo Waiting for HOSOSHI services...
set /a WAIT=0
:WAIT_SERVICES
curl.exe -fsS http://localhost:8081/api/v1/health >nul 2>&1
if errorlevel 1 goto WAIT_MORE
curl.exe -fsS http://localhost:5173 >nul 2>&1
if not errorlevel 1 goto READY
:WAIT_MORE
timeout /t 2 /nobreak >nul
set /a WAIT+=2
if !WAIT! GEQ 120 goto READY_TIMEOUT
goto WAIT_SERVICES

:READY_TIMEOUT
echo.
echo Containers started, but the browser endpoint did not respond within 120 seconds.
echo Check status with: docker compose ps
echo Check backend with: docker compose logs --tail=100 backend
echo.
docker compose ps
pause
exit /b 1

:READY
echo.
echo ============================================================
echo                    HOSOSHI IS READY
echo ============================================================
echo Frontend : http://localhost:5173
echo Backend  : http://localhost:8081
echo Swagger  : http://localhost:8081/swagger-ui/index.html
echo Neo4j    : http://localhost:7474
echo.
echo Local administrator: admin / 123456
echo.
start "" http://localhost:5173
echo You can close this window. Docker containers will keep running.
echo.
pause
endlocal

@echo off
setlocal enabledelayedexpansion
title KEC VAC - Stop MySQL 8 Server (Port 3307)
cd /d "%~dp0\.."

set "MYSQL_BIN=C:\Program Files\MySQL\MySQL Server 8.0\bin"

echo ===============================================================================
echo          KEC VAC DATABASE HOST - STOP STANDALONE MYSQL 8 (PORT 3307)
echo ===============================================================================

set "TARGET_PID="
for /f "tokens=5" %%a in ('netstat -ano ^| findstr /R /C:":3307 .*LISTENING"') do (
    set "TARGET_PID=%%a"
)

if not defined TARGET_PID (
    echo [INFO] No process is currently listening on Port 3307.
    goto finished
)

echo [INFO] Found process listening on Port 3307 (PID: %TARGET_PID%).
echo [INFO] Requesting clean shutdown via mysqladmin...
if exist "%MYSQL_BIN%\mysqladmin.exe" (
    "%MYSQL_BIN%\mysqladmin.exe" -P 3307 -u root shutdown 2>nul
)

rem Check if still alive after 2 seconds
ping -n 3 127.0.0.1 >nul
netstat -ano | findstr /R /C:":3307 .*LISTENING" >nul
if !errorlevel! equ 0 (
    echo [INFO] Process still active. Force terminating PID %TARGET_PID%...
    taskkill /F /PID %TARGET_PID% >nul 2>&1
)

echo [SUCCESS] MySQL Server on Port 3307 has been stopped!

:finished
echo.
echo Current Port 3307 status:
netstat -ano | findstr /R /C:":3307 "
if !errorlevel! neq 0 (
    echo [CONFIRMED] Port 3307 is completely free.
)
echo ===============================================================================

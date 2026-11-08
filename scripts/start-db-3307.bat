@echo off
setlocal enabledelayedexpansion
title KEC VAC - Start MySQL 8 Server (Port 3307)
cd /d "%~dp0\.."

set "MYSQL_BIN=C:\Program Files\MySQL\MySQL Server 8.0\bin"
set "DATA_DIR=E:\mysql-remote-data"
set "CONFIG_FILE=E:\kec-vac-engine\config\my-3307.ini"
set "SCHEMA_FILE=E:\kec-vac-engine\sql\kec_vac_schema.sql"
set "SETUP_SQL=E:\kec-vac-engine\scripts\setup-db-3307.sql"

echo ===============================================================================
echo          KEC VAC DATABASE HOST - START STANDALONE MYSQL 8 (PORT 3307)
echo ===============================================================================

rem Check if MySQL binary exists
if not exist "%MYSQL_BIN%\mysqld.exe" (
    echo [ERROR] MySQL Server binary not found at: %MYSQL_BIN%\mysqld.exe
    pause
    exit /b 1
)

rem Check if port 3307 is already listening
netstat -ano | findstr /R /C:":3307 .*LISTENING" >nul
if !errorlevel! equ 0 (
    echo [INFO] MySQL is ALREADY running and listening on Port 3307!
    goto verify
)

set "NEED_INIT=0"
if not exist "%DATA_DIR%\mysql" (
    echo [INFO] Data directory not found. Initialising new datadir at %DATA_DIR%...
    if not exist "%DATA_DIR%" mkdir "%DATA_DIR%"
    "%MYSQL_BIN%\mysqld.exe" --initialize-insecure --datadir="%DATA_DIR%" --console
    if !errorlevel! neq 0 (
        echo [ERROR] Database initialisation failed!
        pause
        exit /b 1
    )
    echo [SUCCESS] Datadir initialised successfully!
    set "NEED_INIT=1"
)

echo [INFO] Launching MySQL 8 on Port 3307 in a separate window...
start "KEC VAC MySQL 3307 Server" "%MYSQL_BIN%\mysqld.exe" --defaults-file="%CONFIG_FILE%" --console

rem Wait up to 10 seconds for the server to become ready
echo [INFO] Waiting for MySQL Server on port 3307 to become ready...
set /a ATTEMPTS=0
:wait_loop
set /a ATTEMPTS+=1
ping -n 2 127.0.0.1 >nul
netstat -ano | findstr /R /C:":3307 .*LISTENING" >nul
if !errorlevel! equ 0 (
    echo [SUCCESS] Port 3307 is now open and LISTENING!
    goto post_start
)
if !ATTEMPTS! lss 10 goto wait_loop

echo [WARNING] Port 3307 did not report LISTENING within 10 seconds.
goto verify

:post_start
if "!NEED_INIT!"=="1" (
    echo [INFO] Importing database schema from %SCHEMA_FILE%...
    "%MYSQL_BIN%\mysql.exe" -P 3307 -u root < "%SCHEMA_FILE%"
    echo [INFO] Creating user vacuser and applying security grants...
    "%MYSQL_BIN%\mysql.exe" -P 3307 -u root < "%SETUP_SQL%"
    echo [SUCCESS] Database kec_vac and user vacuser configured!
)

:verify
echo.
echo ===============================================================================
echo                           PORT 3307 LISTENER STATUS
echo ===============================================================================
netstat -ano | findstr /R /C:":3307 "
echo.
echo [TEST] Testing connection as vacuser...
"%MYSQL_BIN%\mysql.exe" -h 127.0.0.1 -P 3307 -u vacuser -p"VacSecurePass@2026" -D kec_vac -e "SHOW TABLES; SELECT USER(), CURRENT_USER(), @@version;"
echo.
echo ===============================================================================
echo MySQL 8 on Port 3307 is ready for remote connections!
echo To stop this instance at any time, run: scripts\stop-db-3307.bat
echo ===============================================================================

@echo off
setlocal
title KEC VAC - Remote Connection Probe

set "DEFAULT_IP=192.168.29.137"
set "DEFAULT_PORT=3307"

if not "%~1"=="" set "TARGET_IP=%~1" else set "TARGET_IP=%DEFAULT_IP%"
if not "%~2"=="" set "TARGET_PORT=%~2" else set "TARGET_PORT=%DEFAULT_PORT%"

echo ===============================================================================
echo            KEC VAC REMOTE CONNECTION CLIENT PROBE
echo ===============================================================================
echo Target DB Host : %TARGET_IP%
echo Target DB Port : %TARGET_PORT%
echo.

echo [1/2] Probing TCP port reachability...
powershell -Command "Test-NetConnection -ComputerName '%TARGET_IP%' -Port %TARGET_PORT%"

echo.
echo [2/2] Attempting JDBC Self-Test using config/db-remote.properties...
java -cp "bin;lib/mysql-connector-j-8.0.33.jar" DBConnection --remote

echo.
echo ===============================================================================
pause

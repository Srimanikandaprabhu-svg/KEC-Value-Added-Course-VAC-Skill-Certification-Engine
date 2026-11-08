@echo off
setlocal enabledelayedexpansion
title KEC VAC - LAN IP Discovery and Remote Client Test Guide

echo ===============================================================================
echo            KEC VAC ENGINE - DATABASE HOST LAN IP DISCOVERY TOOL
echo ===============================================================================
echo.
echo Searching for active IPv4 LAN addresses...
echo.

set "LAN_IP="
for /f "tokens=2 delims=:" %%a in ('ipconfig ^| findstr /c:"IPv4 Address"') do (
    set "IP=%%a"
    set "IP=!IP: =!"
    rem Ignore APIPA 169.254 addresses
    echo !IP! | findstr /v "169.254" >nul
    if !errorlevel! equ 0 (
        if not defined LAN_IP set "LAN_IP=!IP!"
        echo   [FOUND] Active IPv4 Address: !IP!
    )
)

if not defined LAN_IP set "LAN_IP=192.168.29.137"

echo.
echo ===============================================================================
echo                 INSTRUCTIONS FOR TESTING FROM ANOTHER DEVICE
echo                 (Friend's Laptop, Phone via Termux, or Second VM)
echo ===============================================================================
echo.
echo Database Host LAN IP : %LAN_IP%
echo Database Host Port   : 3307
echo Dedicated User       : vacuser
echo Target Database      : kec_vac
echo.
echo Ensure both devices are connected to the SAME Wi-Fi or Mobile Hotspot!
echo.
echo --- TEST 1: Quick Port Reachability Test (from another Windows Laptop) ---
echo Run in PowerShell:
echo   Test-NetConnection -ComputerName %LAN_IP% -Port 3307
echo.
echo --- TEST 2: Network Reachability Test (from Linux / Mac / Android Termux) ---
echo Run in Terminal:
echo   nc -zv %LAN_IP% 3307
echo   or
echo   curl -v telnet://%LAN_IP%:3307
echo.
echo --- TEST 3: Direct MySQL CLI Test (from another device with MySQL installed) ---
echo Run in Terminal / Command Prompt:
echo   mysql -h %LAN_IP% -P 3307 -u vacuser -p kec_vac -e "SELECT @@version, USER();"
echo   (Enter password: VacSecurePass@2026)
echo.
echo --- TEST 4: Run Application in Remote Mode (on this or another machine) ---
echo Run:
echo   run.bat --remote --demo
echo   or
echo   java -cp "bin;lib/mysql-connector-j-8.0.33.jar" MainApp --remote --demo
echo.
echo ===============================================================================
pause

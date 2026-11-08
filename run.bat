@echo off
setlocal
title KEC VAC and Skill Certification Engine
cd /d "%~dp0"

echo ===============================================================================
echo                KEC VAC and SKILL CERTIFICATION ENGINE RUNNER
echo ===============================================================================

rem Check if compiled classes exist
if not exist "bin" mkdir "bin"

rem Check for MySQL Connector JAR in lib
if not exist "lib\mysql-connector-j-8.0.33.jar" (
    echo [INFO] Copying MySQL Connector JDBC driver to lib...
    if exist "E:\KonguStudentEval\lib\mysql-connector-j-8.0.33.jar" (
        copy /y "E:\KonguStudentEval\lib\mysql-connector-j-8.0.33.jar" "lib\mysql-connector-j-8.0.33.jar" >nul
    )
)

echo [1/2] Compiling Java source files...
javac -encoding UTF-8 -cp "lib/mysql-connector-j-8.0.33.jar" -d bin src\main\java\*.java
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Java compilation failed!
    pause
    exit /b 1
)
echo [SUCCESS] Compilation successful!

echo [2/2] Launching KEC VAC Engine...
java -cp "bin;lib/mysql-connector-j-8.0.33.jar" MainApp %*
pause

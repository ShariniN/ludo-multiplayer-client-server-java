@echo off
cd /d "%~dp0"
set JAVA="%~dp0jdk\jdk-21.0.2\bin\java.exe"
if not exist %JAVA% set JAVA=java

if not exist "%~dp0src\out\ludo\db\DatabaseServer.class" (
    echo [INFO] Classes not found. Compiling all tiers first...
    call "%~dp0compile_all.bat"
)
echo ===============================================================
echo    Starting LUDO-T Database Server (Tier 3): H2 over TCP on port 8082
echo    Data directory: data\  (schema.sql is applied on every start)
echo    Usage: run_database.bat [port] [--reset] [--allow-remote]
echo      --reset         drop everything and recreate the empty schema (demo initial state)
echo      --allow-remote  accept connections from other computers
echo ===============================================================
%JAVA% -cp "%~dp0src\out;%~dp0lib\*" ludo.db.DatabaseServer %*
if errorlevel 1 pause

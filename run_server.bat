@echo off
cd /d "%~dp0"
set JAVA="%~dp0jdk\jdk-21.0.2\bin\java.exe"
if not exist %JAVA% set JAVA=java

if not exist "%~dp0src\out\ludo\server\ServerMain.class" (
    echo [INFO] Classes not found. Compiling all tiers first...
    call "%~dp0compile_all.bat"
)
echo ===============================================================
echo    Starting LUDO-T Game Server (Tier 2)
echo    TCP game port 8080  -  embedded Apache Tomcat (servlets) on 8081
echo    Connects over JDBC to the H2 Database Server at 127.0.0.1:8082 (run_database.bat)
echo    Usage: run_server.bat [tcpPort] [httpPort] [dbHost] [dbPort]
echo ===============================================================
%JAVA% -cp "%~dp0src\out;%~dp0lib\*" ludo.server.ServerMain %*
if errorlevel 1 pause

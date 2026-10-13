@echo off
cd /d "%~dp0"
title LUDO-T Launcher
set JAVA="%~dp0jdk\jdk-21.0.2\bin\java.exe"
if not exist %JAVA% set JAVA=java

echo ===============================================================
echo   LUDO-T 3-TIER LAUNCHER (COMP63038 Assignment 2)
echo   Tier 3: Database Server  ^|  Tier 2: Game Server  ^|  Tier 1: 4 GUI clients
echo ===============================================================
echo.
echo [1/4] Compiling all packages...
call "%~dp0compile_all.bat"

echo.
echo [2/4] Starting H2 Database Server (port 8082)...
start "LUDO-T Database Server (Tier 3)" %JAVA% -cp "%~dp0src\out;%~dp0lib\*" ludo.db.DatabaseServer 8082
timeout /t 2 /nobreak >nul

echo [3/4] Starting Game Server (TCP 8080, embedded Tomcat 8081)...
start "LUDO-T Game Server (Tier 2)" %JAVA% -cp "%~dp0src\out;%~dp0lib\*" ludo.server.ServerMain 8080 8081 127.0.0.1 8082
timeout /t 2 /nobreak >nul

echo.
echo [4/4] Launching 4 player windows (Red, Green, Yellow, Blue)...
for %%P in ("Player1 RED" "Player2 GREEN" "Player3 YELLOW" "Player4 BLUE") do (
    for /f "tokens=1,2" %%a in (%%P) do (
        start "LUDO-T Player - %%b" %JAVA% -cp "%~dp0src\out;%~dp0lib\*" ludo.client.ClientMain 127.0.0.1 8080 %%a %%b
        timeout /t 1 /nobreak >nul
    )
)

echo.
echo ===============================================================
echo   Three tiers are running as separate processes:
echo     Database Server window, Game Server window, 4 client windows.
echo   The match starts automatically once all four players have joined.
echo   Leaderboard / API: http://127.0.0.1:8081/api/leaderboard   http://127.0.0.1:8081/api/stats
echo ===============================================================
pause

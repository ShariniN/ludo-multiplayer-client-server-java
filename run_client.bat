@echo off
cd /d "%~dp0"
set JAVA="%~dp0jdk\jdk-21.0.2\bin\java.exe"
if not exist %JAVA% set JAVA=java

if not exist "%~dp0src\out\ludo\client\ClientMain.class" (
    echo [INFO] Classes not found. Compiling all tiers first...
    call "%~dp0compile_all.bat"
)
echo ===============================================================
echo    Starting LUDO-T Desktop Client (Tier 1)
echo    Usage: run_client.bat [host] [port] [playerName] [RED^|GREEN^|YELLOW^|BLUE]
echo    With no arguments a connection dialog is shown.
echo ===============================================================
start "LUDO-T Client" %JAVA% -cp "%~dp0src\out;%~dp0lib\*" ludo.client.ClientMain %*

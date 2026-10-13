@echo off
cd /d "%~dp0"
set JAVA="%~dp0jdk\jdk-21.0.2\bin\java.exe"
if not exist %JAVA% set JAVA=java

if not exist "%~dp0src\out\ludo\stress\StressTestRunner.class" (
    echo [INFO] Classes not found. Compiling all tiers first...
    call "%~dp0compile_all.bat"
)
echo ===============================================================
echo    LUDO-T Concurrency Stress Test (automatic test clients)
echo    No arguments : private server on port 8089, 4 bots, 10 seconds
echo    host port    : hammer a running server, e.g. run_stress_test.bat 127.0.0.1 8080
echo    optional 3rd/4th arguments: number of bots, seconds
echo ===============================================================
%JAVA% -cp "%~dp0src\out;%~dp0lib\*" ludo.stress.StressTestRunner %*
pause

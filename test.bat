@echo off
cd /d "%~dp0"
set JAVA="%~dp0jdk\jdk-21.0.2\bin\java.exe"
set JAVAC="%~dp0jdk\jdk-21.0.2\bin\javac.exe"
if not exist %JAVA% set JAVA=java
if not exist %JAVAC% set JAVAC=javac

echo === Compiling production classes ===
call "%~dp0compile_all.bat"
if errorlevel 1 exit /b 1

echo === Compiling test classes ===
powershell -NoProfile -Command "Get-ChildItem -Path 'src\test' -Recurse -Filter '*.java' | ForEach-Object { ($_.FullName.Substring((Get-Location).Path.Length + 1)).Replace('\', '/') } | Set-Content -Path 'test_sources.txt' -Encoding ASCII"
%JAVAC% -d "src\out" -cp "src\out;lib\*;lib\test\*" @"test_sources.txt"
set TEST_COMPILE_ERR=%ERRORLEVEL%
if exist "test_sources.txt" del "test_sources.txt"
if %TEST_COMPILE_ERR% neq 0 (
    echo [ERROR] Test compilation failed!
    pause
    exit /b 1
)

echo === Running the JUnit 5 suite (Assignment 1 domain + Assignment 2 concurrency/network/persistence) ===
rem The classpath goes on the JVM (-cp) because the JUnit launcher's --class-path option rejects the "*" wildcard.
%JAVA% -javaagent:"lib\test\byte-buddy-agent-1.14.12.jar" -cp "src\out;lib\*;lib\test\*" org.junit.platform.console.ConsoleLauncher execute --scan-class-path "src\out" --include-engine=junit-jupiter --details=summary --disable-banner
set TEST_ERR=%ERRORLEVEL%
pause
exit /b %TEST_ERR%

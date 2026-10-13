@echo off
cd /d "%~dp0"
set JAVAC="%~dp0jdk\jdk-21.0.2\bin\javac.exe"
if not exist %JAVAC% set JAVAC=javac

echo === Compiling all LUDO-T packages ===
if not exist "src\out" mkdir "src\out"

powershell -NoProfile -Command "Get-ChildItem -Path 'src\ludo' -Recurse -Filter '*.java' | ForEach-Object { ($_.FullName.Substring((Get-Location).Path.Length + 1)).Replace('\', '/') } | Set-Content -Path 'sources.txt' -Encoding ASCII"

%JAVAC% -d "src\out" -cp "lib\*" @"sources.txt"
set COMPILE_ERR=%ERRORLEVEL%
if exist "sources.txt" del "sources.txt"
rem schema.sql is loaded from the classpath by the database tier
if not exist "src\out\ludo\db" mkdir "src\out\ludo\db"
copy /y "src\ludo\db\schema.sql" "src\out\ludo\db\schema.sql" >nul

if %COMPILE_ERR% neq 0 (
    echo [ERROR] Compilation failed!
    pause
    exit /b 1
)
echo.
echo === Compilation Succeeded! All tiers compiled to src\out ===

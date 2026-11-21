@echo off
echo Starting Orphic Task Manager Backend...
pushd "%~dp0"

echo Checking and freeing port 8080...
for /f "tokens=5" %%a in ('netstat -aon ^| findstr ":8080 " ^| findstr "LISTENING"') do (
    echo Terminating process PID %%a listening on port 8080...
    taskkill /F /PID %%a 2>nul
)

rem Override these with your own environment variables if needed.
if not defined DB_HOST set DB_HOST=localhost
if not defined DB_PORT set DB_PORT=3306
if not defined DB_NAME set DB_NAME=employee_management
if not defined DB_USERNAME set DB_USERNAME=root
if not defined DB_PASSWORD set DB_PASSWORD=Devansh@2004

set "INTELLIJ_MVN=C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.4\plugins\maven\lib\maven3\bin\mvn.cmd"

where mvn >nul 2>&1
if %errorlevel%==0 (
    echo Using System Maven...
    call mvn spring-boot:run -Dmaven.test.skip=true
) else if exist "%INTELLIJ_MVN%" (
    echo Using IntelliJ Bundled Maven...
    call "%INTELLIJ_MVN%" spring-boot:run -Dmaven.test.skip=true
) else (
    echo Maven not found. Install Maven or add it to PATH.
)

popd

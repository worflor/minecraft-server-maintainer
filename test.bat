@echo off
setlocal

echo.
echo ==========================================
echo   Server Maintainer Test Suite
echo ==========================================
echo.

cd /d "%~dp0"

:: Check if Gradle wrapper JAR exists
if not exist "gradle\wrapper\gradle-wrapper.jar" (
    echo Downloading Gradle wrapper...
    mkdir gradle\wrapper 2>nul
    curl -sL "https://services.gradle.org/distributions/gradle-8.11.1-bin.zip" -o gradle\wrapper\gradle.zip
    powershell -Command "Expand-Archive -Path 'gradle\wrapper\gradle.zip' -DestinationPath 'gradle\wrapper\temp' -Force"
    copy "gradle\wrapper\temp\gradle-8.11.1\lib\gradle-launcher-8.11.1.jar" "gradle\wrapper\gradle-wrapper.jar" >nul
    rmdir /s /q "gradle\wrapper\temp"
    del "gradle\wrapper\gradle.zip"
)

:: Parse arguments
set TEST_TYPE=%1
if "%TEST_TYPE%"=="" set TEST_TYPE=test

echo Running: %TEST_TYPE%
echo.

:: Run tests
call gradlew.bat %TEST_TYPE% --console=rich %2 %3 %4 %5

echo.
if %ERRORLEVEL% equ 0 (
    echo ==========================================
    echo   All tests passed!
    echo ==========================================
) else (
    echo ==========================================
    echo   Some tests failed. Check report above.
    echo ==========================================
)

echo.
echo Test report: build\reports\tests\%TEST_TYPE%\index.html
echo.

endlocal

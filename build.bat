@echo off
setlocal

echo.
echo Building Server Maintainer...
echo.

cd /d "%~dp0"

:: Read version from VERSION file
set /p VERSION=<VERSION

:: Use Java 21 explicitly (Minecraft requires Java 21)
set "JAVA_HOME="
for /d %%G in ("C:\Program Files\Eclipse Adoptium\jdk-21*") do set "JAVA_HOME=%%G"
if not defined JAVA_HOME for /d %%G in ("C:\Program Files\Java\jdk-21*") do set "JAVA_HOME=%%G"
if not defined JAVA_HOME for /d %%G in ("C:\Program Files\OpenJDK\jdk-21*") do set "JAVA_HOME=%%G"
if not defined JAVA_HOME for /d %%G in ("C:\Program Files\Microsoft\jdk-21*") do set "JAVA_HOME=%%G"
if not defined JAVA_HOME for /d %%G in ("C:\Program Files\Amazon Corretto\jdk21*") do set "JAVA_HOME=%%G"
if not defined JAVA_HOME for /d %%G in ("C:\Program Files\Zulu\zulu-21*") do set "JAVA_HOME=%%G"
if not defined JAVA_HOME ( echo Java 21 not found! Install a JDK 21. & pause & exit /b 1 )
set "PATH=%JAVA_HOME%\bin;%PATH%"

:: Download ProGuard if not present
if not exist "tools\proguard.jar" (
    echo Downloading ProGuard...
    mkdir tools 2>nul
    curl -sL "https://github.com/Guardsquare/proguard/releases/download/v7.4.2/proguard-7.4.2.zip" -o tools\proguard.zip
    powershell -Command "Expand-Archive -Path 'tools\proguard.zip' -DestinationPath 'tools' -Force"
    copy "tools\proguard-7.4.2\lib\proguard.jar" "tools\proguard.jar" >nul
    rmdir /s /q "tools\proguard-7.4.2"
    del "tools\proguard.zip"
)

:: Clean
if exist build rmdir /s /q build
mkdir build\classes

:: Compile (Java 21, no debug info)
echo Compiling...
javac -g:none --release 21 -d build\classes -sourcepath src src\dev\woflo\fabric\*.java
if errorlevel 1 ( echo Compilation failed! & pause & exit /b 1 )

:: Optimize with ProGuard (aggressive inlining via hidden JVM flag)
echo Optimizing...
java -Dmaximum.inlined.code.length=32 -jar tools\proguard.jar @proguard.cfg
if errorlevel 1 ( echo Optimization failed! & pause & exit /b 1 )

:: Create minimal manifest and package
echo Packaging...
(
echo Manifest-Version: 1.0
echo Main-Class: dev.woflo.fabric.Main
echo Implementation-Version: %VERSION%
) > build\MANIFEST.MF
jar cfm "build\server maintainer by woflo %VERSION%.jar" build\MANIFEST.MF -C build\optimized .

:: Recompress with advzip if available (zopfli = better deflate)
where advzip >nul 2>&1 && (
    echo Recompressing with zopfli...
    advzip -z -4 "build\server maintainer by woflo %VERSION%.jar"
)

:: Show final size
echo.
for %%F in ("build\server maintainer by woflo %VERSION%.jar") do echo Final JAR: %%~zF bytes
echo.
echo Done! [v%VERSION%]
pause

@echo off
setlocal EnableExtensions EnableDelayedExpansion

set "ROOT_DIR=%~dp0"
if "%ROOT_DIR:~-1%"=="\" set "ROOT_DIR=%ROOT_DIR:~0,-1%"
set "BOOTSTRAP_DIR=%ROOT_DIR%\.bootstrap"
set "DOWNLOAD_DIR=%BOOTSTRAP_DIR%\downloads"
set "TOOLS_DIR=%BOOTSTRAP_DIR%\tools"
set "JAVA_DIR=%TOOLS_DIR%\jdk-17.0.18+8"
set "GRADLE_VERSION=8.14.3"
set "GRADLE_DIR=%TOOLS_DIR%\gradle-%GRADLE_VERSION%"
set "JAVA_URL=https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.18+8/OpenJDK17U-jdk_x64_windows_hotspot_17.0.18_8.zip"
set "GRADLE_URL=https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip"
set "JAVA_ARCHIVE=%DOWNLOAD_DIR%\OpenJDK17U-jdk_x64_windows_hotspot_17.0.18_8.zip"
set "GRADLE_ARCHIVE=%DOWNLOAD_DIR%\gradle-%GRADLE_VERSION%-bin.zip"

if not exist "%DOWNLOAD_DIR%" mkdir "%DOWNLOAD_DIR%"
if not exist "%TOOLS_DIR%" mkdir "%TOOLS_DIR%"

where powershell >nul 2>nul
if errorlevel 1 (
    echo PowerShell is required to extract downloads and is not available.
    exit /b 1
)

if not exist "%JAVA_DIR%" (
    echo Downloading Java 17...
    powershell -NoProfile -ExecutionPolicy Bypass -Command "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri '%JAVA_URL%' -OutFile '%JAVA_ARCHIVE%'"
    if errorlevel 1 exit /b 1
    echo Extracting Java 17...
    powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Path '%JAVA_ARCHIVE%' -DestinationPath '%TOOLS_DIR%' -Force"
    if errorlevel 1 exit /b 1
)

if not exist "%GRADLE_DIR%" (
    echo Downloading Gradle %GRADLE_VERSION%...
    powershell -NoProfile -ExecutionPolicy Bypass -Command "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri '%GRADLE_URL%' -OutFile '%GRADLE_ARCHIVE%'"
    if errorlevel 1 exit /b 1
    echo Extracting Gradle %GRADLE_VERSION%...
    powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Path '%GRADLE_ARCHIVE%' -DestinationPath '%TOOLS_DIR%' -Force"
    if errorlevel 1 exit /b 1
)

set "JAVA_HOME=%JAVA_DIR%"
set "GRADLE_HOME=%GRADLE_DIR%"
set "PATH=%JAVA_HOME%\bin;%GRADLE_HOME%\bin;%PATH%"

cd /d "%ROOT_DIR%"

echo Using JAVA_HOME=%JAVA_HOME%
call java -version
if errorlevel 1 exit /b 1

echo.
call gradle.bat --version
if errorlevel 1 exit /b 1

echo.
call gradle.bat clean build
if errorlevel 1 exit /b 1

echo.
echo Bootstrap and build completed successfully.

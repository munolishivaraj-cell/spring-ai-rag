@echo off
rem Maven wrapper for this project: downloads Maven once into .mvn\ and runs it.
rem Usage:  .\mvnw.cmd spring-boot:run
setlocal
set "MVN_VER=3.9.11"
set "MVN_DIR=%~dp0.mvn"
set "MVN_HOME=%MVN_DIR%\apache-maven-%MVN_VER%"

where java >nul 2>nul
if errorlevel 1 goto nojava

if exist "%MVN_HOME%\bin\mvn.cmd" goto run
echo Downloading Maven %MVN_VER% - one time only...
if not exist "%MVN_DIR%" mkdir "%MVN_DIR%"
powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; [Net.ServicePointManager]::SecurityProtocol='Tls12'; $z=Join-Path '%MVN_DIR%' 'maven.zip'; Invoke-WebRequest -UseBasicParsing 'https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/%MVN_VER%/apache-maven-%MVN_VER%-bin.zip' -OutFile $z; Expand-Archive -Force $z '%MVN_DIR%'; Remove-Item $z"
if errorlevel 1 goto dlfail

:run
call "%MVN_HOME%\bin\mvn.cmd" %*
exit /b %errorlevel%

:nojava
echo ERROR: Java was not found. Install JDK 17 or newer, e.g. Eclipse Temurin 21 from https://adoptium.net
echo Tick "Set JAVA_HOME" and "Add to PATH" in the installer, then open a NEW PowerShell window.
exit /b 1

:dlfail
echo ERROR: Could not download Maven. Check your internet connection and try again.
exit /b 1

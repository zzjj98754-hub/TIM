@echo off
setlocal

set MAVEN_VERSION=3.9.12
set MAVEN_DIST_NAME=apache-maven-%MAVEN_VERSION%
set MAVEN_WRAPPER_DIST_DIR=%USERPROFILE%\.m2\wrapper\dists
set MAVEN_CMD=

for /f "delims=" %%i in ('powershell -NoProfile -ExecutionPolicy Bypass -Command "Get-ChildItem -Path \"$env:USERPROFILE\\.m2\\wrapper\\dists\" -Recurse -Filter mvn.cmd -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty FullName"') do (
  if not defined MAVEN_CMD set "MAVEN_CMD=%%i"
)

if not defined MAVEN_CMD (
  set DOWNLOAD_DIR=%TEMP%\tim-maven-wrapper
  set ZIP_FILE=%DOWNLOAD_DIR%\%MAVEN_DIST_NAME%-bin.zip
  set EXTRACT_DIR=%DOWNLOAD_DIR%\extract
  if not exist "%DOWNLOAD_DIR%" mkdir "%DOWNLOAD_DIR%" >nul 2>nul
  if not exist "%EXTRACT_DIR%" mkdir "%EXTRACT_DIR%" >nul 2>nul
  if not exist "%ZIP_FILE%" (
    powershell -NoProfile -ExecutionPolicy Bypass -Command "$wc = New-Object System.Net.WebClient; $wc.DownloadFile('https://archive.apache.org/dist/maven/maven-3/%MAVEN_VERSION%/binaries/%MAVEN_DIST_NAME%-bin.zip', '%ZIP_FILE%')" || exit /b 1
  )
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Path '%ZIP_FILE%' -DestinationPath '%EXTRACT_DIR%' -Force" || exit /b 1
  set MAVEN_CMD=%EXTRACT_DIR%\%MAVEN_DIST_NAME%\bin\mvn.cmd
)

"%MAVEN_CMD%" %*

@echo off
setlocal EnableExtensions
set "ROOT_DIR=%~dp0"
set "GRADLE_VERSION=9.6.0"
set "EXPECTED_SHA256=bbaeb2fef8710818cf0e261201dab964c572f92b942812df0c3620d62a529a01"
if not defined GRADLE_USER_HOME set "GRADLE_USER_HOME=%USERPROFILE%\.gradle"
set "CACHE_ROOT=%GRADLE_USER_HOME%\phira-chart-studio"
set "GRADLE_HOME=%CACHE_ROOT%\gradle-%GRADLE_VERSION%"
set "GRADLE_BIN=%GRADLE_HOME%\bin\gradle.bat"
if not exist "%GRADLE_BIN%" (
  if not exist "%CACHE_ROOT%" mkdir "%CACHE_ROOT%"
  set "ZIP=%CACHE_ROOT%\gradle-%GRADLE_VERSION%-bin.zip"
  echo Downloading Gradle %GRADLE_VERSION%...
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip' -OutFile '%CACHE_ROOT%\gradle-%GRADLE_VERSION%-bin.zip'; $actual=(Get-FileHash -Algorithm SHA256 -LiteralPath '%CACHE_ROOT%\gradle-%GRADLE_VERSION%-bin.zip').Hash.ToLowerInvariant(); if ($actual -ne '%EXPECTED_SHA256%') { throw 'Gradle distribution SHA-256 mismatch; refusing to execute it.' }; Expand-Archive -LiteralPath '%CACHE_ROOT%\gradle-%GRADLE_VERSION%-bin.zip' -DestinationPath '%CACHE_ROOT%' -Force; Remove-Item '%CACHE_ROOT%\gradle-%GRADLE_VERSION%-bin.zip' -Force"
  if errorlevel 1 exit /b 1
)
call "%GRADLE_BIN%" -p "%ROOT_DIR%" %*
exit /b %ERRORLEVEL%

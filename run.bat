@echo off
setlocal

call "%~dp0gradlew.bat" build
if errorlevel 1 exit /b %errorlevel%

call "%~dp0gradlew.bat" test
if errorlevel 1 exit /b %errorlevel%

call "%~dp0gradlew.bat" run
exit /b %errorlevel%

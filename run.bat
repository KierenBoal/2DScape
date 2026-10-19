@echo off
setlocal

call "%~dp0gradlew.bat" build
if errorlevel 1 goto :failed

call "%~dp0gradlew.bat" test
if errorlevel 1 goto :failed

call "%~dp0gradlew.bat" run
if errorlevel 1 goto :failed
exit /b 0

:failed
set "EXIT_CODE=%ERRORLEVEL%"
echo.
echo Gradle failed with exit code %EXIT_CODE%.
pause
exit /b %EXIT_CODE%

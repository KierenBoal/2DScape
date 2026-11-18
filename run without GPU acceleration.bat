@echo off
setlocal
pushd "%~dp0" || exit /b 1

call "%~dp0gradlew.bat" run -PdisableHardwareAcceleration %*
set "RUN_EXIT=%ERRORLEVEL%"
popd

if not "%RUN_EXIT%"=="0" (
    echo Gradle failed with exit code %RUN_EXIT%.
    pause
)
exit /b %RUN_EXIT%

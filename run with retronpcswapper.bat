@echo off
setlocal
set "EXIT_CODE="

pushd "%~dp0"
if errorlevel 1 goto :failed

set "RETRO_DIR=%CD%\.dev\RetroNPCSwapper"

if exist "%RETRO_DIR%\.git" goto :retro_ready
if exist "%RETRO_DIR%\" (
	echo "%RETRO_DIR%" exists but is not a Git checkout.
	set "EXIT_CODE=1"
	goto :failed
)

if not exist "%CD%\.dev\" mkdir "%CD%\.dev"
if errorlevel 1 goto :failed

git clone "https://github.com/AJD-/RetroNPCSwapper.git" "%RETRO_DIR%"
if errorlevel 1 goto :failed

:retro_ready
call "%~dp0gradlew.bat" "-PretroNpcSwapperDir=.dev/RetroNPCSwapper" test
if errorlevel 1 goto :failed

call "%~dp0gradlew.bat" "-PretroNpcSwapperDir=.dev/RetroNPCSwapper" :retroNpcSwapper:test
if errorlevel 1 goto :failed

call "%~dp0gradlew.bat" "-PretroNpcSwapperDir=.dev/RetroNPCSwapper" runWithRetroNpcSwapper
if errorlevel 1 goto :failed

popd
exit /b 0

:failed
if not defined EXIT_CODE set "EXIT_CODE=%ERRORLEVEL%"
if not defined EXIT_CODE set "EXIT_CODE=1"
if "%EXIT_CODE%"=="0" set "EXIT_CODE=1"
echo.
echo Operation failed with exit code %EXIT_CODE%. Game will not start.
pause
popd
exit /b %EXIT_CODE%

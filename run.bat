@echo off
setlocal
pushd "%~dp0" || exit /b 1

rem Token thresholds: warning, Enter to continue, hard failure.
set "TOKEN_WARN=150000"
set "TOKEN_CONFIRM=200000"
set "TOKEN_STOP=250000"

rem cl100k_base: main Java only, no comments.
rem Tests, resources, and README are excluded. Requires Python 3 and:
rem python -m pip install tiktoken
python -c "import tiktoken" >nul 2>&1
if errorlevel 1 (
    echo ERROR: Python 3 with tiktoken is required. Run: python -m pip install tiktoken
    popd
    exit /b 1
)
python -c "import os,pathlib,re,sys,tiktoken; files=sorted(pathlib.Path('src/main/java').rglob('*.java')); files or sys.exit('ERROR: No main Java sources found.'); rx=re.compile(r'\x22(?:\\.|[^\x22\\])*\x22|\x27(?:\\.|[^\x27\\])*\x27|//[^\r\n]*|/\*[\s\S]*?\*/'); source='\n'.join(rx.sub(lambda m: ' ' if m.group().startswith(('//','/*')) else m.group(), p.read_text(encoding='utf-8')) for p in files); count=len(tiktoken.get_encoding('cl100k_base').encode(source,disallowed_special=())); warn=int(os.environ['TOKEN_WARN']); confirm=int(os.environ['TOKEN_CONFIRM']); stop=int(os.environ['TOKEN_STOP']); print(f'Code token count approximately: {count:,}\r\n'); count>=warn and print(f'WARNING: Source has reached {warn:,} tokens.'); count>=stop and sys.exit(f'ERROR: Source has reached the {stop:,}-token hard limit.'); count>=confirm and input(f'WARNING: Source has reached {confirm:,} tokens. Press Enter to continue...')"
if errorlevel 1 goto :token_failed

call "%~dp0gradlew.bat" build
if errorlevel 1 goto :failed

call "%~dp0gradlew.bat" test
if errorlevel 1 goto :failed

call "%~dp0gradlew.bat" run
if errorlevel 1 goto :failed
popd
exit /b 0

:token_failed
set "EXIT_CODE=%ERRORLEVEL%"
echo Token check failed with exit code %EXIT_CODE%.
popd
pause
exit /b %EXIT_CODE%

:failed
set "EXIT_CODE=%ERRORLEVEL%"
echo.
echo Gradle failed with exit code %EXIT_CODE%.
popd
pause
exit /b %EXIT_CODE%

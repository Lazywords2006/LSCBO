@echo off
setlocal
cd /d "%~dp0"

echo [LSCBO] Optimized full experiment -- auto parallel mode
echo [LSCBO] Detecting Python...

set PYTHON=python
where python3 >nul 2>&1
if %ERRORLEVEL% == 0 set PYTHON=python3

echo [LSCBO] Using: %PYTHON%
echo [LSCBO] Starting parallel run (auto-detect CPU count)...
echo.

%PYTHON% scripts\run_lscbo_full_program.py --profile formal --parallel 0 --jvm-threads 0
if errorlevel 1 goto :fail

echo.
echo [LSCBO] Done. Results in results\formal_*
exit /b 0

:fail
echo.
echo [LSCBO] Run failed. Check logs above and in results\formal_*\logs\
exit /b 1

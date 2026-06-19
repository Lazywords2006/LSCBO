@echo off
setlocal EnableExtensions
chcp 65001 >nul
cd /d "%~dp0"

set "ROOT=%~dp0"
if "%ROOT:~-1%"=="\" set "ROOT=%ROOT:~0,-1%"
set "TEX="

for /f "delims=" %%F in ('where /r "%ROOT%" LSCBO_Evolutionary_Intelligence.tex 2^>nul') do (
  if exist "%%~dpFsn-jnl.cls" set "TEX=%%F"
)

if not defined TEX (
  echo [FAIL] Could not locate LSCBO_Evolutionary_Intelligence.tex.
  if not defined NO_PAUSE pause
  exit /b 1
)

echo [LSCBO] Scanning manuscript for high-risk residual claims...
rg -n "\\iffalse|\\fi|large improvement|substantial gain|dramatic superiority|dominates|outperforms all|significantly outperforms all|9,270|9270|12,780|12780|1\.087|1\.060|0\.021359|0\.0755|0\.0016|900\.0|not divided by a hidden reference|9\.6%%|30\.1%%|7\.4%%|22\.6%%|fully simulated|executed through full simulation|full replay of every formal row is used" "%TEX%"
if %ERRORLEVEL%==1 (
  echo [OK] No high-risk residual claims found.
  if not defined NO_PAUSE pause
  exit /b 0
)
if not defined NO_PAUSE pause

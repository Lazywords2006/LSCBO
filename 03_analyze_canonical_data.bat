@echo off
setlocal EnableExtensions
chcp 65001 >nul
cd /d "%~dp0"

set "ROOT=%~dp0"
if "%ROOT:~-1%"=="\" set "ROOT=%ROOT:~0,-1%"
set "RAW="
set "SCRIPT="
set "CLOUDSIM="

for /f "delims=" %%F in ('where /r "%ROOT%" claim_full_raw.csv 2^>nul') do (
  echo %%F | findstr /i "formal-cloudsim_20260619_canonical" >nul && set "RAW=%%F"
)

for /f "delims=" %%F in ('where /r "%ROOT%" claim_smoke_raw.csv 2^>nul') do (
  echo %%F | findstr /i "formal-cloudsim_20260619_canonical" >nul && set "CLOUDSIM=%%F"
)

for /f "delims=" %%F in ('where /r "%ROOT%" analyze_canonical_results.py 2^>nul') do (
  set "SCRIPT=%%F"
)

if not defined RAW (
  echo [FAIL] Could not locate canonical claim_full_raw.csv.
  if not defined NO_PAUSE pause
  exit /b 1
)

if not defined SCRIPT (
  echo [FAIL] Could not locate analyze_canonical_results.py.
  if not defined NO_PAUSE pause
  exit /b 1
)

for %%D in ("%RAW%") do set "OUT=%%~dpDanalysis"

echo [LSCBO] Re-analyzing canonical cloud-scheduling data...
if defined CLOUDSIM (
  python "%SCRIPT%" --raw "%RAW%" --out "%OUT%" --cloudsim "%CLOUDSIM%"
) else (
  python "%SCRIPT%" --raw "%RAW%" --out "%OUT%"
)

echo.
echo [Outputs]
dir "%OUT%\table_main_vs_greedy_by_scale.csv"
dir "%OUT%\table_pairwise_wilcoxon.csv"
dir "%OUT%\table_canonical_ablation.csv"
dir "%OUT%\CANONICAL_VERIFICATION_REPORT.md"
if not defined NO_PAUSE pause

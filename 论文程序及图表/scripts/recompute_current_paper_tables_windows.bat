@echo off
setlocal
cd /d "%~dp0.."

py -3 -c "import scipy" >nul 2>nul
if errorlevel 1 (
  echo Installing scipy for Wilcoxon p-values...
  py -3 -m pip install scipy
)

py -3 scripts\recompute_current_paper_tables.py
endlocal

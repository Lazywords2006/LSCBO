@echo off
setlocal
cd /d "%~dp0"

where py >nul 2>nul
if errorlevel 1 (
  echo Python launcher "py" was not found. Please install Python 3 first.
  exit /b 1
)

py -3 -c "import matplotlib" >nul 2>nul
if errorlevel 1 (
  echo Installing matplotlib...
  py -3 -m pip install matplotlib
  if errorlevel 1 (
    echo Failed to install matplotlib.
    exit /b 1
  )
)

py -3 "%~dp0regenerate_consistent_lscbo_figures.py"
if errorlevel 1 (
  echo Figure regeneration failed.
  exit /b 1
)

echo Done. Figures were regenerated in ..\figures

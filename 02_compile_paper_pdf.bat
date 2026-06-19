@echo off
setlocal EnableExtensions
chcp 65001 >nul
cd /d "%~dp0"

set "PATH=%~dp0.tools\apache-maven-3.9.16\bin;%LOCALAPPDATA%\Programs\MiKTeX\miktex\bin\x64;%ProgramFiles%\MiKTeX\miktex\bin\x64;%SystemDrive%\Strawberry\perl\bin;%SystemDrive%\Strawberry\c\bin;%PATH%"
set "ROOT=%~dp0"
if "%ROOT:~-1%"=="\" set "ROOT=%ROOT:~0,-1%"
set "TEXDIR="

for /f "delims=" %%F in ('where /r "%ROOT%" LSCBO_Evolutionary_Intelligence.tex 2^>nul') do (
  if exist "%%~dpFsn-jnl.cls" set "TEXDIR=%%~dpF"
)

if not defined TEXDIR (
  echo [FAIL] Could not locate LSCBO_Evolutionary_Intelligence.tex.
  if not defined NO_PAUSE pause
  exit /b 1
)

pushd "%TEXDIR%"

echo [LSCBO] Compiling Evolutionary Intelligence manuscript...
where latexmk >nul 2>nul
if %ERRORLEVEL%==0 (
  call latexmk -g -pdf -interaction=nonstopmode -halt-on-error LSCBO_Evolutionary_Intelligence.tex
) else (
  echo [WARN] latexmk not found; using pdflatex fallback.
  call pdflatex -interaction=nonstopmode -halt-on-error LSCBO_Evolutionary_Intelligence.tex
  call pdflatex -interaction=nonstopmode -halt-on-error LSCBO_Evolutionary_Intelligence.tex
)

echo.
if exist LSCBO_Evolutionary_Intelligence.pdf (
  echo [OK] PDF generated:
  dir LSCBO_Evolutionary_Intelligence.pdf
) else (
  echo [FAIL] PDF was not generated.
  popd
  if not defined NO_PAUSE pause
  exit /b 1
)
popd
if not defined NO_PAUSE pause

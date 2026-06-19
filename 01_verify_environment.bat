@echo off
setlocal EnableExtensions
chcp 65001 >nul
cd /d "%~dp0"

set "PATH=%~dp0.tools\apache-maven-3.9.16\bin;%LOCALAPPDATA%\Programs\MiKTeX\miktex\bin\x64;%ProgramFiles%\MiKTeX\miktex\bin\x64;%SystemDrive%\Strawberry\perl\bin;%SystemDrive%\Strawberry\c\bin;%PATH%"

echo [Java]
java -version
echo.

echo [Maven]
call mvn -version
echo.

echo [Python]
python --version
python -c "import scipy, matplotlib, pandas, numpy; print('scipy/matplotlib/pandas/numpy ok')"
echo.

echo [Node]
node --version
npm --version
echo.

echo [LaTeX]
where pdflatex
where latexmk
where perl
echo.

echo [Codex skills]
if exist "%USERPROFILE%\.codex\skills\academic-figure-prompt\SKILL.md" echo academic-figure-prompt OK
if exist "%USERPROFILE%\.codex\skills\academic-figure-prompt-pastel\SKILL.md" echo academic-figure-prompt-pastel OK
if exist "%USERPROFILE%\.codex\skills\plot-from-data\SKILL.md" echo plot-from-data OK
if exist "%USERPROFILE%\.codex\skills\plot-from-image\SKILL.md" echo plot-from-image OK

if not defined NO_PAUSE pause

@echo off
setlocal
cd /d "%~dp0"

echo [1/3] Running E2 phase ablation...
mvn exec:java -Dexec.mainClass="com.edcbo.research.FullAblation"
if errorlevel 1 exit /b %errorlevel%

echo [2/3] Running E3 evolution path...
mvn exec:java -Dexec.mainClass="com.edcbo.research.CBOEvolutionPath"
if errorlevel 1 exit /b %errorlevel%

echo [3/3] Running E4 final main comparison...
mvn exec:java -Dexec.mainClass="com.edcbo.research.LSCBOFinalCorrect"
if errorlevel 1 exit /b %errorlevel%

echo Done. New CSV files are in the results folder.
endlocal

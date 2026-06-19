@echo off
setlocal EnableExtensions
chcp 65001 >nul
cd /d "%~dp0论文程序及图表\LSCBO_FULL_EXPERIMENT_PROGRAM_20260609"

set "PATH=%~dp0.tools\apache-maven-3.9.16\bin;%PATH%"

echo [LSCBO] OPTIONAL SLOW JOB: full CloudSim E4 replay.
echo Run this only if you need to support a full CloudSim replay claim in the manuscript.
echo Default paper workflow does NOT require this.
echo.
pause

call mvn -q -DskipTests compile org.codehaus.mojo:exec-maven-plugin:3.5.0:java ^
  "-Dexec.mainClass=com.edcbo.research.claim.ClaimPreservingCloudSimExperiment" ^
  "-Dexec.args=--mode mainfull --seedRange 43-72 --out results/formal-cloudsim_20260619_canonical/cloudsim_E4_mainfull --cloudsim true"

pause

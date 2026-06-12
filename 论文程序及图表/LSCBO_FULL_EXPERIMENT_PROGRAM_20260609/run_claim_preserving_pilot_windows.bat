@echo off
setlocal
cd /d "%~dp0"
echo [LSCBO] Running claim-preserving PILOT data...
mvn -q -DskipTests compile
if errorlevel 1 goto :fail
mvn -q org.codehaus.mojo:exec-maven-plugin:3.5.0:java -Dexec.mainClass="com.edcbo.research.claim.ClaimPreservingCloudSimExperiment" -Dexec.args="--mode pilot --out results\pilot"
if errorlevel 1 goto :fail
echo [LSCBO] Pilot run completed. Results: results\pilot
exit /b 0
:fail
echo [LSCBO] Run failed. Please check Maven and Java 11+.
exit /b 1

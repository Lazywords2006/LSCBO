@echo off
setlocal
cd /d "%~dp0"
echo [LSCBO] Running claim-preserving FULL data package...
echo [LSCBO] This may take a long time.
mvn -q -DskipTests compile
if errorlevel 1 goto :fail
mvn -q org.codehaus.mojo:exec-maven-plugin:3.5.0:java -Dexec.mainClass="com.edcbo.research.claim.ClaimPreservingCloudSimExperiment" -Dexec.args="--mode full --out results\full"
if errorlevel 1 goto :fail
echo [LSCBO] Full run completed. Results: results\full
exit /b 0
:fail
echo [LSCBO] Run failed. Please check Maven and Java 11+.
exit /b 1

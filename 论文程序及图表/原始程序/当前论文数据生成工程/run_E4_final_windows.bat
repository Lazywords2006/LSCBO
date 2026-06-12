@echo off
setlocal
cd /d "%~dp0"
mvn exec:java -Dexec.mainClass="com.edcbo.research.LSCBOFinalCorrect"
endlocal

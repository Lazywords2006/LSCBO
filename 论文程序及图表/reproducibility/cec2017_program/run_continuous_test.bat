@echo off
setlocal enabledelayedexpansion
set round=1

:loop
echo ========================================
echo Start Round !round! Continuous Testing...
echo Time: %date% %time%
echo ========================================

:: 1. 运行Java测试 (生成CSV到 CEC2017/)
call "C:\Users\LazyM\apache-maven-3.9.6\bin\mvn.cmd" compile exec:java "-Dexec.mainClass=com.edcbo.research.CEC2017_BatchTest"

:: 2. 运行Python绘图 (读取CSV生成图表到 CEC2017/figures/)
echo Generating charts...
python scripts/generate_cec2017_charts.py

echo Round !round! Completed.
timeout /t 5

set /a round+=1
goto loop

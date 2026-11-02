@echo off
chcp 65001 >nul
cd /d "%~dp0"
if not exist out mkdir out
if not exist out-test mkdir out-test
dir /s /b src\main\java\*.java > out\sources.txt
dir /s /b src\test\java\*.java > out\tests.txt
javac -encoding UTF-8 -d out @out\sources.txt || exit /b 1
javac -encoding UTF-8 -d out-test -cp out @out\tests.txt || exit /b 1
java -cp out;out-test monopoly.game.GameTest || exit /b 1
java -cp out;out-test monopoly.game.RulesTest || exit /b 1
java -cp out;out-test monopoly.game.SimulationTest || exit /b 1
pause

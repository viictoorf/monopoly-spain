@echo off
rem Compiles and launches the game (requires JDK 17+). Double-click to open the browser.
rem To play from your phone over your Wi-Fi: run.bat --lan
chcp 65001 >nul
cd /d "%~dp0"
if not exist out mkdir out
dir /s /b src\main\java\*.java > out\sources.txt
javac -encoding UTF-8 -d out @out\sources.txt
if errorlevel 1 (
  echo.
  echo Compilation failed. Make sure you have JDK 17 or later installed ^(java -version and javac -version^).
  pause
  exit /b 1
)
xcopy /e /y /q src\main\resources\* out\ >nul
java -Dfile.encoding=UTF-8 -cp out monopoly.Main %*
pause

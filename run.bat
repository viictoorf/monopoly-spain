@echo off
rem Compila y arranca el juego (necesita JDK 17+). Doble clic y se abre el navegador.
rem Para jugar desde el movil en tu wifi: run.bat --lan
chcp 65001 >nul
cd /d "%~dp0"
if not exist out mkdir out
dir /s /b src\main\java\*.java > out\sources.txt
javac -encoding UTF-8 -d out @out\sources.txt
if errorlevel 1 (
  echo.
  echo No se pudo compilar. Comprueba que tienes un JDK 17 o superior instalado ^(java -version y javac -version^).
  pause
  exit /b 1
)
xcopy /e /y /q src\main\resources\* out\ >nul
java -Dfile.encoding=UTF-8 -cp out monopoly.Main %*
pause

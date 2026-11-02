@echo off
rem Creates dist\MonopolyEspana\MonopolyEspana.exe with Java included (requires JDK 17+).
chcp 65001 >nul
cd /d "%~dp0"
if exist build rmdir /s /q build
if exist dist rmdir /s /q dist
mkdir build\classes
mkdir dist
dir /s /b src\main\java\*.java > build\sources.txt
javac -encoding UTF-8 --release 17 -d build\classes @build\sources.txt || exit /b 1
xcopy /e /y /q src\main\resources\* build\classes\ >nul
jar --create --file build\monopoly.jar --main-class monopoly.Main -C build\classes .
rmdir /s /q build\classes
del build\sources.txt
jpackage --type app-image --name MonopolyEspana --input build --main-jar monopoly.jar --dest dist --win-console || exit /b 1
echo.
echo Done: dist\MonopolyEspana\MonopolyEspana.exe
pause

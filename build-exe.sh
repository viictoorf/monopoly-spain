#!/bin/bash
# Crea una carpeta ejecutable con Java incluido (jpackage, viene con el JDK 17+).
# Hay que ejecutarlo en el sistema para el que quieres el programa: en Windows genera un .exe.
set -e
cd "$(dirname "$0")"
rm -rf build dist
mkdir -p build/classes dist
javac -encoding UTF-8 --release 17 -d build/classes $(find src/main/java -name '*.java')
cp -r src/main/resources/* build/classes/
jar --create --file build/monopoly.jar --main-class monopoly.Main -C build/classes .
rm -rf build/classes
jpackage --type app-image --name MonopolyEspana --input build --main-jar monopoly.jar --dest dist --win-console
echo "Listo: dist/MonopolyEspana"

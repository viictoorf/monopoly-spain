#!/bin/bash
# Compila y arranca el juego (necesita JDK 17+). Opciones: --lan (jugar desde el móvil en tu wifi),
# --port=8080, --no-browser (no abrir el navegador solo).
set -e
cd "$(dirname "$0")"
mkdir -p out
javac -encoding UTF-8 --release 17 -d out $(find src/main/java -name '*.java')
cp -r src/main/resources/* out/
java -cp out monopoly.Main "$@"

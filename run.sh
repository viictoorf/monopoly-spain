#!/bin/bash
# Compiles and launches the game (requires JDK 17+). Options: --lan (play from your phone over your Wi-Fi),
# --port=8080, --no-browser (do not open the browser automatically).
set -e
cd "$(dirname "$0")"
mkdir -p out
javac -encoding UTF-8 --release 17 -d out $(find src/main/java -name '*.java')
cp -r src/main/resources/* out/
java -cp out monopoly.Main "$@"

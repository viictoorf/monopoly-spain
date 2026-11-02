#!/bin/bash
# Compiles and runs all tests (requires JDK 17+).
set -e
cd "$(dirname "$0")"
mkdir -p out out-test
javac -encoding UTF-8 --release 17 -d out $(find src/main/java -name '*.java')
javac -encoding UTF-8 --release 17 -d out-test -cp out src/test/java/monopoly/game/*.java
for t in GameTest RulesTest SimulationTest; do
  java -cp out:out-test monopoly.game.$t
done

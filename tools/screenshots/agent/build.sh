#!/bin/zsh
# Compiles the IDE-driving agent against the sandbox IDE's jars and packs it as a Java agent jar.
# usage: build.sh [ClassName]
# The JVM never reloads a class with the same name, so after editing Agent.java pass a fresh
# name (Agent2, Agent3, ...) and use that same name with run.sh.
set -e
HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$HERE/../../.." && pwd)
NAME=${1:-Agent}
: "${JAVA_HOME:?set JAVA_HOME to a JDK 21+ (e.g. the JBR inside a JetBrains IDE)}"
PLATFORM=$(grep '^platformVersion' "$ROOT/gradle.properties" | sed 's/.*= *//')
IDE=$(find ~/.gradle/caches -maxdepth 6 -type d -name "idea-$PLATFORM-*" | head -1)
[ -n "$IDE" ] || { echo "IDE $PLATFORM not found in ~/.gradle/caches — run ./gradlew runIde once"; exit 1; }
OUT="$HERE/../out/agent"
rm -rf "$OUT" && mkdir -p "$OUT/classes/META-INF" "$OUT/gen"
sed "s/[[:<:]]Agent[[:>:]]/$NAME/g" "$HERE/Agent.java" > "$OUT/gen/$NAME.java"
CP=$(ls "$IDE"/lib/*.jar | tr '\n' ':')
"$JAVA_HOME/bin/javac" -nowarn -cp "$CP" -d "$OUT/classes" "$OUT/gen/$NAME.java"
printf "Agent-Class: $NAME\nManifest-Version: 1.0\n" > "$OUT/classes/META-INF/MANIFEST.MF"
(cd "$OUT/classes" && rm -f "../$NAME.jar" && zip -qr "../$NAME.jar" .)   # the JBR ships javac but no jar tool
echo "built $OUT/$NAME.jar"

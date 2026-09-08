#!/bin/zsh
# Loads the agent into the running sandbox IDE and executes a command script.
# usage: run.sh <ClassName> "<cmd>|arg|arg;;<cmd>..."   (see Agent.java for the command list)
# Prints the agent's log, which it writes to ~/agent.out.
set -e
HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$HERE/../../.." && pwd)
: "${JAVA_HOME:?set JAVA_HOME}"
PID=$(pgrep -f "idea-.*/jbr/Contents/Home/bin/[j]ava" | head -1)
[ -n "$PID" ] || { echo "sandbox IDE is not running"; exit 1; }
NATIVE="$HERE/../native"
[ -f "$NATIVE/Attach.class" ] || "$JAVA_HOME/bin/javac" -d "$NATIVE" "$NATIVE/Attach.java"
rm -f ~/agent.out
"$JAVA_HOME/bin/java" -cp "$NATIVE" Attach "$PID" "$HERE/../out/agent/$1.jar" "$2"
cat ~/agent.out

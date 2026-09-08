#!/bin/zsh
# Re-takes the five README screenshots end to end:
#   demo project -> sandbox config -> runIde -> drive the IDE via the agent -> screencapture -> docs/screenshots/
#
# Prerequisites: macOS, Xcode command line tools (swiftc), JAVA_HOME pointing at a JDK 21+
# (the JBR inside any JetBrains IDE works: ~/Applications/PhpStorm.app/Contents/jbr/Contents/Home),
# and screen recording permission for the terminal (screencapture). Accessibility is NOT needed:
# the IDE is driven from inside its own JVM, not by synthesised input events.
#
# The frame is placed at the top-right corner of the main display so submenus open leftwards and
# stay inside the captured region. Adjust FRAME_* if your display is smaller than 1728x1117 points.
set -e
HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$HERE/../.." && pwd)
OUT="$HERE/out"
DEMO="$OUT/demo-project"
DOCS="$ROOT/docs/screenshots"
AGENT=${AGENT_CLASS:-Agent}
FRAME_W=1400; FRAME_H=860
: "${JAVA_HOME:?set JAVA_HOME}"
[ -x "$HERE/native/winid" ] || swiftc -O "$HERE/native/winid.swift" -o "$HERE/native/winid"
SCREEN_W=$("$HERE/native/winid" 0 | awk -F'\t' '$1 == "SCREEN" {split($2, a, "x"); print int(a[1])}')
FRAME_X=$((SCREEN_W - FRAME_W)); FRAME_Y=33
ide_pid() { pgrep -f "idea-.*/jbr/Contents/Home/bin/[j]ava" | head -1; }

run() { "$HERE/agent/run.sh" "$AGENT" "$1"; }
shot_window() {   # the window id is looked up fresh each try: right after a move the capture can fail once
  for i in 1 2 3 4 5; do
    screencapture -x -o -l "$("$HERE/native/winid.sh" $FRAME_W)" "$1" 2>/dev/null && return
    sleep 1
  done
  echo "window capture failed: $1"; return 1
}
shot_region() { screencapture -x -o -R "$FRAME_X,$FRAME_Y,$FRAME_W,$FRAME_H" "$1"; }   # popups are separate windows

PID=$(ide_pid); [ -n "$PID" ] && { echo "== stopping a sandbox IDE left over from a previous run"; kill "$PID"; sleep 3; }

echo "== demo project"
"$HERE/make-demo.sh" "$DEMO" > /dev/null
echo "== sandbox config"
"$HERE/seed-sandbox.sh" "$DEMO" > /dev/null

echo "== starting sandbox IDE (log: $OUT/runIde.log)"
mkdir -p "$OUT"
(cd "$ROOT" && nohup ./gradlew --no-watch-fs runIde --args="$DEMO" > "$OUT/runIde.log" 2>&1 &)
for i in $(seq 1 120); do
  sleep 5
  PID=$(ide_pid)
  # The main frame is the first big window the IDE process owns (its title is not always reported).
  [ -n "$PID" ] && [ -n "$("$HERE/native/winid" "$PID" | awk -F'\t' '$1 != "SCREEN" {split($6, b, ","); if (b[3] >= 1000 && b[4] >= 600) print $1}')" ] && break
done
sleep 10   # let the tool windows and the markdown preview settle

echo "== agent"
"$HERE/agent/build.sh" "$AGENT" > /dev/null

TASKS="$DEMO/openspec/changes/add-user-authentication/tasks.md"
run "frame|$FRAME_X|$FRAME_Y|$FRAME_W|$FRAME_H;;tw|Project|hide;;tw|Spectra|show;;sleep|1500;;expirenotif;;open|$TASKS;;sleep|1000;;splitlayout;;expand|*;;expand|ACTIVE/add-user-authentication;;select|ACTIVE/add-user-authentication;;focuseditor;;foreground;;sleep|2000" > /dev/null

echo "== 01 tool window"
run "focuseditor;;sleep|1200" > /dev/null
shot_window "$DOCS/01-tool-window.png"

echo "== 02 send-to-terminal submenu"
run "tw|Terminal|show;;sleep|10000;;focuseditor;;sleep|500;;collapse|ACTIVE/add-user-authentication;;rightclick|ACTIVE/add-user-authentication|170;;sleep|800;;submenu|Send to Terminal;;sleep|800;;highlight|Send to Terminal|/spectra-apply;;sleep|600" > /dev/null
shot_region "$DOCS/02-send-to-terminal-menu.png"

echo "== 03 command in terminal"
run "menuclick|/spectra-apply;;sleep|2000" > /dev/null
shot_window "$DOCS/03-command-in-terminal.png"

echo "== 04 author filter"
run "tw|Terminal|hide;;sleep|500;;focuseditor;;sleep|300;;toolbarbtn|Filter by Author;;sleep|1000;;listclick|Alice;;sleep|800;;toolbarbtn|Filter by Author;;sleep|1000" > /dev/null
shot_region "$DOCS/04-filter-by-author.png"

echo "== 05 name filter"
run "esc;;sleep|600;;filter|auth;;sleep|800;;expand|ACTIVE/add-user-authentication;;select|ACTIVE/add-user-authentication;;sleep|300;;foreground;;sleep|800;;filter|auth;;sleep|1000" > /dev/null
shot_window "$DOCS/05-filter-by-name.png"

echo "== stopping sandbox IDE"
kill "$(ide_pid)" 2>/dev/null || true
ls -la "$DOCS"

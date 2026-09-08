#!/bin/zsh
# Prints the CGWindow id of the sandbox IDE's main frame (the window whose width matches $1, default 1400).
# Compiles winid.swift on first use. Needs the Xcode command line tools.
set -e
HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$HERE/../../.." && pwd)
[ -x "$HERE/winid" ] || swiftc -O "$HERE/winid.swift" -o "$HERE/winid"
PID=$(pgrep -f "idea-.*/jbr/Contents/Home/bin/[j]ava" | head -1)
"$HERE/winid" "$PID" | awk -F'\t' -v w="${1:-1400}" '$6 ~ ","w"," {print $1}' | head -1

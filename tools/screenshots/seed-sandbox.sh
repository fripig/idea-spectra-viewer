#!/bin/zsh
# Pre-seeds the runIde sandbox so no "trust project" / tips dialogs get in the way,
# and the dark theme is on. Run before the first runIde.
# usage: seed-sandbox.sh <demo-project-dir>
set -e
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
DEMO=$(cd "$1" && pwd)
PLATFORM=$(grep '^platformVersion' "$ROOT/gradle.properties" | sed 's/.*= *//')
C="$ROOT/build/idea-sandbox/IC-$PLATFORM/config/options"
mkdir -p "$C"
cat > "$C/trusted-paths.xml" <<EOF
<application>
  <component name="Trusted.Paths">
    <option name="TRUSTED_PROJECT_PATHS">
      <map>
        <entry key="$DEMO" value="true" />
      </map>
    </option>
  </component>
  <component name="Trusted.Paths.Settings">
    <option name="TRUSTED_PATHS">
      <list><option value="$(dirname "$DEMO")" /></list>
    </option>
  </component>
</application>
EOF
cat > "$C/ide.general.xml" <<'EOF'
<application>
  <component name="GeneralSettings">
    <option name="showTipsOnStartup" value="false" />
    <option name="confirmExit" value="false" />
  </component>
</application>
EOF
cat > "$C/laf.xml" <<'EOF'
<application>
  <component name="LafManager" autodetect="false">
    <laf themeId="ExperimentalDark" />
  </component>
</application>
EOF
echo "sandbox config seeded at $C"

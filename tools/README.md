# Tools

## README screenshots (`screenshots/`)

Everything needed to re-take the five screenshots in `docs/screenshots/` from a sandbox IDE, without
Accessibility permission and without clicking anything by hand.

```bash
export JAVA_HOME=~/Applications/PhpStorm.app/Contents/jbr/Contents/Home   # any JDK 21+
tools/screenshots/capture.sh
```

That runs, in order:

| Step | Script | What it does |
| --- | --- | --- |
| 1 | `screenshots/make-demo.sh` | Generates the demo project under `screenshots/out/demo-project` (three Active, two Parked, three Archived changes with fixed authors, progress and mtimes). Parked changes live inside `.git/`, which is why the project is generated rather than checked in. |
| 2 | `screenshots/seed-sandbox.sh` | Writes trusted-paths, no-tips and dark-theme settings into the runIde sandbox config so no dialog blocks the run. |
| 3 | `./gradlew runIde` | Starts the sandbox IDE on the demo project, in the background. |
| 4 | `screenshots/agent/build.sh` | Compiles `screenshots/agent/Agent.java` against the sandbox IDE's jars and packs it as a Java agent. |
| 5 | `screenshots/agent/run.sh` | Attaches the agent to the IDE's JVM and runs a `;;`-separated command script that drives Swing directly: places the frame, opens files, expands and selects tree rows, opens the context menu and its submenu, clicks toolbar and popup entries, types into the filter. |
| 6 | `screencapture` | Captures the frame by window id (`screenshots/native/winid.sh`) or, when a popup is showing, the frame's screen region, since popups are separate windows. |

### Working interactively

Once the IDE is up you can send ad-hoc commands, for example:

```bash
tools/screenshots/agent/run.sh Agent "dump"                                # list tree rows with their ids
tools/screenshots/agent/run.sh Agent "expand|*;;select|PARKED/dark-mode-theme"
tools/screenshots/agent/run.sh Agent "windows"                             # visible frames and dialogs, with their buttons
```

The command list is the `switch` in `screenshots/agent/Agent.java`. Two things to know when editing it:

- The JVM never reloads a class it already has, so after each edit build and run under a new name:
  `screenshots/agent/build.sh Agent2` then `screenshots/agent/run.sh Agent2 "..."`
  (or `AGENT_CLASS=Agent2 screenshots/capture.sh`).
- Anything that touches VFS or documents must go through `ApplicationManager.invokeAndWait`
  (the `edt` helper does), not `SwingUtilities.invokeAndWait`, or the write-intent assertion fires.

`screenshots/native/winid.swift` lists windows with `CGWindowListCopyWindowInfo(.optionAll, …)` on purpose: the
sandbox frame is often on another Space, where the on-screen-only variant cannot see it.

# Spectra Viewer

*English · [繁體中文](README.zh-TW.md)*

Browse your [Spectra](https://github.com/spectra-app/spectra) changes right inside your JetBrains IDE — no app switching.

Spectra moves parked changes out of `openspec/changes/` into the git directory, which makes them invisible in the project tree. This plugin adds a **Spectra** tool window that lists Active, Parked, and Archived changes side by side, shows the task progress of each, and opens their Markdown documents directly in the editor.

The plugin reads files directly. It **does not require the Spectra app to be running**, and it never touches Spectra's internal database.

> **Unofficial project**: This is a community-built third-party plugin. It is not affiliated with, nor endorsed by, Spectra. All Spectra-related names belong to their respective owners.

## Features

- **Three groups side by side**: Active (`openspec/changes/`), Parked (`spectra-app/changes/` under the git directory), and Archived (`openspec/changes/archive/`). Group nodes show the change count; while filtering, they show both the matching count and the total.
- **Task progress**: Parses each change's `tasks.md` and shows "completed／total" on the node. Checkboxes inside code blocks are ignored.
- **Open documents**: Double-click an artifact node to open its Markdown in the editor. If the file has been deleted, you get a non-blocking notification instead of an error.
- **Sorting**: Sort by Name, Modified, or Created — Modified (newest first) is the default. Entries with an unknown date always sort last.
- **Name filter**: Type to filter change names in real time (case-insensitive), applied to all three groups at once. All artifacts of a matching change are kept.
- **Refresh**: Re-scanning from the toolbar preserves the expanded state and the filter text.
- **Git worktree support**: When `.git` is a file, the real git directory is resolved via `gitdir:` and `commondir`, and parked changes are located from there.

Sorting and filtering only rebuild the tree — they never re-scan the file system. Scanning itself always runs on a background thread and never blocks the EDT.

## Installation

Download the `.zip` from [Releases](https://github.com/fripig/idea-spectra-viewer/releases), then in your IDE choose **Settings → Plugins → ⚙ → Install Plugin from Disk...** and restart.

Requirements: JetBrains IDE 2026.2 (build 262) or later. The plugin only depends on `com.intellij.modules.platform`, so it installs on any JetBrains IDE — PhpStorm, IntelliJ IDEA, and the rest.

## Development

```bash
./gradlew build          # compile and run unit tests
./gradlew buildPlugin    # produce build/distributions/*.zip
./gradlew runIde         # try it out in a sandbox IDE
```

The compilation target is JVM 21, but since the build compiles against IntelliJ Platform 2026.2 artifacts, it requires a newer JDK to run (CI uses JDK 25).

Release process: push a `release-<version>` tag, and GitHub Actions derives `PLUGIN_VERSION` from the tag, builds, tests, and publishes the Release.

## Project structure

```
src/main/kotlin/com/github/fripig/spectraviewer/
├── discovery/   # file system scanning, parsing tasks.md and .openspec.yaml
├── model/       # SpectraChange, sorting rules
└── toolwindow/  # tool window UI and tree nodes
openspec/specs/  # Spectra specs (this project is itself developed with SDD)
```

## License

[MIT](LICENSE) © fripig

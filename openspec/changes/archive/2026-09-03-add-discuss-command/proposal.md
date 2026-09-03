## Why

右鍵 submenu 目前只提供 `/spectra-apply`、`/spectra-ingest`、`/spectra-archive`、`/spectra-commit` 四個指令，少了流程最前面的 `/spectra-discuss`。要在既有 change 的脈絡下開討論時，使用者得自己打指令與 change 名稱，與其他步驟的體驗不一致。

## What Changes

- Submenu 新增 `/spectra-discuss <change-name>` 項目，排在第一位。
- Submenu 順序改為對齊 CLAUDE.md 的 workflow：`/spectra-discuss → /spectra-apply → /spectra-ingest → /spectra-archive → /spectra-commit`。
- 指令文字格式不變：slash command、一個空格、change 名稱，不帶換行。
- Submenu 的啟用條件、Send to Terminal / Copy Command 切換、剪貼簿 fallback 全部不變。
- 使用者文件（README、plugin.xml 的 change-notes）同步改為五個指令。

## Non-Goals

- 不加 `/spectra-propose`：它用來建立新的 change，對既有 change 名稱沒有意義，放進針對「已選取 change」的 submenu 會是一個永遠不該按的選項。
- 不加其他 spectra 指令（analyze、verify、drift、debug、ask 等）：這次只補齊 CLAUDE.md 列出的主流程。
- 不改 submenu 的啟用邏輯、終端偵測、剪貼簿行為。
- 不為 discuss 另設「不帶 change 名稱」的文字格式；spectra-discuss 明文接受 change 名稱作為主題。

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `changes-tool-window`: Requirement「Send a Spectra command for the selected change」由四個指令改為五個，並固定新順序。

## Impact

- Affected specs: `changes-tool-window`（修改既有 Requirement 與其 Example 表）
- Affected code:
  - Modified:
    - src/main/kotlin/com/github/fripig/spectraviewer/toolwindow/ChangeTreeNodes.kt（SpectraCommand enum 新增 DISCUSS 成員並調整宣告順序）
    - src/test/kotlin/com/github/fripig/spectraviewer/toolwindow/CommandTextTest.kt（順序測試與參數化案例改為五個指令）
    - README.md（Send a command 段落列出五個指令）
    - src/main/resources/META-INF/plugin.xml（change-notes 新增本版 What's New）
  - New: (none)
  - Removed: (none)
- 發版：此變更影響功能，需打 release tag，打 tag 前先更新 change-notes。

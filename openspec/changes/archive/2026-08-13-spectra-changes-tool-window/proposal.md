## Why

Spectra 把 parked change 移出 `openspec/changes/`，實際存放在 `.git/spectra-app/changes/<name>/`。IDE 的專案樹預設不顯示 `.git/` 內容，因此開發者在 PhpStorm 裡完全看不到自己暫存了哪些 change，只能切到終端機執行 `spectra list --parked` 才知道。這讓「暫存後忘記」成為常態。

本變更提供一個 IntelliJ Platform plugin，在 IDE 內以 Tool Window 統一呈現 active、parked、archived 三類 change，讓 parked change 重新變成可見、可點開的東西。

## What Changes

- 新增一個 Gradle + Kotlin 的 IntelliJ Platform plugin 專案骨架（本 repository 目前沒有任何程式碼）。
- 新增 change 掃描層：直接讀取檔案系統解析三個來源目錄，不呼叫 `spectra` CLI，因此使用者環境沒有安裝 CLI 也能運作。
  - active：專案根目錄下的 `openspec/changes/<name>/`
  - parked：專案根目錄下的 `.git/spectra-app/changes/<name>/`
  - archived：專案根目錄下的 `openspec/changes/archive/<name>/`
- 新增名為 Spectra 的 Tool Window，以樹狀結構分成三組顯示 change，節點標示任務完成進度（例如 3/8）。
- 點擊 change 底下的 artifact 節點，在編輯器開啟對應的 markdown 檔案。
- 提供手動 Refresh 動作重新掃描。

## Non-Goals

- 不做任何寫入操作：不提供 park、unpark、archive、勾選任務等會改變 change 狀態的功能。第一版純唯讀。
- 不呼叫 `spectra` CLI，也不讀取 `.git/spectra-app/spectra.db`。後者是 Spectra 的內部資料庫，格式不對外保證。
- 不做檔案系統監聽自動刷新，使用者以 Refresh 動作觸發重新掃描。
- 不做 markdown 的自訂渲染，開檔一律交給 IDE 既有的 markdown 編輯器。
- 不上架 JetBrains Marketplace，本階段只產出可本機安裝的 plugin。

## Capabilities

### New Capabilities

- `change-discovery`: 掃描 active、parked、archived 三個來源目錄，解析每個 change 的名稱、狀態、artifact 檔案清單與任務完成進度，產出供 UI 使用的資料模型。
- `changes-tool-window`: 在 IDE 內以 Tool Window 樹狀呈現掃描結果，支援分組顯示、進度標示、點擊開檔與手動重新整理。

### Modified Capabilities

（無）

## Impact

- Affected specs：新增 `change-discovery` 與 `changes-tool-window` 兩份 spec。
- Affected code：
  - New:
    - `settings.gradle.kts`
    - `build.gradle.kts`
    - `gradle.properties`
    - `gradle/wrapper/gradle-wrapper.properties`
    - `src/main/resources/META-INF/plugin.xml`
    - `src/main/kotlin/com/github/fripig/spectraviewer/model/SpectraChange.kt`
    - `src/main/kotlin/com/github/fripig/spectraviewer/discovery/ChangeScanner.kt`
    - `src/main/kotlin/com/github/fripig/spectraviewer/discovery/TaskProgressParser.kt`
    - `src/main/kotlin/com/github/fripig/spectraviewer/toolwindow/SpectraToolWindowFactory.kt`
    - `src/main/kotlin/com/github/fripig/spectraviewer/toolwindow/SpectraChangesPanel.kt`
    - `src/main/kotlin/com/github/fripig/spectraviewer/toolwindow/ChangeTreeNodes.kt`
    - `src/test/kotlin/com/github/fripig/spectraviewer/discovery/ChangeScannerTest.kt`
    - `src/test/kotlin/com/github/fripig/spectraviewer/discovery/TaskProgressParserTest.kt`
  - Modified:
    - `.gitignore`
  - Removed:（無）
- Dependencies：新增 Gradle IntelliJ Platform Gradle Plugin 與 Kotlin JVM plugin，plugin 本身不引入額外的 runtime 相依套件。
- Behavior：安裝 plugin 後，PhpStorm 側邊出現 Spectra Tool Window；未初始化 Spectra 的專案顯示空狀態提示。

# Spectra Viewer

*[English](README.md) · 繁體中文*

在 JetBrains IDE 裡瀏覽 [Spectra](https://github.com/spectra-app/spectra) 的 changes，不用切app。

Spectra 會把 park 起來的 change 從 `openspec/changes/` 移到 git 目錄底下，專案樹因此完全看不到它們。這個 plugin 提供一個 **Spectra** tool window，把 Active、Parked、Archived 三類 change 並列顯示，標示各自的任務進度，並可直接在編輯器開啟它們的 Markdown 文件。

Plugin 直接讀檔案，**不需開啟 Spectra app**，也不會去碰 Spectra 的內部資料庫。

> **非官方專案**：這是由社群開發的第三方 plugin，與 Spectra 官方沒有隸屬關係，也未經其背書。Spectra 相關名稱屬於各自的擁有者。

## 功能

- **三組並列**：Active（`openspec/changes/`）、Parked（git 目錄下的 `spectra-app/changes/`）、Archived（`openspec/changes/archive/`）。group 節點會顯示 change 數量；篩選時同時顯示符合數與總數。
- **任務進度**：解析 change 的 `tasks.md`，在節點上顯示「完成／總數」。程式碼區塊內的 checkbox 會被忽略。
- **開啟文件**：雙擊 artifact 節點即在編輯器開啟該 Markdown。檔案已被刪除時只跳非阻斷式通知，不會噴錯。
- **排序**：可依 Name、Modified、Created 排序，預設 Modified（最新在前）。日期未知者一律排最後。
- **名稱篩選**：輸入文字即時過濾 change 名稱（不分大小寫），三組同時套用；符合的 change 其 artifact 全部保留。
- **Refresh**：工具列的重新掃描會保留展開狀態與篩選文字。
- **Git worktree 支援**：`.git` 是檔案時會依 `gitdir:` 與 `commondir` 解析出真正的 git 目錄，再從中尋找 parked changes。

排序與篩選都只重建樹，不會重新掃描檔案系統；掃描本身一律在背景執行緒進行，不阻塞 EDT。

## 安裝

從 [Releases](https://github.com/fripig/idea-spectra-viewer/releases) 下載 `.zip`，在 IDE 中選 **Settings → Plugins → ⚙ → Install Plugin from Disk...** 安裝後重啟。

需求：JetBrains IDE 2026.2（build 262）以上。Plugin 只依賴 `com.intellij.modules.platform`，因此 PhpStorm、IntelliJ IDEA 等所有 JetBrains IDE 都能安裝。

## 開發

```bash
./gradlew build          # 編譯並執行單元測試
./gradlew buildPlugin    # 產出 build/distributions/*.zip
./gradlew runIde         # 在沙箱 IDE 中試跑
```

編譯目標為 JVM 21，但因為要對著 IntelliJ Platform 2026.2 的產物編譯，建置本身需要較新的 JDK（CI 使用 JDK 25）。

發版流程：推送 `release-<version>` tag，GitHub Actions 會由 tag 推導 `PLUGIN_VERSION`、建置、測試並發布 Release。

## 專案結構

```
src/main/kotlin/com/github/fripig/spectraviewer/
├── discovery/   # 掃描檔案系統、解析 tasks.md 與 .openspec.yaml
├── model/       # SpectraChange、排序規則
└── toolwindow/  # tool window UI 與樹狀節點
openspec/specs/  # Spectra 規格（本專案自身以 SDD 開發）
```

## 授權

[MIT](LICENSE) © fripig
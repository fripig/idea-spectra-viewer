## 1. 指令清單

- [x] 1.1 先改測試（TDD red）：`src/test/kotlin/com/github/fripig/spectraviewer/toolwindow/CommandTextTest.kt` 的 `the four commands are offered in workflow order` 改名為 five 版本，期望 `SpectraCommand.entries.map { it.slashCommand }` 等於 `/spectra-discuss`、`/spectra-apply`、`/spectra-ingest`、`/spectra-archive`、`/spectra-commit` 這個順序；參數化案例加入 `DISCUSS, /spectra-discuss add-search`。驗證：跑 `./gradlew test --no-watch-fs --tests '*CommandTextTest*'`，看到此測試因 `DISCUSS` 不存在而編譯失敗或斷言失敗。
- [x] 1.2 讓測試通過（TDD green），實作 spec Requirement「Send a Spectra command for the selected change」：在 `src/main/kotlin/com/github/fripig/spectraviewer/toolwindow/ChangeTreeNodes.kt` 的 `SpectraCommand` enum 新增 `DISCUSS("/spectra-discuss")` 並放在第一個，其餘順序維持 apply、ingest、archive、commit；同步更新 enum 上方的順序註解，說明 discuss 開啟一個 change、commit 收尾所以在最後。行為：右鍵單一 change 時 submenu 由上到下出現五個指令，且沒有 `/spectra-propose`。驗證：`./gradlew test --no-watch-fs` 全綠，並在 Run IDE 中手動右鍵一個 change 確認順序與項目文字。

## 2. 使用者文件

- [x] 2.1 [P] `README.md` 的「Send a command to your terminal」段落列出五個指令並依新順序排列。驗證：content review，段落中 `/spectra-discuss` 出現在 `/spectra-apply` 之前，指令總數為五。
- [x] 2.2 [P] `src/main/resources/META-INF/plugin.xml` 的 change-notes 新增下一版 What's New 條目，說明 submenu 加入 `/spectra-discuss` 並改為 workflow 順序。驗證：`./gradlew verifyPlugin --no-watch-fs` 通過，且條目出現在最新版本區塊。

## 3. 驗證與發版

- [x] 3.1 完整建置與測試：`./gradlew build --no-watch-fs` 成功，測試全綠，verifyPlugin 除既有 13 筆 experimental API 警告外沒有新問題。驗證：貼出 gradle 輸出。
- [ ] 3.2 發版：確認 change-notes 已含本版條目後，bump 版本並打 release tag。驗證：`git tag` 列出新 tag，且 tag 指向含 change-notes 的 commit。

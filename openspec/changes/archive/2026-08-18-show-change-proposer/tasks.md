## 1. Metadata 解析

- [x] 1.1 為顯示名稱擷取規則新增失敗測試：在 src/test/kotlin/com/github/fripig/spectraviewer/discovery/ChangeMetadataParserTest.kt 覆蓋 `fripig <fripig@gmail.com>` 取得 `fripig`、`Alice Chen <a@example.com>` 取得 `Alice Chen`、無角括號的 `fripig` 取得 `fripig`、`<fripig@gmail.com>` 取得 null、值為空白取得 null，以及欄位缺失、檔案不存在各取得 null。驗證：執行 ./gradlew test，這些新測試因 API 尚未存在而編譯失敗或斷言失敗。
- [x] 1.2 擴充 ChangeMetadataParser 單次掃描取兩個欄位：讓 parser 一次讀檔即同時回傳建立日期與提案者顯示名稱，維持既有的 100 行掃描上限、只認 column 0 起始的 top-level 欄位，以及任何異常一律降級為 null 而不拋例外。不新增 YAML 函式庫依賴。驗證：1.1 的全部測試通過，且既有的 created 相關測試維持通過（執行 ./gradlew test）。

## 2. Model 與掃描

- [x] 2.1 SpectraChange 新增 createdBy 欄位：型別為 `String?`、不提供預設值，使既有建構點必須明確表態。驗證：執行 ./gradlew build，src/main/kotlin/com/github/fripig/spectraviewer/discovery/ChangeScanner.kt、src/test/kotlin/com/github/fripig/spectraviewer/model/ChangeOrderTest.kt、src/test/kotlin/com/github/fripig/spectraviewer/toolwindow/CopySelectionTest.kt 全部補上該參數後編譯通過。
- [x] 2.2 使掃描結果符合 Report per-change metadata：ChangeScanner 讀取每個 change 時把提案者顯示名稱填入 `createdBy`，metadata 檔缺失或損壞時為 null 且該 change 仍出現在掃描結果中。驗證：在 src/test/kotlin/com/github/fripig/spectraviewer/discovery/ChangeScannerTest.kt 新增兩個案例——metadata 含 `created_by` 時 `createdBy` 被填入、完全沒有 metadata 檔時 `createdBy` 為 null 但 change 仍被回報——執行 ./gradlew test 通過。

## 3. Tool window 渲染

- [x] 3.1 實作 Show the proposer on change nodes 並固定 change node 排列順序：change node 依「名稱、提案者、任務進度」的順序 append，提案者使用與進度相同的 `SimpleTextAttributes.GRAYED_ATTRIBUTES`；`createdBy` 為 null 時完全不 append 提案者那一段，不輸出佔位文字。驗證：新增 src/test/kotlin/com/github/fripig/spectraviewer/toolwindow/ChangeNodeRenderingTest.kt，測試 change node 的文字組裝，覆蓋 spec 中「node text by proposer and progress」表格的四種組合（有提案者有進度、無提案者有進度、有提案者無進度、皆無），執行 ./gradlew test 通過。
- [x] 3.2 確認三個群組共用渲染路徑：SpectraTreeCellRenderer 對 change node 不依 ChangeGroup 分流，Active、Parked、Archived 的提案者顯示行為完全一致，且 archived change 顯示 `created_by` 而非 `archived_by`。驗證：在 src/test/kotlin/com/github/fripig/spectraviewer/toolwindow/ChangeNodeRenderingTest.kt 新增一個測試，對三個群組各建一個帶提案者的 SpectraChange，斷言三者產出的節點文字皆含提案者；執行 ./gradlew test 通過。
- [x] 3.3 確認提案者不外溢到既有行為：排序（ChangeOrder）、名稱篩選（filterChanges）、複製（copyTextFor）與節點 id（SpectraNode.id）皆不受 `createdBy` 影響。驗證：在 src/test/kotlin/com/github/fripig/spectraviewer/toolwindow/ChangeNodeRenderingTest.kt 新增測試，斷言以提案者名稱作為篩選字串時不會match到該 change、複製結果仍只有 change 名稱；執行 ./gradlew test 通過。

## 4. 文件與整體驗證

- [x] 4.1 [P] README.md 的 Features 段落說明 change node 會顯示提案者、來源為 `.openspec.yaml` 的 `created_by`、只顯示名稱不顯示 email、未知時不顯示。驗證：人工閱讀該段落，確認描述與 specs/changes-tool-window/spec.md 的行為一致。
- [x] 4.2 [P] README.zh-TW.md 同步 4.1 的說明。驗證：人工閱讀，確認與 README.md 對應段落語意一致。
- [x] 4.3 整體驗證：執行 ./gradlew build 全綠；執行 ./gradlew runIde，在沙箱 IDE 開啟本專案的 Spectra tool window，確認 Archived 群組每個 change 皆顯示 `fripig`，且進度計數仍在該列尾端。驗證：貼上 build 的輸出結果，並記錄 runIde 的實際觀察。

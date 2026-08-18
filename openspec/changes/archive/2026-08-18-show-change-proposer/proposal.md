## Why

Spectra 的 `.openspec.yaml` 已經記錄了每個 change 的提案者（`created_by`），但 tool window 完全沒有把它呈現出來。在多人協作的 repo 裡，使用者看著 Active／Parked／Archived 三個群組的清單，無法分辨哪些 change 是自己提的、哪些是別人提的，只能逐一開檔案確認。把提案者顯示在 change node 上，一眼就能分流。

## What Changes

- `ChangeMetadataParser` 從「只解析 `created`」擴充為「解析 `created` 與 `created_by` 兩個 top-level 欄位」，沿用既有的逐行掃描與「任何異常都解析成 null」策略，不引入 YAML 函式庫。
- `created_by` 的值會擷取顯示名稱：取 `<` 之前的子字串並去除前後空白；沒有 `<` 時使用整個值去除空白後的結果；結果為空字串時視為未知。
- `SpectraChange` 新增 `createdBy: String?` 欄位，與既有的 `created`／`modified` 一樣不給預設值。
- `ChangeScanner` 在讀取每個 change 時一併填入 `createdBy`。
- Tool window 的 change node 在名稱與任務進度之間，以灰字顯示提案者。Active、Parked、Archived 三個群組行為一致。
- 提案者未知時該段不渲染，不顯示 `unknown` 之類的佔位文字。

## Non-Goals

- 不顯示 email。`created_by` 內 `<>` 包住的 email 一律捨棄，因為 tree 一列的寬度有限，完整字串會擠掉任務進度。
- 不使用 `created_with`（記錄產出 change 的 AI 工具）。本次變更定義的「提案者」是人，不是 agent。
- 不讀取 `archived_by`／`archived_at`。Archived 群組同樣顯示 `created_by`，而不是封存者。
- 不做「只有群組內存在兩位以上提案者才顯示」的動態隱藏規則。這會讓同一個 change 的顯示內容隨其他 change 而變動，難以預期，也無法穩定測試。
- 不新增依提案者排序或篩選的功能。本次只做顯示。
- 不改動 Copy Change Name 的複製內容，剪貼簿仍然只有 change 名稱。

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `change-discovery`: 每個 change 的 metadata 除了建立日期外，還要回報提案者顯示名稱，並定義顯示名稱的擷取規則與各種無法使用時的退場行為。
- `changes-tool-window`: change node 除了名稱與任務進度外，還要顯示提案者，並定義提案者未知時的渲染行為。

## Impact

- Affected specs: `change-discovery`、`changes-tool-window`
- Affected code:
  - Modified:
    - src/main/kotlin/com/github/fripig/spectraviewer/discovery/ChangeMetadataParser.kt
    - src/main/kotlin/com/github/fripig/spectraviewer/discovery/ChangeScanner.kt
    - src/main/kotlin/com/github/fripig/spectraviewer/model/SpectraChange.kt
    - src/main/kotlin/com/github/fripig/spectraviewer/toolwindow/ChangeTreeNodes.kt
    - src/test/kotlin/com/github/fripig/spectraviewer/discovery/ChangeMetadataParserTest.kt
    - src/test/kotlin/com/github/fripig/spectraviewer/discovery/ChangeScannerTest.kt
    - src/test/kotlin/com/github/fripig/spectraviewer/model/ChangeOrderTest.kt
    - src/test/kotlin/com/github/fripig/spectraviewer/toolwindow/CopySelectionTest.kt
    - README.md
    - README.zh-TW.md
  - New:
    - src/test/kotlin/com/github/fripig/spectraviewer/toolwindow/ChangeNodeRenderingTest.kt
  - Removed: (none)
- 無新增外部依賴，掃描仍然只讀既有的 `.openspec.yaml`，不增加額外的檔案開啟次數。

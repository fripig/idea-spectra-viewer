## Why

Spectra Viewer 目前把每個分組內的 change 固定依名稱排序，且沒有任何篩選手段。名稱排序對 Active 分組還堪用，但 Archived 分組會隨專案歷史單向成長，找「上週那個 change」只能靠肉眼掃。使用者手上已有的資訊多半是時間（什麼時候建的、最近改過什麼）與名稱片段，而不是完整名稱。

Spectra CLI 本身早就提供 `spectra list --sort name|modified|created`，預設 `modified`。IDE 端缺這個能力，等於逼使用者為了排序切回終端機——正是這個 plugin 當初要消除的動作。

## What Changes

- 分組內排序改為可切換：Name、Modified、Created 三種，預設 Modified。方向與 CLI 一致——Name 為遞增，兩種日期為新到舊。
- 新增名稱子字串篩選，即時輸入、不分大小寫，同時套用於三個分組。
- 分組節點在篩選生效時把計數改為「符合數/總數」，未篩選時維持單一數字。
- 掃描層新增兩個日期欄位：建立日期取自 `.openspec.yaml` 的 `created`，修改日期取 change 目錄下所有 Markdown 檔案的最新修改時間。
- **BREAKING**（僅限內部契約）：掃描層不再負責排序。原本寫在 `change-discovery` 的「依名稱排序」要求移除，排序改由呈現層負責。

## Non-Goals

- 不做跨分組排序。三個分組本身的順序固定為 Active、Parked、Archived。
- 不篩選 artifact 節點。篩選只比對 change 名稱；命中的 change 其底下 artifact 全部顯示。
- 不做狀態篩選（draft / in-progress / complete）或分組開關。本次只做名稱篩選。
- 不持久化排序與篩選狀態。IDE 重啟後回到預設值。
- 不做正規表示式或模糊比對。單純子字串。
- 不讀取 `.openspec.yaml` 的其他欄位（`created_by`、`archived_at` 等）。

## Capabilities

### New Capabilities

（無）

### Modified Capabilities

- `change-discovery`: 移除「分組內依名稱排序」的要求，改由呈現層決定順序；每個 change 額外報告建立日期與修改日期兩個欄位。
- `changes-tool-window`: 新增分組內排序與名稱篩選；分組節點計數在篩選生效時改為符合數與總數。

## Impact

- Affected specs：`change-discovery` 與 `changes-tool-window` 兩份既有 spec 皆為修改，無新增 capability。
- Affected code：
  - Modified:
    - `src/main/kotlin/com/github/fripig/spectraviewer/model/SpectraChange.kt`
    - `src/main/kotlin/com/github/fripig/spectraviewer/discovery/ChangeScanner.kt`
    - `src/main/kotlin/com/github/fripig/spectraviewer/toolwindow/ChangeTreeNodes.kt`
    - `src/main/kotlin/com/github/fripig/spectraviewer/toolwindow/SpectraChangesPanel.kt`
    - `src/test/kotlin/com/github/fripig/spectraviewer/discovery/ChangeScannerTest.kt`
  - New:
    - `src/main/kotlin/com/github/fripig/spectraviewer/model/ChangeOrder.kt`
    - `src/main/kotlin/com/github/fripig/spectraviewer/discovery/ChangeMetadataParser.kt`
    - `src/test/kotlin/com/github/fripig/spectraviewer/discovery/ChangeMetadataParserTest.kt`
    - `src/test/kotlin/com/github/fripig/spectraviewer/model/ChangeOrderTest.kt`
  - Removed:（無）
- Dependencies：無新增。日期解析只取 `.openspec.yaml` 的單一欄位，不引入 YAML 函式庫。
- Behavior：既有掃描結果的內容不變，只多兩個欄位；分組內的預設順序由名稱遞增改為修改時間新到舊。

## Why

Spectra 3.0.0 的 `spectra init` 已經把新專案初始化在 `docs/spectra/`，並在專案根目錄的 `.spectra.yaml` 寫入未註解的 `spec_dir: docs/spectra`。本外掛的掃描器把 `openspec` 寫成字面常數，並以該目錄是否存在作為「這是不是 Spectra 專案」的判定，因此**任何用現行 Spectra 建立的新專案，工具視窗都會停在「This project is not initialised for Spectra.」空狀態**，即使該專案已有變更。

`.spectra.yaml` 自身的註解把相容規則寫得很明確：新專案初始化在 `docs/spectra`，沒有這個欄位的設定則解析為 `openspec`。兩種版面必須同時支援。

## What Changes

- discovery 層新增一個解析步驟：讀取專案根目錄的 `.spectra.yaml`，決定 spec 目錄的位置。
  - 有 `spec_dir` 且為非空字串 → 使用它（相對於專案根目錄解析）。
  - 檔案不存在，或存在但沒有 `spec_dir` 欄位 → 解析為 `openspec`，且不寫入任何警告（這是舊專案的正常狀態）。
  - 檔案無法解析、`spec_dir` 型別不對，或解析後落在專案根目錄之外 → 解析為 `openspec`，並寫入一則 IDE log 警告。
- 「這是不是 Spectra 專案」的判定改為檢查解析後的 spec 目錄是否存在，不再檢查字面的 `openspec` 目錄。
- 作用中與已封存變更的來源路徑改為跟隨解析結果；暫存變更的來源維持由已解析 git 目錄決定。
- 每個變更目錄內的 `.openspec.yaml` metadata 檔名不變。
- 解析採用與既有 metadata 讀取相同的手寫行掃描方式，不引入 YAML 函式庫相依。
- `change-discovery` 與 `changes-tool-window` 規格中把 `openspec/` 當作字面常數描述的需求文字一併更新。

## Non-Goals (optional)

- **不自行探測磁碟版面。** 不實作「沒有 `spec_dir` 欄位但 `docs/spectra/` 存在就改用它」這類推測式 fallback。外掛一旦採用 Spectra CLI 沒有的解析規則，就會出現 CLI 判定未初始化、工具視窗卻列出變更（或反之）的分歧，這種分歧沒有錯誤訊息、只有數字對不上的症狀，難以歸因。
- **不引入 YAML 函式庫。** 既有的 metadata 讀取已明確選擇手寫行掃描而非扛整個 YAML 相依，`spec_dir` 是同類型的單一純量欄位，沿用同樣做法。
- **不同時掃描兩個目錄合併顯示。**
- **不變更暫存變更的位置**，也不變更變更目錄內的 metadata 檔名。
- **不讀取 `.spectra.yaml` 中 `spec_dir` 以外的欄位。**
- **不變更空狀態訊息文字。** 現行訊息未寫死任何目錄名稱，仍然正確。

## Capabilities

### New Capabilities

（無）

### Modified Capabilities

- `change-discovery`: 變更來源目錄的定位方式從字面的 `openspec/` 改為由 `.spectra.yaml` 的 `spec_dir` 決定，並新增解析與 fallback 規則的需求。
- `changes-tool-window`: 空狀態的觸發條件從「專案根目錄有無 `openspec` 目錄」改為「解析後的 spec 目錄是否存在」。

## Impact

- Affected specs: `change-discovery`、`changes-tool-window`
- Affected code:
  - New:
    - `src/main/kotlin/com/github/fripig/spectraviewer/discovery/SpecDirResolver.kt`
    - `src/test/kotlin/com/github/fripig/spectraviewer/discovery/SpecDirResolverTest.kt`
  - Modified:
    - `src/main/kotlin/com/github/fripig/spectraviewer/discovery/ChangeScanner.kt`
    - `src/test/kotlin/com/github/fripig/spectraviewer/discovery/ChangeScannerTest.kt`
  - Removed:（無）
- 相依套件：無新增。
- 相容性：既有專案（無 `.spectra.yaml`、或該檔未設定 `spec_dir`）的行為完全不變。

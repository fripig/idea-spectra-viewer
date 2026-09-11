## Context

`ChangeScanner` 目前以 `projectRoot.resolve(OPENSPEC_DIR)` 組出 spec 目錄，並在該目錄不是目錄時直接回傳 `SpectraSnapshot.NOT_A_SPECTRA_PROJECT`，作用中與已封存變更再由其下的 `changes/` 推導。`OPENSPEC_DIR` 是寫死的 `"openspec"`。

Spectra 3.0.0 的 `spectra init` 會把新專案初始化在 `docs/spectra/`，並在專案根目錄寫出含未註解 `spec_dir: docs/spectra` 的 `.spectra.yaml`。該檔案自身的註解記載了相容規則：新專案初始化在 `docs/spectra`，沒有這個欄位的設定則解析為 `openspec`。

既有約束與慣例：

- `ChangeMetadataParser` 已明確記載一項取捨：為了少數純量欄位不扛整個 YAML 相依，改以手寫行掃描讀取 Spectra 寫出的扁平鍵值檔，並對每一種偏差（檔案不存在、欄位不存在、值不合用、巢狀文件）解析為「未知」而非錯誤。`build.gradle.kts` 因此沒有任何 YAML 相依。
- 掃描器已有 `warn: (String, Throwable?) -> Unit` 參數，非致命問題寫入 IDE log 而非彈出對話框。
- discovery 層是純 JVM 程式碼，測試不需要 IDE fixture；`build.gradle.kts` 刻意不引入平台測試框架。
- 本儲存庫自身的 `.spectra.yaml` 使用較舊的模板，`spec_dir` 那行是註解掉的，因此它本身就是「有設定檔但無該欄位」這條路徑的真實樣本。

## Goals / Non-Goals

**Goals**

- 讓現行 Spectra 建立的新專案（`docs/spectra/` 版面）能被正確掃描與顯示。
- 讓舊 Spectra／OpenSpec 專案（`openspec/` 版面）的行為完全不變。
- 讓解析規則與 Spectra CLI 逐字一致。
- 把解析邏輯集中在單一位置，讓未來版面再變時只有一個落點。

**Non-Goals**

- 不依據磁碟上存在哪個目錄來推測版面。
- 不引入 YAML 函式庫相依。
- 不改變暫存變更的位置，也不改變 `.openspec.yaml` 的檔名。
- 不改動空狀態訊息文字。
- 不讀取 `.spectra.yaml` 中 `spec_dir` 以外的欄位。

## Decisions

### 解析規則逐字對齊 Spectra CLI，不做磁碟探測

依 `.spectra.yaml` 的欄位決定 spec 目錄，欄位不存在時解析為 `openspec`。不實作「沒有 `spec_dir` 欄位但 `docs/spectra/` 存在就改用它」這類推測。

理由：外掛只要採用 CLI 沒有的規則，就會出現 CLI 判定未初始化而工具視窗列出變更（或反之）的分歧。這種分歧沒有錯誤訊息，在使用者端極難歸因。一致性的價值高於多救回幾個邊緣專案。

考慮過的替代方案：先看設定、設定沒講再探測磁碟。被否決，理由同上。

### 沿用手寫行掃描讀取 spec_dir，不引入 YAML 相依

新增 `SpecDirResolver`，以與 `ChangeMetadataParser` 相同的方式讀取 `.spectra.yaml`：逐行掃描、只認第零欄開始的頂層鍵、只取第一次出現、上限行數、去除行尾註解與包裹引號。

理由：`ChangeMetadataParser` 已為同一類問題做過這個取捨並記錄理由，`spec_dir` 是同型別的單一純量欄位。為了第三個純量欄位引入 YAML 相依會推翻既有取捨，並讓外掛體積與相依面積都變大。

考慮過的替代方案：引入 snakeyaml 並同時重寫 metadata 解析。被否決，理由是它把一個相容性修正擴大成相依性決策的翻案，兩件事應該分開。

行掃描無法辨識的形態（例如 `spec_dir` 出現在巢狀 mapping 底下）一律視為「沒有這個欄位」，解析為 `openspec` 且不警告，與 metadata 解析對巢狀文件的處理一致。

### 解析失敗一律退回 openspec 並寫入 IDE log

`spec_dir` 值去除空白後為空、為絕對路徑、或解析後逃出專案根目錄時，一律退回 `openspec`，並透過既有的 `warn` 參數寫入一則 IDE log 訊息。

理由：退回讓設定檔打錯字的專案仍然可用；IDE log 是本外掛既定的非致命問題呈現管道，不需要新的 UI 狀態。

「欄位不存在」與「欄位不合用」必須分開：前者是舊專案的正常狀態，不得寫入警告；後者才是設定錯誤。

### 解析器獨立成 discovery 物件，ChangeScanner.scan 簽名不變

新增 `SpecDirResolver` 物件，`ChangeScanner` 的 `OPENSPEC_DIR` 常數由對它的呼叫取代，`scan(projectRoot, warn)` 的簽名與回傳型別不變。

理由：解析有獨立的行為表與邊界情況，需要自己的單元測試；放進 `ChangeScanner` 會讓掃描測試同時承擔解析矩陣。刪除測試：移除這個物件，掃描器就得內嵌整套 fallback 規則，且無法單獨測試解析矩陣。它藏了檔案不存在、欄位不存在、值不合用、路徑逃逸四種情況與 fallback 決策，不是純轉發。

### 是否為 Spectra 專案改以解析後的 spec 目錄判定

`ChangeScanner.scan` 先解析 spec 目錄，再以該目錄是否為目錄決定回傳 `NOT_A_SPECTRA_PROJECT` 或完整快照。

理由：這是使用者實際看到的症狀所在。來源路徑修好而判定沒修，新專案仍會停在空狀態，掃描結果根本沒機會顯示。

### 規格文字改以解析後的 spec 目錄表述

`change-discovery` 與 `changes-tool-window` 規格現有需求把 `openspec/` 寫成字面常數。實作改動後這些文字會與程式碼直接矛盾，因此同一變更內一併更新，並新增解析規則本身的需求與情境。

## Implementation Contract

**解析器的可觀察行為**

輸入為專案根目錄路徑與一個警告回呼。輸出為解析後 spec 目錄的路徑，警告透過回呼送出。

| 情況 | spec 目錄 | 警告 |
| --- | --- | --- |
| `.spectra.yaml` 不存在或無法讀取 | `<根目錄>/openspec` | 無 |
| 檔案存在但無頂層 `spec_dir` 鍵 | `<根目錄>/openspec` | 無 |
| `spec_dir` 為非空值且解析後仍位於根目錄內 | `<根目錄>/<spec_dir>` | 無 |
| `spec_dir` 只出現在縮排的巢狀位置 | `<根目錄>/openspec` | 無 |
| `spec_dir` 值去除空白與引號後為空 | `<根目錄>/openspec` | 有 |
| `spec_dir` 為絕對路徑，或解析後逃出根目錄 | `<根目錄>/openspec` | 有 |

值的正規化：去除行尾註解、去除包裹的單引號或雙引號、去除前後空白。只有第一個頂層 `spec_dir` 生效，之後的重複出現不得覆蓋。掃描行數設上限，避免為了確認沒有該欄位而讀完一個異常巨大的檔案。

**掃描行為**

`scan` 先解析 spec 目錄。該路徑不是目錄時回傳 `NOT_A_SPECTRA_PROJECT`。是目錄時，作用中變更取自其下 `changes/`（排除名為 `archive` 的目錄），已封存變更取自 `changes/archive/`，暫存變更維持取自已解析 git 目錄下的 `spectra-app/changes/`。任一來源目錄不存在時對應群組為空且不拋出例外，與現行行為相同。

**範圍界線**

- 在範圍內：新增 `SpecDirResolver`、`ChangeScanner` 的 spec 目錄定位與專案判定、兩份規格的對應需求文字、以及對應的單元測試。
- 不在範圍內：暫存變更的解析、`.openspec.yaml` 的讀取、排序與篩選、工具視窗的空狀態文字、其他編輯器的 viewer。

**驗收條件**

- `./gradlew test` 全數通過，其中 `SpecDirResolverTest` 覆蓋上表每一列。
- `ChangeScannerTest` 新增案例：專案根目錄含 `spec_dir` 指向 `docs/spectra` 的設定檔時，作用中與已封存變更自該目錄下被掃出，且 `isSpectraProject` 為真。
- `ChangeScannerTest` 新增案例：設定 `spec_dir: docs/spectra` 且 `openspec/changes/` 下另有變更時，作用中群組不含後者。
- `ChangeScannerTest` 既有案例在不新增設定檔的情況下維持通過，證明舊版面行為未變。
- `build.gradle.kts` 的相依區塊在本變更後不含任何 YAML 函式庫。

## Risks / Trade-offs

- **手寫行掃描無法處理所有合法 YAML 形態** → 只影響非 Spectra 自身產生的設定檔；Spectra 寫出的是扁平鍵值檔。無法辨識時退回 `openspec`，與既有 metadata 解析的失敗處理一致。
- **設定檔壞掉時使用者只看到 IDE log，容易忽略** → 這是本外掛既定的非致命問題管道，與逐變更略過警告一致；為此新增 UI 狀態不划算。
- **每次掃描多一次小檔案讀取** → 相對於既有的整棵變更樹掃描可以忽略。
- **Spectra 未來再改版面預設** → 解析集中在單一物件，屆時只有一個落點需要調整。

## Migration Plan

無資料遷移，也無設定遷移。既有專案沿用 fallback 規則，行為不變；新專案在外掛更新後即可被掃描。回退方式為還原這次變更，不留下任何持久化狀態。

## Open Questions

無。解析規則已由 `.spectra.yaml` 模板的註解與實際 `spectra init` 輸出確認。

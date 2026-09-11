## 1. 解析器

- [x] 1.1 新增 `SpecDirResolver`，交付 `Requirement: Resolve the spec directory from project configuration` 的核心契約：讀取專案根目錄的 `.spectra.yaml`，找到可用的頂層 `spec_dir` 值時回傳該值相對專案根目錄解析後的路徑，檔案不存在、無法讀取或無頂層 `spec_dir` 鍵時回傳 `<根目錄>/openspec` 且不寫入警告。驗證：新增 `SpecDirResolverTest` 覆蓋這三種情況，`./gradlew test` 通過。
- [x] 1.2 以行掃描實作值的讀取與正規化，交付決策「沿用手寫行掃描讀取 spec_dir，不引入 YAML 相依」：只認第零欄開始的頂層鍵、只取第一次出現、掃描行數設上限、去除行尾註解與包裹引號並修剪空白；`spec_dir` 只出現在縮排的巢狀位置時視為沒有該欄位。驗證：`SpecDirResolverTest` 覆蓋帶行尾註解的引號值、重複鍵、縮排鍵三種輸入並通過，且 `build.gradle.kts` 的相依區塊不含任何 YAML 函式庫。
- [x] 1.3 讓不可用的值退回並寫入 IDE log，交付決策「解析失敗一律退回 openspec 並寫入 IDE log」：值為空、為絕對路徑、或解析後逃出專案根目錄時回傳 `<根目錄>/openspec`，並透過既有的警告回呼送出一則點名 `.spectra.yaml` 與退回行為的訊息，且不彈出對話框。驗證：`SpecDirResolverTest` 逐列覆蓋 design.md 解析結果表中所有「有警告」與「無警告」列並通過。
- [x] 1.4 確立不做磁碟探測的行為，交付決策「解析規則逐字對齊 Spectra CLI，不做磁碟探測」：專案同時存在 `docs/spectra/` 目錄與一份沒有頂層 `spec_dir` 鍵的 `.spectra.yaml` 時，解析結果仍為 `<根目錄>/openspec`，解析過程不得檢查任何候選目錄是否存在。驗證：`SpecDirResolverTest` 新增此案例並通過。

## 2. 掃描整合

- [x] 2.1 讓 `ChangeScanner` 依解析結果定位變更來源，交付 `Requirement: Scan changes from all three Spectra sources` 的修訂行為與決策「解析器獨立成 discovery 物件，ChangeScanner.scan 簽名不變」：以 `SpecDirResolver` 取代 `OPENSPEC_DIR` 常數，作用中變更取自解析後 spec 目錄下的 `changes/`（排除 `archive`），已封存變更取自其下的 `changes/archive/`，暫存變更維持取自已解析 git 目錄下的 `spectra-app/changes/`，`scan` 的簽名與回傳型別不變。驗證：`ChangeScannerTest` 新增案例，專案根目錄含 `spec_dir: docs/spectra` 的設定檔時作用中與已封存變更自該目錄下被掃出；既有案例在不新增設定檔的情況下維持通過，`./gradlew test` 全數通過。
- [x] 2.2 交付決策「是否為 Spectra 專案改以解析後的 spec 目錄判定」與 `Requirement: Indicate loading and empty states` 的判定面：解析後的 spec 目錄不是目錄時回傳 `NOT_A_SPECTRA_PROJECT`，是目錄時 `isSpectraProject` 為真並回傳完整快照。驗證：`ChangeScannerTest` 新增案例斷言設定 `spec_dir: docs/spectra` 且該目錄存在的專案其 `isSpectraProject` 為真，而兩個目錄都不存在的專案回傳 `NOT_A_SPECTRA_PROJECT`。
- [x] 2.3 確認設定了非預設 spec 目錄時舊目錄被忽略：專案設定 `spec_dir: docs/spectra` 且 `openspec/changes/` 下另有變更時，作用中群組只含新目錄下的變更。驗證：`ChangeScannerTest` 對應案例通過。
- [x] 2.4 確認設定檔不可用時掃描仍完整回傳：`.spectra.yaml` 的 `spec_dir` 指向逃出根目錄的路徑時，掃描不拋出例外、退回 `openspec` 版面並照常回傳三個群組，警告只進 IDE log。驗證：`ChangeScannerTest` 新增案例並通過。

## 3. 驗證與規格一致性

- [x] [P] 3.1 交付決策「規格文字改以解析後的 spec 目錄表述」的一致性檢查：確認本變更的 delta 規格與實作行為相符，`change-discovery` 與 `changes-tool-window` 兩份 delta 中的每個情境都有對應的通過測試或既有實作依據。驗證：`spectra validate resolve-spec-dir-from-config` 與 `spectra analyze resolve-spec-dir-from-config` 皆無 Critical 或 Warning 等級的問題。
- [x] [P] 3.2 交付整體回歸把關：外掛在本變更後可建置且測試全綠。驗證：`./gradlew build` 成功，`./gradlew test` 全數通過。

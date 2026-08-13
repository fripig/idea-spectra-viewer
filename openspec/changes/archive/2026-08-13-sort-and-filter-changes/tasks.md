## 1. 模型與掃描層

- [x] 1.1 Report per-change metadata：依設計的「資料契約」擴充 `src/main/kotlin/com/github/fripig/spectraviewer/model/SpectraChange.kt`，為每個 change 加入建立日期與修改日期兩個可為未知的欄位，並新增 `src/main/kotlin/com/github/fripig/spectraviewer/model/ChangeOrder.kt` 定義 Name、Modified、Created 三種排序選項與預設值 Modified。驗證：ChangeOrderTest 斷言預設值為 Modified 且三個選項互斥
- [x] 1.2 依設計決策「建立日期以寬容解析取自 .openspec.yaml，推翻既有的不讀決策」與「不引入 YAML 函式庫，以單行掃描取單一欄位」，新增 `src/main/kotlin/com/github/fripig/spectraviewer/discovery/ChangeMetadataParser.kt`，逐行找出第一個頂層 `created:` 並以 ISO 日期解析；依設計的「失敗模式」，檔案缺失、欄位缺失、值無法解析一律回傳未知而非拋出例外。驗證：ChangeMetadataParserTest 逐列覆蓋 spec 的 creation metadata 對照表，且 ChangeScannerTest 斷言四種情形下 change 皆仍出現在清單中
- [x] 1.3 依設計決策「修改日期取所有 Markdown 檔案的最新修改時間」，在 `ChangeScanner` 的 change 讀取流程中蒐集所有 Markdown 檔案的修改時間取最大值，無法讀取的檔案排除在比較之外，全部無法取得時回傳未知。驗證：ChangeScannerTest 斷言兩個檔案時取較新者、無 Markdown 檔案時為未知，並在編輯既有檔案後重掃斷言修改日期前進
- [x] 1.4 Scan changes from all three Spectra sources：依設計決策「排序責任由掃描層移交呈現層」，移除 `ChangeScanner` 分組掃描結尾的名稱排序，快照內分組順序不再有保證。驗證：ChangeScannerTest 將既有的順序斷言改為集合相等斷言，確認三筆 change 皆存在而不論順序

## 2. 排序與篩選

- [x] 2.1 Sort changes within groups：依設計的「排序規則」與決策「排序方向與次要鍵對齊 Spectra CLI」，在 `src/main/kotlin/com/github/fripig/spectraviewer/toolwindow/ChangeTreeNodes.kt` 實作排序純函式——Name 為名稱遞增，Modified 與 Created 為日期新到舊，同日期以名稱遞增；函式不接觸檔案系統。驗證：ChangeOrderTest 以 spec 中 add-search、mid-tier、zebra-fix 三筆資料逐一斷言三種排序的完整輸出順序
- [x] 2.2 依設計決策「日期未知的 change 一律排在最後」，以日期排序時將日期未知者置於分組最末，與排序方向無關。驗證：ChangeOrderTest 斷言 zebra-fix（建立日期未知）在 Created 排序下排在最後，而非因未知被當成極小值而浮到頂端
- [x] 2.3 Filter changes by name：依設計決策「篩選只比對名稱，計數改為符合數與總數」，實作篩選純函式，以不分大小寫子字串比對 change 名稱並同時套用於三個分組；artifact 路徑不參與比對，命中 change 的 artifact 全數保留。驗證：ChangeOrderTest 斷言 spec 的四個篩選情境——跨分組收斂、大小寫不敏感、命中者 artifact 保留、零符合時三個分組仍存在

## 3. Tool Window

- [x] 3.1 依設計的「使用者可觀察的行為」，在 `src/main/kotlin/com/github/fripig/spectraviewer/toolwindow/SpectraChangesPanel.kt` 的工具列加入排序動作，提供三個互斥選項並標示當前選擇，切換後由既有快照重建樹狀而不重新掃描磁碟。驗證：沙箱 IDE 中切換三種排序確認順序改變，且 IDE 記錄檔沒有新的掃描紀錄
- [x] 3.2 在工具列加入篩選文字輸入欄位，輸入即時套用並重建樹狀而不重新掃描；Refresh 後保留當前的排序選項與篩選字串並套用於新快照。驗證：沙箱 IDE 中輸入字串確認即時收斂，按 Refresh 後排序與篩選維持不變
- [x] 3.3 Display changes as a grouped tree：分組節點在篩選字串為空時顯示單一總數，非空時顯示符合數與總數兩個數字；零符合時分組節點仍然顯示。驗證：沙箱 IDE 中比對 spec 的兩個計數情境，並輸入無任何 change 名稱包含的字串確認三個分組仍在

## 4. 端對端驗證

- [x] 4.1 依設計的「驗收條件」與「範圍邊界」完成端對端驗證，確認交付內容不逸出範圍：以 Gradle wrapper 執行 test 任務全數通過，並在沙箱 IDE 開啟本 repository，確認三種排序、名稱篩選、分組雙計數、Refresh 保留設定四項行為皆符合 spec，且未實作範圍外的跨分組排序、狀態篩選與狀態持久化。驗證：上述人工檢查全部通過且測試全綠

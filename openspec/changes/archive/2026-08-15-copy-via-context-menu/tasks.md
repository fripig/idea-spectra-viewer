## 1. 安裝右鍵選單

- [x] 1.1 依「右鍵先確定選取再開選單」的決策，先確認平台的樹元件在未選取節點上按右鍵時是否已自行調整選取：在沙箱 IDE 中選取一個 change，再到另一個未選取的 change 上按右鍵，觀察選取是否跟著移動。驗證：把觀察到的行為寫進 openspec/changes/copy-via-context-menu/design.md 的「右鍵先確定選取再開選單」段落，明確記下「平台已自帶」或「需要自行補上」。
- [x] 1.2 依「選單項目自訂文字但共用同一個啟用判斷」與「以平台的 popup 安裝機制掛上選單」的決策，在 src/main/kotlin/com/github/fripig/spectraviewer/toolwindow/SpectraChangesPanel.kt 的初始化流程建立一個只含「Copy Change Name」這一項的 action group，其啟用判斷與複製都轉呼叫面板既有的複製路徑、快捷鍵沿用平台 Copy action，並用平台的 popup 安裝方式掛在樹上；不自行攔截滑鼠事件，也不在 plugin.xml 宣告這個 group。驗證：執行 ./gradlew build 通過，並在沙箱 IDE 的 change 節點上按右鍵，看到只有 Copy Change Name 一項的選單，項目旁顯示目前鍵盤配置的複製快捷鍵。
- [x] 1.3 依 1.1 的結論處理右鍵選取：若平台已自帶，不寫任何選取程式碼並在 design 記錄理由；若缺少，在同一個面板檔案補上「右鍵落在未選取節點時把該節點設為唯一選取、落在既有多選範圍內時不動選取」的處理。驗證：在沙箱 IDE 中先選取 change A，再到未選取的 change B 上右鍵按 Copy，貼出來是 B 的名稱；接著多選 A 與 B，在 B 上右鍵按 Copy，貼出來是兩行且兩者仍為選取狀態。

## 2. 驗收

- [x] 2.1 對照 changes-tool-window 規格中 Copy change names to the clipboard 這項需求，逐一手動驗證四個與選單有關的 Scenario：選單只有 Copy Change Name 一項、沒有可複製對象時該項停用、右鍵改變選取、右鍵保留既有多選。驗證：四個 Scenario 的預期結果全部符合。
- [x] 2.2 確認這次變更沒有動到既有行為：Copy 快捷鍵、排序切換、名稱篩選、雙擊 artifact 開檔、Refresh 保留展開狀態各操作一次，且右鍵與複製都不觸發重新掃描。驗證：執行 ./gradlew build 全數通過且測試數量維持 74 個（這次不新增單元測試），沙箱 IDE 中上述五項操作結果與變更前相同。

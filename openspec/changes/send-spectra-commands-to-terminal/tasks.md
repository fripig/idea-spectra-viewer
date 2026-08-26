## 1. 純函式層與相依宣告

- [x] 1.1 [P] 在 `src/test/kotlin/com/github/fripig/spectraviewer/toolwindow/CommandTextTest.kt` 寫下先失敗的測試，鎖定「指令文字由純函式產生，與既有的複製邏輯並列」與「多選時停用整個子選單」兩項決策：四個指令種類對 change 名稱 `add-search` 各自產出 `/spectra-apply add-search` 等單行字串且結尾無換行；選取為單一 change 節點時判定為可用，選取為兩個 change 節點、只有群組節點、只有 artifact 節點或空選取時判定為不可用，選取同時含群組節點、單一 change 節點與 artifact 節點時判定為可用。驗證：`./gradlew --no-watch-fs test` 出現紅燈且失敗原因是缺少待實作的函式，不是編譯以外的意外錯誤。
- [x] 1.2 [P] 讓 plugin 在 IDE 的 terminal plugin 被停用時仍能完整載入，落實「terminal plugin 缺席時的載入安全」：於 `src/main/resources/META-INF/plugin.xml` 加入對 `org.jetbrains.plugins.terminal` 的 optional 相依並指向新增的 `src/main/resources/META-INF/spectra-viewer-terminal.xml`。驗證：`./gradlew --no-watch-fs buildPlugin` 成功，且產出的 plugin.xml 內含該 optional 相依宣告。
- [x] 1.3 實作指令種類列舉與兩個純函式——由指令種類與 change 名稱產出單行指令文字、由樹節點選取判定是否恰有一個 change 節點——放在 `src/main/kotlin/com/github/fripig/spectraviewer/toolwindow/ChangeTreeNodes.kt` 中既有 `copyTextFor` 旁邊，不引入任何 IDE 型別。驗證：`./gradlew --no-watch-fs test` 全綠，且 1.1 的所有案例通過。

## 2. terminal 送出接縫

- [x] 2.1 建立唯一碰觸 terminal API 的接縫，落實「terminal 相依收斂成單一 adapter，且可整檔刪除」：新增 `src/main/kotlin/com/github/fripig/spectraviewer/terminal/TerminalCommandSender.kt`，對外只提供「目前是否有可送出的目標」與「把一段文字送到目標」兩項能力，內部判定目標可用的條件為 Terminal 工具視窗有被選中的 tab 且該 tab 的終端連線已就緒。驗證：全專案 grep `org.jetbrains.plugins.terminal` 與 `com.intellij.terminal` 僅命中這一個 Kotlin 檔案；`./gradlew --no-watch-fs compileKotlin` 通過。
- [x] 2.2 讓面板在觸碰 adapter 之前先確認 terminal plugin 已安裝且啟用，使 terminal plugin 停用時 adapter 的類別完全不被解析。驗證：該檢查只使用平台的 plugin 查詢 API，呼叫端沒有任何 terminal 型別的 import；`./gradlew --no-watch-fs compileKotlin` 通過。

## 3. 選單與兩條路徑

- [x] 3.1 在 `src/main/kotlin/com/github/fripig/spectraviewer/toolwindow/SpectraChangesPanel.kt` 的右鍵選單中，於既有 `Copy Change Name` 之後加入子選單，落實「選單標題隨可送出狀態切換」：有可送出目標時標題為 `Send to Terminal`，否則為 `Copy Command`；子項目依序為 apply、ingest、archive、commit，文字始終是套用選中 change 名稱後的完整指令；選取不是恰好一個 change 節點時整個子選單停用。驗證：`./gradlew --no-watch-fs runIde` 後於樹上分別以單選、多選、群組節點開啟選單，確認標題與啟用狀態符合上述規則。
- [x] 3.2 實作送出路徑，落實「只填入不執行，並把焦點交給 terminal」：把指令文字寫入選中 tab 的輸入位置、不附加換行、不觸發執行，接著啟動 Terminal 工具視窗並把焦點移到該 tab，剪貼簿內容不變。驗證：`./gradlew --no-watch-fs runIde`，於一個已就緒的 terminal tab 觸發項目後，文字出現在輸入位置、游標停在其後、未被執行，焦點落在該 tab，按 Enter 才送出。
- [x] 3.3 實作剪貼簿路徑：沒有可送出目標時把同一段指令文字寫入系統剪貼簿，焦點不移動且不顯示通知；送出過程拋出例外時記錄到 IDE log 後改走剪貼簿，不彈出對話框。驗證：`./gradlew --no-watch-fs runIde`，關閉 Terminal 工具視窗後觸發項目，確認剪貼簿取得指令文字、無通知、焦點留在樹上。
- [x] 3.4 確認觸發子選單不會造成掃描或重建：任一路徑執行後樹的展開狀態、排序與過濾字串維持不變，且既有 `Copy Change Name` 與 IDE Copy 快捷鍵的行為完全不變（單選 `add-search` 得到 `add-search`，不含 slash 指令）。驗證：`./gradlew --no-watch-fs runIde`，在展開若干節點並輸入過濾字串的狀態下觸發子選單與 Copy，逐項比對前後狀態。

## 4. 驗收

- [x] 4.1 跑完整測試套件，確認純函式層行為與既有測試皆未退化。驗證：`./gradlew --no-watch-fs test` 全綠，輸出貼進實作紀錄。
- [x] 4.2 對照規格逐條手動驗收 Send a Spectra command for the selected change，涵蓋送出、使用者自行按 Enter、標題切換、無 tab 降級、多選停用、群組與 artifact 混選仍可用六種情境。驗證：`./gradlew --no-watch-fs runIde`，每種情境的實際結果與 `specs/changes-tool-window/spec.md` 的對應 scenario 一致。
- [x] 4.3 在 IDE 中停用 terminal plugin 並重啟，確認 Spectra 工具視窗正常運作、子選單標題為 `Copy Command`、子選單可用且觸發後取得指令文字。驗證：手動操作結果符合規格中對應的 scenario，IDE log 無 `NoClassDefFoundError`。
- [x] 4.4 更新 `src/main/resources/META-INF/plugin.xml` 的 change-notes，新增一段說明本次功能，讓 Marketplace 的 What's new 反映這次變更。驗證：內容評閱——該段落描述子選單、不代按 Enter 的行為與無 tab 時的剪貼簿降級，且未提及未實作的行為。

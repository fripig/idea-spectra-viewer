## Why

工具視窗已經能複製 change 名稱，但使用者拿到名稱後真正要做的事幾乎都一樣：切到終端機、打出 `/spectra-apply`、再把名稱貼上去。名稱只是半成品，最後一段組裝每次都由人手工重複。

同時，Claude Code session 通常已經開在 IDE 的某個 terminal tab 裡。plugin 有能力把完整指令直接送進那個 tab，讓「從樹上看到某個 change」到「Claude 開始處理它」之間不必經過剪貼簿。

## What Changes

- 樹的右鍵選單新增一個子選單，列出四個核心 spectra 指令，各自已經帶上選中 change 的名稱：`/spectra-apply <name>`、`/spectra-ingest <name>`、`/spectra-archive <name>`、`/spectra-commit <name>`。子選單項目顯示完整指令文字，所以送出什麼零歧義。
- 主要行為是把指令文字填入使用者**目前選中的** terminal tab，並把焦點交給該 tab，但**不代按 Enter**。是否送出由使用者確認。plugin 不猜測哪個 tab 裡跑著 Claude，切 tab 是使用者送出前自己的動作。
- 當 Terminal 工具視窗沒開、或一個 tab 都沒有時，改把同一段指令文字寫入系統剪貼簿。子選單標題隨狀態切換：可送出時顯示 Send to Terminal，只能複製時顯示 Copy Command，讓選單本身先說清楚會發生什麼，因此降級不需要額外通知。
- 選擇多個 change 節點時整個子選單停用。既有的 Copy Change Name 多選語意是換行串接，套到指令上會變成連續執行多行指令。
- 既有的 Copy Change Name 行為完全不變。

## Non-Goals

- 不自動按 Enter 執行指令。取捨是「少按一個鍵」對上「送錯 tab 無法回收」：plugin 無從驗證使用者切對了 tab，所以最後一道確認留給使用者。丟錯 tab 的代價因此只是多一行未送出的文字。
- 不自動開新 terminal tab、也不自動啟動新的 Claude session。沒有 tab 時走剪貼簿，不製造一個裡面只有裸 shell、送 slash 指令只會得到 command not found 的新分頁。
- 不猜測哪個 tab 裡跑著 Claude session。terminal widget 回報的忙碌狀態對互動式 TUI 程式並不可靠，猜錯就是把文字打進使用者跑到一半的工作裡。
- 不做指令清單的設定頁。四個指令寫死在程式碼裡；plugin 目前完全無狀態，為此引入第一個設定服務不划算。
- 不依 change 所屬群組過濾指令。三個群組的子選單內容一致。

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `changes-tool-window`: 新增一條需求，描述指令子選單、送入選中 terminal tab 的行為、無 tab 時的剪貼簿降級，以及多選時的停用規則。既有的 Copy change names 需求不變。

## Impact

- Affected specs: changes-tool-window
- Affected code:
  - New:
    - src/main/kotlin/com/github/fripig/spectraviewer/terminal/TerminalCommandSender.kt
    - src/main/resources/META-INF/spectra-viewer-terminal.xml
    - src/test/kotlin/com/github/fripig/spectraviewer/toolwindow/CommandTextTest.kt
  - Modified:
    - src/main/kotlin/com/github/fripig/spectraviewer/toolwindow/ChangeTreeNodes.kt
    - src/main/kotlin/com/github/fripig/spectraviewer/toolwindow/SpectraChangesPanel.kt
    - src/main/resources/META-INF/plugin.xml
    - build.gradle.kts
    - gradle.properties
  - Removed: (none)
- Dependencies: 對 bundled terminal plugin 新增一筆 optional 相依。terminal plugin 被停用的 IDE 仍能載入本 plugin，子選單永遠走剪貼簿那條路。建置腳本另需把該 bundled plugin 列入編譯期 classpath——編譯期看得到型別與執行期相依是否為必要，是兩件不同的事，兩者都要。
- 不新增測試框架相依：指令文字的組法是純函式，測試不需要 IDE fixture。

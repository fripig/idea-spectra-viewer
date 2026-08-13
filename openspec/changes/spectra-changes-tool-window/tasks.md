## 1. 專案骨架

- [x] 1.1 建立 Gradle 專案骨架，使其能以 Gradle wrapper 在沒有系統 Gradle 的環境完成建置：`settings.gradle.kts`、`build.gradle.kts`、`gradle.properties`、`gradle/wrapper/gradle-wrapper.properties`，並在 `.gitignore` 加入建置產物與沙箱目錄。依設計決策「針對 IntelliJ IDEA Community 建置並只宣告 platform 模組相依」，以 IntelliJ IDEA Community 為開發平台，不引入 PHP 專屬相依。驗證：以 Gradle wrapper 執行 build 任務成功結束
- [x] 1.2 Provide a Spectra tool window：撰寫 `src/main/resources/META-INF/plugin.xml`，註冊名為 Spectra 的 Tool Window 並只宣告 `com.intellij.modules.platform` 相依，使 plugin 可安裝於 PhpStorm。驗證：以 Gradle 的 plugin 驗證任務檢查描述檔通過，且沙箱 IDE 中出現 Spectra Tool Window

## 2. 掃描層

- [x] 2.1 Report per-change metadata：依設計的「掃描層的資料契約」實作 `src/main/kotlin/com/github/fripig/spectraviewer/model/SpectraChange.kt`，定義 change 的名稱、分組、目錄絕對路徑、Markdown artifact 相對路徑清單（依字串排序、排除非 md 檔）、任務進度與推導狀態，以及承載三個分組的掃描結果快照型別。驗證：ChangeScannerTest 斷言 artifact 清單只含 md 檔且順序正確
- [x] 2.2 Resolve the git directory including worktree indirection：依設計決策「以 gitdir 指標解析 git 目錄以支援 worktree」實作 git 目錄解析，`.git` 為目錄時直接採用，為檔案時解析其 gitdir 指標，並在目標目錄存在 commondir 檔案時再解析一次取得共用 git 目錄；無法解析時回傳無結果而非拋出例外。落點為 `src/main/kotlin/com/github/fripig/spectraviewer/discovery/ChangeScanner.kt`。驗證：ChangeScannerTest 以暫存目錄分別建構 `.git` 目錄、`.git` 檔案加 commondir、以及 `.git` 缺失三種佈局並斷言解析結果
- [x] 2.3 Scan changes from all three Spectra sources：依設計決策「以 java.nio 直接讀檔，不呼叫 CLI 也不依賴 VFS」，以 `java.nio.file` 走訪 `openspec/changes/`（排除 `archive` 目錄）、`openspec/changes/archive/` 與 git 目錄下 `spectra-app/changes/`，產出 Active、Parked、Archived 三組並各自依名稱排序。驗證：ChangeScannerTest 以仿真佈局斷言三分組歸屬、archive 目錄未被當成 active change、以及來源目錄不存在時該組為空
- [x] 2.4 Derive task progress from tasks.md：依設計的「任務進度解析契約」實作 `src/main/kotlin/com/github/fripig/spectraviewer/discovery/TaskProgressParser.kt`，計數 Markdown 核取方塊清單項目，方括號內為空白計未完成、為 x（不分大小寫）計已完成、其他字元不計入總數，忽略程式碼圍籬內的行，且核取方塊之後的內容（含平行任務標記）不影響計數；無檔案或無核取方塊時回傳無進度資訊。驗證：TaskProgressParserTest 覆蓋 spec 中列出的每一種核取方塊情形
- [x] 2.5 Derive change status from task progress：依設計決策「Change 狀態由 tasks.md 的勾選狀態推導」，將進度轉換為草稿、尚未開始、進行中、已完成四種狀態。驗證：TaskProgressParserTest 以 spec 的狀態對照表逐列斷言
- [x] 2.6 Degrade gracefully on unreadable changes：依設計的「失敗模式」，單一 change 目錄讀取失敗時略過該筆並寫入 IDE 記錄檔警告而不中斷整次掃描，`.openspec.yaml` 缺失或無法解析時該 change 仍然列出。驗證：ChangeScannerTest 以權限受限或掃描中消失的目錄斷言其餘 change 仍完整回傳

## 3. Tool Window

- [x] 3.1 Display changes as a grouped tree：依設計的「使用者可觀察的行為」與決策「樹狀模型以不可變快照重建」實作 `src/main/kotlin/com/github/fripig/spectraviewer/toolwindow/ChangeTreeNodes.kt` 與樹狀模型建構，第一層為 Active、Parked、Archived 三個固定分組節點並顯示筆數（空組顯示 0），第二層為 change 節點，第三層為 artifact 節點顯示相對路徑。驗證：沙箱 IDE 開啟本 repository，樹狀結構與分組筆數符合 spec 描述
- [x] 3.2 Show task progress on change nodes：change 節點在有進度資訊時同時顯示已完成數與總數，無進度資訊時只顯示名稱。驗證：沙箱 IDE 中比對一個已知進度的 change 節點文字
- [x] 3.3 Scan off the event dispatch thread：依設計決策「掃描於背景執行緒執行，UI 更新回到 EDT」，在 `src/main/kotlin/com/github/fripig/spectraviewer/toolwindow/SpectraChangesPanel.kt` 將掃描排入背景執行緒，僅結果套用回到 EDT。驗證：以 IDE 內建的 EDT 慢速操作偵測執行沙箱 IDE，開啟與刷新 Tool Window 期間不出現 EDT 阻塞警告
- [x] 3.4 Open artifact files in the editor：雙擊 artifact 節點時在編輯器分頁開啟對應 Markdown 檔案；檔案已不存在時顯示不阻斷操作的通知而非拋出例外。驗證：沙箱 IDE 中雙擊節點成功開檔，並在手動刪除檔案後重試確認只出現通知
- [x] 3.5 Refresh on demand：`src/main/kotlin/com/github/fripig/spectraviewer/toolwindow/SpectraToolWindowFactory.kt` 於 Tool Window 首次開啟時執行初次掃描，工具列提供 Refresh 動作重新掃描並整棵樹重建，重建後還原先前展開的分組與 change 節點。驗證：沙箱 IDE 中展開 Parked 分組後按 Refresh，展開狀態保留
- [x] 3.6 Indicate loading and empty states：掃描進行中顯示載入中提示，專案根目錄沒有 `openspec` 目錄時以空狀態文字取代樹狀顯示。驗證：沙箱 IDE 分別開啟本 repository 與一個非 Spectra 專案確認兩種狀態

## 4. 端對端驗證

- [ ] 4.1 依設計的「驗收條件」與「範圍邊界」完成端對端驗證，確認交付內容不逸出範圍：以 Gradle 建置產出可安裝的 plugin 壓縮檔，於沙箱 IDE 開啟本 repository，確認 Spectra Tool Window 列出的 parked change 名稱與終端機 spectra list --parked 的輸出一致，並將一個 change park 後按 Refresh 確認它由 Active 移至 Parked。驗證：上述四項人工檢查全部通過，且以 Gradle wrapper 執行 test 任務全數通過

## 1. 過濾條件收斂為值物件（純機械重構）

- [x] 1.1 在 `src/main/kotlin/com/github/fripig/spectraviewer/model/ChangeFilter.kt` 建立 `ChangeFilter` 值物件，持有名稱過濾字串、已選作者名稱集合、以及是否納入未知作者的布林值，並提供「此條件是否正在過濾」與「某個 `SpectraChange` 是否命中」兩個查詢。此步驟落實設計決策「過濾條件收斂為單一值物件，取代分散的 String 參數」與「以具名布林表達未知作者，而非集合中的 null 元素」。此時作者相關欄位允許恆為空集合與 false，僅需讓名稱比對行為與既有 `filterChanges` 完全一致。驗證：新增 `ChangeFilterTest` 中的名稱比對案例（空字串不過濾、substring 命中、大小寫不敏感）通過 `./gradlew test`。
- [x] 1.2 將 `filterChanges` 與 `applyView` 的過濾參數由 `String` 改為 `ChangeFilter`，並讓 `GroupNode.filtering` 的來源改為 `ChangeFilter` 自行回答，使呼叫端不再各自拼湊過濾狀態。行為不變：既有的名稱過濾與群組計數結果完全相同。驗證：`ChangeOrderTest` 與 `ChangeNodeRenderingTest` 僅調整呼叫簽章、斷言一字不改，`./gradlew test` 全數通過。

## 2. 作者比對核心（TDD）

- [x] 2.1 先寫失敗測試，覆蓋「Filter changes by author」需求的比對規則：未選任何作者時不限縮結果；選取單一作者時只留下該作者的 change；選取多位作者時為 OR；選取未知作者候選時只留下 `createdBy` 為 null 的 change。驗證：`./gradlew test` 出現預期的紅燈，且失敗訊息指向作者比對而非既有行為。
- [x] 2.2 在 `ChangeFilter` 中實作上述作者比對，使 2.1 的測試轉綠，且 1.x 的既有測試維持通過。驗證：`./gradlew test` 全綠。
- [x] 2.3 補上名稱過濾與作者過濾同時生效時為 AND 的測試與實作，並以 delta spec 中「combinations of filter text and author selection」表格的六組輸入作為案例。驗證：對應的參數化或逐項測試在 `./gradlew test` 中通過。
- [x] 2.4 讓「Display changes as a grouped tree」需求的群組計數在僅作者過濾生效時也顯示 `matched/total`：`ChangeFilter` 回報的過濾狀態改為「名稱過濾非空或有選取作者」。驗證：新增測試斷言在空名稱過濾加一位選取作者的情況下 `groupCountText` 回傳 `1/3`，且既有的無過濾（`3`）與名稱過濾（`1/3`）案例維持不變。

## 3. 候選作者清單與選取狀態（TDD）

- [x] 3.1 先寫失敗測試再實作一個純函式，由 `SpectraSnapshot` 算出候選作者清單：Active、Parked、Archived 三群組聯集、去重、依字母序（不分大小寫）排列，並回報 snapshot 中是否存在未知作者；未知作者候選恆排在所有具名作者之後，且僅在確實存在時出現。此步驟落實設計決策「候選作者清單取自完整 snapshot，並在 Refresh 後與選取取交集」的前半部。驗證：以 delta spec 中「candidate lists by snapshot content」表格的六組 snapshot 作為測試案例，`./gradlew test` 通過。
- [x] 3.2 先寫失敗測試再實作「Refresh 後選取與新候選清單取交集」的純函式：仍存在的作者維持選取，已消失的作者其選取自動解除；未知作者候選在新 snapshot 已無未知作者時同樣解除。驗證：測試涵蓋「兩位選取、其中一位消失」與「未知作者候選消失」兩種情況，`./gradlew test` 通過。
- [x] 3.3 先寫失敗測試再實作「候選項目少於兩項時控制項停用」的判斷，並以純函式表達，讓 UI 層只負責轉呼叫。驗證：測試涵蓋候選為空、僅一位具名作者、僅未知作者、兩位以上等情況，`./gradlew test` 通過。

## 4. UI 接線

- [x] 4.1 在 `SpectraChangesPanel` 的 toolbar 加入作者過濾 popup：沿用既有 `SortAction` 的模式，以 `DefaultActionGroup` 搭配每位候選作者一個 `ToggleAction`，按鈕文字為 `Filter by Author`、未知作者項目文字為 `Unknown`。此步驟落實設計決策「以獨立的複選 popup 呈現作者過濾，而非擴充名稱輸入框」與「使用 toolbar 的 popup action group 而非 Swing 下拉元件」，並依「不在候選項目上顯示 change 筆數」的決策，項目文字只呈現作者名稱、不附加任何數字。驗證：在 IDE 沙箱（`./gradlew runIde`）中手動確認按鈕出現在排序按鈕旁、展開後可複選、名稱輸入框版面未被壓縮。
- [x] 4.2 將勾選狀態接上 `ChangeFilter` 與 `rebuildTree()`，使勾選變更只從記憶體中的 snapshot 重建樹、不觸發磁碟掃描，且樹的展開狀態、排序與名稱過濾文字均保留。驗證：在沙箱中勾選作者後確認樹即時更新、群組計數顯示 `matched/total`、且展開的節點維持展開。
- [x] 4.3 先寫失敗測試再實作設計決策「將快照到過濾條件的調和抽成純函式，讓接線層只剩指派」：一個吃 `SpectraSnapshot` 與目前 `ChangeFilter`、回傳新候選清單與調和後 `ChangeFilter` 的純函式，使掃描完成後的順序邏輯（先算候選、再以新候選調和選取）可在無 IDE fixture 的情況下驗證；`SpectraChangesPanel` 的掃描回呼改為呼叫它並指派兩個欄位，Refresh 後已消失的作者自動取消勾選、候選少於兩項時按鈕停用的判斷一併由此函式的輸出驅動。驗證：測試涵蓋「作者仍在則保留勾選」「作者已消失則解除勾選且候選清單不再包含他」「未知作者候選消失時 `includeUnknownAuthor` 轉為 false」三種情況，`./gradlew test` 通過。

## 5. 收尾驗證

- [x] 5.1 確認「Show the proposer on change nodes」需求的既有行為未被破壞：提案者仍顯示在名稱與進度之間、不參與排序、不進入複製內容，且既有測試 `the filter does not match the proposer` 的斷言未被修改（僅隨 1.2 調整呼叫簽章）。驗證：`./gradlew test` 中該測試通過，並以 git diff 檢視確認該測試的斷言內容無異動。
- [x] 5.2 執行完整建置與 plugin 驗證，確認變更未破壞打包流程。驗證：`./gradlew build verifyPlugin` 成功結束且無新增警告。

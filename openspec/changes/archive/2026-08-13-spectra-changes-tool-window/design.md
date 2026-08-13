## Context

本 repository 目前只有 Spectra 的設定與 `openspec/` 目錄，沒有任何程式碼，因此這份設計同時涵蓋 plugin 專案骨架與第一版功能。

目標 IDE 為使用者本機的 PhpStorm 2026.2（build 262.8665.265，內建 JBR 25）。本機沒有安裝獨立的 JDK 或 Gradle，因此建置流程必須自帶 Gradle wrapper，並允許以 PhpStorm 內建的 JBR 作為 JAVA_HOME。

Spectra 的資料佈局（已於本機實測 spectra 2.3.1 確認）：

- active change 位於專案根目錄的 `openspec/changes/<name>/`
- archived change 位於專案根目錄的 `openspec/changes/archive/<name>/`
- parked change 被移出 `openspec/`，位於 git 目錄下的 `spectra-app/changes/<name>/`
- 每個 change 目錄含 `.openspec.yaml`（schema、created、created_by）與 `proposal.md`、`design.md`、`tasks.md`、`specs/<capability>/spec.md` 等 artifact
- git 目錄下另有 `spectra-app/spectra.db`，屬 Spectra 內部狀態，本設計不碰

## Goals / Non-Goals

**Goals:**

- 讓 parked change 在 IDE 內可見、可點開，不需切換到終端機
- 在沒有安裝 `spectra` CLI 的環境仍可完整運作
- 掃描不阻塞 UI 執行緒
- 對 Spectra 未來的格式演進保持寬容：無法解析的 change 降級顯示而非整棵樹失敗

**Non-Goals:**

- 不提供任何寫入操作（park、unpark、archive、任務勾選）
- 不讀取 Spectra 的內部資料庫
- 不做檔案系統事件驅動的自動刷新
- 不自訂 markdown 渲染
- 不支援單一 IDE 視窗同時掛載多個 Spectra 專案根目錄，第一版只處理專案主根目錄

## Decisions

### 以 java.nio 直接讀檔，不呼叫 CLI 也不依賴 VFS

掃描層使用 `java.nio.file` 直接走訪目錄與讀取檔案。

不呼叫 `spectra` CLI 的原因：CLI 未必在 IDE 繼承的 PATH 中（macOS GUI 應用程式的 PATH 與 shell 不同），且每次刷新啟動子行程的延遲與失敗模式都比讀檔複雜。

不使用 IntelliJ VFS 的原因：parked change 位於 git 目錄下，該路徑預設被排除在專案索引之外，VFS 的內容未必即時且可能未載入。NIO 讀取沒有這個顧慮，代價是必須自行確保不在 EDT 上執行。

替代方案：以 `spectra list --json` 取得資料，格式變動由 CLI 吸收。已評估後放棄，理由如上；若未來檔案格式頻繁破壞，可再引入 CLI 作為次要來源。

### 針對 IntelliJ IDEA Community 建置並只宣告 platform 模組相依

Gradle 建置以 IntelliJ IDEA 作為開發用平台，`plugin.xml` 只宣告 `com.intellij.modules.platform` 相依，不引用任何 PHP 專屬 API。

實作時確認：IntelliJ IDEA Community（IC）自 2025.3 起已不再單獨發行，改由統一的 IntelliJ IDEA 發行版取代，因此建置改用 IntelliJ Platform Gradle Plugin 的 intellijIdea 相依宣告。此調整不影響本決策的意圖。

如此產出的 plugin 可安裝在包含 PhpStorm 在內的所有 JetBrains IDE。第一版功能純粹是讀檔加樹狀 UI，沒有任何需要 PhpStorm 專屬 API 的地方，綁定 PhpStorm 只會縮小適用範圍。

替代方案：直接以 PhpStorm 作為目標平台。已評估後放棄，因為會讓 plugin 無法安裝到 IntelliJ IDEA 等其他 IDE，而沒有換得任何功能。

### 掃描於背景執行緒執行，UI 更新回到 EDT

Tool Window 開啟時與每次 Refresh 時，掃描工作丟到 IntelliJ 的背景執行緒執行，完成後把結果切回 EDT 套用到樹狀模型。掃描期間樹狀顯示載入中狀態。

理由：走訪目錄與讀檔是阻塞 IO，在 EDT 上執行會凍結整個 IDE。IntelliJ Platform 對 EDT 上的檔案 IO 有明確禁止。

### Change 狀態由 tasks.md 的勾選狀態推導

不讀取 Spectra 內部資料庫，change 的任務進度與狀態改由解析 `tasks.md` 得出：

- 逐行比對核取方塊語法，區分未完成與已完成
- 已完成數為 0 且總數大於 0 時視為尚未開始
- 已完成數等於總數且總數大於 0 時視為已完成
- 其餘視為進行中
- 沒有 `tasks.md` 或其中沒有任何核取方塊時，不顯示進度，狀態視為草稿

替代方案：讀取 `spectra-app/spectra.db`。已評估後放棄，該資料庫是 Spectra 內部實作，格式不對外保證，耦合風險高。

### 以 gitdir 指標解析 git 目錄以支援 worktree

parked change 的位置相對於 git 目錄而非專案根目錄。專案根目錄下的 `.git` 可能是目錄，也可能是 git worktree 或 submodule 產生的檔案。

解析規則：`.git` 是目錄時直接使用；是檔案時讀取其中的 gitdir 指標取得實際路徑，若該路徑下存在 commondir 檔案則再解析一次取得共用 git 目錄。解析失敗時視為沒有 parked change，不拋出錯誤。

理由：Spectra 本身支援 git worktree 工作流程，若不處理這層間接，在 worktree 中開啟專案會完全看不到 parked change。

### 樹狀模型以不可變快照重建

每次掃描產出一份完整的不可變資料快照，UI 端整棵樹重建而非局部增刪。

理由：change 數量是數十等級，整棵樹重建的成本可忽略，換得的是狀態一致性——不會出現部分節點是舊資料的情況。局部更新的複雜度在這個規模下不划算。

## Implementation Contract

### 使用者可觀察的行為

安裝 plugin 後，IDE 左側工具列出現名為 Spectra 的 Tool Window。開啟後顯示一棵樹：

- 第一層為三個固定分組節點：Active、Parked、Archived，每組標題附帶該組的 change 數量
- 第二層為 change 節點，顯示 change 名稱；當該 change 有可解析的任務時，額外顯示已完成數與總數
- 第三層為該 change 的 artifact 檔案節點，顯示相對於 change 目錄的路徑
- 雙擊 artifact 節點時，該檔案在編輯器分頁中開啟
- 分組內沒有任何 change 時，該分組節點仍然顯示，數量為零
- Tool Window 工具列提供 Refresh 動作，觸發重新掃描並重建整棵樹
- 專案根目錄下不存在 `openspec` 目錄時，樹狀區域改為顯示空狀態文字，說明此專案未初始化 Spectra

### 掃描層的資料契約

掃描層對外提供一個函式，輸入為專案根目錄路徑，輸出為一份掃描結果快照。快照包含三個分組的 change 清單，每個 change 條目包含：

- 名稱：change 目錄的目錄名
- 分組：Active、Parked、Archived 三者之一
- 目錄絕對路徑
- artifact 檔案清單：change 目錄下所有副檔名為 md 的檔案，以相對於 change 目錄的路徑表示，並依路徑字串排序
- 任務進度：已完成數與總數，或無進度資訊
- 推導狀態：草稿、尚未開始、進行中、已完成

各分組的掃描來源：

- Active：專案根目錄下 `openspec/changes/` 的直接子目錄，排除名為 `archive` 的目錄
- Archived：專案根目錄下 `openspec/changes/archive/` 的直接子目錄
- Parked：解析後的 git 目錄下 `spectra-app/changes/` 的直接子目錄

每個分組內的 change 依名稱字串排序。

### 任務進度解析契約

輸入為 `tasks.md` 的完整文字，輸出為已完成數與總數。

- 任一行去除前導空白後符合核取方塊清單項目語法者計入總數
- 方括號內為空白者計為未完成，為字母 x（不分大小寫）者計為已完成
- 方括號內為其他字元者不計入任何一邊，且不計入總數
- 平行任務標記等出現在核取方塊之後的內容不影響計數
- 位於程式碼圍籬區塊內的行不計入
- 檔案不存在或沒有任何核取方塊時，回傳無進度資訊

### 失敗模式

- 單一 change 目錄讀取失敗（權限不足、目錄消失）時，該 change 從結果中略過，其餘 change 正常顯示，並在 IDE 記錄檔留下警告；不彈出錯誤對話框
- `.openspec.yaml` 缺失或無法解析時，該 change 仍然顯示，僅缺少對應中繼資料
- git 目錄無法解析時，Parked 分組顯示為零筆，不視為錯誤
- 雙擊的檔案在開啟前已被刪除時，顯示不干擾操作的提示，不拋出例外

### 驗收條件

- 掃描層與任務進度解析層具備單元測試，測試以暫存目錄建構仿真的 Spectra 佈局，涵蓋：三分組各自有無資料、active 掃描排除 archive 目錄、`.git` 為檔案時的 gitdir 解析、核取方塊計數含程式碼圍籬與非法標記的情形
- 建置指令能在僅有 Gradle wrapper 與 JDK 的環境下完成，產出可安裝的 plugin 壓縮檔
- 以 Gradle 的 IDE 執行任務啟動沙箱 IDE，開啟本 repository 後，Spectra Tool Window 能列出當下的 active 與 parked change，且 parked change 的名稱與終端機執行 `spectra list --parked` 的結果一致
- 手動驗證：將一個 change 執行 park 後按下 Refresh，該 change 從 Active 移動到 Parked 分組

### 範圍邊界

在範圍內：Gradle 與 Kotlin 專案骨架、plugin 描述檔、掃描層、任務進度解析層、Tool Window 樹狀 UI、Refresh 動作、掃描層與解析層的單元測試。

在範圍外：任何寫入 change 的動作、CLI 呼叫、資料庫讀取、檔案系統監聽、設定頁面、圖示美術資源（使用平台內建圖示）、Marketplace 上架流程、CI 設定。

## Risks / Trade-offs

- 本機沒有獨立 JDK 與 Gradle → 專案自帶 Gradle wrapper；建置文件說明可將 JAVA_HOME 指向 PhpStorm 內建的 JBR，或另行安裝 JDK
- Spectra 未來調整 parked change 的存放位置或 artifact 佈局，會導致掃描結果為空 → 掃描層把三個來源路徑集中在單一常數定義處；空結果以空狀態呈現而非錯誤，使用者仍可從 IDE 記錄檔判讀
- git 目錄位於專案索引範圍外，IDE 可能對其存取施加限制 → 使用 NIO 直接讀取繞過 VFS 索引限制
- 整棵樹重建會使展開狀態遺失 → Refresh 後還原先前展開的分組與 change 節點路徑
- 大量 archived change 會拖慢掃描 → 原先規劃將 artifact 清單延後到展開節點時才走訪。實作時修正為一次走訪完成：掃描層契約要求快照包含 artifact 清單，延後走訪會讓快照不再是完整的不可變狀態，與「樹狀模型以不可變快照重建」決策衝突；而實際成本是每個 change 一次目錄走訪，數十個 change 等級僅數毫秒且已在背景執行緒。若日後 archive 成長到讓掃描出現可感延遲，再改為延後走訪

## Context

工具視窗的樹由 SpectraChangesPanel 建立，節點的 user object 是 ChangeTreeNodes.kt 裡的 SpectraNode 三種實作：GroupNode、ChangeNode、ArtifactNode。樹的繪製交給 SpectraTreeCellRenderer，它繼承 ColoredTreeCellRenderer —— 這是「橡皮圖章」式繪製器，只在畫某一列時被借用，本身不是留在畫面上的活元件，因此沒有任何子元件可以掛滑鼠事件。

目前樹上唯一的滑鼠互動是雙擊 artifact 節點開檔，作法是在 SpectraChangesPanel 用 DoubleClickListener 攔截事件後自行做座標命中測試。

建置設定刻意不引入 IntelliJ 平台測試框架（build.gradle.kts 有註解說明原因：它會註冊自己的 JUnit 5 LauncherSessionListener，在真正的 IDE 測試環境外無法建立）。因此現有測試全部針對不依賴 IDE 的純 JVM 程式碼，UI 元件本身沒有自動化測試覆蓋。

## Goals / Non-Goals

**Goals:**

- 使用者在工具視窗選取一個 change 後，能用 IDE 標準的 Copy 動作把它的名稱取到剪貼簿。
- 鍵盤與滑鼠使用者都能使用，不需要先發現某個視覺元素才知道功能存在。
- 複製邏輯本身有自動化測試覆蓋，不依賴 IDE 測試框架。

**Non-Goals:**

- 不在 change 節點上新增可點擊的圖示。討論階段評估過，結論是它需要 fragment 命中測試、hover 重繪、游標與 tooltip 處理，換來的只是同一個功能的第二個入口。
- 不新增右鍵選單。樹目前沒有安裝任何 popup action group，那是獨立的一項工作。
- 不支援複製 artifact 的路徑或分組名稱。
- 不改變樹的繪製結果：節點文字、圖示、進度後綴都維持原樣。

## Decisions

### 以 CopyProvider 提供複製，不加 inline icon

SpectraChangesPanel 實作 UiDataProvider，在資料快照中提供 PlatformDataKeys.COPY_PROVIDER。IDE 內建的 Copy 動作會自動找到它，於是 Cmd+C（Windows 與 Linux 為 Ctrl+C）在工具視窗取得焦點時即可運作，且自動沿用使用者自訂的按鍵配置。

考慮過的替代方案是在繪製器裡多畫一個圖示片段，再於面板攔截點擊、換算列內相對座標、重跑該列的繪製器佈局、最後以 SimpleColoredComponent 的片段標記查詢命中結果。這條路平台確實支援，但複雜度集中在繪製層，而繪製層是這個外掛裡最難自動測試的部分。CopyProvider 完全不碰繪製層。

### 選取到複製文字的轉換抽成純函式

在 ChangeTreeNodes.kt 新增一個純函式，輸入是被選取節點的 SpectraNode 清單，輸出是要寫入剪貼簿的字串，沒有可複製對象時回傳 null。CopyProvider 只負責從樹取出選取節點、呼叫這個函式、把結果交給剪貼簿。

這樣切的理由是可測性：純函式不依賴 IDE，能用現有的 JUnit 設定直接測，而 CopyProvider 本身剩下的邏輯薄到不需要測試。ChangeTreeNodes.kt 已經放了同性質的純顯示邏輯（applyView、filterChanges、sortChanges、groupCountText），新函式與它們同一層級。

### 非 change 節點停用 Copy

分組節點與 artifact 節點沒有值得複製的名稱：分組名稱是固定的三個字串，artifact 的相對路徑屬於另一個功能。與其複製一個使用者八成不要的字串，不如讓動作停用。

CopyProvider 回報「可複製」的條件，與上述純函式回傳非 null 的條件必須是同一個判斷，不能各寫一份，否則會出現動作看起來可用但按下去沒反應的狀態。

### 多選只取 change 節點並以換行接起

樹沿用預設的選取模型，使用者可以同時選取多列，而且可能混選不同層級。取出選取節點中的 change 節點、忽略其餘、依樹上由上而下的順序以單一換行字元接起。這與 IDE 其他樹狀元件複製多列的行為一致。

### 複製後保持靜默

IDE 內建的 Project view 與 Structure 視窗複製後都不給任何提示。這個外掛既有的通知機制（notifyMissing）是留給「檔案不見了」這種需要使用者知道的意外，複製成功不屬於這一類。

## Implementation Contract

**Behavior**：使用者在 Spectra 工具視窗的樹中選取一或多列後按下 IDE 的 Copy 快捷鍵。若選取範圍包含至少一個 change 節點，系統剪貼簿被寫入這些 change 的名稱，多個名稱以換行分隔，順序與它們在樹上由上而下的順序相同。若選取範圍不含任何 change 節點，剪貼簿內容不變，畫面也沒有任何反應。

**Interface**：

- ChangeTreeNodes.kt 匯出一個純函式，接受被選取節點的 SpectraNode 清單，回傳 String?。清單中每個 ChangeNode 貢獻它的 change 名稱；GroupNode 與 ArtifactNode 一律略過；沒有任何 ChangeNode 時回傳 null。
- SpectraChangesPanel 實作 UiDataProvider，提供 PlatformDataKeys.COPY_PROVIDER。其 CopyProvider 的「是否可複製」以上述函式是否回傳非 null 為準；執行複製時把回傳字串交給平台的剪貼簿管理員。
- 複製的字串是 change 名稱本身，例如 sort-and-filter-changes。不含分組前綴，不含 ChangeNode.id 使用的斜線組合，不含節點上顯示的任務進度。

**Failure modes**：沒有可複製對象時，動作停用而非丟出例外，也不寫入空字串到剪貼簿。純函式對空清單回傳 null，與「只選到分組節點」是同一條路徑。

**Acceptance criteria**：

- 新增的單元測試涵蓋：單一 change 節點、多個 change 節點的順序與換行、只選到分組節點、只選到 artifact 節點、混選時只取 change、空清單。
- 手動驗證：在 IDE 中開啟工具視窗，選取一個 change 按 Copy 後貼到編輯器，得到的字串與樹上顯示的名稱相同且不含進度數字；選取一個 artifact 節點按 Copy，剪貼簿維持先前內容。
- 執行專案既有的建置與測試指令後全部通過。

**Scope boundaries**：

- In scope：ChangeTreeNodes.kt 新增純函式、SpectraChangesPanel 實作 UiDataProvider 與 CopyProvider、對應的單元測試、changes-tool-window 規格新增一項需求。
- Out of scope：繪製器的任何修改、右鍵選單、artifact 或分組的複製、複製後的視覺回饋、掃描層與 plugin.xml。

## Risks / Trade-offs

- 功能不可見，使用者可能不知道能按 Copy → 這是 IDE 的通用慣例（所有樹狀元件都支援 Copy），成本是零學習曲線但也零提示。若日後確認需要提示，補右鍵選單比補 inline icon 便宜得多，而且不必動繪製層。
- UiDataProvider 與 CopyProvider 的實際行為只能靠手動驗證，自動化測試只覆蓋純函式 → 接線部分刻意保持極薄，讓「可能出錯的邏輯」全部落在被測試的那一側。
- 多選以換行接起，若使用者只想要其中一個名稱會多貼到內容 → 這是 IDE 既有慣例，且使用者對自己的選取範圍有完全控制權。

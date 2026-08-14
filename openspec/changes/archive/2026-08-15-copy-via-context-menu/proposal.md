## Why

複製 change 名稱已經能用 IDE 的 Copy 快捷鍵，但那是隱形入口：使用者要先知道樹支援 Copy 才會去按。滑鼠使用者的直覺是在節點上按右鍵，而工具視窗的樹目前沒有安裝任何 popup action group，右鍵按下去毫無反應，看起來像功能不存在。

## What Changes

- 工具視窗的樹安裝右鍵選單，選單只有「Copy Change Name」一項，不提供其他動作；項目右側顯示使用者目前的複製快捷鍵。
- 選單項目的複製結果與 Copy 快捷鍵完全一致：change 名稱本身，不含分組前綴，不含任務進度後綴，多選時以換行接起。
- 選取範圍不含任何 change 節點時，選單項目停用（灰色不可點），與 Copy 快捷鍵的停用條件是同一個判斷。
- 在尚未選取的節點上按右鍵時，先把該節點設為選取，再顯示選單，避免選單動作套用在畫面外的舊選取上。
- 複製成功後不顯示任何通知，與既有的 Copy 行為一致。

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `changes-tool-window`: 既有的「Copy change names to the clipboard」需求擴充一段，描述右鍵選單這個第二入口的內容、停用條件與右鍵選取行為。

## Impact

- Affected specs: `changes-tool-window`
- Affected code:
  - Modified:
    - src/main/kotlin/com/github/fripig/spectraviewer/toolwindow/SpectraChangesPanel.kt
  - New: (none)
  - Removed: (none)
- 不新增相依套件。複製文字的計算沿用既有的純函式，不需要新的複製邏輯。
- plugin.xml 不需要新增註冊內容：選單以程式建立的 action group 掛在樹上，不走 plugin.xml 的 action 宣告。

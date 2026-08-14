## Why

change 名稱是使用者最常需要轉貼的字串：接在 spectra 指令後面、寫進 commit message、貼進對話裡。工具視窗現在只能看不能取，使用者得切到終端機重跑一次列表、或照著樹上的文字手動重打，而 change 名稱是長 kebab-case 字串，手打容易錯字。

## What Changes

- 工具視窗的樹支援 IDE 標準的 Copy 動作（macOS 為 Cmd+C，其他平台為 Ctrl+C），把選取 change 的名稱以純文字寫入系統剪貼簿。
- 複製內容是 change 名稱本身，不含 Active/Parked/Archived 的分組前綴，也不含節點上顯示的任務進度後綴。
- 只有 change 節點可複製。分組節點與 artifact 節點被選取時，Copy 動作停用。
- 多選時只取其中的 change 節點，依樹上由上而下的順序以換行接起；選取範圍內沒有任何 change 節點時，Copy 動作停用。
- 複製成功後不顯示任何通知或提示，與 IDE 內建樹狀元件的既有行為一致。

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `changes-tool-window`: 新增一項需求，描述樹如何回應標準 Copy 動作，以及各層節點的可複製性與多選行為。

## Impact

- Affected specs: `changes-tool-window`
- Affected code:
  - Modified:
    - src/main/kotlin/com/github/fripig/spectraviewer/toolwindow/SpectraChangesPanel.kt
    - src/main/kotlin/com/github/fripig/spectraviewer/toolwindow/ChangeTreeNodes.kt
  - New:
    - src/test/kotlin/com/github/fripig/spectraviewer/toolwindow/CopySelectionTest.kt
  - Removed: (none)
- 不新增任何相依套件，也不影響掃描層與 plugin.xml 的註冊內容。

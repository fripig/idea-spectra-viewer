## Why

Spectra Changes tool window 已經在每個 change 節點上顯示提案者（proposer）名稱，但使用者無法依提案者篩選。在多人協作的專案裡，「這些提案是誰提的」「Alice 和 Bob 手上還有哪些沒做完」是每天都會問的問題，目前只能靠肉眼掃過整棵樹。

既有的名稱過濾輸入框刻意不比對提案者（規格明文要求），因此答案不能是「讓輸入框也比對作者」——那會讓過濾結果變得無法預測，群組列的 `matched/total` 也無法解釋是名稱命中還是作者命中。作者是低基數的列舉值（一個專案通常 2 到 5 位提案者），適合用可複選的清單而非自由文字。

## What Changes

- toolbar 新增一個「Filter by Author」popup，內含每位候選作者一個可勾選項目，**支援複選**，多位作者之間為 OR 關係。
- 作者過濾與既有的名稱過濾為 **AND** 關係：兩者同時生效時，change 必須同時符合才會顯示。
- 候選作者清單由當前 snapshot 的 Active、Parked、Archived 三個群組聯集算出，依字母序排列（不分大小寫），代表「未知作者」的項目固定排在最後。
- `created_by` 缺漏的 change 可透過一個獨立的「未知作者」項目單獨篩出。
- 群組節點的 `matched/total` 計數在「僅作者過濾生效」時同樣顯示，不再只看名稱過濾是否為空。
- 重新掃描（Refresh）後，已不存在於新 snapshot 的作者其勾選自動解除；仍存在的作者維持勾選。
- 候選項目少於兩項時，該 popup 按鈕停用。
- 過濾狀態不做持久化，與既有的排序、名稱過濾一致，工具視窗重開後回到未過濾狀態。
- 既有規格「提案者不被名稱過濾比對」的行為保持不變，僅將該句的適用範圍明確限定為名稱過濾。

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `changes-tool-window`: 新增一條「依作者過濾 change」的需求；並修訂既有「Show the proposer on change nodes」需求中關於過濾的敘述，將其限定為名稱過濾，以免與新需求互相矛盾。

## Impact

- Affected specs: `changes-tool-window`
- Affected code:
  - New:
    - src/main/kotlin/com/github/fripig/spectraviewer/model/ChangeFilter.kt
    - src/main/kotlin/com/github/fripig/spectraviewer/model/AuthorCandidates.kt
    - src/test/kotlin/com/github/fripig/spectraviewer/model/ChangeFilterTest.kt
    - src/test/kotlin/com/github/fripig/spectraviewer/model/AuthorCandidatesTest.kt
  - Modified:
    - src/main/kotlin/com/github/fripig/spectraviewer/toolwindow/ChangeTreeNodes.kt
    - src/main/kotlin/com/github/fripig/spectraviewer/toolwindow/SpectraChangesPanel.kt
    - src/test/kotlin/com/github/fripig/spectraviewer/model/ChangeOrderTest.kt
    - src/test/kotlin/com/github/fripig/spectraviewer/toolwindow/ChangeNodeRenderingTest.kt
  - Removed: (none)
- 不影響掃描層：`ChangeScanner` 與 `ChangeMetadataParser` 完全不動，作者資料沿用既有的 `SpectraChange.createdBy`。

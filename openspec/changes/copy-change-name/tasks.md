## 1. 複製文字的純函式

- [ ] 1.1 依「選取到複製文字的轉換抽成純函式」的決策，在 src/test/kotlin/com/github/fripig/spectraviewer/toolwindow/CopySelectionTest.kt 寫下第一個先失敗的測試：單一 ChangeNode 的選取產出該 change 的名稱，不含分組前綴也不含任務進度。驗證：執行 ./gradlew test，該測試因函式尚不存在而無法通過。
- [ ] 1.2 在 src/main/kotlin/com/github/fripig/spectraviewer/toolwindow/ChangeTreeNodes.kt 實作該純函式，輸入為被選取節點的 SpectraNode 清單、輸出為 String?，使單一 change 的選取產出其名稱。驗證：執行 ./gradlew test，1.1 的測試轉為通過。
- [ ] 1.3 依「非 change 節點停用 Copy」的決策，讓只選到 GroupNode、只選到 ArtifactNode、以及空清單三種情況都回傳 null，使呼叫端能據此停用動作。驗證：CopySelectionTest 中對應的三個測試通過。
- [ ] 1.4 依「多選只取 change 節點並以換行接起」的決策，讓多個 ChangeNode 的選取產出以單一換行接起且保留清單順序的字串，混選時忽略 GroupNode 與 ArtifactNode。驗證：CopySelectionTest 的輸出與 specs/changes-tool-window/spec.md 中 Example 表格列出的六組選取對照完全一致。

## 2. 接上 IDE 的 Copy 動作

- [ ] 2.1 依「以 CopyProvider 提供複製，不加 inline icon」的決策，讓 src/main/kotlin/com/github/fripig/spectraviewer/toolwindow/SpectraChangesPanel.kt 實作 UiDataProvider 並提供 PlatformDataKeys.COPY_PROVIDER，其可用性判斷與實際複製都轉呼叫 1.2 的純函式，SpectraTreeCellRenderer 維持不變。驗證：執行 ./gradlew build 通過，並在 IDE 中選取一個 change 按下 Copy 後貼到編輯器，得到與樹上顯示相同的名稱。
- [ ] 2.2 依「複製後保持靜默」的決策，確認複製路徑不經過 NotificationGroupManager，成功複製時畫面無任何提示。驗證：在 IDE 中複製一個 change 名稱後，沒有出現氣泡通知，且工具視窗維持原本的展開狀態與篩選文字。

## 3. 驗收

- [ ] 3.1 對照 changes-tool-window 規格中 Copy change names to the clipboard 這項需求，逐一手動驗證其五個 Scenario：複製單一名稱、分組節點不可複製、artifact 節點不可複製、多選一次複製、複製不觸發重新掃描。驗證：五個 Scenario 的預期結果全部符合。
- [ ] 3.2 確認整個變更沒有破壞既有行為：排序、名稱篩選、雙擊開檔、Refresh 保留展開狀態都維持原樣。驗證：執行 ./gradlew build 全數通過，並在 IDE 中操作上述四項功能各一次。

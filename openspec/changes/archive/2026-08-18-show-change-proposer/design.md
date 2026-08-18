## Context

Tool window 目前在 change node 上顯示兩件事：change 名稱與任務進度（`3/5`）。掃描階段已經會開啟每個 change 的 `.openspec.yaml`，但只取出 `created` 一個欄位。

Spectra 寫出的 metadata 檔是扁平的 key-value，實際內容如下：

```yaml
schema: spec-driven
created: 2026-08-13
created_by: fripig <fripig@gmail.com>
created_with: claude
archived_by: fripig <fripig@gmail.com>
archived_at: 2026-08-14
```

既有的 `ChangeMetadataParser` 明確不是 YAML parser：它逐行掃描、只認 column 0 起始的 top-level 欄位、上限 100 行，且任何異常（檔案不存在、欄位缺失、格式錯誤）都解析成 null，讓 change 即使 metadata 壞掉仍然會出現在清單上。本次變更沿用這個既有取捨，不推翻它。

限制：tree 一列的水平空間有限，且 IntelliJ 的 `ColoredTreeCellRenderer` 是逐段 append，沒有欄位對齊機制——每一段的起始位置都取決於前一段的實際寬度。

## Goals / Non-Goals

#### Goals

- change node 顯示提案者的顯示名稱，來源為 `.openspec.yaml` 的 `created_by`。
- Active、Parked、Archived 三個群組行為完全一致。
- metadata 無法使用時，change 仍然完整顯示，只是少了提案者那一段。
- 顯示名稱的擷取規則可在不啟動 IDE 的情況下單元測試。

#### Non-Goals

- 不顯示 email，不使用 `created_with`、`archived_by`、`archived_at`。
- 不做「群組內只有一位提案者就隱藏」的動態規則。
- 不新增依提案者排序或篩選。
- 不改動 Copy Change Name 的剪貼簿內容。

## Decisions

### 擴充 ChangeMetadataParser 單次掃描取兩個欄位

`created_by` 與 `created` 同樣是 top-level 的純量欄位，用同一套 `startsWith` 前綴比對即可取得。引入 YAML 函式庫要為了兩個純量欄位背負整個依賴與其 API 表面，也會推翻 parser 檔案內已寫明的取捨。

替代方案：改用 snakeyaml 之類的函式庫做完整解析。否決理由是成本與收益不成比例，且完整解析會讓「壞掉的 metadata 不該讓 change 消失」這條規則變得更難維持——函式庫傾向於在格式錯誤時整份拋例外，而不是逐欄位降級。

實作上，parser 需要能一次讀出兩個欄位而不是把檔案讀兩遍：改成單次掃描、把遇到的目標欄位收進一個結果結構後回傳。

### 顯示名稱擷取規則

`created_by` 的慣例格式是 `Name <email>`。顯示名稱定義為：取第一個 `<` 之前的子字串並去除前後空白；字串中沒有 `<` 時，改用整個值去除前後空白的結果；結果為空字串時視為未知。

替代方案一：顯示完整字串。否決理由是 `fripig <fripig@gmail.com>` 會把任務進度擠出可見範圍。
替代方案二：只顯示 email 的 local part。否決理由是那不是使用者自己填的名字，`a.chen` 比 `Alice Chen` 難辨識。

### SpectraChange 新增 createdBy 欄位

與既有的 `created`、`modified` 一致。model 檔案的註解已載明「Neither has a default value, so a new construction site cannot quietly report both as unknown」，新欄位若給預設值會破壞這條既有約束。

型別選 `String?` 而非新的 value class：本次沒有任何行為依附在提案者上（不排序、不篩選、不比對），包一層型別只會多出轉換成本而不擋掉任何錯誤。若日後要加依提案者篩選，再引入型別也不遲。

### change node 排列順序

提案者插在名稱與進度之間，進度留在該列最後。進度是最常被掃視的資訊，放在固定的尾端位置最穩定；若把提案者放在最後，進度的水平位置會隨提案者名字長短而左右跳動，掃視成本上升。

提案者與進度都使用 `SimpleTextAttributes.GRAYED_ATTRIBUTES`，與既有的群組計數、任務進度一致，避免新增第三種視覺層級。

### 三個群組共用渲染路徑

renderer 對 change node 只有一條分支，不依群組分流。Archived 的 change 同樣顯示 `created_by`（提案者），而不是 `archived_by`（封存者）——這一列回答的問題是「誰提的案」，在三個群組裡都是同一個問題。

## Implementation Contract

#### 可觀察行為

1. **Parser**：給定一份 metadata 文字，回傳的結果同時包含建立日期與提案者顯示名稱。`created` 的既有行為（含 100 行掃描上限、只認 column 0 起始欄位、ISO 日期解析失敗回 null）維持不變。
2. **提案者顯示名稱的擷取**，輸入為 `created_by` 的原始值：

   | 輸入 | 輸出 |
   | --- | --- |
   | `fripig <fripig@gmail.com>` | `fripig` |
   | `Alice Chen <a@example.com>` | `Alice Chen` |
   | `fripig` | `fripig` |
   | `<fripig@gmail.com>` | null |
   | 空白字串或只有空白 | null |

   欄位整行缺失、檔案不存在、檔案無法讀取時同樣是 null。

3. **Scanner**：`ChangeScanner.readChange` 產出的 `SpectraChange` 帶有 `createdBy`，其值等於上表對該 change 的 `.openspec.yaml` 套用擷取規則的結果。metadata 檔缺失或損壞時，`createdBy` 為 null 而該 change 仍然出現在掃描結果中。
4. **Renderer**：`createdBy` 非 null 時，change node 依序 append 名稱、提案者、任務進度，提案者與進度皆為灰字；`createdBy` 為 null 時完全不 append 提案者那一段，不輸出佔位文字。

#### 驗收條件

- `ChangeMetadataParserTest` 涵蓋上表五種輸入各一個案例，外加「同時取得 created 與 created_by」「只有 created 沒有 created_by」兩個案例。
- `ChangeScannerTest` 新增案例：一個 change 的 metadata 含 `created_by` 時 `createdBy` 被填入；另一個 change 完全沒有 metadata 檔時 `createdBy` 為 null 且該 change 仍在掃描結果中。
- `./gradlew build` 通過（含全部單元測試）。
- 手動驗證：在本專案執行 `./gradlew runIde`，Spectra tool window 的 Archived 群組中每個 change 皆顯示 `fripig`。

#### 範圍邊界

**In scope**：`ChangeMetadataParser` 的欄位擷取、`SpectraChange` 的新欄位、`ChangeScanner` 的填值、`SpectraTreeCellRenderer` 的 change node 渲染、上述測試、兩份 README 的功能說明。

**Out of scope**：排序規則（`ChangeOrder` 不變）、篩選規則（`filterChanges` 仍只比對名稱）、Copy 行為（`copyTextFor` 不變）、tree 展開狀態與節點 id（`SpectraNode.id` 不變）。

## Risks / Trade-offs

- **單一開發者的 repo 中每一列都顯示同一個名字，形成視覺噪音** → 接受。此功能的價值在多人 repo；用動態隱藏換取乾淨畫面會讓顯示結果依賴其他 change 的內容，難以預期也難以測試，已在 Non-Goals 排除。
- **`created_by` 若填了很長的名字，會把任務進度推出可見範圍** → 進度仍可經由水平捲動看到，且此為使用者自己在 metadata 中填入的值。本次不加截斷，避免在沒有實際案例前先設一個武斷的長度上限。
- **未來 Spectra 若改變 metadata 格式（例如把 `created_by` 改成巢狀結構）** → parser 會解析成 null，退化為「不顯示提案者」，不會讓 change 從清單消失。這與既有 `created` 的失效行為一致。
- **`SpectraChange` 新增必填欄位會使所有建構點編譯失敗** → 這是刻意的；建構點只有 `ChangeScanner` 與兩個測試檔，全部在本次範圍內修正。

## Migration Plan

不適用。純顯示層增強，無持久化狀態、無設定項、無資料遷移。使用者升級外掛後下次掃描即生效；回退為安裝舊版外掛，`.openspec.yaml` 不會被本外掛寫入或修改。

## Open Questions

無。

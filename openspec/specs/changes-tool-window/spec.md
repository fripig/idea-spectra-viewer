# changes-tool-window Specification

## Purpose

TBD - created by archiving change 'spectra-changes-tool-window'. Update Purpose after archive.

## Requirements

### Requirement: Provide a Spectra tool window

The plugin SHALL register a tool window named Spectra, available in JetBrains IDEs that provide the platform module, including PhpStorm. The tool window SHALL NOT depend on any PHP-specific API.

#### Scenario: Tool window is available

- **WHEN** the plugin is installed and a project is opened
- **THEN** a tool window named Spectra is available and can be opened

---
### Requirement: Display changes as a grouped tree

The tool window SHALL display the scan snapshot as a tree. The first level SHALL consist of exactly three group nodes labelled Active, Parked, and Archived. The second level SHALL consist of change nodes showing the change name. The third level SHALL consist of artifact nodes showing each Markdown file path relative to its change directory.

When the filter text is empty, a group node SHALL show the number of changes in that group. When the filter text is non-empty, a group node SHALL show both the number of matching changes and the group's total number of changes, so that filtered-out changes cannot be mistaken for missing data.

A group node SHALL be displayed even when it contains no changes, and even when no change in it matches the filter.

#### Scenario: Groups render with counts

- **GIVEN** a snapshot with two active changes, one parked change, and no archived changes
- **WHEN** the tool window renders the snapshot with an empty filter
- **THEN** the tree shows Active with count 2, Parked with count 1, and Archived with count 0

#### Scenario: Groups render matched and total counts while filtering

- **GIVEN** a snapshot with three active changes, one parked change, and no archived changes, where one active change and the parked change match the filter
- **WHEN** the tool window renders the snapshot with that filter applied
- **THEN** the Active group shows 1 matching out of 3, the Parked group shows 1 matching out of 1, and the Archived group shows 0 matching out of 0

#### Scenario: Parked changes are visible

- **GIVEN** a change that has been parked and therefore no longer exists under `openspec/changes/`
- **WHEN** the tool window renders the snapshot
- **THEN** that change appears under the Parked group with its name


<!-- @trace
source: sort-and-filter-changes
updated: 2026-08-13
code:
  - src/main/kotlin/com/github/fripig/spectraviewer/toolwindow/ChangeTreeNodes.kt
  - src/main/kotlin/com/github/fripig/spectraviewer/model/SpectraChange.kt
  - src/main/kotlin/com/github/fripig/spectraviewer/discovery/ChangeMetadataParser.kt
  - src/main/kotlin/com/github/fripig/spectraviewer/discovery/ChangeScanner.kt
  - src/main/kotlin/com/github/fripig/spectraviewer/toolwindow/SpectraChangesPanel.kt
  - src/main/kotlin/com/github/fripig/spectraviewer/model/ChangeOrder.kt
tests:
  - src/test/kotlin/com/github/fripig/spectraviewer/discovery/ChangeScannerTest.kt
  - src/test/kotlin/com/github/fripig/spectraviewer/model/ChangeOrderTest.kt
  - src/test/kotlin/com/github/fripig/spectraviewer/discovery/ChangeMetadataParserTest.kt
-->

---
### Requirement: Show task progress on change nodes

A change node SHALL display its completed and total task counts when progress information is available. A change node SHALL NOT display counts when no progress information is available.

#### Scenario: Change with tasks

- **GIVEN** a change whose `tasks.md` has 3 of 8 items complete
- **WHEN** the tool window renders that change node
- **THEN** the node text includes both the completed count 3 and the total count 8

#### Scenario: Change without tasks

- **GIVEN** a change with no counted task items
- **WHEN** the tool window renders that change node
- **THEN** the node text shows the change name without task counts

---
### Requirement: Open artifact files in the editor

Double-clicking an artifact node SHALL open the corresponding file in an editor tab. When the file no longer exists at the time of activation, the system SHALL show a non-blocking notification and SHALL NOT raise an unhandled error.

#### Scenario: Open an artifact

- **WHEN** the user double-clicks an artifact node
- **THEN** the corresponding Markdown file opens in an editor tab

#### Scenario: Open a deleted artifact

- **GIVEN** an artifact node whose backing file was deleted after the last scan
- **WHEN** the user double-clicks that node
- **THEN** a non-blocking notification reports that the file is unavailable and the tool window remains usable

---
### Requirement: Refresh on demand

The tool window SHALL provide a Refresh action in its toolbar that triggers a new scan and rebuilds the tree from the resulting snapshot. Previously expanded group and change nodes SHALL be re-expanded after the rebuild. The tool window SHALL run an initial scan when it is first opened.

#### Scenario: Refresh reflects a newly parked change

- **GIVEN** the tool window shows a change under the Active group
- **WHEN** the change is parked outside the IDE and the user triggers Refresh
- **THEN** the change appears under the Parked group and no longer under the Active group

#### Scenario: Expansion state survives refresh

- **GIVEN** the user has expanded the Parked group and one change node inside it
- **WHEN** the user triggers Refresh and those nodes still exist
- **THEN** the Parked group and that change node are expanded again

#### Scenario: Initial scan on open

- **WHEN** the user opens the tool window for the first time in a session
- **THEN** a scan runs and the tree is populated from its result

---
### Requirement: Indicate loading and empty states

While a scan is running, the tool window SHALL indicate that loading is in progress. When the project root has no `openspec` directory, the tool window SHALL replace the tree with an empty-state message stating that the project is not initialised for Spectra.

#### Scenario: Loading indicator

- **WHEN** a scan is in progress
- **THEN** the tool window indicates that it is loading

#### Scenario: Project without Spectra

- **GIVEN** a project root with no `openspec` directory
- **WHEN** the tool window is opened
- **THEN** an empty-state message is shown instead of the tree

---
### Requirement: Sort changes within groups

The tool window SHALL let the user choose how changes are ordered within each group, from exactly three mutually exclusive options: Name, Modified, and Created. The current option SHALL be indicated in the user interface. The default SHALL be Modified.

Name SHALL order changes by name ascending. Modified SHALL order changes by modification date, most recent first. Created SHALL order changes by creation date, most recent first. For both date orders, changes whose date is unknown SHALL be placed last regardless of direction, and changes sharing a date SHALL be ordered by name ascending.

Sorting SHALL apply within each group only; the order of the three groups themselves SHALL NOT change. Changing the sort option SHALL rebuild the tree without rescanning the file system.

#### Scenario: Each option produces its own order

- **WHEN** the user selects a sort option
- **THEN** every group is reordered according to that option's rules

##### Example: three changes under each sort option

- **GIVEN** the Active group contains `add-search` (created 2026-08-10, modified 2026-08-12 09:00), `mid-tier` (created 2026-08-12, modified 2026-08-11 17:00), and `zebra-fix` (creation date unknown, modified 2026-08-13 08:00)
- **WHEN** the user selects each sort option in turn
- **THEN** Name yields `add-search`, `mid-tier`, `zebra-fix`; Modified yields `zebra-fix`, `add-search`, `mid-tier`; and Created yields `mid-tier`, `add-search`, `zebra-fix`

#### Scenario: Changes with an unknown date sort last

- **GIVEN** a group containing changes both with and without a creation date
- **WHEN** the user sorts by Created
- **THEN** every change with a creation date appears before every change without one

#### Scenario: Sorting does not rescan

- **WHEN** the user changes the sort option
- **THEN** the tree is reordered from the existing snapshot and no file system scan runs

#### Scenario: Default sort option

- **WHEN** the tool window is opened for the first time in a session
- **THEN** changes are ordered by Modified, most recent first


<!-- @trace
source: sort-and-filter-changes
updated: 2026-08-13
code:
  - src/main/kotlin/com/github/fripig/spectraviewer/toolwindow/ChangeTreeNodes.kt
  - src/main/kotlin/com/github/fripig/spectraviewer/model/SpectraChange.kt
  - src/main/kotlin/com/github/fripig/spectraviewer/discovery/ChangeMetadataParser.kt
  - src/main/kotlin/com/github/fripig/spectraviewer/discovery/ChangeScanner.kt
  - src/main/kotlin/com/github/fripig/spectraviewer/toolwindow/SpectraChangesPanel.kt
  - src/main/kotlin/com/github/fripig/spectraviewer/model/ChangeOrder.kt
tests:
  - src/test/kotlin/com/github/fripig/spectraviewer/discovery/ChangeScannerTest.kt
  - src/test/kotlin/com/github/fripig/spectraviewer/model/ChangeOrderTest.kt
  - src/test/kotlin/com/github/fripig/spectraviewer/discovery/ChangeMetadataParserTest.kt
-->

---
### Requirement: Filter changes by name

The tool window SHALL provide a text input that filters changes as the user types. When the filter text is non-empty, a change SHALL be shown only when its name contains the filter text, compared case-insensitively. The filter SHALL apply to all three groups at once.

The filter SHALL match change names only. Artifact paths SHALL NOT participate in the comparison, and every artifact of a matching change SHALL remain visible.

Changing the filter SHALL rebuild the tree without rescanning the file system. A refresh SHALL preserve the current filter text and apply it to the new snapshot.

#### Scenario: Filtering narrows every group

- **GIVEN** the Active group contains `add-search`, `mid-tier`, and `zebra-fix`, and the Parked group contains `search-cache`
- **WHEN** the user types `search`
- **THEN** the Active group shows only `add-search` and the Parked group shows only `search-cache`

#### Scenario: Filtering is case-insensitive

- **GIVEN** the Active group contains `add-search`
- **WHEN** the user types `SEARCH`
- **THEN** `add-search` is shown

#### Scenario: Artifacts of a matching change stay visible

- **GIVEN** a change named `add-search` containing `proposal.md` and `tasks.md`
- **WHEN** the user types `search`
- **THEN** `add-search` is shown with both of its artifact nodes

#### Scenario: A filter matching nothing empties the groups

- **GIVEN** any snapshot
- **WHEN** the user types text that no change name contains
- **THEN** all three group nodes are shown with no change nodes beneath them

#### Scenario: Refresh preserves the filter

- **GIVEN** the user has typed a filter text
- **WHEN** the user triggers Refresh
- **THEN** the new snapshot is rendered with the same filter still applied

<!-- @trace
source: sort-and-filter-changes
updated: 2026-08-13
code:
  - src/main/kotlin/com/github/fripig/spectraviewer/toolwindow/ChangeTreeNodes.kt
  - src/main/kotlin/com/github/fripig/spectraviewer/model/SpectraChange.kt
  - src/main/kotlin/com/github/fripig/spectraviewer/discovery/ChangeMetadataParser.kt
  - src/main/kotlin/com/github/fripig/spectraviewer/discovery/ChangeScanner.kt
  - src/main/kotlin/com/github/fripig/spectraviewer/toolwindow/SpectraChangesPanel.kt
  - src/main/kotlin/com/github/fripig/spectraviewer/model/ChangeOrder.kt
tests:
  - src/test/kotlin/com/github/fripig/spectraviewer/discovery/ChangeScannerTest.kt
  - src/test/kotlin/com/github/fripig/spectraviewer/model/ChangeOrderTest.kt
  - src/test/kotlin/com/github/fripig/spectraviewer/discovery/ChangeMetadataParserTest.kt
-->

---
### Requirement: Copy change names to the clipboard

The tool window SHALL respond to the IDE's standard Copy action by writing the names of the selected changes to the system clipboard as plain text.

The tree SHALL also offer a context menu whose only item is labelled `Copy Change Name`, so the same copy is reachable with the mouse alone. The menu item SHALL display the same shortcut as the Copy action, SHALL copy exactly what the Copy action copies, and SHALL be disabled under exactly the same conditions.

When the user opens the context menu on a node that is not part of the current selection, that node SHALL become the selection before the menu appears, so the copy applies to the node under the pointer. When the user opens the context menu on a node that is already part of a multi-node selection, the selection SHALL remain unchanged.

The copied text SHALL be the change name alone. It SHALL NOT include the group the change belongs to, and it SHALL NOT include the task progress counts shown on the node.

Only change nodes SHALL be copyable. When the selection contains no change node, the Copy action SHALL be disabled and the clipboard contents SHALL remain unchanged.

When the selection contains several change nodes, the copied text SHALL contain every selected change name, separated by a single newline, in the order the nodes appear in the tree from top to bottom. Group nodes and artifact nodes in the selection SHALL be ignored rather than blocking the copy.

A successful copy SHALL NOT produce a notification or any other visible feedback.

#### Scenario: Copy a single change name

- **GIVEN** the Active group contains a change named `sort-and-filter-changes` whose task progress is 3 of 7
- **WHEN** the user selects that change node and invokes the Copy action
- **THEN** the clipboard contains exactly `sort-and-filter-changes`, without the group name and without the progress counts

#### Scenario: Copy is unavailable on a group node

- **WHEN** the user selects a group node and invokes the Copy action
- **THEN** the clipboard contents remain unchanged and no notification is shown

#### Scenario: Copy is unavailable on an artifact node

- **WHEN** the user selects an artifact node and invokes the Copy action
- **THEN** the clipboard contents remain unchanged and no notification is shown

#### Scenario: Copy several changes at once

- **WHEN** the user selects more than one change node and invokes the Copy action
- **THEN** the clipboard contains every selected change name, one per line, ordered as the nodes appear in the tree

##### Example: selections and their clipboard contents

| Selection (top to bottom) | Clipboard contents |
| ------------------------- | ------------------ |
| change `add-search` | `add-search` |
| changes `add-search`, `zebra-fix` | `add-search` newline `zebra-fix` |
| group Active | unchanged |
| artifact `design.md` | unchanged |
| group Active, change `add-search`, artifact `design.md` | `add-search` |
| nothing selected | unchanged |

#### Scenario: Copying does not rescan or rebuild the tree

- **WHEN** the user invokes the Copy action
- **THEN** no file system scan runs, and the tree keeps its current expansion state, sort order, and filter text

#### Scenario: The context menu offers Copy Change Name and nothing else

- **GIVEN** a change node is selected
- **WHEN** the user opens the context menu on that node
- **THEN** the menu contains `Copy Change Name` as its only item, showing the Copy shortcut beside it, and invoking it copies the change name

#### Scenario: The context menu item is disabled when nothing is copyable

- **GIVEN** a group node is selected
- **WHEN** the user opens the context menu on that node
- **THEN** the `Copy Change Name` item is shown disabled and the clipboard contents remain unchanged

#### Scenario: The context menu retargets the selection

- **GIVEN** the change `add-search` is selected
- **WHEN** the user opens the context menu on the unselected change `zebra-fix` and invokes `Copy Change Name`
- **THEN** `zebra-fix` is the selected node and the clipboard contains exactly `zebra-fix`

#### Scenario: The context menu keeps an existing multi-node selection

- **GIVEN** the changes `add-search` and `zebra-fix` are both selected
- **WHEN** the user opens the context menu on `zebra-fix` and invokes `Copy Change Name`
- **THEN** both nodes remain selected and the clipboard contains `add-search` and `zebra-fix`, one per line

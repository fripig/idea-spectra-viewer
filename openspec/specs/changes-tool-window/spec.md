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

A filter is active when the filter text is non-empty, when at least one author candidate is selected, or both. When no filter is active, a group node SHALL show the number of changes in that group. When a filter is active, a group node SHALL show both the number of matching changes and the group's total number of changes, so that filtered-out changes cannot be mistaken for missing data.

A group node SHALL be displayed even when it contains no changes, and even when no change in it matches the filter.

#### Scenario: Groups render with counts

- **GIVEN** a snapshot with two active changes, one parked change, and no archived changes
- **WHEN** the tool window renders the snapshot with an empty filter text and no author selected
- **THEN** the tree shows Active with count 2, Parked with count 1, and Archived with count 0

#### Scenario: Groups render matched and total counts while filtering by name

- **GIVEN** a snapshot with three active changes, one parked change, and no archived changes, where one active change and the parked change match the filter text
- **WHEN** the tool window renders the snapshot with that filter text applied and no author selected
- **THEN** the Active group shows 1 matching out of 3, the Parked group shows 1 matching out of 1, and the Archived group shows 0 matching out of 0

#### Scenario: Groups render matched and total counts while filtering by author alone

- **GIVEN** a snapshot with three active changes, of which one is proposed by `alice`
- **WHEN** the tool window renders the snapshot with an empty filter text and the author `alice` selected
- **THEN** the Active group shows 1 matching out of 3

#### Scenario: Parked changes are visible

- **GIVEN** a change that has been parked and therefore no longer exists under `openspec/changes/`
- **WHEN** the tool window renders the snapshot
- **THEN** that change appears under the Parked group with its name


<!-- @trace
source: filter-changes-by-author
updated: 2026-09-03
code:
  - src/main/kotlin/com/github/fripig/spectraviewer/toolwindow/SpectraChangesPanel.kt
  - src/main/kotlin/com/github/fripig/spectraviewer/model/ChangeFilter.kt
  - src/main/kotlin/com/github/fripig/spectraviewer/toolwindow/ChangeTreeNodes.kt
  - src/main/kotlin/com/github/fripig/spectraviewer/model/AuthorCandidates.kt
tests:
  - src/test/kotlin/com/github/fripig/spectraviewer/model/ChangeFilterTest.kt
  - src/test/kotlin/com/github/fripig/spectraviewer/model/AuthorCandidatesTest.kt
  - src/test/kotlin/com/github/fripig/spectraviewer/toolwindow/ChangeNodeRenderingTest.kt
  - src/test/kotlin/com/github/fripig/spectraviewer/model/ChangeOrderTest.kt
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

While a scan is running, the tool window SHALL indicate that loading is in progress. When the project's resolved spec directory does not exist, the tool window SHALL replace the tree with an empty-state message stating that the project is not initialised for Spectra. The message SHALL NOT name a directory, because the spec directory is resolved from the project's configuration and differs between projects.

#### Scenario: Loading indicator

- **WHEN** a scan is in progress
- **THEN** the tool window indicates that it is loading

#### Scenario: Project without Spectra

- **GIVEN** a project root whose resolved spec directory does not exist
- **WHEN** the tool window is opened
- **THEN** an empty-state message is shown instead of the tree

#### Scenario: A project using the configured spec directory shows the tree

- **GIVEN** a project root holding a `.spectra.yaml` containing `spec_dir: docs/spectra`, and a `docs/spectra/` directory
- **WHEN** the tool window is opened
- **THEN** the tree is shown and the empty-state message is not shown

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
### Requirement: Filter changes by author

The tool window SHALL provide an author filter that is separate from the filter text. The author filter SHALL offer a list of candidate authors and SHALL allow any number of them to be selected at the same time.

The candidate list SHALL be derived from the current snapshot: the distinct proposers of every change in the Active, Parked, and Archived groups. Candidates SHALL be ordered alphabetically, compared case-insensitively. When at least one change in the snapshot has an unknown proposer, the candidate list SHALL also offer a single `Unknown` candidate, and that candidate SHALL be ordered after every named author. When every change in the snapshot has a known proposer, the candidate list SHALL NOT offer the `Unknown` candidate.

When no candidate is selected, the author filter SHALL NOT restrict which changes are shown. When one or more candidates are selected, a change SHALL be shown only when its proposer is one of the selected authors, or when its proposer is unknown and the `Unknown` candidate is selected. Selected candidates SHALL be combined as a disjunction.

The author filter and the filter text SHALL be combined as a conjunction. When both are active, a change SHALL be shown only when its name contains the filter text and its proposer satisfies the author selection.

The author filter SHALL apply to all three groups at once. Changing the author selection SHALL rebuild the tree without rescanning the file system.

A refresh SHALL preserve the selection of every author that still appears in the new snapshot's candidate list, and SHALL clear the selection of every author that no longer appears in it.

The author filter control SHALL be disabled when the candidate list holds fewer than two candidates.

The author filter SHALL NOT be persisted. A newly created tool window SHALL start with no candidate selected.

#### Scenario: Selecting one author

- **GIVEN** the Active group contains `add-dark-mode` proposed by `alice` and `fix-login` proposed by `bob`
- **WHEN** the user selects the author `alice`
- **THEN** the Active group shows `add-dark-mode` only

#### Scenario: Selecting several authors

- **GIVEN** the Active group contains `add-dark-mode` proposed by `alice`, `fix-login` proposed by `bob`, and `tidy-logs` proposed by `carol`
- **WHEN** the user selects the authors `alice` and `bob`
- **THEN** the Active group shows `add-dark-mode` and `fix-login`, and does not show `tidy-logs`

#### Scenario: Selecting the unknown-proposer candidate

- **GIVEN** the Active group contains `add-dark-mode` proposed by `alice` and `legacy-change` whose proposer is unknown
- **WHEN** the user selects the `Unknown` candidate
- **THEN** the Active group shows `legacy-change` only

#### Scenario: No selection leaves every change visible

- **GIVEN** the Active group contains changes proposed by `alice` and by `bob`
- **WHEN** no author candidate is selected and the filter text is empty
- **THEN** the Active group shows every change it holds

#### Scenario: The author filter and the filter text are combined as a conjunction

- **GIVEN** the Active group contains `add-dark-mode` proposed by `alice`, `add-light-mode` proposed by `bob`, and `fix-login` proposed by `alice`
- **WHEN** the user selects the author `alice` and types `add` into the filter text
- **THEN** the Active group shows `add-dark-mode` only

##### Example: combinations of filter text and author selection

| Filter text | Selected authors  | Shown changes                             |
| ----------- | ----------------- | ----------------------------------------- |
| empty       | none              | `add-dark-mode`, `add-light-mode`, `fix-login` |
| empty       | `alice`           | `add-dark-mode`, `fix-login`              |
| `add`       | none              | `add-dark-mode`, `add-light-mode`         |
| `add`       | `alice`           | `add-dark-mode`                           |
| `add`       | `alice`, `bob`    | `add-dark-mode`, `add-light-mode`         |
| `zzz`       | `alice`           | none                                      |

#### Scenario: The author filter applies to all three groups

- **GIVEN** `alice` has proposed one change in the Active group, one in the Parked group, and one in the Archived group, and `bob` has proposed one change in each of those groups
- **WHEN** the user selects the author `alice`
- **THEN** each of the three groups shows the change proposed by `alice` and does not show the change proposed by `bob`

#### Scenario: Candidates are ordered alphabetically with the unknown candidate last

- **GIVEN** a snapshot whose changes are proposed by `Carol`, `alice`, and `Bob`, and which also holds one change whose proposer is unknown
- **WHEN** the tool window builds the candidate list
- **THEN** the candidates are `alice`, `Bob`, `Carol`, `Unknown`, in that order

##### Example: candidate lists by snapshot content

| Proposers in the snapshot                    | Candidate list                     |
| -------------------------------------------- | ---------------------------------- |
| `alice`, `bob`                                | `alice`, `bob`                     |
| `bob`, `alice`, `bob`                         | `alice`, `bob`                     |
| `Carol`, `alice`, `Bob`                       | `alice`, `Bob`, `Carol`            |
| `alice`, unknown                              | `alice`, `Unknown`                 |
| unknown only                                  | `Unknown`                          |
| no changes at all                             | empty                              |

#### Scenario: A refresh keeps the selection of an author who is still present

- **GIVEN** the user has selected the author `alice`, whose changes are still present after a rescan
- **WHEN** the user refreshes the tool window
- **THEN** the new snapshot is rendered with `alice` still selected

#### Scenario: A refresh clears the selection of an author who has disappeared

- **GIVEN** the user has selected the authors `alice` and `bob`, and a rescan finds no change proposed by `bob`
- **WHEN** the user refreshes the tool window
- **THEN** `bob` is no longer offered as a candidate and is no longer selected, and `alice` remains selected

#### Scenario: The control is disabled when there is nothing to choose between

- **GIVEN** a snapshot in which every change has the same proposer and no change has an unknown proposer, so the candidate list holds one candidate
- **WHEN** the tool window renders its toolbar
- **THEN** the author filter control is disabled

#### Scenario: Changing the author selection does not rescan

- **GIVEN** the tool window has rendered a snapshot
- **WHEN** the user selects or deselects an author candidate
- **THEN** no file system scan runs, and the tree keeps its current expansion state, sort order, and filter text

#### Scenario: A new tool window starts with no author selected

- **GIVEN** the user selected an author and then closed the tool window
- **WHEN** the tool window is created again
- **THEN** no author candidate is selected and every change is shown

<!-- @trace
source: filter-changes-by-author
updated: 2026-09-03
code:
  - src/main/kotlin/com/github/fripig/spectraviewer/model/AuthorCandidates.kt
  - src/main/kotlin/com/github/fripig/spectraviewer/model/ChangeFilter.kt
  - src/main/kotlin/com/github/fripig/spectraviewer/toolwindow/ChangeTreeNodes.kt
  - src/main/kotlin/com/github/fripig/spectraviewer/toolwindow/SpectraChangesPanel.kt
tests:
  - src/test/kotlin/com/github/fripig/spectraviewer/model/AuthorCandidatesTest.kt
  - src/test/kotlin/com/github/fripig/spectraviewer/model/ChangeFilterTest.kt
  - src/test/kotlin/com/github/fripig/spectraviewer/toolwindow/ChangeNodeRenderingTest.kt
-->


<!-- @trace
source: filter-changes-by-author
updated: 2026-09-03
code:
  - src/main/kotlin/com/github/fripig/spectraviewer/toolwindow/SpectraChangesPanel.kt
  - src/main/kotlin/com/github/fripig/spectraviewer/model/ChangeFilter.kt
  - src/main/kotlin/com/github/fripig/spectraviewer/toolwindow/ChangeTreeNodes.kt
  - src/main/kotlin/com/github/fripig/spectraviewer/model/AuthorCandidates.kt
tests:
  - src/test/kotlin/com/github/fripig/spectraviewer/model/ChangeFilterTest.kt
  - src/test/kotlin/com/github/fripig/spectraviewer/model/AuthorCandidatesTest.kt
  - src/test/kotlin/com/github/fripig/spectraviewer/toolwindow/ChangeNodeRenderingTest.kt
  - src/test/kotlin/com/github/fripig/spectraviewer/model/ChangeOrderTest.kt
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

---
### Requirement: Show the proposer on change nodes

A change node SHALL display the proposer of its change when a proposer is available. A change node SHALL NOT display any placeholder text when the proposer is unknown.

The proposer SHALL be rendered between the change name and the task progress counts, so that the progress counts stay at the end of the node text and their position does not shift with the length of the proposer name. The proposer SHALL be rendered with the same de-emphasised styling as the task progress counts.

This behaviour SHALL be identical for change nodes in the Active, Parked, and Archived groups. An archived change SHALL show its proposer, not whoever archived it.

The proposer SHALL NOT affect the sort order, SHALL NOT be matched by the filter text, and SHALL NOT be included in the text the Copy action writes to the clipboard. The proposer SHALL be matched only by the author selection described in the "Filter changes by author" requirement.

#### Scenario: Change with a known proposer

- **GIVEN** a change named `add-dark-mode` proposed by `fripig` whose `tasks.md` has 3 of 8 items complete
- **WHEN** the tool window renders that change node
- **THEN** the node text contains `add-dark-mode`, then `fripig`, then the counts 3 and 8, in that order

#### Scenario: Change with an unknown proposer

- **GIVEN** a change whose proposer is unknown
- **WHEN** the tool window renders that change node
- **THEN** the node text shows the change name and its task counts, with no proposer segment and no placeholder text

#### Scenario: Change with a proposer and no task progress

- **GIVEN** a change named `add-dark-mode` proposed by `fripig` with no counted task items
- **WHEN** the tool window renders that change node
- **THEN** the node text contains `add-dark-mode` and `fripig`, and no task counts

#### Scenario: Every group shows the proposer

- **GIVEN** one change in each of the Active, Parked, and Archived groups, each with a known proposer
- **WHEN** the tool window renders the tree
- **THEN** all three change nodes display their proposer

#### Scenario: Archived change shows its proposer rather than its archiver

- **GIVEN** an archived change whose `.openspec.yaml` records `created_by: alice <alice@example.com>` and `archived_by: bob <bob@example.com>`
- **WHEN** the tool window renders that change node
- **THEN** the node text contains `alice` and does not contain `bob`

#### Scenario: Copying a change with a proposer

- **GIVEN** a change named `add-dark-mode` proposed by `fripig`
- **WHEN** the user selects that change node and invokes the Copy action
- **THEN** the clipboard contains exactly `add-dark-mode`, without the proposer

#### Scenario: The filter text does not match the proposer

- **GIVEN** the Active group contains a change named `add-dark-mode` proposed by `fripig`
- **WHEN** the user types `fripig` into the filter text and selects no author
- **THEN** that change does not appear among the matching changes

##### Example: node text by proposer and progress

| Change name      | Proposer | Task progress | Node text                       |
| ---------------- | -------- | ------------- | ------------------------------- |
| `add-dark-mode`  | `fripig` | 3 of 8        | `add-dark-mode` `fripig` `3/8`  |
| `add-dark-mode`  | unknown  | 3 of 8        | `add-dark-mode` `3/8`           |
| `add-dark-mode`  | `fripig` | none          | `add-dark-mode` `fripig`        |
| `add-dark-mode`  | unknown  | none          | `add-dark-mode`                 |


<!-- @trace
source: filter-changes-by-author
updated: 2026-09-03
code:
  - src/main/kotlin/com/github/fripig/spectraviewer/toolwindow/SpectraChangesPanel.kt
  - src/main/kotlin/com/github/fripig/spectraviewer/model/ChangeFilter.kt
  - src/main/kotlin/com/github/fripig/spectraviewer/toolwindow/ChangeTreeNodes.kt
  - src/main/kotlin/com/github/fripig/spectraviewer/model/AuthorCandidates.kt
tests:
  - src/test/kotlin/com/github/fripig/spectraviewer/model/ChangeFilterTest.kt
  - src/test/kotlin/com/github/fripig/spectraviewer/model/AuthorCandidatesTest.kt
  - src/test/kotlin/com/github/fripig/spectraviewer/toolwindow/ChangeNodeRenderingTest.kt
  - src/test/kotlin/com/github/fripig/spectraviewer/model/ChangeOrderTest.kt
-->

---
### Requirement: Send a Spectra command for the selected change

The tool window's context menu SHALL offer a submenu that carries five Spectra commands, each already applied to the selected change: `/spectra-discuss`, `/spectra-apply`, `/spectra-ingest`, `/spectra-archive` and `/spectra-commit`, listed in that order. The order follows the project's workflow: discuss opens a change, apply and ingest alternate while it is being built, archive ends it, and commit lands files after any step, so it is last. The submenu SHALL NOT offer `/spectra-propose`, because that command creates a change rather than acting on an existing one. Each item SHALL display the full command text it produces, so what the user reads is exactly what the item delivers.

The command text SHALL be a single line consisting of the slash command name, one space, and the change name, with no trailing newline. It SHALL NOT include the group the change belongs to, and it SHALL NOT include the task progress counts or the proposer shown on the node.

A terminal target SHALL be available only when all of the following hold: the IDE's terminal plugin is installed and enabled, the Terminal tool window has a selected tab, and that tab's terminal connection is ready. When any of the three does not hold, no terminal target is available.

When a terminal target is available, the submenu title SHALL read `Send to Terminal`, and invoking an item SHALL write that item's command text into the selected terminal tab's input position without appending a newline and without executing it, then activate the Terminal tool window and move focus to that tab. The clipboard contents SHALL remain unchanged.

When no terminal target is available, the submenu title SHALL read `Copy Command`, and invoking an item SHALL write that item's command text to the system clipboard. Focus SHALL NOT move, and no notification SHALL be shown.

The submenu SHALL be enabled only when the selection contains exactly one change node. Group nodes and artifact nodes in the selection SHALL be ignored rather than blocking that count, matching how the Copy action treats them. When the selection contains no change node or more than one, the submenu SHALL be disabled.

Invoking any item SHALL NOT run a file system scan, and the tree SHALL keep its current expansion state, sort order, and filter text.

The existing Copy Change Name item SHALL remain first in the context menu, and its behaviour SHALL be unchanged.

#### Scenario: Send a command to the selected terminal tab

- **GIVEN** the Active group contains a change named `add-search` and the Terminal tool window has a selected tab whose terminal connection is ready
- **WHEN** the user selects that change node and invokes `/spectra-apply add-search` from the submenu
- **THEN** the text `/spectra-apply add-search` appears at that tab's input position, no newline is sent, the command is not executed, the Terminal tool window is activated with focus on that tab, and the clipboard contents remain unchanged

#### Scenario: The user decides whether the command runs

- **GIVEN** a command has been written into the selected terminal tab
- **WHEN** the user presses Enter in that tab
- **THEN** the command is submitted, and until that moment nothing has been executed

#### Scenario: The submenu title names the action that will happen

- **WHEN** the user opens the context menu on a single change node
- **THEN** the submenu title reads `Send to Terminal` if a terminal target is available and `Copy Command` if it is not, while the item texts stay the same in both states

#### Scenario: Fall back to the clipboard when the Terminal tool window has no tab

- **GIVEN** the Terminal tool window is closed or holds no tab
- **WHEN** the user invokes `/spectra-ingest add-search` from the submenu
- **THEN** the clipboard contains exactly `/spectra-ingest add-search`, focus does not move, and no notification is shown

#### Scenario: The plugin works when the terminal plugin is disabled

- **GIVEN** the IDE's terminal plugin is disabled
- **WHEN** the user opens the Spectra tool window and opens the context menu on a change node
- **THEN** the tool window works as before, the submenu title reads `Copy Command`, the submenu is enabled, and invoking an item writes the command text to the clipboard

#### Scenario: Writing to the terminal fails

- **GIVEN** a terminal target is available
- **WHEN** invoking an item raises an error while writing to that terminal
- **THEN** the error is recorded in the IDE log, the command text is written to the clipboard instead, and no dialog is shown

#### Scenario: The submenu is disabled for a multi-node selection

- **WHEN** the user selects two change nodes and opens the context menu
- **THEN** the submenu is shown disabled, nothing is sent, and the clipboard contents remain unchanged

#### Scenario: The submenu is disabled when no change node is selected

- **WHEN** the user selects only a group node, only an artifact node, or nothing at all, and opens the context menu
- **THEN** the submenu is shown disabled, nothing is sent, and the clipboard contents remain unchanged

#### Scenario: Group and artifact nodes do not block a single change selection

- **GIVEN** the selection holds the group Active, the change `add-search`, and the artifact `design.md`
- **WHEN** the user opens the context menu
- **THEN** the submenu is enabled and its items carry the change name `add-search`

#### Scenario: Sending or copying a command does not rescan or rebuild the tree

- **WHEN** the user invokes any item in the submenu
- **THEN** no file system scan runs, and the tree keeps its current expansion state, sort order, and filter text

#### Scenario: The submenu lists the five commands in workflow order

- **WHEN** the user opens the context menu on a single change node named `add-search`
- **THEN** the submenu items read, top to bottom, `/spectra-discuss add-search`, `/spectra-apply add-search`, `/spectra-ingest add-search`, `/spectra-archive add-search`, `/spectra-commit add-search`, and no item for `/spectra-propose` is present

#### Scenario: Copy Change Name is unaffected

- **GIVEN** a single change node named `add-search` is selected
- **WHEN** the user invokes the Copy action or the `Copy Change Name` menu item
- **THEN** the clipboard contains exactly `add-search`, with no slash command and no space-separated prefix

##### Example: command text for each submenu item

- **GIVEN** the selected change is named `add-search`

| Submenu item | Command text produced |
| ------------ | --------------------- |
| `/spectra-discuss add-search` | `/spectra-discuss add-search` |
| `/spectra-apply add-search` | `/spectra-apply add-search` |
| `/spectra-ingest add-search` | `/spectra-ingest add-search` |
| `/spectra-archive add-search` | `/spectra-archive add-search` |
| `/spectra-commit add-search` | `/spectra-commit add-search` |

##### Example: submenu state and destination

| Selection | Terminal target available | Submenu title | Invoking an item |
| --------- | ------------------------- | ------------- | ---------------- |
| one change `add-search` | yes | `Send to Terminal` | text written to the selected tab, focus moves there |
| one change `add-search` | no | `Copy Command` | text written to the clipboard, focus stays |
| changes `add-search` and `zebra-fix` | yes | `Send to Terminal` | submenu disabled, nothing happens |
| group Active only | no | `Copy Command` | submenu disabled, nothing happens |
| group Active, change `add-search`, artifact `design.md` | yes | `Send to Terminal` | text for `add-search` written to the selected tab |
| nothing selected | yes | `Send to Terminal` | submenu disabled, nothing happens |

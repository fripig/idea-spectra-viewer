## ADDED Requirements

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

## MODIFIED Requirements

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

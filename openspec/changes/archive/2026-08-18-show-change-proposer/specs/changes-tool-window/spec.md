## ADDED Requirements

### Requirement: Show the proposer on change nodes

A change node SHALL display the proposer of its change when a proposer is available. A change node SHALL NOT display any placeholder text when the proposer is unknown.

The proposer SHALL be rendered between the change name and the task progress counts, so that the progress counts stay at the end of the node text and their position does not shift with the length of the proposer name. The proposer SHALL be rendered with the same de-emphasised styling as the task progress counts.

This behaviour SHALL be identical for change nodes in the Active, Parked, and Archived groups. An archived change SHALL show its proposer, not whoever archived it.

The proposer SHALL NOT affect the sort order, SHALL NOT be matched by the name filter, and SHALL NOT be included in the text the Copy action writes to the clipboard.

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

#### Scenario: Filtering does not match the proposer

- **GIVEN** the Active group contains a change named `add-dark-mode` proposed by `fripig`
- **WHEN** the user types `fripig` into the filter
- **THEN** that change does not appear among the matching changes

##### Example: node text by proposer and progress

| Change name      | Proposer | Task progress | Node text                       |
| ---------------- | -------- | ------------- | ------------------------------- |
| `add-dark-mode`  | `fripig` | 3 of 8        | `add-dark-mode` `fripig` `3/8`  |
| `add-dark-mode`  | unknown  | 3 of 8        | `add-dark-mode` `3/8`           |
| `add-dark-mode`  | `fripig` | none          | `add-dark-mode` `fripig`        |
| `add-dark-mode`  | unknown  | none          | `add-dark-mode`                 |

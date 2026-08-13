## ADDED Requirements

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

## MODIFIED Requirements

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

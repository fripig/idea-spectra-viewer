## ADDED Requirements

### Requirement: Provide a Spectra tool window

The plugin SHALL register a tool window named Spectra, available in JetBrains IDEs that provide the platform module, including PhpStorm. The tool window SHALL NOT depend on any PHP-specific API.

#### Scenario: Tool window is available

- **WHEN** the plugin is installed and a project is opened
- **THEN** a tool window named Spectra is available and can be opened

### Requirement: Display changes as a grouped tree

The tool window SHALL display the scan snapshot as a tree. The first level SHALL consist of exactly three group nodes labelled Active, Parked, and Archived, each showing the number of changes in that group. The second level SHALL consist of change nodes showing the change name. The third level SHALL consist of artifact nodes showing each Markdown file path relative to its change directory.

A group node SHALL be displayed even when it contains no changes, showing a count of zero.

#### Scenario: Groups render with counts

- **GIVEN** a snapshot with two active changes, one parked change, and no archived changes
- **WHEN** the tool window renders the snapshot
- **THEN** the tree shows Active with count 2, Parked with count 1, and Archived with count 0

#### Scenario: Parked changes are visible

- **GIVEN** a change that has been parked and therefore no longer exists under `openspec/changes/`
- **WHEN** the tool window renders the snapshot
- **THEN** that change appears under the Parked group with its name

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

### Requirement: Open artifact files in the editor

Double-clicking an artifact node SHALL open the corresponding file in an editor tab. When the file no longer exists at the time of activation, the system SHALL show a non-blocking notification and SHALL NOT raise an unhandled error.

#### Scenario: Open an artifact

- **WHEN** the user double-clicks an artifact node
- **THEN** the corresponding Markdown file opens in an editor tab

#### Scenario: Open a deleted artifact

- **GIVEN** an artifact node whose backing file was deleted after the last scan
- **WHEN** the user double-clicks that node
- **THEN** a non-blocking notification reports that the file is unavailable and the tool window remains usable

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

### Requirement: Indicate loading and empty states

While a scan is running, the tool window SHALL indicate that loading is in progress. When the project root has no `openspec` directory, the tool window SHALL replace the tree with an empty-state message stating that the project is not initialised for Spectra.

#### Scenario: Loading indicator

- **WHEN** a scan is in progress
- **THEN** the tool window indicates that it is loading

#### Scenario: Project without Spectra

- **GIVEN** a project root with no `openspec` directory
- **WHEN** the tool window is opened
- **THEN** an empty-state message is shown instead of the tree

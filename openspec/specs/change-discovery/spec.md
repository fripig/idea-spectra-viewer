# change-discovery Specification

## Purpose

TBD - created by archiving change 'spectra-changes-tool-window'. Update Purpose after archive.

## Requirements

### Requirement: Scan changes from all three Spectra sources

The system SHALL discover Spectra changes by reading the file system directly, without invoking the `spectra` CLI or reading the Spectra internal database. A scan SHALL take a project root directory as input and produce a snapshot containing three groups of changes: Active, Parked, and Archived.

Active changes SHALL be the immediate subdirectories of `openspec/changes/` under the project root, excluding the directory named `archive`. Archived changes SHALL be the immediate subdirectories of `openspec/changes/archive/` under the project root. Parked changes SHALL be the immediate subdirectories of `spectra-app/changes/` under the resolved git directory.

Within each group, changes SHALL be sorted by name.

#### Scenario: All three sources contain changes

- **WHEN** a scan runs against a project root that has active, parked, and archived change directories
- **THEN** the snapshot contains each change in exactly the group matching its source directory

##### Example: mixed layout

- **GIVEN** the project root contains `openspec/changes/add-search/`, `openspec/changes/archive/old-login/`, and `.git/spectra-app/changes/dark-mode/`
- **WHEN** a scan runs
- **THEN** Active contains `add-search`, Archived contains `old-login`, and Parked contains `dark-mode`

#### Scenario: The archive directory is not treated as an active change

- **WHEN** a scan runs against a project root where `openspec/changes/archive/` exists
- **THEN** no change named `archive` appears in the Active group

#### Scenario: A source directory is absent

- **WHEN** a scan runs and one of the three source directories does not exist
- **THEN** the corresponding group is empty and the remaining groups are populated normally

#### Scenario: Changes are sorted by name

- **WHEN** a group contains more than one change
- **THEN** the changes in that group appear in ascending order by name

##### Example: active group ordering

- **GIVEN** `openspec/changes/` contains directories `zebra-fix`, `add-search`, and `mid-tier`
- **WHEN** a scan runs
- **THEN** the Active group lists them in the order: `add-search`, `mid-tier`, `zebra-fix`

---
### Requirement: Resolve the git directory including worktree indirection

The system SHALL locate parked changes relative to the resolved git directory rather than assuming a `.git` subdirectory of the project root.

When `.git` under the project root is a directory, that directory SHALL be the resolved git directory. When `.git` is a file, the system SHALL read its `gitdir:` pointer to obtain the actual git directory; if that directory contains a `commondir` file, the path recorded in `commondir` SHALL be resolved against it and used as the resolved git directory. When `.git` is absent or cannot be resolved, the Parked group SHALL be empty and the scan SHALL NOT fail.

#### Scenario: Standard repository layout

- **WHEN** the project root contains a `.git` directory
- **THEN** parked changes are read from `spectra-app/changes/` inside that directory

#### Scenario: Git worktree layout

- **GIVEN** the project root contains a `.git` file whose content is a `gitdir:` pointer to a worktree directory containing a `commondir` file
- **WHEN** a scan runs
- **THEN** parked changes are read from `spectra-app/changes/` inside the common git directory named by `commondir`

#### Scenario: Git directory cannot be resolved

- **WHEN** the project root has no `.git` entry, or the `.git` file points to a path that does not exist
- **THEN** the Parked group is empty and the Active and Archived groups are still populated

---
### Requirement: Report per-change metadata

For each discovered change, the system SHALL report its name, its group, the absolute path of its directory, the list of its Markdown artifact files, its task progress, and its derived status.

The name SHALL be the change directory name. The artifact file list SHALL contain every file with the `.md` extension found beneath the change directory, expressed as a path relative to that change directory, sorted as strings.

#### Scenario: Artifact files are listed relative to the change directory

- **GIVEN** a change directory containing `proposal.md`, `tasks.md`, and `specs/theme-engine/spec.md`
- **WHEN** a scan reports that change
- **THEN** the artifact list contains exactly `proposal.md`, `specs/theme-engine/spec.md`, and `tasks.md`, in that string order

#### Scenario: Non-Markdown files are excluded

- **GIVEN** a change directory containing `.openspec.yaml` and `proposal.md`
- **WHEN** a scan reports that change
- **THEN** the artifact list contains `proposal.md` and does not contain `.openspec.yaml`

---
### Requirement: Derive task progress from tasks.md

The system SHALL derive task progress by parsing the `tasks.md` file inside a change directory. A line SHALL count toward the total when, after stripping leading whitespace, it matches a Markdown checkbox list item. A checkbox containing a single space SHALL count as incomplete, and a checkbox containing the letter `x` in either case SHALL count as complete. A checkbox containing any other content SHALL NOT count toward the total. Lines inside fenced code blocks SHALL be ignored. Content following the checkbox, including parallel-task markers, SHALL NOT affect counting.

When `tasks.md` is absent or contains no counted checkbox, the system SHALL report that no progress information is available.

#### Scenario: Mixed checkbox states are counted

- **WHEN** `tasks.md` contains a mixture of complete and incomplete checkbox items
- **THEN** the reported progress is the number of complete items and the total number of counted items

##### Example: checkbox parsing cases

| Line                             | Counted in total | Counted as complete |
| -------------------------------- | ---------------- | ------------------- |
| `- [ ] 1.1 Implement scanner`    | yes              | no                  |
| `- [x] 1.2 Write tests`          | yes              | yes                 |
| `- [X] 1.3 Update docs`          | yes              | yes                 |
| `  - [ ] 1.4 Nested subtask`     | yes              | no                  |
| `- [ ] [P] 1.5 Parallel task`    | yes              | no                  |
| `- [~] 1.6 Unknown marker`       | no               | no                  |
| `## 2. Section heading`          | no               | no                  |
| `- [ ] inside a fenced block`    | no               | no                  |

#### Scenario: No tasks file

- **WHEN** a change directory has no `tasks.md`
- **THEN** the change reports no progress information

#### Scenario: Tasks file without checkboxes

- **WHEN** `tasks.md` exists but contains no counted checkbox line
- **THEN** the change reports no progress information

---
### Requirement: Derive change status from task progress

The system SHALL derive a status for each change from its task progress. When no progress information is available, the status SHALL be Draft. When the total is greater than zero and the complete count is zero, the status SHALL be Not Started. When the total is greater than zero and the complete count equals the total, the status SHALL be Complete. Otherwise the status SHALL be In Progress.

#### Scenario: Status derivation

- **WHEN** a change is reported by a scan
- **THEN** its status follows the derivation rules

##### Example: status by progress

| Complete | Total | Status      |
| -------- | ----- | ----------- |
| none     | none  | Draft       |
| 0        | 8     | Not Started |
| 3        | 8     | In Progress |
| 8        | 8     | Complete    |

---
### Requirement: Degrade gracefully on unreadable changes

A failure to read one change directory SHALL NOT abort the scan. The affected change SHALL be omitted from the snapshot, a warning SHALL be written to the IDE log, and no error dialog SHALL be shown. A missing or unparsable `.openspec.yaml` SHALL NOT omit the change; the change SHALL still be reported without that metadata.

#### Scenario: One change directory is unreadable

- **GIVEN** three active change directories, one of which cannot be read
- **WHEN** a scan runs
- **THEN** the Active group contains the two readable changes and a warning is written to the IDE log

#### Scenario: Change metadata file is missing

- **GIVEN** a change directory with no `.openspec.yaml`
- **WHEN** a scan runs
- **THEN** the change is reported, without the metadata that file would have supplied

---
### Requirement: Scan off the event dispatch thread

The scan SHALL NOT perform file system access on the IDE event dispatch thread.

#### Scenario: Scan is requested from the UI

- **WHEN** a scan is triggered by opening the tool window or by a refresh action
- **THEN** the file system traversal runs on a background thread and only the result delivery touches the event dispatch thread

## MODIFIED Requirements

### Requirement: Scan changes from all three Spectra sources

The system SHALL discover Spectra changes by reading the file system directly, without invoking the `spectra` CLI or reading the Spectra internal database. A scan SHALL take a project root directory as input and produce a snapshot containing three groups of changes: Active, Parked, and Archived.

Active changes SHALL be the immediate subdirectories of `openspec/changes/` under the project root, excluding the directory named `archive`. Archived changes SHALL be the immediate subdirectories of `openspec/changes/archive/` under the project root. Parked changes SHALL be the immediate subdirectories of `spectra-app/changes/` under the resolved git directory.

The order of changes within a group is unspecified. Callers SHALL NOT depend on it; ordering is decided by the presentation layer.

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

#### Scenario: Every discovered change is present regardless of order

- **GIVEN** `openspec/changes/` contains directories `zebra-fix`, `add-search`, and `mid-tier`
- **WHEN** a scan runs
- **THEN** the Active group contains exactly those three changes, in any order

### Requirement: Report per-change metadata

For each discovered change, the system SHALL report its name, its group, the absolute path of its directory, the list of its Markdown artifact files, its task progress, its derived status, its creation date, and its modification date.

The name SHALL be the change directory name. The artifact file list SHALL contain every file with the `.md` extension found beneath the change directory, expressed as a path relative to that change directory, sorted as strings.

The creation date SHALL be parsed from the `created` field of `.openspec.yaml` in the change directory. When that file is absent or unreadable, when it has no `created` field, or when the field's value cannot be parsed as an ISO date, the creation date SHALL be reported as unknown and the change SHALL still be reported.

The modification date SHALL be the most recent modification time among the change's Markdown files. When a file's modification time cannot be read, that file SHALL be excluded from the comparison. When no modification time can be obtained, the modification date SHALL be reported as unknown.

#### Scenario: Artifact files are listed relative to the change directory

- **GIVEN** a change directory containing `proposal.md`, `tasks.md`, and `specs/theme-engine/spec.md`
- **WHEN** a scan reports that change
- **THEN** the artifact list contains exactly `proposal.md`, `specs/theme-engine/spec.md`, and `tasks.md`, in that string order

#### Scenario: Non-Markdown files are excluded

- **GIVEN** a change directory containing `.openspec.yaml` and `proposal.md`
- **WHEN** a scan reports that change
- **THEN** the artifact list contains `proposal.md` and does not contain `.openspec.yaml`

#### Scenario: Creation date is read from the change metadata file

- **GIVEN** a change directory whose `.openspec.yaml` contains the line `created: 2026-08-10`
- **WHEN** a scan reports that change
- **THEN** the creation date is 2026-08-10

#### Scenario: Unusable creation metadata never removes a change

- **WHEN** a change's `.openspec.yaml` is missing, has no `created` field, or has a `created` value that is not an ISO date
- **THEN** the change is reported with an unknown creation date

##### Example: creation metadata cases

| `.openspec.yaml` content        | Creation date | Change reported |
| ------------------------------- | ------------- | --------------- |
| `created: 2026-08-10`           | 2026-08-10    | yes             |
| `schema: spec-driven` only      | unknown       | yes             |
| `created: last Tuesday`         | unknown       | yes             |
| `created:`                      | unknown       | yes             |
| file absent                     | unknown       | yes             |

#### Scenario: Modification date reflects edits to artifact content

- **GIVEN** a change whose Markdown files were last modified at a known time
- **WHEN** one of those files is edited and a new scan runs
- **THEN** the reported modification date is the edited file's new modification time

##### Example: newest Markdown file wins

- **GIVEN** a change containing `proposal.md` modified at 2026-08-11 17:00 and `tasks.md` modified at 2026-08-12 09:00
- **WHEN** a scan reports that change
- **THEN** the modification date is 2026-08-12 09:00

#### Scenario: A change with no Markdown files has no modification date

- **GIVEN** a change directory containing only `.openspec.yaml`
- **WHEN** a scan reports that change
- **THEN** the modification date is unknown
